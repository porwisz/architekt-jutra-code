# Solution Exploration: MCP Token Security

## Problem Reframing

### Research Question
How should our MCP server handle authentication without token passthrough, per MCP spec?

### How Might We Questions

1. **HMW validate incoming tokens at the MCP server** without introducing a new external dependency or complex key management?
2. **HMW authenticate MCP server calls to the backend API** while preserving user identity and meeting the spec's "no passthrough" requirement?
3. **HMW sequence the implementation** to get immediate security improvement without blocking other pre-alpha work?

---

## Decision Area 1: MCP Server Token Validation Strategy

### Alternative 1A: Spring OAuth2 Resource Server with Shared HMAC Secret

**Description**: Add `spring-boot-starter-oauth2-resource-server` to the MCP server and configure `NimbusJwtDecoder.withSecretKey()` using the same HMAC-SHA256 secret the backend uses to sign tokens. Replace the custom `McpJwtFilter` with Spring Security's `oauth2ResourceServer().jwt()` DSL. Add audience and timestamp validators.

**Strengths**:
- Zero backend changes required -- MCP server only
- Uses standard Spring Security components (well-tested, well-documented)
- Interoperable with the backend's jjwt-produced tokens (both implement RFC 7519/7515)
- Achievable in hours, not days
- Eliminates the "accept any JWT string" vulnerability immediately

**Weaknesses**:
- Shared secret must be distributed to the MCP server (secret management concern)
- HMAC symmetric keys do not scale to multi-service architectures (every service needs the secret)
- Cannot validate tokens from third-party issuers (only the backend AS)

**Best when**: Single-backend architecture with pre-alpha maturity, where speed and simplicity matter most. This is the current situation.

**Evidence links**: Finding 5 (NimbusJwtDecoder compatibility confirmed), Finding 2 (spec mandates resource server role), Synthesis Insight 2 ("Phase 1 is essentially free")

---

### Alternative 1B: Token Introspection Endpoint on Backend

**Description**: Instead of validating tokens locally, the MCP server calls a token introspection endpoint (RFC 7662) on the backend for every request. The backend verifies the token and returns active/inactive status with token metadata.

**Strengths**:
- No shared secret needed at the MCP server -- validation is centralized at the backend
- Backend has full control over token revocation (immediately effective)
- Supports opaque tokens if the project ever moves away from JWTs

**Weaknesses**:
- Adds a network round-trip per MCP request (latency penalty)
- Backend must implement a new `/oauth2/introspect` endpoint (not currently present)
- Creates tight runtime coupling -- MCP server cannot validate tokens if backend is down
- More implementation work than local JWT validation for the same security outcome

**Best when**: The project uses opaque tokens, needs centralized revocation, or has multiple resource servers that should not hold signing keys. None of these conditions currently apply.

**Evidence links**: Finding 2 (spec allows both JWT validation and introspection per OAuth 2.1 Section 5.2), Synthesis section on custom OAuth2 filters (backend filter chain can be extended)

---

### Alternative 1C: Adopt mcp-security Library with Custom JwtDecoder

**Description**: Add the `spring-ai-community/mcp-security` library (v0.1.6) to the MCP server. Override its default auto-configuration by providing a custom `JwtDecoder` bean that uses `NimbusJwtDecoder.withSecretKey()` instead of the library's default issuer-uri discovery.

**Strengths**:
- Leverages community-maintained security code purpose-built for MCP servers
- Includes audience claim support and MCP-specific security patterns
- Positions the project to benefit from future library improvements

**Weaknesses**:
- Library assumes asymmetric keys via issuer-uri discovery; custom JwtDecoder override is expected to work per Spring Boot convention but has not been verified (Synthesis: Medium confidence, 80%)
- Adds a community dependency with uncertain maintenance trajectory (v0.1.6 is early)
- The library's auto-configuration may conflict with the HMAC setup in ways that are hard to debug
- More moving parts than the direct Spring Security approach for the same security outcome

**Best when**: The project plans to migrate to asymmetric keys or Spring Authorization Server soon, making the library's conventions the eventual target anyway.

**Evidence links**: Synthesis Contradiction 2 (mcp-security library resolution), Finding 5 (NimbusJwtDecoder as the underlying mechanism regardless)

