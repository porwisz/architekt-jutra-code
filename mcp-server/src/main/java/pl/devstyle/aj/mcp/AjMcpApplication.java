package pl.devstyle.aj.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.schema.JsonSchemaValidator;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.server.transport.WebMvcStatelessServerTransport;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.common.McpTransportContext;
import pl.devstyle.aj.mcp.config.JacksonMcpJsonMapper;
import pl.devstyle.aj.mcp.config.LoggingJsonSchemaValidator;
import pl.devstyle.aj.mcp.security.ExchangedTokenHolder;
import pl.devstyle.aj.mcp.security.McpIntrospectionFilter;
import pl.devstyle.aj.mcp.service.CategoryService;
import pl.devstyle.aj.mcp.service.ProductService;

import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

@SpringBootApplication
public class AjMcpApplication {

    public static void main(String[] args) {
        SpringApplication.run(AjMcpApplication.class, args);
    }

    @Bean
    McpJsonMapper mcpJsonMapper(ObjectMapper objectMapper) {
        return new JacksonMcpJsonMapper(objectMapper);
    }

    public static final String TOKEN_B_KEY = "token_b";

    @Bean
    WebMvcStatelessServerTransport webMvcStatelessServerTransport(McpJsonMapper mcpJsonMapper) {
        return WebMvcStatelessServerTransport.builder()
                .jsonMapper(mcpJsonMapper)
                .messageEndpoint("/")
                .contextExtractor(request -> {
                    // Bridge Token-B from servlet request attribute (set by McpIntrospectionFilter)
                    // into McpTransportContext so it's available on the MCP handler thread
                    Object tokenB = request.servletRequest().getAttribute(
                            McpIntrospectionFilter.EXCHANGED_TOKEN_ATTRIBUTE);
                    if (tokenB != null) {
                        return McpTransportContext.create(Map.of(TOKEN_B_KEY, tokenB));
                    }
                    return McpTransportContext.EMPTY;
                })
                .build();
    }

    @Bean
    RouterFunction<ServerResponse> routerFunction(WebMvcStatelessServerTransport transport) {
        return transport.getRouterFunction();
    }

    @Bean
    McpStatelessSyncServer mcpStatelessServer(WebMvcStatelessServerTransport transport,
                                              McpJsonMapper mcpJsonMapper,
                                              ObjectMapper objectMapper,
                                              ProductService productService,
                                              CategoryService categoryService) {
        JsonSchemaValidator noOpValidator = (schema, instance) ->
                JsonSchemaValidator.ValidationResponse.asValid(null);
        return McpServer.sync(transport)
                .jsonMapper(mcpJsonMapper)
                .jsonSchemaValidator(new LoggingJsonSchemaValidator(noOpValidator, objectMapper))
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(true)
                        .prompts(false)
                        .resources(false, false)
                        .build())
                .tools(
                        productService.buildToolListProducts(),
                        productService.buildToolAddProduct(),
                        categoryService.buildToolListCategories()
                )
                .build();
    }
}
