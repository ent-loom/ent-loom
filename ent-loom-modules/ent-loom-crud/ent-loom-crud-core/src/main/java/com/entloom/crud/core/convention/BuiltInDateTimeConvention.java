package com.entloom.crud.core.convention;

import com.entloom.meta.contract.contribution.Contribution;
import com.entloom.meta.enums.role.DateTimeRole;
import com.entloom.meta.contract.contribution.Priority;
import com.entloom.meta.contract.value.MetaValueSource;
import com.entloom.meta.contract.convention.CreatedTimeFields;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * CRUD 内置创建时间约定。
 *
 * <p>创建时间默认禁止普通写入，由可信业务逻辑提供审计时间。</p>
 */
public final class BuiltInDateTimeConvention implements CrudConvention {
    private final CreatedTimeFields createdTimeFields;

    public BuiltInDateTimeConvention() {
        this(new CreatedTimeFields());
    }

    public BuiltInDateTimeConvention(CreatedTimeFields createdTimeFields) {
        this.createdTimeFields = java.util.Objects.requireNonNull(createdTimeFields, "创建时间字段约定不能为空");
    }

    @Override
    public Collection<? extends Contribution<?>> contribute(CrudConventionContext context) {
        String fieldName = context.field().getName();
        if (!createdTimeFields.matches(fieldName, context.field().getType())) {
            return Collections.emptyList();
        }
        String target = context.entityClass().getName() + "#" + fieldName;
        List<Contribution<?>> contributions = new ArrayList<Contribution<?>>();
        contributions.add(Contribution.<Boolean>builder()
            .target(target)
            .property(CrudConventionProperties.WRITABLE)
            .value(Boolean.FALSE)
            .source(MetaValueSource.MODULE_BUILT_IN_CONVENTION)
            .ruleId("crud.datetime.created-time.writable")
            .priority(Priority.MODULE_BUILT_IN_CONVENTION)
            .build());
        contributions.add(Contribution.<DateTimeRole>builder()
            .target(target)
            .property(CrudConventionProperties.DATE_TIME_ROLE)
            .value(DateTimeRole.CREATED_TIME)
            .source(MetaValueSource.MODULE_BUILT_IN_CONVENTION)
            .ruleId("crud.datetime.created-time.role")
            .priority(Priority.MODULE_BUILT_IN_CONVENTION)
            .build());
        return contributions;
    }
}
