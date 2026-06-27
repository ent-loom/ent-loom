package com.entloom.crud.core.capability.command.spec;

import com.entloom.crud.api.model.QueryFilter;
import com.entloom.crud.core.exception.ValidationException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 删除目标。
 */
public final class DeleteTarget {
    private final Object id;
    private final List<QueryFilter> targetFilters;
    private final Long expectedVersion;

    public DeleteTarget(Object id, List<QueryFilter> targetFilters, Long expectedVersion) {
        this.id = id;
        this.targetFilters = Collections.unmodifiableList(copyFilters(targetFilters));
        this.expectedVersion = expectedVersion;
    }

    public Object getId() {
        return id;
    }

    public Long getLongId() {
        if (id == null) {
            return null;
        }
        if (id instanceof Number) {
            return Long.valueOf(((Number) id).longValue());
        }
        try {
            return Long.valueOf(String.valueOf(id));
        } catch (NumberFormatException ex) {
            throw new ValidationException("id 无法转换为 Long: " + id);
        }
    }

    public List<QueryFilter> getTargetFilters() {
        return copyFilters(targetFilters);
    }

    public Long getExpectedVersion() {
        return expectedVersion;
    }

    public Object id() {
        return getId();
    }

    public Long longId() {
        return getLongId();
    }

    public List<QueryFilter> targetFilters() {
        return getTargetFilters();
    }

    public Long expectedVersion() {
        return getExpectedVersion();
    }

    private static List<QueryFilter> copyFilters(List<QueryFilter> source) {
        List<QueryFilter> target = new ArrayList<QueryFilter>();
        if (source == null) {
            return target;
        }
        for (QueryFilter filter : source) {
            if (filter == null) {
                target.add(null);
                continue;
            }
            target.add(new QueryFilter(filter.getField(), filter.getOperator(), filter.getValue()));
        }
        return target;
    }
}
