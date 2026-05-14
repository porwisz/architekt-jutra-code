# MCP Specification Findings: Authorization & Token Security

## Research Question
How should our MCP server handle authentication without token passthrough, per MCP spec?

---

## 1. MCP Authorization Specification Requirements

**Source**: https://modelcontextprotocol.io/specification/2025-06-18/basic/authorization (also verified at /latest/)

### 1.1 Roles and Architecture

The MCP spec defines three OAuth 2.1 roles:

- **MCP Server** = OAuth 2.1 **Resource Server** (accepts access tokens, serves protected resources)
- **MCP Client** = OAuth 2.1 **Client** (obtains tokens, makes requests on behalf of resource owner)
- **Authorization Server** = issues access tokens for use at the MCP server. "The implementation details of the authorization server are beyond the scope of this specification. It may be hosted with the resource server or a separate entity."

**Source**: Authorization spec, "Roles" section

### 1.2 Core Normative Requirements (MUST)

#### Authorization Server Requirements
- Authorization servers **MUST** implement OAuth 2.1 with appropriate security measures for both confidential and public clients
- Authorization servers **MUST** provide OAuth 2.0 Authorization Server Metadata (RFC 8414) OR OpenID Connect Discovery 1.0
- Authorization servers **SHOULD** issue short-lived access tokens
- For public clients, authorization servers **MUST** rotate refresh tokens

#### MCP Server (Resource Server) Requirements
- MCP servers **MUST** implement OAuth 2.0 Protected Resource Metadata (RFC 9728)
- MCP servers **MUST** validate access tokens as described in OAuth 2.1 Section 5.2
- MCP servers **MUST** validate that access tokens were issued specifically for them as the intended audience (RFC 8707 Section 2)
- MCP servers **MUST** only accept tokens specifically intended for themselves
- MCP servers **MUST** reject tokens that do not include them in the audience claim
- MCP servers **MUST NOT** accept or transit any other tokens
- MCP servers **MUST** use HTTP `WWW-Authenticate` header when returning 401 Unauthorized (per RFC 9728 Section 5.1)
- If validation fails, servers **MUST** respond per OAuth 2.1 Section 5.3 error handling
- Invalid or expired tokens **MUST** receive HTTP 401

#### MCP Client Requirements
- MCP clients **MUST** use Protected Resource Metadata for authorization server discovery
- MCP clients **MUST** use Authorization request header: `Authorization: Bearer <access-token>`
- Authorization **MUST** be included in every HTTP request (even if same logical session)
- Access tokens **MUST NOT** be in URI query string
- MCP clients **MUST NOT** send tokens other than ones issued by the MCP server's authorization server
- MCP clients **MUST** implement Resource Indicators (RFC 8707) with `resource` parameter
- MCP clients **MUST** implement PKCE (S256 method)

**Source**: Authorization spec, "Overview", "Access Token Usage", and "Security Considerations" sections

### 1.3 Critical Token Handling Rule for Downstream APIs

The spec contains an explicit rule about MCP servers calling upstream/downstream APIs:

> "If the MCP server makes requests to upstream APIs, it may act as an OAuth client to them. The access token used at the upstream API is a **separate token**, issued by the upstream authorization server. The MCP server **MUST NOT** pass through the token it received from the MCP client."

**Source**: Authorization spec, "Access Token Privilege Restriction" section