---

### Trade-Off Matrix: Token Validation

| Perspective | 1A: Spring RS + HMAC | 1B: Token Introspection | 1C: mcp-security Library |
|---|---|---|---|
| **Technical Feasibility** | High -- proven, documented, zero backend changes | Medium -- requires new backend endpoint | Medium -- unverified override behavior |
| **User Impact** | Neutral -- transparent to clients | Slightly negative -- added latency | Neutral -- transparent to clients |
| **Simplicity** | High -- one dependency, one bean, one DSL config | Low -- new endpoint + client-side integration | Medium -- library abstraction + override |
| **Risk** | Low -- standard Spring pattern | Medium -- runtime coupling to backend | Medium -- community dependency, unverified |
| **Scalability** | Low -- HMAC secret sharing limits multi-service | High -- centralized validation | Medium -- library may ease future migration |

---

## Decision Area 2: Backend-Calling Strategy

### Alternative 2A: RFC 8693 Token Exchange

**Description**: The MCP server exchanges the validated client token (Token-A, aud: mcp-server) for a new backend-scoped token (Token-B, aud: backend-api) via the backend's token endpoint using the `urn:ietf:params:oauth:grant-type:token-exchange` grant type. The MCP server is registered as a confidential OAuth2 client with the backend.

**Strengths**:
- Most spec-aligned approach -- recommended by Solo.io, Stacklok, Curity, FastMCP, and the RFC itself (Section 2.3 describes this exact proxy use case)
- Preserves user identity in Token-B (audit trail, per-user RBAC maintained)
- Clean audience separation prevents token replay across services
- Spring Authorization Server has GA support since 1.3 (May 2024)
- Backend's `OAuth2TokenFilter` can be extended with a new grant type handler incrementally

**Weaknesses**:
- Medium implementation effort -- backend needs new grant type handler, MCP server needs client registration and token exchange flow
- Adds a token exchange round-trip (can be cached/managed by Spring OAuth2 client)
- Backend uses custom OAuth2 filters, not Spring Authorization Server -- Spring's built-in token exchange support cannot be used directly
- Requires the MCP server to be registered as a confidential client (client_id + client_secret)

**Best when**: The architecture requires proper audience separation, user identity preservation, and long-term spec compliance. This is the recommended end-state.

**Evidence links**: Finding 6 (token exchange ranked first across 4+ sources), Synthesis Validated Finding 5 (95% confidence), Research Report Finding 3 (backend OAuth2 AS can be extended)

---

### Alternative 2B: Client Credentials + User Context Header

**Description**: The MCP server obtains its own token from the backend using the client_credentials grant type (service identity, no user context). User identity is passed as a custom header (e.g., `X-User-Id`, `X-User-Sub`) extracted from the validated client token.

**Strengths**:
- Simpler to implement than token exchange -- client credentials is the most basic OAuth2 grant
- Technically eliminates token passthrough (MCP server uses its own token)
- Spring Security has built-in client credentials support

**Weaknesses**:
- Loses user context in the OAuth token itself -- user identity is only in a custom header, which is not cryptographically bound
- Solo.io explicitly calls this an anti-pattern for MCP servers: poor auditability, no per-user RBAC via standard token claims
- Backend must be modified to trust the custom header (new trust boundary -- the backend must trust that the MCP server correctly populated the header)
- Non-standard pattern that grows harder to maintain as more services are added

**Best when**: The project needs to eliminate passthrough immediately and token exchange implementation will take too long. Acceptable as a very short-term interim only.

**Evidence links**: Synthesis Contradiction 1 (client credentials resolved as "very short-term interim only"), Finding 6 comparison table (client credentials rated "Partial" compliance)

---

### Alternative 2C: Validated Token Forward (Temporary)

**Description**: After Phase 1 validation, the MCP server continues forwarding the validated token to the backend. The token is now confirmed as valid (signature, expiration, audience checked) but it is still the same token -- technically still passthrough.

**Strengths**:
- Zero additional implementation beyond Phase 1 validation
- User identity fully preserved (same token)
- No backend changes needed for this interim step
- Lowest risk transition path

