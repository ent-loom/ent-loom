package com.entloom.crud.starter.config;

import com.entloom.crud.spring.config.CrudCoreConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * starter 自动配置入口。
 */
@Configuration
@Import({CrudCoreConfiguration.class, CrudWebAutoConfiguration.class})
public class CrudAutoConfiguration {
}
