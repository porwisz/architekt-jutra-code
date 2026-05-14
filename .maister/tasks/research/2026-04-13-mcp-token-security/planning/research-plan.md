# Research Plan: MCP Token Security

## Research Overview

### Research Question
How should our MCP server handle authentication without token passthrough, per MCP spec? Specifically: what is the MCP-compliant architecture for authenticating MCP clients, validating tokens at the MCP server layer, and making authenticated calls to the downstream backend API?

### Research Type
**Literature** with **Technical** codebase analysis — MCP specification compliance, OAuth 2.0 proxy patterns, and current implementation gap analysis.

### Scope
- **In scope**: MCP Authorization spec, token passthrough prohibition, OAuth 2.0 proxy/relay patterns, current mcp-server implementation, backend JWT/OAuth2 infrastructure, MCP SDK 0.18.1 capabilities
- **Out of scope**: General OAuth2 theory, non-Java MCP implementations, frontend/plugin auth flows

### Sub-Questions
1. What does the MCP Authorization specification require for token handling at the MCP server?
2. What alternatives to token passthrough does the spec recommend (token exchange, service credentials, etc.)?
3. What OAuth 2.0 patterns exist for proxy servers that need to call downstream APIs on behalf of users?
4. What does our current implementation do wrong, and what already works correctly?
5. How do Spring Security 7 and MCP SDK 0.18.1 support the compliant patterns?
6. What is the simplest spec-compliant approach for our pre-alpha stage?

---

## Methodology

### Primary Approach
Multi-source literature review combined with codebase gap analysis. Read the MCP spec's authorization sections to understand requirements, survey OAuth 2.0 proxy patterns (token exchange RFC 8693, service accounts, credential vaulting), then map findings against the current implementation to identify the minimal change path.

### Analysis Framework
1. **Requirement extraction** — What does the MCP spec mandate vs. recommend?
2. **Pattern catalog** — What OAuth 2.0 patterns solve the "proxy calling downstream API" problem?
3. **Gap analysis** — What does our current implementation violate, and what is reusable?
4. **Feasibility assessment** — Which patterns fit our stack (HMAC-SHA JWT, Spring Boot 4.0.5, Spring Security 7, MCP SDK 0.18.1)?
5. **Trade-off comparison** — Security vs. complexity vs. spec compliance for each viable approach

### Fallback Strategies
- If MCP spec is ambiguous on proxy patterns, survey how other MCP server implementations handle it (GitHub repos, blog posts)
- If Spring Security 7 lacks direct support for a pattern, check if manual filter implementation is feasible

---

## Data Sources

See `planning/sources.md` for the complete source manifest with file paths and URLs.

**Summary**: 4 source categories covering MCP specification docs, current codebase (14+ source files), external OAuth 2.0 pattern references, and Spring ecosystem capabilities.

---

## Research Phases

### Phase 1: Broad Discovery
**Goal**: Collect all relevant spec text, identify the full set of MCP authorization requirements, and catalog the current implementation components.

**Actions**:
- Read MCP Authorization specification (spec.modelcontextprotocol.io/latest/basic/authorization)
- Read MCP security best practices documentation
- Inventory all security-related files in mcp-server and backend modules
- Identify which OAuth2 infrastructure already exists in the backend

### Phase 2: Targeted Reading
**Goal**: Deep-read the spec sections on token passthrough, token validation, and downstream API access. Read current implementation files to understand exact behavior.

**Actions**:
- Extract specific requirements around token validation at the MCP server
- Read the spec's guidance on "acting on behalf of" users to downstream services
- Read McpJwtFilter, AccessTokenHolder, JwtForwardingInterceptor to document current flow
- Read backend JwtTokenProvider and OAuth2 filters to understand existing capabilities
- Read MCP SDK source/docs for built-in auth support in WebMvcStatelessServerTransport

### Phase 3: Pattern Research
**Goal**: Survey OAuth 2.0 patterns that solve the proxy-to-downstream problem.

**Actions**:
- Research RFC 8693 (Token Exchange) — MCP server exchanges client token for downstream token
- Research service account / client credentials pattern — MCP server uses its own identity
- Research credential vaulting — MCP server stores per-user credentials
- Research Spring Security OAuth2 Resource Server — validate tokens without passthrough
- Assess Spring Authorization Server for token exchange support

### Phase 4: Synthesis and Verification
**Goal**: Compare patterns against requirements, assess feasibility, identify the recommended approach.

**Actions**:
- Map each pattern against MCP spec requirements (compliant / partially compliant / non-compliant)
- Assess implementation complexity for each pattern given our stack
- Cross-reference with what the backend OAuth2 server already supports
- Identify the minimal-change spec-compliant approach for pre-alpha
- Document trade-offs and recommendation

---

## Gathering Strategy

### Instances: 4

| # | Category ID | Focus Area | Tools | Output Prefix |
|---|------------|------------|-------|---------------|
| 1 | mcp-spec | MCP Authorization specification text, security best practices, token passthrough rules, spec-compliant alternatives | WebFetch, WebSearch | mcp-spec |
| 2 | codebase | Current mcp-server security implementation (McpJwtFilter, AccessTokenHolder, JwtForwardingInterceptor, SecurityConfig), backend JWT infrastructure (JwtTokenProvider, SecurityConfiguration, OAuth2 filters), MCP SDK transport config | Read, Grep, Glob | codebase |
| 3 | external-patterns | OAuth 2.0 proxy patterns: RFC 8693 Token Exchange, service-to-service auth (client credentials), credential vaulting, token validation at proxy layer. Real-world MCP server auth examples. | WebSearch, WebFetch | external-patterns |
| 4 | spring-ecosystem | Spring Security 7 OAuth2 Resource Server (token validation without passthrough), Spring Authorization Server token exchange support, spring-boot-starter-oauth2-resource-server configuration, MCP SDK 0.18.1 auth hooks | WebSearch, WebFetch, Read | spring-ecosystem |

### Rationale
The research question spans four distinct knowledge domains. The MCP spec gatherer focuses purely on what the specification requires and recommends. The codebase gatherer documents exactly what exists today and identifies reusable components. The external patterns gatherer surveys industry-standard OAuth 2.0 proxy authentication approaches independent of any specific framework. The Spring ecosystem gatherer focuses on framework-level support for implementing whatever pattern is chosen, ensuring we know what Spring Boot 4.0.5 and Spring Security 7 provide out of the box versus what requires custom implementation.

---

## Success Criteria

1. **Spec requirements extracted** — Clear list of what the MCP spec mandates vs. recommends for MCP server token handling
2. **Token passthrough violation documented** — Specific spec text explaining why current approach is non-compliant
3. **Alternative patterns cataloged** — At least 3 viable approaches with pros/cons
4. **Current implementation gap analysis** — What violates the spec, what is reusable, what needs to change
5. **Spring ecosystem feasibility** — Which patterns have framework support vs. require custom code
6. **Recommended approach** — One primary recommendation with rationale, suitable for pre-alpha stage
7. **Evidence-backed** — All claims linked to spec text, RFC sections, or framework documentation

---

## Expected Outputs

1. **Research findings report** — Comprehensive analysis covering all sub-questions with evidence
2. **Pattern comparison matrix** — Structured comparison of viable approaches (compliance, complexity, security)
3. **Recommendation** — Specific approach for the pre-alpha stage with implementation guidance
4. **Gap analysis** — Current implementation components mapped to required changes
