package com.example.mcp.swagger;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.WebMvcStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

@AutoConfiguration(afterName = {
        "org.springframework.ai.mcp.server.autoconfigure.McpServerSseWebMvcAutoConfiguration",
        "org.springframework.ai.mcp.server.autoconfigure.McpServerStreamableHttpWebMvcAutoConfiguration",
        "org.springframework.ai.mcp.server.autoconfigure.McpServerStatelessWebMvcAutoConfiguration"
})
@ConditionalOnClass(McpSyncServer.class)
@ConditionalOnProperty(prefix = "swagger.mcp", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(SwaggerMcpProperties.class)
public class SwaggerMcpAutoConfiguration {

    @Bean(name = "swaggerMcpFallbackTransportProvider")
    @ConditionalOnMissingBean(value = {McpSyncServer.class, WebMvcStreamableServerTransportProvider.class})
    public WebMvcStreamableServerTransportProvider fallbackTransportProvider(ObjectMapper objectMapper, Environment environment) {
        String endpoint = environment.getProperty("spring.ai.mcp.server.streamable-http.mcp-endpoint", "/mcp");
        return WebMvcStreamableServerTransportProvider.builder()
                .jsonMapper(new JacksonMcpJsonMapper(objectMapper))
                .mcpEndpoint(endpoint)
                .build();
    }

    @Bean(name = "swaggerMcpFallbackRouterFunction")
    @ConditionalOnMissingBean(value = McpSyncServer.class, name = "webMvcStreamableServerRouterFunction")
    public RouterFunction<ServerResponse> fallbackRouterFunction(
            WebMvcStreamableServerTransportProvider swaggerMcpFallbackTransportProvider) {
        return swaggerMcpFallbackTransportProvider.getRouterFunction();
    }

    @Bean
    @ConditionalOnMissingBean(McpSyncServer.class)
    public McpSyncServer fallbackMcpSyncServer(WebMvcStreamableServerTransportProvider transportProvider) {
        McpSchema.ServerCapabilities capabilities = McpSchema.ServerCapabilities.builder()
                .tools(true)
                .build();
        return McpServer.sync(transportProvider)
                .serverInfo("swagger-mcp-server", "1.0.0")
                .capabilities(capabilities)
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public OpenApiToMcpToolConverter openApiToMcpToolConverter() {
        return new OpenApiToMcpToolConverter();
    }

    @Bean
    @ConditionalOnMissingBean
    public SwaggerMcpToolSelector swaggerMcpToolSelector() {
        return new SwaggerMcpToolSelector();
    }

    @Bean
    @ConditionalOnMissingBean
    public SwaggerMcpResponseOptimizer swaggerMcpResponseOptimizer(ObjectMapper objectMapper, SwaggerMcpProperties properties) {
        return new SwaggerMcpResponseOptimizer(objectMapper, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public SwaggerMcpSecurityPolicy swaggerMcpSecurityPolicy(SwaggerMcpProperties properties) {
        return new SwaggerMcpSecurityPolicy(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(McpSyncServer.class)
    public SwaggerMcpServerAdapter swaggerMcpServerAdapter(
            McpSyncServer mcpSyncServer,
            OpenApiToMcpToolConverter converter,
            SwaggerMcpToolSelector toolSelector,
            SwaggerMcpResponseOptimizer responseOptimizer,
            SwaggerMcpSecurityPolicy securityPolicy,
            SwaggerMcpProperties properties,
            Environment environment,
            RestTemplateBuilder restTemplateBuilder,
            ObjectMapper objectMapper) {
        return new SwaggerMcpServerAdapter(
                mcpSyncServer,
                converter,
                toolSelector,
                responseOptimizer,
                securityPolicy,
                properties,
                environment,
                restTemplateBuilder,
                objectMapper
        );
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(McpSyncServer.class)
    public SwaggerMcpService swaggerMcpService(
            OpenApiToMcpToolConverter converter,
            SwaggerMcpServerAdapter adapter,
            SwaggerMcpSecurityPolicy securityPolicy,
            SwaggerMcpProperties properties,
            Environment environment,
            RestTemplateBuilder restTemplateBuilder) {
        return new SwaggerMcpService(
                converter,
                adapter,
                securityPolicy,
                properties,
                environment,
                restTemplateBuilder
        );
    }
}
