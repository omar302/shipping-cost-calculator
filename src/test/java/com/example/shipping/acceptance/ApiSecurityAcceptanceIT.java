package com.example.shipping.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@DisplayName("API Security")
class ApiSecurityAcceptanceIT extends AcceptanceTestSupport {

    // A request every shipping rule accepts, so the key is the only thing under test.
    private static final String VALID_REQUEST = """
            { "weightKg": 2.00, "zone": "DOMESTIC", "orderTotal": 0.00 }
            """;

    // A request the weight rules reject, for proving which refusal comes first.
    private static final String ZERO_WEIGHT_REQUEST = """
            { "weightKg": 0.00, "zone": "DOMESTIC", "orderTotal": 0.00 }
            """;

    private MvcTestResult calculateWithKey(String apiKey) {
        return calculateWithKey(apiKey, VALID_REQUEST);
    }

    private MvcTestResult calculateWithKey(String apiKey, String json) {
        return mvc.post().uri("/api/shipping/calculate")
                .header(API_KEY_HEADER, apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
                .exchange();
    }

    private MvcTestResult adminRatesWithKey(String apiKey) {
        return mvc.get().uri("/api/admin/rates").header(API_KEY_HEADER, apiKey).exchange();
    }

    private MvcTestResult calculateWithoutKey() {
        return calculateWithoutKey(VALID_REQUEST);
    }

    private MvcTestResult calculateWithoutKey(String json) {
        return mvc.post().uri("/api/shipping/calculate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
                .exchange();
    }

    @Nested
    @DisplayName("A request that does not carry a recognised API key is refused")
    class UnrecognisedApiKey {

        @Test
        @DisplayName("The one where a key configured as USER is accepted")
        void configuredKeyIsAccepted() {
            assertThat(calculateWithKey(USER_KEY)).hasStatusOk();
        }

        // A separate test rather than a parameter: an absent header is a different
        // request from one carrying an empty value, and no parameter source can
        // express "send no header at all".
        @Test
        @DisplayName("The one where a request carries no API key at all and is refused")
        void requestWithoutAnApiKeyIsRefused() {
            assertThat(calculateWithoutKey()).hasStatus(HttpStatus.UNAUTHORIZED);
        }

        // The explanation for each is pinned by the refusal-explanation rule.
        @ParameterizedTest(name = "The one where a key of \"{0}\" is not configured and is refused")
        @ValueSource(strings = {"not-a-configured-key", ""})
        void keyThatIsNotConfiguredIsRefused(String apiKey) {
            assertThat(calculateWithKey(apiKey)).hasStatus(HttpStatus.UNAUTHORIZED);
        }
    }

    @Nested
    @DisplayName("Access is granted according to the role the key carries")
    class RoleBasedAccess {

        // ADMIN is a superset of USER, not a separate lane, so both reach the
        // calculating endpoint. What each response contains is other specs' business.
        @ParameterizedTest(name = "The one where a {0} key may calculate a shipping cost")
        @ValueSource(strings = {USER_KEY, ADMIN_KEY})
        void everyRoleMayCalculateAShippingCost(String apiKey) {
            assertThat(calculateWithKey(apiKey)).hasStatusOk();
        }

        @Test
        @DisplayName("The one where an ADMIN key may read the administrative rates")
        void adminKeyMayReachAnAdministrativeEndpoint() {
            assertThat(adminRatesWithKey(ADMIN_KEY)).hasStatusOk();
        }

        // The point of having roles: a valid key is not a licence for every endpoint.
        // 403 rather than 401 — the caller is known, but not permitted.
        @Test
        @DisplayName("The one where a USER key is refused at an administrative endpoint")
        void userKeyIsRefusedAtAnAdministrativeEndpoint() {
            assertThat(adminRatesWithKey(USER_KEY)).hasStatus(HttpStatus.FORBIDDEN);
        }
    }

    @Nested
    @DisplayName("Authentication and authorisation are applied before any shipping rule")
    class SecurityBeforeShippingRules {

        @Test
        @DisplayName("The one where a request carries no key and a weight of 0kg, and is refused as unauthenticated, the weight never being examined")
        void unauthenticatedRequestIsRefusedBeforeItsWeightIsExamined() {
            assertThat(calculateWithoutKey(ZERO_WEIGHT_REQUEST))
                    .hasStatus(HttpStatus.UNAUTHORIZED)
                    .bodyJson().extractingPath("$.detail").asString()
                    .isEqualTo("API key is required");
        }

        // A 404 here would tell a caller who may not use administrative endpoints which
        // ones exist. Authorisation is settled before the request is routed.
        @Test
        @DisplayName("The one where a USER key reaches an administrative path that does not exist and is refused as not permitted rather than told it is not there")
        void unauthorisedRequestIsRefusedBeforeItIsRouted() {
            assertThat(mvc.get().uri("/api/admin/does-not-exist")
                    .header(API_KEY_HEADER, USER_KEY)
                    .exchange())
                    .hasStatus(HttpStatus.FORBIDDEN)
                    .bodyJson().extractingPath("$.detail").asString()
                    .isEqualTo("API key is not permitted to use this endpoint");
        }

        // Once authorised, every shipping rule applies exactly as before — the security
        // layer refuses first or gets out of the way entirely.
        @Test
        @DisplayName("The one where a valid USER key carries a weight of 0kg and is rejected for the weight, with the existing explanation")
        void authorisedRequestIsStillSubjectToEveryShippingRule() {
            assertThat(calculateWithKey(USER_KEY, ZERO_WEIGHT_REQUEST))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson().extractingPath("$.detail").asString()
                    .isEqualTo("Parcel weight must be above 0kg and at most 50kg, but was 0.00kg");
        }
    }

    @Nested
    @DisplayName("A refusal is explained without disclosing the key")
    class RefusalExplanation {

        @Test
        @DisplayName("The one where a request with no key is refused with an explanation naming the missing credential")
        void missingKeyRefusalNamesTheMissingCredential() {
            assertThat(calculateWithoutKey())
                    .hasStatus(HttpStatus.UNAUTHORIZED)
                    .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .bodyJson().extractingPath("$.detail").asString()
                    .isEqualTo("API key is required");
        }

        @Test
        @DisplayName("The one where a request with an unrecognised key is refused without the key appearing anywhere in the response")
        void unrecognisedKeyRefusalNeverEchoesTheKey() {
            MvcTestResult result = calculateWithKey("not-a-configured-key");

            assertThat(result)
                    .hasStatus(HttpStatus.UNAUTHORIZED)
                    .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .bodyJson().extractingPath("$.detail").asString()
                    .isEqualTo("API key is not recognised");
            // The deliberate departure from the rejection contract every other spec
            // follows: a rejected key is a credential, so it is never named.
            assertThat(result).bodyText().doesNotContain("not-a-configured-key");
        }

        // A header sent but empty names no key at all, so it is unrecognised rather
        // than missing — the same call destination-zones makes for an empty zone.
        @Test
        @DisplayName("The one where a request with an empty key is refused as unrecognised rather than as missing")
        void emptyKeyRefusalIsUnrecognisedRatherThanMissing() {
            assertThat(calculateWithKey(""))
                    .hasStatus(HttpStatus.UNAUTHORIZED)
                    .bodyJson().extractingPath("$.detail").asString()
                    .isEqualTo("API key is not recognised");
        }

        @Test
        @DisplayName("The one where a USER key at an administrative endpoint is refused as not permitted")
        void forbiddenRefusalNamesThePermissionRatherThanTheKey() {
            assertThat(adminRatesWithKey(USER_KEY))
                    .hasStatus(HttpStatus.FORBIDDEN)
                    .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .bodyJson().extractingPath("$.detail").asString()
                    .isEqualTo("API key is not permitted to use this endpoint");
        }
    }

    @Nested
    @DisplayName("The API documentation is reachable without a key")
    class PublicDocumentation {

        // The counter-example — the calculate endpoint refused without a key, so that
        // being unauthenticated opens the documentation and nothing else — is
        // requestWithoutAnApiKeyIsRefused above, not repeated here.
        @Test
        @DisplayName("The one where Swagger UI is fetched with no API key and is served")
        void swaggerUiIsServedWithoutAnApiKey() {
            assertThat(mvc.get().uri("/swagger-ui.html").exchange())
                    .hasStatus3xxRedirection();
        }

        // The UI cannot render without the document it fetches, so opening one without
        // the other would leave the documentation unreadable.
        @Test
        @DisplayName("The one where the OpenAPI document behind Swagger UI is fetched with no API key and is served")
        void openApiDocumentIsServedWithoutAnApiKey() {
            assertThat(mvc.get().uri("/v3/api-docs").exchange()).hasStatusOk();
        }

        // Documentation an integrator reads before being issued a key has to tell them
        // a key is needed, and which header carries it.
        @Test
        @DisplayName("The one where the documentation declares that the endpoints need an X-API-Key header")
        void documentationDeclaresTheApiKeyRequirement() {
            MvcTestResult result = mvc.get().uri("/v3/api-docs").exchange();

            assertThat(result).bodyJson()
                    .extractingPath("$.components.securitySchemes.apiKey.in").asString()
                    .isEqualTo("header");
            assertThat(result).bodyJson()
                    .extractingPath("$.components.securitySchemes.apiKey.name").asString()
                    .isEqualTo(API_KEY_HEADER);
        }
    }
}
