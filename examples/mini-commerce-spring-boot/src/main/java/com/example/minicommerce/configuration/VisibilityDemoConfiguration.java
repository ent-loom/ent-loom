package com.example.minicommerce.configuration;

import com.entloom.crud.core.adapter.AccessEntryResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** 本地可见性 Demo：用服务端启动配置识别业务入口。 */
@Configuration(proxyBeanMethods = false)
@Profile("visibility")
public class VisibilityDemoConfiguration {
    @Bean
    AccessEntryResolver visibilityDemoAccessEntryResolver(@Value("${mini-commerce.access-entry}") String accessEntry) {
        if (accessEntry.isBlank() || !accessEntry.equals(accessEntry.trim())) {
            throw new IllegalArgumentException("Demo 业务入口不能为空或包含首尾空格");
        }
        return spec -> accessEntry;
    }
}
