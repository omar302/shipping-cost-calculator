package com.example.shipping.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    static final String API_KEY_HEADER = "X-API-Key";

    private final ApiKeyProperties apiKeys;

    public ApiKeyAuthenticationFilter(ApiKeyProperties apiKeys) {
        this.apiKeys = apiKeys;
    }

    // An absent, empty or unconfigured key simply leaves the context unauthenticated;
    // refusing is the filter chain's job, so public paths still work without a key.
    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String apiKey = request.getHeader(API_KEY_HEADER);
        Role role = apiKeys.roleFor(apiKey);

        if (role != null) {
            SecurityContextHolder.getContext().setAuthentication(
                    UsernamePasswordAuthenticationToken.authenticated(
                            apiKey, null, List.of(new SimpleGrantedAuthority(role.authority()))));
        }

        chain.doFilter(request, response);
    }
}
