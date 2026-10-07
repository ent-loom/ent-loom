package com.entloom.crud.core.governance.fact;

import com.entloom.crud.core.exception.ValidationException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 每个元组内的角色取交集，多个元组取并集；保留角色之间的配对关系。 */
public final class RelationFactSet {
    private final List<Map<String, String>> tuples;

    public RelationFactSet(Collection<? extends Map<String, String>> tuples) {
        Objects.requireNonNull(tuples, "关系事实元组不能为空");
        List<Map<String, String>> copy = new ArrayList<Map<String, String>>();
        for (Map<String, String> tuple : tuples) {
            if (tuple == null) throw new ValidationException("关系事实元组不能为空");
            Map<String, String> values = new LinkedHashMap<String, String>();
            for (Map.Entry<String, String> entry : tuple.entrySet()) {
                if (entry.getKey() == null || entry.getKey().trim().isEmpty()
                    || entry.getValue() == null || entry.getValue().trim().isEmpty()) {
                    throw new ValidationException("关系事实角色和标识不能为空");
                }
                values.put(entry.getKey(), entry.getValue());
            }
            copy.add(Collections.unmodifiableMap(values));
        }
        this.tuples = Collections.unmodifiableList(copy);
    }

    public List<Map<String, String>> getTuples() {
        return tuples;
    }
}
