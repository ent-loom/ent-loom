package com.entloom.meta.contract.diagnostic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 带诊断的结果对象。
 */
public final class MetaDiagnosticResult<T> {
    private final T value;
    private final List<MetaDiagnostic> diagnostics;

    private MetaDiagnosticResult(T value, List<MetaDiagnostic> diagnostics) {
        this.value = value;
        this.diagnostics = diagnostics == null
            ? Collections.<MetaDiagnostic>emptyList()
            : Collections.unmodifiableList(new ArrayList<MetaDiagnostic>(diagnostics));
    }

    public static <T> MetaDiagnosticResult<T> of(T value, List<MetaDiagnostic> diagnostics) {
        return new MetaDiagnosticResult<T>(value, diagnostics);
    }

    public static <T> MetaDiagnosticResult<T> success(T value) {
        return new MetaDiagnosticResult<T>(value, Collections.<MetaDiagnostic>emptyList());
    }

    public T value() {
        return value;
    }

    public List<MetaDiagnostic> diagnostics() {
        return diagnostics;
    }

    public boolean hasErrors() {
        for (MetaDiagnostic diagnostic : diagnostics) {
            if (diagnostic.level() == MetaDiagnosticLevel.ERROR) {
                return true;
            }
        }
        return false;
    }
}
