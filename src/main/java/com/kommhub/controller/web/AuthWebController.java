package com.kommhub.controller.web;

import com.kommhub.model.dto.request.LoginRequest;
import com.kommhub.model.dto.response.AuthResponse;
import com.kommhub.security.CustomUserDetails;
import com.kommhub.security.CustomUserDetailsService;
import com.kommhub.security.JwtUtil;
import com.kommhub.security.WebRememberMeCookie;
import com.kommhub.security.WebSessionAuthenticator;
import com.kommhub.service.AuthService;
import com.kommhub.service.BetaKeyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class AuthWebController {

    private static final String DASHBOARD = "/dashboard";

    private final BetaKeyService betaKeyService;
    private final AuthService authService;
    private final JwtUtil jwtUtil;
    private final CustomUserDetailsService userDetailsService;
    private final WebSessionAuthenticator sessionAuthenticator;

    @Value("${jwt.refresh-token.expiration}")
    private long refreshTokenExpiration;

    @Value("${jwt.refresh-cookie.secure:true}")
    private boolean cookieSecure;

    @GetMapping("/login")
    public String loginPage() {
        return "auth/login";
    }

    @PostMapping("/login")
    public String login(@RequestParam String username,
                         @RequestParam String password,
                         @RequestParam(name = "rememberMe", defaultValue = "false") boolean rememberMe,
                         HttpServletRequest request,
                         HttpServletResponse response,
                         Model model) {
        try {
            AuthResponse auth = authService.login(LoginRequest.builder().username(username).password(password).build());
            UUID userId = jwtUtil.extractUserId(auth.getAccessToken());
            CustomUserDetails userDetails = userDetailsService.loadUserById(userId);
            sessionAuthenticator.authenticate(userDetails, request, response);

            if (rememberMe) {
                response.addHeader(HttpHeaders.SET_COOKIE,
                        WebRememberMeCookie.build(auth.getRefreshToken(), refreshTokenExpiration, cookieSecure));
            }
            return "redirect:" + DASHBOARD;
        } catch (DisabledException e) {
            model.addAttribute("error", "Email not verified. Please check your inbox for the verification code.");
        } catch (BadCredentialsException e) {
            model.addAttribute("error", "Invalid username or password");
        } catch (Exception e) {
            model.addAttribute("error", "Something went wrong. Please try again.");
        }
        model.addAttribute("username", username);
        return "auth/login";
    }

    @PostMapping("/logout")
    public String logout(@CookieValue(name = WebRememberMeCookie.NAME, required = false) String rememberCookie,
                          HttpServletRequest request,
                          HttpServletResponse response) {
        if (rememberCookie != null) {
            authService.logout(rememberCookie);
            response.addHeader(HttpHeaders.SET_COOKIE, WebRememberMeCookie.build("", 0, cookieSecure));
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        new CookieClearingLogoutHandler("JSESSIONID").logout(request, response, authentication);
        return "redirect:/login";
    }

    @GetMapping("/register")
    public String registerPage(Model model) {
        model.addAttribute("betaEnabled", betaKeyService.isBetaEnabled());
        return "auth/register";
    }

    @GetMapping("/forgot-password")
    public String forgotPasswordPage() {
        return "auth/forgot-password";
    }

    @GetMapping("/reset-password")
    public String resetPasswordPage() {
        return "auth/reset-password";
    }

    @GetMapping("/verify-email")
    public String verifyEmailPage(@RequestParam(required = false, defaultValue = "") String email,
                                  Model model) {
        model.addAttribute("email", email);
        return "auth/verify-email";
    }
}
