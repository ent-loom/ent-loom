package com.entloom.meta.starter;

import com.entloom.crud.core.adapter.ResourceCatalogAdapter;
import com.entloom.crud.annotations.EntCrudEntity;
import com.entloom.doc.annotations.EntDocEntity;
import com.entloom.doc.core.spi.DocEntityMetaResolver;
import com.entloom.doc.core.spi.DocOverrideProvider;
import com.entloom.meta.adapter.crud.MetaCrudAdapter;
import com.entloom.meta.adapter.doc.MetaDocAdapter;
import com.entloom.meta.annotations.EntEntity;
import com.entloom.meta.contract.diagnostic.DefaultMetaDiagnosticPolicy;
import com.entloom.meta.contract.diagnostic.MetaDiagnosticPolicy;
import com.entloom.meta.core.parser.EntMetaParser;
import com.entloom.meta.core.parser.ReflectiveEntMetaParser;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.Ordered;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.annotation.Order;
import org.springframework.core.type.filter.AnnotationTypeFilter;

/**
 * Auto-configuration for Meta driven CRUD/DOC adapter assembly.
 */
@Configuration
@ConditionalOnClass(ReflectiveEntMetaParser.class)
@ConditionalOnProperty(prefix = "ent.loom.meta", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(EntLoomMetaProperties.class)
@AutoConfigureBefore(name = "com.entloom.crud.spring.config.CrudAutoConfiguration")
public class EntLoomMetaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public EntMetaParser entLoomMetaParser() {
        return new ReflectiveEntMetaParser();
    }

    @Bean
    @ConditionalOnMissingBean
    public DocEntityMetaResolver entLoomDocEntityMetaResolver() {
        return new DefaultDocEntityMetaResolver();
    }

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE - 100)
    @Conditional(EntityClassesConfiguredCondition.class)
    @ConditionalOnClass(MetaCrudAdapter.class)
    @ConditionalOnProperty(prefix = "ent.loom.meta.crud", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(MetaCrudAdapter.class)
    public ResourceCatalogAdapter entLoomMetaCrudAdapter(
        EntLoomMetaProperties properties,
        EntMetaParser parser
    ) {
        return new MetaCrudAdapter(resolveEntityClasses(properties), parser, diagnosticPolicy(properties));
    }

    @Bean
    @Conditional(EntityClassesConfiguredCondition.class)
    @ConditionalOnClass(MetaDocAdapter.class)
    @ConditionalOnProperty(prefix = "ent.loom.meta.doc", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public MetaDocAdapter entLoomMetaDocAdapter(
        EntLoomMetaProperties properties,
        EntMetaParser parser,
        DocEntityMetaResolver entityMetaResolver,
        ObjectProvider<DocOverrideProvider> overrideProvider
    ) {
        DocOverrideProvider provider = overrideProvider.getIfAvailable();
        return new MetaDocAdapter(
            entityMetaResolver,
            com.entloom.doc.core.spi.DocIndexProvider.noop(),
            resolveEntityClasses(properties),
            parser,
            new com.entloom.meta.adapter.doc.merge.DocRuntimeModelMerger(),
            provider == null ? DocOverrideProvider.noop() : provider,
            diagnosticPolicy(properties)
        );
    }

    private static MetaDiagnosticPolicy diagnosticPolicy(EntLoomMetaProperties properties) {
        if (properties.getDiagnostics().isFailFast()) {
            return DefaultMetaDiagnosticPolicy.failFast();
        }
        return DefaultMetaDiagnosticPolicy.lenient();
    }

    private static List<Class<?>> resolveEntityClasses(EntLoomMetaProperties properties) {
        Set<Class<?>> classes = new LinkedHashSet<Class<?>>();
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = EntLoomMetaAutoConfiguration.class.getClassLoader();
        }
        classes.addAll(resolvePackageClasses(properties.getBasePackages(), classLoader));
        for (String className : properties.getEntityClassNames()) {
            String normalized = EntLoomMetaProperties.trimToNull(className);
            if (normalized == null) {
                continue;
            }
            classes.add(resolveClass(normalized, classLoader));
        }
        if (classes.isEmpty()) {
            throw new IllegalStateException("已配置 ent.loom.meta.entity-class-names 或 ent.loom.meta.base-packages，"
                + "但未解析到任何实体类");
        }
        return new ArrayList<Class<?>>(classes);
    }

    private static List<Class<?>> resolvePackageClasses(List<String> basePackages, ClassLoader classLoader) {
        List<Class<?>> classes = new ArrayList<Class<?>>();
        if (basePackages == null || basePackages.isEmpty()) {
            return classes;
        }
        ClassPathScanningCandidateComponentProvider scanner =
            new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(EntEntity.class));
        scanner.addIncludeFilter(new AnnotationTypeFilter(EntCrudEntity.class));
        scanner.addIncludeFilter(new AnnotationTypeFilter(EntDocEntity.class));
        for (String basePackage : basePackages) {
            String normalized = EntLoomMetaProperties.trimToNull(basePackage);
            if (normalized == null) {
                continue;
            }
            Set<BeanDefinition> candidates = scanner.findCandidateComponents(normalized);
            for (BeanDefinition candidate : candidates) {
                String className = EntLoomMetaProperties.trimToNull(candidate.getBeanClassName());
                if (className == null) {
                    continue;
                }
                classes.add(resolveScannedClass(className, classLoader, normalized));
            }
        }
        return classes;
    }

    private static Class<?> resolveClass(String className, ClassLoader classLoader) {
        try {
            return Class.forName(className, false, classLoader);
        } catch (ClassNotFoundException ex) {
            throw new IllegalStateException("无法加载 ent.loom.meta.entity-class-names 配置的实体类: " + className, ex);
        }
    }

    private static Class<?> resolveScannedClass(String className, ClassLoader classLoader, String basePackage) {
        try {
            return Class.forName(className, false, classLoader);
        } catch (ClassNotFoundException ex) {
            throw new IllegalStateException("无法加载 ent.loom.meta.base-packages 扫描到的实体类: "
                + className + "，扫描包: " + basePackage, ex);
        }
    }
}
