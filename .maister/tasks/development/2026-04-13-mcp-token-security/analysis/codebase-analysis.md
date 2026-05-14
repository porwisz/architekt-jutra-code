# Codebase Analysis Report

**Date**: 2026-04-13
**Task**: Implement RFC 7662 Token Introspection and RFC 8693 Token Exchange to replace token passthrough in MCP server
**Description**: Replace the current trust-and-forward token model in the MCP server with proper token introspection (RFC 7662) at the MCP server and token exchange (RFC 8693) at the backend, eliminating direct passthrough of client tokens to the backend API.
**Analyzer**: codebase-analyzer skill (2 Explore agents: File Discovery + Code Analysis, Context Discovery)

---

## Summary

The codebase has a two-module architecture: a Spring Boot backend (authorization server + resource server) and a separate MCP server. The current MCP server performs **zero token validation** -- it creates a dummy authentication for any request with a Bearer token and blindly forwards that token to the backend. This is a significant security gap. The backend already has a mature OAuth2 filter chain (authorization code, refresh token, DCR, PKCE) and JWT infrastructure that can be extended with introspection and token exchange grant types. The package is `pl.devstyle.aj` (not `dev.aj` as initially reported by agents).

---

## Files Identified

### Primary Files

**src/main/java/pl/devstyle/aj/core/oauth2/OAuth2TokenFilter.java** (283 lines)
- Handles POST /oauth2/token with grant_type dispatcher (authorization_code, refresh_token)
- Must be extended with `urn:ietf:params:oauth:grant-type:token-exchange` grant type

**src/main/java/pl/devstyle/aj/core/security/JwtTokenProvider.java** (120 lines)
- Generates user tokens (permissions claim) and OAuth2 tokens (scopes claim)
- Missing `aud` claim on OAuth2 tokens -- needed for audience-restricted tokens
- HMAC-SHA256 via jjwt 0.12.6; shared secret means MCP server could validate locally, but introspection is the proper approach

**mcp-server/src/main/java/pl/devstyle/aj/mcp/security/McpJwtFilter.java** (47 lines)
- No token validation at all -- creates dummy `UsernamePasswordAuthenticationToken("mcp-user", null, List.of())` for any Bearer token
- Must be replaced with introspection-based filter

**mcp-server/src/main/java/pl/devstyle/aj/mcp/security/AccessTokenHolder.java** (30 lines)
- ThreadLocal storage for forwarding tokens to backend
- Must be replaced with token exchange flow

**mcp-server/src/main/java/pl/devstyle/aj/mcp/config/RestClientConfig.java** (87 lines)
- JwtForwardingInterceptor reads from AccessTokenHolder and sets Bearer auth on outgoing requests
- Must be replaced with interceptor that performs token exchange

**src/main/java/pl/devstyle/aj/core/oauth2/OAuth2MetadataController.java** (85 lines)
- /.well-known/oauth-authorization-server metadata endpoint
- Must add `introspection_endpoint` and `urn:ietf:params:oauth:grant-type:token-exchange` to grant_types_supported

**src/main/java/pl/devstyle/aj/core/security/SecurityConfiguration.java** (168 lines)
- Filter chain ordering and endpoint authorization rules
- Must add /oauth2/introspect to permitAll (or client-auth-only)
- Must register new introspection filter

### Related Files

**mcp-server/src/main/java/pl/devstyle/aj/mcp/AjMcpApplication.java** (80 lines)
- contextExtractor extracts Bearer token into McpTransportContext
- Service methods read token from context and set AccessTokenHolder

**mcp-server/src/main/java/pl/devstyle/aj/mcp/service/ProductService.java** (304 lines)
- Sets AccessTokenHolder before each backend call; must be refactored to not manage tokens manually

**mcp-server/src/main/java/pl/devstyle/aj/mcp/service/CategoryService.java** (97 lines)
- Same AccessTokenHolder pattern as ProductService

**mcp-server/src/main/java/pl/devstyle/aj/mcp/config/SecurityConfig.java** (42 lines)
- Registers McpJwtFilter; must switch to introspection-based filter

**mcp-server/src/main/java/pl/devstyle/aj/mcp/controller/WellKnownController.java** (30 lines)
- /.well-known/oauth-protected-resource metadata; may need resource indicator updates

**src/main/java/pl/devstyle/aj/core/oauth2/OAuth2AuthorizationFilter.java** (199 lines)
- Authorization endpoint with PKCE; unchanged but related context