This is the single most important sentence for our architecture. It means:
1. The MCP server receives Token-A from the MCP client (issued by the MCP server's auth server)
2. When calling the backend API, the MCP server must use Token-B (a separate token, issued by/for the backend)
3. Token-A MUST NOT be forwarded to the backend

### 1.4 Protected Resource Metadata (RFC 9728)

MCP servers MUST serve Protected Resource Metadata. Discovery mechanisms (at least one MUST be implemented):

1. **WWW-Authenticate Header**: Include `resource_metadata` URL in the header on 401 responses
2. **Well-Known URI**: Serve at `/.well-known/oauth-protected-resource` (with optional path suffix)

The metadata document MUST include `authorization_servers` field with at least one authorization server.

**Source**: Authorization spec, "Authorization Server Location" and "Protected Resource Metadata Discovery Requirements"

### 1.5 Dynamic Client Registration

- MCP clients and authorization servers **MAY** support RFC 7591 Dynamic Client Registration (changed from SHOULD in the 2025-06-18 version to MAY in latest)
- The latest spec also introduces **Client ID Metadata Documents** as the preferred registration approach
- Pre-registration is also supported as a fallback

**Source**: Authorization spec, "Client Registration Approaches" section

### 1.6 Resource Parameter Implementation (RFC 8707)

- MCP clients **MUST** include the `resource` parameter in both authorization and token requests
- The `resource` parameter identifies the MCP server the client intends to use the token with
- Must use the canonical URI of the MCP server (e.g., `https://mcp.example.com`)
- Clients MUST send this parameter regardless of whether the authorization server supports it

**Source**: Authorization spec, "Resource Parameter Implementation" section

### 1.7 Standards Referenced

| Standard | Usage |
|----------|-------|
| OAuth 2.1 (draft-ietf-oauth-v2-1-13) | Base authorization framework |
| RFC 8414 | Authorization Server Metadata |
| RFC 7591 | Dynamic Client Registration |
| RFC 9728 | Protected Resource Metadata |
| RFC 8707 | Resource Indicators (token audience binding) |
| RFC 9068 | JWT Access Token Profile (audience claim) |
| OAuth Client ID Metadata Document (draft) | Client ID as HTTPS URL |

---

## 2. Token Passthrough Anti-Pattern (Security Best Practices)

**Source**: https://modelcontextprotocol.io/specification/2025-06-18/basic/security_best_practices#token-passthrough (also at /latest/)

### 2.1 Definition

> "Token passthrough" is an anti-pattern where an MCP server accepts tokens from an MCP client without validating that the tokens were properly issued *to the MCP server* and passes them through to the downstream API.

### 2.2 Prohibition

> Token passthrough is explicitly forbidden in the authorization specification.

Mitigation requirement:
> MCP servers **MUST NOT** accept any tokens that were not explicitly issued for the MCP server.

### 2.3 Risks (Complete List)

**Risk 1: Security Control Circumvention**
- MCP Server or downstream APIs may implement rate limiting, request validation, or traffic monitoring that depend on token audience or credential constraints
- If clients use tokens directly with downstream APIs without proper MCP server validation, they bypass these controls

**Risk 2: Accountability and Audit Trail Issues**
- MCP Server cannot identify or distinguish between MCP Clients when they call with an upstream-issued access token (opaque to the MCP Server)
- Downstream Resource Server logs show requests from a different source with a different identity (not the MCP server that's actually forwarding)
- Both factors make incident investigation, controls, and auditing more difficult
- A malicious actor with a stolen token can use the server as a proxy for data exfiltration

**Risk 3: Trust Boundary Issues**
- Downstream Resource Server grants trust to specific entities with assumptions about origin or behavior patterns
- Breaking this trust boundary leads to unexpected issues
- If token is accepted by multiple services without proper validation, compromising one service gives access to all connected services

**Risk 4: Future Compatibility Risk**
- Even if MCP Server starts as a "pure proxy" today, it may need security controls later
- Starting with proper token audience separation makes it easier to evolve the security model

### 2.4 Access Token Privilege Restriction (Two Dimensions)

From the Authorization spec's Security Considerations:

1. **Audience validation failures** -- MCP server doesn't verify tokens were intended for it (e.g., via audience claim per RFC 9068). Allows attackers to reuse legitimate tokens across different services.

2. **Token passthrough** -- MCP server accepts tokens with incorrect audiences AND forwards them unmodified to downstream services. Causes the "confused deputy" problem where downstream API incorrectly trusts the token.

---

## 3. What "Tokens Issued for the MCP Server" Means Concretely

Based on the spec, for tokens to be "issued for the MCP server":

1. **Audience binding**: The token's audience claim (per RFC 9068, RFC 8707) must identify the MCP server as the intended recipient
2. **Issuer trust**: The token must come from the authorization server that the MCP server trusts (as declared in its Protected Resource Metadata)
3. **Resource parameter**: The MCP client must have requested the token with `resource=<MCP-server-canonical-URI>` per RFC 8707
4. **Validation**: The MCP server must validate signature, expiration, audience, and scopes before processing any request

For our architecture, this means:
- The backend's OAuth2 server issues tokens with the MCP server's URI as the audience
- The MCP server validates these tokens (signature, audience, expiration)
- When calling the backend API, the MCP server obtains a **separate** token (e.g., via token exchange or client credentials)

---

## 4. Confused Deputy Problem

**Source**: Security Best Practices, "Confused Deputy Problem" section

This is relevant because our MCP server acts as a proxy to the backend API.

### 4.1 Vulnerable Conditions (all must be present)
- MCP proxy server uses a **static client ID** with a third-party authorization server
- MCP proxy server allows MCP clients to **dynamically register**
- Third-party authorization server sets a **consent cookie** after first authorization
- MCP proxy server does not implement proper per-client consent before forwarding

### 4.2 Mitigation
- MCP proxy servers using static client IDs **MUST** obtain user consent for each dynamically registered client before forwarding to third-party authorization servers
- Must maintain per-client consent storage
- Must implement consent UI with CSRF protection

---

## 5. MCP SDK Authorization Support

### 5.1 MCP Java SDK Status

**Source**: https://github.com/modelcontextprotocol/java-sdk/releases

The MCP Java SDK is currently at **v1.1.1** (released 2026-03-27). Our project uses v0.18.1, which is significantly behind. The SDK has undergone a major version bump (0.x to 1.x).

The core MCP Java SDK is **transport-agnostic**. Authorization/security is NOT built into the SDK itself but delegated to:
- Spring Security integration (via separate libraries)
- Transport-level configuration

### 5.2 spring-ai-community/mcp-security Library

**Source**: https://github.com/spring-ai-community/mcp-security

A community library providing Spring Security configuration for MCP servers:

**Features**:
- OAuth 2.0 Resource Server capabilities for MCP servers
- `McpServerOAuth2Configurer` for Spring Security filter chain integration
- JWT validation with issuer URI
- Optional audience claim validation (RFC 8707 resource indicators)
- API-key based authentication alternative
- Method-level security (`@PreAuthorize`) for tool execution
- Dynamic Client Registration support
- Scope step-up (automatic re-authorization for additional permissions)

**MCP Client OAuth2 Flows Supported**:
1. Authorization Code (user-present)
2. Client Credentials (machine-to-machine)
3. Hybrid (mixed user/machine tokens)

**Version Compatibility**:
- Java 17+
- Spring AI 2.0.x (for mcp-security 0.1.x)
- Spring AI 1.1.x (for mcp-security 0.0.6)
- Current stable: **0.1.6**

**Limitations**:
- WebFlux servers unsupported
- JWT-only (no opaque token support)
- Deprecated SSE transport incompatible

**Configuration Example** (from README):
```java
@Bean
SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http
        .with(new McpServerOAuth2Configurer(), configurer -> configurer
            .issuerUri("https://auth.example.com")
            .validateAudienceClaim(true))
        .build();
}
```

**Property**: `spring.security.oauth2.resourceserver.jwt.issuer-uri`

---

## 6. Scope Minimization Best Practices

**Source**: Security Best Practices, "Scope Minimization" section

Relevant for our MCP server's scope design:

- Start with minimal initial scope set (e.g., `mcp:tools-basic`) for low-risk discovery/read
- Use incremental elevation via `WWW-Authenticate` `scope="..."` challenges for privileged operations
- Server should accept reduced scope tokens
- Avoid publishing all possible scopes in `scopes_supported`
- Avoid wildcard/omnibus scopes (`*`, `all`, `full-access`)
- Our existing `mcp:read` and `mcp:edit` scopes align with this guidance

---

## 7. Summary of Spec-Mandated Architecture for Our Case

Based on all gathered spec text, here is what our MCP server architecture MUST look like:

```
MCP Client                  MCP Server (Resource Server)           Backend API
   |                              |                                    |
   |-- Token-A (for MCP server) ->|                                    |
   |                              |-- Validate Token-A                 |
   |                              |   (audience = MCP server URI)      |
   |                              |   (issuer = our OAuth2 server)     |
   |                              |   (signature, expiry, scopes)      |
   |                              |                                    |
   |                              |-- Token-B (separate, for backend)->|
   |                              |   (obtained via token exchange,    |
   |                              |    client credentials, or          |
   |                              |    service account)                |
   |                              |                                    |
   |<-- MCP Response -------------|<-- API Response -------------------|
```

**Key constraints**:
1. Token-A MUST be validated by the MCP server (not just forwarded)
2. Token-A MUST have the MCP server as its audience
3. Token-B MUST be a separate token (MUST NOT be Token-A)
4. The MCP server MUST implement Protected Resource Metadata (RFC 9728)
5. The MCP server MUST return 401 with WWW-Authenticate header for unauthenticated requests

---

## 8. Confidence Assessment

| Finding | Confidence | Basis |
|---------|-----------|-------|
| Token passthrough is forbidden | **High (100%)** | Explicit MUST NOT in both auth spec and security best practices |
| MCP server must validate tokens as resource server | **High (100%)** | Multiple MUST statements in auth spec |
| Downstream API requires separate token | **High (100%)** | Explicit statement: "access token used at upstream API is a separate token" |
| Protected Resource Metadata required | **High (100%)** | Explicit MUST in auth spec |
| RFC 8707 resource parameter required | **High (100%)** | Explicit MUST for clients, MUST validate for servers |
| MCP Java SDK 0.18.1 has no built-in auth | **High (95%)** | SDK is transport-agnostic; auth is framework-level concern |
| mcp-security library provides resource server support | **High (90%)** | Verified from GitHub README; version compatibility needs checking |
| Dynamic Client Registration changed from SHOULD to MAY | **Medium (80%)** | Observed difference between 2025-06-18 and latest spec versions |
