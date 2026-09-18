package com.entloom.crud.starter.dao;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.ClassUtils;

/** 收集显式扫描包，交由统一扫描器注册 DAO。 */
public final class EntDaoScanRegistrar implements ImportBeanDefinitionRegistrar {
    private static final String BEAN_NAME = EntDaoScannerConfigurer.class.getName();

    @Override
    @SuppressWarnings("unchecked")
    public void registerBeanDefinitions(AnnotationMetadata metadata, BeanDefinitionRegistry registry) {
        AnnotationAttributes attributes = AnnotationAttributes.fromMap(
            metadata.getAnnotationAttributes(EntDaoScan.class.getName()));
        List<String> packages = new ArrayList<>(Arrays.asList(attributes.getStringArray("basePackages")));
        for (Class<?> type : attributes.getClassArray("basePackageClasses")) {
            packages.add(ClassUtils.getPackageName(type));
        }
        if (packages.isEmpty()) {
            packages.add(ClassUtils.getPackageName(metadata.getClassName()));
        }
        if (packages.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("EntDaoScan 扫描包不能为空");
        }
        if (registry.containsBeanDefinition(BEAN_NAME)) {
            List<String> existing = (List<String>) registry.getBeanDefinition(BEAN_NAME)
                .getConstructorArgumentValues().getIndexedArgumentValue(0, List.class).getValue();
            existing.addAll(packages);
        } else {
            RootBeanDefinition definition = new RootBeanDefinition(EntDaoScannerConfigurer.class);
            definition.getConstructorArgumentValues().addIndexedArgumentValue(0, packages);
            registry.registerBeanDefinition(BEAN_NAME, definition);
        }
    }
}
