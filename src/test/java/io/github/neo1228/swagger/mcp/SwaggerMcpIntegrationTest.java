package io.github.neo1228.swagger.mcp;

import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.spec.McpSchema;
import io.swagger.v3.oas.annotations.Operation;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;

@SpringBootTest(
        classes = SwaggerMcpIntegrationTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.ai.mcp.server.protocol=STREAMABLE_HTTP",
                "swagger.mcp.enabled=true",
                "swagger.mcp.tool-name-prefix=api_",
                "swagger.mcp.security.confirmation-token=CONFIRM",
                "swagger.mcp.smart-context.enabled=true",
                "swagger.mcp.smart-context.gateway-tool-enabled=true",
                "swagger.mcp.smart-context.gateway-only=false",
                "swagger.mcp.response.max-chars=2048"
        }
)
class SwaggerMcpIntegrationTest {

    @Autowired
    private McpSyncServer mcpSyncServer;

    @Autowired
    private SwaggerMcpServerAdapter adapter;

    @Autowired
    private SwaggerMcpToolSelector toolSelector;

    @Test
    void registersToolsForControllerOperations() {
        await().atMost(15, SECONDS).untilAsserted(() -> {
            List<String> toolNames = mcpSyncServer.listTools().stream().map(McpSchema.Tool::name).toList();
            assertThat(toolNames).contains("api_gethello", "api_createorder", "api_meta_discover_api_tools");
        });
    }

    @Test
    void executesOperationThroughAdapterAndAppliesProjection() {
        await().atMost(15, SECONDS).untilAsserted(() ->
                assertThat(mcpSyncServer.listTools().stream().map(McpSchema.Tool::name).toList()).contains("api_gethello"));

        McpSchema.CallToolResult result = adapter.invokeTool(
                "api_gethello",
                Map.of("name", "Neo", "_projection", "$.message")
        );

        assertThat(result.isError()).isFalse();
        String text = ((McpSchema.TextContent) result.content().get(0)).text();
        assertThat(text).contains("Hello Neo");
    }

    @Test
    void requiresConfirmationForRiskyOperations() {
        await().atMost(15, SECONDS).untilAsserted(() ->
                assertThat(mcpSyncServer.listTools().stream().map(McpSchema.Tool::name).toList()).contains("api_createorder"));

        McpSchema.CallToolResult denied = adapter.invokeTool(
                "api_createorder",
                Map.of("body", Map.of("item", "shoe", "quantity", 1))
        );
        assertThat(denied.isError()).isTrue();
        assertThat(((McpSchema.TextContent) denied.content().get(0)).text()).contains("Confirmation is required");

        McpSchema.CallToolResult approved = adapter.invokeTool(
                "api_createorder",
                Map.of("body", Map.of("item", "shoe", "quantity", 1), "_confirm", "CONFIRM")
        );
        assertThat(approved.isError()).isFalse();
        assertThat(((McpSchema.TextContent) approved.content().get(0)).text()).contains("CREATED");
    }

    @Test
    void selectsRelevantToolsByNaturalLanguage() {
        await().atMost(15, SECONDS).untilAsserted(() ->
                assertThat(mcpSyncServer.listTools().stream().map(McpSchema.Tool::name).toList()).contains("api_gethello"));

        List<SwaggerMcpToolSelector.ScoredTool> scoredTools = toolSelector.select("say hello to user", 3);
        assertThat(scoredTools).isNotEmpty();
        assertThat(scoredTools.get(0).operation().toolName()).isEqualTo("api_gethello");
    }

    @RestController
    static class DummyController {

        @Operation(operationId = "getHello", summary = "Get greeting message")
        @GetMapping(path = "/hello", produces = MediaType.APPLICATION_JSON_VALUE)
        public Map<String, Object> hello(@RequestParam(defaultValue = "world") String name) {
            return Map.of(
                    "message", "Hello " + name,
                    "detail", Map.of("service", "dummy", "name", name)
            );
        }

        @Operation(operationId = "createOrder", summary = "Create an order")
        @PostMapping(path = "/orders", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
        public Map<String, Object> createOrder(@RequestBody Map<String, Object> body) {
            return Map.of(
                    "status", "CREATED",
                    "order", body
            );
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @ImportAutoConfiguration(SwaggerMcpAutoConfiguration.class)
    @Import(DummyController.class)
    static class TestApp {
    }
}

