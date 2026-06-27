package com.entloom.meta.core.descriptor;

import com.entloom.meta.contract.descriptor.EntIndexDescriptor;
import com.entloom.meta.contract.value.SourcedValue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Default immutable index descriptor.
 */
public final class DefaultEntIndexDescriptor implements EntIndexDescriptor {
    private final String indexName;
    private final List<String> fields;
    private final boolean unique;
    private final Map<String, SourcedValue<?>> sourcedValues;

    public DefaultEntIndexDescriptor(String indexName, List<String> fields, boolean unique) {
        this(indexName, fields, unique, Collections.<String, SourcedValue<?>>emptyMap());
    }

    public DefaultEntIndexDescriptor(
        String indexName,
        List<String> fields,
        boolean unique,
        Map<String, SourcedValue<?>> sourcedValues
    ) {
        this.indexName = indexName;
        this.fields = fields == null
            ? Collections.<String>emptyList()
            : Collections.unmodifiableList(new ArrayList<String>(fields));
        this.unique = unique;
        this.sourcedValues = immutableMap(sourcedValues);
    }

    @Override
    public String indexName() {
        return indexName;
    }

    @Override
    public String name() {
        return indexName;
    }

    @Override
    public List<String> fields() {
        return fields;
    }

    @Override
    public boolean unique() {
        return unique;
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
