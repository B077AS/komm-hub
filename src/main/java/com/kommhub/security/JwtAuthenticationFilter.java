package com.kommhub.security;

import com.google.gson.Gson;
import com.kommhub.model.dto.response.ErrorResponse;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final Gson gson;
    private final JwtUtil jwtUtil;
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            final String jwt = authHeader.substring(7);
            final Claims claims = jwtUtil.validateToken(jwt);

            // Only access tokens authenticate API requests. Refresh tokens and
            // installation tickets are signed with the same key and also carry a
            // userId claim, so without this check they would be accepted here too.
            if (jwtUtil.getTokenType(claims) != JwtUtil.TokenType.ACCESS) {
                log.warn("Rejected non-access token on API request");
                filterChain.doFilter(request, response);
                return;
            }

            final UUID userId = UUID.fromString(claims.get("userId", String.class));

            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = this.userDetailsService.loadUserById(userId);

                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
                );
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }

            filterChain.doFilter(request, response);

        } catch (ExpiredJwtException e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");

            ErrorResponse errorResponse = ErrorResponse.builder()
                    .message("Token has expired. Please login again.")
                    .error("TOKEN_EXPIRED")
                    .status(401)
                    .build();

            response.getWriter().write(gson.toJson(errorResponse));

        } catch (Exception e) {
            log.error("Unexpected error occurred: {}", e.getMessage());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");

            ErrorResponse errorResponse = ErrorResponse.builder()
                    .message("Invalid or malformed token")
                    .error("INVALID_TOKEN")
                    .status(401)
                    .build();

            response.getWriter().write(gson.toJson(errorResponse));
        }
    }
}