**src/main/java/pl/devstyle/aj/core/oauth2/PublicClientRegistrationFilter.java** (202 lines)
- DCR; scopes hardcoded to mcp:read/mcp:edit; unchanged but relevant for scope understanding

**src/main/java/pl/devstyle/aj/core/oauth2/AuthorizationCodeService.java** (74 lines)
- In-memory auth code storage; pattern reference for new in-memory services

**src/main/java/pl/devstyle/aj/core/oauth2/RefreshTokenService.java** (86 lines)
- In-memory refresh token storage with rotation; pattern reference

**src/main/java/pl/devstyle/aj/core/oauth2/RegisteredClientEntity.java** (71 lines)
- JPA entity for registered clients; token exchange needs client authentication

**src/main/java/pl/devstyle/aj/core/security/JwtAuthenticationFilter.java** (55 lines)
- Validates Bearer tokens on backend side; must handle audience-restricted tokens

**src/main/java/pl/devstyle/aj/core/oauth2/OAuth2Error.java** (36 lines)
- RFC 6749 error codes; may need new error codes for introspection/exchange failures

---

## Current Functionality

### Token Flow (Current -- Insecure)

```
MCP Client
  |-- Bearer token (OAuth2, issued by backend) -->
MCP Server (McpJwtFilter)
  |-- NO validation, creates dummy auth
  |-- contextExtractor stores token in McpTransportContext
  |-- Service reads token, sets AccessTokenHolder (ThreadLocal)
  |-- JwtForwardingInterceptor reads AccessTokenHolder -->
Backend (JwtAuthenticationFilter)
  |-- Validates JWT signature + expiry
  |-- Maps scopes/permissions to Spring Security authorities
```

### Security Issues

1. **No token validation at MCP server** -- any string after "Bearer " is accepted
2. **Token passthrough** -- the MCP server forwards the exact client token to the backend, meaning the client token has direct backend access
3. **No audience restriction** -- tokens lack `aud` claim, so a token issued for one resource can be used at any
4. **ThreadLocal token management** -- fragile, error-prone pattern in service layer

### Key Components/Functions

- **OAuth2TokenFilter.doFilter()**: Grant type dispatcher at lines 62-69; extensible switch for new grants
- **JwtTokenProvider.generateOAuth2Token()**: Creates OAuth2 tokens with issuer, subject, scopes; missing audience
- **JwtTokenProvider.parseToken()**: Unified parser that checks both "permissions" and "scopes" claims
- **McpJwtFilter.doFilterInternal()**: The security gap -- dummy auth for any Bearer header
- **JwtForwardingInterceptor.intercept()**: Reads ThreadLocal token and sets it on outgoing requests

### Data Flow

OAuth2 tokens are issued via authorization_code grant with PKCE. The token contains: issuer, subject (username), scopes (mcp:read, mcp:edit), iat, exp. No audience claim. The JwtAuthenticationFilter on the backend maps scopes to `PERMISSION_mcp:read` / `PERMISSION_mcp:edit` authorities.

---

## Dependencies

### Backend Imports (What OAuth2 Depends On)

- **jjwt 0.12.6**: JWT creation and validation (HMAC-SHA256)
- **spring-security-oauth2-authorization-server**: RegisteredClient, RegisteredClientRepository
- **spring-boot-starter-security**: Filter chain, authentication framework
- **Spring JPA**: RegisteredClientEntity persistence

### MCP Server Imports (Current)

- **mcp-spring-webmvc 0.18.1**: MCP protocol transport
- **spring-boot-starter-security**: Security filter chain
- **No OAuth2 resource server or client dependencies** -- these must be added for introspection/exchange

### Consumers (What Depends On This)

- **JwtTokenProvider**: AuthController, SecurityConfiguration, OAuth2TokenFilter, test helpers (4+ consumers)
- **OAuth2TokenFilter**: SecurityConfiguration only (1 consumer)
- **AccessTokenHolder**: RestClientConfig, McpJwtFilter, ProductService, CategoryService, tests (5+ consumers)
- **McpJwtFilter**: SecurityConfig, tests (2+ consumers)
- **RegisteredClientEntity/Repository**: OAuth2TokenFilter, OAuth2AuthorizationFilter, PublicClientRegistrationFilter, OAuth2MetadataController (4+ consumers)

