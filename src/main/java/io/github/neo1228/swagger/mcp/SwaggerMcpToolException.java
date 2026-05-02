package io.github.neo1228.swagger.mcp;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Common exception type for API-tool execution failures that must be surfaced to MCP clients.
 */
public class SwaggerMcpToolException extends RuntimeException {

    private final SwaggerMcpErrorCode code;
    private final int status;
    private final Map<String, Object> details;

    private SwaggerMcpToolException(
            SwaggerMcpErrorCode code,
            int status,
            String message,
            Map<String, Object> details,
            Throwable cause) {
        super(message, cause);
        this.code = code;
        this.status = status;
        this.details = details == null || details.isEmpty() ? Map.of() : Map.copyOf(details);
    }

    public static SwaggerMcpToolException unknownTool(String toolName) {
        return new SwaggerMcpToolException(
                SwaggerMcpErrorCode.UNKNOWN_TOOL,
                404,
                "Unknown tool: " + toolName,
                detail("toolName", toolName),
                null
        );
    }

    public static SwaggerMcpToolException invalidArgument(String message) {
        return invalidArgument(message, Map.of());
    }

    public static SwaggerMcpToolException invalidArgument(String message, Map<String, Object> details) {
        return new SwaggerMcpToolException(SwaggerMcpErrorCode.INVALID_ARGUMENT, 400, message, details, null);
    }

    public static SwaggerMcpToolException securityDenied(String message) {
        return new SwaggerMcpToolException(SwaggerMcpErrorCode.SECURITY_DENIED, 403, message, Map.of(), null);
    }

    public static SwaggerMcpToolException workflow(String message) {
        return workflow(message, Map.of());
    }

    public static SwaggerMcpToolException workflow(String message, Map<String, Object> details) {
        return new SwaggerMcpToolException(SwaggerMcpErrorCode.WORKFLOW_ERROR, 400, message, details, null);
    }

    public static SwaggerMcpToolException dispatchFailed(String message, Throwable cause) {
        return new SwaggerMcpToolException(SwaggerMcpErrorCode.HTTP_DISPATCH_FAILED, 502, message, Map.of(), cause);
    }

    public static SwaggerMcpToolException dispatchInterrupted(Throwable cause) {
        return new SwaggerMcpToolException(
                SwaggerMcpErrorCode.HTTP_DISPATCH_INTERRUPTED,
                503,
                "HTTP dispatch interrupted",
                Map.of(),
                cause
        );
    }

    public static SwaggerMcpToolException internal(String message, Throwable cause) {
        return new SwaggerMcpToolException(SwaggerMcpErrorCode.INTERNAL_ERROR, 500, message, Map.of(), cause);
    }

    public SwaggerMcpErrorCode code() {
        return code;
    }

    public int status() {
        return status;
    }

    public Map<String, Object> details() {
        return details;
    }

    private static Map<String, Object> detail(String key, Object value) {
        Map<String, Object> details = new LinkedHashMap<>();
        if (value != null) {
            details.put(key, value);
        }
        return details;
    }
}
