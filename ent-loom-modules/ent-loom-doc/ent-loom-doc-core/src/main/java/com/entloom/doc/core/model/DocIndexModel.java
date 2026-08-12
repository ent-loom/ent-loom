package com.entloom.doc.core.model;

import com.entloom.meta.contract.value.SourcedValue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Stable DOC index model.
 */
public final class DocIndexModel {
    private final SourcedValue<String> name;
    private final List<String> fields;
    private final SourcedValue<Boolean> unique;

    public DocIndexModel(SourcedValue<String> name, List<String> fields, SourcedValue<Boolean> unique) {
        this.name = name == null ? SourcedValue.unknown(null) : name;
        this.fields = fields == null ? Collections.<String>emptyList() : Collections.unmodifiableList(new ArrayList<String>(fields));
        this.unique = unique == null ? SourcedValue.unknown(null) : unique;
    }

    public SourcedValue<String> name() {
        return name;
    }

    public List<String> fields() {
        return fields;
    }

    public SourcedValue<Boolean> unique() {
        return unique;
    }
}
