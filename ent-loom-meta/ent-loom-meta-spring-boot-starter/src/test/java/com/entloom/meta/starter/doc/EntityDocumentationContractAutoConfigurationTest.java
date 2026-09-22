package com.entloom.meta.starter.doc;

import com.entloom.doc.core.contract.EntityDocumentationExposurePolicy;
import com.entloom.crud.api.model.SubjectContext;
import com.entloom.crud.core.governance.subject.CrudSubjectResolver;
import com.entloom.meta.adapter.doc.MetaDocAdapter;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EntityDocumentationContractAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(MetaDocAdapterConfiguration.class)
        .withConfiguration(AutoConfigurations.of(EntityDocumentationContractAutoConfiguration.class));

    private final WebApplicationContextRunner webContextRunner = new WebApplicationContextRunner()
        .withUserConfiguration(MetaDocAdapterConfiguration.class)
        .withConfiguration(AutoConfigurations.of(EntityDocumentationContractAutoConfiguration.class));

    @Test
    void serviceShouldStayDisabledByDefault() {
        contextRunner.run(context ->
            Assertions.assertFalse(context.containsBean("entityDocumentationContractService"))
        );
    }

    @Test
    void enabledServiceShouldRequireSubjectAndPolicyResolver() {
        contextRunner
            .withPropertyValues("ent.loom.doc.contract.enabled=true")
            .run(context -> {
                Assertions.assertNull(context.getStartupFailure());
                Assertions.assertFalse(context.containsBean("entityDocumentationContractService"));
            });
    }

    @Test
    void enabledServiceShouldResolvePolicyFromCurrentSubject() {
        contextRunner
            .withUserConfiguration(SubjectAndPolicyConfiguration.class)
            .withPropertyValues("ent.loom.doc.contract.enabled=true")
            .run(context -> {
                Assertions.assertTrue(context.containsBean("entityDocumentationContractService"));

                MetaDocAdapter adapter = context.getBean(MetaDocAdapter.class);
                Map<String, Object> contract = new LinkedHashMap<String, Object>();
                contract.put("contractVersion", "1.0.0");
                when(adapter.buildDocumentationContract(
                    isNull(),
                    same(SubjectAndPolicyConfiguration.POLICY)
                )).thenReturn(contract);

                Assertions.assertSame(
                    contract,
                    context.getBean(EntityDocumentationContractService.class).build()
                );
                verify(adapter).buildDocumentationContract(isNull(), same(SubjectAndPolicyConfiguration.POLICY));
            });
    }

    @Test
    void serviceShouldRejectNullSubjectBeforePolicyResolution() {
        EntityDocumentationContractService service = new EntityDocumentationContractService(
            Mockito.mock(MetaDocAdapter.class),
            () -> null,
            subject -> EntityDocumentationExposurePolicy.denyAll()
        );

        Assertions.assertThrows(IllegalStateException.class, service::build);
    }

    @Test
    void serviceShouldPassDenyAllPolicyForUnrecognizedSubject() {
        MetaDocAdapter adapter = Mockito.mock(MetaDocAdapter.class);
        CrudSubjectResolver subjectResolver = () -> {
            SubjectContext subject = new SubjectContext();
            subject.setSubjectId("anonymous");
            return subject;
        };
        EntityDocumentationExposurePolicy denyAll = EntityDocumentationExposurePolicy.denyAll();
        EntityDocumentationContractService service = new EntityDocumentationContractService(
            adapter,
            subjectResolver,
            subject -> "doc-reader".equals(subject.getSubjectId()) ?
                SubjectAndPolicyConfiguration.POLICY : denyAll
        );
        when(adapter.buildDocumentationContract(isNull(), same(denyAll)))
            .thenReturn(Map.of("contractVersion", "1.0.0", "entities", java.util.List.of()));

        Assertions.assertEquals(0, ((java.util.List<?>) service.build().get("entities")).size());
        verify(adapter).buildDocumentationContract(isNull(), same(denyAll));
    }

    @Test
    void httpControllerShouldStayDisabledByDefault() {
        webContextRunner
            .withUserConfiguration(SubjectAndPolicyConfiguration.class)
            .withPropertyValues("ent.loom.doc.contract.enabled=true")
            .run(context -> {
                Assertions.assertNull(context.getStartupFailure());
                Assertions.assertFalse(context.containsBean("entityDocumentationContractController"));
            });
    }

    @Test
    void httpControllerShouldRequireContractService() {
        webContextRunner
            .withPropertyValues(
                "ent.loom.doc.contract.enabled=true",
                "ent.loom.doc.contract.http.enabled=true"
            )
            .run(context -> {
                Assertions.assertNull(context.getStartupFailure());
                Assertions.assertFalse(context.containsBean("entityDocumentationContractController"));
            });
    }

    @Test
    void httpControllerShouldRegisterOnlyWhenContractServiceExists() {
        webContextRunner
            .withUserConfiguration(SubjectAndPolicyConfiguration.class)
            .withPropertyValues(
                "ent.loom.doc.contract.enabled=true",
                "ent.loom.doc.contract.http.enabled=true"
            )
            .run(context -> {
                Assertions.assertNull(context.getStartupFailure());
                Assertions.assertTrue(context.containsBean("entityDocumentationContractController"));
            });
    }

    @Configuration
    static class MetaDocAdapterConfiguration {
        @Bean
        MetaDocAdapter metaDocAdapter() {
            return Mockito.mock(MetaDocAdapter.class);
        }
    }

    @Configuration
    static class SubjectAndPolicyConfiguration {
        private static final EntityDocumentationExposurePolicy POLICY = EntityDocumentationExposurePolicy.denyAll();

        @Bean
        CrudSubjectResolver crudSubjectResolver() {
            SubjectContext subject = new SubjectContext();
            subject.setSubjectId("doc-reader");
            return () -> subject;
        }

        @Bean
        EntityDocumentationExposurePolicyResolver entityDocumentationExposurePolicyResolver() {
            return subject -> {
                Assertions.assertEquals("doc-reader", subject.getSubjectId());
                return POLICY;
            };
        }
    }
}
