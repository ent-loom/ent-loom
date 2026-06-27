package com.entloom.meta.contract.diagnostic;

import java.util.List;

/**
 * Meta 诊断处理策略。
 */
public interface MetaDiagnosticPolicy {
    void evaluate(List<MetaDiagnostic> diagnostics);
}
