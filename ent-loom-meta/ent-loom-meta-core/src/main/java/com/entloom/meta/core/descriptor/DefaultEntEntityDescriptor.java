package com.entloom.meta.core.descriptor;

import com.entloom.meta.contract.descriptor.EntEntityDescriptor;
import com.entloom.meta.contract.descriptor.EntFieldDescriptor;
import com.entloom.meta.contract.descriptor.EntIndexDescriptor;
import com.entloom.meta.contract.descriptor.EntRelationDescriptor;
import com.entloom.meta.contract.value.SourcedValue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Default immutable entity descriptor.
 */
public final class DefaultEntEntityDescriptor implements EntEntityDescriptor {
    private final Class<?> entityClass;
    private final String entityName;
    private final String serviceName;
    private final String label;
    private final String description;
    private final List<String> defaultLabelFields;
    private final Long plannedVolume;
    private final List<? extends EntFieldDescriptor> fields;
    private final List<? extends EntRelationDescriptor> relations;
    private final List<? extends EntIndexDescriptor> indexes;
    private final Map<String, SourcedValue<?>> sourcedValues;

    public DefaultEntEntityDescriptor(
        Class<?> entityClass,
        String entityName,
        String serviceName,
        String label,
        String description,
        List<String> defaultLabelFields,
        Long plannedVolume,
        List<? extends EntFieldDescriptor> fields,
        List<? extends EntRelationDescriptor> relations,
        List<? extends EntIndexDescriptor> indexes
    ) {
        this(
            entityClass,
            entityName,
            serviceName,
            label,
            description,
            defaultLabelFields,
            plannedVolume,
            fields,
            relations,
            indexes,
            Collections.<String, SourcedValue<?>>emptyMap()
        );
    }

    public DefaultEntEntityDescriptor(
        Class<?> entityClass,
        String entityName,
        String serviceName,
        String label,
        String description,
        List<String> defaultLabelFields,
        Long plannedVolume,
        List<? extends EntFieldDescriptor> fields,
        List<? extends EntRelationDescriptor> relations,
        List<? extends EntIndexDescriptor> indexes,
        Map<String, SourcedValue<?>> sourcedValues
    ) {
        this.entityClass = entityClass;
        this.entityName = entityName;
        this.serviceName = serviceName;
        this.label = label;
        this.description = description;
        this.defaultLabelFields = immutableCopy(defaultLabelFields);
        this.plannedVolume = plannedVolume;
        this.fields = immutableCopy(fields);
        this.relations = immutableCopy(relations);
        this.indexes = immutableCopy(indexes);
        this.sourcedValues = immutableMap(sourcedValues);
    }

    @Override
    public Class<?> entityClass() {
        return entityClass;
    }

    @Override
    public String entityName() {
        return entityName;
    }

    @Override
    public String serviceName() {
        return serviceName;
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
    public List<String> defaultLabelFields() {
        return defaultLabelFields;
    }

    @Override
    public Long plannedVolume() {
        return plannedVolume;
    }

    @Override
    public List<? extends EntFieldDescriptor> fields() {
        return fields;
    }

    @Override
    public List<? extends EntRelationDescriptor> relations() {
        return relations;
    }

    @Override
    public List<? extends EntIndexDescriptor> indexes() {
        return indexes;
    }

    @Override
    public Map<String, SourcedValue<?>> sourcedValues() {
        return sourcedValues;
    }

    private static <T> List<T> immutableCopy(List<? extends T> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<T>(source));
    }

    private static Map<String, SourcedValue<?>> immutableMap(Map<String, SourcedValue<?>> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<String, SourcedValue<?>>(source));
    }
}
