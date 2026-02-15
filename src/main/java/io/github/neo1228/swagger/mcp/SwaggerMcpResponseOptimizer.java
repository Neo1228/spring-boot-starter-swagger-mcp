package io.github.neo1228.swagger.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SwaggerMcpResponseOptimizer {

    private final ObjectMapper objectMapper;
    private final SwaggerMcpProperties properties;

    public SwaggerMcpResponseOptimizer(ObjectMapper objectMapper, SwaggerMcpProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public OptimizationResult optimize(String rawResponseBody, Map<String, Object> arguments) {
        String sourceText = rawResponseBody == null ? "" : rawResponseBody;
        Map<String, Object> safeArguments = arguments == null ? Map.of() : arguments;

        Object structuredContent = parseJsonOrNull(sourceText);
        if (structuredContent == null) {
            String trimmed = truncate(sourceText, properties.getResponse().getMaxChars());
            return new OptimizationResult(trimmed, null);
        }

        if (properties.getResponse().isProjectionArgumentEnabled()) {
            String projection = asString(safeArguments.get("_projection"));
            if (StringUtils.hasText(projection)) {
                structuredContent = project(structuredContent, projection);
            }
        }

        boolean summarize = asBoolean(
                safeArguments.get("_summarize"),
                properties.getResponse().isSummarizeByDefault()
                        || sourceText.length() >= properties.getResponse().getSummaryThresholdChars()
        );

        if (summarize) {
            int maxDepth = asInt(safeArguments.get("_maxDepth"), properties.getResponse().getMaxDepth());
            int maxArrayItems = asInt(safeArguments.get("_maxArrayItems"), properties.getResponse().getMaxArrayItems());
            int maxObjectEntries = asInt(safeArguments.get("_maxObjectEntries"), properties.getResponse().getMaxObjectEntries());
            structuredContent = summarize(structuredContent, 0, maxDepth, maxArrayItems, maxObjectEntries);
        }

        String text = toJsonText(structuredContent);
        text = truncate(text, properties.getResponse().getMaxChars());
        return new OptimizationResult(text, structuredContent);
    }

    private Object parseJsonOrNull(String source) {
        try {
            return objectMapper.readValue(source, Object.class);
        }
        catch (Exception ex) {
            return null;
        }
    }

    private Object project(Object source, String expression) {
        try {
            String json = objectMapper.writeValueAsString(source);
            return JsonPath.read(json, expression);
        }
        catch (PathNotFoundException ex) {
            return Map.of("projectionWarning", "No value matched expression", "projection", expression);
        }
        catch (Exception ex) {
            return Map.of("projectionError", ex.getMessage(), "projection", expression);
        }
    }

    @SuppressWarnings("unchecked")
    private Object summarize(Object value, int depth, int maxDepth, int maxArrayItems, int maxObjectEntries) {
        if (value == null) {
            return null;
        }
        if (depth >= maxDepth) {
            return "[truncated-depth]";
        }
        if (value instanceof Map<?, ?> mapValue) {
            Map<String, Object> summarized = new LinkedHashMap<>();
            int count = 0;
            for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
                if (count >= maxObjectEntries) {
                    summarized.put("_truncated", "remaining keys omitted");
                    break;
                }
                summarized.put(String.valueOf(entry.getKey()), summarize(entry.getValue(), depth + 1, maxDepth, maxArrayItems, maxObjectEntries));
                count++;
            }
            return summarized;
        }
        if (value instanceof List<?> listValue) {
            List<Object> summarized = new ArrayList<>();
            int limit = Math.min(maxArrayItems, listValue.size());
            for (int i = 0; i < limit; i++) {
                summarized.add(summarize(listValue.get(i), depth + 1, maxDepth, maxArrayItems, maxObjectEntries));
            }
            if (listValue.size() > limit) {
                summarized.add("[truncated " + (listValue.size() - limit) + " items]");
            }
            return summarized;
        }
        if (value instanceof String stringValue) {
            return truncate(stringValue, properties.getResponse().getTruncateStringsAt());
        }
        return value;
    }

    private String toJsonText(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        }
        catch (JsonProcessingException ex) {
            return String.valueOf(value);
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

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String truncate(String input, int maxChars) {
        if (input == null) {
            return "";
        }
        if (maxChars <= 0 || input.length() <= maxChars) {
            return input;
        }
        return input.substring(0, maxChars) + "...[truncated]";
    }

    public record OptimizationResult(String text, Object structuredContent) {
    }
}

