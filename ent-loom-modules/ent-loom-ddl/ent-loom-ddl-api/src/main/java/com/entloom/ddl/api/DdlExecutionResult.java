package com.entloom.ddl.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * DDL 执行结果。
 */
public final class DdlExecutionResult {
    private final List<String> generatedSql;
    private final List<String> executedSql;
    private final List<String> errors;
    private final List<String> warnings;

    public DdlExecutionResult(List<String> generatedSql, List<String> executedSql, List<String> errors) {
        this(generatedSql, executedSql, errors, Collections.<String>emptyList());
    }

    public DdlExecutionResult(List<String> generatedSql, List<String> executedSql,
                              List<String> errors, List<String> warnings) {
        this.generatedSql = immutableCopy(generatedSql);
        this.executedSql = immutableCopy(executedSql);
        this.errors = immutableCopy(errors);
        this.warnings = immutableCopy(warnings);
    }

    public List<String> generatedSql() {
        return generatedSql;
    }

    public List<String> executedSql() {
        return executedSql;
    }

    public List<String> errors() {
        return errors;
    }

    /** 不影响迁移执行与成功状态的提示。 */
    public List<String> warnings() {
        return warnings;
    }

    public boolean success() {
        return errors.isEmpty();
    }

    private static List<String> immutableCopy(List<String> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<String>(source));
    }
}
