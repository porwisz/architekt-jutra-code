# Implementation Plan: MCP Token Security (RFC 7662 + RFC 8693)

## Overview
Total Steps: 32
Task Groups: 6
Expected Tests: 22-34

## Implementation Steps

### Task Group 1: Backend — JwtTokenProvider Enhancement
**Dependencies:** None
**Estimated Steps:** 5

- [x] 1.0 Complete JwtTokenProvider enhancement
  - [x] 1.1 Write 4 focused tests for JwtTokenProvider changes
    - Test generateOAuth2Token with audience produces JWT containing `aud` claim
    - Test generateOAuth2Token with null audience produces JWT without `aud` claim (backward compat)
    - Test parseRawClaims returns Claims for a valid token (verify sub, scopes, iss, exp, iat accessible)
    - Test parseRawClaims returns empty Optional for invalid/expired token
  - [x] 1.2 Add overloaded `generateOAuth2Token(String username, Set<String> scopes, String issuer, String audience)` method
    - When audience is non-null, set `aud` claim via `.audience().add(audience)`
    - Existing 3-param method delegates to new method with `null` audience
    - File: `src/main/java/pl/devstyle/aj/core/security/JwtTokenProvider.java`
  - [x] 1.3 Add `Optional<Claims> parseRawClaims(String token)` method
    - Parse token using existing jjwt parser
    - Return raw Claims object on success, `Optional.empty()` on any exception
    - Used by introspection endpoint to access exp (Date), iat (Date), iss, aud (Set), sub, scopes
    - File: `src/main/java/pl/devstyle/aj/core/security/JwtTokenProvider.java`
  - [x] 1.4 Ensure Group 1 tests pass
    - Run ONLY the 4 tests written in 1.1

**Acceptance Criteria:**
- The 4 tests pass
- Existing generateOAuth2Token callers are unaffected (backward compatible)
- parseRawClaims correctly exposes all claim types needed by introspection

---

### Task Group 2: Backend — OAuth2IntrospectionFilter (RFC 7662)
**Dependencies:** Group 1
**Estimated Steps:** 7

- [x] 2.0 Complete OAuth2 introspection endpoint
  - [x] 2.1 Write 6 focused tests for introspection endpoint
    - Test active token returns RFC 7662 response: `{active: true, sub, scope (space-delimited), exp, iat, iss, aud, token_type, client_id}`
    - Test expired token returns `{active: false}` with no other fields
    - Test invalid/malformed token returns `{active: false}`
    - Test missing client credentials returns 401 with `invalid_client` error
    - Test wrong client_secret returns 401 with `invalid_client` error
    - Test client_secret_basic authentication (Authorization: Basic header) works for introspection
    - Follow existing OAuth2IntegrationTests pattern: TestContainers + real PostgreSQL, `action_condition_expectedResult` naming
  - [x] 2.2 Create `OAuth2IntrospectionFilter` extending `OncePerRequestFilter`
    - Package: `pl.devstyle.aj.core.oauth2`
    - Match on `POST /oauth2/introspect`
    - Authenticate client via `client_secret_post` (form params) or `client_secret_basic` (Authorization: Basic header)
    - Reuse: client auth pattern from `OAuth2TokenFilter.handleAuthorizationCodeGrant()` lines 117-131
    - New: implement `client_secret_basic` — decode Base64 Authorization header, split on `:` to extract client_id:client_secret
    - Validate token via `JwtTokenProvider.parseRawClaims()`
    - Active response: `{active: true, sub, scope (space-delimited from scopes list), exp (epoch seconds), iat (epoch seconds), iss, aud, token_type: "Bearer", client_id}`
    - Inactive response: `{active: false}` — no other fields (RFC 7662 Section 2.2)
    - Content-Type: `application/json`
    - Reuse: `ObjectMapper` for JSON serialization, `OAuth2Error`/`OAuth2ErrorResponse` for client auth errors
    - File: `src/main/java/pl/devstyle/aj/core/oauth2/OAuth2IntrospectionFilter.java`
  - [x] 2.3 Extract shared client authentication helper
    - Extract client auth logic (client_secret_post + client_secret_basic) into a reusable method or helper class
    - Both introspection and token exchange endpoints use identical client auth
    - Prevents drift between the two endpoints
  - [x] 2.4 Update `SecurityConfiguration`
    - Add `.requestMatchers(HttpMethod.POST, "/oauth2/introspect").permitAll()` alongside existing `/oauth2/token` rule
    - Register `OAuth2IntrospectionFilter` in filter chain after `OAuth2TokenFilter`
    - File: `src/main/java/pl/devstyle/aj/core/security/SecurityConfiguration.java`
  - [x] 2.5 Update `OAuth2MetadataController`
    - Add `introspection_endpoint` field: `baseUrl + "/oauth2/introspect"`
    - Change `Map.of()` to `Map.ofEntries()` (already has 9 entries, will exceed 10-param limit)
    - File: `src/main/java/pl/devstyle/aj/core/oauth2/OAuth2MetadataController.java`
  - [x] 2.6 Ensure Group 2 tests pass
    - Run ONLY the 6 tests written in 2.1

