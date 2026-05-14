package pl.devstyle.aj.core.oauth2;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import pl.devstyle.aj.core.security.JwtTokenProvider;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class OAuth2IntrospectionFilter extends org.springframework.web.filter.OncePerRequestFilter {

    private static final String INTROSPECTION_ENDPOINT = "/oauth2/introspect";

    private final OAuth2ClientAuthenticator clientAuthenticator;
    private final JwtTokenProvider jwtTokenProvider;
    private final ObjectMapper objectMapper;

    public OAuth2IntrospectionFilter(OAuth2ClientAuthenticator clientAuthenticator,
                                     JwtTokenProvider jwtTokenProvider) {
        this.clientAuthenticator = clientAuthenticator;
        this.jwtTokenProvider = jwtTokenProvider;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!(INTROSPECTION_ENDPOINT.equals(request.getRequestURI()) && "POST".equals(request.getMethod()))) {
            filterChain.doFilter(request, response);
            return;
        }

        log.info("Introspection request received");

        try {
            // Authenticate the calling client
            var credentials = clientAuthenticator.extractCredentials(request);
            if (credentials.isEmpty()) {
                sendError(response, OAuth2Error.INVALID_CLIENT, "Client authentication required");
                return;
            }

            var client = clientAuthenticator.authenticate(credentials.get());
            if (client.isEmpty()) {
                sendError(response, OAuth2Error.INVALID_CLIENT, "Client authentication failed");
                return;
            }

            // Validate the token parameter
            String token = request.getParameter("token");
            if (token == null || token.isBlank()) {
                sendInactiveResponse(response);
                return;
            }

            var claimsOpt = jwtTokenProvider.parseRawClaims(token);
            if (claimsOpt.isEmpty()) {
                sendInactiveResponse(response);
                return;
            }

            var claims = claimsOpt.get();

            // Build active introspection response per RFC 7662
            Map<String, Object> introspectionResponse = new LinkedHashMap<>();
            introspectionResponse.put("active", true);
            introspectionResponse.put("sub", claims.getSubject());

            // Convert scopes list to space-delimited string
            @SuppressWarnings("unchecked")
            List<String> scopes = claims.get("scopes", List.class);
            if (scopes != null && !scopes.isEmpty()) {
                introspectionResponse.put("scope", scopes.stream().collect(Collectors.joining(" ")));
            }

            introspectionResponse.put("exp", claims.getExpiration().getTime() / 1000);
            introspectionResponse.put("iat", claims.getIssuedAt().getTime() / 1000);

            if (claims.getIssuer() != null) {
                introspectionResponse.put("iss", claims.getIssuer());
            }

            if (claims.getAudience() != null && !claims.getAudience().isEmpty()) {
                var audience = claims.getAudience();
                introspectionResponse.put("aud", audience.size() == 1 ? audience.iterator().next() : audience);
            }

            introspectionResponse.put("token_type", "Bearer");
            introspectionResponse.put("client_id", credentials.get().clientId());

            sendJsonResponse(response, introspectionResponse);

            log.info("Introspection response | active=true | sub={} | client_id={}",
                    claims.getSubject(), credentials.get().clientId());

        } catch (Exception e) {
            log.error("Introspection endpoint error | reason={}", e.getMessage(), e);
            if (!response.isCommitted()) {
                sendError(response, OAuth2Error.SERVER_ERROR, "Internal server error");
            }
        }
    }

    private void sendInactiveResponse(HttpServletResponse response) throws IOException {
        sendJsonResponse(response, Map.of("active", false));
    }

    private void sendJsonResponse(HttpServletResponse response, Map<String, Object> body) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json");
        byte[] json = objectMapper.writeValueAsBytes(body);
        response.getOutputStream().write(json);
        response.getOutputStream().flush();
    }

    private void sendError(HttpServletResponse response, OAuth2Error error, String description) throws IOException {
        int status = switch (error) {
            case INVALID_CLIENT -> HttpServletResponse.SC_UNAUTHORIZED;
            case UNAUTHORIZED_CLIENT, ACCESS_DENIED -> HttpServletResponse.SC_FORBIDDEN;
            case SERVER_ERROR -> HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
            default -> HttpServletResponse.SC_BAD_REQUEST;
        };
        log.warn("OAuth2 introspection error | error={} | description={} | status={}", error, description, status);
        response.setStatus(status);
        response.setContentType("application/json");
        byte[] body = objectMapper.writeValueAsBytes(OAuth2ErrorResponse.of(error, description));
        response.getOutputStream().write(body);
        response.getOutputStream().flush();
    }
}
