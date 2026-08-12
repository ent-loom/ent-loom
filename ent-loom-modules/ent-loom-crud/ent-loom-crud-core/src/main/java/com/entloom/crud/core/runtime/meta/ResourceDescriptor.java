package com.entloom.crud.core.runtime.meta;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Getter;

/**
 * 框架内统一资源身份描述。
 */
@Getter
public final class ResourceDescriptor {
    /** 资源对应的实体类型。 */
    private final Class<?> entityType;
    /** 稳定资源编码。 */
    private final String resourceCode;
    /** 资源归属服务。 */
    private final String ownerService;
    /** 可解析到该资源的别名集合。 */
    private final Set<String> aliases;

    public ResourceDescriptor(
        Class<?> entityType,
        String resourceCode,
        String ownerService,
        Collection<String> aliases
    ) {
        this.entityType = entityType;
        this.resourceCode = requireNonBlank(resourceCode, "resourceCode");
        this.ownerService = normalize(ownerService);
        LinkedHashSet<String> normalizedAliases = new LinkedHashSet<String>();
        if (entityType != null) {
            addAlias(normalizedAliases, entityType.getSimpleName());
            addAlias(normalizedAliases, entityType.getName());
        }
        if (aliases != null) {
            for (String alias : aliases) {
                addAlias(normalizedAliases, alias);
            }
        }
        normalizedAliases.remove(this.resourceCode);
        this.aliases = Collections.unmodifiableSet(normalizedAliases);
    }

    public boolean matches(String code) {
        String normalized = normalize(code);
        return resourceCode.equals(normalized) || aliases.contains(normalized);
    }

    private static String requireNonBlank(String value, String fieldName) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
        return normalized;
    }

    private static void addAlias(Set<String> aliases, String alias) {
        String normalized = normalize(alias);
        if (normalized != null) {
            aliases.add(normalized);
        }
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
