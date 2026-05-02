package io.github.neo1228.swagger.mcp;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public class SwaggerMcpOperationCatalog {

    private static final String UNTAGGED_GROUP = "default";

    private final Map<String, OpenApiOperationDescriptor> operationsByToolName = new ConcurrentHashMap<>();
    private final List<OpenApiOperationDescriptor> operations = new CopyOnWriteArrayList<>();

    public void replaceAll(List<OpenApiOperationDescriptor> newOperations) {
        operationsByToolName.clear();
        operations.clear();
        if (newOperations == null || newOperations.isEmpty()) {
            return;
        }
        for (OpenApiOperationDescriptor operation : newOperations) {
            operationsByToolName.put(operation.toolName(), operation);
            operations.add(operation);
        }
    }

    public Optional<OpenApiOperationDescriptor> findByToolName(String toolName) {
        if (toolName == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(operationsByToolName.get(toolName));
    }

    public List<OpenApiOperationDescriptor> operations() {
        return List.copyOf(operations);
    }

    public List<GroupSummary> summarizeGroups(int maxToolsPerGroup) {
        Map<String, GroupAccumulator> groups = new LinkedHashMap<>();
        for (OpenApiOperationDescriptor operation : operations) {
            List<String> tags = operation.tags() == null || operation.tags().isEmpty()
                    ? List.of(UNTAGGED_GROUP)
                    : operation.tags();
            for (String rawTag : tags) {
                String groupName = normalizeGroup(rawTag);
                GroupAccumulator accumulator = groups.computeIfAbsent(groupName, GroupAccumulator::new);
                accumulator.add(operation);
            }
        }
        return groups.values().stream()
                .map(accumulator -> accumulator.toSummary(maxToolsPerGroup))
                .sorted(Comparator.comparing(GroupSummary::name))
                .collect(Collectors.toList());
    }

    public CatalogStats stats() {
        long riskyCount = operations.stream().filter(OpenApiOperationDescriptor::risky).count();
        long readOnlyCount = operations.stream().filter(OpenApiOperationDescriptor::isReadOnly).count();
        return new CatalogStats(operations.size(), summarizeGroups(0).size(), readOnlyCount, riskyCount);
    }

    private String normalizeGroup(String rawTag) {
        if (rawTag == null || rawTag.isBlank()) {
            return UNTAGGED_GROUP;
        }
        return rawTag.trim().toLowerCase(Locale.ROOT);
    }

    private static class GroupAccumulator {
        private final String name;
        private final Map<String, Long> methods = new LinkedHashMap<>();
        private final List<String> toolNames = new ArrayList<>();
        private int operationCount;
        private int riskyCount;
        private int readOnlyCount;

        GroupAccumulator(String name) {
            this.name = name;
        }

        void add(OpenApiOperationDescriptor operation) {
            operationCount++;
            if (operation.risky()) {
                riskyCount++;
            }
            if (operation.isReadOnly()) {
                readOnlyCount++;
            }
            methods.merge(operation.httpMethod().name(), 1L, Long::sum);
            toolNames.add(operation.toolName());
        }

        GroupSummary toSummary(int maxToolsPerGroup) {
            List<String> sampleTools = maxToolsPerGroup <= 0
                    ? List.of()
                    : toolNames.stream().limit(maxToolsPerGroup).toList();
            return new GroupSummary(name, operationCount, readOnlyCount, riskyCount, Map.copyOf(methods), sampleTools);
        }
    }

    public record GroupSummary(
            String name,
            int operationCount,
            int readOnlyCount,
            int riskyCount,
            Map<String, Long> methods,
            List<String> sampleTools
    ) {
    }

    public record CatalogStats(
            int operationCount,
            int groupCount,
            long readOnlyCount,
            long riskyCount
    ) {
    }
}
