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
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Set;

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

    @Autowired
    private SwaggerMcpOperationCatalog operationCatalog;

    @Autowired
    private SwaggerMcpProperties properties;

    @Test
    void registersToolsForControllerOperations() {
        await().atMost(15, SECONDS).untilAsserted(() -> {
            List<String> toolNames = mcpSyncServer.listTools().stream().map(McpSchema.Tool::name).toList();
            assertThat(toolNames).contains(
                    "api_gethello",
                    "api_createorder",
                    "api_meta_discover_api_tools",
                    "api_meta_describe_api_tool",
                    "api_meta_get_api_capabilities",
                    "api_meta_validate_api_call",
                    "api_meta_list_api_groups",
                    "api_meta_plan_api_workflow",
                    "api_meta_invoke_api_workflow",
                    "api_meta_invoke_api_by_intent"
            );
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
    void exposesCatalogMetaToolsForApiExploration() {
        await().atMost(15, SECONDS).untilAsserted(() ->
                assertThat(mcpSyncServer.listTools().stream().map(McpSchema.Tool::name).toList())
                        .contains("api_meta_describe_api_tool", "api_meta_list_api_groups", "api_gethello"));

        McpSchema.CallToolResult groups = adapter.listApiGroups(Map.of("maxToolsPerGroup", 3));
        assertThat(groups.isError()).isFalse();
        @SuppressWarnings("unchecked")
        Map<String, Object> groupPayload = (Map<String, Object>) groups.structuredContent();
        assertThat(groupPayload).containsKeys("operationCount", "groupCount", "groups");
        assertThat((Integer) groupPayload.get("operationCount")).isGreaterThanOrEqualTo(6);

        McpSchema.CallToolResult description = adapter.describeApiTool(Map.of("toolName", "api_gethello"));
        assertThat(description.isError()).isFalse();
        @SuppressWarnings("unchecked")
        Map<String, Object> descriptionPayload = (Map<String, Object>) description.structuredContent();
        assertThat(descriptionPayload)
                .containsEntry("toolName", "api_gethello")
                .containsEntry("method", "GET")
                .containsEntry("path", "/hello")
                .containsEntry("readOnly", true)
                .containsKey("inputSchema");
        assertThat(descriptionPayload.get("requiredArguments")).isInstanceOf(List.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void exposesGatewayCapabilitiesForMcpClients() {
        await().atMost(15, SECONDS).untilAsserted(() ->
                assertThat(mcpSyncServer.listTools().stream().map(McpSchema.Tool::name).toList())
                        .contains("api_meta_get_api_capabilities", "api_meta_validate_api_call", "api_gethello"));

        McpSchema.CallToolResult result = adapter.getApiCapabilities(Map.of("maxGroups", 3, "maxToolsPerGroup", 2));

        assertThat(result.isError()).isFalse();
        Map<String, Object> payload = (Map<String, Object>) result.structuredContent();
        assertThat((List<String>) payload.get("gatewayTools"))
                .contains(
                        "api_meta_get_api_capabilities",
                        "api_meta_validate_api_call",
                        "api_meta_plan_api_workflow",
                        "api_meta_invoke_api_workflow"
                );
        assertThat((Map<String, Object>) payload.get("catalog"))
                .containsKeys("operationCount", "groupCount", "readOnlyCount", "riskyCount", "groups");
        assertThat((Map<String, Object>) payload.get("orchestration"))
                .containsEntry("recursiveMetaToolsAllowed", false)
                .containsEntry("defaultDryRun", true);
        assertThat((Map<String, Object>) payload.get("safety"))
                .containsEntry("validateBeforeExecute", "api_meta_validate_api_call")
                .containsEntry("confirmationArgument", "_confirm");
        assertThat((Map<String, Object>) payload.get("runtime"))
                .containsEntry("bytecodeRelease", 17)
                .containsEntry("virtualThreadsEnabled", true)
                .containsKey("virtualThreadsAvailable")
                .containsKey("httpDispatchThreadModel");
    }

    @Test
    @SuppressWarnings("unchecked")
    void validatesApiCallsWithoutDispatchingHttp() {
        await().atMost(15, SECONDS).untilAsserted(() ->
                assertThat(mcpSyncServer.listTools().stream().map(McpSchema.Tool::name).toList())
                        .contains("api_meta_validate_api_call", "api_getorder", "api_createorder"));

        McpSchema.CallToolResult missingArgument = adapter.validateApiCall(Map.of(
                "toolName", "api_getorder",
                "arguments", Map.of()
        ));
        assertThat(missingArgument.isError()).isFalse();
        Map<String, Object> missingPayload = (Map<String, Object>) missingArgument.structuredContent();
        assertThat(missingPayload)
                .containsEntry("toolName", "api_getorder")
                .containsEntry("valid", false)
                .containsEntry("wouldExecute", false);
        assertThat((List<String>) missingPayload.get("errors"))
                .anySatisfy(error -> assertThat(error).contains("path parameter: orderId"));

        McpSchema.CallToolResult riskyWithoutConfirmation = adapter.validateApiCall(Map.of(
                "toolName", "api_createorder",
                "arguments", Map.of("body", Map.of("id", "order-2"))
        ));
        assertThat(riskyWithoutConfirmation.isError()).isFalse();
        Map<String, Object> riskyPayload = (Map<String, Object>) riskyWithoutConfirmation.structuredContent();
        assertThat(riskyPayload)
                .containsEntry("risky", true)
                .containsEntry("valid", false);
        assertThat((List<String>) riskyPayload.get("errors"))
                .anySatisfy(error -> assertThat(error).contains("Confirmation is required"));

        McpSchema.CallToolResult valid = adapter.validateApiCall(Map.of(
                "toolName", "api_getorder",
                "arguments", Map.of("orderId", "order-2")
        ));
        assertThat(valid.isError()).isFalse();
        Map<String, Object> validPayload = (Map<String, Object>) valid.structuredContent();
        assertThat(validPayload)
                .containsEntry("valid", true)
                .containsEntry("wouldExecute", true);
        assertThat((Map<String, Object>) validPayload.get("dispatchPreview"))
                .containsEntry("resolvedPath", "/orders/order-2");
    }

    @Test
    @SuppressWarnings("unchecked")
    void plansApiWorkflowFromCatalog() {
        await().atMost(15, SECONDS).untilAsserted(() ->
                assertThat(mcpSyncServer.listTools().stream().map(McpSchema.Tool::name).toList())
                        .contains("api_meta_plan_api_workflow", "api_createorder", "api_getorder"));

        McpSchema.CallToolResult result = adapter.planApiWorkflow(Map.of("goal", "create and read order", "topK", 5));

        assertThat(result.isError()).isFalse();
        Map<String, Object> payload = (Map<String, Object>) result.structuredContent();
        assertThat(payload).containsEntry("goal", "create and read order");
        assertThat((Integer) payload.get("candidateCount")).isGreaterThan(0);
        assertThat((List<Map<String, Object>>) payload.get("steps"))
                .extracting(step -> step.get("toolName"))
                .contains("api_createorder", "api_getorder");
        assertThat((Map<String, Object>) payload.get("executionModel"))
                .containsEntry("toolName", "api_meta_invoke_api_workflow");
    }

    @Test
    @SuppressWarnings("unchecked")
    void dryRunsApiWorkflowWithoutExecutingSteps() {
        await().atMost(15, SECONDS).untilAsserted(() ->
                assertThat(mcpSyncServer.listTools().stream().map(McpSchema.Tool::name).toList()).contains("api_gethello"));

        McpSchema.CallToolResult result = adapter.invokeApiWorkflow(Map.of(
                "dryRun", true,
                "steps", List.of(Map.of(
                        "id", "hello",
                        "toolName", "api_gethello",
                        "arguments", Map.of("name", "Neo")
                ))
        ));

        assertThat(result.isError()).isFalse();
        Map<String, Object> payload = (Map<String, Object>) result.structuredContent();
        assertThat(payload)
                .containsEntry("dryRun", true)
                .containsEntry("success", true)
                .containsEntry("stepCount", 1);
        List<Map<String, Object>> steps = (List<Map<String, Object>>) payload.get("steps");
        assertThat(steps.get(0))
                .containsEntry("id", "hello")
                .containsEntry("toolName", "api_gethello")
                .containsEntry("wouldExecute", true);
        assertThat((Map<String, Object>) steps.get(0).get("arguments")).containsEntry("name", "Neo");
    }

    @Test
    @SuppressWarnings("unchecked")
    void dryRunRejectsUnavailableWorkflowReferences() {
        await().atMost(15, SECONDS).untilAsserted(() ->
                assertThat(mcpSyncServer.listTools().stream().map(McpSchema.Tool::name).toList())
                        .contains("api_gethello", "api_getorder"));

        McpSchema.CallToolResult result = adapter.invokeApiWorkflow(Map.of(
                "dryRun", true,
                "steps", List.of(
                        Map.of(
                                "id", "hello",
                                "toolName", "api_gethello",
                                "arguments", Map.of("name", "Neo")
                        ),
                        Map.of(
                                "id", "read",
                                "toolName", "api_getorder",
                                "arguments", Map.of("orderId", "${missing:$.order.id}")
                        )
                )
        ));

        assertThat(result.isError()).isTrue();
        Map<String, Object> payload = (Map<String, Object>) result.structuredContent();
        assertThat(payload).containsEntry("success", false);
        List<Map<String, Object>> steps = (List<Map<String, Object>>) payload.get("steps");
        assertThat(steps.get(1))
                .containsEntry("valid", false)
                .containsEntry("wouldExecute", false);
        assertThat((List<String>) steps.get(1).get("errors"))
                .anySatisfy(error -> assertThat(error).contains("Unknown or unavailable workflow step reference: missing"));
        assertThat((List<Map<String, Object>>) steps.get(1).get("workflowReferences"))
                .anySatisfy(reference -> assertThat(reference).containsEntry("stepId", "missing"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void executesApiWorkflowWithJsonPathInterpolation() {
        await().atMost(15, SECONDS).untilAsserted(() ->
                assertThat(mcpSyncServer.listTools().stream().map(McpSchema.Tool::name).toList())
                        .contains("api_createorder", "api_getorder"));

        McpSchema.CallToolResult result = adapter.invokeApiWorkflow(Map.of(
                "dryRun", false,
                "steps", List.of(
                        Map.of(
                                "id", "create",
                                "toolName", "api_createorder",
                                "arguments", Map.of(
                                        "body", Map.of("id", "order-1", "item", "shoe"),
                                        "_confirm", "CONFIRM"
                                )
                        ),
                        Map.of(
                                "id", "read",
                                "toolName", "api_getorder",
                                "arguments", Map.of("orderId", "${create:$.order.id}")
                        )
                )
        ));

        assertThat(result.isError()).isFalse();
        Map<String, Object> payload = (Map<String, Object>) result.structuredContent();
        assertThat(payload)
                .containsEntry("dryRun", false)
                .containsEntry("success", true)
                .containsEntry("stepCount", 2);
        List<Map<String, Object>> steps = (List<Map<String, Object>>) payload.get("steps");
        assertThat((Map<String, Object>) steps.get(1).get("arguments")).containsEntry("orderId", "order-1");
        assertThat((Map<String, Object>) steps.get(1).get("structuredContent")).containsEntry("orderId", "order-1");
    }

    @Test
    void blocksRecursiveMetaToolInvocationInWorkflow() {
        await().atMost(15, SECONDS).untilAsserted(() ->
                assertThat(mcpSyncServer.listTools().stream().map(McpSchema.Tool::name).toList())
                        .contains("api_meta_describe_api_tool", "api_meta_invoke_api_workflow"));

        McpSchema.CallToolResult result = adapter.invokeApiWorkflow(Map.of(
                "steps", List.of(Map.of(
                        "id", "meta",
                        "toolName", "api_meta_describe_api_tool",
                        "arguments", Map.of("toolName", "api_gethello")
                ))
        ));

        assertThat(result.isError()).isTrue();
        assertThat(((McpSchema.TextContent) result.content().get(0)).text())
                .contains("Workflow steps cannot invoke meta tools");
    }

    @Test
    void validatesRequiredArgumentsBeforeHttpDispatch() {
        await().atMost(15, SECONDS).untilAsserted(() ->
                assertThat(mcpSyncServer.listTools().stream().map(McpSchema.Tool::name).toList())
                        .contains("api_getorder", "api_searchorders", "api_echoheader", "api_createorder"));

        McpSchema.CallToolResult missingPath = adapter.invokeTool("api_getorder", Map.of());
        assertThat(missingPath.isError()).isTrue();
        assertThat(((McpSchema.TextContent) missingPath.content().get(0)).text())
                .contains("Missing required argument(s)")
                .contains("path parameter: orderId");

        McpSchema.CallToolResult missingQuery = adapter.invokeTool("api_searchorders", Map.of());
        assertThat(missingQuery.isError()).isTrue();
        assertThat(((McpSchema.TextContent) missingQuery.content().get(0)).text())
                .contains("query parameter: q");

        McpSchema.CallToolResult missingHeader = adapter.invokeTool("api_echoheader", Map.of());
        assertThat(missingHeader.isError()).isTrue();
        assertThat(((McpSchema.TextContent) missingHeader.content().get(0)).text())
                .contains("header parameter: X-Trace-Id");

        McpSchema.CallToolResult missingBody = adapter.invokeTool("api_createorder", Map.of("_confirm", "CONFIRM"));
        assertThat(missingBody.isError()).isTrue();
        assertThat(((McpSchema.TextContent) missingBody.content().get(0)).text())
                .contains("request body: body");
    }

    @Test
    @SuppressWarnings("unchecked")
    void rejectsUnresolvedPathTemplates() {
        Map<String, OpenApiOperationDescriptor> operations =
                (Map<String, OpenApiOperationDescriptor>) ReflectionTestUtils.getField(operationCatalog, "operationsByToolName");
        assertThat(operations).isNotNull();

        OpenApiOperationDescriptor descriptor = new OpenApiOperationDescriptor(
                "api_broken_path",
                "brokenPath",
                HttpMethod.GET,
                "/orders/{orderId}",
                "Broken path descriptor",
                List.of("orders"),
                List.of(new OpenApiParameterDescriptor("id", OpenApiParameterLocation.PATH, true, null)),
                false,
                null,
                false
        );
        try {
            operations.put("api_broken_path", descriptor);

            McpSchema.CallToolResult result = adapter.invokeTool("api_broken_path", Map.of("id", "123"));

            assertThat(result.isError()).isTrue();
            assertThat(((McpSchema.TextContent) result.content().get(0)).text())
                    .contains("Unresolved path template");
        }
        finally {
            operations.remove("api_broken_path");
        }
    }

    @Test
    void filtersArgumentHeadersWithDenylistAndAllowlist() {
        await().atMost(15, SECONDS).untilAsserted(() ->
                assertThat(mcpSyncServer.listTools().stream().map(McpSchema.Tool::name).toList()).contains("api_readoptionalheaders"));

        try {
            properties.getExecution().setAllowedArgumentHeaders(Set.of());
            McpSchema.CallToolResult defaultFiltered = adapter.invokeTool(
                    "api_readoptionalheaders",
                    Map.of("_headers", Map.of("Host", "malicious.example", "X-Trace-Id", "trace-1"))
            );
            assertThat(defaultFiltered.isError()).isFalse();
            String defaultText = ((McpSchema.TextContent) defaultFiltered.content().get(0)).text();
            assertThat(defaultText).contains("trace-1");
            assertThat(defaultText).doesNotContain("malicious.example");

            properties.getExecution().setAllowedArgumentHeaders(Set.of("X-Allowed"));
            McpSchema.CallToolResult allowlisted = adapter.invokeTool(
                    "api_readoptionalheaders",
                    Map.of("_headers", Map.of("X-Allowed", "allowed", "X-Trace-Id", "trace-2"))
            );
            assertThat(allowlisted.isError()).isFalse();
            String allowlistedText = ((McpSchema.TextContent) allowlisted.content().get(0)).text();
            assertThat(allowlistedText).contains("allowed");
            assertThat(allowlistedText).doesNotContain("trace-2");
        }
        finally {
            properties.getExecution().setAllowedArgumentHeaders(Set.of());
        }
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

        @Operation(operationId = "getOrder", summary = "Get order by id")
        @GetMapping(path = "/orders/{orderId}", produces = MediaType.APPLICATION_JSON_VALUE)
        public Map<String, Object> getOrder(@PathVariable String orderId) {
            return Map.of("orderId", orderId);
        }

        @Operation(operationId = "searchOrders", summary = "Search orders")
        @GetMapping(path = "/orders/search", produces = MediaType.APPLICATION_JSON_VALUE)
        public Map<String, Object> searchOrders(@RequestParam String q) {
            return Map.of("query", q);
        }

        @Operation(operationId = "echoHeader", summary = "Echo required trace header")
        @GetMapping(path = "/echo-header", produces = MediaType.APPLICATION_JSON_VALUE)
        public Map<String, Object> echoHeader(@RequestHeader("X-Trace-Id") String traceId) {
            return Map.of("traceId", traceId);
        }

        @Operation(operationId = "readOptionalHeaders", summary = "Read optional headers")
        @GetMapping(path = "/optional-headers", produces = MediaType.APPLICATION_JSON_VALUE)
        public Map<String, Object> readOptionalHeaders(
                @RequestHeader(value = "Host", required = false) String host,
                @RequestHeader(value = "X-Trace-Id", required = false) String traceId,
                @RequestHeader(value = "X-Allowed", required = false) String allowed) {
            return Map.of(
                    "host", host == null ? "" : host,
                    "traceId", traceId == null ? "" : traceId,
                    "allowed", allowed == null ? "" : allowed
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
