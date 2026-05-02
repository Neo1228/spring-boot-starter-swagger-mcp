package io.github.neo1228.swagger.mcp;

/**
 * Stable machine-readable error codes returned in MCP structured error content.
 */
public enum SwaggerMcpErrorCode {
    /** Tool name is not present in the exposed operation catalog. */
    UNKNOWN_TOOL,
    /** Client supplied missing or malformed tool arguments. */
    INVALID_ARGUMENT,
    /** Security policy rejected an otherwise known operation. */
    SECURITY_DENIED,
    /** Workflow definition, validation, or interpolation failed. */
    WORKFLOW_ERROR,
    /** Outbound HTTP dispatch failed before a response was produced. */
    HTTP_DISPATCH_FAILED,
    /** Outbound HTTP dispatch was interrupted. */
    HTTP_DISPATCH_INTERRUPTED,
    /** Unexpected adapter failure not covered by a narrower code. */
    INTERNAL_ERROR
}
