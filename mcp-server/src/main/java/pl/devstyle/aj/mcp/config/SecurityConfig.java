package pl.devstyle.aj.mcp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.client.RestClient;
import pl.devstyle.aj.mcp.security.McpAuthenticationEntryPoint;
import pl.devstyle.aj.mcp.security.McpIntrospectionFilter;
import pl.devstyle.aj.mcp.security.TokenExchangeClient;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${aj.mcp.base-url}")
    private String mcpBaseUrl;

    @Value("${aj.oauth.client-id}")
    private String clientId;

    @Value("${aj.oauth.client-secret}")
    private String clientSecret;

    @Bean
    public McpAuthenticationEntryPoint mcpAuthenticationEntryPoint() {
        return new McpAuthenticationEntryPoint(mcpBaseUrl + "/.well-known/oauth-protected-resource");
    }

    @Bean
    public TokenExchangeClient tokenExchangeClient(RestClient oauthRestClient) {
        return new TokenExchangeClient(oauthRestClient, clientId, clientSecret);
    }

    @Bean
    public McpIntrospectionFilter mcpIntrospectionFilter(RestClient oauthRestClient,
                                                         TokenExchangeClient tokenExchangeClient,
                                                         McpAuthenticationEntryPoint mcpAuthenticationEntryPoint) {
        return new McpIntrospectionFilter(
                oauthRestClient,
                tokenExchangeClient,
                mcpAuthenticationEntryPoint,
                clientId,
                clientSecret
        );
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           McpIntrospectionFilter mcpIntrospectionFilter,
                                           McpAuthenticationEntryPoint mcpAuthenticationEntryPoint) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/.well-known/**").permitAll()
                        .requestMatchers("/actuator/health/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(mcpAuthenticationEntryPoint)
                )
                .addFilterBefore(mcpIntrospectionFilter,
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
