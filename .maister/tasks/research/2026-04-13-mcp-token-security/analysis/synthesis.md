# Synthesis: MCP Token Security

## Research Question
How should our MCP server handle authentication without token passthrough, per MCP spec?

---

## Executive Summary

The MCP authorization specification unambiguously forbids token passthrough -- the current pattern where our MCP server blindly forwards client tokens to the backend API. This is not a recommendation but a normative requirement (MUST NOT). The critical finding is that our backend already implements a full OAuth2 Authorization Server with DCR, authorization code + PKCE, token endpoint, and refresh tokens, meaning the infrastructure for a compliant solution largely exists. The gap is narrow: the MCP server lacks token validation, the backend tokens lack audience claims, and no mechanism exists for the MCP server to obtain a separate backend-scoped token.

Two cross-validated approaches emerge: (1) an immediate fix that adds JWT validation at the MCP server using the shared HMAC secret (zero backend changes, achievable in hours), and (2) a proper fix that implements RFC 8693 token exchange to eliminate token forwarding entirely. The Spring ecosystem provides off-the-shelf components for both approaches.

---

## Cross-Source Analysis

### Validated Findings (Confirmed by Multiple Sources)

**1. Token passthrough is explicitly forbidden**
- Confidence: **High (100%)**
- Sources: MCP authorization spec (MUST NOT), MCP security best practices ("explicitly forbidden"), external patterns analysis (Solo.io, Stacklok confirm this)
- Validation: Three independent sources agree with zero contradiction

**2. Backend already has OAuth2 Authorization Server infrastructure**
- Confidence: **High (100%)**
- Sources: Codebase analysis (context summary), Spring ecosystem findings (references specific filters)
- Evidence: `OAuth2TokenFilter`, `OAuth2AuthorizationFilter`, `PublicClientRegistrationFilter`, `DatabaseRegisteredClientRepository`, `JwtTokenProvider.generateOAuth2Token()`
- Implication: This is the single most important finding -- it means the compliant architecture is an incremental change, not a greenfield build

**3. MCP server currently performs zero token validation**
- Confidence: **High (100%)**
- Sources: Codebase analysis (McpJwtFilter), Spring ecosystem findings (Section 1.5)
- Evidence: McpJwtFilter extracts token but does not validate signature, expiration, audience, or issuer
- Cross-reference: This directly violates multiple MUST requirements in the MCP spec

**4. NimbusJwtDecoder can validate HMAC-SHA256 tokens from the backend**
- Confidence: **High (100%)**
- Sources: Spring ecosystem findings (Section 1.7), Spring Security documentation
- Validation: Both jjwt (backend) and Nimbus JOSE+JWT (Spring Security) implement the same JWT/JWS standard (RFC 7519/7515). Same key bytes produce interoperable results.

**5. Token exchange (RFC 8693) is the recommended long-term pattern**
- Confidence: **High (95%)**
- Sources: External patterns findings (ranked first), MCP spec (Section 1.3 implies separate token), Spring ecosystem findings (GA in Spring Auth Server 1.3+)
- Cross-reference: Solo.io, Stacklok, Curity, and FastMCP all recommend token exchange for MCP proxy servers

### Contradictions Resolved

**Contradiction 1: Client Credentials -- simple but anti-pattern?**
- Spring ecosystem findings present client credentials as a viable interim approach
- External patterns findings cite Solo.io calling it an anti-pattern (loses user context, poor auditability)
- **Resolution**: Client credentials is acceptable as a very short-term interim only if user identity is embedded as a custom claim/header. Not suitable beyond pre-alpha. Token exchange should replace it promptly.

**Contradiction 2: mcp-security library -- use it or not?**
- MCP spec findings recommend it (v0.1.6, JWT validation, audience claim support)
- Spring ecosystem findings note it assumes asymmetric keys via issuer-uri discovery, while our backend uses HMAC shared secrets
- **Resolution**: The library's auto-configuration won't work out-of-the-box with our symmetric key setup. A custom `JwtDecoder` bean using `NimbusJwtDecoder.withSecretKey()` is simpler and more reliable for pre-alpha. The mcp-security library becomes relevant if/when migrating to asymmetric keys or Spring Authorization Server.

