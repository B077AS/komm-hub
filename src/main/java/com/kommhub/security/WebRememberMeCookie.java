package com.kommhub.security;

import org.springframework.http.ResponseCookie;

public class WebRememberMeCookie {

    public static final String NAME = "komm_web_remember";

    public static String build(String value, long maxAgeSeconds, boolean secure) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAgeSeconds)
                .build()
                .toString();
    }
}
