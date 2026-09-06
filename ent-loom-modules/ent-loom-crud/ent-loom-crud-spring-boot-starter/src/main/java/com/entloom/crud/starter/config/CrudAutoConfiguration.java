package com.entloom.crud.starter.config;

import com.entloom.crud.starter.config.CrudCoreConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * starter 自动配置入口。
 */
@Configuration
@AutoConfigureAfter(JdbcTemplateAutoConfiguration.class)
@Import({CrudCoreConfiguration.class, CrudWebAutoConfiguration.class})
public class CrudAutoConfiguration {
}
