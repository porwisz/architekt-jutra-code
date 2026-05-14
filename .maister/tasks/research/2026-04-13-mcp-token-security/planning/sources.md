# Research Sources

## MCP Specification Sources

### Specification Documents
- MCP Authorization spec: `https://spec.modelcontextprotocol.io/latest/basic/authorization/`
- MCP security best practices: `https://modelcontextprotocol.io/specification/2025-06-18/basic/security`
- MCP spec root (for cross-references): `https://spec.modelcontextprotocol.io/latest/`

### Key Topics to Extract
- Token passthrough anti-pattern definition and prohibition
- MCP server token validation requirements
- Downstream API access patterns recommended by spec
- Protected Resource Metadata requirements
- OAuth 2.0 flow requirements for MCP servers

---

## Codebase Sources

### MCP Server — Security Layer
- `mcp-server/src/main/java/pl/devstyle/aj/mcp/security/McpJwtFilter.java` — Current JWT filter (no validation, trust-and-forward)
- `mcp-server/src/main/java/pl/devstyle/aj/mcp/security/AccessTokenHolder.java` — ThreadLocal token storage for forwarding
- `mcp-server/src/main/java/pl/devstyle/aj/mcp/security/McpAuthenticationEntryPoint.java` — 401 response with WWW-Authenticate header
- `mcp-server/src/main/java/pl/devstyle/aj/mcp/config/SecurityConfig.java` — Spring Security filter chain configuration

### MCP Server — Token Forwarding
- `mcp-server/src/main/java/pl/devstyle/aj/mcp/config/RestClientConfig.java` — JwtForwardingInterceptor (passthrough to backend)
- `mcp-server/src/main/java/pl/devstyle/aj/mcp/client/AjApiClient.java` — HTTP interface client for backend API

### MCP Server — OAuth2 Discovery
- `mcp-server/src/main/java/pl/devstyle/aj/mcp/controller/WellKnownController.java` — Protected Resource Metadata endpoint

### MCP Server — Configuration
- `mcp-server/src/main/resources/application.yml` — Server config (backend URL, OAuth server URL, MCP base URL)
- `mcp-server/pom.xml` — Dependencies (MCP SDK 0.18.1, Spring Boot 4.0.5, Spring Security)

### MCP Server — Service Layer
- `mcp-server/src/main/java/pl/devstyle/aj/mcp/service/ProductService.java` — Tool handler (uses AccessTokenHolder)
- `mcp-server/src/main/java/pl/devstyle/aj/mcp/service/CategoryService.java` — Tool handler (uses AccessTokenHolder)

### Backend — JWT Infrastructure
- `src/main/java/pl/devstyle/aj/core/security/JwtTokenProvider.java` — HMAC-SHA JWT creation and validation (includes generateOAuth2Token)
- `src/main/java/pl/devstyle/aj/core/security/JwtAuthenticationFilter.java` — Backend JWT validation filter
- `src/main/java/pl/devstyle/aj/core/security/SecurityConfiguration.java` — Backend security chain (already supports MCP scopes: mcp:read, mcp:edit)

### Backend — OAuth2 Server
- `src/main/java/pl/devstyle/aj/core/oauth2/OAuth2MetadataController.java` — Authorization Server Metadata
- `src/main/java/pl/devstyle/aj/core/oauth2/OAuth2TokenFilter.java` — Token endpoint (authorization_code, refresh_token grants)
- `src/main/java/pl/devstyle/aj/core/oauth2/OAuth2AuthorizationFilter.java` — Authorization endpoint
- `src/main/java/pl/devstyle/aj/core/oauth2/PublicClientRegistrationFilter.java` — Dynamic Client Registration (RFC 7591)
- `src/main/java/pl/devstyle/aj/core/oauth2/AuthorizationCodeService.java` — Authorization code management
- `src/main/java/pl/devstyle/aj/core/oauth2/RefreshTokenService.java` — Refresh token management
- `src/main/java/pl/devstyle/aj/core/oauth2/DatabaseRegisteredClientRepository.java` — Client registration persistence

### MCP Server — Tests
- `mcp-server/src/test/java/pl/devstyle/aj/mcp/security/McpJwtFilterTests.java` — Filter behavior tests
- `mcp-server/src/test/java/pl/devstyle/aj/mcp/security/AccessTokenHolderTests.java` — Token holder tests
- `mcp-server/src/test/java/pl/devstyle/aj/mcp/config/RestClientConfigTests.java` — RestClient/interceptor tests

### Project Standards
- `.maister/docs/standards/backend/security.md` — JWT authentication pattern standards
- `.maister/docs/standards/backend/plugin-auth.md` — Plugin authentication via browser SDK and server SDK
- `.claude/skills/mcp-server-designer/SKILL.md` — MCP server design reference (auth section)

---

## Configuration Sources

- `mcp-server/src/main/resources/application.yml` — MCP server configuration
- `mcp-server/pom.xml` — MCP SDK version (0.18.1), Spring Boot version (4.0.5)
- Root `pom.xml` — Backend dependency versions

---

## External Sources

### MCP Specification & Guides
- MCP Authorization spec: `https://spec.modelcontextprotocol.io/latest/basic/authorization/`
- MCP security considerations: `https://modelcontextprotocol.io/specification/2025-06-18/basic/security`
- MCP spec GitHub (for issues/discussions on auth): `https://github.com/modelcontextprotocol/specification`

### OAuth 2.0 Standards
- RFC 8693 — OAuth 2.0 Token Exchange: `https://datatracker.ietf.org/doc/html/rfc8693`
- RFC 6749 — OAuth 2.0 Framework (client credentials grant): `https://datatracker.ietf.org/doc/html/rfc6749#section-4.4`
- RFC 7591 — Dynamic Client Registration: `https://datatracker.ietf.org/doc/html/rfc7591`

### Spring Ecosystem
- Spring Security OAuth2 Resource Server: `https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/`
- Spring Authorization Server: `https://docs.spring.io/spring-authorization-server/reference/`
- Spring Security 7 migration guide: `https://docs.spring.io/spring-security/reference/migration-7/`

### MCP SDK
- MCP Java SDK GitHub: `https://github.com/modelcontextprotocol/java-sdk`
- MCP SDK 0.18.1 release / changelog
- WebMvcStatelessServerTransport source and auth hooks
