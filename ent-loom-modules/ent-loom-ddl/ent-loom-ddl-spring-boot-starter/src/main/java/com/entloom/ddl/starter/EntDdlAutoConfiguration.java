package com.entloom.ddl.starter;

import com.entloom.ddl.api.DdlEngine;
import com.entloom.ddl.api.MetadataLoader;
import com.entloom.ddl.api.QueryStrategy;
import com.entloom.ddl.api.SqlExecutor;
import com.entloom.ddl.core.DefaultDdlEngine;
import com.entloom.ddl.spring.EntDdlSpringExecutor;
import com.entloom.ddl.spring.EntDdlSpringOptions;
import com.entloom.ddl.spring.SpringAnnotationMetadataLoader;
import com.entloom.ddl.spring.SpringJdbcQueryStrategy;
import com.entloom.ddl.spring.SpringJdbcSqlExecutor;
import com.entloom.ddl.spring.SpringPackageEntityClassResolver;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * ent-loom-ddl Spring Boot 自动配置。
 */
@AutoConfiguration
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
    public MetadataLoader entDdlMetadataLoader() {
        return new SpringAnnotationMetadataLoader(new SpringPackageEntityClassResolver(null));
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnMissingBean
    public QueryStrategy entDdlQueryStrategy(DataSource dataSource) {
        return new SpringJdbcQueryStrategy(dataSource);
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnMissingBean
    public SqlExecutor entDdlSqlExecutor(DataSource dataSource) {
        return new SpringJdbcSqlExecutor(dataSource);
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
                                                     ObjectProvider<QueryStrategy> queryStrategyProvider,
                                                     ObjectProvider<SqlExecutor> sqlExecutorProvider,
                                                     ObjectProvider<EntDdlSpringOptions> optionsProvider) {
        EntDdlSpringOptions options = optionsProvider.getIfAvailable(EntDdlSpringOptions::new);
        return new EntDdlSpringExecutor(ddlEngine, metadataLoader,
                queryStrategyProvider.getIfAvailable(), sqlExecutorProvider.getIfAvailable(), options);
    }

    private List<Class<?>> resolveClasses(List<String> classNames) {
        if (classNames == null || classNames.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, Class<?>> classesByName = new TreeMap<String, Class<?>>();
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = EntDdlAutoConfiguration.class.getClassLoader();
        }
        for (String className : classNames) {
            if (className == null || className.trim().isEmpty()) {
                continue;
            }
            try {
                Class<?> entityClass = Class.forName(className.trim(), false, classLoader);
                classesByName.putIfAbsent(entityClass.getName(), entityClass);
            } catch (ClassNotFoundException | LinkageError | RuntimeException ignored) {
                // 外部配置中的不可加载类不应阻断其他显式实体。
            }
        }
        return new ArrayList<Class<?>>(classesByName.values());
    }
}
