# Requirements: MCP Token Security

## Initial Description
Replace the token passthrough anti-pattern in the MCP server with RFC 7662 Token Introspection and RFC 8693 Token Exchange. Big-bang implementation — both mechanisms ship together.

## Research-Informed Requirements

### From Research (ADRs)
- ADR-001: Token Introspection (RFC 7662) over local JWT validation
- ADR-002: RFC 8693 Token Exchange for backend calls
- ADR-003: Big-bang implementation (both mechanisms together)
- ADR-004: Extend custom filter chain (not migrate to Spring Auth Server)

### From Gap Analysis Decisions
- Introspection endpoint: permitAll at filter chain, client auth inside filter (consistent with /oauth2/token pattern)
- MCP server client registration: environment variables (client_id + client_secret)
- Audience enforcement: MCP server enforces, backend permissive
- Introspection caching: none (every request calls introspect)
- Services: read user identity from SecurityContext for logging
- Exchange audience value: backend issuer URL

### Scope Mapping
- mcp:read → PERMISSION_READ (matches existing SecurityConfiguration)
- mcp:edit → PERMISSION_EDIT (matches existing SecurityConfiguration)

### Auth Methods
- Introspection endpoint supports: client_secret_post + client_secret_basic (matching token endpoint)

## Functional Requirements

### Backend Changes

1. **OAuth2IntrospectionFilter** (new) — RFC 7662 compliant endpoint at POST /oauth2/introspect
   - Authenticate calling client (client_secret_post or client_secret_basic)
   - Parse and validate the submitted token via JwtTokenProvider
   - Check audience claim matches the requesting resource server
   - Return JSON: {active: true/false, sub, scope, exp, iat, iss, aud, token_type, client_id}
   - Return {active: false} for expired, invalid, or wrong-audience tokens

2. **Token Exchange Grant Handler** (extend OAuth2TokenFilter) — RFC 8693 compliant
   - New grant type: urn:ietf:params:oauth:grant-type:token-exchange
   - Accept: subject_token, subject_token_type, requested_token_type
   - Validate subject_token (parse, check signature, check not expired)
   - Authenticate calling client (MCP server) via client_secret
   - Issue Token-B with aud=backend-issuer-url, mapped permissions (mcp:read→READ, mcp:edit→EDIT)
   - Preserve user identity (sub claim) from subject_token
   - Return standard OAuth2 token response with issued_token_type

3. **JwtTokenProvider Enhancement** — Add audience (aud) claim
   - New parameter: audience in generateOAuth2Token()
   - OAuth2 tokens issued by auth code flow get aud=mcp-server (or as specified by client)
   - Tokens issued by token exchange get aud=backend-issuer-url
   - parseToken() does NOT enforce audience (backend remains permissive)

4. **OAuth2MetadataController Updates**
   - Add introspection_endpoint to /.well-known/oauth-authorization-server
   - Add urn:ietf:params:oauth:grant-type:token-exchange to grant_types_supported

5. **SecurityConfiguration Updates**
   - Add /oauth2/introspect to permitAll (same pattern as /oauth2/token)
   - Register OAuth2IntrospectionFilter in filter chain (after OAuth2TokenFilter)

### MCP Server Changes

6. **Replace McpJwtFilter** with introspection-based validation
   - Extract Bearer token from Authorization header
   - Call backend POST /oauth2/introspect with token + MCP server client credentials
   - If active: true → populate SecurityContext with user identity and scopes
   - If active: false → return 401 with WWW-Authenticate header
   - No caching — every request calls introspect

7. **Add Token Exchange Client**
   - After successful introspection, exchange Token-A for Token-B
   - POST to backend /oauth2/token with grant_type=token-exchange, subject_token=Token-A
   - Authenticate with MCP server client credentials
   - Store Token-B for use by RestClient

8. **Replace RestClient Configuration**
   - Remove JwtForwardingInterceptor and AccessTokenHolder
   - New interceptor uses Token-B from token exchange (not the original Token-A)
   - Handle 401 from backend by re-exchanging token

9. **Refactor Services** (ProductService, CategoryService)
   - Remove AccessTokenHolder injection and setAccessToken() calls
   - Remove token parameter from service methods
   - Read user identity from SecurityContextHolder when needed for logging
   - Tool handlers no longer pass token to service methods

10. **Update AjMcpApplication**
    - contextExtractor may still extract token for use by the new introspection filter
    - OR: let the security filter chain handle token extraction directly

11. **Configuration Updates**
    - application.yml: add aj.oauth.client-id, aj.oauth.client-secret for MCP server
    - pom.xml: no new dependencies needed (use RestClient for HTTP calls to introspect/exchange)

## Non-Functional Requirements
- Existing OAuth2 flows (auth code, refresh token, DCR) MUST NOT break
- Existing OAuth2IntegrationTests (6 tests) and AuthIntegrationTests (9 tests) must pass
- MCP client experience unchanged — clients continue sending Bearer tokens
- New integration tests for introspection and token exchange endpoints
- RFC 7662 compliant introspection response format
- RFC 8693 compliant token exchange request/response format

## Success Criteria
1. No token passthrough — MCP server never forwards Token-A to backend
2. Token validation at MCP server — invalid tokens rejected with 401
3. User identity preserved — Token-B contains same sub claim as Token-A
4. Audience separation — Token-A has aud:mcp-server, Token-B has aud:backend
5. Existing flows unaffected — auth code, refresh token, DCR work as before
6. RFC compliance — introspection and token exchange conform to specs