**Acceptance Criteria:**
- The 6 tests pass
- POST /oauth2/introspect returns RFC 7662 compliant responses
- Client auth works via both client_secret_post and client_secret_basic
- Metadata endpoint advertises introspection_endpoint
- Existing OAuth2 flows are unaffected

---

### Task Group 3: Backend — Token Exchange Grant (RFC 8693)
**Dependencies:** Group 1
**Estimated Steps:** 6

- [x] 3.0 Complete token exchange grant handler
  - [x] 3.1 Write 6 focused tests for token exchange
    - Test successful exchange returns Token-B with mapped permissions, correct audience, and RFC 8693 response format
    - Test scope mapping: `mcp:read` maps to `READ`, `mcp:edit` maps to `EDIT`, unknown scopes silently dropped
    - Test invalid/expired subject_token returns `invalid_grant` error
    - Test missing required params (subject_token, subject_token_type) returns `invalid_request` error
    - Test wrong subject_token_type returns `invalid_request` error
    - Test client_secret_basic authentication works for token exchange
    - Follow existing OAuth2IntegrationTests pattern
  - [x] 3.2 Add `handleTokenExchangeGrant()` method to `OAuth2TokenFilter`
    - Add `urn:ietf:params:oauth:grant-type:token-exchange` case to grant_type dispatcher (lines 62-69)
    - Authenticate calling client via shared client auth helper (from step 2.3)
    - Validate required params: `subject_token`, `subject_token_type` (must be `urn:ietf:params:oauth:token-type:access_token`)
    - Optional: `requested_token_type` (default to access_token type)
    - Validate subject_token via `JwtTokenProvider.parseToken()`
    - File: `src/main/java/pl/devstyle/aj/core/oauth2/OAuth2TokenFilter.java`
  - [x] 3.3 Implement scope mapping logic
    - Map MCP scopes to backend permissions: `mcp:read` -> `READ`, `mcp:edit` -> `EDIT`
    - Unknown scopes silently dropped (Token-B only contains mapped permissions)
    - Generate Token-B via `JwtTokenProvider.generateOAuth2Token(sub, mappedScopes, issuer, issuerUrl)` with audience = issuer URL
  - [x] 3.4 Return RFC 8693 Section 2.2 response
    - Response: `{access_token, issued_token_type: "urn:ietf:params:oauth:token-type:access_token", token_type: "Bearer", expires_in: 900}`
    - Reuse: `sendTokenResponse` pattern from existing OAuth2TokenFilter
  - [x] 3.5 Update `OAuth2MetadataController` grant_types_supported
    - Add `urn:ietf:params:oauth:grant-type:token-exchange` to `grant_types_supported` array
    - File: `src/main/java/pl/devstyle/aj/core/oauth2/OAuth2MetadataController.java`
  - [x] 3.6 Ensure Group 3 tests pass
    - Run ONLY the 6 tests written in 3.1

