package com.entloom.ui.core;

/**
 * 字段级 UI 契约。
 */
public final class UiFieldContract {
    private final String fieldName;
    private final String fieldLabel;
    private final UiComponentType componentType;
    private final boolean visibleInList;
    private final boolean visibleInForm;

    public UiFieldContract(String fieldName,
                           String fieldLabel,
                           UiComponentType componentType,
                           boolean visibleInList,
                           boolean visibleInForm) {
        if (fieldName == null || fieldName.trim().isEmpty()) {
            throw new IllegalArgumentException("fieldName must not be blank");
        }
        this.fieldName = fieldName;
        this.fieldLabel = fieldLabel == null ? "" : fieldLabel;
        this.componentType = componentType == null ? UiComponentType.TEXT : componentType;
        this.visibleInList = visibleInList;
        this.visibleInForm = visibleInForm;
    }

    public String fieldName() {
        return fieldName;
    }

    public String fieldLabel() {
        return fieldLabel;
    }

    public UiComponentType componentType() {
        return componentType;
    }

    public boolean visibleInList() {
        return visibleInList;
    }

    public boolean visibleInForm() {
        return visibleInForm;
    }

    public enum UiComponentType {
        TEXT,
        TEXTAREA,
        NUMBER,
        SWITCH,
        DATE_TIME,
        SELECT,
        IMAGE,
        FILE
    }
}
