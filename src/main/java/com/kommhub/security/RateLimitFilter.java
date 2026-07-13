package com.kommhub.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.gson.Gson;
import com.kommhub.model.dto.response.ErrorResponse;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Token-bucket rate limiting for {@code /api/**}. Runs after {@link JwtAuthenticationFilter}
 * on the authenticated chain (so the user principal is available) and standalone on the
 * public chain. Authenticated requests are keyed per user; public ones per client IP —
 * which is only correct because {@code server.forward-headers-strategy=framework} makes
 * {@code getRemoteAddr()} return the real client IP behind the reverse proxy.
 *
 * <p>Each matched {@link Rule} gets its own bucket per subject, so exhausting one endpoint's
 * budget never blocks another. Rules are matched in declaration order, first match wins, and
 * the trailing {@code /api/**} default catches everything else with a loose per-subject limit.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final Gson gson;
    private final SecurityUtil securityUtil;

    @Value("${komm.ratelimit.enabled:true}")
    private boolean enabled;

    // Shadow mode: when false, breaches are logged but the request still proceeds.
    @Value("${komm.ratelimit.enforce:false}")
    private boolean enforce;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    // key = ruleId + "|" + subject (userId or IP) -> that subject's bucket for that rule.
    // Idle expiry MUST stay >= the longest rule refill window (1h) so an entry is only ever
    // evicted after it has fully refilled — otherwise idling could reset a drained budget.
    // maximumSize is a hard memory ceiling against IP churn; active subjects keep their entry
    // alive because every request re-touches the key (expireAfterAccess).
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofHours(2))
            .maximumSize(200_000)
            .build();

    /** method: HTTP method to match, or "*" for any. Limits are capacity tokens, refilled greedily. */
    private record Rule(String id, String method, String pathPattern,
                        long capacity, long refillTokens, Duration refillPeriod) {}

    // Ordered; first match wins. Public endpoints are keyed per IP (no principal on those paths),
    // authenticated ones per user. See conversation notes for the reasoning behind each number.
    private static final List<Rule> RULES = List.of(
            // ── Public, per-IP: strict on abuse-prone + email/CPU/bandwidth-heavy paths ──
            new Rule("login",            "POST", "/api/auth/login",               10, 10, Duration.ofMinutes(1)),
            new Rule("register",         "POST", "/api/auth/register",             5,  5,  Duration.ofHours(1)),
            new Rule("verify-email",     "POST", "/api/auth/verify-email",         10, 10, Duration.ofMinutes(15)),
            new Rule("resend-verify",    "POST", "/api/auth/resend-verification",  5,  5,  Duration.ofHours(1)),
            new Rule("forgot-password",  "POST", "/api/auth/forgot-password",      5,  5,  Duration.ofHours(1)),
            new Rule("reset-password",   "POST", "/api/auth/reset-password",       10, 10, Duration.ofMinutes(15)),
            new Rule("beta-request",     "POST", "/api/auth/beta-request",         2,  1,  Duration.ofMinutes(5)),
            new Rule("install-validate", "POST", "/api/installations/validate",    5,  5,  Duration.ofMinutes(1)),
            new Rule("invite-info",      "GET",  "/api/invites/*/info",            30, 30, Duration.ofMinutes(1)),
            new Rule("client-download",  "GET",  "/api/client/download",           5,  5,  Duration.ofHours(1)),

            // ── Authenticated, per-user: expensive or spammy ──
            new Rule("gifs",             "GET",  "/api/gifs/**",                   60, 60, Duration.ofMinutes(1)),
            new Rule("dm-attach",        "POST", "/api/dm/attachments",            20, 20, Duration.ofMinutes(1)),
            new Rule("friend-request",   "POST", "/api/friends/request/**",        10, 10, Duration.ofMinutes(1)),
            new Rule("install-jar",      "GET",  "/api/installations/jar",         5,  5,  Duration.ofHours(1)),
            new Rule("install-create",   "POST", "/api/installations/create",      5,  5,  Duration.ofHours(1)),
            new Rule("server-create",    "POST", "/api/servers",                   5,  5,  Duration.ofHours(1)),
            new Rule("server-ticket",    "POST", "/api/servers/*/ticket",          20, 20, Duration.ofMinutes(1)),
            new Rule("permissions",      "*",    "/api/permissions/**",            40, 40, Duration.ofMinutes(1)),
            new Rule("moderation",       "*",    "/api/moderation/**",             40, 40, Duration.ofMinutes(1)),

            // ── Catch-all default: loose, just stops runaway loops ──
            new Rule("default",          "*",    "/api/**",                        300, 300, Duration.ofMinutes(1))
    );

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        // Only guards the API; CORS preflight is never rate limited.
        return !enabled
                || !request.getRequestURI().startsWith("/api/")
                || "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        Rule rule = resolveRule(request);
        if (rule == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String subject = subjectKey(request);
        Bucket bucket = buckets.get(rule.id() + "|" + subject, k -> newBucket(rule));
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            filterChain.doFilter(request, response);
            return;
        }

        long retryAfterSeconds = Math.max(1, probe.getNanosToWaitForRefill() / 1_000_000_000L);

        if (!enforce) {
            // Shadow mode: record what would have been blocked, then let it through.
            log.warn("RATE LIMIT (shadow) would block {} {} for subject {} on rule '{}' (retry-after {}s)",
                    request.getMethod(), request.getRequestURI(), subject, rule.id(), retryAfterSeconds);
            filterChain.doFilter(request, response);
            return;
        }

        log.warn("RATE LIMIT blocked {} {} for subject {} on rule '{}'",
                request.getMethod(), request.getRequestURI(), subject, rule.id());
        writeTooManyRequests(response, retryAfterSeconds);
    }

    private Rule resolveRule(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String method = request.getMethod();
        for (Rule rule : RULES) {
            if ((rule.method().equals("*") || rule.method().equalsIgnoreCase(method))
                    && pathMatcher.match(rule.pathPattern(), uri)) {
                return rule;
            }
        }
        return null;
    }

    /** Authenticated -> "u:<userId>"; otherwise "ip:<clientIp>" (real IP via forwarded headers). */
    private String subjectKey(HttpServletRequest request) {
        UUID userId = securityUtil.getCurrentUserId();
        if (userId != null) {
            return "u:" + userId;
        }
        return "ip:" + request.getRemoteAddr();
    }

    private static Bucket newBucket(Rule rule) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(rule.capacity())
                .refillGreedy(rule.refillTokens(), rule.refillPeriod())
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    private void writeTooManyRequests(HttpServletResponse response, long retryAfterSeconds) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType("application/json");
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));

        ErrorResponse body = ErrorResponse.builder()
                .status(HttpStatus.TOO_MANY_REQUESTS.value())
                .error(HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase())
                .message("Too many requests. Please slow down and try again in " + retryAfterSeconds + "s.")
                .build();
        response.getWriter().write(gson.toJson(body));
    }
}
