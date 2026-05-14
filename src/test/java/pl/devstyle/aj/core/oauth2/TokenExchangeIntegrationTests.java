package pl.devstyle.aj.core.oauth2;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pl.devstyle.aj.TestcontainersConfiguration;
import pl.devstyle.aj.core.security.JwtTokenProvider;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
class TokenExchangeIntegrationTests {

    private static final String TOKEN_EXCHANGE_GRANT_TYPE = "urn:ietf:params:oauth:grant-type:token-exchange";
    private static final String ACCESS_TOKEN_TYPE = "urn:ietf:params:oauth:token-type:access_token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    /**
     * Register a confidential client (client_secret_post) and return [client_id, client_secret].
     */
    private String[] registerConfidentialClient() throws Exception {
        var body = """
                {
                    "client_name": "MCP Token Exchange Client",
                    "grant_types": ["authorization_code"],
                    "redirect_uris": ["http://localhost:3000/callback"],
                    "response_types": ["code"],
                    "token_endpoint_auth_method": "client_secret_post",
                    "scope": "mcp:read mcp:edit"
                }
                """;

        var result = mockMvc.perform(post("/oauth2/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        var registration = objectMapper.readTree(result.getResponse().getContentAsString());
        return new String[]{
                registration.get("client_id").asText(),
                registration.get("client_secret").asText()
        };
    }

    /**
     * Generate a valid OAuth2 access token (Token-A) with MCP scopes.
     */
    private String generateTokenA() {
        return jwtTokenProvider.generateOAuth2Token("admin", Set.of("mcp:read", "mcp:edit"), "http://localhost");
    }

    @Test
    void tokenExchange_success_returnsTokenBWithMappedPermissionsAndRfc8693Format() throws Exception {
        var client = registerConfidentialClient();
        var clientId = client[0];
        var clientSecret = client[1];
        var subjectToken = generateTokenA();

        mockMvc.perform(post("/oauth2/token")
                        .param("grant_type", TOKEN_EXCHANGE_GRANT_TYPE)
                        .param("subject_token", subjectToken)
                        .param("subject_token_type", ACCESS_TOKEN_TYPE)
                        .param("scope", "mcp:read mcp:edit")
                        .param("client_id", clientId)
                        .param("client_secret", clientSecret))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").value(notNullValue()))
                .andExpect(jsonPath("$.issued_token_type").value(ACCESS_TOKEN_TYPE))
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").value(900))
                .andExpect(jsonPath("$.refresh_token").doesNotExist());
    }

    @Test
    void tokenExchange_scopeMapping_mapsKnownScopesAndDropsUnknown() throws Exception {
        var client = registerConfidentialClient();
        var clientId = client[0];
        var clientSecret = client[1];
        var subjectToken = generateTokenA();

        // Request with known and unknown scopes — unknown should be silently dropped
        var result = mockMvc.perform(post("/oauth2/token")
                        .param("grant_type", TOKEN_EXCHANGE_GRANT_TYPE)
                        .param("subject_token", subjectToken)
                        .param("subject_token_type", ACCESS_TOKEN_TYPE)
                        .param("scope", "mcp:read mcp:edit unknown:scope")
                        .param("client_id", clientId)
                        .param("client_secret", clientSecret))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").value(notNullValue()))
                .andReturn();

        // Parse the returned Token-B and verify mapped scopes
        var tokenJson = objectMapper.readTree(result.getResponse().getContentAsString());
        var tokenB = tokenJson.get("access_token").asText();
        var parsed = jwtTokenProvider.parseToken(tokenB);
        assert parsed.isPresent();
        var permissions = parsed.get().permissions();
        assert permissions.contains("READ") : "Expected READ permission from mcp:read mapping";
        assert permissions.contains("EDIT") : "Expected EDIT permission from mcp:edit mapping";
        assert !permissions.contains("unknown:scope") : "Unknown scope should be dropped";
    }

    @Test
    void tokenExchange_invalidSubjectToken_returnsInvalidGrant() throws Exception {
        var client = registerConfidentialClient();
        var clientId = client[0];
        var clientSecret = client[1];

        mockMvc.perform(post("/oauth2/token")
                        .param("grant_type", TOKEN_EXCHANGE_GRANT_TYPE)
                        .param("subject_token", "invalid.jwt.token")
                        .param("subject_token_type", ACCESS_TOKEN_TYPE)
                        .param("client_id", clientId)
                        .param("client_secret", clientSecret))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_grant"));
    }

    @Test
    void tokenExchange_missingRequiredParams_returnsInvalidRequest() throws Exception {
        var client = registerConfidentialClient();
        var clientId = client[0];
        var clientSecret = client[1];

        // Missing subject_token
        mockMvc.perform(post("/oauth2/token")
                        .param("grant_type", TOKEN_EXCHANGE_GRANT_TYPE)
                        .param("subject_token_type", ACCESS_TOKEN_TYPE)
                        .param("client_id", clientId)
                        .param("client_secret", clientSecret))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"));

        // Missing subject_token_type
        mockMvc.perform(post("/oauth2/token")
                        .param("grant_type", TOKEN_EXCHANGE_GRANT_TYPE)
                        .param("subject_token", "some-token")
                        .param("client_id", clientId)
                        .param("client_secret", clientSecret))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"));
    }

    @Test
    void tokenExchange_wrongSubjectTokenType_returnsInvalidRequest() throws Exception {
        var client = registerConfidentialClient();
        var clientId = client[0];
        var clientSecret = client[1];
        var subjectToken = generateTokenA();

        mockMvc.perform(post("/oauth2/token")
                        .param("grant_type", TOKEN_EXCHANGE_GRANT_TYPE)
                        .param("subject_token", subjectToken)
                        .param("subject_token_type", "urn:ietf:params:oauth:token-type:refresh_token")
                        .param("client_id", clientId)
                        .param("client_secret", clientSecret))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"));
    }

    @Test
    void tokenExchange_clientSecretBasicAuth_succeeds() throws Exception {
        var client = registerConfidentialClient();
        var clientId = client[0];
        var clientSecret = client[1];
        var subjectToken = generateTokenA();

        var basicAuth = Base64.getEncoder().encodeToString(
                (clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(post("/oauth2/token")
                        .header("Authorization", "Basic " + basicAuth)
                        .param("grant_type", TOKEN_EXCHANGE_GRANT_TYPE)
                        .param("subject_token", subjectToken)
                        .param("subject_token_type", ACCESS_TOKEN_TYPE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").value(notNullValue()))
                .andExpect(jsonPath("$.issued_token_type").value(ACCESS_TOKEN_TYPE))
                .andExpect(jsonPath("$.token_type").value("Bearer"));
    }

    // --- Gap-filling tests (Group 6) ---

    @Test
    void tokenExchange_allUnknownScopes_returnsTokenWithEmptyPermissions() throws Exception {
        var client = registerConfidentialClient();
        var clientId = client[0];
        var clientSecret = client[1];
        var subjectToken = generateTokenA();

        var result = mockMvc.perform(post("/oauth2/token")
                        .param("grant_type", TOKEN_EXCHANGE_GRANT_TYPE)
                        .param("subject_token", subjectToken)
                        .param("subject_token_type", ACCESS_TOKEN_TYPE)
                        .param("scope", "unknown:one unknown:two")
                        .param("client_id", clientId)
                        .param("client_secret", clientSecret))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").value(notNullValue()))
                .andReturn();

        // Token-B should have empty permissions since no scopes mapped
        var tokenJson = objectMapper.readTree(result.getResponse().getContentAsString());
        var tokenB = tokenJson.get("access_token").asText();
        var parsed = jwtTokenProvider.parseToken(tokenB);
        assert parsed.isPresent();
        assert parsed.get().permissions().isEmpty() : "All unknown scopes should result in empty permissions";
    }

    @Test
    void tokenExchange_noScopeParam_usesSubjectTokenScopesForMapping() throws Exception {
        var client = registerConfidentialClient();
        var clientId = client[0];
        var clientSecret = client[1];
        // Token-A has mcp:read and mcp:edit scopes
        var subjectToken = generateTokenA();

        var result = mockMvc.perform(post("/oauth2/token")
                        .param("grant_type", TOKEN_EXCHANGE_GRANT_TYPE)
                        .param("subject_token", subjectToken)
                        .param("subject_token_type", ACCESS_TOKEN_TYPE)
                        // No scope parameter — should fall back to subject token scopes
                        .param("client_id", clientId)
                        .param("client_secret", clientSecret))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").value(notNullValue()))
                .andReturn();

        var tokenJson = objectMapper.readTree(result.getResponse().getContentAsString());
        var tokenB = tokenJson.get("access_token").asText();
        var parsed = jwtTokenProvider.parseToken(tokenB);
        assert parsed.isPresent();
        var permissions = parsed.get().permissions();
        assert permissions.contains("READ") : "Expected READ from mcp:read in subject token";
        assert permissions.contains("EDIT") : "Expected EDIT from mcp:edit in subject token";
    }

}
