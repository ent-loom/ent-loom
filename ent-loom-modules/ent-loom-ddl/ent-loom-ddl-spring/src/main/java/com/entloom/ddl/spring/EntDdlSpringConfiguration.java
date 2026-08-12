package com.entloom.ddl.spring;

import com.entloom.ddl.api.DdlEngine;
import com.entloom.ddl.api.MetadataLoader;
import com.entloom.ddl.api.QueryStrategy;
import com.entloom.ddl.api.SqlExecutor;
import com.entloom.ddl.core.DefaultDdlEngine;
import com.entloom.ddl.core.NoopQueryStrategy;
import com.entloom.ddl.core.NoopSqlExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * DDL Spring 适配配置。
 */
@Configuration
public class EntDdlSpringConfiguration {
    @Bean
    public EntDdlSpringOptions entDdlSpringOptions() {
        return new EntDdlSpringOptions();
    }

    @Bean
    public DdlEngine entDdlEngine() {
        return new DefaultDdlEngine();
    }

    @Bean
    public QueryStrategy entDdlQueryStrategy() {
        return new NoopQueryStrategy();
    }

    @Bean
    public SqlExecutor entDdlSqlExecutor() {
        return new NoopSqlExecutor();
    }

    @Bean
    public MetadataLoader entDdlMetadataLoader() {
        return new SpringAnnotationMetadataLoader(new SpringPackageEntityClassResolver(null));
    }

    @Bean
    public EntDdlSpringExecutor entDdlSpringExecutor(DdlEngine ddlEngine,
                                                     MetadataLoader metadataLoader,
                                                     QueryStrategy queryStrategy,
                                                     SqlExecutor sqlExecutor,
                                                     EntDdlSpringOptions options) {
        return new EntDdlSpringExecutor(ddlEngine, metadataLoader, queryStrategy, sqlExecutor, options);
    }
}
