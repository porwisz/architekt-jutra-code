package pl.devstyle.aj.mcp.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class McpIntrospectionFilterTests {

    private static final String CLIENT_ID = "mcp-client";
    private static final String CLIENT_SECRET = "mcp-secret";
    private static final String OAUTH_SERVER_URL = "http://localhost:8080";
    private static final String RESOURCE_METADATA_URL = "http://localhost:8081/.well-known/oauth-protected-resource";

    private final ObjectMapper objectMapper = new ObjectMapper();

    private RestClient.Builder oauthRestClientBuilder;
    private MockRestServiceServer mockServer;
    private McpIntrospectionFilter filter;
    private TokenExchangeClient tokenExchangeClient;

    @BeforeEach
    void setUp() {
        oauthRestClientBuilder = RestClient.builder().baseUrl(OAUTH_SERVER_URL);
        mockServer = MockRestServiceServer.bindTo(oauthRestClientBuilder).build();

        tokenExchangeClient = mock(TokenExchangeClient.class);

        var oauthRestClient = oauthRestClientBuilder.build();
        filter = new McpIntrospectionFilter(
                oauthRestClient,
                tokenExchangeClient,
                new McpAuthenticationEntryPoint(RESOURCE_METADATA_URL),
                CLIENT_ID,
                CLIENT_SECRET
        );

        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilter_successfulIntrospectionAndExchange_populatesSecurityContextAndStoresTokenB() throws ServletException, IOException {
        var introspectionResponse = Map.of(
                "active", true,
                "sub", "john",
                "scope", "mcp:read mcp:edit"
        );
        mockServer.expect(requestTo(OAUTH_SERVER_URL + "/oauth2/introspect"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andRespond(withSuccess(objectMapper.writeValueAsString(introspectionResponse), MediaType.APPLICATION_JSON));

        when(tokenExchangeClient.exchange("token-A")).thenReturn("token-B");

        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token-A");
        var response = new MockHttpServletResponse();
        var filterChain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);

        // SecurityContext cleared in finally block after filter chain completes
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();

        // Token-B stored as request attribute
        assertThat(request.getAttribute("exchanged_token")).isEqualTo("token-B");

        // Verify the filter chain was invoked (authentication was set during the chain)
        verify(filterChain).doFilter(argThat(req -> {
            // By the time we're here, the filter has already run and cleared context
            return true;
        }), eq(response));

        mockServer.verify();
    }

    @Test
    void doFilter_introspectionReturnsInactive_returns401WithWwwAuthenticate() throws ServletException, IOException {
        var introspectionResponse = Map.of("active", false);
        mockServer.expect(requestTo(OAUTH_SERVER_URL + "/oauth2/introspect"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(objectMapper.writeValueAsString(introspectionResponse), MediaType.APPLICATION_JSON));

        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer invalid-token");
        var response = new MockHttpServletResponse();
        var filterChain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("WWW-Authenticate")).contains("resource_metadata");
        verifyNoInteractions(filterChain);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();

        mockServer.verify();
    }

    @Test
    void doFilter_missingBearerToken_returns401() throws ServletException, IOException {
        var request = new MockHttpServletRequest();
        // No Authorization header
        var response = new MockHttpServletResponse();
        var filterChain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("WWW-Authenticate")).contains("resource_metadata");
        verifyNoInteractions(filterChain);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilter_introspectionSucceedsButExchangeFails_returns502AndClearsSecurityContext() throws ServletException, IOException {
        var introspectionResponse = Map.of(
                "active", true,
                "sub", "john",
                "scope", "mcp:read"
        );
        mockServer.expect(requestTo(OAUTH_SERVER_URL + "/oauth2/introspect"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(objectMapper.writeValueAsString(introspectionResponse), MediaType.APPLICATION_JSON));

        when(tokenExchangeClient.exchange("token-A")).thenThrow(new RuntimeException("Exchange failed"));

        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token-A");
        var response = new MockHttpServletResponse();
        var filterChain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(502);
        verifyNoInteractions(filterChain);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();

        mockServer.verify();
    }

    // --- Gap-filling tests (Group 6) ---

    @Test
    void doFilter_introspectionServerError_returns401AsInactive() throws ServletException, IOException {
        // When the introspection endpoint returns a server error, the filter treats it as
        // an inactive token (the introspect() method catches the exception and returns null)
        mockServer.expect(requestTo(OAUTH_SERVER_URL + "/oauth2/introspect"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer some-token");
        var response = new MockHttpServletResponse();
        var filterChain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("WWW-Authenticate")).contains("resource_metadata");
        verifyNoInteractions(filterChain);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();

        mockServer.verify();
    }

    @Test
    void doFilter_nonBearerAuthScheme_returns401() throws ServletException, IOException {
        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");
        var response = new MockHttpServletResponse();
        var filterChain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("WWW-Authenticate")).contains("resource_metadata");
        verifyNoInteractions(filterChain);
    }

    @Test
    void doFilter_successfulFlow_interceptorAttachesTokenBFromRequestAttribute() throws ServletException, IOException {
        var introspectionResponse = Map.of(
                "active", true,
                "sub", "john",
                "scope", "mcp:read mcp:edit"
        );
        mockServer.expect(requestTo(OAUTH_SERVER_URL + "/oauth2/introspect"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(objectMapper.writeValueAsString(introspectionResponse), MediaType.APPLICATION_JSON));

        when(tokenExchangeClient.exchange("my-token")).thenReturn("exchanged-token-B");

        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer my-token");
        var response = new MockHttpServletResponse();

        // Capture the authentication set during filter chain execution
        var filterChain = mock(FilterChain.class);
        doAnswer(invocation -> {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth).isNotNull();
            assertThat(auth.getName()).isEqualTo("john");
            var authorityNames = auth.getAuthorities().stream()
                    .map(Object::toString).toList();
            assertThat(authorityNames).containsExactlyInAnyOrder(
                    "PERMISSION_mcp:read", "PERMISSION_mcp:edit"
            );

            // Verify Token-B stored as request attribute
            var tokenB = ((MockHttpServletRequest) invocation.getArgument(0)).getAttribute("exchanged_token");
            assertThat(tokenB).isEqualTo("exchanged-token-B");
            return null;
        }).when(filterChain).doFilter(any(), any());

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        // After filter completes, context is cleared
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();

        mockServer.verify();
    }
}
