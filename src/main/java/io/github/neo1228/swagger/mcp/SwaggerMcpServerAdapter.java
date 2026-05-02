package io.github.neo1228.swagger.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SwaggerMcpServerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(SwaggerMcpServerAdapter.class);
    private static final String ARGUMENTS_FIELD = "arguments";
    private static final String STEPS_FIELD = "steps";
    private static final Pattern WORKFLOW_TEMPLATE = Pattern.compile("\\$\\{([A-Za-z0-9_-]+):(.*?)}");

    private final McpSyncServer mcpSyncServer;
    private final OpenApiToMcpToolConverter converter;
    private final SwaggerMcpToolSelector toolSelector;
    private final SwaggerMcpOperationCatalog operationCatalog;
    private final SwaggerMcpResponseOptimizer responseOptimizer;
    private final SwaggerMcpSecurityPolicy securityPolicy;
    private final SwaggerMcpProperties properties;
    private final Environment environment;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final Set<String> registeredToolNames = ConcurrentHashMap.newKeySet();
    private final String discoverToolName;
    private final String describeToolName;
    private final String capabilitiesToolName;
    private final String validateToolName;
    private final String listGroupsToolName;
    private final String planWorkflowToolName;
    private final String invokeWorkflowToolName;
    private final String invokeByIntentToolName;

    public SwaggerMcpServerAdapter(
            McpSyncServer mcpSyncServer,
            OpenApiToMcpToolConverter converter,
            SwaggerMcpToolSelector toolSelector,
            SwaggerMcpOperationCatalog operationCatalog,
            SwaggerMcpResponseOptimizer responseOptimizer,
            SwaggerMcpSecurityPolicy securityPolicy,
            SwaggerMcpProperties properties,
            Environment environment,
            RestTemplateBuilder restTemplateBuilder,
            ObjectMapper objectMapper) {
        this.mcpSyncServer = mcpSyncServer;
        this.converter = converter;
        this.toolSelector = toolSelector;
        this.operationCatalog = operationCatalog;
        this.responseOptimizer = responseOptimizer;
        this.securityPolicy = securityPolicy;
        this.properties = properties;
        this.environment = environment;
        this.objectMapper = objectMapper;
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(properties.getExecution().getConnectTimeout())
                .withReadTimeout(properties.getExecution().getReadTimeout());
        this.restTemplate = restTemplateBuilder
                .requestFactorySettings(settings)
                .errorHandler(new DefaultResponseErrorHandler() {
                    @Override
                    public boolean hasError(HttpStatusCode statusCode) {
                        return false;
                    }
                })
                .build();
        this.discoverToolName = converter.toToolName("meta_discover_api_tools", properties.getToolNamePrefix());
        this.describeToolName = converter.toToolName("meta_describe_api_tool", properties.getToolNamePrefix());
        this.capabilitiesToolName = converter.toToolName("meta_get_api_capabilities", properties.getToolNamePrefix());
        this.validateToolName = converter.toToolName("meta_validate_api_call", properties.getToolNamePrefix());
        this.listGroupsToolName = converter.toToolName("meta_list_api_groups", properties.getToolNamePrefix());
        this.planWorkflowToolName = converter.toToolName("meta_plan_api_workflow", properties.getToolNamePrefix());
        this.invokeWorkflowToolName = converter.toToolName("meta_invoke_api_workflow", properties.getToolNamePrefix());
        this.invokeByIntentToolName = converter.toToolName("meta_invoke_api_by_intent", properties.getToolNamePrefix());
    }

    public synchronized void registerOperations(List<OpenApiOperationDescriptor> operations) {
        removeRegisteredTools();

        SwaggerMcpProperties.SmartContext smartContext = properties.getSmartContext();
        List<OpenApiOperationDescriptor> sourceOperations = operations == null ? List.of() : operations;
        List<OpenApiOperationDescriptor> eligibleOperations = new ArrayList<>();
        for (OpenApiOperationDescriptor operation : sourceOperations) {
            if (!securityPolicy.shouldExpose(operation)) {
                continue;
            }
            if (smartContext.isEnabled() && smartContext.isGatewayToolEnabled() && isReservedMetaToolName(operation.toolName())) {
                logger.debug("Skipping tool registration because name is reserved for a meta tool: {}", operation.toolName());
                continue;
            }
            eligibleOperations.add(operation);
        }
        operationCatalog.replaceAll(eligibleOperations);
        toolSelector.setCandidates(eligibleOperations);

        Set<String> existingToolNames = new LinkedHashSet<>();
        for (McpSchema.Tool tool : mcpSyncServer.listTools()) {
            existingToolNames.add(tool.name());
        }

        if (smartContext.isEnabled() && smartContext.isGatewayToolEnabled()) {
            registerDiscoverTool(existingToolNames);
            registerDescribeTool(existingToolNames);
            registerCapabilitiesTool(existingToolNames);
            registerValidateTool(existingToolNames);
            registerListGroupsTool(existingToolNames);
            registerPlanWorkflowTool(existingToolNames);
            registerInvokeWorkflowTool(existingToolNames);
            registerIntentInvokeTool(existingToolNames);
        }

        if (!smartContext.isGatewayOnly()) {
            for (OpenApiOperationDescriptor operation : eligibleOperations) {
                if (existingToolNames.contains(operation.toolName())) {
                    logger.debug("Skipping tool registration because name already exists: {}", operation.toolName());
                    continue;
                }
                registerOperationTool(operation);
                existingToolNames.add(operation.toolName());
            }
        }
        if (!registeredToolNames.isEmpty()) {
            mcpSyncServer.notifyToolsListChanged();
        }
    }

    public McpSchema.CallToolResult invokeTool(String toolName, Map<String, Object> arguments) {
        OpenApiOperationDescriptor operation = operationCatalog.findByToolName(toolName).orElse(null);
        if (operation == null) {
            return errorResult("Unknown tool: " + toolName);
        }
        Map<String, Object> safeArguments = copyMap(arguments);
        securityPolicy.auditStart(operation, safeArguments);
        try {
            String argumentValidation = validateRequiredArguments(operation, safeArguments);
            if (argumentValidation != null) {
                securityPolicy.auditEnd(operation, false, 400);
                return errorResult(argumentValidation);
            }

            var validationResult = securityPolicy.validateExecution(operation, safeArguments);
            if (validationResult.isPresent()) {
                securityPolicy.auditEnd(operation, false, 403);
                return errorResult(validationResult.get());
            }

            ResponseEntity<String> response = executeHttp(operation, safeArguments);
            String responseBody = response.getBody() == null ? "" : response.getBody();
            SwaggerMcpResponseOptimizer.OptimizationResult optimized = responseOptimizer.optimize(responseBody, safeArguments);

            boolean success = response.getStatusCode().is2xxSuccessful();
            securityPolicy.auditEnd(operation, success, response.getStatusCode().value());

            String text = "HTTP " + response.getStatusCode().value() + "\n" + optimized.text();
            McpSchema.CallToolResult.Builder resultBuilder = McpSchema.CallToolResult.builder()
                    .isError(!success)
                    .addTextContent(text);
            if (optimized.structuredContent() != null) {
                resultBuilder.structuredContent(optimized.structuredContent());
            }
            return resultBuilder.build();
        }
        catch (Exception ex) {
            securityPolicy.auditEnd(operation, false, 500);
            logger.warn("Tool execution failed: {}", operation.toolName(), ex);
            return errorResult("Tool execution failed: " + ex.getMessage());
        }
    }

    private boolean isReservedMetaToolName(String toolName) {
        return discoverToolName.equals(toolName)
                || describeToolName.equals(toolName)
                || capabilitiesToolName.equals(toolName)
                || validateToolName.equals(toolName)
                || listGroupsToolName.equals(toolName)
                || planWorkflowToolName.equals(toolName)
                || invokeWorkflowToolName.equals(toolName)
                || invokeByIntentToolName.equals(toolName);
    }

    private void registerOperationTool(OpenApiOperationDescriptor operation) {
        McpSchema.Tool tool = converter.convert(operation, properties);
        McpServerFeatures.SyncToolSpecification specification = McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> invokeTool(operation.toolName(), request.arguments()))
                .build();
        mcpSyncServer.addTool(specification);
        registeredToolNames.add(operation.toolName());
    }

    private void registerDiscoverTool(Set<String> existingToolNames) {
        if (existingToolNames.contains(discoverToolName)) {
            return;
        }
        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name(discoverToolName)
                .title("Discover Relevant API Tools")
                .description("Find the most relevant API tools for a user request")
                .inputSchema(new McpSchema.JsonSchema(
                        "object",
                        discoverInputSchemaProperties(),
                        List.of("query"),
                        Boolean.FALSE,
                        null,
                        null
                ))
                .annotations(new McpSchema.ToolAnnotations(
                        "Discover API Tools",
                        Boolean.TRUE,
                        Boolean.FALSE,
                        Boolean.TRUE,
                        Boolean.FALSE,
                        Boolean.FALSE
                ))
                .build();

        McpServerFeatures.SyncToolSpecification specification = McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> discoverRelevantTools(request.arguments()))
                .build();

        mcpSyncServer.addTool(specification);
        existingToolNames.add(discoverToolName);
        registeredToolNames.add(discoverToolName);
    }

    private void registerDescribeTool(Set<String> existingToolNames) {
        if (existingToolNames.contains(describeToolName)) {
            return;
        }
        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name(describeToolName)
                .title("Describe API Tool Contract")
                .description("Return the full OpenAPI-derived contract for one generated API tool")
                .inputSchema(new McpSchema.JsonSchema(
                        "object",
                        describeInputSchemaProperties(),
                        List.of("toolName"),
                        Boolean.FALSE,
                        null,
                        null
                ))
                .annotations(new McpSchema.ToolAnnotations(
                        "Describe API Tool",
                        Boolean.TRUE,
                        Boolean.FALSE,
                        Boolean.TRUE,
                        Boolean.FALSE,
                        Boolean.FALSE
                ))
                .build();

        McpServerFeatures.SyncToolSpecification specification = McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> describeApiTool(request.arguments()))
                .build();

        mcpSyncServer.addTool(specification);
        existingToolNames.add(describeToolName);
        registeredToolNames.add(describeToolName);
    }

    private void registerCapabilitiesTool(Set<String> existingToolNames) {
        if (existingToolNames.contains(capabilitiesToolName)) {
            return;
        }
        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name(capabilitiesToolName)
                .title("Get API Gateway Capabilities")
                .description("Return catalog, safety, orchestration, and execution-policy summaries for MCP clients")
                .inputSchema(new McpSchema.JsonSchema(
                        "object",
                        capabilitiesInputSchemaProperties(),
                        List.of(),
                        Boolean.FALSE,
                        null,
                        null
                ))
                .annotations(new McpSchema.ToolAnnotations(
                        "Get API Capabilities",
                        Boolean.TRUE,
                        Boolean.FALSE,
                        Boolean.TRUE,
                        Boolean.FALSE,
                        Boolean.FALSE
                ))
                .build();

        McpServerFeatures.SyncToolSpecification specification = McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> getApiCapabilities(request.arguments()))
                .build();

        mcpSyncServer.addTool(specification);
        existingToolNames.add(capabilitiesToolName);
        registeredToolNames.add(capabilitiesToolName);
    }

    private void registerValidateTool(Set<String> existingToolNames) {
        if (existingToolNames.contains(validateToolName)) {
            return;
        }
        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name(validateToolName)
                .title("Validate API Call")
                .description("Validate generated API tool arguments and execution policy without dispatching HTTP")
                .inputSchema(new McpSchema.JsonSchema(
                        "object",
                        validateInputSchemaProperties(),
                        List.of("toolName"),
                        Boolean.FALSE,
                        null,
                        null
                ))
                .annotations(new McpSchema.ToolAnnotations(
                        "Validate API Call",
                        Boolean.TRUE,
                        Boolean.FALSE,
                        Boolean.TRUE,
                        Boolean.FALSE,
                        Boolean.FALSE
                ))
                .build();

        McpServerFeatures.SyncToolSpecification specification = McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> validateApiCall(request.arguments()))
                .build();

        mcpSyncServer.addTool(specification);
        existingToolNames.add(validateToolName);
        registeredToolNames.add(validateToolName);
    }

    private void registerListGroupsTool(Set<String> existingToolNames) {
        if (existingToolNames.contains(listGroupsToolName)) {
            return;
        }
        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name(listGroupsToolName)
                .title("List API Tool Groups")
                .description("Summarize the exposed API tool catalog by OpenAPI tag/group")
                .inputSchema(new McpSchema.JsonSchema(
                        "object",
                        listGroupsInputSchemaProperties(),
                        List.of(),
                        Boolean.FALSE,
                        null,
                        null
                ))
                .annotations(new McpSchema.ToolAnnotations(
                        "List API Groups",
                        Boolean.TRUE,
                        Boolean.FALSE,
                        Boolean.TRUE,
                        Boolean.FALSE,
                        Boolean.FALSE
                ))
                .build();

        McpServerFeatures.SyncToolSpecification specification = McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> listApiGroups(request.arguments()))
                .build();

        mcpSyncServer.addTool(specification);
        existingToolNames.add(listGroupsToolName);
        registeredToolNames.add(listGroupsToolName);
    }

    private void registerPlanWorkflowTool(Set<String> existingToolNames) {
        if (existingToolNames.contains(planWorkflowToolName)) {
            return;
        }
        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name(planWorkflowToolName)
                .title("Plan API Workflow")
                .description("Create a deterministic multi-step API workflow plan from the exposed OpenAPI tool catalog")
                .inputSchema(new McpSchema.JsonSchema(
                        "object",
                        planWorkflowInputSchemaProperties(),
                        List.of("goal"),
                        Boolean.FALSE,
                        null,
                        null
                ))
                .annotations(new McpSchema.ToolAnnotations(
                        "Plan API Workflow",
                        Boolean.TRUE,
                        Boolean.FALSE,
                        Boolean.TRUE,
                        Boolean.FALSE,
                        Boolean.FALSE
                ))
                .build();

        McpServerFeatures.SyncToolSpecification specification = McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> planApiWorkflow(request.arguments()))
                .build();

        mcpSyncServer.addTool(specification);
        existingToolNames.add(planWorkflowToolName);
        registeredToolNames.add(planWorkflowToolName);
    }

    private void registerInvokeWorkflowTool(Set<String> existingToolNames) {
        if (existingToolNames.contains(invokeWorkflowToolName)) {
            return;
        }
        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name(invokeWorkflowToolName)
                .title("Invoke API Workflow")
                .description("Dry-run or execute multiple generated API tools sequentially with JSONPath-based step interpolation")
                .inputSchema(new McpSchema.JsonSchema(
                        "object",
                        invokeWorkflowInputSchemaProperties(),
                        List.of(STEPS_FIELD),
                        Boolean.FALSE,
                        null,
                        null
                ))
                .annotations(new McpSchema.ToolAnnotations(
                        "Invoke API Workflow",
                        Boolean.FALSE,
                        Boolean.FALSE,
                        Boolean.FALSE,
                        Boolean.FALSE,
                        Boolean.FALSE
                ))
                .build();

        McpServerFeatures.SyncToolSpecification specification = McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> invokeApiWorkflow(request.arguments()))
                .build();

        mcpSyncServer.addTool(specification);
        existingToolNames.add(invokeWorkflowToolName);
        registeredToolNames.add(invokeWorkflowToolName);
    }

    private void registerIntentInvokeTool(Set<String> existingToolNames) {
        if (existingToolNames.contains(invokeByIntentToolName)) {
            return;
        }
        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name(invokeByIntentToolName)
                .title("Invoke API By Intent")
                .description("Select and execute the best matching API tool for a user request")
                .inputSchema(new McpSchema.JsonSchema(
                        "object",
                        invokeByIntentSchemaProperties(),
                        List.of("query"),
                        Boolean.FALSE,
                        null,
                        null
                ))
                .annotations(new McpSchema.ToolAnnotations(
                        "Invoke API By Intent",
                        Boolean.FALSE,
                        Boolean.FALSE,
                        Boolean.FALSE,
                        Boolean.FALSE,
                        Boolean.FALSE
                ))
                .build();

        McpServerFeatures.SyncToolSpecification specification = McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> invokeByIntent(request.arguments()))
                .build();

        mcpSyncServer.addTool(specification);
        existingToolNames.add(invokeByIntentToolName);
        registeredToolNames.add(invokeByIntentToolName);
    }

    McpSchema.CallToolResult describeApiTool(Map<String, Object> arguments) {
        Map<String, Object> safeArguments = copyMap(arguments);
        String toolName = asString(safeArguments.get("toolName"));
        if (!StringUtils.hasText(toolName)) {
            return errorResult("toolName is required");
        }
        OpenApiOperationDescriptor operation = operationCatalog.findByToolName(toolName).orElse(null);
        if (operation == null) {
            return errorResult("Unknown tool: " + toolName);
        }
        Map<String, Object> structured = describeOperation(operation);
        return successResult(structured);
    }

    McpSchema.CallToolResult getApiCapabilities(Map<String, Object> arguments) {
        Map<String, Object> safeArguments = copyMap(arguments);
        int maxGroups = Math.max(0, asInt(safeArguments.get("maxGroups"), 10));
        int maxToolsPerGroup = Math.max(0, asInt(safeArguments.get("maxToolsPerGroup"), 3));

        SwaggerMcpOperationCatalog.CatalogStats stats = operationCatalog.stats();
        List<Map<String, Object>> groups = new ArrayList<>();
        int count = 0;
        for (SwaggerMcpOperationCatalog.GroupSummary group : operationCatalog.summarizeGroups(maxToolsPerGroup)) {
            if (maxGroups > 0 && count >= maxGroups) {
                break;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", group.name());
            item.put("operationCount", group.operationCount());
            item.put("readOnlyCount", group.readOnlyCount());
            item.put("riskyCount", group.riskyCount());
            item.put("methods", group.methods());
            item.put("sampleTools", group.sampleTools());
            groups.add(item);
            count++;
        }

        Map<String, Object> structured = new LinkedHashMap<>();
        structured.put("catalog", mapOf(
                "operationCount", stats.operationCount(),
                "groupCount", stats.groupCount(),
                "readOnlyCount", stats.readOnlyCount(),
                "riskyCount", stats.riskyCount(),
                "groups", groups
        ));
        structured.put("gatewayTools", List.of(
                capabilitiesToolName,
                listGroupsToolName,
                discoverToolName,
                describeToolName,
                validateToolName,
                planWorkflowToolName,
                invokeWorkflowToolName,
                invokeByIntentToolName
        ));
        structured.put("orchestration", mapOf(
                "workflowPlanning", planWorkflowToolName,
                "workflowExecution", invokeWorkflowToolName,
                "defaultDryRun", true,
                "stepInterpolation", "${stepId:$.json.path}",
                "recursiveMetaToolsAllowed", false
        ));
        structured.put("safety", mapOf(
                "validateBeforeExecute", validateToolName,
                "riskyMethods", properties.getSecurity().getRiskyHttpMethods(),
                "confirmationRequiredForRiskyOperations", properties.getSecurity().isRequireConfirmationForRiskyOperations(),
                "confirmationArgument", "_confirm",
                "dynamicHeadersArgument", "_headers",
                "blockedArgumentHeaders", properties.getExecution().getBlockedArgumentHeaders(),
                "allowedArgumentHeaders", properties.getExecution().getAllowedArgumentHeaders()
        ));
        structured.put("responseControls", mapOf(
                "projectionArgumentEnabled", properties.getResponse().isProjectionArgumentEnabled(),
                "projectionArgument", "_projection",
                "summarizeArgument", "_summarize",
                "maxChars", properties.getResponse().getMaxChars(),
                "maxDepth", properties.getResponse().getMaxDepth()
        ));
        return successResult(structured);
    }

    McpSchema.CallToolResult validateApiCall(Map<String, Object> arguments) {
        Map<String, Object> safeArguments = copyMap(arguments);
        String toolName = asString(safeArguments.get("toolName"));
        if (!StringUtils.hasText(toolName)) {
            return errorResult("toolName is required");
        }
        if (isReservedMetaToolName(toolName)) {
            return errorResult("Meta tools cannot be validated as API calls: " + toolName);
        }
        OpenApiOperationDescriptor operation = operationCatalog.findByToolName(toolName).orElse(null);
        if (operation == null) {
            return errorResult("Unknown tool: " + toolName);
        }

        Map<String, Object> delegatedArguments = extractDelegatedArguments(safeArguments);
        ToolCallValidation validation = validateToolCall(operation, delegatedArguments);
        return successResult(validation.toStructuredContent());
    }

    McpSchema.CallToolResult listApiGroups(Map<String, Object> arguments) {
        Map<String, Object> safeArguments = copyMap(arguments);
        int maxToolsPerGroup = Math.max(0, asInt(safeArguments.get("maxToolsPerGroup"), 5));

        SwaggerMcpOperationCatalog.CatalogStats stats = operationCatalog.stats();
        List<Map<String, Object>> groups = new ArrayList<>();
        for (SwaggerMcpOperationCatalog.GroupSummary group : operationCatalog.summarizeGroups(maxToolsPerGroup)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", group.name());
            item.put("operationCount", group.operationCount());
            item.put("readOnlyCount", group.readOnlyCount());
            item.put("riskyCount", group.riskyCount());
            item.put("methods", group.methods());
            item.put("sampleTools", group.sampleTools());
            groups.add(item);
        }

        Map<String, Object> structured = new LinkedHashMap<>();
        structured.put("operationCount", stats.operationCount());
        structured.put("groupCount", stats.groupCount());
        structured.put("readOnlyCount", stats.readOnlyCount());
        structured.put("riskyCount", stats.riskyCount());
        structured.put("groups", groups);
        return successResult(structured);
    }

    McpSchema.CallToolResult planApiWorkflow(Map<String, Object> arguments) {
        Map<String, Object> safeArguments = copyMap(arguments);
        String goal = asString(safeArguments.get("goal"));
        if (!StringUtils.hasText(goal)) {
            return errorResult("goal is required");
        }
        int requestedTopK = asInt(safeArguments.get("topK"), properties.getSmartContext().getDefaultTopK());
        int topK = Math.max(1, requestedTopK);

        List<SwaggerMcpToolSelector.ScoredTool> candidates = toolSelector.select(goal, topK);
        List<Map<String, Object>> steps = new ArrayList<>();
        int index = 1;
        for (SwaggerMcpToolSelector.ScoredTool candidate : candidates) {
            OpenApiOperationDescriptor operation = candidate.operation();
            Map<String, Object> contract = describeOperation(operation);
            Map<String, Object> step = new LinkedHashMap<>();
            step.put("stepId", "step" + index++);
            step.put("toolName", operation.toolName());
            step.put("operationId", operation.operationId());
            step.put("method", operation.httpMethod().name());
            step.put("path", operation.path());
            step.put("description", operation.description());
            step.put("score", candidate.score());
            step.put("readOnly", operation.isReadOnly());
            step.put("idempotent", operation.isIdempotent());
            step.put("risky", operation.risky());
            step.put("requiredArguments", contract.get("requiredArguments"));
            step.put("inputSchema", contract.get("inputSchema"));
            steps.add(step);
        }

        Map<String, Object> structured = new LinkedHashMap<>();
        structured.put("goal", goal);
        structured.put("candidateCount", steps.size());
        structured.put("steps", steps);
        structured.put("executionModel", mapOf(
                "toolName", invokeWorkflowToolName,
                "defaultDryRun", true,
                "order", "Steps are validated and then executed sequentially when dryRun=false",
                "arguments", "Each step accepts {id, toolName, arguments, continueOnError}",
                "interpolation", "Use ${stepId:$.json.path} in later step arguments to read prior structuredContent",
                "safety", "Meta tools cannot be invoked recursively; risky operations still require _confirm"
        ));
        return successResult(structured);
    }

    McpSchema.CallToolResult invokeApiWorkflow(Map<String, Object> arguments) {
        Map<String, Object> safeArguments = copyMap(arguments);
        Object rawSteps = safeArguments.get(STEPS_FIELD);
        if (!(rawSteps instanceof List<?> rawStepList) || rawStepList.isEmpty()) {
            return errorResult("steps must be a non-empty array");
        }

        List<Map<String, Object>> steps = new ArrayList<>();
        for (Object rawStep : rawStepList) {
            if (!(rawStep instanceof Map<?, ?> rawStepMap)) {
                return errorResult("Each workflow step must be an object");
            }
            steps.add(copyStringKeyMap(rawStepMap));
        }

        boolean dryRun = asBoolean(safeArguments.get("dryRun"), true);
        boolean continueOnError = asBoolean(safeArguments.get("continueOnError"), false);
        List<Map<String, Object>> stepResults = new ArrayList<>();
        Map<String, Object> workflowContext = new LinkedHashMap<>();
        Set<String> stepIds = new LinkedHashSet<>();
        Set<String> dryRunAvailableStepIds = new LinkedHashSet<>();
        boolean success = true;

        for (int i = 0; i < steps.size(); i++) {
            Map<String, Object> step = steps.get(i);
            String id = workflowStepId(step, i);
            if (!stepIds.add(id)) {
                return errorResult("Duplicate workflow step id: " + id);
            }
            String toolName = asString(step.get("toolName"));
            if (!StringUtils.hasText(toolName)) {
                return errorResult("steps[" + i + "].toolName is required");
            }
            if (isReservedMetaToolName(toolName)) {
                return errorResult("Workflow steps cannot invoke meta tools: " + toolName);
            }
            OpenApiOperationDescriptor operation = operationCatalog.findByToolName(toolName).orElse(null);
            if (operation == null) {
                return errorResult("Unknown workflow step tool: " + toolName);
            }

            Map<String, Object> originalArguments = stepArguments(step);
            Map<String, Object> resolvedArguments;
            WorkflowReferenceValidation referenceValidation = new WorkflowReferenceValidation(List.of(), List.of());
            try {
                if (dryRun) {
                    referenceValidation = validateWorkflowReferences(originalArguments, dryRunAvailableStepIds);
                    resolvedArguments = maskWorkflowTemplates(originalArguments);
                }
                else {
                    resolvedArguments = resolveWorkflowArguments(originalArguments, workflowContext);
                }
            }
            catch (IllegalArgumentException ex) {
                return errorResult("Failed to resolve workflow step '" + id + "': " + ex.getMessage());
            }

            Map<String, Object> stepResult = new LinkedHashMap<>();
            stepResult.put("id", id);
            stepResult.put("toolName", toolName);
            stepResult.put("method", operation.httpMethod().name());
            stepResult.put("path", operation.path());
            stepResult.put("risky", operation.risky());
            stepResult.put("arguments", resolvedArguments);

            if (dryRun) {
                ToolCallValidation validation = validateToolCall(operation, resolvedArguments);
                Map<String, Object> validationContent = validation.toStructuredContent(referenceValidation.errors());
                validationContent.put("workflowReferences", referenceValidation.references());
                stepResult.putAll(validationContent);
                if (!Boolean.TRUE.equals(validationContent.get("valid"))) {
                    success = false;
                }
                stepResults.add(stepResult);
                if (Boolean.TRUE.equals(validationContent.get("valid"))) {
                    dryRunAvailableStepIds.add(id);
                }
                else {
                    boolean stepContinueOnError = asBoolean(step.get("continueOnError"), continueOnError);
                    if (!stepContinueOnError) {
                        break;
                    }
                }
                continue;
            }

            McpSchema.CallToolResult delegatedResult = invokeTool(toolName, resolvedArguments);
            boolean stepError = Boolean.TRUE.equals(delegatedResult.isError());
            stepResult.put("isError", stepError);
            stepResult.put("text", firstText(delegatedResult));
            stepResult.put("structuredContent", delegatedResult.structuredContent());
            stepResults.add(stepResult);
            workflowContext.put(id, delegatedResult.structuredContent() != null
                    ? delegatedResult.structuredContent()
                    : firstText(delegatedResult));

            if (stepError) {
                success = false;
                boolean stepContinueOnError = asBoolean(step.get("continueOnError"), continueOnError);
                if (!stepContinueOnError) {
                    break;
                }
            }
        }

        Map<String, Object> structured = new LinkedHashMap<>();
        structured.put("dryRun", dryRun);
        structured.put("stepCount", stepResults.size());
        structured.put("success", success);
        structured.put("steps", stepResults);
        return McpSchema.CallToolResult.builder()
                .isError(!success)
                .addTextContent(toJsonText(structured))
                .structuredContent(structured)
                .build();
    }

    private McpSchema.CallToolResult discoverRelevantTools(Map<String, Object> arguments) {
        Map<String, Object> safeArguments = copyMap(arguments);
        String query = asString(safeArguments.get("query"));
        if (!StringUtils.hasText(query)) {
            return errorResult("query is required");
        }
        int requestedTopK = asInt(safeArguments.get("topK"), properties.getSmartContext().getDefaultTopK());
        int topK = Math.max(1, requestedTopK);

        List<SwaggerMcpToolSelector.ScoredTool> results = toolSelector.select(query, topK);
        List<Map<String, Object>> payload = new ArrayList<>();
        for (SwaggerMcpToolSelector.ScoredTool scoredTool : results) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("toolName", scoredTool.operation().toolName());
            item.put("operationId", scoredTool.operation().operationId());
            item.put("method", scoredTool.operation().httpMethod().name());
            item.put("path", scoredTool.operation().path());
            item.put("description", scoredTool.operation().description());
            item.put("score", scoredTool.score());
            payload.add(item);
        }

        Map<String, Object> structured = new LinkedHashMap<>();
        structured.put("query", query);
        structured.put("count", payload.size());
        structured.put("tools", payload);

        return successResult(structured);
    }

    private McpSchema.CallToolResult invokeByIntent(Map<String, Object> arguments) {
        Map<String, Object> safeArguments = copyMap(arguments);
        String query = asString(safeArguments.get("query"));
        if (!StringUtils.hasText(query)) {
            return errorResult("query is required");
        }
        int requestedTopK = asInt(safeArguments.get("topK"), properties.getSmartContext().getDefaultTopK());
        int topK = Math.max(1, requestedTopK);

        List<SwaggerMcpToolSelector.ScoredTool> results = toolSelector.select(query, topK);
        if (results.isEmpty()) {
            return errorResult("No matching API tool found for query: " + query);
        }
        SwaggerMcpToolSelector.ScoredTool selected = results.get(0);
        if (selected.score() < properties.getSmartContext().getMinScore()) {
            return errorResult("No API tool passed the minimum relevance threshold for query: " + query);
        }

        Map<String, Object> delegatedArguments = extractDelegatedArguments(safeArguments);
        McpSchema.CallToolResult delegatedResult = invokeTool(selected.operation().toolName(), delegatedArguments);

        Map<String, Object> wrapper = new LinkedHashMap<>();
        wrapper.put("selectedTool", selected.operation().toolName());
        wrapper.put("score", selected.score());
        wrapper.put("result", delegatedResult.structuredContent());

        String text = "Selected tool: " + selected.operation().toolName() + "\n" + firstText(delegatedResult);
        McpSchema.CallToolResult.Builder builder = McpSchema.CallToolResult.builder()
                .isError(Boolean.TRUE.equals(delegatedResult.isError()))
                .addTextContent(text);
        builder.structuredContent(wrapper);
        return builder.build();
    }

    private ResponseEntity<String> executeHttp(OpenApiOperationDescriptor operation, Map<String, Object> arguments) {
        String resolvedPath = resolvePath(operation, arguments);
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(resolveBaseUrl() + resolvedPath);
        applyQueryParameters(uriBuilder, operation, arguments);

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.ALL));
        applyDefaultHeaders(headers);
        applyHeaderParameters(headers, operation, arguments);
        applyArgumentHeaders(headers, arguments);
        copyIncomingHeaders(headers);

        Object body = resolveRequestBody(operation, arguments);
        HttpEntity<?> requestEntity = body == null ? new HttpEntity<>(headers) : new HttpEntity<>(body, headers);
        return restTemplate.exchange(uriBuilder.build(true).toUri(), operation.httpMethod(), requestEntity, String.class);
    }

    private void applyDefaultHeaders(HttpHeaders headers) {
        Map<String, String> defaultHeaders = properties.getExecution().getDefaultHeaders();
        if (defaultHeaders == null || defaultHeaders.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> entry : defaultHeaders.entrySet()) {
            if (StringUtils.hasText(entry.getKey()) && entry.getValue() != null) {
                headers.set(entry.getKey(), entry.getValue());
            }
        }
    }

    private void applyQueryParameters(UriComponentsBuilder uriBuilder, OpenApiOperationDescriptor operation, Map<String, Object> arguments) {
        for (OpenApiParameterDescriptor parameter : operation.parameters()) {
            if (parameter.location() != OpenApiParameterLocation.QUERY) {
                continue;
            }
            Object value = arguments.get(parameter.name());
            if (value == null) {
                continue;
            }
            if (value instanceof Collection<?> collection) {
                if (!collection.isEmpty()) {
                    uriBuilder.queryParam(parameter.name(), collection.toArray());
                }
            }
            else {
                uriBuilder.queryParam(parameter.name(), value);
            }
        }
    }

    private void applyHeaderParameters(HttpHeaders headers, OpenApiOperationDescriptor operation, Map<String, Object> arguments) {
        for (OpenApiParameterDescriptor parameter : operation.parameters()) {
            if (parameter.location() != OpenApiParameterLocation.HEADER) {
                continue;
            }
            Object value = arguments.get(parameter.name());
            if (value != null) {
                headers.set(parameter.name(), String.valueOf(value));
            }
        }
    }

    private void applyArgumentHeaders(HttpHeaders headers, Map<String, Object> arguments) {
        Object rawHeaders = arguments.get("_headers");
        if (!(rawHeaders instanceof Map<?, ?> headerMap)) {
            return;
        }
        for (Map.Entry<?, ?> entry : headerMap.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            String headerName = String.valueOf(entry.getKey()).trim();
            if (!StringUtils.hasText(headerName) || !isArgumentHeaderAllowed(headerName)) {
                continue;
            }
            headers.set(headerName, String.valueOf(entry.getValue()));
        }
    }

    private boolean isArgumentHeaderAllowed(String headerName) {
        String normalized = headerName.toLowerCase(Locale.ROOT);
        Set<String> allowedHeaders = properties.getExecution().getAllowedArgumentHeaders();
        if (allowedHeaders != null && !allowedHeaders.isEmpty() && !containsIgnoreCase(allowedHeaders, normalized)) {
            return false;
        }
        Set<String> blockedHeaders = properties.getExecution().getBlockedArgumentHeaders();
        return blockedHeaders == null || !containsIgnoreCase(blockedHeaders, normalized);
    }

    private boolean containsIgnoreCase(Collection<String> candidates, String normalizedValue) {
        if (candidates == null || candidates.isEmpty()) {
            return false;
        }
        for (String candidate : candidates) {
            if (candidate != null && candidate.trim().toLowerCase(Locale.ROOT).equals(normalizedValue)) {
                return true;
            }
        }
        return false;
    }

    private void copyIncomingHeaders(HttpHeaders headers) {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (!(requestAttributes instanceof ServletRequestAttributes servletRequestAttributes)) {
            return;
        }
        HttpServletRequest request = servletRequestAttributes.getRequest();
        if (request == null) {
            return;
        }
        if (properties.getExecution().isCopyIncomingAuthorizationHeader()) {
            String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (StringUtils.hasText(authorization) && !headers.containsKey(HttpHeaders.AUTHORIZATION)) {
                headers.set(HttpHeaders.AUTHORIZATION, authorization);
            }
        }
        if (properties.getExecution().isCopyIncomingCookieHeader()) {
            String cookie = request.getHeader(HttpHeaders.COOKIE);
            if (StringUtils.hasText(cookie) && !headers.containsKey(HttpHeaders.COOKIE)) {
                headers.set(HttpHeaders.COOKIE, cookie);
            }
        }
    }

    private String validateRequiredArguments(OpenApiOperationDescriptor operation, Map<String, Object> arguments) {
        List<String> missing = new ArrayList<>();
        for (OpenApiParameterDescriptor parameter : operation.parameters()) {
            if (parameter.required() && isMissing(arguments.get(parameter.name()))) {
                missing.add(parameter.location().name().toLowerCase(Locale.ROOT) + " parameter: " + parameter.name());
            }
        }
        if (operation.requestBodyRequired() && isMissing(arguments.get("body"))) {
            missing.add("request body: body");
        }
        if (!missing.isEmpty()) {
            return "Missing required argument(s): " + String.join(", ", missing);
        }
        return null;
    }

    private ToolCallValidation validateToolCall(OpenApiOperationDescriptor operation, Map<String, Object> arguments) {
        Map<String, Object> safeArguments = copyMap(arguments);
        List<String> errors = new ArrayList<>();
        String argumentValidation = validateRequiredArguments(operation, safeArguments);
        if (argumentValidation != null) {
            errors.add(argumentValidation);
        }
        securityPolicy.validateExecution(operation, safeArguments).ifPresent(errors::add);

        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("method", operation.httpMethod().name());
        preview.put("pathTemplate", operation.path());
        if (argumentValidation == null) {
            try {
                preview.put("resolvedPath", resolvePath(operation, safeArguments));
            }
            catch (Exception ex) {
                errors.add(ex.getMessage());
                preview.put("resolvedPathError", ex.getMessage());
            }
        }
        else {
            preview.put("resolvedPathError", argumentValidation);
        }
        preview.put("queryArguments", argumentNamesForLocation(operation, OpenApiParameterLocation.QUERY, safeArguments));
        preview.put("headerArguments", argumentNamesForLocation(operation, OpenApiParameterLocation.HEADER, safeArguments));
        preview.put("hasBody", safeArguments.containsKey("body"));

        Map<String, Object> contract = describeOperation(operation);
        return new ToolCallValidation(
                errors.isEmpty(),
                operation.toolName(),
                operation.operationId(),
                operation.risky(),
                operation.isReadOnly(),
                operation.isIdempotent(),
                contract.get("requiredArguments"),
                errors,
                preview
        );
    }

    private List<String> argumentNamesForLocation(
            OpenApiOperationDescriptor operation,
            OpenApiParameterLocation location,
            Map<String, Object> arguments) {
        List<String> names = new ArrayList<>();
        for (OpenApiParameterDescriptor parameter : operation.parameters()) {
            if (parameter.location() == location && arguments.containsKey(parameter.name())) {
                names.add(parameter.name());
            }
        }
        return names;
    }

    private boolean isMissing(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String stringValue) {
            return !StringUtils.hasText(stringValue);
        }
        return false;
    }

    private Object resolveRequestBody(OpenApiOperationDescriptor operation, Map<String, Object> arguments) {
        if (operation.requestBodySchema() == null) {
            return null;
        }
        Object body = arguments.get("body");
        if (body == null && operation.requestBodyRequired()) {
            throw new IllegalArgumentException("body is required for " + operation.toolName());
        }
        return body;
    }

    private String resolvePath(OpenApiOperationDescriptor operation, Map<String, Object> arguments) {
        String resolvedPath = operation.path();
        for (OpenApiParameterDescriptor parameter : operation.parameters()) {
            if (parameter.location() != OpenApiParameterLocation.PATH) {
                continue;
            }
            Object value = arguments.get(parameter.name());
            if (value == null) {
                if (parameter.required()) {
                    throw new IllegalArgumentException("Missing required path parameter: " + parameter.name());
                }
                continue;
            }
            String placeholder = "{" + parameter.name() + "}";
            String encodedValue = UriUtils.encodePathSegment(String.valueOf(value), StandardCharsets.UTF_8);
            resolvedPath = resolvedPath.replace(placeholder, encodedValue);
        }
        if (resolvedPath.contains("{") || resolvedPath.contains("}")) {
            throw new IllegalArgumentException("Unresolved path template for " + operation.toolName() + ": " + resolvedPath);
        }
        return resolvedPath;
    }

    private String resolveBaseUrl() {
        String configuredBaseUrl = properties.getExecution().getBaseUrl();
        if (StringUtils.hasText(configuredBaseUrl)) {
            return trimTrailingSlash(configuredBaseUrl);
        }
        String port = environment.getProperty("local.server.port");
        if (!StringUtils.hasText(port)) {
            port = environment.getProperty("server.port", "8080");
        }
        return "http://127.0.0.1:" + port;
    }

    private String trimTrailingSlash(String value) {
        String result = value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private Map<String, Object> describeOperation(OpenApiOperationDescriptor operation) {
        McpSchema.Tool tool = converter.convert(operation, properties);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("toolName", operation.toolName());
        payload.put("operationId", operation.operationId());
        payload.put("method", operation.httpMethod().name());
        payload.put("path", operation.path());
        payload.put("description", operation.description());
        payload.put("tags", operation.tags());
        payload.put("readOnly", operation.isReadOnly());
        payload.put("idempotent", operation.isIdempotent());
        payload.put("risky", operation.risky());
        payload.put("confirmationRequired", operation.risky()
                && properties.getSecurity().isRequireConfirmationForRiskyOperations());
        payload.put("requiredArguments", tool.inputSchema().required() == null ? List.of() : tool.inputSchema().required());
        Map<String, Object> inputProperties = tool.inputSchema().properties();
        payload.put("inputSchema", mapOf(
                "type", tool.inputSchema().type(),
                "properties", inputProperties,
                "required", tool.inputSchema().required(),
                "additionalProperties", tool.inputSchema().additionalProperties()
        ));
        payload.put("parameters", describeParameters(operation, inputProperties));
        payload.put("requestBody", describeRequestBody(operation, tool));
        return payload;
    }

    private List<Map<String, Object>> describeParameters(
            OpenApiOperationDescriptor operation,
            Map<String, Object> inputProperties) {
        List<Map<String, Object>> parameters = new ArrayList<>();
        for (OpenApiParameterDescriptor parameter : operation.parameters()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", parameter.name());
            item.put("location", parameter.location().name().toLowerCase(Locale.ROOT));
            item.put("required", parameter.required());
            item.put("schema", inputProperties.get(parameter.name()));
            parameters.add(item);
        }
        return parameters;
    }

    private Map<String, Object> describeRequestBody(OpenApiOperationDescriptor operation, McpSchema.Tool tool) {
        if (operation.requestBodySchema() == null) {
            return mapOf("required", false, "schema", null);
        }
        return mapOf(
                "required", operation.requestBodyRequired(),
                "argumentName", "body",
                "schema", tool.inputSchema().properties().get("body")
        );
    }

    private Map<String, Object> discoverInputSchemaProperties() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("query", mapOf("type", "string", "description", "Natural language intent"));
        properties.put("topK", mapOf("type", "integer", "description", "Number of tools to return"));
        return properties;
    }

    private Map<String, Object> describeInputSchemaProperties() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("toolName", mapOf("type", "string", "description", "Generated API tool name to describe"));
        return properties;
    }

    private Map<String, Object> capabilitiesInputSchemaProperties() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("maxGroups", mapOf("type", "integer", "description", "Maximum groups to include; use 0 for all groups"));
        properties.put("maxToolsPerGroup", mapOf("type", "integer", "description", "Maximum sample tool names per group; use 0 for counts only"));
        return properties;
    }

    private Map<String, Object> validateInputSchemaProperties() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("toolName", mapOf("type", "string", "description", "Generated API tool name to validate"));
        properties.put(ARGUMENTS_FIELD, mapOf("type", "object", "additionalProperties", true, "description", "Arguments to validate without dispatching HTTP"));
        properties.put("_confirm", mapOf("type", "string", "description", "Optional confirmation token for risky operations"));
        return properties;
    }

    private Map<String, Object> listGroupsInputSchemaProperties() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("maxToolsPerGroup", mapOf("type", "integer", "description", "Maximum sample tool names per group; use 0 for counts only"));
        return properties;
    }

    private Map<String, Object> planWorkflowInputSchemaProperties() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("goal", mapOf("type", "string", "description", "Natural language workflow goal"));
        properties.put("topK", mapOf("type", "integer", "description", "Number of candidate API steps to include"));
        return properties;
    }

    private Map<String, Object> invokeWorkflowInputSchemaProperties() {
        Map<String, Object> stepProperties = new LinkedHashMap<>();
        stepProperties.put("id", mapOf("type", "string", "description", "Optional stable step id for later interpolation"));
        stepProperties.put("toolName", mapOf("type", "string", "description", "Generated API tool name to execute"));
        stepProperties.put(ARGUMENTS_FIELD, mapOf(
                "type", "object",
                "additionalProperties", true,
                "description", "Arguments for the generated API tool. Supports ${stepId:$.json.path} references."
        ));
        stepProperties.put("continueOnError", mapOf("type", "boolean", "description", "Continue workflow after this step fails"));

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("dryRun", mapOf("type", "boolean", "description", "Validate and resolve steps without executing; defaults to true"));
        properties.put("continueOnError", mapOf("type", "boolean", "description", "Continue after failed steps by default"));
        properties.put(STEPS_FIELD, mapOf(
                "type", "array",
                "description", "Sequential API workflow steps",
                "items", mapOf(
                        "type", "object",
                        "properties", stepProperties,
                        "required", List.of("toolName"),
                        "additionalProperties", false
                )
        ));
        return properties;
    }

    private Map<String, Object> invokeByIntentSchemaProperties() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("query", mapOf("type", "string", "description", "Natural language intent"));
        properties.put("topK", mapOf("type", "integer", "description", "Number of candidate tools to evaluate"));
        properties.put(ARGUMENTS_FIELD, mapOf("type", "object", "additionalProperties", true, "description", "Arguments to pass to the selected tool"));
        properties.put("_confirm", mapOf("type", "string", "description", "Optional confirmation token for risky operations"));
        return properties;
    }

    private Map<String, Object> extractDelegatedArguments(Map<String, Object> arguments) {
        Map<String, Object> delegated = new LinkedHashMap<>();
        Object nestedArguments = arguments.get(ARGUMENTS_FIELD);
        if (nestedArguments instanceof Map<?, ?> nestedMap) {
            for (Map.Entry<?, ?> entry : nestedMap.entrySet()) {
                if (entry.getKey() != null) {
                    delegated.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
        }
        if (arguments.containsKey("_confirm") && !delegated.containsKey("_confirm")) {
            delegated.put("_confirm", arguments.get("_confirm"));
        }
        return delegated;
    }

    private String workflowStepId(Map<String, Object> step, int index) {
        String explicitId = asString(step.get("id"));
        if (StringUtils.hasText(explicitId)) {
            return explicitId;
        }
        return "step" + (index + 1);
    }

    private Map<String, Object> stepArguments(Map<String, Object> step) {
        Object rawArguments = step.get(ARGUMENTS_FIELD);
        if (!(rawArguments instanceof Map<?, ?> rawArgumentsMap)) {
            return new LinkedHashMap<>();
        }
        return copyStringKeyMap(rawArgumentsMap);
    }

    private Map<String, Object> resolveWorkflowArguments(
            Map<String, Object> arguments,
            Map<String, Object> workflowContext) {
        Map<String, Object> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : arguments.entrySet()) {
            resolved.put(entry.getKey(), resolveWorkflowValue(entry.getValue(), workflowContext));
        }
        return resolved;
    }

    private Object resolveWorkflowValue(Object value, Map<String, Object> workflowContext) {
        if (value instanceof Map<?, ?> rawMap) {
            Map<String, Object> resolved = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                if (entry.getKey() != null) {
                    resolved.put(String.valueOf(entry.getKey()), resolveWorkflowValue(entry.getValue(), workflowContext));
                }
            }
            return resolved;
        }
        if (value instanceof List<?> rawList) {
            List<Object> resolved = new ArrayList<>();
            for (Object item : rawList) {
                resolved.add(resolveWorkflowValue(item, workflowContext));
            }
            return resolved;
        }
        if (value instanceof String stringValue) {
            return resolveWorkflowTemplate(stringValue, workflowContext);
        }
        return value;
    }

    private WorkflowReferenceValidation validateWorkflowReferences(Object value, Set<String> availableStepIds) {
        List<String> errors = new ArrayList<>();
        List<Map<String, Object>> references = new ArrayList<>();
        collectWorkflowReferences(value, availableStepIds, errors, references);
        return new WorkflowReferenceValidation(errors, references);
    }

    private void collectWorkflowReferences(
            Object value,
            Set<String> availableStepIds,
            List<String> errors,
            List<Map<String, Object>> references) {
        if (value instanceof Map<?, ?> rawMap) {
            for (Object item : rawMap.values()) {
                collectWorkflowReferences(item, availableStepIds, errors, references);
            }
            return;
        }
        if (value instanceof List<?> rawList) {
            for (Object item : rawList) {
                collectWorkflowReferences(item, availableStepIds, errors, references);
            }
            return;
        }
        if (!(value instanceof String stringValue)) {
            return;
        }

        Matcher matcher = WORKFLOW_TEMPLATE.matcher(stringValue);
        while (matcher.find()) {
            String stepId = matcher.group(1);
            String jsonPath = matcher.group(2);
            references.add(mapOf("stepId", stepId, "jsonPath", jsonPath));
            if (!availableStepIds.contains(stepId)) {
                errors.add("Unknown or unavailable workflow step reference: " + stepId);
            }
            try {
                JsonPath.compile(jsonPath);
            }
            catch (Exception ex) {
                errors.add("Invalid workflow JSONPath reference ${" + stepId + ":" + jsonPath + "}: " + ex.getMessage());
            }
        }
    }

    private Map<String, Object> maskWorkflowTemplates(Map<String, Object> arguments) {
        Map<String, Object> masked = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : arguments.entrySet()) {
            masked.put(entry.getKey(), maskWorkflowTemplateValue(entry.getValue()));
        }
        return masked;
    }

    private Object maskWorkflowTemplateValue(Object value) {
        if (value instanceof Map<?, ?> rawMap) {
            Map<String, Object> masked = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                if (entry.getKey() != null) {
                    masked.put(String.valueOf(entry.getKey()), maskWorkflowTemplateValue(entry.getValue()));
                }
            }
            return masked;
        }
        if (value instanceof List<?> rawList) {
            List<Object> masked = new ArrayList<>();
            for (Object item : rawList) {
                masked.add(maskWorkflowTemplateValue(item));
            }
            return masked;
        }
        if (!(value instanceof String stringValue)) {
            return value;
        }
        Matcher matcher = WORKFLOW_TEMPLATE.matcher(stringValue);
        if (matcher.matches()) {
            return "__workflow_ref_" + matcher.group(1);
        }
        StringBuffer masked = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(masked, Matcher.quoteReplacement("__workflow_ref_" + matcher.group(1)));
        }
        matcher.appendTail(masked);
        return masked.toString();
    }

    private Object resolveWorkflowTemplate(String value, Map<String, Object> workflowContext) {
        Matcher exactMatcher = WORKFLOW_TEMPLATE.matcher(value);
        if (exactMatcher.matches()) {
            return readWorkflowContext(exactMatcher.group(1), exactMatcher.group(2), workflowContext);
        }

        Matcher matcher = WORKFLOW_TEMPLATE.matcher(value);
        StringBuffer resolved = new StringBuffer();
        while (matcher.find()) {
            Object replacement = readWorkflowContext(matcher.group(1), matcher.group(2), workflowContext);
            matcher.appendReplacement(resolved, Matcher.quoteReplacement(String.valueOf(replacement)));
        }
        matcher.appendTail(resolved);
        return resolved.toString();
    }

    private Object readWorkflowContext(String stepId, String jsonPath, Map<String, Object> workflowContext) {
        if (!workflowContext.containsKey(stepId)) {
            throw new IllegalArgumentException("Unknown workflow step reference: " + stepId);
        }
        try {
            String json = objectMapper.writeValueAsString(workflowContext.get(stepId));
            return JsonPath.read(json, jsonPath);
        }
        catch (PathNotFoundException ex) {
            throw new IllegalArgumentException("No value matched workflow reference: ${" + stepId + ":" + jsonPath + "}");
        }
        catch (Exception ex) {
            throw new IllegalArgumentException("Invalid workflow reference ${" + stepId + ":" + jsonPath + "}: " + ex.getMessage());
        }
    }

    private Map<String, Object> copyStringKeyMap(Map<?, ?> source) {
        Map<String, Object> copied = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry.getKey() != null) {
                copied.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return copied;
    }

    private McpSchema.CallToolResult successResult(Object structuredContent) {
        String text = toJsonText(structuredContent);
        return McpSchema.CallToolResult.builder()
                .isError(Boolean.FALSE)
                .addTextContent(text)
                .structuredContent(structuredContent)
                .build();
    }

    private McpSchema.CallToolResult errorResult(String message) {
        return McpSchema.CallToolResult.builder()
                .isError(Boolean.TRUE)
                .addTextContent(message)
                .build();
    }

    private String toJsonText(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        }
        catch (Exception ex) {
            return String.valueOf(value);
        }
    }

    private String firstText(McpSchema.CallToolResult result) {
        if (result == null || CollectionUtils.isEmpty(result.content())) {
            return "";
        }
        for (McpSchema.Content content : result.content()) {
            if (content instanceof McpSchema.TextContent textContent) {
                return textContent.text();
            }
        }
        return result.toString();
    }

    private void removeRegisteredTools() {
        for (String toolName : new ArrayList<>(registeredToolNames)) {
            try {
                mcpSyncServer.removeTool(toolName);
            }
            catch (Exception ex) {
                logger.debug("Tool was not registered or already removed: {}", toolName);
            }
        }
        registeredToolNames.clear();
    }

    private Map<String, Object> copyMap(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return new LinkedHashMap<>();
        }
        return new LinkedHashMap<>(source);
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private int asInt(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        }
        catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private boolean asBoolean(Object value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean boolValue) {
            return boolValue;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private Map<String, Object> mapOf(Object... keyValuePairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValuePairs.length; i += 2) {
            map.put(String.valueOf(keyValuePairs[i]), keyValuePairs[i + 1]);
        }
        return map;
    }

    private record ToolCallValidation(
            boolean valid,
            String toolName,
            String operationId,
            boolean risky,
            boolean readOnly,
            boolean idempotent,
            Object requiredArguments,
            List<String> errors,
            Map<String, Object> dispatchPreview) {

        Map<String, Object> toStructuredContent() {
            return toStructuredContent(List.of());
        }

        Map<String, Object> toStructuredContent(List<String> additionalErrors) {
            List<String> mergedErrors = new ArrayList<>(errors);
            mergedErrors.addAll(additionalErrors);
            boolean mergedValid = valid && mergedErrors.isEmpty();
            Map<String, Object> structured = new LinkedHashMap<>();
            structured.put("toolName", toolName);
            structured.put("operationId", operationId);
            structured.put("valid", mergedValid);
            structured.put("wouldExecute", mergedValid);
            structured.put("isError", !mergedValid);
            structured.put("risky", risky);
            structured.put("readOnly", readOnly);
            structured.put("idempotent", idempotent);
            structured.put("requiredArguments", requiredArguments);
            structured.put("errors", mergedErrors);
            structured.put("dispatchPreview", dispatchPreview);
            return structured;
        }
    }

    private record WorkflowReferenceValidation(
            List<String> errors,
            List<Map<String, Object>> references) {
    }
}