**Acceptance Criteria:**
- The 6 tests pass
- Token exchange produces audience-restricted Token-B with mapped permissions
- Error responses follow RFC 6749 Section 5.2 format
- Metadata endpoint includes token-exchange in grant_types_supported
- Existing OAuth2 grant types (authorization_code, refresh_token) are unaffected

---

### Task Group 4: MCP Server — Introspection + Token Exchange + RestClient
**Dependencies:** Groups 2, 3
**Estimated Steps:** 8

- [x] 4.0 Complete MCP server security filter replacement
  - [x] 4.1 Write 5 focused tests for MCP server security flow
    - Test successful introspection + exchange: SecurityContext populated with correct principal and authorities, Token-B stored as request attribute
    - Test introspection returns active=false: request rejected with 401 + WWW-Authenticate header
    - Test missing Bearer token: request rejected with 401
    - Test introspection succeeds but token exchange fails: returns HTTP 502 (not 401), SecurityContext cleared
    - Test RestClient interceptor attaches Token-B from request attribute as Authorization header
    - Note: these tests will need to mock/stub the backend HTTP calls (use MockRestServiceServer or WireMock)
  - [x] 4.2 Create `TokenExchangeClient` component
    - Package: `pl.devstyle.aj.mcp.security`
    - Accepts Token-A, calls `POST /oauth2/token` with grant_type=token-exchange params + client credentials (client_secret_post)
    - Client credentials from config: `aj.oauth.client-id`, `aj.oauth.client-secret`
    - Parses response to extract `access_token` (Token-B)
    - No caching — exchange per request
    - Uses a dedicated RestClient (not `ajRestClient` bean) to avoid circular dependency
    - File: `mcp-server/src/main/java/pl/devstyle/aj/mcp/security/TokenExchangeClient.java`
  - [x] 4.3 Create `McpIntrospectionFilter` extending `OncePerRequestFilter`
    - Package: `pl.devstyle.aj.mcp.security`
    - Extract Bearer token from Authorization header
    - POST to backend `/oauth2/introspect` with `token`, `client_id`, `client_secret` (client_secret_post)
    - Uses same dedicated RestClient as TokenExchangeClient (not `ajRestClient`)
    - If `active: true`: extract sub and scope, create `UsernamePasswordAuthenticationToken` with authorities (space-split scope, prefix `PERMISSION_`)
    - If `active: false` or HTTP error: return 401 via `McpAuthenticationEntryPoint`
    - After successful introspection: call `TokenExchangeClient` to get Token-B
    - Store Token-B as request attribute: `request.setAttribute("exchanged_token", tokenB)`
    - If exchange fails after introspection: return HTTP 502 immediately, clear SecurityContext in `finally` block
    - No caching of introspection results
    - File: `mcp-server/src/main/java/pl/devstyle/aj/mcp/security/McpIntrospectionFilter.java`
  - [x] 4.4 Replace RestClient interceptor in `RestClientConfig`
    - Remove `JwtForwardingInterceptor` inner class
    - Remove `AccessTokenHolder` dependency from `ajRestClient` bean
    - New interceptor reads Token-B from request attribute via `RequestContextHolder.getRequestAttributes()`
    - Attaches Token-B as `Authorization: Bearer <Token-B>` on outgoing requests
    - Handle 401 from backend: log rejection reason, propagate as `McpToolException.apiError` (no retry)
    - File: `mcp-server/src/main/java/pl/devstyle/aj/mcp/config/RestClientConfig.java`
  - [x] 4.5 Update `SecurityConfig` to register new filter
    - Replace `McpJwtFilter` with `McpIntrospectionFilter` in filter chain
    - File: `mcp-server/src/main/java/pl/devstyle/aj/mcp/config/SecurityConfig.java`
  - [x] 4.6 Add configuration properties to `application.yml`
    - Add `aj.oauth.client-id: ${AJ_OAUTH_CLIENT_ID}`
    - Add `aj.oauth.client-secret: ${AJ_OAUTH_CLIENT_SECRET}`
    - Reuse existing `aj.oauth.server-url` for introspection and exchange base URL
    - File: `mcp-server/src/main/resources/application.yml`
  - [x] 4.7 Ensure Group 4 tests pass
    - Run ONLY the 5 tests written in 4.1

