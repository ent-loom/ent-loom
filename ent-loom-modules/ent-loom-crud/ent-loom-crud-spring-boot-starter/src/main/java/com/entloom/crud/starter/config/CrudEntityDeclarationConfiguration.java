package com.entloom.crud.starter.config;

import com.entloom.declaration.EntityDeclarations;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 实体声明在编译运行模型前加载一次；不依赖实体注册表，避免归属规则装配成环。 */
@Configuration(proxyBeanMethods = false)
@org.springframework.context.annotation.Import(com.entloom.declaration.EntityDeclarationConfiguration.class)
public class CrudEntityDeclarationConfiguration {
    @Bean
    public SmartInitializingSingleton removedGlobalEntityContractGuard(org.springframework.core.env.ConfigurableEnvironment environment) {
        return () -> {
            var prefix = org.springframework.boot.context.properties.source.ConfigurationPropertyName.of("ent.loom.crud.contracts");
            for (var source : org.springframework.boot.context.properties.source.ConfigurationPropertySources.get(environment)) {
                if (source.containsDescendantOf(prefix) == org.springframework.boot.context.properties.source.ConfigurationPropertyState.PRESENT
                    || source.getConfigurationProperty(prefix) != null) {
                    throw new IllegalArgumentException("全局输入契约已删除，请迁移到 ent/entities/<资源码>.yml 的 crud.contracts");
                }
            }
        };
    }

    @Bean
    public SmartInitializingSingleton crudEntityDeclarationResourceGuard(CrudEntityDeclarations declarations,
            ObjectProvider<EntityMetaRegistry> entities) {
        return () -> {
            EntityMetaRegistry registry = entities.getIfAvailable();
            if (registry != null) declarations.validate(registry);
            else if (!declarations.options().isEmpty()) throw new IllegalArgumentException(
                "实体声明未被消费：CRUD 实体注册表不存在，资源=" + declarations.options().keySet());
        };
    }

    @Bean
    @ConditionalOnProperty(name = "ent.loom.crud.controller.enabled", havingValue = "true")
    public SmartInitializingSingleton entityDeclarationHttpGuard(
        CrudEntityDeclarations declarations,
        ObjectProvider<EntityMetaRegistry> entities) {
        return () -> {
            var registry = entities.getIfAvailable();
            if (registry != null) declarations.assertHttpAccess(registry);
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public CrudEntityDeclarations crudEntityDeclarations(EntityDeclarations declarations) {
        return new CrudEntityDeclarations(declarations);
    }
}
