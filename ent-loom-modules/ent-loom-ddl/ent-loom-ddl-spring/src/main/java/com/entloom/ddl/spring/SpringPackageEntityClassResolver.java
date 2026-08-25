package com.entloom.ddl.spring;

import com.entloom.ddl.annotations.EntDbEntity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

/**
 * 基于 Spring 的实体类包扫描器。
 */
public final class SpringPackageEntityClassResolver {
    private final ClassLoader classLoader;

    public SpringPackageEntityClassResolver(ClassLoader classLoader) {
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        this.classLoader = classLoader != null
                ? classLoader
                : (contextClassLoader != null ? contextClassLoader : SpringPackageEntityClassResolver.class.getClassLoader());
    }

    public List<Class<?>> resolve(List<String> basePackages) {
        List<String> packages = normalizePackages(basePackages);
        if (packages.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, Class<?>> classes = new LinkedHashMap<String, Class<?>>();
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(EntDbEntity.class));
        for (String basePackage : packages) {
            try {
                for (BeanDefinition candidate : scanner.findCandidateComponents(basePackage)) {
                    String className = candidate.getBeanClassName();
                    if (className == null || className.trim().isEmpty()) {
                        continue;
                    }
                    try {
                        Class<?> entityClass = Class.forName(className.trim(), false, classLoader);
                        classes.putIfAbsent(entityClass.getName(), entityClass);
                    } catch (ClassNotFoundException | LinkageError | RuntimeException ignored) {
                        // 不可加载类不应阻断同一包的其他实体发现。
                    }
                }
            } catch (LinkageError | RuntimeException ignored) {
                // 单个包扫描失败时继续处理其他包。
            }
        }
        List<Class<?>> result = new ArrayList<Class<?>>(classes.values());
        result.sort(Comparator.comparing(Class::getName));
        return result;
    }

    private static List<String> normalizePackages(List<String> basePackages) {
        if (basePackages == null || basePackages.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> packages = new ArrayList<String>();
        for (String basePackage : basePackages) {
            if (basePackage != null && !basePackage.trim().isEmpty()) {
                packages.add(basePackage.trim());
            }
        }
        Collections.sort(packages);
        List<String> distinctPackages = new ArrayList<String>();
        String previous = null;
        for (String basePackage : packages) {
            if (!basePackage.equals(previous)) {
                distinctPackages.add(basePackage);
                previous = basePackage;
            }
        }
        return distinctPackages;
    }
}
