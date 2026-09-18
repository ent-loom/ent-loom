package com.entloom.crud.starter.dao;

import java.util.List;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ClassUtils;

/** 扫描独立接口，以接口全名注册 DAO，避免跨包同名冲突。 */
public final class EntDaoScannerConfigurer implements BeanDefinitionRegistryPostProcessor, BeanFactoryAware,
    EnvironmentAware, ResourceLoaderAware {
    private final List<String> packages;
    private BeanFactory beanFactory;
    private Environment environment;
    private ResourceLoader resourceLoader;

    public EntDaoScannerConfigurer(List<String> packages) {
        this.packages = packages;
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
        List<String> scanPackages = packages.isEmpty() && AutoConfigurationPackages.has(beanFactory)
            ? AutoConfigurationPackages.get(beanFactory) : packages;
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false, environment) {
            @Override
            protected boolean isCandidateComponent(AnnotatedBeanDefinition definition) {
                return definition.getMetadata().isIndependent() && !definition.getMetadata().isAnnotation();
            }
        };
        scanner.setResourceLoader(resourceLoader);
        scanner.addIncludeFilter(new AnnotationTypeFilter(EntDao.class));
        for (String basePackage : scanPackages) {
            for (var candidate : scanner.findCandidateComponents(basePackage)) {
                String name = candidate.getBeanClassName();
                if (registry.containsBeanDefinition(name)) {
                    if (EntDaoFactoryBean.class.getName().equals(registry.getBeanDefinition(name).getBeanClassName())) {
                        continue;
                    }
                    throw new IllegalStateException("DAO Bean 名称冲突: " + name);
                }
                Class<?> daoType = ClassUtils.resolveClassName(name, resourceLoader.getClassLoader());
                RootBeanDefinition definition = new RootBeanDefinition(EntDaoFactoryBean.class);
                definition.getConstructorArgumentValues().addIndexedArgumentValue(0, daoType);
                definition.setAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE, daoType);
                registry.registerBeanDefinition(name, definition);
            }
        }
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        // 无需修改其他 Bean。
    }
}