**Acceptance Criteria:**
- The 5 tests pass
- McpIntrospectionFilter validates tokens via backend introspection endpoint
- Token exchange produces Token-B stored as request attribute
- RestClient interceptor uses Token-B for all outgoing backend calls
- Exchange failure after introspection returns 502 (not 401)
- No circular dependencies between beans

---

### Task Group 5: MCP Server — Service Refactoring + Cleanup
**Dependencies:** Group 4
**Estimated Steps:** 7

- [x] 5.0 Complete service refactoring and obsolete code removal
  - [x] 5.1 Write 3 focused tests for refactored services
    - Test ProductService methods work without token parameter (service reads user from SecurityContext)
    - Test CategoryService methods work without token parameter
    - Test AjMcpApplication tool handlers invoke services without extracting token from McpTransportContext
  - [x] 5.2 Refactor `ProductService`
    - Remove `AccessTokenHolder` field and injection
    - Remove `token` parameter from `listProducts()`, `addProduct()`, and other methods
    - Remove `accessTokenHolder.setAccessToken(token)` calls
    - For user identity logging: read from `SecurityContextHolder.getContext().getAuthentication().getName()`
    - File: `mcp-server/src/main/java/pl/devstyle/aj/mcp/service/ProductService.java`
  - [x] 5.3 Refactor `CategoryService`
    - Same changes as ProductService: remove AccessTokenHolder, remove token params
    - File: `mcp-server/src/main/java/pl/devstyle/aj/mcp/service/CategoryService.java`
  - [x] 5.4 Update `AjMcpApplication`
    - Simplify `contextExtractor` — no longer needs to extract Bearer token for service methods
    - Return `McpTransportContext.EMPTY` or remove custom extraction
    - Remove `TOKEN_KEY` constant if no longer used
    - Update `buildTool*()` methods — tool handlers no longer extract token from McpTransportContext
    - File: `mcp-server/src/main/java/pl/devstyle/aj/mcp/AjMcpApplication.java`
  - [x] 5.5 Delete obsolete files
    - Delete `AccessTokenHolder.java`: `mcp-server/src/main/java/pl/devstyle/aj/mcp/security/AccessTokenHolder.java`
    - Delete `McpJwtFilter.java`: `mcp-server/src/main/java/pl/devstyle/aj/mcp/security/McpJwtFilter.java`
    - Delete `AccessTokenHolderTests.java` and `McpJwtFilterTests.java` (test files for deleted classes)
  - [x] 5.6 Update existing tests
    - Update `ToolCallIntegrationTests` to work with new auth flow (mock introspection + exchange)
    - Update `RestClientConfigTests` for new interceptor behavior
    - Remove references to deleted classes from any remaining tests
  - [x] 5.7 Ensure Group 5 tests pass
    - Run ONLY the 3 tests written in 5.1

**Acceptance Criteria:**
- The 3 tests pass
- No remaining references to AccessTokenHolder anywhere in codebase
- No remaining references to McpJwtFilter anywhere in codebase
- Services read user identity from SecurityContext, not method parameters
- Tool handlers do not manage tokens

---

### Task Group 6: Test Review & Gap Analysis
**Dependencies:** All previous groups (1-5)
**Estimated Steps:** 4

