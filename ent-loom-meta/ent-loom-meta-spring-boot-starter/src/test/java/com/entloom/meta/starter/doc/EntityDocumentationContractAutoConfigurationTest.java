package com.entloom.meta.starter.doc;

import com.entloom.doc.core.contract.EntityDocumentationExposurePolicy;
import com.entloom.meta.adapter.doc.MetaDocAdapter;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
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

    @Test
    void serviceShouldStayDisabledByDefault() {
        contextRunner.run(context ->
            Assertions.assertFalse(context.containsBean("entityDocumentationContractService"))
        );
    }

    @Test
    void enabledServiceShouldRequireExplicitExposurePolicy() {
        contextRunner
            .withPropertyValues("entloom.doc.contract.enabled=true")
            .run(context -> {
                Assertions.assertNull(context.getStartupFailure());
                Assertions.assertFalse(context.containsBean("entityDocumentationContractService"));
            });
    }

    @Test
    void enabledServiceShouldUseApplicationExposurePolicy() {
        contextRunner
            .withUserConfiguration(ExposurePolicyConfiguration.class)
            .withPropertyValues("entloom.doc.contract.enabled=true")
            .run(context -> {
                Assertions.assertTrue(context.containsBean("entityDocumentationContractService"));

                MetaDocAdapter adapter = context.getBean(MetaDocAdapter.class);
                EntityDocumentationExposurePolicy policy = context.getBean(EntityDocumentationExposurePolicy.class);
                Map<String, Object> contract = new LinkedHashMap<String, Object>();
                contract.put("contractVersion", "1.0.0");
                when(adapter.buildDocumentationContract(isNull(), same(policy))).thenReturn(contract);

                Assertions.assertSame(
                    contract,
                    context.getBean(EntityDocumentationContractService.class).build()
                );
                verify(adapter).buildDocumentationContract(isNull(), same(policy));
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
    static class ExposurePolicyConfiguration {
        @Bean
        EntityDocumentationExposurePolicy entityDocumentationExposurePolicy() {
            return EntityDocumentationExposurePolicy.denyAll();
        }
    }
}
