package com.entloom.meta.starter.doc;

import com.entloom.crud.core.governance.subject.CrudSubjectResolver;
import com.entloom.meta.adapter.doc.MetaDocAdapter;
import com.entloom.meta.starter.EntLoomMetaAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 实体文档契约服务自动配置。
 */
@Configuration
@AutoConfigureAfter(EntLoomMetaAutoConfiguration.class)
@ConditionalOnClass(MetaDocAdapter.class)
@ConditionalOnProperty(prefix = "entloom.doc.contract", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(EntityDocumentationContractProperties.class)
public class EntityDocumentationContractAutoConfiguration {

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
}
