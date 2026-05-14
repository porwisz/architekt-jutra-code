# External Patterns Findings: OAuth 2.0 Proxy Authentication for MCP Servers

## Research Question
How should our MCP server handle authentication without token passthrough, per MCP spec? Specifically: what OAuth 2.0 patterns exist for proxy/intermediary servers that need to call downstream APIs on behalf of users?

---

## Pattern 1: OAuth 2.0 Token Exchange (RFC 8693)

### Overview
RFC 8693 defines a standard mechanism for exchanging one security token for another at an authorization server acting as a Security Token Service (STS). This is the most spec-aligned pattern for MCP proxy servers.

### How It Works
1. MCP client authenticates and obtains Token A (audience: MCP server)
2. MCP server receives Token A, validates it
3. MCP server sends Token A as `subject_token` to the authorization server's token endpoint
4. Authorization server validates Token A, issues Token B (audience: backend API)
5. MCP server uses Token B to call backend API

### Key Parameters (Section 2.1)
| Parameter | Required | Purpose |
|-----------|----------|---------|
| `grant_type` | Yes | `urn:ietf:params:oauth:grant-type:token-exchange` |
| `subject_token` | Yes | The token representing the user (received from MCP client) |
| `subject_token_type` | Yes | Type of subject token (e.g., `urn:ietf:params:oauth:token-type:access_token`) |
| `audience` | No | Logical name of the target backend service |
| `resource` | No | URI of the target backend service |
| `requested_token_type` | No | Desired token format for the new token |
| `actor_token` | No | Token representing the MCP server itself (for delegation) |
| `scope` | No | Requested scopes for the new token |

### Impersonation vs. Delegation (Section 2.1)
- **Impersonation**: New token represents the original user only; MCP server identity is not embedded. Simpler but less auditable.
- **Delegation**: New token contains both subject (user) and actor (MCP server). Uses `actor_token` parameter. More auditable but more complex.

### Applicability to Our MCP Server
- Our backend already has an OAuth2 token endpoint (`OAuth2TokenFilter`)
- Would require adding a new grant type (`token-exchange`) to the backend's token endpoint
- MCP server validates the client token, then exchanges it for a backend-scoped token
- Preserves user identity in the backend token (user-level audit trail maintained)
- The backend `JwtTokenProvider.generateOAuth2Token()` could be extended to support this grant

### Trade-offs
| Aspect | Assessment |
|--------|-----------|
| MCP spec compliance | High -- recommended pattern, respects audience boundaries |
| User context preserved | Yes -- subject_token carries user identity |
| Implementation complexity | Medium -- requires new grant type in backend OAuth2 server |
| Security | Strong -- tokens are audience-scoped, short-lived |
| Standards alignment | RFC 8693 is an IETF standard |

### Evidence
- RFC 8693 Section 2.3 explicitly demonstrates the proxy/resource-server use case: "a resource server exchanging a received OAuth token for a new token to call a backend service"
- Source: https://datatracker.ietf.org/doc/html/rfc8693

---

## Pattern 2: Client Credentials Grant (Service Account)

### Overview
MCP server authenticates to the backend using its own service identity (client_id + client_secret), independent of the user's token. The MCP server validates the user's token locally, then makes backend calls as itself.

### How It Works
1. MCP client authenticates and obtains Token A (audience: MCP server)
2. MCP server validates Token A locally (verifies signature, claims, expiry)
3. MCP server uses its own credentials (client_id/client_secret) to request Token B from the backend
4. Token B represents the MCP server application, not the user
5. MCP server calls backend API with Token B

### Key Parameters
| Parameter | Value |
|-----------|-------|
| `grant_type` | `client_credentials` |
| `client_id` | MCP server's registered client ID |
| `client_secret` | MCP server's secret |
| `scope` | Requested backend scopes (e.g., `mcp:read mcp:edit`) |

### Applicability to Our MCP Server
- Simplest to implement -- our backend already supports OAuth2 client registration (`PublicClientRegistrationFilter`, `DatabaseRegisteredClientRepository`)
- Would need to register the MCP server as a confidential client with the backend
- Backend `SecurityConfiguration` already supports `mcp:read` and `mcp:edit` scopes

### Trade-offs
| Aspect | Assessment |
|--------|-----------|
| MCP spec compliance | Partial -- MCP server validates client token (good) but backend loses user identity |
| User context preserved | No -- backend sees MCP server identity, not the user |
| Implementation complexity | Low -- standard OAuth2 grant, minimal changes |
| Security | Mixed -- clean trust boundary but poor auditability per-user |
| Standards alignment | RFC 6749 Section 4.4 |

