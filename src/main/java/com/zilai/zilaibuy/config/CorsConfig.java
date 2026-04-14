package com.zilai.zilaibuy.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Global CORS filter that runs before Spring Security.
 * Ensures Access-Control-Allow-Origin is present on every response,
 * including 401/403 error responses from Spring Security.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorsConfig implements Filter {

    private final Set<String> allowedOrigins;

    public CorsConfig(@Value("${app.cors.allowed-origins}") String originsConfig) {
        allowedOrigins = Arrays.stream(originsConfig.split(","))
                .map(String::trim)
                .collect(Collectors.toSet());
        // Always allow local development
        allowedOrigins.add("http://localhost:3000");
        allowedOrigins.add("http://localhost:4200");
        allowedOrigins.add("http://127.0.0.1:3000");
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        String origin = request.getHeader("Origin");
        if (origin != null && allowedOrigins.contains(origin)) {
            response.setHeader("Access-Control-Allow-Origin", origin);
            response.setHeader("Access-Control-Allow-Credentials", "true");
            response.setHeader("Vary", "Origin");
        }

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setHeader("Access-Control-Allow-Methods", "GET,POST,PUT,PATCH,DELETE,OPTIONS");
            response.setHeader("Access-Control-Allow-Headers", "Authorization,Content-Type,Accept");
            response.setHeader("Access-Control-Max-Age", "3600");
            response.setStatus(HttpServletResponse.SC_OK);
            return;
        }

        chain.doFilter(req, res);
    }
}