- [x] 6.0 Review and fill critical test gaps
  - [x] 6.1 Review tests from previous groups (24 existing tests across groups 1-5)
  - [x] 6.2 Analyze gaps for MCP token security feature only
    - Check: end-to-end flow coverage (introspect + exchange + API call)
    - Check: scope mapping edge cases (empty scopes, all unknown scopes)
    - Check: RFC compliance details (response field names, content types, status codes)
    - Check: metadata endpoint correctness (introspection_endpoint URL, grant_types_supported array)
    - Check: backward compatibility (existing auth code flow, refresh token flow still work)
  - [x] 6.3 Write up to 10 additional strategic tests
    - Focus on integration gaps between groups (e.g., full chain from MCP client to backend)
    - Focus on error boundary conditions not covered
    - Focus on backward compatibility regression
  - [x] 6.4 Run all feature-specific tests (expect 24-34 total)
    - Run all new introspection, token exchange, and MCP security tests
    - Run existing OAuth2IntegrationTests and AuthIntegrationTests as regression
    - All tests must pass

**Acceptance Criteria:**
- All feature tests pass (24-34 total)
- No more than 10 additional tests added
- Existing OAuth2IntegrationTests pass (regression)
- Existing AuthIntegrationTests pass (regression)
- No regressions in MCP server tests

---

## Execution Order

1. Group 1: JwtTokenProvider Enhancement (5 steps) — no dependencies
2. Group 2: OAuth2IntrospectionFilter (7 steps) — depends on Group 1
3. Group 3: Token Exchange Grant (6 steps) — depends on Group 1, parallel with Group 2
4. Group 4: MCP Server Security (8 steps) — depends on Groups 2 and 3
5. Group 5: Service Refactoring + Cleanup (7 steps) — depends on Group 4
6. Group 6: Test Review & Gap Analysis (4 steps) — depends on all previous

Note: Groups 2 and 3 can execute in parallel since they both depend only on Group 1 and modify different files (new OAuth2IntrospectionFilter vs. existing OAuth2TokenFilter + OAuth2MetadataController). The only shared touch point is OAuth2MetadataController — Group 2 adds `introspection_endpoint`, Group 3 adds token-exchange to `grant_types_supported`. If executed sequentially, no conflict. If parallel, the second group must merge with the first group's changes to OAuth2MetadataController.

## Standards Compliance

Follow standards from `.maister/docs/standards/`:
- `global/` — Minimal implementation (no caching, no speculative abstractions), error handling (fail-fast, clear messages), coding style (naming consistency)
- `backend/security.md` — JWT authentication pattern, centralized SecurityFilterChain authorization
- `backend/api.md` — RESTful principles for new endpoints
- `backend/models.md` — JPA patterns if RegisteredClientEntity changes needed
- `testing/backend-testing.md` — TestContainers + real PostgreSQL, integration-first, `*Tests` suffix, `action_condition_expectedResult` naming, 2-8 tests per feature group
- `global/commenting.md` — Let code speak, comment only non-obvious logic (RFC reference links are acceptable)

## Notes

- **Test-Driven**: Each group starts with 2-8 tests before implementation
- **Run Incrementally**: Only new tests after each group — do NOT run entire test suite until Group 6
- **Mark Progress**: Check off steps as completed in this file
- **Reuse First**: Prioritize existing components listed in spec (client auth pattern, sendError/sendTokenResponse, OAuth2Error enum, McpAuthenticationEntryPoint)
- **Parallel Groups**: Groups 2 and 3 can run in parallel (both depend only on Group 1)
- **Dedicated RestClient**: MCP server introspection/exchange calls must use a separate RestClient from the `ajRestClient` bean to avoid circular dependencies
- **RFC Compliance**: Introspection follows RFC 7662 Section 2.2; Token Exchange follows RFC 8693 Section 2.1/2.2; error responses follow RFC 6749 Section 5.2
