package com.entloom.meta.core.convention;

import com.entloom.meta.contract.contribution.Contribution;
import com.entloom.meta.contract.contribution.Priority;
import com.entloom.meta.contract.value.MetaValueSource;
import com.entloom.meta.contract.descriptor.MetaDescriptorProperties;
import com.entloom.meta.contract.convention.CreatedTimeFields;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Meta 内置创建时间约定。
 */
public final class BuiltInDateTimeConvention implements MetaConvention {
    private final CreatedTimeFields createdTimeFields;

    public BuiltInDateTimeConvention() {
        this(new CreatedTimeFields());
    }

    public BuiltInDateTimeConvention(CreatedTimeFields createdTimeFields) {
        this.createdTimeFields = java.util.Objects.requireNonNull(createdTimeFields, "创建时间字段约定不能为空");
    }

    @Override
    public Collection<? extends Contribution<?>> contribute(MetaConventionContext context) {
        String fieldName = context.field().getName();
        if (!createdTimeFields.matches(fieldName, context.field().getType())) {
            return Collections.emptyList();
        }
        String target = context.entityClass().getName() + "#" + fieldName;
        List<Contribution<?>> contributions = new ArrayList<Contribution<?>>();
        contributions.add(contribution(
            target,
            MetaDescriptorProperties.ROLE,
            "DATETIME.CREATED_TIME",
            "meta.datetime.created-time.role"
        ));
        contributions.add(contribution(
            target,
            MetaDescriptorProperties.LABEL,
            "创建时间",
            "meta.datetime.created-time.label"
        ));
        contributions.add(contribution(
            target,
            MetaDescriptorProperties.READ_ONLY,
            Boolean.TRUE,
            "meta.datetime.created-time.read-only"
        ));
        return contributions;
    }

    private static <T> Contribution<T> contribution(
        String target,
        String property,
        T value,
        String ruleId
    ) {
        return Contribution.<T>builder()
            .target(target)
            .property(property)
            .value(value)
            .source(MetaValueSource.META_BUILT_IN_CONVENTION)
            .ruleId(ruleId)
            .priority(Priority.META_BUILT_IN_CONVENTION)
            .build();
    }
}
