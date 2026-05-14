# Decision Log

## ADR-001: Token Validation via Introspection (RFC 7662)

### Status
Accepted

### Context
The MCP server must validate incoming access tokens before processing requests. Two primary mechanisms exist: local JWT validation (decode and verify the token using a shared signing secret) and token introspection (call the Authorization Server to verify the token). The backend currently uses HMAC-SHA256 (symmetric) signing for JWTs. Local validation would require distributing this signing secret to the MCP server. The project is in pre-alpha with a single backend but anticipates growth.

### Decision Drivers
- Avoid distributing the HMAC signing secret to additional services (security principle of least privilege)
- Centralize token validation logic at the Authorization Server for consistent behavior
- Support future migration to opaque tokens or asymmetric keys without MCP server changes
- Enable immediate token revocation (introspection checks current state, not just token claims)

### Considered Options
1. **Spring OAuth2 Resource Server with shared HMAC secret** (Alternative 1A) -- Local JWT validation using `NimbusJwtDecoder.withSecretKey()` with the same secret the backend uses
2. **Token Introspection endpoint on backend** (Alternative 1B) -- MCP server calls RFC 7662 introspection endpoint for every request
3. **mcp-security community library** (Alternative 1C) -- Use `spring-ai-community/mcp-security` with custom JwtDecoder override

### Decision Outcome
Chosen option: **Token Introspection (Option 2)**, because it avoids distributing the signing secret to the MCP server, centralizes validation at the Authorization Server, and positions the architecture for future changes (opaque tokens, asymmetric keys) without requiring MCP server modifications. The added network round-trip per request is acceptable for a pre-alpha system with a single backend on the same network.

### Consequences

#### Good
- MCP server never holds the JWT signing secret -- reduced blast radius if MCP server is compromised
- Token revocation is immediately effective (introspection checks live state)
- Future migration to opaque tokens or asymmetric keys requires zero MCP server changes
- Consistent validation logic -- all token validation happens in one place (the AS)
- The introspection endpoint is reusable by any future service that needs to validate tokens

#### Bad
- Adds a network round-trip per MCP request (latency penalty, typically single-digit milliseconds on same-network)
- Requires implementing a new `/oauth2/introspect` endpoint on the backend (new filter in the custom OAuth2 chain)
- Creates runtime coupling -- MCP server cannot validate tokens if the backend is down
- More implementation effort than local JWT validation for pre-alpha

---

## ADR-002: Backend Token Acquisition via RFC 8693 Token Exchange

### Status
Accepted

### Context
The MCP specification forbids passing through the client's token to downstream services. The MCP server needs a separate token scoped to the backend API for making API calls. Several mechanisms exist: token exchange (RFC 8693), client credentials with user context header, validated token forwarding (still passthrough), and internal API keys.

### Decision Drivers
- Preserve user identity in backend API calls (audit trail, per-user RBAC)
- Comply with MCP spec's MUST NOT passthrough requirement
- Use standards-based approach (RFC) rather than ad-hoc patterns
- Leverage existing backend OAuth2 infrastructure

### Considered Options
1. **RFC 8693 Token Exchange** (Alternative 2A) -- Exchange Token-A for Token-B via the token endpoint
2. **Client Credentials + User Context Header** (Alternative 2B) -- MCP server gets its own token, passes user identity as custom header
3. **Validated Token Forward** (Alternative 2C) -- Continue forwarding validated Token-A (still violates spec)
4. **Internal API Key** (Alternative 2D) -- Static API key bypassing OAuth2

### Decision Outcome
Chosen option: **RFC 8693 Token Exchange (Option 1)**, because it is the only option that both preserves user identity cryptographically bound in the token AND fully complies with the MCP spec's no-passthrough requirement. It is recommended by Solo.io, Stacklok, Curity, and FastMCP for the MCP proxy use case. The backend's `OAuth2TokenFilter` already handles multiple grant types and can be extended incrementally.

### Consequences

#### Good
- Full MCP spec compliance -- no token passthrough
- User identity preserved in Token-B (sub claim carried forward)
- Clean audience separation -- Token-A (aud: mcp-server) vs Token-B (aud: backend-api) prevents cross-service replay
- Standard RFC pattern supported by Spring ecosystem (GA in Spring Authorization Server 1.3+)
- Backend audit logs correctly attribute actions to the original user

