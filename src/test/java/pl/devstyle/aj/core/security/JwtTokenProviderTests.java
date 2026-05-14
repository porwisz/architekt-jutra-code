package pl.devstyle.aj.core.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTests {

    private static final String TEST_SECRET = Base64.getEncoder()
            .encodeToString("this-is-a-test-secret-key-32bytes!".getBytes());
    private static final long EXPIRATION_MS = 86400000L;

    private final JwtTokenProvider provider = new JwtTokenProvider(TEST_SECRET, EXPIRATION_MS);

    @Test
    void generateOAuth2Token_withAudience_producesJwtContainingAudClaim() {
        var token = provider.generateOAuth2Token("alice", Set.of("mcp:read"), "https://issuer.example.com", "https://resource.example.com");

        var claims = provider.parseRawClaims(token);

        assertThat(claims).isPresent();
        assertThat(claims.get().getAudience()).contains("https://resource.example.com");
        assertThat(claims.get().getSubject()).isEqualTo("alice");
        assertThat(claims.get().getIssuer()).isEqualTo("https://issuer.example.com");
    }

    @Test
    void generateOAuth2Token_withNullAudience_producesJwtWithoutAudClaim() {
        var token = provider.generateOAuth2Token("bob", Set.of("mcp:read"), "https://issuer.example.com", null);

        var claims = provider.parseRawClaims(token);

        assertThat(claims).isPresent();
        assertThat(claims.get().getAudience()).isNull();
        assertThat(claims.get().getSubject()).isEqualTo("bob");
    }

    @Test
    void parseRawClaims_validToken_returnsClaimsWithAllFields() {
        var token = provider.generateOAuth2Token("charlie", Set.of("mcp:read", "mcp:edit"), "https://issuer.example.com", "https://resource.example.com");

        var result = provider.parseRawClaims(token);

        assertThat(result).isPresent();
        Claims claims = result.get();
        assertThat(claims.getSubject()).isEqualTo("charlie");
        assertThat(claims.getIssuer()).isEqualTo("https://issuer.example.com");
        assertThat(claims.getIssuedAt()).isNotNull();
        assertThat(claims.getExpiration()).isNotNull();
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
        assertThat(claims.getAudience()).contains("https://resource.example.com");
        assertThat(claims.get("scopes", java.util.List.class)).containsExactlyInAnyOrder("mcp:read", "mcp:edit");
    }

    @Test
    void parseRawClaims_invalidToken_returnsEmpty() {
        var result = provider.parseRawClaims("not-a-valid-jwt-token");

        assertThat(result).isEmpty();
    }
}
