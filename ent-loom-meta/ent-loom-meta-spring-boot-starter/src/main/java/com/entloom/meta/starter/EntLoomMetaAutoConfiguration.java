package com.entloom.meta.starter;

import com.entloom.crud.core.adapter.ResourceCatalogAdapter;
import com.entloom.crud.core.convention.CrudConvention;
import com.entloom.doc.core.spi.DocEntityMetaResolver;
import com.entloom.doc.core.spi.DocOverrideProvider;
import com.entloom.meta.adapter.crud.MetaCrudAdapter;
import com.entloom.meta.adapter.doc.MetaDocAdapter;
import com.entloom.meta.contract.diagnostic.DefaultMetaDiagnosticPolicy;
import com.entloom.meta.contract.diagnostic.MetaDiagnosticPolicy;
import com.entloom.meta.core.convention.MetaConvention;
import com.entloom.meta.core.parser.EntMetaParser;
import com.entloom.meta.core.parser.ReflectiveEntMetaParser;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.annotation.Order;

/**
 * Auto-configuration for Meta driven CRUD/DOC adapter assembly.
 */
@Configuration
@ConditionalOnClass(ReflectiveEntMetaParser.class)
@ConditionalOnProperty(prefix = "ent.loom.meta", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(EntLoomMetaProperties.class)
@AutoConfigureBefore(name = "com.entloom.crud.starter.config.CrudAutoConfiguration")
public class EntLoomMetaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public EntMetaParser entLoomMetaParser(ObjectProvider<MetaConvention> conventionProvider) {
        List<MetaConvention> conventions = new ArrayList<MetaConvention>();
        conventionProvider.orderedStream().forEach(conventions::add);
        return new ReflectiveEntMetaParser(conventions);
    }

    @Bean
    @ConditionalOnMissingBean
    public DocEntityMetaResolver entLoomDocEntityMetaResolver() {
        return new DefaultDocEntityMetaResolver();
    }

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE - 100)
    @Conditional(EntityClassNamesPresentCondition.class)
    @ConditionalOnClass(MetaCrudAdapter.class)
    @ConditionalOnProperty(prefix = "ent.loom.meta.crud", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(MetaCrudAdapter.class)
    public ResourceCatalogAdapter entLoomMetaCrudAdapter(
        EntLoomMetaProperties properties,
        EntMetaParser parser,
        ObjectProvider<CrudConvention> conventionProvider
    ) {
        List<CrudConvention> conventions = new ArrayList<CrudConvention>();
        conventionProvider.orderedStream().forEach(conventions::add);
        return new MetaCrudAdapter(resolveEntityClasses(properties), parser, conventions, diagnosticPolicy(properties));
    }

    @Bean
    @Conditional(EntityClassNamesPresentCondition.class)
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
        List<Class<?>> classes = new ArrayList<Class<?>>();
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = EntLoomMetaAutoConfiguration.class.getClassLoader();
        }
        for (String className : properties.getEntityClassNames()) {
            String normalized = EntLoomMetaProperties.trimToNull(className);
            if (normalized == null) {
                continue;
            }
            classes.add(resolveClass(normalized, classLoader));
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
}
