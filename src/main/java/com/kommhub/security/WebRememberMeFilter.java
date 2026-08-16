package com.kommhub.security;

import com.kommhub.model.dto.response.AuthResponse;
import com.kommhub.service.AuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebRememberMeFilter extends OncePerRequestFilter {

    private final AuthService authService;
    private final JwtUtil jwtUtil;
    private final CustomUserDetailsService userDetailsService;
    private final WebSessionAuthenticator sessionAuthenticator;

    @Value("${jwt.refresh-token.expiration}")
    private long refreshTokenExpiration;

    @Value("${jwt.refresh-cookie.secure:true}")
    private boolean cookieSecure;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {

        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            String cookieValue = readCookie(request);
            if (cookieValue != null) {
                try {
                    AuthResponse auth = authService.refresh(cookieValue);
                    UUID userId = jwtUtil.extractUserId(auth.getAccessToken());
                    CustomUserDetails userDetails = userDetailsService.loadUserById(userId);
                    sessionAuthenticator.authenticate(userDetails, request, response);
                    response.addHeader(HttpHeaders.SET_COOKIE,
                            WebRememberMeCookie.build(auth.getRefreshToken(), refreshTokenExpiration, cookieSecure));
                } catch (Exception e) {
                    log.debug("Remember-me cookie rejected: {}", e.getMessage());
                    response.addHeader(HttpHeaders.SET_COOKIE, WebRememberMeCookie.build("", 0, cookieSecure));
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private String readCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) {
            if (WebRememberMeCookie.NAME.equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }
}
