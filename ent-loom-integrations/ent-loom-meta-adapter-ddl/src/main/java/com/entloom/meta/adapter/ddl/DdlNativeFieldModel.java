package com.entloom.meta.adapter.ddl;

import com.entloom.ddl.enums.GenerationStrategy;
import com.entloom.meta.contract.value.SourcedValue;

final class DdlNativeFieldModel {
    private final String fieldName;
    private final Class<?> javaType;
    private final SourcedValue<String> columnName;
    private final SourcedValue<String> columnDefinition;
    private final SourcedValue<Boolean> nullable;
    private final SourcedValue<Boolean> unique;
    private final SourcedValue<Boolean> persisted;
    private final SourcedValue<Boolean> primaryKey;
    private final SourcedValue<Integer> length;
    private final SourcedValue<Integer> precision;
    private final SourcedValue<Integer> scale;
    private final SourcedValue<String> defaultValue;
    private final SourcedValue<String> comment;
    private final SourcedValue<String> renameFrom;
    private final SourcedValue<GenerationStrategy> generationStrategy;

    DdlNativeFieldModel(
        String fieldName,
        Class<?> javaType,
        SourcedValue<String> columnName,
        SourcedValue<String> columnDefinition,
        SourcedValue<Boolean> nullable,
        SourcedValue<Boolean> unique,
        SourcedValue<Boolean> persisted,
        SourcedValue<Boolean> primaryKey,
        SourcedValue<Integer> length,
        SourcedValue<Integer> precision,
        SourcedValue<Integer> scale,
        SourcedValue<String> defaultValue,
        SourcedValue<String> comment,
        SourcedValue<String> renameFrom,
        SourcedValue<GenerationStrategy> generationStrategy
    ) {
        this.fieldName = fieldName;
        this.javaType = javaType;
        this.columnName = columnName;
        this.columnDefinition = columnDefinition;
        this.nullable = nullable;
        this.unique = unique;
        this.persisted = persisted;
        this.primaryKey = primaryKey;
        this.length = length;
        this.precision = precision;
        this.scale = scale;
        this.defaultValue = defaultValue;
        this.comment = comment;
        this.renameFrom = renameFrom;
        this.generationStrategy = generationStrategy;
    }

    String fieldName() {
        return fieldName;
    }

    Class<?> javaType() {
        return javaType;
    }

    SourcedValue<String> columnName() {
        return columnName;
    }

    SourcedValue<String> columnDefinition() {
        return columnDefinition;
    }

    SourcedValue<Boolean> nullable() {
        return nullable;
    }

    SourcedValue<Boolean> unique() {
        return unique;
    }

    SourcedValue<Boolean> persisted() {
        return persisted;
    }

    SourcedValue<Boolean> primaryKey() {
        return primaryKey;
    }

    SourcedValue<Integer> length() {
        return length;
    }

    SourcedValue<Integer> precision() {
        return precision;
    }

    SourcedValue<Integer> scale() {
        return scale;
    }

    SourcedValue<String> defaultValue() {
        return defaultValue;
    }

    SourcedValue<String> comment() {
        return comment;
    }

    SourcedValue<String> renameFrom() {
        return renameFrom;
    }

    SourcedValue<GenerationStrategy> generationStrategy() {
        return generationStrategy;
    }
}
