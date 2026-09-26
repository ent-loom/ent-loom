package com.entloom.crud.core.governance.scope;

import com.entloom.crud.api.enums.CrudOperationDomain;
import com.entloom.crud.api.model.SubjectContext;
import com.entloom.crud.core.exception.DataScopeDeniedException;
import com.entloom.crud.core.governance.model.CrudResourceAction;
import com.entloom.crud.core.runtime.meta.EntityFieldMeta;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.spec.BaseSpec;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 按实体和可信业务入口声明读取条件；配置和 Java 声明共用此实现。 */
public final class ReadVisibilityContributor implements CrudDataScopeContributor {
    private final Map<String, Map<String, CrudReadScope>> bindings;

    public ReadVisibilityContributor(EntityMetaRegistry registry,
            Map<Class<?>, Map<String, Map<String, Object>>> visibility) {
        if (visibility == null) throw new IllegalArgumentException("读取可见性声明不能为空");
        Map<String, Map<String, CrudReadScope>> compiled = new LinkedHashMap<String, Map<String, CrudReadScope>>();
        for (Map.Entry<Class<?>, Map<String, Map<String, Object>>> entity : visibility.entrySet()) {
            if (entity.getKey() == null || entity.getValue() == null) {
                throw new IllegalArgumentException("实体及可见性入口不能为空");
            }
            EntityMeta meta = registry.getEntityMeta(entity.getKey());
            Map<String, CrudReadScope> entries = new LinkedHashMap<String, CrudReadScope>();
            for (Map.Entry<String, Map<String, Object>> entry : entity.getValue().entrySet()) {
                String accessEntry = requireText(entry.getKey(), "业务入口");
                if (entry.getValue() == null) throw new IllegalArgumentException("入口必须显式声明条件，空映射表示不追加限制");
                Map<String, Object> conditions = new LinkedHashMap<String, Object>();
                for (Map.Entry<String, Object> condition : CrudReadScope.allow(entry.getValue()).getConditions().entrySet()) {
                    EntityFieldMeta field = meta.resolveFieldMeta(condition.getKey());
                    if (field == null || field.isRelation() || meta.resolveColumn(condition.getKey()) == null) {
                        throw new IllegalArgumentException("可见性条件必须使用实体持久化字段: " + condition.getKey());
                    }
                    conditions.put(condition.getKey(), normalize(condition.getValue(), field.getJavaType()));
                }
                entries.put(accessEntry, CrudReadScope.allow(conditions));
            }
            compiled.put(entity.getKey().getName(), Collections.unmodifiableMap(entries));
        }
        this.bindings = Collections.unmodifiableMap(compiled);
    }

    @Override
    public boolean supports(CrudResourceAction action, BaseSpec spec) {
        return action != null && (action.getOperationDomain() == CrudOperationDomain.QUERY
            || action.getOperationDomain() == CrudOperationDomain.STATS
            || action.getOperationDomain() == CrudOperationDomain.EXPORT);
    }

    @Override
    public CrudDataScope contribute(CrudResourceAction action, SubjectContext subject, BaseSpec spec, CrudDataScope grantedScope) {
        Map<String, CrudReadScope> resolved = new LinkedHashMap<String, CrudReadScope>();
        for (Map.Entry<String, Map<String, CrudReadScope>> entity : bindings.entrySet()) {
            CrudReadScope scope = entity.getValue().get(action.getAccessEntry());
            resolved.put(entity.getKey(), scope == null ? CrudReadScope.deny() : scope);
        }
        CrudDataScope result = CrudDataScope.allowAll().withReadScopes(resolved);
        CrudReadScope root = result.readScope(spec.getRootType());
        if (root != null && !root.isAllowed()) {
            throw new DataScopeDeniedException("实体未配置当前业务入口的读取可见性: " + action.getResource());
        }
        return result;
    }

    private static String requireText(String value, String label) {
        if (value == null || value.trim().isEmpty() || !value.equals(value.trim())) {
            throw new IllegalArgumentException(label + "不能为空或包含首尾空格");
        }
        return value;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object normalize(Object value, Class<?> type) {
        if (value instanceof Collection<?>) {
            List<Object> result = new ArrayList<Object>();
            for (Object item : (Collection<?>) value) result.add(normalize(item, type));
            return result;
        }
        if (type.isEnum()) return Enum.valueOf((Class) type, value.toString()).name();
        if (type == Boolean.class || type == boolean.class) {
            if (value instanceof Boolean) return value;
            if ("true".equals(value) || "false".equals(value)) return Boolean.valueOf(value.toString());
            throw new IllegalArgumentException("布尔可见性条件仅允许 true/false");
        }
        if (type == String.class && value instanceof String) return value;
        try {
            BigDecimal number = new BigDecimal(value.toString());
            if (type == Long.class || type == long.class) return number.longValueExact();
            if (type == Integer.class || type == int.class) return number.intValueExact();
            if (type == Short.class || type == short.class) return number.shortValueExact();
            if (type == Byte.class || type == byte.class) return number.byteValueExact();
            if (type == BigDecimal.class) return number;
            if (type == java.math.BigInteger.class) return number.toBigIntegerExact();
            if (type == Double.class || type == double.class) return number.doubleValue();
            if (type == Float.class || type == float.class) return number.floatValue();
        } catch (NumberFormatException | ArithmeticException ex) {
            throw new IllegalArgumentException("可见性条件与字段类型不匹配: " + type.getName(), ex);
        }
        throw new IllegalArgumentException("不支持的可见性字段类型: " + type.getName());
    }

}
