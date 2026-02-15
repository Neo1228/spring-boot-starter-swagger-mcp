package io.github.neo1228.swagger.mcp;

import io.modelcontextprotocol.spec.McpSchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.NumberSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiToMcpToolConverterTest {

    @Test
    void convertsOpenApiOperationToMcpToolSchema() {
        OpenApiToMcpToolConverter converter = new OpenApiToMcpToolConverter();
        SwaggerMcpProperties properties = new SwaggerMcpProperties();

        ObjectSchema bodySchema = new ObjectSchema();
        bodySchema.addProperty("amount", new NumberSchema());
        bodySchema.setRequired(List.of("amount"));

        OpenApiOperationDescriptor descriptor = new OpenApiOperationDescriptor(
                "api_create_order",
                "createOrder",
                HttpMethod.POST,
                "/orders",
                "Create order",
                List.of("orders"),
                List.of(
                        new OpenApiParameterDescriptor("userId", OpenApiParameterLocation.PATH, true, new StringSchema()),
                        new OpenApiParameterDescriptor("page", OpenApiParameterLocation.QUERY, false, new IntegerSchema())
                ),
                true,
                bodySchema,
                true
        );

        McpSchema.Tool tool = converter.convert(descriptor, properties);

        assertThat(tool.name()).isEqualTo("api_create_order");
        assertThat(tool.description()).contains("POST /orders");
        assertThat(tool.inputSchema().type()).isEqualTo("object");
        assertThat(tool.inputSchema().properties()).containsKeys("userId", "page", "body", "_confirm", "_headers");
        assertThat(tool.inputSchema().required()).contains("userId", "body", "_confirm");

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) tool.inputSchema().properties().get("body");
        assertThat(body.get("type")).isEqualTo("object");
        assertThat(body).containsKey("properties");
    }
}

