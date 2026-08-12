package com.entloom.crud.engine.jdbc.sql;

import com.entloom.crud.core.exception.ValidationException;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * JDBC 通用谓词片段构建工具。
 */
public final class JdbcPredicateBuilder {
    private JdbcPredicateBuilder() {
    }

    public static void appendEqualityOrIn(
        List<String> predicates,
        List<Object> args,
        String qualified,
        Object value,
        String field
    ) {
        if (value instanceof Collection<?>) {
            Collection<?> values = (Collection<?>) value;
            if (values.isEmpty()) {
                throw new ValidationException(field + " 需要非空集合");
            }
            String placeholders = values.stream().map(v -> "?").collect(Collectors.joining(","));
            predicates.add(qualified + " in (" + placeholders + ")");
            args.addAll(values);
            return;
        }
        predicates.add(qualified + " = ?");
        args.add(value);
    }
}
