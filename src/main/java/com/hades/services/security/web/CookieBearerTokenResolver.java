package com.hades.services.security.web;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;

import java.util.Arrays;

public class CookieBearerTokenResolver implements BearerTokenResolver {

    private final DefaultBearerTokenResolver defaultBearerTokenResolver = new DefaultBearerTokenResolver();
    // Using a consistent cookie name
    private static final String COOKIE_NAME = "hades_session";

    @Override
    public String resolve(HttpServletRequest request) {
        // First, attempt to resolve from the Authorization header
        String token = defaultBearerTokenResolver.resolve(request);
        if (token != null) {
            return token;
        }

        // If not found in header, check for the cookie
        if (request.getCookies() != null) {
            return Arrays.stream(request.getCookies())
                    .filter(c -> COOKIE_NAME.equals(c.getName()))
                    .map(Cookie::getValue)
                    .findFirst()
                    .orElse(null);
        }

        return null;
    }
}
