# Spring Ecosystem Findings: MCP Server Authentication

## Research Question
How should our MCP server handle authentication without token passthrough, per MCP spec? Specifically: what Spring ecosystem capabilities exist for implementing spec-compliant authentication?

---

## 1. Spring Security OAuth2 Resource Server — JWT with HMAC Shared Secret

### 1.1 Core Capability

Spring Security's `spring-boot-starter-oauth2-resource-server` provides a complete OAuth2 resource server implementation that can validate JWT tokens using HMAC-SHA (symmetric) keys — exactly what the backend currently uses with `io.jsonwebtoken/jjwt`.

**Source**: [Spring Security OAuth2 Resource Server JWT docs](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html) (Spring Security 7.0.4)

### 1.2 NimbusJwtDecoder with SecretKey Configuration

The MCP server can validate JWT tokens locally using `NimbusJwtDecoder.withSecretKey()`:

```java
@Bean
public JwtDecoder jwtDecoder(@Value("${app.jwt.secret}") String secret) {
    byte[] keyBytes = Base64.getDecoder().decode(secret);
    SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "HmacSHA256");
    return NimbusJwtDecoder.withSecretKey(secretKey)
            .macAlgorithm(MacAlgorithm.HS256)
            .build();
}
```

**Source**: [NimbusJwtDecoder.SecretKeyJwtDecoderBuilder API](https://docs.spring.io/spring-security/site/docs/current/api/org/springframework/security/oauth2/jwt/NimbusJwtDecoder.SecretKeyJwtDecoderBuilder.html)

**Builder methods available**:
- `macAlgorithm(MacAlgorithm)` — set HS256/HS384/HS512 (RFC 7518 Section 3.2)
- `validateType(boolean)` — control typ header verification
- `jwtProcessorCustomizer(Consumer<ConfigurableJWTProcessor>)` — custom processor logic
- `build()` — create the decoder

### 1.3 Custom Claim Validation (Audience, Issuer)

Spring Security supports adding custom validators for audience and other claims:

```java
@Bean
JwtDecoder jwtDecoder(@Value("${app.jwt.secret}") String secret) {
    byte[] keyBytes = Base64.getDecoder().decode(secret);
    SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "HmacSHA256");
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey)
            .macAlgorithm(MacAlgorithm.HS256)
            .build();

    // Add audience validation
    OAuth2TokenValidator<Jwt> audienceValidator =
        new JwtClaimValidator<List<String>>("aud", aud -> aud.contains("mcp-server"));
    OAuth2TokenValidator<Jwt> withTimestamp = new JwtTimestampValidator();
    OAuth2TokenValidator<Jwt> combined = new DelegatingOAuth2TokenValidator<>(
        withTimestamp, audienceValidator);
    decoder.setJwtValidator(combined);

    return decoder;
}
```

**Source**: Spring Security JWT docs, "Configuring Validation" section

**Also available via Spring Boot properties** (for issuer-uri based setups):
```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          audiences: https://mcp-server.example.com
```

### 1.4 Authority Extraction from JWT Claims

The current backend uses `scopes` claim in OAuth2 tokens and `permissions` claim in regular tokens. Spring Security can extract these automatically:

```java
@Bean
public JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtGrantedAuthoritiesConverter grantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
    grantedAuthoritiesConverter.setAuthoritiesClaimName("scopes");  // matches backend's generateOAuth2Token
    grantedAuthoritiesConverter.setAuthorityPrefix("PERMISSION_");  // matches backend's PERMISSION_ convention

    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesConverter);
    return converter;
}
```

**Source**: Spring Security JWT docs, "Extracting Authorities Manually" section

**Relevance to codebase**: The backend's `JwtTokenProvider.generateOAuth2Token()` produces tokens with `scopes` claim (line 57 of JwtTokenProvider.java). The backend's `SecurityConfiguration` expects `PERMISSION_mcp:read` and `PERMISSION_mcp:edit` authorities. The MCP server resource server config must map the `scopes` claim to authorities with `PERMISSION_` prefix to maintain consistency.

### 1.5 Complete SecurityFilterChain for MCP Server as Resource Server

Replacing the current custom `McpJwtFilter` with standard OAuth2 resource server:

```java
@Bean
public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        .csrf(csrf -> csrf.disable())
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/.well-known/**").permitAll()
            .requestMatchers("/actuator/health/**").permitAll()
            .anyRequest().authenticated()
        )
        .oauth2ResourceServer(oauth2 -> oauth2
            .jwt(jwt -> jwt
                .decoder(jwtDecoder())
                .jwtAuthenticationConverter(jwtAuthenticationConverter())
            )
        )
        .exceptionHandling(ex -> ex
            .authenticationEntryPoint(new McpAuthenticationEntryPoint(...))
        );
    return http.build();
}
```

**Source**: Spring Security JWT docs, "Overriding or Replacing Boot Auto Configuration" section

### 1.6 Required Dependency Addition

The MCP server's `pom.xml` currently has `spring-boot-starter-security` but NOT `spring-boot-starter-oauth2-resource-server`. This dependency is needed:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>
```

**Source**: Codebase analysis — `mcp-server/pom.xml` (lines 42-45 have only `spring-boot-starter-security`)

### 1.7 Key Compatibility Note — HMAC Shared Secret

The current backend uses `io.jsonwebtoken/jjwt` with `Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret))` (JwtTokenProvider.java, line 25). The Spring Security `NimbusJwtDecoder` uses Nimbus JOSE+JWT internally (not jjwt), but both can decode the same HMAC-SHA256 JWTs if given the same secret key bytes. The JWT format is standard — the library difference does not matter.

**Confidence**: High (100%) — Both libraries implement the same JWT/JWS standard (RFC 7519/7515).

---

## 2. Spring Authorization Server — Token Exchange (RFC 8693)

### 2.1 GA Support Status

Spring Authorization Server **1.3** (GA since May 22, 2024) includes built-in support for RFC 8693 Token Exchange. This is a standard feature, not preview.

Current maintenance releases: 1.5.5 and 1.4.8 (December 2025).

**Source**: [Spring Authorization Server 1.3 GA announcement](https://spring.io/blog/2024/05/22/spring-authorization-server-1-3-goes-ga/)

### 2.2 Token Exchange Grant Type

Token exchange enables the MCP server to exchange a client's access token for a new token scoped to the backend:

- **Grant type**: `urn:ietf:params:oauth:grant-type:token-exchange`
- **Delegation**: MCP server acts on behalf of user, retaining both identities
- **Impersonation**: MCP server acts as the user (indistinguishable)

**Source**: [Token Exchange support in Spring Security 6.3.0-M3](https://spring.io/blog/2024/03/19/token-exchange-support-in-spring-security-6-3-0-m3/)

### 2.3 Client-Side Support

Spring Security 6.3+ (which maps to Spring Security 7 in Spring Boot 4.x) provides client-side token exchange support:

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          mcp-to-backend:
            authorization-grant-type: urn:ietf:params:oauth:grant-type:token-exchange
            client-id: mcp-server
            client-secret: ${MCP_CLIENT_SECRET}
        provider:
          mcp-to-backend:
            token-uri: https://auth-server/oauth2/token
```

