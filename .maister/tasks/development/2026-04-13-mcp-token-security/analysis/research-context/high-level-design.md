# High-Level Design: MCP Token Security

## Design Overview

**Business context**: The MCP server currently violates two normative MUST NOT requirements in the MCP authorization specification by forwarding client tokens unmodified to the backend API. This creates confused deputy vulnerabilities, breaks audit trails, and blocks spec compliance. The backend already has a 70-80% complete OAuth2 Authorization Server, making the fix incremental rather than greenfield.

**Chosen approach**: Implement **RFC 7662 Token Introspection** for validating incoming tokens and **RFC 8693 Token Exchange** for obtaining backend-scoped tokens, deployed as a **big-bang release** (both mechanisms ship together). The MCP server calls the backend's new `/oauth2/introspect` endpoint to verify each client token, then exchanges validated tokens for backend-scoped tokens via the token endpoint. This eliminates token passthrough entirely and centralizes token validation at the Authorization Server. The approach extends the existing custom OAuth2 filter chain rather than migrating to Spring Authorization Server.

**Key decisions:**
- **Token Introspection over local JWT validation** -- centralizes validation at the AS, supports future opaque tokens and immediate revocation, avoids distributing the HMAC signing secret to additional services
- **RFC 8693 Token Exchange for backend calls** -- preserves user identity in backend tokens, enables clean audience separation (mcp-server vs backend-api), follows industry consensus from Solo.io, Stacklok, Curity, and FastMCP
- **Big-bang implementation** -- both introspection and token exchange ship together to achieve full spec compliance from the first deployment, avoiding a partially-compliant intermediate state
- **Extend custom filter chain** -- add new grant type handler and introspection filter to existing `OAuth2TokenFilter` rather than migrating to Spring Authorization Server
- **MCP server as confidential OAuth2 client** -- registered via the existing DCR infrastructure with `client_secret_post` authentication for introspection and token exchange calls

---

## Architecture

### System Context (C4 Level 1)

```
+-------------------+          +-------------------+          +-------------------+
|                   |  Token-A |                   |  Token-B |                   |
|    MCP Client     |--------->|    MCP Server     |--------->|   Backend API     |
| (Claude Desktop,  |  (HTTP)  | (Resource Server) |  (HTTP)  | (Resource Server  |
|  other clients)   |          |                   |          |  + Auth Server)   |
+-------------------+          +-------------------+          +-------------------+
                                       |                             ^
                                       |  POST /oauth2/introspect    |
                                       |  (RFC 7662, HTTP)           |
                                       +---------------------------->|
                                       |                             |
                                       |  POST /oauth2/token         |
                                       |  grant_type=token-exchange  |
                                       |  (RFC 8693, HTTP)           |
                                       +---------------------------->|
```

**Actors and systems:**
- **MCP Client** -- Claude Desktop or other MCP-compatible clients. Obtains Token-A (aud: mcp-server) from the backend OAuth2 AS via authorization code + PKCE flow.
- **MCP Server** -- Spring Boot application acting as an OAuth2 Resource Server. Validates Token-A via introspection, exchanges it for Token-B, calls Backend API with Token-B.
- **Backend API** -- Spring Boot application serving as both the OAuth2 Authorization Server (issuing tokens, introspection, token exchange) and the Resource Server (validating Token-B for API access).

### Container Overview (C4 Level 2)

