package com.example.shipping.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableConfigurationProperties(ApiKeyProperties.class)
public class SecurityConfig {

    private static final String[] PUBLIC_DOCUMENTATION_PATHS = {
            "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**",
    };

    private static final String[] ADMIN_PATHS = {"/api/admin/**"};

    @Bean
    public SecurityFilterChain apiKeySecurity(
            HttpSecurity http, ApiKeyProperties apiKeys, ObjectMapper objectMapper)
            throws Exception {
        ProblemDetailWriter problemDetails = new ProblemDetailWriter(objectMapper);

        return http
                // The API key is the whole credential, so there is no session or login
                // form to protect and nothing for CSRF tokens to defend.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Documentation is public so an integrator can read it before they have
                // been issued a key. The UI cannot render without the OpenAPI document
                // it fetches, so opening one without the other would achieve nothing.
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(PUBLIC_DOCUMENTATION_PATHS).permitAll()
                        // ADMIN is a superset of USER: administrative paths need the
                        // ADMIN role, everything else only a recognised key.
                        .requestMatchers(ADMIN_PATHS).hasRole("ADMIN")
                        .anyRequest().authenticated())
                // Without this the default for an unauthenticated caller is 403; the
                // spec distinguishes "not authenticated" (401) from "not permitted" (403).
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(new ApiKeyAuthenticationEntryPoint(problemDetails))
                        .accessDeniedHandler(new ApiKeyAccessDeniedHandler(problemDetails)))
                .addFilterBefore(new ApiKeyAuthenticationFilter(apiKeys),
                        UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