### Critical Limitation
The Solo.io article explicitly calls the service account pattern an **anti-pattern** for MCP servers due to:
- High blast radius (MCP server has broad access)
- Violation of least-privilege (all users get same access level)
- Poor auditability (cannot trace actions to specific users)
- Circumvents upstream RBAC

### Evidence
- RFC 6749 Section 4.4: "The client credentials grant type MUST only be used by confidential clients"
- Solo.io: "Using an all-access admin credential in the MCP server" is listed as Pattern 1 anti-pattern
- Source: https://datatracker.ietf.org/doc/html/rfc6749#section-4.4
- Source: https://www.solo.io/blog/mcp-authorization-patterns-for-upstream-api-calls

---

## Pattern 3: On-Behalf-Of (OBO) Flow

### Overview
A specific implementation of token exchange where the middle-tier service (MCP server) presents the user's token to an authorization server and receives a new token that preserves user identity but is scoped for a different audience (the backend API). Microsoft Entra ID popularized this pattern; it maps closely to RFC 8693 delegation semantics.

### How It Works
1. MCP client authenticates and obtains Token A (audience: MCP server)
2. MCP server receives request with Token A
3. MCP server authenticates itself (client_id + client_secret) to the authorization server
4. MCP server sends Token A as an `assertion` along with `requested_token_use=on_behalf_of`
5. Authorization server validates both the MCP server's credentials and Token A
6. Authorization server issues Token B: audience = backend API, subject = original user
7. MCP server calls backend with Token B

### Key Parameters (Microsoft Entra variant)
| Parameter | Required | Purpose |
|-----------|----------|---------|
| `grant_type` | Yes | `urn:ietf:params:oauth:grant-type:jwt-bearer` |
| `client_id` | Yes | MCP server's application ID |
| `client_secret` | Yes | MCP server's secret (or certificate assertion) |
| `assertion` | Yes | The access token received from the client (Token A) |
| `scope` | Yes | Scopes for the backend API |
| `requested_token_use` | Yes | `on_behalf_of` |

### Difference from Generic Token Exchange
- OBO is more prescriptive: always produces a delegated token preserving user identity
- RFC 8693 is more flexible: supports impersonation, delegation, and arbitrary token types
- OBO requires the middle tier to authenticate itself (confidential client)
- OBO automatically handles consent chaining (user consents to middle tier + downstream in one step)

### Applicability to Our MCP Server
- Very applicable -- preserves user identity while respecting audience boundaries
- Conceptually similar to token exchange but with clearer semantics for our use case
- Our backend would need to support this as a new grant type
- The MCP server would need to be registered as a confidential client

### Trade-offs
| Aspect | Assessment |
|--------|-----------|
| MCP spec compliance | High -- validates token, exchanges for audience-scoped token |
| User context preserved | Yes -- new token retains user identity and permissions |
| Implementation complexity | Medium-High -- requires authorization server support for OBO |
| Security | Strong -- two-party validation (MCP server + user token) |
| Standards alignment | Microsoft-specific but maps to RFC 8693 delegation |

### Evidence
- Microsoft Learn: "The on-behalf-of (OBO) flow describes the scenario of a web API using an identity other than its own to call another web API. Referred to as delegation in OAuth, the intent is to pass a user's identity and permissions through the request chain."
- Source: https://learn.microsoft.com/en-us/entra/identity-platform/v2-oauth2-on-behalf-of-flow

---

## Pattern 4: Credential Vaulting (Token Vault)

### Overview
MCP server manages a secure, centralized store for user credentials/tokens for downstream services. Users authorize the MCP server to act on their behalf through a one-time consent flow. The vault stores, encrypts, refreshes, and serves tokens on demand.

### How It Works
1. User completes a one-time OAuth consent flow, authorizing the MCP server to access the backend on their behalf
2. Backend issues access token + refresh token; vault encrypts and stores them
3. On each MCP request, MCP server validates the client token, looks up the stored backend token for that user
4. MCP server calls backend API with the stored token
5. Vault automatically refreshes tokens in the background before expiry

### Architecture
```
MCP Client --> MCP Server --> Token Vault --> Backend API
                  |                |
                  |  (validates    |  (retrieves stored
                  |   client       |   backend token
                  |   token)       |   for user)
                  |                |
```

### Key Components
- **Consent flow**: One-time user authorization via browser redirect
- **Encrypted storage**: Tokens stored encrypted at rest (e.g., Fernet encryption)
- **Automatic refresh**: Background scheduler rotates tokens using refresh tokens
- **Scoped retrieval**: MCP server requests tokens scoped to specific user + service

### Industry Adoption
- **Auth0 Token Vault**: Purpose-built for AI agent workflows and MCP servers. Implements token exchange allowing agents to exchange user authorization for short-lived access tokens.
- **FastMCP OAuth Proxy**: Stores upstream provider tokens server-side, encrypted. Issues its own JWTs to MCP clients. "MCP clients only ever receive FastMCP-issued JWTs -- the upstream provider token is never sent to the client."

