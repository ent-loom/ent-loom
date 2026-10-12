package com.entloom.crud.starter.config;

import com.entloom.crud.core.governance.identity.BusinessIdentityRegistration;
import com.entloom.crud.core.governance.identity.BusinessIdentityRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 策略、MVC 和演示账号共用唯一身份装配入口。 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CrudAccessProperties.class)
public class CrudIdentityConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public ConfiguredBusinessIdentities configuredBusinessIdentities(CrudAccessProperties properties) {
        return new ConfiguredBusinessIdentities(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public BusinessIdentityRegistry businessIdentityRegistry(ConfiguredBusinessIdentities identities,
        ObjectProvider<BusinessIdentityRegistration<?>> registrations, ObjectProvider<ConfiguredExampleAccounts> accounts) {
        return new BusinessIdentityRegistry(identities.registrations(registrations.orderedStream().toList(), accounts.getIfAvailable()));
    }
}
