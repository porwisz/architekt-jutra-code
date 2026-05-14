# Scope Clarifications

## Decisions Made

### Critical Decisions
1. **Introspection endpoint auth model**: Same pattern as /oauth2/token — permitAll at filter chain, client auth handled inside the introspection filter. Consistent with existing codebase.
2. **MCP server client registration**: Environment variables (client_id + client_secret). Pre-registered out-of-band. Simplest for pre-alpha.
3. **Introspection approach**: RFC 7662 remote call (honor ADR-001). Centralizes validation at authorization server.
4. **Audience enforcement**: MCP server enforces aud=mcp-server via introspection. Backend remains permissive (web UI login tokens lack aud).

### Important Decisions
5. **Introspection caching**: None — every MCP request triggers backend introspection call. Prioritizes security over latency.
6. **Service auth awareness**: Services read user identity from SecurityContext for logging. Not fully auth-unaware but no longer manage tokens.
7. **Exchange audience value**: Backend issuer URL (from aj.oauth.server-url config).