**Contradiction 3: Immediate validation as "partial compliance"**
- Adding JWT validation without separate tokens still technically forwards the same token to the backend
- The spec says tokens MUST be validated AND a separate token MUST be used for downstream calls
- **Resolution**: Phase 1 (validate tokens) addresses the most critical security gap (accepting unvalidated tokens). Phase 2 (token exchange) achieves full compliance. The phased approach is pragmatic for pre-alpha but Phase 2 must not be deferred past beta.

### Low-Confidence Findings

**1. MCP SDK v0.18.1 vs v1.1.1 compatibility**
- Confidence: **Medium (70%)**
- The SDK underwent a major version bump (0.x to 1.x). Impact on auth integration is unclear since the SDK is transport-agnostic and delegates auth to Spring Security. However, API changes may affect transport classes.
- Gap: No investigation of breaking changes between 0.18.1 and 1.1.1.

**2. mcp-security library compatibility with custom JwtDecoder**
- Confidence: **Medium (80%)**
- Expectation is that providing a custom `JwtDecoder` bean overrides the library's issuer-uri auto-configuration. Needs verification.

---

## Patterns and Themes

### Architectural Patterns

| Pattern | Description | Prevalence | Quality |
|---------|-------------|-----------|---------|
| Resource Server | MCP server validates tokens as OAuth2 Resource Server | Spec-mandated, universal | Standard, well-documented |
| Token Exchange (RFC 8693) | Exchange client token for backend-scoped token | Primary recommendation across 4+ sources | RFC standard, Spring GA support |
| Client Credentials | Service-to-service auth with MCP server's own identity | Common fallback | Well-supported but loses user context |
| Protected Resource Metadata | RFC 9728 discovery mechanism | Spec-mandated | Already partially implemented (well-known endpoint exists) |

### Implementation Patterns

| Pattern | Description | Prevalence | Quality |
|---------|-------------|-----------|---------|
| HMAC Shared Secret | Symmetric JWT validation using shared secret | Current backend approach | Simple but limits scalability |
| Custom OAuth2 Filters | Hand-written OAuth2 server (not Spring Auth Server) | Current backend | Functional but limits ecosystem leverage |
| ThreadLocal Token Holder | `AccessTokenHolder` for passing tokens to interceptors | Current MCP server | Anti-pattern, enables passthrough |

### Organizational Patterns

| Pattern | Description | Assessment |
|---------|-------------|-----------|
| Scope-based access control | `mcp:read`, `mcp:edit` mapped to `PERMISSION_` authorities | Good -- aligns with MCP spec scope minimization |
| Well-known endpoint | `/.well-known/oauth-protected-resource` already served | Good -- partially spec-compliant |
| WWW-Authenticate header | 401 responses include `resource_metadata` link | Good -- spec-compliant |

---

## Key Insights

### Insight 1: The OAuth2 AS is the project's biggest asset for compliance
- **Supporting evidence**: Backend has DCR, authorization code + PKCE, token endpoint, refresh tokens, scope definitions
- **Implications**: Adding token exchange to the existing `OAuth2TokenFilter` is an incremental change (new grant type handler), not a new system
- **Confidence**: High

### Insight 2: The immediate fix requires zero backend changes
- **Supporting evidence**: `NimbusJwtDecoder.withSecretKey()` validates HMAC-SHA256 JWTs using the same shared secret. Spring Security's `oauth2ResourceServer().jwt()` DSL replaces the custom `McpJwtFilter` entirely.
- **Implications**: The MCP server can start validating tokens today by adding one dependency and replacing the security configuration
- **Confidence**: High

