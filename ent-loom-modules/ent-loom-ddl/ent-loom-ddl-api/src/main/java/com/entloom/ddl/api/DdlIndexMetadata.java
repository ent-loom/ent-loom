package com.entloom.ddl.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 索引元数据。
 */
public final class DdlIndexMetadata {
    private final String name;
    private final List<String> fields;
    private final boolean unique;
    private final String expression;

    public DdlIndexMetadata(String name, List<String> fields, boolean unique, String expression) {
        this.name = name == null ? "" : name.trim();
        this.fields = immutableCopy(fields);
        this.unique = unique;
        this.expression = expression == null ? "" : expression.trim();
    }

    public String name() {
        return name;
    }

    public List<String> fields() {
        return fields;
    }

    public boolean unique() {
        return unique;
    }

    public String expression() {
        return expression;
    }

    private static List<String> immutableCopy(List<String> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<String>(source));
    }
}
