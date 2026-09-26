package com.entloom.crud.core.governance.service;

import com.entloom.crud.core.exception.DataScopeDeniedException;
import com.entloom.crud.core.governance.scope.CrudDataScope;
import com.entloom.crud.core.governance.scope.CrudReadScope;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 数据范围交集计算器。
 */
public final class CrudScopeIntersectionService {

    /**
     * 计算治理范围与业务约束范围的交集。
     */
    public CrudDataScope intersect(CrudDataScope governanceScope, CrudDataScope businessScopeConstraint) {
        if (businessScopeConstraint == null) {
            return governanceScope;
        }
        if (businessScopeConstraint.isExplicitAll() && businessScopeConstraint.getDimensions().isEmpty()
            && businessScopeConstraint.getReadScopes().isEmpty()) return governanceScope;
        if (governanceScope == null) {
            throw new DataScopeDeniedException("治理范围不能为空");
        }
        if (governanceScope.isExplicitAll() && governanceScope.getReadScopes().isEmpty()) return businessScopeConstraint;
        Map<String, CrudReadScope> readScopes = new LinkedHashMap<String, CrudReadScope>(governanceScope.getReadScopes());
        for (Map.Entry<String, CrudReadScope> entry : businessScopeConstraint.getReadScopes().entrySet()) {
            CrudReadScope current = readScopes.get(entry.getKey());
            CrudReadScope next = entry.getValue();
            if (current == null) {
                readScopes.put(entry.getKey(), next);
            } else if (!current.isAllowed() || !next.isAllowed()) {
                readScopes.put(entry.getKey(), CrudReadScope.deny());
            } else {
                Map<String, Object> conditions = new LinkedHashMap<String, Object>(current.getConditions());
                for (Map.Entry<String, Object> condition : next.getConditions().entrySet()) {
                    Object value = conditions.containsKey(condition.getKey())
                        ? intersectValue(conditions.get(condition.getKey()), condition.getValue(), condition.getKey())
                        : condition.getValue();
                    conditions.put(condition.getKey(), value);
                }
                readScopes.put(entry.getKey(), CrudReadScope.allow(conditions));
            }
        }
        if (businessScopeConstraint.isExplicitAll()) return governanceScope.withReadScopes(readScopes);
        if (governanceScope.isExplicitAll()) return businessScopeConstraint.withReadScopes(readScopes);

        Map<String, Object> merged = new LinkedHashMap<String, Object>(governanceScope.getDimensions());
        for (Map.Entry<String, Object> entry : businessScopeConstraint.getDimensions().entrySet()) {
            String dimension = entry.getKey();
            Object narrowed = normalizeValue(entry.getValue());
            if (narrowed == null) {
                throw new DataScopeDeniedException("业务范围约束值不能为空: " + dimension);
            }
            if (!merged.containsKey(dimension)) {
                merged.put(dimension, narrowed);
                continue;
            }
            merged.put(dimension, intersectValue(merged.get(dimension), narrowed, dimension));
        }
        if (merged.isEmpty()) {
            throw new DataScopeDeniedException("治理范围维度不能为空");
        }
        return new CrudDataScope(false, merged, readScopes);
    }

    private Object intersectValue(Object currentValue, Object constraintValue, String dimension) {
        List<Object> currentValues = toValueList(currentValue);
        List<Object> constraintValues = toValueList(constraintValue);
        List<Object> intersection = new ArrayList<Object>();
        for (Object currentItem : currentValues) {
            if (contains(constraintValues, currentItem)) {
                intersection.add(currentItem);
            }
        }
        if (intersection.isEmpty()) {
            throw new DataScopeDeniedException("业务范围约束与治理范围冲突: " + dimension);
        }
        if (intersection.size() == 1) {
            return intersection.get(0);
        }
        return intersection;
    }

    private boolean contains(List<Object> values, Object target) {
        for (Object value : values) {
            if (value == null ? target == null : value.equals(target)) {
                return true;
            }
        }
        return false;
    }

    private List<Object> toValueList(Object value) {
        Object normalized = normalizeValue(value);
        if (normalized instanceof Collection<?>) {
            return new ArrayList<Object>((Collection<?>) normalized);
        }
        List<Object> values = new ArrayList<Object>();
        values.add(normalized);
        return values;
    }

    /**
     * 规范化范围值，统一集合/数组/单值表示。
     */
    private Object normalizeValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Collection<?>) {
            LinkedHashSet<Object> deduplicated = new LinkedHashSet<Object>();
            for (Object item : (Collection<?>) value) {
                if (item != null) {
                    deduplicated.add(item);
                }
            }
            if (deduplicated.isEmpty()) {
                return null;
            }
            if (deduplicated.size() == 1) {
                return deduplicated.iterator().next();
            }
            return new ArrayList<Object>(deduplicated);
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            List<Object> values = new ArrayList<Object>(length);
            for (int i = 0; i < length; i++) {
                Object item = Array.get(value, i);
                if (item != null) {
                    values.add(item);
                }
            }
            return normalizeValue(values);
        }
        return value;
    }
}
