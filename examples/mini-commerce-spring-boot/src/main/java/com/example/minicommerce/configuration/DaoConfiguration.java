package com.example.minicommerce.configuration;

import com.entloom.crud.core.capability.dao.EntityAccessScope;
import com.entloom.crud.core.capability.dao.EntityDaoScopeResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 示例主数据范围；生产项目应根据可信租户或组织上下文解析。 */
@Configuration(proxyBeanMethods = false)
public class DaoConfiguration {
    @Bean
    public EntityDaoScopeResolver entityDaoScopeResolver() {
        return entityType -> EntityAccessScope.unrestricted();
    }
}
