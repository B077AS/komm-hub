package com.kommhub.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class WebSessionUserFilter extends OncePerRequestFilter {

    private final CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof SessionUser sessionUser) {
            try {
                CustomUserDetails userDetails = userDetailsService.loadUserById(sessionUser.getUserId());
                if (userDetails.isEnabled()) {
                    UsernamePasswordAuthenticationToken fresh = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
                    fresh.setDetails(auth.getDetails());
                    SecurityContext context = SecurityContextHolder.createEmptyContext();
                    context.setAuthentication(fresh);
                    // Deliberately NOT saved to the session repository: the session keeps
                    // only the SessionUser, this hydrated context lives for this request.
                    SecurityContextHolder.setContext(context);
                } else {
                    signOut(request);
                }
            } catch (UsernameNotFoundException e) {
                signOut(request);
            }
        }

        filterChain.doFilter(request, response);
    }

    private void signOut(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }
}
