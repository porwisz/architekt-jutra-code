package pl.devstyle.aj.mcp.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import pl.devstyle.aj.mcp.security.McpIntrospectionFilter;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RestClientConfigTests {

    @Test
    void interceptor_addsAuthorizationHeader_whenTokenBPresentInRequestAttribute() throws IOException {
        var interceptor = new RestClientConfig.TokenBForwardingInterceptor();

        var servletRequest = new MockHttpServletRequest();
        servletRequest.setAttribute(McpIntrospectionFilter.EXCHANGED_TOKEN_ATTRIBUTE, "token-B-value");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(servletRequest));

        try {
            var request = mock(HttpRequest.class);
            var headers = new HttpHeaders();
            when(request.getHeaders()).thenReturn(headers);
            var body = new byte[0];
            var execution = mock(ClientHttpRequestExecution.class);
            var response = mock(ClientHttpResponse.class);
            when(execution.execute(any(), any())).thenReturn(response);

            interceptor.intercept(request, body, execution);

            assertThat(headers.getFirst("Authorization")).isEqualTo("Bearer token-B-value");
            verify(execution).execute(request, body);
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }
}
