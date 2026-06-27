package com.entloom.meta.contract.diagnostic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Meta 诊断 fail-fast 异常。
 */
public class MetaDiagnosticException extends RuntimeException {
    private final List<MetaDiagnostic> diagnostics;

    public MetaDiagnosticException(List<MetaDiagnostic> diagnostics) {
        super(message(diagnostics));
        this.diagnostics = diagnostics == null
            ? Collections.<MetaDiagnostic>emptyList()
            : Collections.unmodifiableList(new ArrayList<MetaDiagnostic>(diagnostics));
    }

    public List<MetaDiagnostic> diagnostics() {
        return diagnostics;
    }

    private static String message(List<MetaDiagnostic> diagnostics) {
        if (diagnostics == null || diagnostics.isEmpty()) {
            return "Meta diagnostics failed";
        }
        return "Meta diagnostics failed: " + diagnostics.get(0).message();
    }
}