**Consumer Count**: ~15 files directly affected
**Impact Scope**: Medium-High -- changes span both modules, touch security filter chains, service layer, and HTTP client configuration

---

## Test Coverage

### Test Files

- **OAuth2IntegrationTests.java**: 6 tests covering auth code flow, PKCE, refresh tokens, DCR
- **AuthIntegrationTests.java**: 9 tests covering JWT auth, permissions mapping
- **SecurityTestHelperTests.java**: 3 tests for mock auth annotation helpers
- **AccessTokenHolderTests.java**: 1 test for ThreadLocal behavior
- **McpJwtFilterTests.java**: 2 tests for token extraction
- **RestClientConfigTests.java**: 1 test for interceptor
- **ToolCallIntegrationTests.java**: MCP tool invocation tests

### Coverage Assessment

- **Test count**: ~22 tests across both modules
- **Gaps**:
  - No token introspection tests (endpoint does not exist yet)
  - No token exchange tests (grant type does not exist yet)
  - No scope enforcement tests at MCP server level
  - No audience validation tests
  - No tests for token exchange error scenarios (expired subject token, insufficient scope)
  - Existing OAuth2IntegrationTests provide a solid pattern for new tests

---

## Coding Patterns

### Naming Conventions

- **Packages**: `pl.devstyle.aj.core.oauth2`, `pl.devstyle.aj.core.security`, `pl.devstyle.aj.mcp.security`
- **Filters**: `*Filter.java` extending `OncePerRequestFilter`
- **Services**: `*Service.java` as Spring `@Component`/`@Service`
- **Tests**: `*Tests.java` suffix (not `Test`), `*IntegrationTests.java` for integration tests

### Architecture Patterns

- **Security**: Custom servlet filters (not Spring Security OAuth2 resource server); filters are manually ordered in SecurityConfiguration
- **OAuth2**: Hand-rolled OAuth2 server using servlet filters rather than Spring Authorization Server auto-config
- **In-memory storage**: ConcurrentHashMap with TTL cleanup for auth codes and refresh tokens
- **HTTP Client**: Spring RestClient with HttpServiceProxyFactory for declarative API interfaces
- **State Management**: ThreadLocal for request-scoped token forwarding (anti-pattern to be removed)
- **Configuration**: @Value injection from application properties

---

## Complexity Assessment

| Factor | Value | Level |
|--------|-------|-------|
| Primary Files | 7 files to modify/create | Medium |
| Related Files | 8+ files affected | Medium |
| Dependencies | jjwt, spring-security, mcp-spring-webmvc | Medium |
| Consumers | ~15 files | Medium-High |
| Test Coverage | 22 tests, major gaps for new features | Medium |
| Cross-module | Changes span both backend and MCP server | High |

### Overall: Moderate-Complex

Two RFCs to implement across two modules. The backend OAuth2 filter chain is hand-rolled (not using Spring Authorization Server auto-config), so new endpoints follow the same manual filter pattern. The MCP server needs new dependencies and a fundamentally different authentication/authorization approach. However, the existing patterns are clear and consistent, reducing ambiguity.

---

## Key Findings

### Strengths
- Clean, hand-rolled OAuth2 filter chain with clear separation of concerns -- easy to extend with new grant types
- Existing patterns (AuthorizationCodeService, RefreshTokenService) provide templates for new services
- Good integration test infrastructure in OAuth2IntegrationTests that can be extended
- Consistent filter ordering in SecurityConfiguration makes it straightforward to add new filters

### Concerns
- **McpJwtFilter performs zero validation** -- this is the core security gap driving this task
- **Token passthrough** means a compromised MCP server leaks user tokens with full backend access
- **No audience claim** on tokens means tokens are not bound to specific resource servers
- **In-memory token storage** (auth codes, refresh tokens) will not survive restarts -- same concern applies to any introspection caching
- **ThreadLocal token pattern** is fragile and couples service code to request handling

### Opportunities
- Token introspection gives the MCP server proper token validation without sharing the JWT secret
- Token exchange enables the principle of least privilege -- MCP server gets a narrower, audience-restricted token for backend calls
- Removing AccessTokenHolder/ThreadLocal pattern simplifies service code significantly
- Adding `aud` claim enables future multi-resource-server scenarios

---

## Impact Assessment