**Source**: [Baeldung Token Exchange Guide](https://www.baeldung.com/spring-security-token-exchange-guide)

### 2.4 Feasibility for This Project

**Important caveat**: The current backend uses a **custom OAuth2 server implementation** (hand-written filters: `OAuth2TokenFilter`, `OAuth2AuthorizationFilter`, `PublicClientRegistrationFilter`) rather than Spring Authorization Server. The `OAuth2TokenFilter` only supports `authorization_code` and `refresh_token` grant types (lines 63-68).

Adding token exchange would require either:
- **Option A**: Adding a `urn:ietf:params:oauth:grant-type:token-exchange` handler to the custom `OAuth2TokenFilter`
- **Option B**: Migrating to Spring Authorization Server (significant effort)

**Source**: Codebase analysis — `OAuth2TokenFilter.java` lines 62-69

**Confidence**: High (100%) — direct code reading

---

## 3. MCP SDK + Spring Security Integration

### 3.1 spring-ai-community/mcp-security Project

A community project provides turnkey Spring Security integration for MCP servers:

**Dependency**:
```xml
<groupId>org.springaicommunity</groupId>
<artifactId>mcp-server-security-spring-boot</artifactId>
<version>0.1.6</version>
```

**Source**: [spring-ai-community/mcp-security GitHub](https://github.com/spring-ai-community/mcp-security)

**What it provides**:
- `McpServerOAuth2Configurer` — a security configurer that secures all MCP endpoints
- Auto-configuration: set `spring.security.oauth2.resourceserver.jwt.issuer-uri` and get a working SecurityFilterChain
- Optional audience claim validation (RFC 8707 resource indicators)
- Protected Resource Metadata generation
- WebMVC-only (no WebFlux support)

**Configuration**:
```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://your-auth-server.com
```

### 3.2 Compatibility Concern

The mcp-security project versions:
- **0.1.x** requires **Spring AI 2.0.x** (which maps to Spring Boot 4.x)
- **0.0.6** for Spring AI 1.1.x

**Source**: [mcp-security README](https://github.com/spring-ai-community/mcp-security)

**Key limitation for our project**: The mcp-security project expects an issuer-uri based setup where the authorization server exposes a JWK Set endpoint for public key retrieval. Our backend uses **HMAC-SHA shared secrets** (symmetric keys), which means there is no JWK Set endpoint. The mcp-security auto-configuration would need to be bypassed in favor of a custom `JwtDecoder` bean using `NimbusJwtDecoder.withSecretKey()`.

**Confidence**: High (90%) — the library assumes asymmetric keys via issuer discovery; custom JwtDecoder bean should override this, but needs verification.

### 3.3 MCP Java SDK Transport Auth Hooks

The MCP Java SDK (0.18.1) itself does **not** include built-in authentication or authorization. Auth is deliberately left to the host application's security framework:

- The SDK provides transport classes (`WebMvcStatelessServerTransport`) that are Spring-managed servlets
- Spring Security's filter chain runs before requests reach the transport
- The SDK has no `@PreAuthorize` or token-validation code — it relies entirely on the SecurityFilterChain
- Thread-local context from Spring Security (e.g., `SecurityContextHolder`) is available within MCP tool handlers

**Source**: [MCP Java SDK Server docs](https://java.sdk.modelcontextprotocol.io/latest-snapshot/server/) and codebase analysis

**Implication**: The current approach of using a custom `McpJwtFilter` before `UsernamePasswordAuthenticationFilter` is the correct integration point. Replacing it with `oauth2ResourceServer()` DSL is a drop-in change at the SecurityFilterChain level.

---

## 4. Service-to-Service Auth: MCP Server to Backend

### 4.1 OAuth2ClientHttpRequestInterceptor (Spring Security 7 / 6.4+)

Spring Security provides `OAuth2ClientHttpRequestInterceptor` for adding OAuth2 tokens to outbound `RestClient` calls automatically:

```java
@Bean
public RestClient backendRestClient(OAuth2AuthorizedClientManager authorizedClientManager) {
    OAuth2ClientHttpRequestInterceptor interceptor =
        new OAuth2ClientHttpRequestInterceptor(authorizedClientManager);
    return RestClient.builder()
        .baseUrl(backendUrl)
        .requestInterceptor(interceptor)
        .build();
}
```

**Usage with client registration**:
```java
String result = restClient.get()
    .uri("/api/products")
    .attributes(clientRegistrationId("backend-service"))
    .retrieve()
    .body(String.class);
```

**Source**: [RestClient Support for OAuth2 in Spring Security 6.4](https://spring.io/blog/2024/10/28/restclient-support-for-oauth2-in-spring-security-6-4/) and [Spring Security Authorized Clients docs](https://docs.spring.io/spring-security/reference/servlet/oauth2/client/authorized-clients.html)

### 4.2 Client Credentials Flow

For service-to-service communication where the MCP server uses its **own identity** (not acting on behalf of a user):

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          backend-service:
            client-id: mcp-server
            client-secret: ${MCP_CLIENT_SECRET}
            authorization-grant-type: client_credentials
            scope: mcp:read,mcp:edit
        provider:
          backend-service:
            token-uri: https://auth-server/oauth2/token
```

**Token lifecycle management**:
- Token is obtained on first request to backend
- Cached automatically by `OAuth2AuthorizedClientManager`
- Refreshed when expired (401/403 triggers re-acquisition)
- No manual token management needed

**Source**: [Spring Security OAuth2 Client docs](https://docs.spring.io/spring-security/reference/servlet/oauth2/client/index.html)

### 4.3 Token Exchange Flow (On-Behalf-Of)

For calls where the MCP server needs to act **on behalf of the authenticated user**:

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          backend-on-behalf:
            authorization-grant-type: urn:ietf:params:oauth:grant-type:token-exchange
            client-id: mcp-server
            client-secret: ${MCP_CLIENT_SECRET}
        provider:
          backend-on-behalf:
            token-uri: https://auth-server/oauth2/token
```

**Source**: [Spring Security Token Exchange blog](https://spring.io/blog/2024/03/19/token-exchange-support-in-spring-security-6-3-0-m3/)

### 4.4 Outside-Request-Context Support

For service-to-service calls that may happen outside an HTTP request context:
- Use `AuthorizedClientServiceOAuth2AuthorizedClientManager` (backed by `OAuth2AuthorizedClientService`)
- Instead of `DefaultOAuth2AuthorizedClientManager` which requires `HttpServletRequest`

**Source**: [Spring Security Authorized Clients docs](https://docs.spring.io/spring-security/reference/servlet/oauth2/client/authorized-clients.html)

### 4.5 Current Implementation vs Spring OAuth2 Client

The current MCP server uses a custom `JwtForwardingInterceptor` (in `RestClientConfig.java`) that reads the raw token from `AccessTokenHolder` (ThreadLocal) and forwards it verbatim to the backend. This is the **token passthrough** pattern that violates the MCP spec.

Replacing this with `OAuth2ClientHttpRequestInterceptor` would:
- Use proper token exchange or client credentials
- Handle token lifecycle (acquisition, caching, refresh) automatically
- Remove the `AccessTokenHolder` ThreadLocal hack
- Make the MCP server a proper OAuth2 client

**Source**: Codebase analysis — `RestClientConfig.java` lines 67-86, `AccessTokenHolder.java`

---

## 5. Feasibility Assessment for Current Stack

### 5.1 What Spring Provides Out of the Box

| Capability | Spring Support | Required For |
|-----------|---------------|-------------|
| JWT validation with HMAC secret | `NimbusJwtDecoder.withSecretKey()` | MCP server validates incoming tokens |
| Custom claim extraction (scopes) | `JwtGrantedAuthoritiesConverter` | Map scopes to Spring authorities |
| Audience validation | `JwtClaimValidator` | Restrict tokens to MCP server audience |
| Client credentials flow | `spring-boot-starter-oauth2-client` | Service-to-service auth |
| Token exchange (RFC 8693) | Spring Security 7 + Spring Auth Server 1.3+ | On-behalf-of delegation |
| RestClient + OAuth2 interceptor | `OAuth2ClientHttpRequestInterceptor` | Automatic token management for backend calls |

### 5.2 What Requires Custom Implementation

| Capability | Why Custom | Effort |
|-----------|-----------|--------|
| Token exchange grant handler | Backend uses custom OAuth2 filters, not Spring Authorization Server | Medium — add handler to `OAuth2TokenFilter` |
| Audience claim in tokens | Backend's `generateOAuth2Token()` doesn't include `aud` claim | Low — add `.claim("aud", audience)` to JwtTokenProvider |
| MCP server as registered client | Backend DCR exists but MCP server needs pre-registration or auto-registration | Low |

### 5.3 Simplest Path (Pre-Alpha Recommendation)

**For pre-alpha**: Replace the custom `McpJwtFilter` with `oauth2ResourceServer().jwt()` using `NimbusJwtDecoder.withSecretKey()`. This gives the MCP server proper JWT validation (currently missing) using the same shared secret the backend uses, without requiring any backend changes.

**Required changes**:
1. Add `spring-boot-starter-oauth2-resource-server` to MCP server pom.xml
2. Share `app.jwt.secret` with MCP server (via env var)
3. Replace `SecurityConfig` to use `.oauth2ResourceServer(oauth2 -> oauth2.jwt(...))` with custom `JwtDecoder`
4. Configure `JwtAuthenticationConverter` to map `scopes`/`permissions` claim to authorities

**What this achieves**: MCP server validates tokens itself (no blind trust-and-forward). The token forwarding for backend calls can remain temporarily (it becomes forwarding a *validated* token rather than an *unvalidated* one), with proper token exchange added in a later phase.

---

## Sources

- [Spring Security OAuth2 Resource Server JWT](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)
- [NimbusJwtDecoder.SecretKeyJwtDecoderBuilder API](https://docs.spring.io/spring-security/site/docs/current/api/org/springframework/security/oauth2/jwt/NimbusJwtDecoder.SecretKeyJwtDecoderBuilder.html)
- [spring-ai-community/mcp-security](https://github.com/spring-ai-community/mcp-security)
- [Spring Authorization Server 1.3 GA](https://spring.io/blog/2024/05/22/spring-authorization-server-1-3-goes-ga/)
- [Token Exchange in Spring Security 6.3](https://spring.io/blog/2024/03/19/token-exchange-support-in-spring-security-6-3-0-m3/)
- [RestClient OAuth2 Support in Spring Security 6.4](https://spring.io/blog/2024/10/28/restclient-support-for-oauth2-in-spring-security-6-4/)
- [Spring Security Authorized Clients](https://docs.spring.io/spring-security/reference/servlet/oauth2/client/authorized-clients.html)
- [MCP Java SDK Server docs](https://java.sdk.modelcontextprotocol.io/latest-snapshot/server/)
- Codebase: `mcp-server/pom.xml`, `McpJwtFilter.java`, `SecurityConfig.java`, `RestClientConfig.java`, `AccessTokenHolder.java`, `JwtTokenProvider.java`, `SecurityConfiguration.java`, `OAuth2TokenFilter.java`
