package com.entloom.meta.starter;

import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.ddl.api.DdlGenerationDefaults;
import com.entloom.ddl.api.MetadataLoader;
import com.entloom.ddl.spring.SpringPackageEntityClassResolver;
import com.entloom.ddl.starter.EntDdlProperties;
import com.entloom.meta.core.parser.EntMetaParser;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ResourceLoader;

/** DDL 存在时，使用共享 Meta 解析器投影建表模型。 */
@AutoConfiguration(after = EntLoomMetaAutoConfiguration.class,
    beforeName = "com.entloom.ddl.starter.EntDdlAutoConfiguration")
@ConditionalOnClass(EntDdlProperties.class)
@ConditionalOnProperty(prefix = "ent.loom.meta", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(prefix = "ent.loom.meta.ddl", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties({EntLoomMetaProperties.class, EntDdlProperties.class})
public class EntLoomMetaDdlAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(MetadataLoader.class)
    public MetadataLoader entLoomMetaDdlMetadataLoader(
        EntLoomMetaProperties properties,
        EntDdlProperties ddlProperties,
        EntMetaParser parser,
        ResourceLoader resourceLoader,
        BeanFactory beanFactory,
        ObjectProvider<EntityMetaRegistry> registryProvider
    ) {
        return new MetaDdlMetadataLoader(
            EntLoomMetaAutoConfiguration.resolveEntityClasses(properties, resourceLoader, beanFactory),
            new SpringPackageEntityClassResolver(resourceLoader.getClassLoader()),
            parser,
            new DdlGenerationDefaults(ddlProperties.getDefaults().getGenerationStrategy()),
            EntLoomMetaAutoConfiguration.diagnosticPolicy(properties),
            registryProvider
        );
    }
}
