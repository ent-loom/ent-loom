package com.entloom.ddl.spring;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;

class SpringPackageEntityClassResolverTest {
    @Test
    void brokenScannedEntryShouldFailFast() {
        SpringPackageEntityClassResolver resolver = new SpringPackageEntityClassResolver(null) {
            @Override
            protected ClassPathScanningCandidateComponentProvider createScanner() {
                return new ClassPathScanningCandidateComponentProvider(false) {
                    @Override
                    public Set<BeanDefinition> findCandidateComponents(String basePackage) {
                        GenericBeanDefinition definition = new GenericBeanDefinition();
                        definition.setBeanClassName("com.entloom.missing.BrokenDdlEntity");
                        Set<BeanDefinition> definitions = new LinkedHashSet<BeanDefinition>();
                        definitions.add(definition);
                        return definitions;
                    }
                };
            }
        };

        IllegalStateException ex = Assertions.assertThrows(IllegalStateException.class,
            () -> resolver.resolve(Collections.singletonList("com.entloom")));
        Assertions.assertTrue(ex.getMessage().contains("无法加载 entloom.ddl.base-packages 扫描到的实体类"));
    }
}
