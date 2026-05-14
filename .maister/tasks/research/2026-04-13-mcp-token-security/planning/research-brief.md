# Research Brief: MCP Token Security

## Research Question

Our MCP server implementation uses a "token passthrough" pattern (forwarding client JWT tokens directly to the backend API without validation). This is explicitly forbidden by the MCP specification. What is the correct approach to handle authentication between MCP clients, the MCP server, and the downstream backend API?

## Research Type

**Literature** — best practices, industry standards, MCP specification compliance

## Scope

### Included
- MCP Authorization specification and security best practices
- Token passthrough risks and mitigations per MCP spec
- OAuth 2.0 patterns for MCP proxy servers
- Our current implementation analysis (mcp-server module)
- Backend JWT infrastructure (JwtTokenProvider, SecurityConfiguration)
- MCP SDK capabilities (v0.18.1, `mcp-spring-webmvc`)

### Excluded
- General OAuth2 theory unrelated to MCP
- Non-Java MCP implementations
- Frontend/plugin auth (separate concern, already documented)

### Constraints
- Must work with existing backend JWT infrastructure (HMAC-SHA, `app.jwt.secret`)
- Spring Boot 4.0.5 / Spring Security 7
- MCP SDK 0.18.1 (`WebMvcStatelessServerTransport`)
- Pre-alpha phase — acceptable to make breaking changes

## Current State (Problem)

### Token Flow (Trust-and-Forward)
1. MCP client sends request with Bearer token in Authorization header
2. `McpJwtFilter` extracts token but does NOT validate — creates dummy SecurityContext
3. `contextExtractor` stores raw token in `McpTransportContext`
4. Tool handlers pass token to service methods
5. `AccessTokenHolder` (ThreadLocal) stores token for RestClient interceptor
6. `JwtForwardingInterceptor` adds the SAME token to backend API calls
7. Backend `JwtAuthenticationFilter` validates the token

### Why This Is Wrong (per MCP spec)
The MCP spec explicitly states: "Token passthrough is an anti-pattern where an MCP server accepts tokens from an MCP client without validating that the tokens were properly issued to the MCP server and passes them through to the downstream API."

Risks identified by the spec:
- Security control circumvention (rate limiting, validation bypass)
- Accountability/audit trail issues (can't distinguish MCP clients)
- Trust boundary violations
- Lateral movement if token is compromised
- Future compatibility risk

## Success Criteria

1. Understand the MCP-compliant authentication architecture
2. Identify concrete approaches that fit our Spring Boot + HMAC JWT stack
3. Assess trade-offs (complexity, security, spec compliance)
4. Recommend an approach suitable for our pre-alpha stage
