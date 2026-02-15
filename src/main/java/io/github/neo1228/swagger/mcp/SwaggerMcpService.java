package io.github.neo1228.swagger.mcp;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.core.util.Json31;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SwaggerMcpService implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger logger = LoggerFactory.getLogger(SwaggerMcpService.class);

    private final OpenApiToMcpToolConverter converter;
    private final SwaggerMcpServerAdapter adapter;
    private final SwaggerMcpSecurityPolicy securityPolicy;
    private final SwaggerMcpProperties properties;
    private final Environment environment;
    private final RestTemplate restTemplate;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public SwaggerMcpService(
            OpenApiToMcpToolConverter converter,
            SwaggerMcpServerAdapter adapter,
            SwaggerMcpSecurityPolicy securityPolicy,
            SwaggerMcpProperties properties,
            Environment environment,
            RestTemplateBuilder restTemplateBuilder) {
        this.converter = converter;
        this.adapter = adapter;
        this.securityPolicy = securityPolicy;
        this.properties = properties;
        this.environment = environment;
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(properties.getExecution().getConnectTimeout())
                .withReadTimeout(properties.getExecution().getReadTimeout());
        this.restTemplate = restTemplateBuilder
                .requestFactorySettings(settings)
                .build();
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (!properties.isEnabled()) {
            logger.info("Swagger MCP bridge is disabled (swagger.mcp.enabled=false)");
            return;
        }
        refresh();
    }

    public synchronized void refresh() {
        OpenAPI openAPI = loadOpenApiViaHttp();
        if (openAPI == null || openAPI.getPaths() == null || openAPI.getPaths().isEmpty()) {
            logger.warn("OpenAPI document is empty. MCP tools were not registered.");
            return;
        }
        List<OpenApiOperationDescriptor> operations = collectOperations(openAPI);
        adapter.registerOperations(operations);
        logger.info("Swagger MCP bridge registered {} candidate API operations", operations.size());
    }

    private OpenAPI loadOpenApiViaHttp() {
        String baseUrl = properties.getExecution().getBaseUrl();
        if (!StringUtils.hasText(baseUrl)) {
            String port = environment.getProperty("local.server.port");
            if (!StringUtils.hasText(port)) {
                port = environment.getProperty("server.port", "8080");
            }
            baseUrl = "http://127.0.0.1:" + port;
        }
        String docsPath = properties.getApiDocsPath();
        if (!StringUtils.hasText(docsPath)) {
            docsPath = "/v3/api-docs";
        }
        if (!docsPath.startsWith("/")) {
            docsPath = "/" + docsPath;
        }
        String url = trimTrailingSlash(baseUrl) + docsPath;
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            if (!response.getStatusCode().is2xxSuccessful() || !StringUtils.hasText(response.getBody())) {
                logger.warn("Unable to load OpenAPI from {} (status={})", url, response.getStatusCode().value());
                return null;
            }
            String body = response.getBody();
            try {
                return Json31.mapper().readValue(body, OpenAPI.class);
            }
            catch (Exception json31Ex) {
                return Json.mapper().readValue(body, OpenAPI.class);
            }
        }
        catch (Exception ex) {
            logger.warn("Failed to fetch OpenAPI document from {}", url, ex);
            return null;
        }
    }

    private List<OpenApiOperationDescriptor> collectOperations(OpenAPI openAPI) {
        List<OpenApiOperationDescriptor> operations = new ArrayList<>();
        Set<String> reservedToolNames = new LinkedHashSet<>();

        for (Map.Entry<String, PathItem> pathEntry : openAPI.getPaths().entrySet()) {
            String path = normalizePath(pathEntry.getKey());
            PathItem pathItem = pathEntry.getValue();
            if (pathItem == null || !isPathIncluded(path)) {
                continue;
            }
            for (Map.Entry<PathItem.HttpMethod, Operation> opEntry : pathItem.readOperationsMap().entrySet()) {
                PathItem.HttpMethod pathMethod = opEntry.getKey();
                Operation operation = opEntry.getValue();
                HttpMethod httpMethod = toHttpMethod(pathMethod);
                if (httpMethod == null || operation == null || !isMethodIncluded(httpMethod)) {
                    continue;
                }
                List<String> tags = operation.getTags() == null ? List.of() : new ArrayList<>(operation.getTags());
                boolean risky = securityPolicy.isRisky(httpMethod, path, tags);
                String operationId = resolveOperationId(httpMethod, path, operation.getOperationId());
                String baseToolName = converter.toToolName(operationId, properties.getToolNamePrefix());
                String toolName = deduplicateToolName(baseToolName, reservedToolNames);
                String description = StringUtils.hasText(operation.getSummary()) ? operation.getSummary() : operation.getDescription();

                List<OpenApiParameterDescriptor> parameters = extractParameters(operation.getParameters());
                Schema<?> requestBodySchema = extractRequestBodySchema(operation.getRequestBody());
                boolean requestBodyRequired = operation.getRequestBody() != null
                        && Boolean.TRUE.equals(operation.getRequestBody().getRequired());

                OpenApiOperationDescriptor descriptor = new OpenApiOperationDescriptor(
                        toolName,
                        operationId,
                        httpMethod,
                        path,
                        description,
                        tags,
                        parameters,
                        requestBodyRequired,
                        requestBodySchema,
                        risky
                );
                operations.add(descriptor);
            }
        }
        return operations;
    }

    private String deduplicateToolName(String baseToolName, Set<String> reservedToolNames) {
        String toolName = baseToolName;
        int suffix = 2;
        while (reservedToolNames.contains(toolName)) {
            toolName = baseToolName + "_" + suffix;
            suffix++;
        }
        reservedToolNames.add(toolName);
        return toolName;
    }

    private List<OpenApiParameterDescriptor> extractParameters(List<Parameter> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return List.of();
        }
        List<OpenApiParameterDescriptor> result = new ArrayList<>();
        for (Parameter parameter : parameters) {
            if (parameter == null || !StringUtils.hasText(parameter.getName())) {
                continue;
            }
            OpenApiParameterLocation location = toLocation(parameter.getIn());
            if (location == null) {
                continue;
            }
            result.add(new OpenApiParameterDescriptor(
                    parameter.getName(),
                    location,
                    Boolean.TRUE.equals(parameter.getRequired()),
                    parameter.getSchema()
            ));
        }
        return result;
    }

    private Schema<?> extractRequestBodySchema(RequestBody requestBody) {
        if (requestBody == null) {
            return null;
        }
        Content content = requestBody.getContent();
        if (content == null || content.isEmpty()) {
            return null;
        }
        MediaType jsonMediaType = content.get("application/json");
        if (jsonMediaType == null) {
            for (Map.Entry<String, MediaType> entry : content.entrySet()) {
                if (entry.getKey() != null && entry.getKey().toLowerCase().contains("json")) {
                    jsonMediaType = entry.getValue();
                    break;
                }
            }
        }
        if (jsonMediaType == null) {
            jsonMediaType = content.values().stream().findFirst().orElse(null);
        }
        return jsonMediaType == null ? null : jsonMediaType.getSchema();
    }

    private String resolveOperationId(HttpMethod method, String path, String operationId) {
        if (StringUtils.hasText(operationId)) {
            return operationId;
        }
        String generated = method.name().toLowerCase() + "_" + path.replace("/", "_").replaceAll("[{}]", "");
        generated = generated.replaceAll("_+", "_").replaceAll("^_+|_+$", "");
        return StringUtils.hasText(generated) ? generated : method.name().toLowerCase() + "_root";
    }

    private HttpMethod toHttpMethod(PathItem.HttpMethod method) {
        if (method == null) {
            return null;
        }
        try {
            return HttpMethod.valueOf(method.name());
        }
        catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private OpenApiParameterLocation toLocation(String rawLocation) {
        if (!StringUtils.hasText(rawLocation)) {
            return null;
        }
        return switch (rawLocation.toLowerCase()) {
            case "path" -> OpenApiParameterLocation.PATH;
            case "query" -> OpenApiParameterLocation.QUERY;
            case "header" -> OpenApiParameterLocation.HEADER;
            case "cookie" -> OpenApiParameterLocation.COOKIE;
            default -> null;
        };
    }

    private boolean isPathIncluded(String path) {
        List<String> includes = properties.getIncludePathPatterns();
        List<String> excludes = properties.getExcludePathPatterns();

        boolean included = includes == null || includes.isEmpty()
                || includes.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
        if (!included) {
            return false;
        }
        return excludes == null || excludes.isEmpty()
                || excludes.stream().noneMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private boolean isMethodIncluded(HttpMethod method) {
        Set<String> includeMethods = properties.getIncludeHttpMethods();
        return includeMethods == null
                || includeMethods.isEmpty()
                || includeMethods.contains(method.name());
    }

    private String normalizePath(String path) {
        if (!StringUtils.hasText(path)) {
            return "/";
        }
        return path.startsWith("/") ? path : "/" + path;
    }

    private String trimTrailingSlash(String value) {
        String trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}