### Applicability to Our MCP Server
- Most relevant for multi-provider scenarios (e.g., MCP server accessing GitHub, Slack, etc.)
- For our single-backend architecture, this is overengineered -- token exchange (Pattern 1) is simpler
- However, the FastMCP variant (issuing own JWTs, storing backend tokens) is worth noting as a reference implementation

### Trade-offs
| Aspect | Assessment |
|--------|-----------|
| MCP spec compliance | High -- no token passthrough, proper audience separation |
| User context preserved | Yes -- per-user tokens stored |
| Implementation complexity | High -- requires vault infrastructure, encryption, refresh scheduling |
| Security | Strong -- centralized management, audit trail, auto-revocation |
| Standards alignment | Implementation pattern, not a standard itself |

### Evidence
- Scalekit: "A token vault is a secure, centralized service for managing authentication credentials used by AI agents."
- Auth0: "Auth0 Token Vault acts as a secure, centralized store for provider tokens" with "impersonation where the client presents a subject_token and the authorization server issues a new token"
- FastMCP: "Instead of directly forwarding tokens from the upstream OAuth provider, it issues its own JWT tokens to MCP clients. This maintains proper OAuth 2.0 token audience boundaries."
- Source: https://www.scalekit.com/blog/token-vault-ai-agent-workflows
- Source: https://auth0.com/blog/auth0-token-vault-secure-token-exchange-for-ai-agents/
- Source: https://gofastmcp.com/servers/auth/oauth-proxy

---

## Pattern 5: Secure Infrastructure Offloading (Gateway Pattern)

### Overview
Authentication and credential management is extracted from the MCP server entirely and handled by trusted infrastructure (API gateway, agent gateway). The gateway validates tokens, performs exchanges, and injects appropriate credentials before requests reach the MCP server or backend.

### How It Works
1. MCP client sends request with Token A to gateway
2. Gateway validates Token A
3. Gateway performs token exchange (RFC 8693) to get Token B for backend
4. Gateway forwards request to MCP server with Token B (or injects it)
5. MCP server calls backend with the gateway-provided token

### Key Implementations
- **Solo.io agentgateway**: Extracts credential management to trusted infrastructure; handles token exchange, policy enforcement, and credential injection
- **Azure API Management**: Provides MCP server security through gateway-level token management
- **Red Hat MCP Gateway**: Advanced authentication and authorization at the gateway level

### Applicability to Our MCP Server
- Overkill for our pre-alpha stage with a single backend
- More appropriate for enterprise deployments with multiple downstream services
- Worth noting as a future evolution path

### Trade-offs
| Aspect | Assessment |
|--------|-----------|
| MCP spec compliance | High -- complete separation of concerns |
| User context preserved | Yes -- gateway handles exchange preserving identity |
| Implementation complexity | Very High -- requires gateway infrastructure |
| Security | Strongest -- MCP server never touches sensitive tokens |
| Standards alignment | Enterprise pattern using standard OAuth2 flows |

### Evidence
- Solo.io advocates Pattern 5 as enterprise-ready: "offloading authorization to secure infrastructure enables policy enforcement, credential lifecycle management, and auditability"
- Source: https://www.solo.io/blog/mcp-authorization-patterns-for-upstream-api-calls

---

## MCP-Specific Community Patterns

### What the MCP Community Recommends

**MCP Specification Position (2025-03-26 and later)**:
- MCP server acts as OAuth 2.1 Resource Server (validates tokens, does NOT forward them)
- MCP client drives the OAuth2 flow
- Protected Resource Metadata (RFC 9728) advertises which authorization server to use
- 2026-03-15 spec mandates RFC 8707 resource indicators to prevent token mis-redemption

**Token Passthrough is Explicitly Prohibited**:
- MCP spec: "Servers MUST NOT request sensitive information through elicitation"
- Passing client tokens directly to backend APIs violates audience boundaries
- Creates confused deputy attack vectors

**Emerging Pattern: URL Elicitation**:
- Recently approved MCP proposal
- MCP server initiates a callback URL directing users to authenticate directly with upstream providers
- Keeps credentials away from MCP client entirely
- Relevant for multi-provider scenarios

**ToolHive (Stacklok) Approach**:
- Acts as a gateway: validates user tokens, enforces access policies
- Acquires backend tokens through exchange or federation
- MCP server remains "auth-agnostic"

