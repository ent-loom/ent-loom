package com.entloom.crud.starter.config;

import com.entloom.crud.starter.dao.EntDaoScannerConfigurer;
import java.util.Collections;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/** 默认扫描 Spring Boot 应用包；显式 EntDaoScan 优先。 */
@AutoConfiguration(after = CrudAutoConfiguration.class)
public class EntDaoAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(EntDaoScannerConfigurer.class)
    public static EntDaoScannerConfigurer entDaoScannerConfigurer() {
        return new EntDaoScannerConfigurer(Collections.emptyList());
    }
}
