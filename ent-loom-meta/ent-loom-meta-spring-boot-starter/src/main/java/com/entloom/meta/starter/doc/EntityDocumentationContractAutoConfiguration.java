package com.entloom.meta.starter.doc;

import com.entloom.crud.core.governance.subject.CrudSubjectResolver;
import com.entloom.meta.adapter.doc.MetaDocAdapter;
import com.entloom.meta.starter.EntLoomMetaAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 实体文档契约服务自动配置。
 */
@Configuration
@AutoConfigureAfter(value = EntLoomMetaAutoConfiguration.class,
    name = "com.entloom.crud.starter.config.CrudAutoConfiguration")
@ConditionalOnClass(MetaDocAdapter.class)
@ConditionalOnProperty(prefix = "entloom.doc.contract", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(EntityDocumentationContractProperties.class)
public class EntityDocumentationContractAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(EntityDocumentationExposurePolicyResolver.class)
    @ConditionalOnProperty(prefix = "entloom.doc.contract.exposure", name = "enabled", havingValue = "true")
    public EntityDocumentationExposurePolicyResolver entityDocumentationExposurePolicyResolver(
        EntityDocumentationContractProperties properties
    ) {
        return new ConfiguredEntityDocumentationExposurePolicyResolver(properties.getExposure());
    }

    @Bean
    @ConditionalOnBean({MetaDocAdapter.class, CrudSubjectResolver.class, EntityDocumentationExposurePolicyResolver.class})
    @ConditionalOnMissingBean
    public EntityDocumentationContractService entityDocumentationContractService(
        MetaDocAdapter metaDocAdapter,
        CrudSubjectResolver subjectResolver,
        EntityDocumentationExposurePolicyResolver exposurePolicyResolver
    ) {
        return new EntityDocumentationContractService(metaDocAdapter, subjectResolver, exposurePolicyResolver);
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnProperty(prefix = "entloom.doc.contract.http", name = "enabled", havingValue = "true")
    @ConditionalOnBean(EntityDocumentationContractService.class)
    @ConditionalOnMissingBean
    public EntityDocumentationContractController entityDocumentationContractController(
        EntityDocumentationContractService contractService
    ) {
        return new EntityDocumentationContractController(contractService);
    }
}