### Insight 3: Missing `aud` claim is a latent vulnerability
- **Supporting evidence**: Backend's `generateOAuth2Token()` does not include an `aud` claim. Without audience binding, tokens issued for one service can be replayed at another.
- **Implications**: Adding `aud` claim to token generation is a prerequisite for proper audience validation at the MCP server. Low-effort change (single `.claim("aud", audience)` addition).
- **Confidence**: High

### Insight 4: In-memory auth storage is a production risk
- **Supporting evidence**: Authorization codes and refresh tokens are stored in-memory (codebase context)
- **Implications**: Server restart invalidates all active sessions. Not related to MCP security directly but compounds risk. Should be addressed separately.
- **Confidence**: High

### Insight 5: SDK version gap creates upgrade risk
- **Supporting evidence**: MCP Java SDK 0.18.1 vs current 1.1.1 (major version boundary)
- **Implications**: Security improvements may require SDK upgrade. The longer the gap grows, the harder the migration. Should be evaluated as a separate task.
- **Confidence**: Medium

---

## Relationships and Dependencies

### Component Dependency Map

```
MCP Client (Claude Desktop, etc.)
    |
    | Token-A (aud: mcp-server)
    v
MCP Server
    |-- SecurityFilterChain (validates Token-A) [NEEDS: JwtDecoder with shared secret]
    |-- MCP Tool Handlers (process requests)
    |-- RestClient (calls backend)
    |       |
    |       | Token-B (aud: backend-api) [NEEDS: token exchange or client credentials]
    |       v
    Backend API
        |-- SecurityConfiguration (validates Token-B)
        |-- OAuth2 Authorization Server
        |       |-- OAuth2TokenFilter [NEEDS: token-exchange grant type]
        |       |-- JwtTokenProvider [NEEDS: aud claim in tokens]
        |       |-- DatabaseRegisteredClientRepository [NEEDS: MCP server registration]
```

### Critical Path for Compliance

1. Add `aud` claim to `JwtTokenProvider.generateOAuth2Token()` (backend)
2. Add `spring-boot-starter-oauth2-resource-server` to MCP server
3. Replace `McpJwtFilter` with `oauth2ResourceServer().jwt()` + `NimbusJwtDecoder.withSecretKey()`
4. Add audience validator for `aud == "mcp-server"`
5. Register MCP server as confidential OAuth2 client with backend
6. Add token-exchange grant type to `OAuth2TokenFilter`
7. Replace `JwtForwardingInterceptor` with `OAuth2ClientHttpRequestInterceptor`
8. Remove `AccessTokenHolder`

Steps 1-4 = Phase 1 (immediate fix, validates tokens).
Steps 5-8 = Phase 2 (proper fix, eliminates passthrough).

---

## Gaps and Uncertainties

### Information Gaps

1. **MCP SDK 0.18.1 to 1.1.1 migration impact** -- No investigation of breaking changes. The auth integration is likely unaffected (SDK is transport-agnostic) but transport API changes could require code modifications.

2. **Backend `generateOAuth2Token()` full signature** -- The context mentions it lacks `aud` but full method review wasn't in codebase findings. Line numbers referenced but file not directly examined in this synthesis.

3. **In-memory storage implementation details** -- Referenced as a concern but not examined. Scope: separate task.

4. **MCP server's current Protected Resource Metadata content** -- The well-known endpoint exists but the metadata document content wasn't verified against RFC 9728 requirements.

### Unverified Claims

1. **mcp-security library custom JwtDecoder override** -- Assumed to work based on Spring Boot convention (custom bean overrides auto-configuration) but not tested.

2. **Spring Security 7 token exchange client-side support** -- Documented in Spring blogs but compatibility with custom OAuth2 server (vs Spring Authorization Server) needs verification.

### Unresolved Inconsistencies

1. **Scope naming** -- Backend uses `mcp:read`/`mcp:edit` in `SecurityConfiguration` but the MCP spec's scope minimization guidance suggests starting with more granular scopes like `mcp:tools-basic`. Current scopes may be acceptable for pre-alpha.

---

## Framework Application: Literature Research

### Current State Analysis

