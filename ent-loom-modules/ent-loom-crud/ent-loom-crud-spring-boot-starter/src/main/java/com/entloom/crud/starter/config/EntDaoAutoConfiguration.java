package com.entloom.crud.starter.config;

import com.entloom.crud.core.capability.dao.EntityDaoFactory;
import com.entloom.crud.core.capability.dao.EntityDaoScopeResolver;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.starter.dao.EntDaoScannerConfigurer;
import com.entloom.crud.starter.dao.EntityDaoBeanRegistrar;
import java.util.Collections;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/** 按实体注册基础 DAO，并扫描自定义接口；显式 EntDaoScan 优先。 */
@AutoConfiguration(after = CrudAutoConfiguration.class)
public class EntDaoAutoConfiguration {
    @Bean
    @ConditionalOnBean({EntityMetaRegistry.class, EntityDaoFactory.class, EntityDaoScopeResolver.class})
    public EntityDaoBeanRegistrar entityDaoBeanRegistrar() {
        return new EntityDaoBeanRegistrar();
    }

    @Bean
    @ConditionalOnMissingBean(EntDaoScannerConfigurer.class)
    public static EntDaoScannerConfigurer entDaoScannerConfigurer() {
        return new EntDaoScannerConfigurer(Collections.emptyList());
    }
}
