package pl.devstyle.aj.mcp.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import pl.devstyle.aj.mcp.client.AjApiClient;
import pl.devstyle.aj.mcp.security.ExchangedTokenHolder;

import java.io.IOException;

@Configuration
public class RestClientConfig {

    private static final Logger LOG = LoggerFactory.getLogger(RestClientConfig.class);

    @Bean
    public RestClient ajRestClient(@Value("${aj.backend.url}") String backendUrl) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5_000);
        requestFactory.setReadTimeout(30_000);

        return RestClient.builder()
                .baseUrl(backendUrl)
                .requestFactory(requestFactory)
                .requestInterceptor(new TokenBForwardingInterceptor())
                .defaultStatusHandler(status -> status.is4xxClientError() || status.is5xxServerError(),
                        (request, response) -> {
                            int statusCode = response.getStatusCode().value();
                            String body = new String(response.getBody().readNBytes(8192));
                            LOG.warn("Backend API error: {} {} - {}", statusCode, request.getURI(), body);

                            throw switch (statusCode) {
                                case 400 -> pl.devstyle.aj.mcp.exception.McpToolException.validationError(body);
                                case 401 -> {
                                    LOG.warn("Backend rejected Token-B: {}", body);
                                    yield pl.devstyle.aj.mcp.exception.McpToolException.apiError("Authentication required");
                                }
                                case 403 -> pl.devstyle.aj.mcp.exception.McpToolException.apiError("Insufficient permissions");
                                case 404 -> pl.devstyle.aj.mcp.exception.McpToolException.notFound(body);
                                default -> pl.devstyle.aj.mcp.exception.McpToolException.apiError(
                                        "Backend error (status %d). Please retry.".formatted(statusCode));
                            };
                        })
                .build();
    }

    @Bean
    public RestClient oauthRestClient(@Value("${aj.backend.url}") String backendUrl) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5_000);
        requestFactory.setReadTimeout(10_000);

        return RestClient.builder()
                .baseUrl(backendUrl)
                .requestFactory(requestFactory)
                .build();
    }

    @Bean
    public AjApiClient ajApiClient(RestClient ajRestClient) {
        var adapter = RestClientAdapter.create(ajRestClient);
        var factory = HttpServiceProxyFactory.builderFor(adapter).build();
        return factory.createClient(AjApiClient.class);
    }

    /**
     * Interceptor that reads Token-B from ExchangedTokenHolder (ThreadLocal)
     * and forwards it to the backend. Both the tool handler and this interceptor
     * run on the same MCP SDK thread (boundedElastic), so ThreadLocal works.
     */
    public static class TokenBForwardingInterceptor implements ClientHttpRequestInterceptor {

        @Override
        public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                            ClientHttpRequestExecution execution) throws IOException {
            String tokenB = ExchangedTokenHolder.get();
            if (tokenB != null) {
                LOG.debug("Forwarding Token-B to backend: {} {}", request.getMethod(), request.getURI());
                request.getHeaders().setBearerAuth(tokenB);
            } else {
                LOG.warn("No Token-B available for backend call: {} {}", request.getMethod(), request.getURI());
            }
            return execution.execute(request, body);
        }
    }
}