**How it currently works**: MCP server receives JWT from client, stores it in ThreadLocal (`AccessTokenHolder`), forwards it unmodified to backend via `JwtForwardingInterceptor`. No validation occurs at the MCP server.

**Strengths**:
- Simple implementation
- User context preserved in backend calls
- Backend's OAuth2 AS infrastructure is production-grade for pre-alpha

**Weaknesses**:
- Violates MCP spec (MUST NOT passthrough tokens)
- No token validation at MCP server (accepts any JWT, including expired/forged)
- No audience binding (tokens for other services could be replayed)
- Confused deputy vulnerability
- ThreadLocal pattern is fragile

### Best Practices Comparison

| Practice | Industry Standard | Our Status | Gap |
|----------|------------------|-----------|-----|
| Token validation at resource server | Mandatory (MCP spec, OAuth2) | Missing | Critical |
| Audience-scoped tokens | Required (RFC 8707, RFC 9068) | Missing `aud` claim | High |
| Separate downstream tokens | Required (MCP spec) | Missing (passthrough) | High |
| Protected Resource Metadata | Required (RFC 9728) | Partially implemented | Low |
| Scope-based access control | Recommended | Implemented (`mcp:read`, `mcp:edit`) | None |

### Trade-Off Analysis: Phased vs Big-Bang

**Phased approach** (recommended):
- Phase 1: Add JWT validation only (hours of work, zero backend changes)
- Phase 2: Add token exchange (days of work, backend changes needed)
- Pros: Immediate security improvement, lower risk per change, unblocks other work
- Cons: Phase 1 is only partially compliant (still forwards validated token)

**Big-bang approach**:
- Implement token validation + token exchange simultaneously
- Pros: Full compliance in one change
- Cons: Higher risk, more testing surface, blocks other work longer

### Applicability Assessment

Token Exchange (RFC 8693) is the recommended long-term pattern because:
- It preserves user identity (unlike client credentials)
- It has standard RFC backing (unlike OBO which is Microsoft-specific)
- Spring ecosystem has GA support
- Our backend's custom `OAuth2TokenFilter` can be extended incrementally
- Single-backend architecture makes it straightforward

---

## Conclusions

### Primary Conclusions

1. **The MCP server's current token handling violates the spec.** Token passthrough is explicitly forbidden by normative MUST NOT requirements. This is not a future concern -- it is a current compliance and security gap.

2. **The fix is achievable incrementally.** Phase 1 (JWT validation) requires zero backend changes and can be completed in hours. Phase 2 (token exchange) requires moderate backend work and can be completed in days.

3. **The backend OAuth2 AS is a major strategic asset.** The existing infrastructure (DCR, authorization code + PKCE, token endpoint, refresh tokens, scope definitions) means we are building on solid ground, not starting from scratch.

### Secondary Conclusions

4. **The MCP SDK version gap (0.18.1 vs 1.1.1) should be investigated separately.** It is not blocking for security fixes but creates growing upgrade debt.

5. **In-memory auth storage is a separate production concern** that should be tracked independently.

6. **The mcp-security community library is worth watching** but not necessary for pre-alpha given our symmetric key setup.

### Recommendations

| Priority | Action | Effort | Phase |
|----------|--------|--------|-------|
| Critical | Add JWT validation at MCP server | Low (hours) | 1 - Immediate |
| Critical | Add `aud` claim to backend token generation | Low (minutes) | 1 - Immediate |
| High | Register MCP server as OAuth2 client | Low (hours) | 2 - Next sprint |
| High | Add token-exchange grant to `OAuth2TokenFilter` | Medium (days) | 2 - Next sprint |
| High | Replace `JwtForwardingInterceptor` with `OAuth2ClientHttpRequestInterceptor` | Medium (days) | 2 - Next sprint |
| Medium | Investigate MCP SDK 0.18.1 to 1.1.1 upgrade | Unknown | Separate task |
| Medium | Move auth storage from in-memory to database | Medium | Separate task |