```
+-----------------------------------------------------------------------+
|  MCP Server (Spring Boot)                                             |
|                                                                       |
|  +-------------------------+    +------------------------------+      |
|  | SecurityFilterChain     |    | Token Exchange Client        |      |
|  | (opaque token           |--->| (calls POST /oauth2/token    |      |
|  |  introspection)         |    |  with grant_type=            |      |
|  +-------------------------+    |  token-exchange)             |      |
|              |                  +------------------------------+      |
|              v                             |                          |
|  +-------------------------+               v                          |
|  | MCP Tool Handlers       |    +------------------------------+      |
|  | (process MCP requests)  |    | RestClient                   |      |
|  +-------------------------+    | (uses Token-B for            |      |
|                                 |  backend API calls)          |      |
|                                 +------------------------------+      |
+-----------------------------------------------------------------------+
         |  Introspect (HTTP POST)         |  API calls (HTTP + Token-B)
         |  Token Exchange (HTTP POST)     |
         v                                 v
+-----------------------------------------------------------------------+
|  Backend API (Spring Boot)                                            |
|                                                                       |
|  +-----------------------------+   +-------------------------------+  |
|  | OAuth2 Authorization Server |   | API Endpoints                 |  |
|  |                             |   | (SecurityFilterChain          |  |
|  | +-------------------------+ |   |  validates Token-B)           |  |
|  | | OAuth2IntrospectionFilter| |   +-------------------------------+  |
|  | | POST /oauth2/introspect | |                                      |
|  | +-------------------------+ |   +-------------------------------+  |
|  | | OAuth2TokenFilter        | |   | JwtTokenProvider              |  |
|  | | (authorization_code,     | |   | (generates tokens with        |  |
|  | |  refresh_token,          | |   |  aud claim)                   |  |
|  | |  token-exchange)         | |   +-------------------------------+  |
|  | +-------------------------+ |                                      |
|  | | ClientAuthenticator      | |   +-------------------------------+  |
|  | | (authenticates MCP       | |   | DatabaseRegisteredClient-     |  |
|  | |  server for intro-       | |   |   Repository                  |  |
|  | |  spection + exchange)    | |   | (stores MCP server client     |  |
|  | +-------------------------+ |   |  registration)                |  |
|  +-----------------------------+   +-------------------------------+  |
+-----------------------------------------------------------------------+
```

**Container responsibilities:**

| Container | Responsibility |
|-----------|---------------|
| **SecurityFilterChain (MCP)** | Intercepts incoming requests, extracts Bearer token, calls backend introspection endpoint to validate, populates SecurityContext with authenticated principal |
| **Token Exchange Client** | After introspection confirms token validity, exchanges Token-A for Token-B via RFC 8693 grant at the backend token endpoint |
| **MCP Tool Handlers** | Process MCP protocol requests (tool calls, resource reads) using the authenticated user context |
| **RestClient** | HTTP client configured with Token-B interceptor for all backend API calls |
| **OAuth2IntrospectionFilter** | New backend filter at POST /oauth2/introspect implementing RFC 7662; authenticates the calling client, validates the subject token, returns active/metadata |
| **OAuth2TokenFilter (extended)** | Existing filter extended with token-exchange grant type; validates subject_token, issues Token-B with backend-api audience |
| **ClientAuthenticator** | Shared logic for authenticating the MCP server's client credentials on introspection and token exchange requests |
| **JwtTokenProvider** | Extended to include `aud` claim in all generated tokens |
| **DatabaseRegisteredClientRepository** | Stores MCP server's confidential client registration alongside existing public client registrations |

---

## Key Components

