package pl.devstyle.aj.mcp.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Exchanges Token-A (user's access token) for Token-B (backend-scoped token)
 * via RFC 8693 Token Exchange against the backend /oauth2/token endpoint.
 * No caching -- exchange per request.
 */
@Slf4j
public class TokenExchangeClient {

    private static final String GRANT_TYPE = "urn:ietf:params:oauth:grant-type:token-exchange";
    private static final String TOKEN_TYPE = "urn:ietf:params:oauth:token-type:access_token";

    private final RestClient oauthRestClient;
    private final String clientId;
    private final String clientSecret;
    private final ObjectMapper objectMapper;

    public TokenExchangeClient(RestClient oauthRestClient, String clientId, String clientSecret) {
        this.oauthRestClient = oauthRestClient;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Exchange Token-A for Token-B via the backend token endpoint.
     *
     * @param subjectToken the user's access token (Token-A)
     * @return the exchanged access token (Token-B)
     * @throws TokenExchangeException if the exchange fails
     */
    public String exchange(String subjectToken) {
        var params = new LinkedMultiValueMap<String, String>();
        params.add("grant_type", GRANT_TYPE);
        params.add("subject_token", subjectToken);
        params.add("subject_token_type", TOKEN_TYPE);
        params.add("client_id", clientId);
        params.add("client_secret", clientSecret);

        try {
            var responseBody = oauthRestClient.post()
                    .uri("/oauth2/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(params)
                    .retrieve()
                    .body(String.class);

            var json = objectMapper.readTree(responseBody);
            var accessToken = json.path("access_token");
            if (accessToken.isMissingNode() || accessToken.isNull()) {
                throw new TokenExchangeException("Token exchange response missing access_token");
            }
            return accessToken.asText();
        } catch (TokenExchangeException e) {
            throw e;
        } catch (RestClientException e) {
            log.error("Token exchange HTTP call failed", e);
            throw new TokenExchangeException("Token exchange failed: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Token exchange processing failed", e);
            throw new TokenExchangeException("Token exchange failed: " + e.getMessage(), e);
        }
    }

    public static class TokenExchangeException extends RuntimeException {
        public TokenExchangeException(String message) {
            super(message);
        }

        public TokenExchangeException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
