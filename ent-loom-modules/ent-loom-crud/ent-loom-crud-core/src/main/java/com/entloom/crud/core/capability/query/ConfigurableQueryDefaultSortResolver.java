package com.entloom.crud.core.capability.query;

import com.entloom.crud.api.enums.QueryOperation;
import com.entloom.crud.api.enums.SortDirection;
import com.entloom.crud.api.enums.SortTarget;
import com.entloom.crud.api.model.QuerySort;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 基于统一创建时间角色及全局方向配置的默认排序解析器。
 */
public class ConfigurableQueryDefaultSortResolver implements QueryDefaultSortResolver {
    /** 是否启用。 */
    private final boolean enabled;
    /** 生效操作集合。 */
    private final Set<QueryOperation> applyTo;
    /** 创建时间排序方向。 */
    private final SortDirection timeDirection;
    /** 是否追加主键排序。 */
    private final boolean appendId;
    /** 主键排序方向。 */
    private final SortDirection idDirection;
    /** 无时间字段时是否回退主键排序。 */
    private final boolean fallbackToId;

    public ConfigurableQueryDefaultSortResolver(
        boolean enabled,
        Set<QueryOperation> applyTo,
        SortDirection timeDirection,
        boolean appendId,
        SortDirection idDirection,
        boolean fallbackToId
    ) {
        if (timeDirection == null) {
            throw new IllegalArgumentException("默认排序 timeDirection 不能为空");
        }
        if (idDirection == null) {
            throw new IllegalArgumentException("默认排序 idDirection 不能为空");
        }
        this.enabled = enabled;
        this.applyTo = copyApplyTo(applyTo);
        this.timeDirection = timeDirection;
        this.appendId = appendId;
        this.idDirection = idDirection;
        this.fallbackToId = fallbackToId;
    }

    @Override
    public List<QuerySort> resolve(QuerySpec<?> spec, EntityMeta rootMeta) {
        if (!enabled || spec == null || rootMeta == null) {
            return Collections.emptyList();
        }
        if (spec.getOp() == null || !applyTo.contains(spec.getOp())) {
            return Collections.emptyList();
        }
        if (!spec.getSorts().isEmpty()) {
            return Collections.emptyList();
        }

        List<QuerySort> result = new ArrayList<QuerySort>();
        String timeField = rootMeta.getCreatedTimeField();
        if (isAvailableField(rootMeta, timeField) && rootMeta.resolveFieldMeta(timeField).isSortable()) {
            result.add(new QuerySort(timeField, timeDirection, SortTarget.FIELD));
            if (appendId) {
                addIdSortIfAvailable(result, rootMeta);
            }
            return result;
        }

        if (fallbackToId) {
            addIdSortIfAvailable(result, rootMeta);
        }
        return result;
    }

    private void addIdSortIfAvailable(List<QuerySort> result, EntityMeta rootMeta) {
        String idField = trimToNull(rootMeta.getIdField());
        if (!isAvailableField(rootMeta, idField) || containsField(result, idField)) {
            return;
        }
        result.add(new QuerySort(idField, idDirection, SortTarget.FIELD));
    }

    private boolean isAvailableField(EntityMeta rootMeta, String field) {
        String normalized = trimToNull(field);
        return normalized != null
            && rootMeta.getAllowedFields().contains(normalized)
            && rootMeta.resolveColumn(normalized) != null;
    }

    private boolean containsField(List<QuerySort> sorts, String field) {
        for (QuerySort sort : sorts) {
            if (sort != null && field.equals(sort.getField())) {
                return true;
            }
        }
        return false;
    }

    private static Set<QueryOperation> copyApplyTo(Set<QueryOperation> source) {
        if (source == null || source.isEmpty()) {
            return EnumSet.noneOf(QueryOperation.class);
        }
        return EnumSet.copyOf(source);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
