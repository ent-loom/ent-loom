package com.entloom.meta.adapter.crud;

import com.entloom.crud.annotations.EntCrudEntity;
import com.entloom.crud.core.convention.CrudConvention;
import com.entloom.crud.core.convention.CrudConventionProperties;
import com.entloom.meta.annotations.EntEntity;
import com.entloom.meta.annotations.EntField;
import com.entloom.meta.annotations.meta.EntMetaDateTime;
import com.entloom.meta.contract.contribution.Contribution;
import com.entloom.meta.contract.contribution.Priority;
import com.entloom.meta.contract.descriptor.MetaDescriptorProperties;
import com.entloom.meta.contract.diagnostic.DefaultMetaDiagnosticPolicy;
import com.entloom.meta.contract.diagnostic.MetaDiagnostic;
import com.entloom.meta.contract.diagnostic.MetaDiagnosticCode;
import com.entloom.meta.contract.value.MetaValueSource;
import com.entloom.meta.core.convention.MetaConvention;
import com.entloom.meta.enums.EntFieldKind;
import com.entloom.meta.enums.role.DateTimeRole;
import com.entloom.meta.core.parser.ReflectiveEntMetaParser;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class CrudConventionConsumerTest {

    @Test
    void meta_created_time_should_be_projected_as_non_writable() {
        MetaCrudAdapter adapter = new MetaCrudAdapter(
            Collections.<Class<?>>singletonList(MetaCreatedTimeOrder.class),
            new ReflectiveEntMetaParser()
        );

        Assertions.assertFalse(
            adapter.runtimeModel().getEntity(MetaCreatedTimeOrder.class)
                .getField("createdAt")
                .isWritable()
        );
    }

    @Test
    void native_and_meta_candidates_should_share_priority_contract() {
        CrudConvention projectConvention = context -> Arrays.asList(Contribution.<Boolean>builder()
            .target(context.entityClass().getName() + "#" + context.field().getName())
            .property(CrudConventionProperties.WRITABLE)
            .value(Boolean.TRUE)
            .source(MetaValueSource.MODULE_PROJECT_CONVENTION)
            .ruleId("test.project.created-time.writable")
            .priority(Priority.MODULE_PROJECT_CONVENTION)
            .build());

        MetaCrudAdapter adapter = new MetaCrudAdapter(
            Collections.<Class<?>>singletonList(MetaAndNativeCreatedTimeOrder.class),
            new ReflectiveEntMetaParser(),
            Collections.singletonList(projectConvention),
            DefaultMetaDiagnosticPolicy.failFast()
        );

        Assertions.assertTrue(
            adapter.runtimeModel().getEntity(MetaAndNativeCreatedTimeOrder.class)
                .getField("createdAt")
                .isWritable()
        );
    }

    @Test
    void meta_read_only_rule_id_should_participate_in_crud_tie_breaking() {
        MetaConvention metaConvention = context -> Arrays.asList(Contribution.<Boolean>builder()
            .target(context.entityClass().getName() + "#" + context.field().getName())
            .property(MetaDescriptorProperties.READ_ONLY)
            .value(Boolean.TRUE)
            .source(MetaValueSource.META_PROJECT_CONVENTION)
            .ruleId("z-meta-read-only")
            .priority(Priority.META_PROJECT_CONVENTION)
            .build());
        CrudConvention nativeConvention = context -> Arrays.asList(Contribution.<Boolean>builder()
            .target(context.entityClass().getName() + "#" + context.field().getName())
            .property(CrudConventionProperties.WRITABLE)
            .value(Boolean.TRUE)
            .source(MetaValueSource.META_PROJECT_CONVENTION)
            .ruleId("n-native-writable")
            .priority(Priority.META_PROJECT_CONVENTION)
            .build());

        MetaCrudAdapter adapter = new MetaCrudAdapter(
            Collections.<Class<?>>singletonList(MetaAndNativeCreatedTimeOrder.class),
            new ReflectiveEntMetaParser(Collections.singletonList(metaConvention)),
            Collections.singletonList(nativeConvention),
            DefaultMetaDiagnosticPolicy.lenient()
        );

        Assertions.assertTrue(
            adapter.runtimeModel().getEntity(MetaAndNativeCreatedTimeOrder.class)
                .getField("createdAt")
                .isWritable()
        );
    }

    @Test
    void unsupported_datetime_role_should_be_structured_warning() {
        MetaCrudAdapter adapter = new MetaCrudAdapter(
            Collections.<Class<?>>singletonList(UnsupportedDateTimeOrder.class),
            new ReflectiveEntMetaParser(),
            DefaultMetaDiagnosticPolicy.failFast()
        );

        MetaDiagnostic diagnostic = find(adapter.diagnostics(), MetaDiagnosticCode.CONSUMER_UNSUPPORTED_PROPERTY);
        Assertions.assertNotNull(diagnostic);
        Assertions.assertEquals("role", diagnostic.property());
    }

    private MetaDiagnostic find(List<MetaDiagnostic> diagnostics, MetaDiagnosticCode code) {
        for (MetaDiagnostic diagnostic : diagnostics) {
            if (diagnostic.code() == code) {
                return diagnostic;
            }
        }
        return null;
    }

    @EntEntity(entity = "meta_created_time_order")
    private static final class MetaCreatedTimeOrder {
        private Long id;
        private LocalDateTime createdAt;
    }

    @EntEntity(entity = "meta_native_created_time_order")
    @EntCrudEntity(name = "meta_native_created_time_order")
    private static final class MetaAndNativeCreatedTimeOrder {
        private Long id;
        private LocalDateTime createdAt;
    }

    @EntEntity(entity = "unsupported_datetime_order")
    private static final class UnsupportedDateTimeOrder {
        @EntField(EntFieldKind.DATETIME)
        @EntMetaDateTime(DateTimeRole.UPDATED_TIME)
        private LocalDateTime updatedAt;
    }
}
