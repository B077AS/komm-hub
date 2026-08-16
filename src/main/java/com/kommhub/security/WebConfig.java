package com.kommhub.security;

import com.google.gson.Gson;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final RateLimitFilter rateLimitFilter;
    private final WebSessionUserFilter webSessionUserFilter;
    private final Gson gson;
    private final ObjectProvider<WebRememberMeFilter> webRememberMeFilterProvider;

    @Value("${komm.cors.allowed-origins}")
    private List<String> corsAllowedOrigins;

    @Bean
    public org.springframework.boot.web.servlet.FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration() {
        var registration = new org.springframework.boot.web.servlet.FilterRegistrationBean<>(rateLimitFilter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public org.springframework.boot.web.servlet.FilterRegistrationBean<WebRememberMeFilter> webRememberMeFilterRegistration() {
        var registration = new org.springframework.boot.web.servlet.FilterRegistrationBean<>(webRememberMeFilterProvider.getObject());
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public org.springframework.boot.web.servlet.FilterRegistrationBean<WebSessionUserFilter> webSessionUserFilterRegistration() {
        var registration = new org.springframework.boot.web.servlet.FilterRegistrationBean<>(webSessionUserFilter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOriginPatterns(corsAllowedOrigins);
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setExposedHeaders(Arrays.asList("Authorization"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }

    // Public endpoints - no authentication required
    @Bean
    @Order(1)
    public SecurityFilterChain publicSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/auth/**", "/api/installations/validate", "/api/invites/*/info", "/api/client/**", "/api/launcher/**")
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/login").permitAll()
                        .requestMatchers("/api/auth/refresh").permitAll()
                        .requestMatchers("/api/auth/logout").permitAll()
                        .requestMatchers("/api/auth/register").permitAll()
                        .requestMatchers("/api/auth/register-info").permitAll()
                        .requestMatchers("/api/auth/ca").permitAll()
                        .requestMatchers("/api/auth/beta-request").permitAll()
                        .requestMatchers("/api/auth/verify-email").permitAll()
                        .requestMatchers("/api/auth/resend-verification").permitAll()
                        .requestMatchers("/api/auth/forgot-password").permitAll()
                        .requestMatchers("/api/auth/reset-password").permitAll()
                        .requestMatchers("/api/installations/validate").permitAll()
                        .requestMatchers("/api/invites/*/info").permitAll()
                        .requestMatchers("/api/client/**").permitAll()
                        .requestMatchers("/api/launcher/**").permitAll()
                        .anyRequest().denyAll()
                )
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json");
                            response.getWriter().write(gson.toJson(Map.of(
                                    "error", "Unauthorized",
                                    "message", authException.getMessage() == null
                                            ? "Authentication required" : authException.getMessage(),
                                    "code", "UNAUTHORIZED")));
                        }));

        return http.build();
    }

    // Protected API endpoints - JWT authentication required
    @Bean
    @Order(2)
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/**")
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(rateLimitFilter, JwtAuthenticationFilter.class)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json");
                            response.getWriter().write(
                                    "{\"error\":\"Unauthorized\",\"message\":\"Authentication required\",\"code\":\"UNAUTHORIZED\"}"
                            );
                        }));

        return http.build();
    }

    @Bean
    @Order(3)
    public SecurityFilterChain webSocketSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/ws/**")
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll()
                );

        return http.build();
    }

    // Web Security Chain - session-cookie auth for browser pages (see AuthWebController
    // for the actual /login and /logout handlers and WebRememberMeFilter below for silent
    // remember-me re-auth). Everything not explicitly public requires an authenticated
    // session - there is only the one protected area, /dashboard.
    @Bean
    @Order(4)
    public SecurityFilterChain webSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/**")
                // CSRF stays on its defaults: session-backed token repository, with the token
                // injected into the Thymeleaf forms server-side. No JS ever reads the token,
                // so there is no reason to expose it in a script-readable cookie.
                // Without this, Spring Security silently applies its own default LogoutFilter
                // on POST /logout, which intercepts the request before it ever reaches
                // AuthWebController.logout() - meaning the remember-me token never gets
                // revoked server-side and none of our cookies get cleared.
                .logout(AbstractHttpConfigurer::disable)

                .authorizeHttpRequests(auth -> auth
                        // Public pages
                        .requestMatchers("/", "/home", "/download").permitAll()
                        .requestMatchers("/login", "/register", "/forgot-password", "/reset-password", "/verify-email").permitAll()
                        .requestMatchers(HttpMethod.POST, "/logout").permitAll()
                        .requestMatchers("/invite/**").permitAll()
                        // Static assets - must stay public or the login/home pages can't load their own CSS/JS
                        .requestMatchers("/css/**", "/js/**", "/fonts/**").permitAll()
                        .requestMatchers("/favicon.ico", "/favicon.svg", "/logo.png", "/robots.txt", "/sitemap.xml").permitAll()

                        .anyRequest().authenticated()
                )

                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(webRememberMeFilterProvider.getObject(), RateLimitFilter.class)
                .addFilterAfter(webSessionUserFilter, WebRememberMeFilter.class)

                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.sendRedirect("/login");
                        })
                );

        return http.build();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/css/**")
                .addResourceLocations("classpath:/static/css/");
        registry.addResourceHandler("/js/**")
                .addResourceLocations("classpath:/static/js/");
        registry.addResourceHandler("/fonts/**")
                .addResourceLocations("classpath:/static/fonts/");
        registry.addResourceHandler("/favicon.ico", "/favicon.svg", "/logo.png", "/robots.txt", "/sitemap.xml")
                .addResourceLocations("classpath:/static/");
    }
}