package com.entloom.crud.engine.jdbc.command;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * JDBC 默认 Command 处理器配置。
 */
public class JdbcCrudCommandOptions {
    private boolean ignoreUnchangedNonWritableUpdateFields = false;
    private boolean ignoreNonWritableUpdateFields = false;
    private CreateScopeFieldValidationMode createScopeFieldValidationMode = CreateScopeFieldValidationMode.STRICT_ALL;
    private Set<String> strictCreateScopeFieldResources = new LinkedHashSet<String>();

    public boolean isIgnoreUnchangedNonWritableUpdateFields() {
        return ignoreUnchangedNonWritableUpdateFields;
    }

    public void setIgnoreUnchangedNonWritableUpdateFields(boolean ignoreUnchangedNonWritableUpdateFields) {
        this.ignoreUnchangedNonWritableUpdateFields = ignoreUnchangedNonWritableUpdateFields;
    }

    public boolean isIgnoreNonWritableUpdateFields() {
        return ignoreNonWritableUpdateFields;
    }

    public void setIgnoreNonWritableUpdateFields(boolean ignoreNonWritableUpdateFields) {
        this.ignoreNonWritableUpdateFields = ignoreNonWritableUpdateFields;
    }

    public CreateScopeFieldValidationMode getCreateScopeFieldValidationMode() {
        return createScopeFieldValidationMode;
    }

    public void setCreateScopeFieldValidationMode(CreateScopeFieldValidationMode createScopeFieldValidationMode) {
        this.createScopeFieldValidationMode = createScopeFieldValidationMode == null
            ? CreateScopeFieldValidationMode.STRICT_ALL
            : createScopeFieldValidationMode;
    }

    public Set<String> getStrictCreateScopeFieldResources() {
        return Collections.unmodifiableSet(strictCreateScopeFieldResources);
    }

    public void setStrictCreateScopeFieldResources(Set<String> strictCreateScopeFieldResources) {
        this.strictCreateScopeFieldResources = normalizeResources(strictCreateScopeFieldResources);
    }

    public boolean isStrictCreateScopeFieldResource(String resourceCode, Class<?> entityType) {
        if (createScopeFieldValidationMode == CreateScopeFieldValidationMode.DISABLED) {
            return false;
        }
        if (createScopeFieldValidationMode == CreateScopeFieldValidationMode.STRICT_ALL) {
            return true;
        }
        return matchesResource(resourceCode) || matchesResource(entityType == null ? null : entityType.getSimpleName())
            || matchesResource(entityType == null ? null : entityType.getName());
    }

    private boolean matchesResource(String resource) {
        String normalized = normalize(resource);
        return normalized != null && strictCreateScopeFieldResources.contains(normalized);
    }

    private Set<String> normalizeResources(Set<String> resources) {
        LinkedHashSet<String> result = new LinkedHashSet<String>();
        if (resources == null) {
            return result;
        }
        for (String resource : resources) {
            String normalized = normalize(resource);
            if (normalized != null) {
                result.add(normalized);
            }
        }
        return result;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    public enum CreateScopeFieldValidationMode {
        STRICT_ALL,
        STRICT_RESOURCES,
        DISABLED
    }
}
