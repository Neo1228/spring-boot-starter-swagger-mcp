package io.github.neo1228.swagger.mcp;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ConfigurationProperties(prefix = "swagger.mcp")
public class SwaggerMcpProperties {

    private boolean enabled = true;
    private String apiDocsPath = "/v3/api-docs";
    private String toolNamePrefix = "api_";
    private List<String> includePathPatterns = new ArrayList<>(List.of("/**"));
    private List<String> excludePathPatterns = new ArrayList<>(List.of(
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/mcp/**",
            "/sse/**",
            "/error",
            "/actuator/**"
    ));
    private Set<String> includeHttpMethods = new LinkedHashSet<>(Set.of(
            "GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS"
    ));
    private final Execution execution = new Execution();
    private final SmartContext smartContext = new SmartContext();
    private final Response response = new Response();
    private final Security security = new Security();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getToolNamePrefix() {
        return toolNamePrefix;
    }

    public void setToolNamePrefix(String toolNamePrefix) {
        this.toolNamePrefix = toolNamePrefix;
    }

    public String getApiDocsPath() {
        return apiDocsPath;
    }

    public void setApiDocsPath(String apiDocsPath) {
        this.apiDocsPath = apiDocsPath;
    }

    public List<String> getIncludePathPatterns() {
        return includePathPatterns;
    }

    public void setIncludePathPatterns(List<String> includePathPatterns) {
        this.includePathPatterns = includePathPatterns;
    }

    public List<String> getExcludePathPatterns() {
        return excludePathPatterns;
    }

    public void setExcludePathPatterns(List<String> excludePathPatterns) {
        this.excludePathPatterns = excludePathPatterns;
    }

    public Set<String> getIncludeHttpMethods() {
        return includeHttpMethods;
    }

    public void setIncludeHttpMethods(Set<String> includeHttpMethods) {
        this.includeHttpMethods = includeHttpMethods;
    }

    public Execution getExecution() {
        return execution;
    }

    public SmartContext getSmartContext() {
        return smartContext;
    }

    public Response getResponse() {
        return response;
    }

    public Security getSecurity() {
        return security;
    }

    public static class Execution {
        private String baseUrl = "";
        private Duration connectTimeout = Duration.ofSeconds(3);
        private Duration readTimeout = Duration.ofSeconds(30);
        private boolean copyIncomingAuthorizationHeader = true;
        private boolean copyIncomingCookieHeader = false;
        private Map<String, String> defaultHeaders = new LinkedHashMap<>();

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public Duration getConnectTimeout() {
            return connectTimeout;
        }

        public void setConnectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        public Duration getReadTimeout() {
            return readTimeout;
        }

        public void setReadTimeout(Duration readTimeout) {
            this.readTimeout = readTimeout;
        }

        public boolean isCopyIncomingAuthorizationHeader() {
            return copyIncomingAuthorizationHeader;
        }

        public void setCopyIncomingAuthorizationHeader(boolean copyIncomingAuthorizationHeader) {
            this.copyIncomingAuthorizationHeader = copyIncomingAuthorizationHeader;
        }

        public boolean isCopyIncomingCookieHeader() {
            return copyIncomingCookieHeader;
        }

        public void setCopyIncomingCookieHeader(boolean copyIncomingCookieHeader) {
            this.copyIncomingCookieHeader = copyIncomingCookieHeader;
        }

        public Map<String, String> getDefaultHeaders() {
            return defaultHeaders;
        }

        public void setDefaultHeaders(Map<String, String> defaultHeaders) {
            this.defaultHeaders = defaultHeaders;
        }
    }

    public static class SmartContext {
        private boolean enabled = true;
        private boolean gatewayToolEnabled = true;
        private boolean gatewayOnly = false;
        private int defaultTopK = 8;
        private double minScore = 0.08d;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isGatewayToolEnabled() {
            return gatewayToolEnabled;
        }

        public void setGatewayToolEnabled(boolean gatewayToolEnabled) {
            this.gatewayToolEnabled = gatewayToolEnabled;
        }

        public boolean isGatewayOnly() {
            return gatewayOnly;
        }

        public void setGatewayOnly(boolean gatewayOnly) {
            this.gatewayOnly = gatewayOnly;
        }

        public int getDefaultTopK() {
            return defaultTopK;
        }

        public void setDefaultTopK(int defaultTopK) {
            this.defaultTopK = defaultTopK;
        }

        public double getMinScore() {
            return minScore;
        }

        public void setMinScore(double minScore) {
            this.minScore = minScore;
        }
    }

    public static class Response {
        private int maxChars = 8000;
        private int summaryThresholdChars = 4000;
        private int maxDepth = 4;
        private int maxObjectEntries = 20;
        private int maxArrayItems = 20;
        private int truncateStringsAt = 1024;
        private boolean projectionArgumentEnabled = true;
        private boolean summarizeByDefault = false;

        public int getMaxChars() {
            return maxChars;
        }

        public void setMaxChars(int maxChars) {
            this.maxChars = maxChars;
        }

        public int getSummaryThresholdChars() {
            return summaryThresholdChars;
        }

        public void setSummaryThresholdChars(int summaryThresholdChars) {
            this.summaryThresholdChars = summaryThresholdChars;
        }

        public int getMaxDepth() {
            return maxDepth;
        }

        public void setMaxDepth(int maxDepth) {
            this.maxDepth = maxDepth;
        }

        public int getMaxObjectEntries() {
            return maxObjectEntries;
        }

        public void setMaxObjectEntries(int maxObjectEntries) {
            this.maxObjectEntries = maxObjectEntries;
        }

        public int getMaxArrayItems() {
            return maxArrayItems;
        }

        public void setMaxArrayItems(int maxArrayItems) {
            this.maxArrayItems = maxArrayItems;
        }

        public int getTruncateStringsAt() {
            return truncateStringsAt;
        }

        public void setTruncateStringsAt(int truncateStringsAt) {
            this.truncateStringsAt = truncateStringsAt;
        }

        public boolean isProjectionArgumentEnabled() {
            return projectionArgumentEnabled;
        }

        public void setProjectionArgumentEnabled(boolean projectionArgumentEnabled) {
            this.projectionArgumentEnabled = projectionArgumentEnabled;
        }

        public boolean isSummarizeByDefault() {
            return summarizeByDefault;
        }

        public void setSummarizeByDefault(boolean summarizeByDefault) {
            this.summarizeByDefault = summarizeByDefault;
        }
    }

    public static class Security {
        private boolean auditLogEnabled = true;
        private boolean exposeRiskyTools = true;
        private boolean requireConfirmationForRiskyOperations = true;
        private String confirmationToken = "CONFIRM";
        private Set<String> riskyHttpMethods = new LinkedHashSet<>(Set.of("POST", "PUT", "PATCH", "DELETE"));
        private List<String> riskyPathPatterns = new ArrayList<>();
        private List<String> blockedPathPatterns = new ArrayList<>();
        private List<String> roleProtectedPathPatterns = new ArrayList<>();
        private Set<String> requiredAnyRole = new LinkedHashSet<>();

        public boolean isAuditLogEnabled() {
            return auditLogEnabled;
        }

        public void setAuditLogEnabled(boolean auditLogEnabled) {
            this.auditLogEnabled = auditLogEnabled;
        }

        public boolean isExposeRiskyTools() {
            return exposeRiskyTools;
        }

        public void setExposeRiskyTools(boolean exposeRiskyTools) {
            this.exposeRiskyTools = exposeRiskyTools;
        }

        public boolean isRequireConfirmationForRiskyOperations() {
            return requireConfirmationForRiskyOperations;
        }

        public void setRequireConfirmationForRiskyOperations(boolean requireConfirmationForRiskyOperations) {
            this.requireConfirmationForRiskyOperations = requireConfirmationForRiskyOperations;
        }

        public String getConfirmationToken() {
            return confirmationToken;
        }

        public void setConfirmationToken(String confirmationToken) {
            this.confirmationToken = confirmationToken;
        }

        public Set<String> getRiskyHttpMethods() {
            return riskyHttpMethods;
        }

        public void setRiskyHttpMethods(Set<String> riskyHttpMethods) {
            this.riskyHttpMethods = riskyHttpMethods;
        }

        public List<String> getRiskyPathPatterns() {
            return riskyPathPatterns;
        }

        public void setRiskyPathPatterns(List<String> riskyPathPatterns) {
            this.riskyPathPatterns = riskyPathPatterns;
        }

        public List<String> getBlockedPathPatterns() {
            return blockedPathPatterns;
        }

        public void setBlockedPathPatterns(List<String> blockedPathPatterns) {
            this.blockedPathPatterns = blockedPathPatterns;
        }

        public List<String> getRoleProtectedPathPatterns() {
            return roleProtectedPathPatterns;
        }

        public void setRoleProtectedPathPatterns(List<String> roleProtectedPathPatterns) {
            this.roleProtectedPathPatterns = roleProtectedPathPatterns;
        }

        public Set<String> getRequiredAnyRole() {
            return requiredAnyRole;
        }

        public void setRequiredAnyRole(Set<String> requiredAnyRole) {
            this.requiredAnyRole = requiredAnyRole;
        }
    }
}

