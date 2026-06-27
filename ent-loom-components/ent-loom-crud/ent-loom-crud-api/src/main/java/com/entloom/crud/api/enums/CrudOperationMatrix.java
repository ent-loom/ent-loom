package com.entloom.crud.api.enums;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * CRUD operation 合法矩阵。
 */
public final class CrudOperationMatrix {
    private static final Map<CrudOperationDomain, Set<String>> LEGAL_OPERATIONS = buildLegalOperations();

    private CrudOperationMatrix() {
    }

    public static boolean isLegal(CrudOperationDomain domain, String operation) {
        if (domain == null || operation == null) {
            return false;
        }
        Set<String> operations = LEGAL_OPERATIONS.get(domain);
        return operations != null && operations.contains(operation);
    }

    public static Set<String> legalOperations(CrudOperationDomain domain) {
        Set<String> operations = LEGAL_OPERATIONS.get(domain);
        return operations == null ? Collections.<String>emptySet() : operations;
    }

    private static Map<CrudOperationDomain, Set<String>> buildLegalOperations() {
        Map<CrudOperationDomain, Set<String>> operations = new EnumMap<CrudOperationDomain, Set<String>>(CrudOperationDomain.class);
        operations.put(CrudOperationDomain.QUERY, names(QueryOperation.values()));
        operations.put(CrudOperationDomain.COMMAND, names(CommandOperation.values()));
        operations.put(CrudOperationDomain.STATS, names(StatsOperation.values()));
        operations.put(CrudOperationDomain.IMPORT, names(ImportOperation.values()));
        operations.put(CrudOperationDomain.EXPORT, names(ExportOperation.values()));
        return Collections.unmodifiableMap(operations);
    }

    private static Set<String> names(Enum<?>[] values) {
        Set<String> names = new LinkedHashSet<String>();
        for (Enum<?> value : values) {
            names.add(value.name());
        }
        return Collections.unmodifiableSet(names);
    }
}
