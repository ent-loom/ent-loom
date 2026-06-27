package com.entloom.meta.core.descriptor;

import com.entloom.base.util.value.TypedValueType;
import com.entloom.meta.contract.descriptor.EntFieldConstraintDescriptor;
import com.entloom.meta.contract.descriptor.EntFieldDescriptor;
import com.entloom.meta.contract.value.SourcedValue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Default immutable field descriptor.
 */
public final class DefaultEntFieldDescriptor implements EntFieldDescriptor {
    private final String fieldName;
    private final Class<?> javaType;
    private final String fieldKind;
    private final String role;
    private final String label;
    private final String description;
    private final List<String> examples;
    private final String createDefaultValue;
    private final TypedValueType createDefaultValueType;
    private final Object typedCreateDefaultValue;
    private final List<? extends EntFieldConstraintDescriptor> constraints;
    private final Boolean required;
    private final Boolean readOnly;
    private final Map<String, SourcedValue<?>> sourcedValues;

    public DefaultEntFieldDescriptor(
        String fieldName,
        Class<?> javaType,
        String fieldKind,
        String role,
        String label,
        String description,
        List<String> examples,
        String createDefaultValue,
        TypedValueType createDefaultValueType,
        Object typedCreateDefaultValue,
        List<? extends EntFieldConstraintDescriptor> constraints,
        Boolean required,
        Boolean readOnly
    ) {
        this(
            fieldName,
            javaType,
            fieldKind,
            role,
            label,
            description,
            examples,
            createDefaultValue,
            createDefaultValueType,
            typedCreateDefaultValue,
            constraints,
            required,
            readOnly,
            Collections.<String, SourcedValue<?>>emptyMap()
        );
    }

    public DefaultEntFieldDescriptor(
        String fieldName,
        Class<?> javaType,
        String fieldKind,
        String role,
        String label,
        String description,
        List<String> examples,
        String createDefaultValue,
        TypedValueType createDefaultValueType,
        Object typedCreateDefaultValue,
        List<? extends EntFieldConstraintDescriptor> constraints,
        Boolean required,
        Boolean readOnly,
        Map<String, SourcedValue<?>> sourcedValues
    ) {
        this.fieldName = fieldName;
        this.javaType = javaType;
        this.fieldKind = fieldKind;
        this.role = role;
        this.label = label;
        this.description = description;
        this.examples = examples == null
            ? Collections.<String>emptyList()
            : Collections.unmodifiableList(new ArrayList<String>(examples));
        this.createDefaultValue = createDefaultValue;
        this.createDefaultValueType = createDefaultValueType == null ? TypedValueType.UNSET : createDefaultValueType;
        this.typedCreateDefaultValue = typedCreateDefaultValue;
        this.constraints = constraints == null
            ? Collections.<EntFieldConstraintDescriptor>emptyList()
            : Collections.unmodifiableList(new ArrayList<EntFieldConstraintDescriptor>(constraints));
        this.required = required;
        this.readOnly = readOnly;
        this.sourcedValues = immutableMap(sourcedValues);
    }

    @Override
    public String fieldName() {
        return fieldName;
    }

    @Override
    public Class<?> javaType() {
        return javaType;
    }

    @Override
    public String fieldKind() {
        return fieldKind;
    }

    @Override
    public String role() {
        return role;
    }

    @Override
    public String label() {
        return label;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public List<String> examples() {
        return examples;
    }

    @Override
    public String createDefaultValue() {
        return createDefaultValue;
    }

    @Override
    public TypedValueType createDefaultValueType() {
        return createDefaultValueType;
    }

    @Override
    public Object typedCreateDefaultValue() {
        return typedCreateDefaultValue;
    }

    @Override
    public List<? extends EntFieldConstraintDescriptor> constraints() {
        return constraints;
    }

    @Override
    public Boolean required() {
        return required;
    }

    @Override
    public Boolean readOnly() {
        return readOnly;
    }

    @Override
    public Map<String, SourcedValue<?>> sourcedValues() {
        return sourcedValues;
    }

    private static Map<String, SourcedValue<?>> immutableMap(Map<String, SourcedValue<?>> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<String, SourcedValue<?>>(source));
    }
}
