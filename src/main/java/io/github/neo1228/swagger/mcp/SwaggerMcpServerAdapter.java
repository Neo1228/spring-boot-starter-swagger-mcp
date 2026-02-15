package io.github.neo1228.swagger.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class SwaggerMcpServerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(SwaggerMcpServerAdapter.class);
    private static final String ARGUMENTS_FIELD = "arguments";

    private final McpSyncServer mcpSyncServer;
    private final OpenApiToMcpToolConverter converter;
    private final SwaggerMcpToolSelector toolSelector;
    private final SwaggerMcpResponseOptimizer responseOptimizer;
    private final SwaggerMcpSecurityPolicy securityPolicy;
    private final SwaggerMcpProperties properties;
    private final Environment environment;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final Map<String, OpenApiOperationDescriptor> operationsByToolName = new ConcurrentHashMap<>();
    private final Set<String> registeredToolNames = ConcurrentHashMap.newKeySet();
    private final String discoverToolName;
    private final String invokeByIntentToolName;

    public SwaggerMcpServerAdapter(
            McpSyncServer mcpSyncServer,
            OpenApiToMcpToolConverter converter,
            SwaggerMcpToolSelector toolSelector,
            SwaggerMcpResponseOptimizer responseOptimizer,
            SwaggerMcpSecurityPolicy securityPolicy,
            SwaggerMcpProperties properties,
            Environment environment,
            RestTemplateBuilder restTemplateBuilder,
            ObjectMapper objectMapper) {
        this.mcpSyncServer = mcpSyncServer;
        this.converter = converter;
        this.toolSelector = toolSelector;
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
        this.invokeByIntentToolName = converter.toToolName("meta_invoke_api_by_intent", properties.getToolNamePrefix());
    }

    public synchronized void registerOperations(List<OpenApiOperationDescriptor> operations) {
        removeRegisteredTools();
        operationsByToolName.clear();

        List<OpenApiOperationDescriptor> eligibleOperations = new ArrayList<>();
        for (OpenApiOperationDescriptor operation : operations) {
            if (!securityPolicy.shouldExpose(operation)) {
                continue;
            }
            eligibleOperations.add(operation);
            operationsByToolName.put(operation.toolName(), operation);
        }
        toolSelector.setCandidates(eligibleOperations);

        Set<String> existingToolNames = new LinkedHashSet<>();
        for (McpSchema.Tool tool : mcpSyncServer.listTools()) {
            existingToolNames.add(tool.name());
        }

        SwaggerMcpProperties.SmartContext smartContext = properties.getSmartContext();
        if (smartContext.isEnabled() && smartContext.isGatewayToolEnabled()) {
            registerDiscoverTool(existingToolNames);
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
        OpenApiOperationDescriptor operation = operationsByToolName.get(toolName);
        if (operation == null) {
            return errorResult("Unknown tool: " + toolName);
        }
        Map<String, Object> safeArguments = copyMap(arguments);
        securityPolicy.auditStart(operation, safeArguments);
        try {
            var validationResult = securityPolicy.validateExecution(operation, safeArguments);
            if (validationResult.isPresent()) {
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
        registeredToolNames.add(discoverToolName);
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
        registeredToolNames.add(invokeByIntentToolName);
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
            headers.set(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
        }
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

    private Map<String, Object> discoverInputSchemaProperties() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("query", mapOf("type", "string", "description", "Natural language intent"));
        properties.put("topK", mapOf("type", "integer", "description", "Number of tools to return"));
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

    private Map<String, Object> mapOf(Object... keyValuePairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValuePairs.length; i += 2) {
            map.put(String.valueOf(keyValuePairs[i]), keyValuePairs[i + 1]);
        }
        return map;
    }
}

