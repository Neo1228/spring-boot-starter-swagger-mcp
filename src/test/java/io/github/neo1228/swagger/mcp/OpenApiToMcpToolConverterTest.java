package io.github.neo1228.swagger.mcp;

import io.modelcontextprotocol.spec.McpSchema;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.NumberSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.math.BigDecimal;
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

    @Test
    void preservesOpenApiSchemaConstraintsForMcpClients() {
        OpenApiToMcpToolConverter converter = new OpenApiToMcpToolConverter();
        SwaggerMcpProperties properties = new SwaggerMcpProperties();

        StringSchema skuSchema = new StringSchema();
        skuSchema.setDescription("Product SKU");
        skuSchema.setMinLength(3);
        skuSchema.setMaxLength(20);
        skuSchema.setPattern("^[A-Z0-9-]+$");
        skuSchema.setExample("SKU-123");
        skuSchema.setDeprecated(true);

        NumberSchema priceSchema = new NumberSchema();
        priceSchema.setMinimum(new BigDecimal("0.01"));
        priceSchema.setMaximum(new BigDecimal("9999.99"));
        priceSchema.setExclusiveMinimum(false);
        priceSchema.setMultipleOf(new BigDecimal("0.01"));
        priceSchema.setReadOnly(true);

        ArraySchema tagsSchema = new ArraySchema();
        tagsSchema.setItems(new StringSchema());
        tagsSchema.setMinItems(1);
        tagsSchema.setMaxItems(5);
        tagsSchema.setUniqueItems(true);

        ObjectSchema bodySchema = new ObjectSchema();
        bodySchema.setMinProperties(1);
        bodySchema.setMaxProperties(10);
        bodySchema.addProperty("sku", skuSchema);
        bodySchema.addProperty("price", priceSchema);
        bodySchema.addProperty("tags", tagsSchema);
        bodySchema.setRequired(List.of("sku"));

        OpenApiOperationDescriptor descriptor = new OpenApiOperationDescriptor(
                "api_create_product",
                "createProduct",
                HttpMethod.POST,
                "/products",
                "Create product",
                List.of("products"),
                List.of(),
                true,
                bodySchema,
                false
        );

        McpSchema.Tool tool = converter.convert(descriptor, properties);

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) tool.inputSchema().properties().get("body");
        assertThat(body).containsEntry("minProperties", 1).containsEntry("maxProperties", 10);

        @SuppressWarnings("unchecked")
        Map<String, Object> fields = (Map<String, Object>) body.get("properties");
        @SuppressWarnings("unchecked")
        Map<String, Object> sku = (Map<String, Object>) fields.get("sku");
        assertThat(sku)
                .containsEntry("minLength", 3)
                .containsEntry("maxLength", 20)
                .containsEntry("pattern", "^[A-Z0-9-]+$")
                .containsEntry("example", "SKU-123")
                .containsEntry("deprecated", true);

        @SuppressWarnings("unchecked")
        Map<String, Object> price = (Map<String, Object>) fields.get("price");
        assertThat(price)
                .containsEntry("minimum", new BigDecimal("0.01"))
                .containsEntry("maximum", new BigDecimal("9999.99"))
                .containsEntry("exclusiveMinimum", false)
                .containsEntry("multipleOf", new BigDecimal("0.01"))
                .containsEntry("readOnly", true);

        @SuppressWarnings("unchecked")
        Map<String, Object> tags = (Map<String, Object>) fields.get("tags");
        assertThat(tags)
                .containsEntry("minItems", 1)
                .containsEntry("maxItems", 5)
                .containsEntry("uniqueItems", true);
    }
}

