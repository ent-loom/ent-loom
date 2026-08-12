package com.entloom.crud.core.util;

import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.api.enums.CrudOperationKey;
import com.entloom.crud.api.enums.QueryOperation;
import com.entloom.crud.api.enums.StatsOperation;
import com.entloom.crud.core.capability.stats.StatsSpec;
import com.entloom.crud.core.capability.exporting.ExportSpec;
import com.entloom.crud.core.capability.importing.ImportSpec;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.runtime.router.CrudRouteKey;
import com.entloom.crud.core.runtime.spec.BaseSpec;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * 路由 key 构建器。
 */
public final class RouteKeyFactory {
    private RouteKeyFactory() {
    }

    /**
     * 构建查询路由 key。
     *
     * @param spec 查询 spec
     * @return routeKey
     */
    public static CrudRouteKey buildQueryRoute(QuerySpec<?> spec) {
        QueryOperation op = spec.getOp();
        return buildRoute(spec, op == null ? null : CrudOperationKey.of(op));
    }

    public static String buildQueryRouteKey(QuerySpec<?> spec) {
        return buildQueryRoute(spec).toString();
    }

    /**
     * 构建命令路由 key。
     *
     * @param spec 命令 spec
     * @return routeKey
     */
    public static CrudRouteKey buildCommandRoute(CommandSpec<?> spec) {
        CommandOperation op = spec.getOp();
        return buildRoute(spec, op == null ? null : CrudOperationKey.of(op));
    }

    public static CrudRouteKey buildStatsRoute(BaseSpec spec) {
        StatsOperation operation = spec instanceof StatsSpec ? ((StatsSpec) spec).getOperation() : StatsOperation.QUERY;
        return buildRoute(spec, CrudOperationKey.of(operation));
    }

    public static String buildStatsRouteKey(BaseSpec spec) {
        return buildStatsRoute(spec).toString();
    }

    public static CrudRouteKey buildImportRoute(ImportSpec spec) {
        return buildRoute(spec, spec == null ? null : spec.getOperationKey());
    }

    public static String buildImportRouteKey(ImportSpec spec) {
        return buildImportRoute(spec).toString();
    }

    public static CrudRouteKey buildExportRoute(ExportSpec spec) {
        return buildRoute(spec, spec == null ? null : spec.getOperationKey());
    }

    public static String buildExportRouteKey(ExportSpec spec) {
        return buildExportRoute(spec).toString();
    }

    private static CrudRouteKey buildRoute(BaseSpec spec, CrudOperationKey operationKey) {
        return new CrudRouteKey(
            resolveEntityTypeNames(spec.getEntityClasses(), spec.getRootType()),
            operationKey,
            normalizeScene(spec.getScene())
        );
    }

    public static String buildCommandRouteKey(CommandSpec<?> spec) {
        return buildCommandRoute(spec).toString();
    }

    /**
     * 归一化 scene。
     *
     * @param scene 场景值
     * @return 归一化结果
     */
    public static String normalizeScene(String scene) {
        return scene == null ? "" : scene.trim().toLowerCase(Locale.ROOT);
    }

    private static List<Class<?>> resolveEntityClasses(List<Class<?>> entityClasses, Class<?> rootType) {
        if (rootType == null) {
            throw new ValidationException("rootType 不能为空");
        }
        List<Class<?>> source = new ArrayList<Class<?>>();
        if (entityClasses == null || entityClasses.isEmpty()) {
            source.add(rootType);
        } else {
            source.addAll(entityClasses);
        }

        List<Class<?>> canonical = new ArrayList<Class<?>>();
        LinkedHashSet<Class<?>> deduplicated = new LinkedHashSet<Class<?>>();
        for (Class<?> entityClass : source) {
            if (entityClass == null) {
                continue;
            }
            if (deduplicated.add(entityClass)) {
                canonical.add(entityClass);
            }
        }

        if (canonical.isEmpty()) {
            throw new ValidationException("entityClasses 不能为空");
        }
        if (!rootType.equals(canonical.get(0))) {
            throw new ValidationException("entityClasses 首元素必须等于 rootType");
        }
        return canonical;
    }

    private static List<String> resolveEntityTypeNames(List<Class<?>> entityClasses, Class<?> rootType) {
        return resolveEntityClasses(entityClasses, rootType).stream().map(Class::getName).collect(Collectors.toList());
    }

    private static String buildEntityPart(List<Class<?>> entityClasses) {
        return entityClasses.stream().map(Class::getName).collect(Collectors.joining(">"));
    }
}
