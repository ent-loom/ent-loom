package com.entloom.ddl.starter;

import com.entloom.ddl.api.DdlEngine;
import com.entloom.ddl.api.MetadataLoader;
import com.entloom.ddl.api.QueryStrategy;
import com.entloom.ddl.api.SqlExecutor;
import com.entloom.ddl.core.DefaultDdlEngine;
import com.entloom.ddl.core.NoopQueryStrategy;
import com.entloom.ddl.core.NoopSqlExecutor;
import com.entloom.ddl.spring.EntDdlSpringExecutor;
import com.entloom.ddl.spring.EntDdlSpringOptions;
import com.entloom.ddl.spring.SpringAnnotationMetadataLoader;
import com.entloom.ddl.spring.SpringPackageEntityClassResolver;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ent-loom-ddl Spring Boot 自动配置。
 */
@Configuration
@ConditionalOnClass(EntDdlSpringExecutor.class)
@EnableConfigurationProperties(EntDdlProperties.class)
public class EntDdlAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public DdlEngine entDdlEngine() {
        return new DefaultDdlEngine();
    }

    @Bean
    @ConditionalOnMissingBean
    public QueryStrategy entDdlQueryStrategy() {
        return new NoopQueryStrategy();
    }

    @Bean
    @ConditionalOnMissingBean
    public SqlExecutor entDdlSqlExecutor() {
        return new NoopSqlExecutor();
    }

    @Bean
    @ConditionalOnMissingBean
    public MetadataLoader entDdlMetadataLoader() {
        return new SpringAnnotationMetadataLoader(new SpringPackageEntityClassResolver(null));
    }

    @Bean
    @ConditionalOnMissingBean
    public EntDdlSpringOptions entDdlSpringOptions(EntDdlProperties properties) {
        EntDdlSpringOptions options = new EntDdlSpringOptions();
        options.setEnabled(properties.isEnabled());
        options.setSchema(properties.getSchema());
        options.setCreateDatabaseIfMissing(properties.isCreateDatabaseIfMissing());
        options.setMode(properties.getMode());
        options.setBasePackages(properties.getBasePackages());
        options.setEntityClasses(resolveClasses(properties.getEntityClassNames()));
        return options;
    }

    @Bean
    @ConditionalOnMissingBean
    public EntDdlSpringExecutor entDdlSpringExecutor(DdlEngine ddlEngine,
                                                     MetadataLoader metadataLoader,
                                                     QueryStrategy queryStrategy,
                                                     SqlExecutor sqlExecutor,
                                                     ObjectProvider<EntDdlSpringOptions> optionsProvider) {
        EntDdlSpringOptions options = optionsProvider.getIfAvailable(EntDdlSpringOptions::new);
        return new EntDdlSpringExecutor(ddlEngine, metadataLoader, queryStrategy, sqlExecutor, options);
    }

    private List<Class<?>> resolveClasses(List<String> classNames) {
        List<Class<?>> classes = new ArrayList<Class<?>>();
        if (classNames == null || classNames.isEmpty()) {
            return classes;
        }
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = EntDdlAutoConfiguration.class.getClassLoader();
        }
        for (String className : classNames) {
            if (className == null || className.trim().isEmpty()) {
                continue;
            }
            try {
                classes.add(Class.forName(className.trim(), false, classLoader));
            } catch (ClassNotFoundException ex) {
                throw new IllegalStateException("无法加载 entloom.ddl.entity-class-names 配置的实体类: "
                        + className.trim(), ex);
            }
        }
        return classes;
    }
}
