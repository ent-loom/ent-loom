package com.entloom.crud.starter.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** CRUD 统一严格绑定一次，子配置共享同一实例，避免父前缀忽略拼写错误。 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CrudProperties.class)
public class CrudPropertiesConfiguration {
    @Bean
    public CrudExampleProperties crudExampleProperties(CrudProperties properties) {
        return properties.getExample();
    }
}
