package com.entloom.meta.adapter.ddl;

import com.entloom.meta.contract.value.SourcedValue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class DdlNativeIndexModel {
    private final SourcedValue<String> name;
    private final List<String> fields;
    private final SourcedValue<Boolean> unique;
    private final String expression;

    DdlNativeIndexModel(SourcedValue<String> name, List<String> fields, SourcedValue<Boolean> unique, String expression) {
        this.name = name;
        this.fields = Collections.unmodifiableList(new ArrayList<String>(fields));
        this.unique = unique;
        this.expression = expression;
    }

    SourcedValue<String> name() {
        return name;
    }

    List<String> fields() {
        return fields;
    }

    SourcedValue<Boolean> unique() {
        return unique;
    }

    String expression() {
        return expression;
    }
}
