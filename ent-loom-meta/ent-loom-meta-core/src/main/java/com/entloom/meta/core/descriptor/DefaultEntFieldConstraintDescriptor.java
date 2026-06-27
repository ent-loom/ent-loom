package com.entloom.meta.core.descriptor;

import com.entloom.meta.contract.descriptor.EntFieldConstraintDescriptor;
import com.entloom.meta.contract.value.SourcedValue;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Default immutable field constraint descriptor.
 */
public final class DefaultEntFieldConstraintDescriptor implements EntFieldConstraintDescriptor {
    private final String name;
    private final String value;
    private final Map<String, SourcedValue<?>> sourcedValues;

    public DefaultEntFieldConstraintDescriptor(String name, String value) {
        this(name, value, Collections.<String, SourcedValue<?>>emptyMap());
    }

    public DefaultEntFieldConstraintDescriptor(String name, String value, Map<String, SourcedValue<?>> sourcedValues) {
        this.name = name;
        this.value = value;
        this.sourcedValues = immutableMap(sourcedValues);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String value() {
        return value;
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
