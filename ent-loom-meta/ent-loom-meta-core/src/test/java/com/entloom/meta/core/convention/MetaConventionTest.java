package com.entloom.meta.core.convention;

import com.entloom.base.common.OptionalBoolean;
import com.entloom.meta.annotations.EntEntity;
import com.entloom.meta.annotations.EntField;
import com.entloom.meta.annotations.meta.EntMetaDateTime;
import com.entloom.meta.contract.descriptor.EntEntityDescriptor;
import com.entloom.meta.contract.descriptor.EntFieldDescriptor;
import com.entloom.meta.contract.descriptor.MetaDescriptorProperties;
import com.entloom.meta.contract.contribution.Priority;
import com.entloom.meta.contract.value.MetaValueSource;
import com.entloom.meta.core.parser.ReflectiveEntMetaParser;
import com.entloom.meta.enums.EntFieldKind;
import com.entloom.meta.enums.role.DateTimeRole;
import java.time.LocalDateTime;
import java.util.Arrays;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class MetaConventionTest {

    @Test
    void built_in_datetime_convention_should_describe_unannotated_created_at() {
        EntEntityDescriptor descriptor = new ReflectiveEntMetaParser().parse(ConventionEntity.class);
        EntFieldDescriptor field = field(descriptor, "createdAt");

        Assertions.assertEquals("DATETIME", field.fieldKind());
        Assertions.assertEquals("DATETIME.CREATED_TIME", field.role());
        Assertions.assertEquals("创建时间", field.label());
        Assertions.assertEquals(Boolean.TRUE, field.readOnly());
        Assertions.assertEquals(
            MetaValueSource.META_BUILT_IN_CONVENTION,
            field.sourcedValue(MetaDescriptorProperties.ROLE).source()
        );
    }

    @Test
    void explicit_meta_annotation_should_override_only_the_contributed_properties() {
        EntEntityDescriptor descriptor = new ReflectiveEntMetaParser().parse(ConventionEntity.class);
        EntFieldDescriptor field = field(descriptor, "createTime");

        Assertions.assertEquals("DATETIME.UPDATED_TIME", field.role());
        Assertions.assertEquals("更新时间", field.label());
        Assertions.assertEquals(Boolean.FALSE, field.readOnly());
        Assertions.assertEquals(MetaValueSource.META_EXPLICIT,
            field.sourcedValue(MetaDescriptorProperties.ROLE).source());
        Assertions.assertEquals(MetaValueSource.META_EXPLICIT,
            field.sourcedValue(MetaDescriptorProperties.LABEL).source());
        Assertions.assertEquals(MetaValueSource.META_EXPLICIT,
            field.sourcedValue(MetaDescriptorProperties.READ_ONLY).source());
    }

    @Test
    void convention_collection_order_should_not_change_rule_selection() {
        MetaConvention first = context -> Arrays.asList(ContributionFixtures.label(
            context,
            "z-rule",
            "Z",
            MetaValueSource.META_BUILT_IN_CONVENTION,
            Priority.META_BUILT_IN_CONVENTION
        ));
        MetaConvention second = context -> Arrays.asList(ContributionFixtures.label(
            context,
            "a-rule",
            "A",
            MetaValueSource.META_PROJECT_CONVENTION,
            Priority.META_PROJECT_CONVENTION
        ));

        EntFieldDescriptor field = field(
            new ReflectiveEntMetaParser(Arrays.asList(first, second)).parse(CustomConventionEntity.class),
            "name"
        );

        Assertions.assertEquals("A", field.label());
    }

    private static EntFieldDescriptor field(EntEntityDescriptor descriptor, String name) {
        for (EntFieldDescriptor field : descriptor.fields()) {
            if (name.equals(field.fieldName())) {
                return field;
            }
        }
        Assertions.fail("Missing field: " + name);
        return null;
    }

    @EntEntity(entity = "convention_entity")
    private static final class ConventionEntity {
        private LocalDateTime createdAt;

        @EntField(value = EntFieldKind.DATETIME, label = "更新时间", readOnly = OptionalBoolean.FALSE)
        @EntMetaDateTime(DateTimeRole.UPDATED_TIME)
        private LocalDateTime createTime;
    }

    @EntEntity(entity = "custom_convention_entity")
    private static final class CustomConventionEntity {
        private String name;
    }

    private static final class ContributionFixtures {
        private static com.entloom.meta.contract.contribution.Contribution<String> label(
            MetaConventionContext context,
            String ruleId,
            String value,
            MetaValueSource source,
            Priority priority
        ) {
            return com.entloom.meta.contract.contribution.Contribution.<String>builder()
                .target(context.entityClass().getName() + "#" + context.field().getName())
                .property(MetaDescriptorProperties.LABEL)
                .value(value)
                .source(source)
                .ruleId(ruleId)
                .priority(priority)
                .build();
        }
    }
}
