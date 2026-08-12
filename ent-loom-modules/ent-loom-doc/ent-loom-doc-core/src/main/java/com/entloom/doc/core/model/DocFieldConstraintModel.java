package com.entloom.doc.core.model;

/**
 * Stable DOC field constraint model.
 */
public final class DocFieldConstraintModel {
    private final String name;
    private final String value;

    public DocFieldConstraintModel(String name, String value) {
        this.name = name;
        this.value = value;
    }

    public String name() {
        return name;
    }

    public String value() {
        return value;
    }
}