| Component | Purpose | Responsibilities | Key Interfaces | Dependencies |
|-----------|---------|-----------------|----------------|--------------|
| **OAuth2IntrospectionFilter** | Centralized token validation endpoint (RFC 7662) | Accept POST with `token` param; authenticate calling client; validate token signature, expiry, audience; return JSON `{active: true/false, sub, scope, exp, iat}` | HTTP POST `/oauth2/introspect` (inbound from MCP server) | JwtTokenProvider (for token parsing), ClientAuthenticator, SecurityConfiguration (endpoint access rules) |
| **Token Exchange Grant Handler** | Issue backend-scoped tokens from MCP client tokens (RFC 8693) | Validate `subject_token`; verify subject_token_type; map MCP scopes to backend permissions; generate Token-B with `aud: backend-api`; return standard token response | HTTP POST `/oauth2/token` with `grant_type=urn:ietf:params:oauth:grant-type:token-exchange` | JwtTokenProvider, ClientAuthenticator, DatabaseRegisteredClientRepository |
| **MCP Server Security Config** | Configure MCP server as opaque-token resource server | Configure `oauth2ResourceServer().opaqueToken().introspectionUri(...)` or custom introspection client; set client credentials for introspection calls; map introspection response to authorities | Spring Security DSL (internal) | Backend introspection endpoint |
| **Token Exchange Client (MCP)** | Obtain backend-scoped tokens from the MCP server side | After successful introspection, POST to token endpoint with subject_token; cache exchanged tokens; handle token refresh/expiry | HTTP POST to backend `/oauth2/token` (outbound) | MCP server client credentials, backend token endpoint |
| **RestClient (MCP)** | HTTP client for backend API calls using exchanged tokens | Attach Token-B as Bearer header; handle 401 responses by re-exchanging | HTTP requests to backend API endpoints | Token Exchange Client |
| **JwtTokenProvider (extended)** | Generate audience-scoped JWT tokens | Add `aud` claim parameterized by context (mcp-server for auth code tokens, backend-api for exchanged tokens) | Internal (called by OAuth2TokenFilter, Token Exchange Grant Handler) | JWT signing secret |
| **OAuth2MetadataController (extended)** | Advertise introspection endpoint in AS metadata | Add `introspection_endpoint` to `/.well-known/oauth-authorization-server` response | HTTP GET (discovery) | None |

---

## Data Flow

### Primary Flow: MCP Client Request to Backend API

```
MCP Client                    MCP Server                      Backend API
    |                             |                                |
    |  1. MCP request +           |                                |
    |     Token-A (Bearer)        |                                |
    |  (aud: mcp-server)          |                                |
    |---------------------------->|                                |
    |                             |  2. POST /oauth2/introspect    |
    |                             |     token=Token-A              |
    |                             |     client_id=mcp-server       |
    |                             |     client_secret=***          |
    |                             |------------------------------->|
    |                             |                                |
    |                             |  3. {active: true, sub: user,  |
    |                             |      scope: "mcp:read          |
    |                             |      mcp:edit", exp: ...}      |
    |                             |<-------------------------------|
    |                             |                                |
    |                             |  4. POST /oauth2/token         |
    |                             |     grant_type=urn:ietf:       |
    |                             |       params:oauth:grant-type: |
    |                             |       token-exchange           |
    |                             |     subject_token=Token-A      |
    |                             |     subject_token_type=        |
    |                             |       urn:ietf:params:oauth:   |
    |                             |       token-type:access_token  |
    |                             |     requested_token_type=      |
    |                             |       urn:ietf:params:oauth:   |
    |                             |       token-type:access_token  |
    |                             |     client_id=mcp-server       |
    |                             |     client_secret=***          |
    |                             |------------------------------->|
    |                             |                                |
    |                             |  5. {access_token: Token-B,    |
    |                             |      token_type: Bearer,       |
    |                             |      issued_token_type: ...,   |
    |                             |      expires_in: 3600}         |
    |                             |<-------------------------------|
    |                             |                                |
    |                             |  6. GET /api/resource           |
    |                             |     Authorization: Bearer      |
    |                             |       Token-B                  |
    |                             |------------------------------->|
    |                             |                                |
    |                             |  7. API response               |
    |                             |<-------------------------------|
    |                             |                                |
    |  8. MCP response            |                                |
    |<----------------------------|                                |
```

### Token Lifecycle