#### Bad
- Medium implementation effort -- new grant type handler in the custom OAuth2 filter chain
- Adds a token exchange round-trip (can be mitigated by caching Token-B for its lifetime)
- Custom implementation required since backend uses custom filters, not Spring Authorization Server's built-in token exchange
- MCP server must be registered as a confidential OAuth2 client (client_id + client_secret management)

---

## ADR-003: Big-Bang Implementation Phasing

### Status
Accepted

### Context
The research identified two main phases of work: token validation (introspection) and token exchange. These could be implemented incrementally (validation first, exchange later) or together in a single release. The research recommended a two-phase approach for risk reduction, but the user selected big-bang to achieve full compliance immediately.

### Decision Drivers
- Achieve full MCP spec compliance from the first deployment
- Avoid a partially-compliant intermediate state (validated passthrough is still passthrough)
- The team has dedicated time and confidence in the OAuth2 implementation
- Both mechanisms are well-understood with high-confidence research findings

### Considered Options
1. **Big-bang** (Alternative 3A) -- Implement introspection and token exchange together
2. **Two-phase** (Alternative 3B) -- Validation first (hours), then token exchange (days), separated by a deployment boundary
3. **Three-phase** (Alternative 3C) -- Validation, then exchange, then SDK upgrade

### Decision Outcome
Chosen option: **Big-bang (Option 1)**, because the user prefers full spec compliance from the first deployment. The introspection endpoint and token exchange grant handler are independent backend changes that can be developed in parallel and tested together. This avoids the risk of Phase 2 being deferred, which was identified as a concern in the research.

### Consequences

#### Good
- Full spec compliance from day one -- no MUST NOT violations at any point after deployment
- Single code review and testing cycle -- reduces context-switching overhead
- No risk of Phase 2 being deferred indefinitely
- Cleaner git history -- single coherent change rather than an interim state

#### Bad
- Larger change surface increases testing complexity
- If something goes wrong, harder to isolate which change caused the issue (introspection vs exchange)
- Delays the first security improvement (cannot ship validation alone while exchange is still in progress)
- More backend changes in a single PR (new filter + extended existing filter + token provider changes)

---

## ADR-004: Extend Custom OAuth2 Filter Chain

### Status
Accepted

### Context
The backend implements OAuth2 Authorization Server functionality via custom servlet filters (`OAuth2TokenFilter`, `OAuth2AuthorizationFilter`, `PublicClientRegistrationFilter`) rather than Spring Authorization Server. Adding introspection and token exchange requires either extending these custom filters or migrating to Spring Authorization Server which has built-in support for both features.

### Decision Drivers
- Minimize change scope -- this is a security fix, not an architectural migration
- Leverage working, tested code rather than replacing it
- Follow existing code patterns that the team understands
- Avoid introducing a large new dependency (Spring Authorization Server) for incremental functionality

### Considered Options
1. **Extend custom filter chain** -- Add `OAuth2IntrospectionFilter` and token-exchange handler to `OAuth2TokenFilter`
2. **Migrate to Spring Authorization Server** -- Replace custom filters with Spring Auth Server's built-in OAuth2 AS

### Decision Outcome
Chosen option: **Extend custom filter chain (Option 1)**, because the custom filters already handle multiple OAuth2 flows successfully and adding new handlers follows an established pattern in the codebase. Migrating to Spring Authorization Server would be a significant effort that introduces more risk than the security fix itself. The migration is tracked as a deferred idea for when maintenance burden justifies it.

### Consequences

#### Good
- Minimal change to existing working code
- Follows established patterns in the codebase (new filter mirrors existing filters)
- No new major dependency
- Lower risk -- changes are additive, not replacement

#### Bad
- No built-in token exchange support from Spring Auth Server (must implement grant handler manually)
- Growing custom code base for OAuth2 -- each new feature increases maintenance
- Cannot leverage Spring Auth Server ecosystem improvements without future migration
- The custom filter chain is a growing technical debt that will eventually need addressing
