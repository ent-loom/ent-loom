package com.entloom.crud.spring.config;

import com.entloom.crud.spring.config.module.CrudCommandEngineConfiguration;
import com.entloom.crud.spring.config.module.CrudCommonConfiguration;
import com.entloom.crud.spring.config.module.CrudGatewayConfiguration;
import com.entloom.crud.spring.config.module.CrudIdempotencyConfiguration;
import com.entloom.crud.spring.config.module.CrudQueryEngineConfiguration;
import com.entloom.crud.spring.config.module.CrudSqlSecurityConfiguration;
import com.entloom.crud.spring.config.module.CrudStatsEngineConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * ent-loom-crud 核心装配入口。
 *
 * <p>该类仅负责聚合各子配置，避免单个配置类承担过多职责。
 *
 * <p>导入顺序按依赖关系组织：公共能力 -> 幂等/安全 -> 引擎 -> 网关。
 */
@Configuration
@EnableConfigurationProperties(CrudProperties.class)
@Import({
    CrudCommonConfiguration.class,
    CrudIdempotencyConfiguration.class,
    CrudSqlSecurityConfiguration.class,
    CrudStatsEngineConfiguration.class,
    CrudQueryEngineConfiguration.class,
    CrudCommandEngineConfiguration.class,
    CrudGatewayConfiguration.class
})
public class CrudCoreConfiguration {
}