1. **Token-A issuance**: MCP Client obtains Token-A from Backend OAuth2 AS via authorization code + PKCE flow. Token-A has `aud: mcp-server` and scopes `mcp:read`, `mcp:edit`.
2. **Token-A validation**: MCP Server sends Token-A to backend introspection endpoint. Backend verifies signature, expiry, and returns metadata.
3. **Token-B issuance**: MCP Server exchanges Token-A for Token-B via token exchange. Token-B has `aud: backend-api` with permissions mapped from MCP scopes.
4. **Token-B usage**: MCP Server uses Token-B for all backend API calls. Token-B can be cached for its lifetime and reused across multiple API calls within the same MCP request or session.
5. **Token-B expiry**: When Token-B expires, the MCP Server re-exchanges using Token-A (if still valid) or the request fails with 401 back to the MCP Client.

### Data Transformation

| Stage | Token | Audience | Scopes/Permissions | Subject |
|-------|-------|----------|-------------------|---------|
| Client to MCP Server | Token-A | mcp-server | mcp:read, mcp:edit | user-id |
| MCP Server to Backend | Token-B | backend-api | Mapped backend permissions | user-id (preserved) |

---

## Integration Points

| Integration | Direction | Protocol | Authentication | Description |
|-------------|-----------|----------|---------------|-------------|
| MCP Client to MCP Server | Inbound | HTTP (MCP protocol) | Bearer Token-A | MCP protocol requests with OAuth2 access token |
| MCP Server to Introspection | Outbound | HTTP POST | client_secret_post | RFC 7662 token validation; MCP server authenticates as confidential client |
| MCP Server to Token Exchange | Outbound | HTTP POST | client_secret_post | RFC 8693 token exchange; same client credentials as introspection |
| MCP Server to Backend API | Outbound | HTTP | Bearer Token-B | Standard API calls using the exchanged backend-scoped token |
| MCP Client to OAuth2 AS | Outbound (existing) | HTTP | Authorization code + PKCE | Existing flow for obtaining Token-A; unchanged except Token-A now includes `aud` claim |
| AS Metadata Discovery | Inbound (existing) | HTTP GET | None | `/.well-known/oauth-authorization-server` extended with `introspection_endpoint` |

### Existing Infrastructure Leveraged

| Component | Current State | Change Needed |
|-----------|--------------|---------------|
| OAuth2TokenFilter | Handles authorization_code, refresh_token | Add token-exchange grant type |
| JwtTokenProvider | Generates tokens without aud | Add aud claim parameter |
| DatabaseRegisteredClientRepository | Stores public client registrations | Store MCP server confidential client |
| SecurityConfiguration | Protects API endpoints | Add /oauth2/introspect endpoint rules |
| OAuth2MetadataController | Serves AS metadata | Add introspection_endpoint field |
| PublicClientRegistrationFilter | DCR for public clients | No change (MCP server registered separately) |
| MCP scope definitions | mcp:read, mcp:edit mapped to PERMISSION_ | No change |

---

## Design Decisions

