package com.entloom.meta.contract.diagnostic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Meta 诊断收集器。
 */
public final class MetaDiagnosticCollector {
    private final List<MetaDiagnostic> diagnostics = new ArrayList<MetaDiagnostic>();

    public void add(MetaDiagnostic diagnostic) {
        if (diagnostic != null) {
            diagnostics.add(diagnostic);
        }
    }

    public void addAll(Iterable<MetaDiagnostic> diagnostics) {
        if (diagnostics == null) {
            return;
        }
        for (MetaDiagnostic diagnostic : diagnostics) {
            add(diagnostic);
        }
    }

    public List<MetaDiagnostic> diagnostics() {
        return Collections.unmodifiableList(new ArrayList<MetaDiagnostic>(diagnostics));
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
