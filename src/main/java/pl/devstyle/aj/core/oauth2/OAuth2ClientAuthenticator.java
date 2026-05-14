package pl.devstyle.aj.core.oauth2;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

/**
 * Shared client authentication for OAuth2 endpoints.
 * Supports client_secret_post (form params) and client_secret_basic (Authorization: Basic header).
 */
@Slf4j
@RequiredArgsConstructor
public class OAuth2ClientAuthenticator {

    private final RegisteredClientRepository registeredClientRepository;
    private final PasswordEncoder passwordEncoder;

    public record ClientCredentials(String clientId, String clientSecret) {}

    /**
     * Extract client credentials from the request using client_secret_post or client_secret_basic.
     *
     * @return extracted credentials, or empty if no client credentials are present
     */
    public Optional<ClientCredentials> extractCredentials(HttpServletRequest request) {
        // Try client_secret_basic first (Authorization: Basic header)
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Basic ")) {
            try {
                String encoded = authHeader.substring("Basic ".length());
                String decoded = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
                int colonIndex = decoded.indexOf(':');
                if (colonIndex > 0) {
                    String clientId = decoded.substring(0, colonIndex);
                    String clientSecret = decoded.substring(colonIndex + 1);
                    return Optional.of(new ClientCredentials(clientId, clientSecret));
                }
            } catch (IllegalArgumentException e) {
                log.warn("Failed to decode Basic auth header");
            }
        }

        // Fall back to client_secret_post (form parameters)
        String clientId = request.getParameter("client_id");
        String clientSecret = request.getParameter("client_secret");
        if (clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank()) {
            return Optional.of(new ClientCredentials(clientId, clientSecret));
        }

        return Optional.empty();
    }

    /**
     * Authenticate a client using the extracted credentials.
     *
     * @return the authenticated RegisteredClient, or empty if authentication fails
     */
    public Optional<RegisteredClient> authenticate(ClientCredentials credentials) {
        RegisteredClient client = registeredClientRepository.findByClientId(credentials.clientId());
        if (client == null) {
            log.warn("Client authentication failed | reason=Client not found | client_id={}", credentials.clientId());
            return Optional.empty();
        }

        if (client.getClientSecret() == null) {
            log.warn("Client authentication failed | reason=Public client cannot use secret auth | client_id={}", credentials.clientId());
            return Optional.empty();
        }

        if (!passwordEncoder.matches(credentials.clientSecret(), client.getClientSecret())) {
            log.warn("Client authentication failed | reason=Invalid client secret | client_id={}", credentials.clientId());
            return Optional.empty();
        }

        return Optional.of(client);
    }
}
