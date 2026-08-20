package com.entloom.crud.core.convention;

import com.entloom.meta.contract.contribution.Contribution;
import com.entloom.meta.contract.contribution.Priority;
import com.entloom.meta.contract.value.MetaValueSource;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * CRUD 内置创建时间约定。
 *
 * <p>创建时间默认只允许创建时写入，避免普通更新覆盖审计字段。</p>
 */
public final class BuiltInDateTimeConvention implements CrudConvention {
    @Override
    public Collection<? extends Contribution<?>> contribute(CrudConventionContext context) {
        String fieldName = context.field().getName();
        if (!("createTime".equals(fieldName) || "createdAt".equals(fieldName))
            || !isDateTime(context.field())) {
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
        return contributions;
    }

    private static boolean isDateTime(Field field) {
        Class<?> type = field.getType();
        return LocalDate.class.equals(type)
            || LocalDateTime.class.equals(type)
            || Instant.class.equals(type)
            || Date.class.equals(type);
    }
}
