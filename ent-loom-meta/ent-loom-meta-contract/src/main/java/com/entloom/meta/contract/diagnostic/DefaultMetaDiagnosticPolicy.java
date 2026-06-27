package com.entloom.meta.contract.diagnostic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 默认 Meta 诊断策略。
 */
public final class DefaultMetaDiagnosticPolicy implements MetaDiagnosticPolicy {
    private static final DefaultMetaDiagnosticPolicy FAIL_FAST = new DefaultMetaDiagnosticPolicy(true);
    private static final DefaultMetaDiagnosticPolicy LENIENT = new DefaultMetaDiagnosticPolicy(false);

    private final boolean failFast;

    private DefaultMetaDiagnosticPolicy(boolean failFast) {
        this.failFast = failFast;
    }

    public static DefaultMetaDiagnosticPolicy failFast() {
        return FAIL_FAST;
    }

    public static DefaultMetaDiagnosticPolicy lenient() {
        return LENIENT;
    }

    @Override
    public void evaluate(List<MetaDiagnostic> diagnostics) {
        if (!failFast) {
            return;
        }
        List<MetaDiagnostic> errors = errors(diagnostics);
        if (!errors.isEmpty()) {
            throw new MetaDiagnosticException(errors);
        }
    }

    private List<MetaDiagnostic> errors(List<MetaDiagnostic> diagnostics) {
        if (diagnostics == null || diagnostics.isEmpty()) {
            return Collections.emptyList();
        }
        List<MetaDiagnostic> errors = new ArrayList<MetaDiagnostic>();
        for (MetaDiagnostic diagnostic : diagnostics) {
            if (diagnostic != null && diagnostic.level() == MetaDiagnosticLevel.ERROR) {
                errors.add(diagnostic);
            }
        }
        return errors;
    }
}
