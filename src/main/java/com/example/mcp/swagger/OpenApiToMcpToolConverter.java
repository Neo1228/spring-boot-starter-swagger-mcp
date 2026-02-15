package com.example.mcp.swagger;

import io.modelcontextprotocol.spec.McpSchema;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Schema;
import org.springframework.http.HttpMethod;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class OpenApiToMcpToolConverter {

    private static final Pattern NON_WORD = Pattern.compile("[^a-zA-Z0-9_]");
    private static final Pattern MULTI_UNDERSCORE = Pattern.compile("_+");

    public McpSchema.Tool convert(OpenApiOperationDescriptor operation, SwaggerMcpProperties properties) {
        Map<String, Object> inputProperties = new LinkedHashMap<>();
        Set<String> required = new LinkedHashSet<>();

        for (OpenApiParameterDescriptor parameter : operation.parameters()) {
            Map<String, Object> schema = toJsonSchema(parameter.schema());
            inputProperties.put(parameter.name(), schema);
            if (parameter.required()) {
                required.add(parameter.name());
            }
        }

        if (operation.requestBodySchema() != null) {
            inputProperties.put("body", toJsonSchema(operation.requestBodySchema()));
            if (operation.requestBodyRequired()) {
                required.add("body");
            }
        }

        inputProperties.put("_headers", objectSchema(true, "Optional extra HTTP headers"));
        if (properties.getResponse().isProjectionArgumentEnabled()) {
            inputProperties.put("_projection", stringSchema("Optional JSONPath projection"));
        }
        inputProperties.put("_summarize", booleanSchema("Override response summarization"));
        inputProperties.put("_maxDepth", integerSchema("Override max JSON summary depth"));
        inputProperties.put("_maxArrayItems", integerSchema("Override max summary array items"));
        inputProperties.put("_maxObjectEntries", integerSchema("Override max summary object entries"));

        if (operation.risky() && properties.getSecurity().isRequireConfirmationForRiskyOperations()) {
            inputProperties.put("_confirm", stringSchema(
                    "Confirmation token required for risky operations: " + properties.getSecurity().getConfirmationToken()));
            required.add("_confirm");
        }

        McpSchema.JsonSchema inputSchema = new McpSchema.JsonSchema(
                "object",
                inputProperties,
                new ArrayList<>(required),
                Boolean.FALSE,
                null,
                null
        );

        String title = StringUtils.hasText(operation.operationId()) ? operation.operationId() : operation.toolName();
        String description = buildDescription(operation);
        McpSchema.ToolAnnotations annotations = new McpSchema.ToolAnnotations(
                title,
                isReadOnly(operation.httpMethod()),
                operation.risky(),
                isIdempotent(operation.httpMethod()),
                Boolean.FALSE,
                Boolean.FALSE
        );

        return McpSchema.Tool.builder()
                .name(operation.toolName())
                .title(title)
                .description(description)
                .inputSchema(inputSchema)
                .annotations(annotations)
                .build();
    }

    public String toToolName(String rawName, String prefix) {
        String base = StringUtils.hasText(rawName) ? rawName : "tool";
        String normalized = NON_WORD.matcher(base).replaceAll("_");
        normalized = MULTI_UNDERSCORE.matcher(normalized).replaceAll("_");
        normalized = normalized.replaceAll("^_+|_+$", "").toLowerCase();
        if (!StringUtils.hasText(normalized)) {
            normalized = "tool";
        }
        String safePrefix = StringUtils.hasText(prefix) ? prefix : "";
        return safePrefix + normalized;
    }

    private String buildDescription(OpenApiOperationDescriptor operation) {
        StringBuilder builder = new StringBuilder();
        if (StringUtils.hasText(operation.description())) {
            builder.append(operation.description().trim());
        }
        else {
            builder.append("Execute ").append(operation.httpMethod()).append(" ").append(operation.path());
        }
        builder.append(" [").append(operation.httpMethod()).append(" ").append(operation.path()).append("]");
        if (operation.risky()) {
            builder.append(" (risky operation: confirmation may be required)");
        }
        return builder.toString();
    }

    private boolean isReadOnly(HttpMethod method) {
        return HttpMethod.GET.equals(method) || HttpMethod.HEAD.equals(method) || HttpMethod.OPTIONS.equals(method);
    }

    private boolean isIdempotent(HttpMethod method) {
        return HttpMethod.GET.equals(method)
                || HttpMethod.HEAD.equals(method)
                || HttpMethod.PUT.equals(method)
                || HttpMethod.DELETE.equals(method)
                || HttpMethod.OPTIONS.equals(method);
    }

    private Map<String, Object> toJsonSchema(Schema<?> schema) {
        if (schema == null) {
            return stringSchema("string");
        }
        Map<String, Object> jsonSchema = new LinkedHashMap<>();
        if (StringUtils.hasText(schema.get$ref())) {
            jsonSchema.put("$ref", schema.get$ref());
        }
        if (StringUtils.hasText(schema.getType())) {
            jsonSchema.put("type", schema.getType());
        }
        if (StringUtils.hasText(schema.getFormat())) {
            jsonSchema.put("format", schema.getFormat());
        }
        if (StringUtils.hasText(schema.getDescription())) {
            jsonSchema.put("description", schema.getDescription());
        }
        if (schema.getDefault() != null) {
            jsonSchema.put("default", schema.getDefault());
        }
        if (schema.getEnum() != null && !schema.getEnum().isEmpty()) {
            jsonSchema.put("enum", new ArrayList<>(schema.getEnum()));
        }
        if (schema.getNullable() != null) {
            jsonSchema.put("nullable", schema.getNullable());
        }

        if (schema.getProperties() != null && !schema.getProperties().isEmpty()) {
            Map<String, Object> properties = new LinkedHashMap<>();
            for (Map.Entry<String, Schema> entry : ((Map<String, Schema>) schema.getProperties()).entrySet()) {
                properties.put(entry.getKey(), toJsonSchema(entry.getValue()));
            }
            jsonSchema.put("properties", properties);
        }
        if (schema.getRequired() != null && !schema.getRequired().isEmpty()) {
            jsonSchema.put("required", new ArrayList<>(schema.getRequired()));
        }

        if (schema instanceof ArraySchema arraySchema) {
            jsonSchema.put("type", "array");
            jsonSchema.put("items", toJsonSchema(arraySchema.getItems()));
        }
        else if (schema.getItems() != null) {
            jsonSchema.put("items", toJsonSchema(schema.getItems()));
        }

        Object additionalProperties = schema.getAdditionalProperties();
        if (additionalProperties instanceof Boolean boolValue) {
            jsonSchema.put("additionalProperties", boolValue);
        }
        else if (additionalProperties instanceof Schema<?> additionalSchema) {
            jsonSchema.put("additionalProperties", toJsonSchema(additionalSchema));
        }

        if (schema instanceof ComposedSchema composed) {
            addComposed("allOf", composed.getAllOf(), jsonSchema);
            addComposed("anyOf", composed.getAnyOf(), jsonSchema);
            addComposed("oneOf", composed.getOneOf(), jsonSchema);
        }

        if (!jsonSchema.containsKey("type")) {
            if (jsonSchema.containsKey("properties")) {
                jsonSchema.put("type", "object");
            }
            else if (jsonSchema.containsKey("items")) {
                jsonSchema.put("type", "array");
            }
            else {
                jsonSchema.put("type", "string");
            }
        }
        return jsonSchema;
    }

    private void addComposed(String key, Collection<Schema> source, Map<String, Object> target) {
        if (source == null || source.isEmpty()) {
            return;
        }
        List<Map<String, Object>> converted = new ArrayList<>();
        for (Schema<?> schema : source) {
            converted.add(toJsonSchema(schema));
        }
        target.put(key, converted);
    }

    private Map<String, Object> stringSchema(String description) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "string");
        if (StringUtils.hasText(description)) {
            schema.put("description", description);
        }
        return schema;
    }

    private Map<String, Object> booleanSchema(String description) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "boolean");
        schema.put("description", description);
        return schema;
    }

    private Map<String, Object> integerSchema(String description) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "integer");
        schema.put("description", description);
        return schema;
    }

    private Map<String, Object> objectSchema(boolean additionalProperties, String description) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", additionalProperties);
        schema.put("description", description);
        return schema;
    }
}
