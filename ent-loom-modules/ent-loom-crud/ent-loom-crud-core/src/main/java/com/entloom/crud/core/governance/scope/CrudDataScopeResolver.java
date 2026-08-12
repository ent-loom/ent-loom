package com.entloom.crud.core.governance.scope;

import com.entloom.crud.api.enums.QueryOperation;
import com.entloom.crud.api.model.SubjectContext;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import com.entloom.crud.core.governance.model.CrudResourceAction;
import com.entloom.crud.core.capability.exporting.ExportSpec;
import com.entloom.crud.core.capability.importing.ImportSpec;
import com.entloom.crud.core.runtime.spec.BaseSpec;
import com.entloom.crud.core.runtime.spec.FilterableSpec;
import com.entloom.crud.core.capability.query.spec.QuerySpec;

/**
 * 数据范围解析器。
 */
public interface CrudDataScopeResolver {
    /**
     * 解析查询数据范围。
     *
     * @param action 资源动作
     * @param subject 主体
     * @param spec 查询 spec
     * @return 数据范围
     */
    CrudDataScope resolveQueryScope(CrudResourceAction action, SubjectContext subject, QuerySpec<?> spec);

    /**
     * 解析写路径数据范围。
     *
     * @param action 资源动作
     * @param subject 主体
     * @param spec 命令 spec
     * @return 数据范围
     */
    CrudDataScope resolveCommandScope(CrudResourceAction action, SubjectContext subject, CommandSpec<?> spec);

    /**
     * 解析统计路径数据范围。
     *
     * <p>默认桥接到既有 Query 数据范围解析逻辑，使仅依赖 filters 的业务解析器可以继续工作。
     * 新实现建议直接覆盖该方法并基于 BaseSpec / FilterableSpec 解析。</p>
     *
     * @param action 资源动作
     * @param subject 主体
     * @param spec stats spec
     * @return 数据范围
     */
    default CrudDataScope resolveStatsScope(CrudResourceAction action, SubjectContext subject, BaseSpec spec) {
        if (spec instanceof QuerySpec<?>) {
            return resolveQueryScope(action, subject, (QuerySpec<?>) spec);
        }
        QuerySpec.Builder<Object> builder = QuerySpec.<Object>builder()
            .rootType(spec == null ? null : spec.getRootType())
            .entityClasses(spec == null ? null : spec.getEntityClasses())
            .scene(spec == null ? null : spec.getScene())
            .subject(subject)
            .attributes(spec == null ? null : spec.getAttributes())
            .op(QueryOperation.LIST)
            .resultType(Object.class);
        if (spec instanceof FilterableSpec) {
            FilterableSpec filterable = (FilterableSpec) spec;
            builder.filters(filterable.getFilters());
            builder.sorts(filterable.getSorts());
            builder.time(filterable.getTime());
        }
        return resolveQueryScope(action, subject, builder.build());
    }

    default CrudDataScope resolveImportScope(CrudResourceAction action, SubjectContext subject, ImportSpec spec) {
        if (spec == null) {
            return resolveCommandScope(action, subject, null);
        }
        return resolveCommandScope(action, subject, CommandSpec.<Object>builder()
            .rootType(spec.getRootType())
            .entityClasses(spec.getEntityClasses())
            .scene(spec.getScene())
            .subject(subject)
            .attributes(spec.getAttributes())
            .payload(spec.getPayload())
            .build());
    }

    default CrudDataScope resolveExportScope(CrudResourceAction action, SubjectContext subject, ExportSpec spec) {
        if (spec == null) {
            return resolveQueryScope(action, subject, null);
        }
        return resolveQueryScope(action, subject, QuerySpec.<Object>builder()
            .rootType(spec.getRootType())
            .entityClasses(spec.getEntityClasses())
            .scene(spec.getScene())
            .subject(subject)
            .attributes(spec.getAttributes())
            .op(QueryOperation.LIST)
            .filters(spec.getFilters())
            .sorts(spec.getSorts())
            .time(spec.getTime())
            .page(spec.getPage())
            .limit(spec.getLimit())
            .selectFields(spec.getFields())
            .resultType(Object.class)
            .build());
    }
}
