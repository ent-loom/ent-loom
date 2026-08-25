package com.entloom.ddl.core;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * DDL Core 模块边界守卫。
 */
class DdlCoreBoundaryTest {

    @Test
    @DisplayName("Core 不得依赖 Spring、Servlet、Starter 或 Meta Core")
    void coreShouldNotDependOnIntegrationPackages() {
        ArchRule rule = classes()
                .that().resideInAPackage("com.entloom.ddl.core..")
                .should(notDependOnPackages(
                        "org.springframework",
                        "jakarta.servlet",
                        "javax.servlet",
                        "com.entloom.ddl.spring",
                        "com.entloom.ddl.starter",
                        "com.entloom.meta"));

        rule.check(importedCoreClasses());
    }

    private static JavaClasses importedCoreClasses() {
        return new ClassFileImporter().importPackages("com.entloom.ddl.core");
    }

    private static ArchCondition<JavaClass> notDependOnPackages(String... packagePrefixes) {
        return new ArchCondition<JavaClass>("not depend on integration packages") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                for (Dependency dependency : item.getDirectDependenciesFromSelf()) {
                    String targetPackage = dependency.getTargetClass().getPackageName();
                    for (String packagePrefix : packagePrefixes) {
                        if (targetPackage.startsWith(packagePrefix)) {
                            events.add(SimpleConditionEvent.violated(
                                    item,
                                    item.getFullName() + " depends on forbidden package " + targetPackage));
                        }
                    }
                }
            }
        };
    }
}
