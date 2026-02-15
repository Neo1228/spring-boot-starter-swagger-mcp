package com.example.mcp.swagger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SwaggerMcpToolSelector {

    private static final Pattern SPLIT_PATTERN = Pattern.compile("[^a-zA-Z0-9]+");
    private final List<Candidate> candidates = new CopyOnWriteArrayList<>();

    public void setCandidates(List<OpenApiOperationDescriptor> operations) {
        List<Candidate> newCandidates = new ArrayList<>();
        for (OpenApiOperationDescriptor operation : operations) {
            String searchText = buildSearchText(operation);
            newCandidates.add(new Candidate(operation, searchText, tokenize(searchText)));
        }
        candidates.clear();
        candidates.addAll(newCandidates);
    }

    public List<ScoredTool> select(String query, int topK) {
        Set<String> queryTokens = tokenize(query);
        String normalizedQuery = normalize(query);
        if (queryTokens.isEmpty() && normalizedQuery.isEmpty()) {
            return List.of();
        }
        return candidates.stream()
                .map(candidate -> new ScoredTool(candidate.operation(), score(queryTokens, normalizedQuery, candidate)))
                .filter(scored -> scored.score() > 0d)
                .sorted(Comparator.comparingDouble(ScoredTool::score).reversed())
                .limit(Math.max(1, topK))
                .collect(Collectors.toList());
    }

    private double score(Set<String> queryTokens, String normalizedQuery, Candidate candidate) {
        if (candidate.tokens().isEmpty()) {
            return 0d;
        }
        long overlap = queryTokens.stream().filter(candidate.tokens()::contains).count();
        double overlapScore = queryTokens.isEmpty() ? 0d : (double) overlap / (double) queryTokens.size();
        double coverageScore = (double) overlap / (double) candidate.tokens().size();
        double containsScore = normalizedQuery.isEmpty() ? 0d : (candidate.searchText().contains(normalizedQuery) ? 0.45d : 0d);
        return overlapScore * 0.65d + coverageScore * 0.2d + containsScore;
    }

    private String buildSearchText(OpenApiOperationDescriptor operation) {
        StringBuilder builder = new StringBuilder();
        builder.append(operation.toolName()).append(' ');
        builder.append(operation.operationId()).append(' ');
        builder.append(operation.httpMethod()).append(' ');
        builder.append(operation.path()).append(' ');
        if (operation.description() != null) {
            builder.append(operation.description()).append(' ');
        }
        if (operation.tags() != null) {
            operation.tags().forEach(tag -> builder.append(tag).append(' '));
        }
        return normalize(builder.toString());
    }

    private Set<String> tokenize(String value) {
        String normalized = normalize(value);
        if (normalized.isEmpty()) {
            return Set.of();
        }
        String[] parts = SPLIT_PATTERN.split(normalized);
        Set<String> tokens = new LinkedHashSet<>();
        for (String part : parts) {
            if (!part.isBlank()) {
                tokens.add(part);
            }
        }
        return tokens;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
    }

    private record Candidate(OpenApiOperationDescriptor operation, String searchText, Set<String> tokens) {
    }

    public record ScoredTool(OpenApiOperationDescriptor operation, double score) {
    }
}
