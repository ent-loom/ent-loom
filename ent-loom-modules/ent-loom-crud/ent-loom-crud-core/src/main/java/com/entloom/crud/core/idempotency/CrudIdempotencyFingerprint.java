package com.entloom.crud.core.idempotency;

import com.entloom.crud.api.model.PageRequest;
import com.entloom.crud.api.model.QueryFilter;
import com.entloom.crud.api.model.QuerySort;
import com.entloom.crud.api.model.QueryTimeRange;
import com.entloom.crud.api.model.SubjectContext;
import com.entloom.crud.core.capability.exporting.ExportSpec;
import com.entloom.crud.core.capability.importing.ImportSpec;
import com.entloom.crud.core.foundation.taskfile.FileRef;
import com.entloom.crud.core.governance.scope.CrudDataScope;
import com.entloom.crud.core.runtime.spec.BaseSpec;
import java.lang.reflect.Array;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * CRUD 导入导出幂等指纹。
 *
 * <p>幂等管理器只接收普通 Map、List 和标量，避免对完整 Spec、Class 或 JDK Bean
 * 做反射。指纹只包含影响导入导出执行结果的请求语义，不包含运行时对象身份。</p>
 */
public final class CrudIdempotencyFingerprint {
    private CrudIdempotencyFingerprint() {
    }

    /**
     * 构造导出请求指纹。
     *
     * @param spec 已完成治理和 payload 定制的导出规范
     * @return 可交给 PayloadCanonicalizer 的普通对象
     */
    public static Map<String, Object> forExport(ExportSpec spec) {
        if (spec == null) {
            throw new IllegalArgumentException("导出规范不能为空");
        }
        Map<String, Object> result = base(spec);
        put(result, "operation", enumValue(spec.getOperation()));
        put(result, "format", spec.getFormat());
        put(result, "fileName", spec.getFileName());
        put(result, "taskId", spec.getTaskId());
        put(result, "async", Boolean.valueOf(spec.isAsync()));
        put(result, "fields", normalize(spec.getFields()));
        put(result, "filters", filters(spec.getFilters()));
        put(result, "sorts", sorts(spec.getSorts()));
        put(result, "time", time(spec.getTime()));
        put(result, "renderOptions", renderOptions(spec.getRenderOptions()));
        put(result, "page", page(spec.getPage()));
        put(result, "limit", spec.getLimit());
        put(result, "payload", normalize(spec.getPayload()));
        put(result, "includeExecutionMeta", Boolean.valueOf(spec.isIncludeExecutionMeta()));
        return result;
    }

    /**
     * 构造导入请求指纹。
     *
     * @param spec 已完成治理和 payload 定制的导入规范
     * @return 可交给 PayloadCanonicalizer 的普通对象
     */
    public static Map<String, Object> forImport(ImportSpec spec) {
        if (spec == null) {
            throw new IllegalArgumentException("导入规范不能为空");
        }
        Map<String, Object> result = base(spec);
        put(result, "operation", enumValue(spec.getOperation()));
        put(result, "format", spec.getFormat());
        put(result, "mode", enumValue(spec.getMode()));
        put(result, "sourceFile", sourceFile(spec.getSourceFile()));
        put(result, "taskId", spec.getTaskId());
        put(result, "batchSize", spec.getBatchSize());
        put(result, "async", Boolean.valueOf(spec.isAsync()));
        put(result, "transactionPolicy", enumValue(spec.getTransactionPolicy()));
        put(result, "payload", normalize(spec.getPayload()));
        put(result, "includeExecutionMeta", Boolean.valueOf(spec.isIncludeExecutionMeta()));
        return result;
    }

