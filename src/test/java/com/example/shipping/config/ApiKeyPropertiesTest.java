package com.example.shipping.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

// The binder is the seam under test, so this runs a context holding nothing but the
// properties themselves — no web layer, no security filter chain.
class ApiKeyPropertiesTest {

    private final ApplicationContextRunner contexts = new ApplicationContextRunner()
            .withConfiguration(
                    org.springframework.boot.autoconfigure.AutoConfigurations.of(
                            ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(EnableApiKeyProperties.class);

    @Test
    @DisplayName("The one where a key is configured as ADMNI and the application refuses to start")
    void unrecognisedRoleRefusesToStart() {
        contexts.withPropertyValues("shipping.api-keys.test-key=ADMNI")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("The one where keys are configured as USER and ADMIN and the application starts normally")
    void recognisedRolesStartNormally() {
        contexts.withPropertyValues(
                        "shipping.api-keys.user-key=USER",
                        "shipping.api-keys.admin-key=ADMIN")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @org.springframework.boot.context.properties.EnableConfigurationProperties(ApiKeyProperties.class)
    static class EnableApiKeyProperties {
    }
}