| ID | Decision | Rationale | ADR Link |
|----|----------|-----------|----------|
| ADR-001 | Token Introspection (RFC 7662) over local JWT validation | Centralizes validation, avoids secret distribution, supports future opaque tokens | [ADR-001](../outputs/decision-log.md#adr-001-token-validation-via-introspection-rfc-7662) |
| ADR-002 | RFC 8693 Token Exchange for backend calls | Preserves user identity, clean audience separation, industry consensus | [ADR-002](../outputs/decision-log.md#adr-002-backend-token-acquisition-via-rfc-8693-token-exchange) |
| ADR-003 | Big-bang implementation (both mechanisms together) | Full compliance from first deployment, no partially-compliant window | [ADR-003](../outputs/decision-log.md#adr-003-big-bang-implementation-phasing) |
| ADR-004 | Extend custom filter chain rather than adopt Spring Authorization Server | Avoids large migration, leverages existing working code, incremental change | [ADR-004](../outputs/decision-log.md#adr-004-extend-custom-oauth2-filter-chain) |

---

## Concrete Examples

### Example 1: Successful MCP Tool Call with Token Exchange

**Given** a user authenticated via Claude Desktop with a valid Token-A (aud: mcp-server, scopes: mcp:read mcp:edit, exp: future),
**When** the MCP client sends a `tools/call` request to list projects,
**Then** the MCP server:
1. Extracts Token-A from the Authorization header
2. Calls POST /oauth2/introspect with Token-A and receives `{active: true, sub: "user-123", scope: "mcp:read mcp:edit"}`
3. Populates SecurityContext with user-123's identity and permissions
4. Exchanges Token-A for Token-B (aud: backend-api) via POST /oauth2/token
5. Calls GET /api/projects with Bearer Token-B
6. Returns project list to the MCP client

### Example 2: Expired Token Rejected at Introspection

**Given** a user's Token-A has expired (exp < now),
**When** the MCP client sends a `tools/call` request,
**Then** the MCP server:
1. Extracts Token-A from the Authorization header
2. Calls POST /oauth2/introspect with Token-A and receives `{active: false}`
3. Returns HTTP 401 with `WWW-Authenticate: Bearer error="invalid_token", resource_metadata="/.well-known/oauth-protected-resource"` to the MCP client
4. No backend API call is made
5. No token exchange is attempted

### Example 3: Token Replay Across Services Prevented

**Given** an attacker obtains Token-B (aud: backend-api) from a network capture,
**When** the attacker presents Token-B to the MCP server as if it were Token-A,
**Then** the MCP server:
1. Sends Token-B to the introspection endpoint
2. The backend introspection endpoint checks the token's audience and finds `aud: backend-api` (not `mcp-server`)
3. Returns `{active: false}` because the token was not issued for the MCP server audience
4. MCP server rejects the request with HTTP 401

---

## Out of Scope

The following items are explicitly **not addressed** by this design:

1. **MCP SDK upgrade (0.18.1 to 1.1.1)** -- Major version boundary with unknown breaking changes. Tracked as a separate task. This design works with the current SDK version.

2. **Migration to asymmetric keys (RSA/EC)** -- Would eliminate HMAC secret management and enable JWKS discovery. Deferred as a stretch goal; the introspection approach actually reduces the urgency since the MCP server no longer needs the signing secret.

3. **Spring Authorization Server migration** -- Replacing the custom OAuth2 filter chain. Significant effort with limited incremental value for pre-alpha. Deferred.

4. **In-memory auth storage migration** -- Authorization codes and refresh tokens stored in memory are lost on restart. Separate production concern.

5. **Granular MCP scope refinement** -- Current scopes (mcp:read, mcp:edit) are acceptable for pre-alpha. Tool-level scopes deferred.

6. **Token caching strategy** -- The design assumes Token-B can be cached but does not prescribe a specific caching mechanism. Implementation detail for the specification phase.

7. **Rate limiting on introspection/exchange endpoints** -- Important for production but not designed here.

8. **Protected Resource Metadata content verification** -- The well-known endpoint exists but content has not been verified against RFC 9728. Low priority.

---

## Success Criteria

1. **No token passthrough** -- The MCP server never forwards Token-A to the backend API. All backend calls use Token-B obtained via token exchange.

2. **Token validation at MCP server** -- Every incoming request is validated via introspection before processing. Invalid, expired, or wrong-audience tokens are rejected with HTTP 401.

3. **User identity preserved** -- Token-B contains the same `sub` claim as Token-A. Backend audit logs and per-user RBAC continue to function correctly.

4. **Audience separation enforced** -- Token-A has `aud: mcp-server`, Token-B has `aud: backend-api`. Tokens are rejected when presented to the wrong service.

5. **Existing OAuth2 flows unaffected** -- Authorization code + PKCE, refresh token, and DCR flows continue to work without modification.

6. **RFC compliance** -- Introspection endpoint conforms to RFC 7662 response format. Token exchange conforms to RFC 8693 request/response format. AS metadata includes `introspection_endpoint`.
