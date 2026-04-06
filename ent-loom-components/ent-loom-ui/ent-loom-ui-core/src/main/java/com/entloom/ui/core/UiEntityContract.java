package com.entloom.ui.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 实体级 UI 契约。
 */
public final class UiEntityContract {
    private final String entityCode;
    private final String titleFieldName;
    private final List<UiFieldContract> fields;

    public UiEntityContract(String entityCode, String titleFieldName, List<UiFieldContract> fields) {
        this.entityCode = requireText(entityCode, "entityCode");
        this.titleFieldName = requireText(titleFieldName, "titleFieldName");
        this.fields = immutableCopy(fields);
    }

    public String entityCode() {
        return entityCode;
    }

    public String titleFieldName() {
        return titleFieldName;
    }

    public List<UiFieldContract> fields() {
        return fields;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    private static List<UiFieldContract> immutableCopy(List<UiFieldContract> source) {
        if (source == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<UiFieldContract>(source));
    }
}