- **Primary changes (new code)**:
  - Backend: New introspection endpoint filter (OAuth2IntrospectionFilter or similar)
  - Backend: Token exchange grant handler in OAuth2TokenFilter
  - Backend: Add `aud` claim to JwtTokenProvider.generateOAuth2Token()
  - MCP Server: New introspection-based authentication filter (replacing McpJwtFilter)
  - MCP Server: Token exchange client service
  - MCP Server: New RestClient interceptor using exchanged tokens

- **Primary changes (modifications)**:
  - Backend: OAuth2MetadataController -- add introspection_endpoint, token-exchange grant
  - Backend: SecurityConfiguration -- add introspection endpoint to permitAll, register filter
  - MCP Server: SecurityConfig -- replace McpJwtFilter
  - MCP Server: RestClientConfig -- replace JwtForwardingInterceptor

- **Removals**:
  - MCP Server: AccessTokenHolder (replaced by token exchange)
  - MCP Server: McpJwtFilter (replaced by introspection filter)
  - MCP Server: JwtForwardingInterceptor (replaced by exchange-based interceptor)

- **Service refactoring**:
  - ProductService, CategoryService -- remove AccessTokenHolder usage
  - AjMcpApplication contextExtractor -- may simplify

- **Test updates**:
  - New: Introspection endpoint tests (active/inactive tokens, client auth, scopes)
  - New: Token exchange tests (valid exchange, scope narrowing, error cases)
  - New: MCP server introspection filter tests
  - Update: ToolCallIntegrationTests for new auth flow
  - Remove: AccessTokenHolderTests, McpJwtFilterTests (replaced classes)

### Risk Level: Medium

The changes are significant in scope (two modules, two RFCs) but follow well-established patterns in the codebase. The hand-rolled OAuth2 filter approach means no framework magic to fight. Primary risk is ensuring backward compatibility -- existing OAuth2 flows (authorization_code, refresh_token) must continue working. The MCP server changes are largely isolated (no external consumers beyond MCP clients). Test coverage for existing OAuth2 flows provides a regression safety net.

---

## Recommendations

### Implementation Strategy

1. **Backend first, MCP server second**: Implement introspection endpoint and token exchange grant on the backend before touching the MCP server. This allows testing each RFC independently.

2. **Extend, don't rewrite**: Add the token-exchange case to the existing grant_type dispatcher in OAuth2TokenFilter (line 62-69). Create a new filter for introspection (POST /oauth2/introspect) following the same OncePerRequestFilter pattern as existing filters.

3. **Add `aud` claim to generateOAuth2Token()**: Accept audience parameter, include in JWT. This is prerequisite for token exchange to produce audience-restricted tokens.

4. **MCP server introspection filter**: Replace McpJwtFilter with a filter that calls POST /oauth2/introspect on the backend. Cache introspection results briefly (30-60s) to avoid per-request round-trips. Use the response to populate Spring Security authentication with proper authorities.

5. **MCP server token exchange**: After introspection validates the client token, perform RFC 8693 token exchange to get a backend-scoped token. The MCP server acts as the client (with its own client_id/secret registered via DCR or pre-configured). Replace JwtForwardingInterceptor with an interceptor that uses the exchanged token.

6. **Remove AccessTokenHolder**: Once token exchange is in place, services no longer need to manually manage tokens. The interceptor handles it transparently.

### Backward Compatibility

- Existing authorization_code and refresh_token flows must not be affected
- The metadata endpoint additions are purely additive
- MCP clients already send Bearer tokens; the MCP server just validates them properly now

### Testing Strategy

- Extend OAuth2IntegrationTests with introspection and token exchange test cases
- Add MCP server integration tests that mock/stub the introspection endpoint
- Test error scenarios: expired tokens, revoked tokens, insufficient scopes, invalid audience

### New Dependencies (MCP Server)

- Consider adding `spring-boot-starter-oauth2-client` or implement introspection/exchange calls with the existing RestClient
- No need for `spring-boot-starter-oauth2-resource-server` if using hand-rolled introspection (consistent with backend pattern)

---

## Next Steps

The orchestrator should invoke gap analysis to identify:
1. Specific RFC 7662 requirements vs current implementation gaps
2. Specific RFC 8693 requirements vs current implementation gaps
3. Client authentication requirements for introspection endpoint
4. MCP server configuration requirements (client credentials, backend URLs)
5. Whether the in-memory storage pattern is acceptable for token exchange tokens or if caching strategy differs
