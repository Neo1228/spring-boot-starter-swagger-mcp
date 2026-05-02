package io.github.neo1228.swagger.mcp;

import io.swagger.v3.oas.models.media.Schema;
import org.springframework.http.HttpMethod;

import java.util.List;

public record OpenApiOperationDescriptor(
        String toolName,
        String operationId,
        HttpMethod httpMethod,
        String path,
        String description,
        List<String> tags,
        List<OpenApiParameterDescriptor> parameters,
        boolean requestBodyRequired,
        Schema<?> requestBodySchema,
        boolean risky
) {

    public boolean isReadOnly() {
        return HttpMethod.GET.equals(httpMethod)
                || HttpMethod.HEAD.equals(httpMethod)
                || HttpMethod.OPTIONS.equals(httpMethod);
    }

    public boolean isIdempotent() {
        return isReadOnly()
                || HttpMethod.PUT.equals(httpMethod)
                || HttpMethod.DELETE.equals(httpMethod);
    }
}

record OpenApiParameterDescriptor(
        String name,
        OpenApiParameterLocation location,
        boolean required,
        Schema<?> schema
) {
}

enum OpenApiParameterLocation {
    PATH,
    QUERY,
    HEADER,
    COOKIE
}

