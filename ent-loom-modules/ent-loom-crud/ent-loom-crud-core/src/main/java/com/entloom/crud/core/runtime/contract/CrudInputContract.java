package com.entloom.crud.core.runtime.contract;

import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.ResourceDescriptor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * CRUD 外部输入契约。
 *
 * <p>契约只描述外部请求的稳定边界，不承载实体最终非空、跨字段规则或权限规则。</p>
 */
public final class CrudInputContract {
    private final Map<String, List<String>> createRequiredFields;
    private final Map<String, List<String>> updateForbiddenFields;

    public CrudInputContract(
        Map<String, ? extends List<String>> createRequiredFields,
        Map<String, ? extends List<String>> updateForbiddenFields
    ) {
        this.createRequiredFields = normalize(createRequiredFields);
        this.updateForbiddenFields = normalize(updateForbiddenFields);
    }

    public static CrudInputContract empty() {
        return new CrudInputContract(Collections.<String, List<String>>emptyMap(),
            Collections.<String, List<String>>emptyMap());
    }

    /**
     * 返回创建请求必须显式提供的字段。
     *
     * <p>未配置实体契约时返回空集合。实体结构、数据库非空和最终值校验不自动推导为外部
     * 请求必填，避免把内部持久化约束误加到所有调用方。</p>
     */
    public Set<String> resolveCreateRequiredFields(EntityMeta meta) {
        if (meta == null) {
            return Collections.emptySet();
        }
        List<String> configured = find(createRequiredFields, meta);
        return configured == null
            ? Collections.<String>emptySet()
            : new LinkedHashSet<String>(configured);
    }

    /**
     * 按 DOC/元数据阶段可用的资源身份解析显式创建必填字段。
     *
     * <p>该方法不做实体字段推断；没有匹配到资源契约时返回空集合。</p>
     */
    public Set<String> resolveConfiguredCreateRequiredFields(
        String resourceCode,
        Class<?> entityType,
        Collection<String> aliases
    ) {
        List<String> configured = find(createRequiredFields, resourceCode, entityType, aliases);
        return configured == null
            ? Collections.<String>emptySet()
            : new LinkedHashSet<String>(configured);
    }

    /** 返回更新请求禁止修改的字段。未配置时没有额外禁改字段。 */
    public Set<String> resolveUpdateForbiddenFields(EntityMeta meta) {
        List<String> configured = find(updateForbiddenFields, meta);
        return configured == null
            ? Collections.<String>emptySet()
            : new LinkedHashSet<String>(configured);
    }

    /**
     * 校验更新载荷是否尝试修改契约明确禁止的字段。
     *
     * <p>实体主键在更新请求中只承担目标定位语义，即使它出现在载荷中也不视为修改字段。</p>
     */
    public void validateUpdate(Map<String, ?> values, EntityMeta meta) {
        if (values == null || meta == null) {
            return;
        }
        validateUpdateFields(values.keySet(), meta);
    }

    /** 校验更新请求中实际出现的字段，允许调用方保留显式 null 的出现信息。 */
    public void validateUpdateFields(Set<String> presentFields, EntityMeta meta) {
        if (presentFields == null || meta == null) {
            return;
        }
        for (String fieldName : resolveUpdateForbiddenFields(meta)) {
            if (isUpdateTargetId(fieldName, meta)) {
                continue;
            }
            if (meta.resolveFieldMeta(fieldName) == null) {
                throw new IllegalArgumentException("更新契约字段不存在: " + meta.getEntityName() + "." + fieldName);
            }
            if (presentFields.contains(fieldName)) {
                throw new IllegalArgumentException("禁止修改字段: " + meta.getEntityName() + "." + fieldName);
            }
        }
    }

    private boolean isUpdateTargetId(String fieldName, EntityMeta meta) {
        if (fieldName == null || meta == null) {
            return false;
        }
        String idField = trimToNull(meta.getIdField());
        return fieldName.equals(idField) || "id".equals(fieldName);
    }

    public Map<String, List<String>> getCreateRequiredFields() {
        return createRequiredFields;
    }

    public Map<String, List<String>> getUpdateForbiddenFields() {
        return updateForbiddenFields;
    }

    private List<String> find(Map<String, List<String>> contracts, EntityMeta meta) {
        if (meta == null || meta.getResourceDescriptor() == null) {
            return null;
        }
        ResourceDescriptor descriptor = meta.getResourceDescriptor();
        return find(contracts, descriptor.getResourceCode(), descriptor.getEntityType(), descriptor.getAliases());
    }

    private List<String> find(
        Map<String, List<String>> contracts,
        String resourceCode,
        Class<?> entityType,
        Collection<String> aliases
    ) {
        if (contracts.isEmpty()) {
            return null;
        }
        List<String> exact = contracts.get(resourceCode);
        if (exact != null) {
            return exact;
        }
        if (entityType != null) {
            exact = contracts.get(entityType.getSimpleName());
            if (exact != null) {
                return exact;
            }
            exact = contracts.get(entityType.getName());
            if (exact != null) {
                return exact;
            }
        }
        if (aliases != null) {
            for (String alias : aliases) {
                exact = contracts.get(alias);
                if (exact != null) {
                    return exact;
                }
            }
        }
        for (Map.Entry<String, List<String>> entry : contracts.entrySet()) {
            if (equalsNormalized(resourceCode, entry.getKey())
                || matchesEntityType(entityType, entry.getKey())
                || containsNormalized(aliases, entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private boolean matchesEntityType(Class<?> entityType, String value) {
        return entityType != null
            && (equalsNormalized(entityType.getSimpleName(), value)
                || equalsNormalized(entityType.getName(), value));
    }

    private boolean containsNormalized(Collection<String> values, String target) {
        if (values == null) {
            return false;
        }
        for (String value : values) {
            if (equalsNormalized(value, target)) {
                return true;
            }
        }
        return false;
    }

    private boolean equalsNormalized(String left, String right) {
        String normalizedLeft = trimToNull(left);
        String normalizedRight = trimToNull(right);
        return normalizedLeft == null ? normalizedRight == null : normalizedLeft.equals(normalizedRight);
    }

    private Map<String, List<String>> normalize(Map<String, ? extends List<String>> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyMap();
        }
        LinkedHashMap<String, List<String>> result = new LinkedHashMap<String, List<String>>();
        for (Map.Entry<String, ? extends List<String>> entry : source.entrySet()) {
            String entity = trimToNull(entry.getKey());
            if (entity == null) {
                continue;
            }
            LinkedHashSet<String> fields = new LinkedHashSet<String>();
            if (entry.getValue() != null) {
                for (String field : entry.getValue()) {
                    String normalized = trimToNull(field);
                    if (normalized != null) {
                        fields.add(normalized);
                    }
                }
            }
            result.put(entity, Collections.unmodifiableList(new ArrayList<String>(fields)));
        }
        return Collections.unmodifiableMap(result);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
