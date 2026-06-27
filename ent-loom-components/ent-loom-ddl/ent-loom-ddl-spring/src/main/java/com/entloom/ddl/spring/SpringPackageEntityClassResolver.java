package com.entloom.ddl.spring;

import com.entloom.ddl.annotations.EntDbEntity;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

/**
 * 基于 Spring 的实体类包扫描器。
 */
public class SpringPackageEntityClassResolver {
    private final ClassLoader classLoader;

    public SpringPackageEntityClassResolver(ClassLoader classLoader) {
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        this.classLoader = classLoader != null
                ? classLoader
                : (contextClassLoader != null ? contextClassLoader : SpringPackageEntityClassResolver.class.getClassLoader());
    }

    public List<Class<?>> resolve(List<String> basePackages) {
        Set<Class<?>> classes = new LinkedHashSet<Class<?>>();
        if (basePackages == null || basePackages.isEmpty()) {
            return new ArrayList<Class<?>>();
        }
        ClassPathScanningCandidateComponentProvider scanner = createScanner();
        scanner.addIncludeFilter(new AnnotationTypeFilter(EntDbEntity.class));
        for (String basePackage : basePackages) {
            if (basePackage == null || basePackage.trim().isEmpty()) {
                continue;
            }
            Set<BeanDefinition> candidates = scanner.findCandidateComponents(basePackage.trim());
            for (BeanDefinition candidate : candidates) {
                String className = candidate.getBeanClassName();
                if (className == null || className.trim().isEmpty()) {
                    continue;
                }
                try {
                    classes.add(Class.forName(className, false, classLoader));
                } catch (ClassNotFoundException ex) {
                    throw new IllegalStateException("无法加载 entloom.ddl.base-packages 扫描到的实体类: "
                            + className, ex);
                }
            }
        }
        return new ArrayList<Class<?>>(classes);
    }

    protected ClassPathScanningCandidateComponentProvider createScanner() {
        return new ClassPathScanningCandidateComponentProvider(false);
    }
}
