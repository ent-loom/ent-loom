package com.entloom.crud.starter.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** CRUD 统一严格绑定一次，子配置共享同一实例，避免父前缀忽略拼写错误。 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CrudProperties.class)
public class CrudPropertiesConfiguration {
    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean(CrudAccessProperties.class)
    public CrudAccessProperties crudAccessProperties(CrudProperties properties) {
        return properties.getAccess();
    }

    @Bean
    public org.springframework.beans.factory.SmartInitializingSingleton crudAccessSourceGuard(
            CrudProperties properties, CrudAccessProperties access) {
        return () -> {
            CrudAccessProperties configured = properties.getAccess();
            if (access != configured && (!configured.getIdentities().isEmpty() || !configured.getRelations().isEmpty()
                    || !configured.getPolicies().isEmpty() || configured.getDefaultPolicy() != null
                    || configured.getDefaults().getActions() != null || !configured.getDefaults().getAccess().isEmpty())) {
                throw new IllegalArgumentException("CRUD 访问策略不能同时使用应用配置和 CrudAccessProperties Bean");
            }
        };
    }

    @Bean
    public CrudExampleProperties crudExampleProperties(CrudProperties properties) {
        return properties.getExample();
    }
}
