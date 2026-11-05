package com.entloom.meta.adapter.doc;

import com.entloom.base.common.OptionalBoolean;
import com.entloom.base.util.reflect.EntityProperties;
import com.entloom.doc.annotations.EntDocEntity;
import com.entloom.doc.annotations.EntDocField;
import com.entloom.doc.core.spi.DocEntityOverride;
import com.entloom.doc.core.spi.DocFieldOverride;
import com.entloom.doc.core.spi.DocOverrideProvider;
import java.util.Arrays;

/** 实体展示差异与字段同源声明，复杂覆盖仍由业务提供器处理。 */
final class AnnotationDocOverrides implements DocOverrideProvider {
    @Override
    public DocEntityOverride overrideFor(Class<?> type, String resourceCode) {
        DocEntityOverride.Builder builder = DocEntityOverride.builder();
        EntDocEntity entity = type.getAnnotation(EntDocEntity.class);
        if (entity != null) {
            builder.group(value(entity.group())).remark(value(entity.remark()))
                .hidden(booleanValue(entity.hidden())).visibleFor(Arrays.asList(entity.visibleFor()));
        }
        for (java.lang.reflect.Field field : EntityProperties.fields(type)) {
            EntDocField annotation = field.getAnnotation(EntDocField.class);
            if (annotation == null) continue;
            builder.field(DocFieldOverride.builder(field.getName())
                .example(value(annotation.example())).examples(Arrays.asList(annotation.examples()))
                .group(value(annotation.group())).remark(value(annotation.remark()))
                .hidden(booleanValue(annotation.hidden())).visibleFor(Arrays.asList(annotation.visibleFor()))
                .targetEntityLabel(value(annotation.targetEntityLabel())).relationRemark(value(annotation.relationRemark()))
                .build());
        }
        return builder.build();
    }

    private static String value(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private static Boolean booleanValue(OptionalBoolean value) {
        return value == OptionalBoolean.UNSET ? null : Boolean.valueOf(value == OptionalBoolean.TRUE);
    }
}
