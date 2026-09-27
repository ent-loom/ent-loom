package com.entloom.meta.starter;

import com.entloom.crud.core.adapter.ResourceCatalogAdapter;
import com.entloom.crud.core.convention.CrudConvention;
import com.entloom.crud.core.runtime.contract.CrudInputContract;
import com.entloom.crud.core.runtime.model.CrudIdPolicyDefaults;
import com.entloom.doc.core.spi.DocEntityMetaResolver;
import com.entloom.doc.core.spi.DocOverrideProvider;
import com.entloom.meta.adapter.crud.MetaCrudAdapter;
import com.entloom.meta.adapter.doc.MetaDocAdapter;
import com.entloom.meta.adapter.doc.merge.DocRuntimeModelMerger;
import com.entloom.meta.contract.diagnostic.DefaultMetaDiagnosticPolicy;
import com.entloom.meta.contract.diagnostic.MetaDiagnosticPolicy;
import com.entloom.meta.core.convention.MetaConvention;
import com.entloom.meta.core.parser.EntMetaParser;
import com.entloom.meta.core.parser.ReflectiveEntMetaParser;
import com.entloom.meta.core.model.MetaEntityDefaults;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import com.entloom.meta.annotations.EntEntity;
import com.entloom.crud.annotations.EntCrudEntity;
import com.entloom.doc.annotations.EntDocEntity;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.filter.AnnotationTypeFilter;
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
    public EntMetaParser entLoomMetaParser(
        ObjectProvider<MetaConvention> conventionProvider,
        EntLoomMetaProperties properties
    ) {
        List<MetaConvention> conventions = new ArrayList<MetaConvention>();
        conventionProvider.orderedStream().forEach(conventions::add);
        return new ReflectiveEntMetaParser(
            conventions,
            new MetaEntityDefaults(properties.getDefaults().getService(), properties.getDefaults().getIdPolicy())
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public DocEntityMetaResolver entLoomDocEntityMetaResolver() {
        return new DefaultDocEntityMetaResolver();
    }

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE - 100)
    @Conditional(EntitySourcesPresentCondition.class)
    @ConditionalOnClass(MetaCrudAdapter.class)
    @ConditionalOnProperty(prefix = "ent.loom.meta.crud", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(MetaCrudAdapter.class)
    public ResourceCatalogAdapter entLoomMetaCrudAdapter(
        EntLoomMetaProperties properties,
        EntMetaParser parser,
        ResourceLoader resourceLoader,
        ObjectProvider<CrudConvention> conventionProvider,
        ObjectProvider<CrudInputContract> inputContractProvider,
        ObjectProvider<CrudIdPolicyDefaults> idPolicyDefaultsProvider
    ) {
        List<CrudConvention> conventions = new ArrayList<CrudConvention>();
        conventionProvider.orderedStream().forEach(conventions::add);
        CrudInputContract inputContract = inputContractProvider.getIfAvailable();
        return new MetaCrudAdapter(
            resolveEntityClasses(properties, resourceLoader),
            parser,
            conventions,
            inputContract == null ? CrudInputContract.empty() : inputContract,
            diagnosticPolicy(properties),
            idPolicyDefaultsProvider.getIfAvailable(CrudIdPolicyDefaults::new)
        );
    }

    @Bean
    @Conditional(EntitySourcesPresentCondition.class)
    @ConditionalOnClass(MetaDocAdapter.class)
    @ConditionalOnProperty(prefix = "ent.loom.meta.doc", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public MetaDocAdapter entLoomMetaDocAdapter(
        EntLoomMetaProperties properties,
        EntMetaParser parser,
        ResourceLoader resourceLoader,
        DocEntityMetaResolver entityMetaResolver,
        ObjectProvider<DocOverrideProvider> overrideProvider,
        ObjectProvider<CrudInputContract> inputContractProvider
    ) {
        DocOverrideProvider provider = overrideProvider.getIfAvailable();
        return new MetaDocAdapter(
            entityMetaResolver,
            com.entloom.doc.core.spi.DocIndexProvider.noop(),
            resolveEntityClasses(properties, resourceLoader),
            parser,
            new DocRuntimeModelMerger(inputContractProvider.getIfAvailable()),
            provider == null ? DocOverrideProvider.noop() : provider,
            diagnosticPolicy(properties)
        );
    }

    static MetaDiagnosticPolicy diagnosticPolicy(EntLoomMetaProperties properties) {
        if (properties.getDiagnostics().isFailFast()) {
            return DefaultMetaDiagnosticPolicy.failFast();
        }
        return DefaultMetaDiagnosticPolicy.lenient();
    }

    static List<Class<?>> resolveEntityClasses(EntLoomMetaProperties properties, ResourceLoader resourceLoader) {
        List<Class<?>> classes = new ArrayList<Class<?>>();
        ClassLoader classLoader = resourceLoader.getClassLoader();
        if (classLoader == null) {
            classLoader = EntLoomMetaAutoConfiguration.class.getClassLoader();
        }
        Set<String> classNames = new TreeSet<String>(properties.getEntityClassNames());
        ClassPathScanningCandidateComponentProvider scanner =
            new ClassPathScanningCandidateComponentProvider(false);
        scanner.setResourceLoader(resourceLoader);
        scanner.addIncludeFilter(new AnnotationTypeFilter(EntEntity.class));
        scanner.addIncludeFilter(new AnnotationTypeFilter(EntCrudEntity.class));
        scanner.addIncludeFilter(new AnnotationTypeFilter(EntDocEntity.class));
        for (String basePackage : properties.getBasePackages()) {
            for (BeanDefinition candidate : scanner.findCandidateComponents(basePackage)) {
                classNames.add(candidate.getBeanClassName());
            }
        }
        for (String className : classNames) {
            classes.add(resolveClass(className, classLoader));
        }
        return classes;
    }

    private static Class<?> resolveClass(String className, ClassLoader classLoader) {
        try {
            return Class.forName(className, false, classLoader);
        } catch (ClassNotFoundException ex) {
            throw new IllegalStateException("无法加载 ent.loom.meta 配置或扫描发现的实体类: " + className, ex);
        }
    }
}