**Weaknesses**:
- Still violates the MCP spec's MUST NOT passthrough requirement -- validation does not make passthrough compliant
- No audience separation (Token-A used at both MCP server and backend)
- Confused deputy risk remains (the backend cannot distinguish whether the MCP server or the client is making the call)
- Creates complacency risk -- "it works, why change it?"

**Best when**: Used as an explicit temporary step between Phase 1 (validation) and Phase 2 (token exchange), with a committed timeline for Phase 2. Not acceptable as a permanent state.

**Evidence links**: Synthesis Contradiction 3 (validated forward as partial compliance), Research Report conclusion 2 (Phase 2 "must not be deferred past beta")

---

### Alternative 2D: Internal API Key (Bypass OAuth)

**Description**: The MCP server authenticates to the backend using a static API key or shared secret, bypassing the OAuth2 system entirely for service-to-service calls. User context is passed as a header or claim.

**Strengths**:
- Simplest possible implementation -- static key, no token lifecycle
- No OAuth2 client configuration needed
- Fast -- no token exchange round-trip

**Weaknesses**:
- Completely circumvents the OAuth2 security model the backend already implements
- No token expiration, rotation, or revocation for service calls
- User context is not cryptographically bound
- Creates a parallel auth system alongside the existing OAuth2 AS -- increases maintenance complexity
- Does not leverage the existing OAuth2 infrastructure (the project's biggest asset per Synthesis Insight 1)
- Security regression: API keys are harder to rotate, audit, and scope

**Best when**: The MCP server and backend are in the same trust boundary (e.g., same process, same Kubernetes pod with network policy). Not recommended for separate Spring Boot applications on different ports.

**Evidence links**: Synthesis Insight 1 (OAuth2 AS is the project's biggest asset -- bypassing it is counter-strategic), Finding 3 (backend already has full OAuth2 AS)

---

### Trade-Off Matrix: Backend-Calling Strategy

| Perspective | 2A: Token Exchange | 2B: Client Creds + Header | 2C: Validated Forward | 2D: Internal API Key |
|---|---|---|---|---|
| **Technical Feasibility** | Medium -- new grant type handler in custom filters | Medium -- standard grant but needs header trust | High -- no additional work | High -- trivial to implement |
| **User Impact** | Positive -- proper audit trail, per-user RBAC | Degraded -- user context not in token | Neutral -- works as before | Degraded -- user context not in token |
| **Simplicity** | Medium -- token exchange flow is well-defined but multi-step | Medium -- simple grant but header convention is ad-hoc | High -- nothing changes | High -- but creates parallel auth system |
| **Risk** | Low -- RFC-standard, proven pattern | Medium -- non-standard header trust model | Medium -- still violates spec | High -- security regression, no rotation |
| **Scalability** | High -- works for multi-service, multi-audience | Low -- header convention does not scale | Low -- no audience separation | Low -- API keys are per-service static secrets |

---

## Decision Area 3: Implementation Phasing

### Alternative 3A: Big-Bang (Full Compliance in One Go)

**Description**: Implement token validation (Phase 1) and token exchange (Phase 2) simultaneously in a single development effort. Ship both together.

**Strengths**:
- Full spec compliance from the first deployment
- No temporary "partially compliant" state
- Single code review and testing cycle

**Weaknesses**:
- Larger change surface increases testing complexity and risk of regressions
- Blocks other work for longer (days instead of hours for the first improvement)
- If something goes wrong, harder to isolate which change caused the issue
- The two phases have very different complexity profiles (hours vs days) -- bundling them means delayed security improvement

**Best when**: The team has dedicated time, the project is not under time pressure, and the team is confident in the OAuth2 implementation.

**Evidence links**: Synthesis Trade-Off Analysis (big-bang: "Higher risk, more testing surface, blocks other work longer")

---

### Alternative 3B: Two-Phase (Validation First, Then Token Exchange)

**Description**: Phase 1 adds JWT validation at the MCP server (hours of work, zero backend changes). Phase 2 adds token exchange (days of work, backend changes). Separated by at least one deployment/sprint boundary.

**Strengths**:
- Immediate security improvement (Phase 1 closes the "accept any token" gap)
- Lower risk per change -- each phase is independently testable
- Phase 1 unblocks other work quickly
- Natural verification point between phases (confirm validation works before adding exchange)
- Aligns with the research recommendation (Synthesis, Research Report both recommend this)

**Weaknesses**:
- Phase 1 is only partially compliant (validated passthrough is still passthrough)
- Creates a window where the system is improved but not fully compliant
- Requires discipline to follow through on Phase 2 (deferred work risk)

**Best when**: The team wants to reduce risk incrementally and get an immediate security win. This is the most common recommendation across all research sources.

**Evidence links**: Synthesis "Phase 1 is essentially free" (Insight 2), Research Report Phase 1/Phase 2 breakdown, Synthesis Critical Path steps 1-4 vs 5-8

---

### Alternative 3C: Three-Phase (Validation, Token Exchange, SDK Upgrade)

**Description**: Phase 1 -- JWT validation (hours). Phase 2 -- token exchange (days). Phase 3 -- MCP SDK upgrade from 0.18.1 to 1.1.1 (unknown effort). Each phase is a separate task/sprint.

**Strengths**:
- Addresses the SDK version gap as part of the security roadmap
- Smallest possible change per phase
- SDK upgrade may bring security improvements or API changes that affect auth

**Weaknesses**:
- SDK upgrade is a separate concern with unknown effort (Confidence: Medium, 70%)
- Bundling SDK upgrade into the security roadmap conflates two different risks
- Phase 3 may not be needed for security compliance at all

**Best when**: The team wants a comprehensive security roadmap that includes dependency hygiene. However, the SDK upgrade is better tracked as a separate task.

**Evidence links**: Finding 7 (SDK version gap, Medium confidence), Synthesis Secondary Conclusion 4 ("investigated separately"), Research Report Separate Tasks table

---

### Trade-Off Matrix: Implementation Phasing

| Perspective | 3A: Big-Bang | 3B: Two-Phase | 3C: Three-Phase |
|---|---|---|---|
| **Technical Feasibility** | High -- all pieces are known | High -- each phase is well-defined | Medium -- Phase 3 has unknown effort |
| **User Impact** | Positive -- full compliance from day one | Positive -- immediate validation improvement | Positive -- same as 3B plus SDK modernization |
| **Simplicity** | Low -- large change surface | High -- each phase is focused | Medium -- Phase 3 scope unclear |
| **Risk** | Medium-High -- harder to isolate failures | Low -- incremental, independently testable | Low for Phase 1-2, Unknown for Phase 3 |
| **Scalability** | N/A | N/A | N/A |

---

## User Preferences

No direct user preferences were provided beyond the research question. Inferred constraints from project context:

- **Pre-alpha stage**: favors simplicity and speed over gold-plated solutions
- **Existing OAuth2 AS**: favors leveraging existing infrastructure over new systems
- **Single backend**: reduces the urgency for multi-service patterns
- **Spring Boot stack**: favors Spring-native solutions over custom implementations

---

## Recommended Approach

### Selected Combination

**Decision Area 1**: Alternative 1A -- Spring OAuth2 Resource Server with Shared HMAC Secret
**Decision Area 2**: Alternative 2A -- RFC 8693 Token Exchange (with 2C as explicit interim)
**Decision Area 3**: Alternative 3B -- Two-Phase Implementation

### Primary Rationale

The two-phase approach with Spring OAuth2 Resource Server validation (Phase 1) followed by RFC 8693 token exchange (Phase 2) is the strongest combination because:

1. It addresses the most critical vulnerability immediately (zero token validation) with minimal effort (hours, zero backend changes), using proven Spring Security components.
2. It converges on the industry-consensus long-term pattern (token exchange) that preserves user identity, enables proper audience separation, and leverages the existing backend OAuth2 AS -- the project's biggest infrastructure asset.
3. The phased approach minimizes risk per change while maintaining a clear path to full spec compliance.

### Key Trade-Offs Accepted

- **Partial compliance window**: Between Phase 1 and Phase 2, validated tokens are still forwarded (Alternative 2C as interim). This is a known spec violation accepted for the security improvement of validation.
- **HMAC shared secret distribution**: The MCP server needs the same JWT signing secret as the backend. This is acceptable for a two-service pre-alpha architecture but would need revisiting for multi-service expansion (migration to asymmetric keys).
- **Custom token exchange implementation**: Because the backend uses custom OAuth2 filters (not Spring Authorization Server), the token exchange grant handler must be implemented manually in `OAuth2TokenFilter`. This is more work than using Spring Auth Server's built-in support but avoids a larger migration.

### Key Assumptions

1. `NimbusJwtDecoder.withSecretKey()` correctly validates tokens produced by the backend's jjwt library (High confidence -- both implement RFC 7519/7515)
2. The backend's `OAuth2TokenFilter` can be extended with a new grant type handler without disrupting existing authorization code and refresh token flows (High confidence -- the filter already handles multiple grant types)
3. Phase 2 will be prioritized before beta (discipline assumption -- if deferred, the system remains partially non-compliant)
4. The HMAC shared secret can be securely shared between the two services via environment variables (Acceptable for pre-alpha, revisit for production)

### Confidence Level

**High (90%)**. The recommendation aligns with all research sources, leverages existing infrastructure, and follows the incremental risk reduction pattern recommended by the synthesis. The 10% uncertainty comes from untested Spring Security 7 token exchange client-side behavior with the custom OAuth2 server.

---

## Why Not Others

### Why Not 1B (Token Introspection)?
Adds a network round-trip per request and requires building a new backend endpoint, all for the same security outcome that local JWT validation achieves with less effort. Introspection is valuable when tokens are opaque or centralized revocation is critical -- neither applies here.

### Why Not 1C (mcp-security Library)?
The library's auto-configuration assumes asymmetric keys via issuer-uri discovery. Overriding with a custom `JwtDecoder` is expected to work but has not been verified (80% confidence). For pre-alpha, the direct Spring Security approach is simpler with fewer unknowns. The library becomes relevant if the project migrates to asymmetric keys.

### Why Not 2B (Client Credentials + Header)?
Loses user context in the token itself. Solo.io explicitly identifies this as an anti-pattern for MCP servers due to poor auditability and inability to enforce per-user RBAC through standard token claims. The added header trust model is non-standard and fragile.

### Why Not 2D (Internal API Key)?
Bypasses the existing OAuth2 infrastructure, which is the project's biggest strategic asset. Creates a parallel auth system with no token expiration, rotation, or revocation. A security regression masquerading as simplification.

### Why Not 3A (Big-Bang)?
The two phases have very different complexity profiles (hours vs days). Bundling them delays the immediate security win (token validation) by the time needed for the more complex work (token exchange). The incremental approach reduces risk and delivers value sooner.

### Why Not 3C (Three-Phase with SDK Upgrade)?
The SDK upgrade is a separate concern with unknown effort and unclear security impact. Bundling it into the security roadmap conflates two risks. Better tracked as an independent task per the research recommendation.

---

## Deferred Ideas

1. **Migration to asymmetric keys (RSA/EC)**: Eliminates shared secret management, enables standard JWKS discovery, and unlocks the mcp-security library's full auto-configuration. Worth evaluating when moving toward production but not blocking for pre-alpha. (Stretch)

2. **Spring Authorization Server migration**: Replacing the custom OAuth2 filter chain with Spring Authorization Server would provide built-in token exchange, introspection, and JWKS endpoints. Significant effort but reduces long-term maintenance. (Stretch)

3. **In-memory auth storage migration to database**: Authorization codes and refresh tokens stored in memory are lost on restart. Not related to MCP token security directly but compounds overall auth reliability risk. (Out-of-scope, tracked separately)

4. **MCP SDK 0.18.1 to 1.1.1 upgrade**: Major version boundary with unknown breaking changes. May bring security improvements but does not block the recommended approach. (Out-of-scope, tracked separately)

5. **Granular MCP scope refinement**: Current scopes (`mcp:read`, `mcp:edit`) may need refinement to more granular tool-level scopes (e.g., `mcp:tools-basic`) per MCP spec guidance. Acceptable for pre-alpha. (Stretch)

6. **Protected Resource Metadata content verification**: The well-known endpoint exists but its content has not been verified against RFC 9728 requirements. Low priority but should be checked. (Stretch)
