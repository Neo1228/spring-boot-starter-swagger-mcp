package io.github.neo1228.swagger.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class SwaggerMcpSecurityPolicy {

    private static final Logger logger = LoggerFactory.getLogger(SwaggerMcpSecurityPolicy.class);
    private final SwaggerMcpProperties properties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public SwaggerMcpSecurityPolicy(SwaggerMcpProperties properties) {
        this.properties = properties;
    }

    public boolean isRisky(HttpMethod method, String path, Collection<String> tags) {
        SwaggerMcpProperties.Security security = properties.getSecurity();
        boolean riskyMethod = security.getRiskyHttpMethods().contains(method.name());
        boolean riskyPath = security.getRiskyPathPatterns().stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
        boolean riskyTag = tags != null && tags.stream()
                .filter(StringUtils::hasText)
                .map(tag -> tag.toLowerCase(Locale.ROOT))
                .anyMatch(tag -> tag.contains("delete") || tag.contains("payment") || tag.contains("admin"));
        return riskyMethod || riskyPath || riskyTag;
    }

    public boolean isBlocked(OpenApiOperationDescriptor operation) {
        return properties.getSecurity().getBlockedPathPatterns().stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, operation.path()));
    }

    public boolean shouldExpose(OpenApiOperationDescriptor operation) {
        if (isBlocked(operation)) {
            return false;
        }
        if (!operation.risky()) {
            return true;
        }
        return properties.getSecurity().isExposeRiskyTools();
    }

    public Optional<String> validateExecution(OpenApiOperationDescriptor operation, Map<String, Object> arguments) {
        if (isBlocked(operation)) {
            return Optional.of("Blocked operation: " + operation.path());
        }

        SwaggerMcpProperties.Security security = properties.getSecurity();
        if (operation.risky() && security.isRequireConfirmationForRiskyOperations()) {
            String confirmation = arguments == null ? null : asString(arguments.get("_confirm"));
            if (!StringUtils.hasText(confirmation) || !confirmation.equals(security.getConfirmationToken())) {
                return Optional.of("Confirmation is required. Provide _confirm=\"" + security.getConfirmationToken() + "\"");
            }
        }

        if (shouldCheckRoles(operation) && !hasAnyRequiredRole(security.getRequiredAnyRole())) {
            return Optional.of("Forbidden: required role(s) " + security.getRequiredAnyRole());
        }
        return Optional.empty();
    }

    public void auditStart(OpenApiOperationDescriptor operation, Map<String, Object> arguments) {
        if (!properties.getSecurity().isAuditLogEnabled()) {
            return;
        }
        logger.info("MCP tool execution started: tool={}, method={}, path={}, argKeys={}",
                operation.toolName(), operation.httpMethod(), operation.path(), argumentKeys(arguments));
    }

    public void auditEnd(OpenApiOperationDescriptor operation, boolean success, int statusCode) {
        if (!properties.getSecurity().isAuditLogEnabled()) {
            return;
        }
        logger.info("MCP tool execution finished: tool={}, success={}, status={}",
                operation.toolName(), success, statusCode);
    }

    private boolean shouldCheckRoles(OpenApiOperationDescriptor operation) {
        SwaggerMcpProperties.Security security = properties.getSecurity();
        if (security.getRequiredAnyRole().isEmpty()) {
            return false;
        }
        if (operation.risky()) {
            return true;
        }
        return security.getRoleProtectedPathPatterns().stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, operation.path()));
    }

    private boolean hasAnyRequiredRole(Set<String> requiredRoles) {
        if (requiredRoles == null || requiredRoles.isEmpty()) {
            return true;
        }
        Set<String> currentAuthorities = getCurrentAuthorities();
        if (currentAuthorities.isEmpty()) {
            return false;
        }
        return requiredRoles.stream().anyMatch(currentAuthorities::contains);
    }

    @SuppressWarnings("unchecked")
    private Set<String> getCurrentAuthorities() {
        try {
            Class<?> holderClass = Class.forName("org.springframework.security.core.context.SecurityContextHolder");
            Method getContext = holderClass.getMethod("getContext");
            Object context = getContext.invoke(null);
            if (context == null) {
                return Collections.emptySet();
            }

            Method getAuthentication = context.getClass().getMethod("getAuthentication");
            Object authentication = getAuthentication.invoke(context);
            if (authentication == null) {
                return Collections.emptySet();
            }

            Method getAuthorities = authentication.getClass().getMethod("getAuthorities");
            Object authoritiesObj = getAuthorities.invoke(authentication);
            if (!(authoritiesObj instanceof Collection<?> authorities)) {
                return Collections.emptySet();
            }

            Set<String> authoritySet = new LinkedHashSet<>();
            for (Object authority : authorities) {
                if (authority == null) {
                    continue;
                }
                Method getAuthority = authority.getClass().getMethod("getAuthority");
                Object value = getAuthority.invoke(authority);
                if (value != null) {
                    authoritySet.add(String.valueOf(value));
                }
            }
            return authoritySet;
        }
        catch (ClassNotFoundException ex) {
            return Collections.emptySet();
        }
        catch (Exception ex) {
            logger.debug("Failed to resolve Spring Security authorities", ex);
            return Collections.emptySet();
        }
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Set<String> argumentKeys(Map<String, Object> arguments) {
        return arguments == null ? Set.of() : arguments.keySet();
    }
}

