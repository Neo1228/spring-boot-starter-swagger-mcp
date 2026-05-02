package io.github.neo1228.swagger.mcp;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SwaggerMcpOperationCatalogTest {

    @Test
    void summarizesOperationsByOpenApiTag() {
        SwaggerMcpOperationCatalog catalog = new SwaggerMcpOperationCatalog();
        catalog.replaceAll(List.of(
                operation("api_get_order", HttpMethod.GET, List.of("orders"), false),
                operation("api_create_order", HttpMethod.POST, List.of("orders"), true),
                operation("api_health", HttpMethod.GET, List.of(), false)
        ));

        SwaggerMcpOperationCatalog.CatalogStats stats = catalog.stats();
        assertThat(stats.operationCount()).isEqualTo(3);
        assertThat(stats.groupCount()).isEqualTo(2);
        assertThat(stats.readOnlyCount()).isEqualTo(2);
        assertThat(stats.riskyCount()).isEqualTo(1);

        assertThat(catalog.summarizeGroups(1))
                .extracting(SwaggerMcpOperationCatalog.GroupSummary::name)
                .containsExactly("default", "orders");
        SwaggerMcpOperationCatalog.GroupSummary orders = catalog.summarizeGroups(2).stream()
                .filter(group -> group.name().equals("orders"))
                .findFirst()
                .orElseThrow();
        assertThat(orders.operationCount()).isEqualTo(2);
        assertThat(orders.sampleTools()).containsExactly("api_get_order", "api_create_order");
    }

    private OpenApiOperationDescriptor operation(String toolName, HttpMethod method, List<String> tags, boolean risky) {
        return new OpenApiOperationDescriptor(
                toolName,
                toolName,
                method,
                "/" + toolName,
                toolName,
                tags,
                List.of(),
                false,
                null,
                risky
        );
    }
}
