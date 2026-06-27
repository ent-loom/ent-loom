package com.entloom.doc.core.model;

import com.entloom.meta.contract.value.SourcedValue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Stable DOC field model.
 */
public final class DocFieldModel {
    private final String property;
    private final Class<?> javaType;
    private final SourcedValue<String> column;
    private final SourcedValue<String> name;
    private final SourcedValue<String> description;
    private final SourcedValue<String> example;
    private final List<String> examples;
    private final SourcedValue<Boolean> required;
    private final SourcedValue<Boolean> readOnly;
    private final SourcedValue<Integer> maxLength;
    private final SourcedValue<Integer> minLength;
    private final SourcedValue<String> fieldKind;
    private final SourcedValue<String> role;
    private final SourcedValue<String> createDefaultValue;
    private final SourcedValue<String> group;
    private final SourcedValue<String> remark;
    private final SourcedValue<Boolean> hidden;
    private final List<String> visibleFor;
    private final List<DocFieldConstraintModel> constraints;

    public DocFieldModel(
        String property,
        Class<?> javaType,
        SourcedValue<String> column,
        SourcedValue<String> name,
        SourcedValue<String> description,
        SourcedValue<String> example,
        List<String> examples,
        SourcedValue<Boolean> required,
        SourcedValue<Boolean> readOnly,
        SourcedValue<Integer> maxLength,
        SourcedValue<Integer> minLength,
        SourcedValue<String> fieldKind,
        SourcedValue<String> role,
        SourcedValue<String> createDefaultValue,
        List<DocFieldConstraintModel> constraints
    ) {
        this(
            property,
            javaType,
            column,
            name,
            description,
            example,
            examples,
            required,
            readOnly,
            maxLength,
            minLength,
            fieldKind,
            role,
            createDefaultValue,
            SourcedValue.unknown(null),
            SourcedValue.unknown(null),
            SourcedValue.defaulted(Boolean.FALSE),
            Collections.<String>emptyList(),
            constraints
        );
    }

    public DocFieldModel(
        String property,
        Class<?> javaType,
        SourcedValue<String> column,
        SourcedValue<String> name,
        SourcedValue<String> description,
        SourcedValue<String> example,
        List<String> examples,
        SourcedValue<Boolean> required,
        SourcedValue<Boolean> readOnly,
        SourcedValue<Integer> maxLength,
        SourcedValue<Integer> minLength,
        SourcedValue<String> fieldKind,
        SourcedValue<String> role,
        SourcedValue<String> createDefaultValue,
        SourcedValue<String> group,
        SourcedValue<String> remark,
        SourcedValue<Boolean> hidden,
        List<String> visibleFor,
        List<DocFieldConstraintModel> constraints
    ) {
        this.property = property;
        this.javaType = javaType == null ? Object.class : javaType;
        this.column = column == null ? SourcedValue.unknown(null) : column;
        this.name = name == null ? SourcedValue.unknown(null) : name;
        this.description = description == null ? SourcedValue.unknown(null) : description;
        this.example = example == null ? SourcedValue.unknown(null) : example;
        this.examples = examples == null ? Collections.<String>emptyList() : Collections.unmodifiableList(new ArrayList<String>(examples));
        this.required = required == null ? SourcedValue.unknown(null) : required;
        this.readOnly = readOnly == null ? SourcedValue.unknown(null) : readOnly;
        this.maxLength = maxLength == null ? SourcedValue.unknown(null) : maxLength;
        this.minLength = minLength == null ? SourcedValue.unknown(null) : minLength;
        this.fieldKind = fieldKind == null ? SourcedValue.unknown(null) : fieldKind;
        this.role = role == null ? SourcedValue.unknown(null) : role;
        this.createDefaultValue = createDefaultValue == null ? SourcedValue.unknown(null) : createDefaultValue;
        this.group = group == null ? SourcedValue.unknown(null) : group;
        this.remark = remark == null ? SourcedValue.unknown(null) : remark;
        this.hidden = hidden == null ? SourcedValue.defaulted(Boolean.FALSE) : hidden;
        this.visibleFor = visibleFor == null ? Collections.<String>emptyList() : Collections.unmodifiableList(new ArrayList<String>(visibleFor));
        this.constraints = constraints == null
            ? Collections.<DocFieldConstraintModel>emptyList()
            : Collections.unmodifiableList(new ArrayList<DocFieldConstraintModel>(constraints));
    }

    public String property() {
        return property;
    }

    public Class<?> javaType() {
        return javaType;
    }

    public SourcedValue<String> column() {
        return column;
    }

    public SourcedValue<String> name() {
        return name;
    }

    public SourcedValue<String> description() {
        return description;
    }

    public SourcedValue<String> example() {
        return example;
    }

    public List<String> examples() {
        return examples;
    }

    public SourcedValue<Boolean> required() {
        return required;
    }

    public SourcedValue<Boolean> readOnly() {
        return readOnly;
    }

    public SourcedValue<Integer> maxLength() {
        return maxLength;
    }

    public SourcedValue<Integer> minLength() {
        return minLength;
    }

    public SourcedValue<String> fieldKind() {
        return fieldKind;
    }

    public SourcedValue<String> role() {
        return role;
    }

    public SourcedValue<String> createDefaultValue() {
        return createDefaultValue;
    }

    public SourcedValue<String> group() {
        return group;
    }

    public SourcedValue<String> remark() {
        return remark;
    }

    public SourcedValue<Boolean> hidden() {
        return hidden;
    }

    public List<String> visibleFor() {
        return visibleFor;
    }

    public List<DocFieldConstraintModel> constraints() {
        return constraints;
    }
}
