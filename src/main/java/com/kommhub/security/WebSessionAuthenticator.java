package com.kommhub.security;

import com.kommhub.model.db.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

@Component
public class WebSessionAuthenticator {

    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    public void authenticate(CustomUserDetails userDetails, HttpServletRequest request, HttpServletResponse response) {

        if (request.getSession(false) != null) {
            request.changeSessionId();
        }

        User user = userDetails.getUser();
        Object details = new WebAuthenticationDetailsSource().buildDetails(request);

        UsernamePasswordAuthenticationToken sessionToken = new UsernamePasswordAuthenticationToken(
                SessionUser.builder()
                        .userId(user.getUserId())
                        .username(user.getUsername())
                        .build(),
                null, userDetails.getAuthorities());
        sessionToken.setDetails(details);
        SecurityContext sessionContext = SecurityContextHolder.createEmptyContext();
        sessionContext.setAuthentication(sessionToken);
        securityContextRepository.saveContext(sessionContext, request, response);

        UsernamePasswordAuthenticationToken requestToken = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());
        requestToken.setDetails(details);
        SecurityContext requestContext = SecurityContextHolder.createEmptyContext();
        requestContext.setAuthentication(requestToken);
        SecurityContextHolder.setContext(requestContext);
    }
}