    private static Map<String, Object> base(BaseSpec spec) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        put(result, "scene", spec.getScene());
        put(result, "rootType", typeName(spec.getRootType()));
        put(result, "entityClasses", classNames(spec.getEntityClasses()));
        put(result, "subject", subject(spec.getSubject()));
        put(result, "attributes", normalize(spec.getAttributes()));
        put(result, "grantedScope", scope(spec.getGrantedScope()));
        put(result, "governanceScope", scope(spec.getGovernanceScope()));
        put(result, "accessDecision", enumValue(spec.getAccessDecision()));
        return result;
    }

    private static List<Object> classNames(List<Class<?>> source) {
        List<Object> result = new ArrayList<Object>();
        if (source == null) {
            return result;
        }
        for (Class<?> type : source) {
            result.add(typeName(type));
        }
        return result;
    }

    private static Map<String, Object> subject(SubjectContext source) {
        if (source == null) {
            return null;
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        put(result, "subjectId", source.getSubjectId());
        put(result, "tenantId", source.getTenantId());
        put(result, "orgId", source.getOrgId());
        return result;
    }

    private static Map<String, Object> scope(CrudDataScope source) {
        if (source == null) {
            return null;
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        put(result, "explicitAll", Boolean.valueOf(source.isExplicitAll()));
        put(result, "dimensions", normalize(source.getDimensions()));
        return result;
    }

    private static List<Object> filters(List<QueryFilter> source) {
        List<Object> result = new ArrayList<Object>();
        if (source == null) {
            return result;
        }
        for (QueryFilter filter : source) {
            if (filter == null) {
                result.add(null);
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            put(item, "field", filter.getField());
            put(item, "operator", enumValue(filter.getOperator()));
            put(item, "value", normalize(filter.getValue()));
            result.add(item);
        }
        return result;
    }

    private static List<Object> sorts(List<QuerySort> source) {
        List<Object> result = new ArrayList<Object>();
        if (source == null) {
            return result;
        }
        for (QuerySort sort : source) {
            if (sort == null) {
                result.add(null);
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            put(item, "field", sort.getField());
            put(item, "direction", enumValue(sort.getDirection()));
            put(item, "target", enumValue(sort.getTarget()));
            result.add(item);
        }
        return result;
    }

    private static Map<String, Object> time(QueryTimeRange source) {
        if (source == null) {
            return null;
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        put(result, "field", source.getField());
        put(result, "start", source.getStart());
        put(result, "end", source.getEnd());
        put(result, "timezone", source.getTimezone());
        return result;
    }

    private static Map<String, Object> renderOptions(com.entloom.crud.core.capability.exporting.ExportRenderOptions source) {
        if (source == null) {
            return null;
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        put(result, "timezone", source.getTimezone());
        return result;
    }

    private static Map<String, Object> page(PageRequest source) {
        if (source == null) {
            return null;
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        put(result, "page", Integer.valueOf(source.getPage()));
        put(result, "limit", Integer.valueOf(source.getLimit()));
        return result;
    }

    private static Map<String, Object> sourceFile(FileRef source) {
        if (source == null) {
            return null;
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        put(result, "fileId", source.getFileId());
        return result;
    }

    private static String typeName(Class<?> type) {
        return type == null ? null : type.getName();
    }

    private static String enumValue(Enum<?> value) {
        return value == null ? null : value.getDeclaringClass().getName() + "#" + value.name();
    }

    private static void put(Map<String, Object> target, String key, Object value) {
        target.put(key, value == null ? null : normalize(value));
    }

    /**
     * 将请求值收敛为只包含 Map、List 和稳定标量的结构。
     */
    private static Object normalize(Object value) {
        if (value == null || value instanceof String || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Character) {
            return typedScalar("char", String.valueOf(value));
        }
        if (value instanceof Number) {
            return typedScalar(value.getClass().getName(), String.valueOf(value));
        }
        if (value instanceof Enum<?>) {
            return typedScalar("enum:" + ((Enum<?>) value).getDeclaringClass().getName(), ((Enum<?>) value).name());
        }
        if (value instanceof Class<?>) {
            return typedScalar("class", ((Class<?>) value).getName());
        }
        if (value instanceof TemporalAccessor) {
            return typedScalar("temporal:" + value.getClass().getName(), value.toString());
        }
        if (value instanceof Date) {
            return typedScalar("date:" + value.getClass().getName(), ((Date) value).toInstant().toString());
        }
        if (value instanceof UUID) {
            return typedScalar("uuid", value.toString());
        }
        if (value instanceof Map<?, ?>) {
            return normalizeMap((Map<?, ?>) value);
        }
        if (value instanceof Collection<?>) {
            return normalizeCollection((Collection<?>) value);
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            List<Object> result = new ArrayList<Object>(length);
            for (int i = 0; i < length; i++) {
                result.add(normalize(Array.get(value, i)));
            }
            return result;
        }
        throw new IllegalArgumentException("幂等指纹不支持的载荷类型: " + value.getClass().getName());
    }

    private static Map<String, Object> normalizeMap(Map<?, ?> source) {
        List<Map.Entry<?, ?>> entries = new ArrayList<Map.Entry<?, ?>>(source.entrySet());
        Collections.sort(entries, new Comparator<Map.Entry<?, ?>>() {
            @Override
            public int compare(Map.Entry<?, ?> left, Map.Entry<?, ?> right) {
                return String.valueOf(left.getKey()).compareTo(String.valueOf(right.getKey()));
            }
        });
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        for (Map.Entry<?, ?> entry : entries) {
            String key = String.valueOf(entry.getKey());
            if (result.containsKey(key)) {
                throw new IllegalArgumentException("幂等指纹存在重复 Map 键: " + key);
            }
            result.put(key, normalize(entry.getValue()));
        }
        return result;
    }

    private static List<Object> normalizeCollection(Collection<?> source) {
        List<Object> result = new ArrayList<Object>(source.size());
        for (Object item : source) {
            result.add(normalize(item));
        }
        if (source instanceof Set<?>) {
            final StablePayloadCanonicalizer canonicalizer = new StablePayloadCanonicalizer();
            Collections.sort(result, new Comparator<Object>() {
                @Override
                public int compare(Object left, Object right) {
                    return canonicalizer.canonicalize(left).compareTo(canonicalizer.canonicalize(right));
                }
            });
        }
        return result;
    }

    private static Map<String, Object> typedScalar(String type, String value) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("type", type);
        result.put("value", value);
        return result;
    }
}
