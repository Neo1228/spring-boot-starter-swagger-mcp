package io.github.neo1228.swagger.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds consistent MCP tool results, including structured error content for programmatic clients.
 */
class SwaggerMcpToolResults {

    private final ObjectMapper objectMapper;

    SwaggerMcpToolResults(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    McpSchema.CallToolResult success(Object structuredContent) {
        return McpSchema.CallToolResult.builder()
                .isError(Boolean.FALSE)
                .addTextContent(toJsonText(structuredContent))
                .structuredContent(structuredContent)
                .build();
    }

    McpSchema.CallToolResult error(SwaggerMcpToolException exception) {
        return McpSchema.CallToolResult.builder()
                .isError(Boolean.TRUE)
                .addTextContent(exception.getMessage())
                .structuredContent(errorContent(exception))
                .build();
    }

    private Map<String, Object> errorContent(SwaggerMcpToolException exception) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", exception.code().name());
        error.put("message", exception.getMessage());
        error.put("status", exception.status());
        error.put("retryable", retryable(exception.code()));
        if (!exception.details().isEmpty()) {
            error.put("details", exception.details());
        }

        Map<String, Object> structured = new LinkedHashMap<>();
        structured.put("error", error);
        return structured;
    }

    private boolean retryable(SwaggerMcpErrorCode code) {
        return code == SwaggerMcpErrorCode.HTTP_DISPATCH_FAILED
                || code == SwaggerMcpErrorCode.HTTP_DISPATCH_INTERRUPTED;
    }

    private String toJsonText(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        }
        catch (Exception ex) {
            return String.valueOf(value);
        }
    }
}