### Evidence
- Solo.io documents 5 patterns, recommends infrastructure offloading for enterprise
- Stacklok/ToolHive: "Rather than MCP servers handling authentication directly, ToolHive acts as a gateway"
- FastMCP: Implements token factory pattern -- issues own JWTs, stores upstream tokens encrypted
- Source: https://dev.to/stacklok/beyond-api-keys-token-exchange-identity-federation-mcp-servers-5dm8
- Source: https://gofastmcp.com/servers/auth/oauth-proxy

---

## Pattern Comparison Matrix

| Pattern | Spec Compliance | User Context | Complexity | Security | Best For |
|---------|----------------|-------------|------------|----------|----------|
| **Token Exchange (RFC 8693)** | High | Preserved | Medium | Strong | Single-backend with user-level access |
| **Client Credentials** | Partial | Lost | Low | Mixed | Service-to-service without user context |
| **On-Behalf-Of (OBO)** | High | Preserved | Medium-High | Strong | Microsoft ecosystems, enterprise |
| **Credential Vaulting** | High | Preserved | High | Strong | Multi-provider scenarios |
| **Gateway Offloading** | High | Preserved | Very High | Strongest | Enterprise multi-service deployments |

---

## Recommendation for Our Pre-Alpha MCP Server

### Primary: Token Exchange (RFC 8693)

**Rationale**:
1. Directly addresses the spec requirement -- MCP server validates client token and exchanges it for a backend-scoped token
2. Preserves user identity in backend calls (audit trail, per-user authorization)
3. Medium implementation complexity -- requires adding token-exchange grant to our existing OAuth2 server
4. Our backend already has `JwtTokenProvider.generateOAuth2Token()` which can be extended
5. Our backend already has `OAuth2TokenFilter` handling grant types -- adding token-exchange is incremental
6. Clean separation: MCP server token has `aud=mcp-server`, backend token has `aud=backend-api`

### Fallback: Client Credentials (Interim)

If token exchange is too complex for the immediate pre-alpha milestone:
1. Register MCP server as a confidential client with the backend
2. MCP server validates client token locally, then uses client_credentials to get a backend token
3. Embed user ID as a custom claim or header to partially preserve user context
4. Mark as tech debt -- upgrade to token exchange before beta

### Not Recommended for Now
- Credential Vaulting: Overengineered for single-backend architecture
- Gateway Offloading: Requires infrastructure we do not have
- OBO: Microsoft-specific semantics; token exchange (RFC 8693) is the standard equivalent

---

## Sources

### RFCs and Standards
- [RFC 8693 - OAuth 2.0 Token Exchange](https://datatracker.ietf.org/doc/html/rfc8693)
- [RFC 6749 - OAuth 2.0 Framework (Client Credentials)](https://datatracker.ietf.org/doc/html/rfc6749#section-4.4)

### MCP-Specific Resources
- [Solo.io - MCP Authorization Patterns for Upstream API Calls](https://www.solo.io/blog/mcp-authorization-patterns-for-upstream-api-calls)
- [Stacklok - Beyond API Keys: Token Exchange, Identity Federation & MCP Servers](https://dev.to/stacklok/beyond-api-keys-token-exchange-identity-federation-mcp-servers-5dm8)
- [FastMCP - OAuth Proxy](https://gofastmcp.com/servers/auth/oauth-proxy)
- [Curity - Design MCP Authorization to Securely Expose APIs](https://curity.io/resources/learn/design-mcp-authorization-apis/)
- [MCP Authorization Specification](https://modelcontextprotocol.io/specification/draft/basic/authorization)
- [Securing Spring AI MCP servers with OAuth2](https://spring.io/blog/2025/04/02/mcp-server-oauth2/)

### On-Behalf-Of Flow
- [Microsoft - OAuth 2.0 On-Behalf-Of flow](https://learn.microsoft.com/en-us/entra/identity-platform/v2-oauth2-on-behalf-of-flow)
- [Okta - Set up OAuth 2.0 On-Behalf-Of Token Exchange](https://developer.okta.com/docs/guides/set-up-token-exchange/main/)

### Token Vault / Credential Management
- [Scalekit - What is a token vault? Secure credential management for AI agent workflows](https://www.scalekit.com/blog/token-vault-ai-agent-workflows)
- [Auth0 - Token Vault: Secure Token Exchange for AI Agents](https://auth0.com/blog/auth0-token-vault-secure-token-exchange-for-ai-agents/)

### Additional Context
- [The New MCP Authorization Specification (2026)](https://dasroot.net/posts/2026/04/mcp-authorization-specification-oauth-2-1-resource-indicators/)
- [MCP Auth - OAuth Gateway for MCP Servers](https://mcp-auth.cloud/)
- [Red Hat - Advanced authentication and authorization for MCP Gateway](https://developers.redhat.com/articles/2025/12/12/advanced-authentication-authorization-mcp-gateway)
