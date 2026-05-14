# Research Report: MCP Token Security

**Research type**: Literature review with codebase gap analysis
**Date**: 2026-04-13
**Methodology**: Multi-source literature review (MCP spec, codebase analysis, external patterns, Spring ecosystem)

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [Research Objectives](#research-objectives)
3. [Methodology](#methodology)
4. [Findings](#findings)
5. [Analysis and Insights](#analysis-and-insights)
6. [Conclusions](#conclusions)
7. [Recommendations](#recommendations)
8. [Appendices](#appendices)

---

## Executive Summary

This report investigates how the project's MCP server should handle authentication to comply with the MCP authorization specification, which explicitly forbids token passthrough -- the current implementation pattern.

**What was researched**: The MCP authorization specification's normative requirements for token handling, the current codebase's security implementation, industry patterns for proxy authentication (RFC 8693 token exchange, client credentials, OBO, credential vaulting, gateway offloading), and Spring ecosystem capabilities for implementing compliant authentication.

**How it was researched**: Four parallel research tracks analyzed the MCP specification (authorization spec + security best practices), the project codebase (MCP server and backend security classes), external OAuth2 proxy patterns (5 patterns from RFCs and industry sources), and Spring Security/Spring Authorization Server capabilities. Cross-referencing validated findings across sources.

**Key findings**:
- The MCP server currently performs zero token validation and forwards client tokens unmodified to the backend -- violating two normative MUST NOT requirements in the MCP spec
- The backend already implements a full OAuth2 Authorization Server (DCR, authorization code + PKCE, token endpoint, refresh tokens, scope definitions), meaning compliant infrastructure largely exists
- Token exchange (RFC 8693) is the recommended long-term pattern, supported by Spring Authorization Server 1.3+ GA and extensible through the backend's custom OAuth2 filters
- An immediate fix (JWT validation via `NimbusJwtDecoder.withSecretKey()`) requires zero backend changes and can be completed in hours

**Main conclusions**: The compliance gap is real but the fix is incremental. A two-phase approach -- immediate JWT validation followed by proper token exchange -- achieves full MCP spec compliance with minimal risk and leverages existing infrastructure.

---

## Research Objectives

### Primary Research Question
How should our MCP server handle authentication without token passthrough, per MCP spec?

### Sub-Questions
1. What exactly does the MCP spec require for token handling at resource servers?
2. What authentication infrastructure already exists in our codebase?
3. What OAuth2 patterns exist for proxy servers calling downstream APIs on behalf of users?
4. What Spring ecosystem capabilities can we leverage?
5. What is the simplest path to compliance for a pre-alpha project?

### Scope
- **Included**: MCP authorization spec requirements, codebase security analysis, external OAuth2 proxy patterns, Spring ecosystem capabilities
- **Excluded**: Frontend/client-side MCP implementation, non-OAuth authentication methods, gateway infrastructure, multi-provider token vaulting

---

## Methodology

### Research Type
Literature review with codebase gap analysis -- combining specification analysis, codebase examination, external pattern research, and ecosystem evaluation.

### Data Sources
| Source Category | Files Analyzed | Description |
|----------------|---------------|-------------|
| MCP Specification | 2 spec pages | Authorization spec + Security best practices |
| Codebase | ~15 source files | MCP server security, backend OAuth2 filters, token providers |
| External Patterns | 8+ articles/RFCs | RFC 8693, RFC 6749, Solo.io, Stacklok, FastMCP, Auth0, Microsoft |
| Spring Ecosystem | 6+ documentation pages | Spring Security OAuth2 RS, Spring Auth Server, mcp-security library |

### Analysis Framework
Literature Research Framework: Current state analysis, best practices comparison, trade-off analysis, and applicability assessment.

---

## Findings

### Finding 1: Token Passthrough is Explicitly Forbidden

**Category**: Specification Requirement
**Confidence**: High (100%)

The MCP authorization specification contains two separate normative prohibitions:

1. Authorization spec, "Access Token Privilege Restriction" section:
   > "The MCP server MUST NOT pass through the token it received from the MCP client."

2. Security Best Practices, "Token Passthrough" section:
   > "Token passthrough is explicitly forbidden in the authorization specification."
   > "MCP servers MUST NOT accept any tokens that were not explicitly issued for the MCP server."

**Risks identified by the spec**:
- Security control circumvention (rate limiting, validation bypass)
- Accountability and audit trail issues (cannot distinguish MCP clients)
- Trust boundary violations (confused deputy problem)
- Future compatibility risk (cannot evolve security model)

**Implications**: The current implementation is in direct violation. This is not a recommendation to follow but a mandatory requirement to meet.

**Sources**: MCP Authorization Spec (2025-06-18 and latest), MCP Security Best Practices

---

### Finding 2: MCP Server Must Act as OAuth2 Resource Server

**Category**: Specification Requirement
**Confidence**: High (100%)

The MCP spec defines three OAuth 2.1 roles. The MCP server is the **Resource Server**, meaning it must:

- Validate access tokens per OAuth 2.1 Section 5.2
- Validate tokens were issued specifically for the MCP server as audience (RFC 8707 Section 2)
- Reject tokens that do not include the MCP server in the audience claim
- Return HTTP 401 with `WWW-Authenticate` header for invalid/expired tokens
- Implement Protected Resource Metadata (RFC 9728)

**Referenced standards**: OAuth 2.1, RFC 8414, RFC 9728, RFC 8707, RFC 9068

**Implications**: The MCP server must validate token signature, expiration, audience, and scopes before processing any request. The current `McpJwtFilter` does none of this.

**Sources**: MCP Authorization Spec, Sections 1.1-1.2

---

### Finding 3: Backend Already Has Full OAuth2 Authorization Server

**Category**: Codebase Analysis
**Confidence**: High (100%)

The backend implements a complete (custom) OAuth2 Authorization Server with:

| Component | Status | Evidence |
|-----------|--------|----------|
| Dynamic Client Registration | Implemented | `PublicClientRegistrationFilter`, `DatabaseRegisteredClientRepository` |
| Authorization Code + PKCE | Implemented | `OAuth2AuthorizationFilter` |
| Token Endpoint | Implemented | `OAuth2TokenFilter` (supports `authorization_code`, `refresh_token`) |
| Refresh Tokens | Implemented | `OAuth2TokenFilter` grant type handler |
| JWT Token Generation | Implemented | `JwtTokenProvider.generateOAuth2Token()` |
| MCP Scopes | Defined | `mcp:read`, `mcp:edit` mapped to `PERMISSION_` authorities |
| Protected Resource Metadata | Served | `/.well-known/oauth-protected-resource` endpoint |
| WWW-Authenticate Header | Implemented | 401 responses include `resource_metadata` link |

**What is missing**:
- `aud` (audience) claim in generated tokens
- Token exchange grant type (`urn:ietf:params:oauth:grant-type:token-exchange`)
- MCP server registered as OAuth2 client

**Implications**: The infrastructure for MCP spec compliance is 70-80% built. The remaining work is incremental extensions to existing components.

**Sources**: Codebase analysis (backend security package)

---

### Finding 4: MCP Server Performs Zero Token Validation

**Category**: Codebase Analysis / Security Gap
**Confidence**: High (100%)

The current `McpJwtFilter` extracts the JWT from the Authorization header and stores it in `AccessTokenHolder` (ThreadLocal). It does **not**:
- Validate the JWT signature
- Check token expiration
- Verify the audience claim
- Verify the issuer
- Check scopes/permissions

The `JwtForwardingInterceptor` in `RestClientConfig` then reads the token from `AccessTokenHolder` and forwards it unmodified to the backend API.

**Current flow**:
```
Client --[Token-A]--> McpJwtFilter --[stores]--> AccessTokenHolder
                                                       |
JwtForwardingInterceptor --[reads]--> Backend API --[Token-A forwarded]-->
```

**Implications**: Any valid-looking JWT string (even expired, forged, or intended for a different service) would be accepted and forwarded. This is both a spec violation and a direct security vulnerability.

**Sources**: Codebase analysis (`McpJwtFilter.java`, `RestClientConfig.java`, `AccessTokenHolder.java`)

---

### Finding 5: NimbusJwtDecoder Can Validate Backend Tokens with Shared Secret

**Category**: Spring Ecosystem Capability
**Confidence**: High (100%)

Spring Security's `NimbusJwtDecoder.withSecretKey()` validates HMAC-SHA256 JWTs using the same key format the backend's `jjwt` library produces. Both implement RFC 7519/7515 -- the JWT format is standard and library-interoperable.

```java
@Bean
public JwtDecoder jwtDecoder(@Value("${app.jwt.secret}") String secret) {
    byte[] keyBytes = Base64.getDecoder().decode(secret);
    SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "HmacSHA256");
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey)
            .macAlgorithm(MacAlgorithm.HS256)
            .build();

    OAuth2TokenValidator<Jwt> audienceValidator =
        new JwtClaimValidator<List<String>>("aud", aud -> aud.contains("mcp-server"));
    OAuth2TokenValidator<Jwt> combined = new DelegatingOAuth2TokenValidator<>(
        new JwtTimestampValidator(), audienceValidator);
    decoder.setJwtValidator(combined);
    return decoder;
}
```

**Required dependency**: `spring-boot-starter-oauth2-resource-server` (not currently in MCP server's pom.xml)

**Implications**: The MCP server can start validating tokens immediately with zero backend changes. Share the `app.jwt.secret` environment variable and add one Spring Boot starter dependency.

**Sources**: Spring Security OAuth2 Resource Server JWT docs, NimbusJwtDecoder API

---

### Finding 6: Token Exchange (RFC 8693) is the Recommended Long-Term Pattern

**Category**: External Patterns
**Confidence**: High (95%)

Five OAuth2 proxy authentication patterns were evaluated:

| Pattern | Spec Compliance | User Context | Complexity | Recommendation |
|---------|----------------|-------------|------------|----------------|
| Token Exchange (RFC 8693) | High | Preserved | Medium | **Primary recommendation** |
| Client Credentials | Partial | Lost | Low | Interim fallback only |
| On-Behalf-Of (OBO) | High | Preserved | Medium-High | Microsoft-specific, use RFC 8693 instead |
| Credential Vaulting | High | Preserved | High | Overengineered for single-backend |
| Gateway Offloading | High | Preserved | Very High | Overengineered for pre-alpha |

**Why token exchange wins**:
- RFC 8693 Section 2.3 explicitly describes the proxy/resource-server use case
- Preserves user identity in backend calls (audit trail, per-user RBAC)
- Our backend's `OAuth2TokenFilter` can be extended with a new grant type handler
- Spring Authorization Server has GA support since 1.3 (May 2024)
- Clean audience separation: Token-A (aud: mcp-server) vs Token-B (aud: backend-api)

**Sources**: RFC 8693, Solo.io, Stacklok, FastMCP, Curity, Spring Authorization Server blog

---

### Finding 7: MCP Java SDK Version Gap (0.18.1 vs 1.1.1)

**Category**: Dependency Risk
**Confidence**: Medium (70%)

The project uses MCP Java SDK v0.18.1. The current release is v1.1.1 (released 2026-03-27), representing a major version boundary (0.x to 1.x). The SDK is transport-agnostic and delegates auth to Spring Security, so the version gap likely does not block security fixes. However:

- API breaking changes may affect transport classes
- Security improvements in newer versions may be missed
- The gap will widen over time, increasing migration difficulty

**Sources**: MCP Java SDK GitHub releases

---

### Finding 8: Backend Token Generation Lacks Audience Claim

**Category**: Codebase Gap
**Confidence**: High (100%)

The backend's `JwtTokenProvider.generateOAuth2Token()` method produces tokens with `sub`, `scopes`, `iat`, and `exp` claims but does not include an `aud` (audience) claim. Without audience binding:

- Tokens cannot be validated for intended recipient
- Cross-service token replay is possible
- RFC 8707 resource indicators cannot be enforced

**Fix**: Add `.claim("aud", audience)` to token generation. The audience value should be parameterized (e.g., "mcp-server" for MCP-bound tokens, "backend-api" for backend-bound tokens).

**Sources**: Codebase analysis (`JwtTokenProvider.java`)

---

### Findings Summary Table

| # | Finding | Category | Confidence | Impact |
|---|---------|----------|-----------|--------|
| 1 | Token passthrough forbidden by spec | Spec requirement | High (100%) | Critical -- current impl violates |
| 2 | MCP server must be OAuth2 Resource Server | Spec requirement | High (100%) | Critical -- not currently implemented |
| 3 | Backend has full OAuth2 AS | Codebase asset | High (100%) | Positive -- infrastructure exists |
| 4 | MCP server has zero token validation | Security gap | High (100%) | Critical -- immediate vulnerability |
| 5 | NimbusJwtDecoder validates HMAC tokens | Spring capability | High (100%) | Enables immediate fix |
| 6 | Token exchange is recommended pattern | External consensus | High (95%) | Guides long-term architecture |
| 7 | MCP SDK version gap (0.18.1 vs 1.1.1) | Dependency risk | Medium (70%) | Moderate -- upgrade debt |
| 8 | Backend tokens lack `aud` claim | Codebase gap | High (100%) | High -- prerequisite for audience validation |

---

## Analysis and Insights

### Patterns Identified

**Pattern: OAuth2 Resource Server at MCP Server**
- Type: Architectural (spec-mandated)
- Description: MCP server validates incoming tokens using standard OAuth2 resource server mechanisms
- Prevalence: Universal -- mandated by MCP spec, implemented by mcp-security library, FastMCP, ToolHive
- Assessment: The canonical pattern. No compliant alternative exists.
- Example: Spring Security `oauth2ResourceServer().jwt()` with custom `JwtDecoder`

**Pattern: Separate Token Acquisition for Downstream**
- Type: Architectural (spec-mandated)
- Description: MCP server obtains a distinct token (different audience, potentially different scopes) for backend API calls
- Prevalence: Universal -- required by spec, implemented via token exchange, client credentials, or credential vaulting
- Assessment: Token exchange (RFC 8693) is the standard mechanism. Client credentials is a degraded fallback.

**Pattern: Custom OAuth2 Filter Chain**
- Type: Implementation (project-specific)
- Description: Backend uses hand-written servlet filters for OAuth2 endpoints rather than Spring Authorization Server
- Assessment: Functional but limits leverage of Spring ecosystem features (e.g., built-in token exchange support). Acceptable for pre-alpha but creates growing maintenance burden.

### Key Insights

**1. The compliance gap is narrow, not wide**
- Importance: High
- Evidence: Backend OAuth2 AS handles 70-80% of the requirements. Missing pieces are incremental (audience claim, token exchange grant, MCP server client registration).
- Implications: Full compliance is achievable in two focused sprints, not a major architectural rewrite.

**2. Phase 1 (JWT validation) is essentially free**
- Importance: High
- Evidence: Adding `spring-boot-starter-oauth2-resource-server` + `NimbusJwtDecoder.withSecretKey()` replaces the custom `McpJwtFilter` with standard Spring Security. Zero backend changes.
- Implications: There is no reason to delay Phase 1. It should be the next security task.

**3. The `AccessTokenHolder` ThreadLocal is the root of the passthrough pattern**
- Importance: Medium
- Evidence: The entire passthrough flow depends on McpJwtFilter storing the raw token and JwtForwardingInterceptor reading it. Replacing both with Spring OAuth2 Resource Server + `OAuth2ClientHttpRequestInterceptor` eliminates the pattern architecturally.
- Implications: Phase 2 should remove `AccessTokenHolder` entirely, not just stop using it.

### Quality Assessment

**Strengths**:
- Existing OAuth2 AS infrastructure is solid and well-structured
- MCP scopes (`mcp:read`, `mcp:edit`) align with spec's scope minimization guidance
- Protected Resource Metadata endpoint already exists
- WWW-Authenticate header with resource_metadata link already implemented

**Weaknesses**:
- Zero token validation at MCP server (critical gap)
- Token passthrough violates spec (critical gap)
- No audience claims in tokens
- In-memory auth storage (production risk, separate concern)

**Opportunities**:
- Spring ecosystem provides off-the-shelf components for both phases
- The mcp-security community library can be leveraged when moving to asymmetric keys
- Token exchange positions the architecture for multi-service expansion

**Threats**:
- MCP SDK version gap may create compatibility issues during upgrade
- Custom OAuth2 filter chain limits Spring ecosystem leverage
- HMAC shared secret approach limits scalability (both services must share the secret)

---

## Conclusions

### Primary Conclusions

**1. The MCP server's token handling is non-compliant and must be fixed.**
Confidence: High (100%). The MCP authorization specification contains normative MUST NOT requirements that the current implementation violates. Token passthrough creates confused deputy vulnerabilities and audit trail gaps. This is the project's most critical security gap.

**2. A two-phase approach achieves compliance incrementally with minimal risk.**
Confidence: High (95%).

- **Phase 1 (Immediate -- hours)**: Add JWT validation at the MCP server using `NimbusJwtDecoder.withSecretKey()` with the shared HMAC secret. Add `aud` claim to backend token generation. This eliminates the "accept any token" vulnerability with zero architectural changes.

- **Phase 2 (Next sprint -- days)**: Implement RFC 8693 token exchange. Register MCP server as OAuth2 client. Add token-exchange grant to `OAuth2TokenFilter`. Replace `JwtForwardingInterceptor` with `OAuth2ClientHttpRequestInterceptor`. Remove `AccessTokenHolder`. This achieves full spec compliance.

**3. The backend's existing OAuth2 AS makes compliance achievable, not aspirational.**
Confidence: High (100%). The presence of DCR, authorization code + PKCE, token endpoint, refresh tokens, and scope definitions means the infrastructure is 70-80% complete. The remaining work extends existing components rather than building new ones.

### Secondary Conclusions

**4. The MCP SDK version gap (0.18.1 vs 1.1.1) should be investigated as a separate task.** It likely does not block security fixes but creates growing upgrade debt across a major version boundary.

**5. In-memory auth storage is a separate production concern.** Authorization codes and refresh tokens stored in memory will be lost on server restart. Should be tracked and addressed independently.

**6. The mcp-security community library is worth monitoring** but not necessary for pre-alpha given the symmetric key setup. It becomes relevant if the project migrates to asymmetric keys or Spring Authorization Server.

### Direct Answer to Research Question

**How should our MCP server handle authentication without token passthrough, per MCP spec?**

The MCP server must act as an OAuth 2.1 Resource Server that validates incoming tokens (signature, expiration, audience, scopes) and obtains a separate token for backend API calls. Concretely:

1. **Validate incoming tokens**: Replace `McpJwtFilter` with Spring Security's `oauth2ResourceServer().jwt()` DSL using `NimbusJwtDecoder.withSecretKey()` to validate HMAC-SHA256 tokens from the backend's OAuth2 AS. Add audience validation to ensure tokens were issued specifically for the MCP server.

2. **Obtain separate downstream tokens**: Implement RFC 8693 token exchange -- the MCP server exchanges the validated client token for a new token scoped to the backend API. This preserves user identity while respecting audience boundaries. As a very short-term interim, client credentials can be used, but token exchange should be implemented before beta.

3. **Remove passthrough infrastructure**: Delete `AccessTokenHolder`, `JwtForwardingInterceptor`, and the current `McpJwtFilter`. Replace with standard Spring Security components.

---

## Recommendations

### Phase 1: Immediate Fix (Token Validation)

| Action | Effort | Risk |
|--------|--------|------|
| Add `spring-boot-starter-oauth2-resource-server` to MCP server pom.xml | Minutes | None |
| Share `app.jwt.secret` with MCP server via environment variable | Minutes | Low (secret management) |
| Create `JwtDecoder` bean with `NimbusJwtDecoder.withSecretKey()` | Hours | None |
| Create `JwtAuthenticationConverter` mapping `scopes` claim to `PERMISSION_` authorities | Hours | None |
| Replace `McpJwtFilter` security config with `oauth2ResourceServer().jwt()` DSL | Hours | Low |
| Add `aud` claim to `JwtTokenProvider.generateOAuth2Token()` in backend | Minutes | Low |
| Add audience validator (`aud` contains "mcp-server") to MCP server `JwtDecoder` | Minutes | None |

**Benefits**: Eliminates the "accept any token" vulnerability. MCP server validates signature, expiration, audience, and scopes. No backend architectural changes needed.

**Limitations**: Token is still forwarded to backend after validation (partial compliance). Full compliance requires Phase 2.

### Phase 2: Proper Fix (Token Exchange)

| Action | Effort | Risk |
|--------|--------|------|
| Register MCP server as confidential OAuth2 client with backend | Hours | Low |
| Add `urn:ietf:params:oauth:grant-type:token-exchange` handler to `OAuth2TokenFilter` | Days | Medium |
| Add `spring-boot-starter-oauth2-client` to MCP server pom.xml | Minutes | None |
| Configure token exchange client registration in MCP server | Hours | Low |
| Replace `JwtForwardingInterceptor` with `OAuth2ClientHttpRequestInterceptor` | Hours | Medium |
| Remove `AccessTokenHolder` and `JwtForwardingInterceptor` | Minutes | None |

**Benefits**: Full MCP spec compliance. Clean audience separation (Token-A for MCP server, Token-B for backend). User identity preserved in backend calls. Standard Spring Security token lifecycle management.

### Separate Tasks (Not Blocking)

| Action | Priority | Rationale |
|--------|----------|-----------|
| Investigate MCP SDK 0.18.1 to 1.1.1 upgrade | Medium | Growing version debt across major boundary |
| Migrate in-memory auth storage to database | Medium | Production reliability concern |
| Evaluate migration to asymmetric keys (RSA/EC) | Low | Eliminates shared secret management |
| Evaluate mcp-security library adoption | Low | Relevant when moving to asymmetric keys |

---

## Appendices

### A. Complete Source List

**MCP Specification**:
- MCP Authorization Spec: https://modelcontextprotocol.io/specification/2025-06-18/basic/authorization
- MCP Security Best Practices: https://modelcontextprotocol.io/specification/2025-06-18/basic/security_best_practices

**RFCs and Standards**:
- RFC 8693 -- OAuth 2.0 Token Exchange
- RFC 8707 -- Resource Indicators for OAuth 2.0
- RFC 9728 -- OAuth 2.0 Protected Resource Metadata
- RFC 9068 -- JWT Profile for OAuth 2.0 Access Tokens
- RFC 8414 -- OAuth 2.0 Authorization Server Metadata
- RFC 7591 -- OAuth 2.0 Dynamic Client Registration
- OAuth 2.1 (draft-ietf-oauth-v2-1-13)

**Industry Sources**:
- Solo.io: MCP Authorization Patterns for Upstream API Calls
- Stacklok: Beyond API Keys -- Token Exchange, Identity Federation & MCP Servers
- FastMCP: OAuth Proxy documentation
- Curity: Design MCP Authorization to Securely Expose APIs
- Auth0: Token Vault -- Secure Token Exchange for AI Agents
- Scalekit: Token Vault for AI Agent Workflows
- Microsoft: OAuth 2.0 On-Behalf-Of Flow

**Spring Ecosystem**:
- Spring Security OAuth2 Resource Server JWT documentation
- Spring Authorization Server 1.3 GA announcement
- Token Exchange support in Spring Security 6.3
- RestClient OAuth2 Support in Spring Security 6.4
- spring-ai-community/mcp-security GitHub repository
- MCP Java SDK documentation

**Codebase Files Analyzed**:
- `mcp-server/pom.xml`
- `McpJwtFilter.java`
- `SecurityConfig.java` (MCP server)
- `RestClientConfig.java`
- `AccessTokenHolder.java`
- `JwtTokenProvider.java`
- `SecurityConfiguration.java` (backend)
- `OAuth2TokenFilter.java`
- `OAuth2AuthorizationFilter.java`
- `PublicClientRegistrationFilter.java`
- `DatabaseRegisteredClientRepository.java`

### B. Gaps and Uncertainties

1. **MCP SDK 0.18.1 to 1.1.1 breaking changes**: Not investigated. Auth integration likely unaffected but transport API changes possible.
2. **mcp-security library custom JwtDecoder override**: Expected to work per Spring Boot convention but not verified.
3. **Spring Security 7 token exchange with custom OAuth2 server**: Documented for Spring Authorization Server; compatibility with custom filters needs verification.
4. **Protected Resource Metadata document content**: Endpoint exists but content not verified against RFC 9728 requirements.
5. **In-memory auth storage scope**: Concern identified but not investigated in detail.

### C. Standards Cross-Reference

| MCP Requirement | Referenced Standard | Our Status |
|----------------|-------------------|-----------|
| Token validation | OAuth 2.1 Section 5.2 | Missing (Phase 1 fix) |
| Audience validation | RFC 8707 Section 2 | Missing -- no `aud` claim (Phase 1 fix) |
| Protected Resource Metadata | RFC 9728 | Partially implemented |
| WWW-Authenticate header | RFC 9728 Section 5.1 | Implemented |
| Resource parameter | RFC 8707 | Not verified |
| PKCE (S256) | OAuth 2.1 | Implemented in backend |
| Dynamic Client Registration | RFC 7591 | Implemented in backend |
| No token passthrough | MCP Auth Spec | Violated (Phase 2 fix) |
