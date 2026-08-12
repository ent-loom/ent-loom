package com.entloom.crud.core.governance.scope;

import com.entloom.crud.api.model.SubjectContext;
import com.entloom.crud.core.governance.model.CrudResourceAction;
import com.entloom.crud.core.capability.exporting.ExportSpec;
import com.entloom.crud.core.capability.importing.ImportSpec;
import com.entloom.crud.core.runtime.spec.BaseSpec;
import com.entloom.crud.core.runtime.spec.CrudSpecAttributeKeys;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 默认 mandatory 范围解析器。
 *
 * <p>优先读取 spec 中显式透传的范围，其次退化为 subject 的 org/tenant 维度。</p>
 */
public class DefaultCrudDataScopeResolver implements CrudDataScopeResolver {
    @Override
    public CrudDataScope resolveQueryScope(CrudResourceAction action, SubjectContext subject, QuerySpec<?> spec) {
        return resolveScope(subject, spec == null ? null : spec.getAttributes());
    }

    @Override
    public CrudDataScope resolveCommandScope(CrudResourceAction action, SubjectContext subject, CommandSpec<?> spec) {
        return resolveScope(subject, spec == null ? null : spec.getAttributes());
    }

    @Override
    public CrudDataScope resolveStatsScope(CrudResourceAction action, SubjectContext subject, BaseSpec spec) {
        return resolveScope(subject, spec == null ? null : spec.getAttributes());
    }

    @Override
    public CrudDataScope resolveImportScope(CrudResourceAction action, SubjectContext subject, ImportSpec spec) {
        return resolveScope(subject, spec == null ? null : spec.getAttributes());
    }

    @Override
    public CrudDataScope resolveExportScope(CrudResourceAction action, SubjectContext subject, ExportSpec spec) {
        return resolveScope(subject, spec == null ? null : spec.getAttributes());
    }

    /**
     * 解析主体和属性中的数据范围。
     */
    @SuppressWarnings("unchecked")
    private CrudDataScope resolveScope(SubjectContext subject, Map<String, Object> attrs) {
        if (attrs != null) {
            Object scopeValue = attrs.get(CrudSpecAttributeKeys.CRUD_DATA_SCOPE);
            if (scopeValue instanceof CrudDataScope) {
                return (CrudDataScope) scopeValue;
            }
            Object explicitAll = attrs.get(CrudSpecAttributeKeys.CRUD_EXPLICIT_ALL);
            if (Boolean.TRUE.equals(explicitAll)) {
                return CrudDataScope.allowAll();
            }
            Object dimensions = attrs.get(CrudSpecAttributeKeys.CRUD_DATA_SCOPE_DIMENSIONS);
            if (dimensions instanceof Map<?, ?>) {
                Map<String, Object> result = new LinkedHashMap<String, Object>();
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) dimensions).entrySet()) {
                    result.put(String.valueOf(entry.getKey()), entry.getValue());
                }
                if (!result.isEmpty()) {
                    return CrudDataScope.scoped(result);
                }
            }
        }

        Map<String, Object> dimensions = new LinkedHashMap<String, Object>();
        if (subject != null && subject.getOrgId() != null && !subject.getOrgId().trim().isEmpty()) {
            dimensions.put("orgId", subject.getOrgId());
        } else if (subject != null && subject.getTenantId() != null && !subject.getTenantId().trim().isEmpty()) {
            dimensions.put("tenantId", subject.getTenantId());
        }
        if (dimensions.isEmpty()) {
            return null;
        }
        return CrudDataScope.scoped(dimensions);
    }
}
