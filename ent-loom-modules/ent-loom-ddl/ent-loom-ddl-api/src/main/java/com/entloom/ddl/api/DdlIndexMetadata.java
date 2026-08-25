package com.entloom.ddl.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * DDL 索引元数据。
 *
 * <p>索引必须至少包含一个物理列或一个原生表达式。两者同时提供时，
 * 表达式优先，兼容注解层的既有语义。未提供索引名时由 SQL 生成器根据
 * 内容计算确定性名称。</p>
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
        if (this.fields.isEmpty() && this.expression.isEmpty()) {
            throw new IllegalArgumentException("index must define fields or expression");
        }
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
        List<String> copy = new ArrayList<String>(source.size());
        Set<String> seen = new HashSet<String>();
        for (String field : source) {
            if (field == null || field.trim().isEmpty()) {
                throw new IllegalArgumentException("index field must not be blank");
            }
            String normalized = field.trim();
            if (!seen.add(normalized)) {
                throw new IllegalArgumentException("index field must not be duplicated: " + normalized);
            }
            copy.add(normalized);
        }
        return Collections.unmodifiableList(copy);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof DdlIndexMetadata)) {
            return false;
        }
        DdlIndexMetadata that = (DdlIndexMetadata) other;
        return unique == that.unique
                && name.equals(that.name)
                && fields.equals(that.fields)
                && expression.equals(that.expression);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, fields, unique, expression);
    }
}
