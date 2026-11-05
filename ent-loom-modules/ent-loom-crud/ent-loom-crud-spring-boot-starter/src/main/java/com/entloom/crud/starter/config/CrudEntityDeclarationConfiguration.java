package com.entloom.crud.starter.config;

import com.entloom.crud.core.governance.policy.EntityPolicyRegistry;
import com.entloom.crud.core.governance.policy.CrudEntityActions;
import org.springframework.context.annotation.Import;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.env.Environment;
import org.springframework.beans.factory.BeanFactory;
import com.entloom.crud.annotations.EntCrudEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** 实体声明在编译运行模型前加载一次；不依赖实体注册表，避免归属规则装配成环。 */
@Configuration(proxyBeanMethods = false)
@Import(CrudPropertiesConfiguration.class)
public class CrudEntityDeclarationConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public CrudEntityActions crudEntityActions(CrudProperties properties) {
        return new CrudEntityActions(properties.getDefaults().getActions());
    }

    @Bean
    public SmartInitializingSingleton removedGlobalEntityContractGuard(org.springframework.core.env.ConfigurableEnvironment environment) {
        return () -> {
            var prefix = org.springframework.boot.context.properties.source.ConfigurationPropertyName.of("ent.loom.crud.contracts");
            for (var source : org.springframework.boot.context.properties.source.ConfigurationPropertySources.get(environment)) {
                if (source.containsDescendantOf(prefix) == org.springframework.boot.context.properties.source.ConfigurationPropertyState.PRESENT
                    || source.getConfigurationProperty(prefix) != null) {
                    throw new IllegalArgumentException("全局输入契约已删除，请使用 @EntCrudEntity 声明输入字段约束");
                }
            }
        };
    }

    @Bean
    public SmartInitializingSingleton crudEntityDeclarationResourceGuard(CrudEntityDeclarations declarations,
            ObjectProvider<EntityMetaRegistry> entities) {
        return () -> {
            EntityMetaRegistry registry = entities.getIfAvailable();
            if (registry != null) declarations.validate(registry);
            else if (!declarations.options().isEmpty()) throw new IllegalArgumentException(
                "实体声明未被消费：CRUD 实体注册表不存在，资源=" + declarations.options().keySet());
        };
    }

    @Bean
    @ConditionalOnProperty(name = "ent.loom.crud.controller.enabled", havingValue = "true")
    public SmartInitializingSingleton entityDeclarationHttpGuard(
        CrudEntityDeclarations declarations,
        ObjectProvider<EntityMetaRegistry> entities,
        ObjectProvider<EntityPolicyRegistry> policies) {
        return () -> {
            var registry = entities.getIfAvailable();
            if (registry != null) declarations.assertHttpAccess(registry, policies.getIfAvailable());
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public CrudEntityDeclarations crudEntityDeclarations(Environment environment, ResourceLoader resourceLoader, BeanFactory beanFactory, CrudEntityActions actions) {
        Binder binder = Binder.get(environment);
        List<String> packages = binder.bind("ent.loom.meta.base-packages", Bindable.listOf(String.class)).orElse(List.of());
        Set<String> classNames = new TreeSet<>(binder.bind("ent.loom.meta.entity-class-names", Bindable.listOf(String.class)).orElse(List.of()));
        if (packages.isEmpty() && classNames.isEmpty() && AutoConfigurationPackages.has(beanFactory)) {
            packages = AutoConfigurationPackages.get(beanFactory);
        }
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.setResourceLoader(resourceLoader);
        scanner.addIncludeFilter(new AnnotationTypeFilter(EntCrudEntity.class));
        for (String basePackage : packages) {
            scanner.findCandidateComponents(basePackage).forEach(candidate -> classNames.add(candidate.getBeanClassName()));
        }
        ClassLoader classLoader = resourceLoader.getClassLoader();
        if (classLoader == null) classLoader = CrudEntityDeclarationConfiguration.class.getClassLoader();
        List<Class<?>> entityTypes = new ArrayList<>();
        for (String className : classNames) {
            try {
                entityTypes.add(Class.forName(className, false, classLoader));
            } catch (ClassNotFoundException exception) {
                throw new IllegalArgumentException("无法加载实体类: " + className, exception);
            }
        }
        return new CrudEntityDeclarations(entityTypes, actions);
    }
}
