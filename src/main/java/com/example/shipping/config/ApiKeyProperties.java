package com.example.shipping.config;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

// Keyed by the API key itself, so several keys can share a role and one can be
// revoked or rotated without affecting the others: shipping.api-keys.<key>=<role>.
@ConfigurationProperties(prefix = "shipping")
public record ApiKeyProperties(Map<String, Role> apiKeys) {

    public ApiKeyProperties {
        apiKeys = apiKeys == null ? Map.of() : Map.copyOf(apiKeys);
    }

    public Role roleFor(String apiKey) {
        return apiKey == null ? null : apiKeys.get(apiKey);
    }
}
