package com.entloom.meta.starter;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

class EntLoomMetaStarterBoundaryGuardTest {

    @Test
    void crudCoreShouldNotDependOnMetaStarterOrAdapters() {
        classes()
            .that().resideInAPackage("com.entloom.crud.core..")
            .should(notDependOnPackages("com.entloom.meta.starter", "com.entloom.meta.adapter"))
            .check(importedClasses());
    }

    @Test
    void docCoreShouldNotDependOnMetaStarterOrAdapters() {
        classes()
            .that().resideInAPackage("com.entloom.doc.core..")
            .should(notDependOnPackages("com.entloom.meta.starter", "com.entloom.meta.adapter"))
            .check(importedClasses());
    }

    @Test
    void metaCoreShouldRemainIndependentFromSubFrameworksAndStarter() {
        classes()
            .that().resideInAPackage("com.entloom.meta.core..")
            .should(notDependOnPackages(
                "com.entloom.crud",
                "com.entloom.doc",
                "com.entloom.meta.adapter",
                "com.entloom.meta.starter",
                "org.springframework"
            ))
            .check(importedClasses());
    }

    @Test
    void onlyStarterPackageShouldDependOnSpringAutoConfigure() {
        classes()
            .that().resideOutsideOfPackage("com.entloom.meta.starter..")
            .should(notDependOnPackages("org.springframework.boot.autoconfigure"))
            .check(importedClasses());
    }

    private static JavaClasses importedClasses() {
        return new ClassFileImporter().importPackages(
            "com.entloom.crud.core",
            "com.entloom.doc.core",
            "com.entloom.meta.core",
            "com.entloom.meta.starter"
        );
    }

    private static ArchCondition<JavaClass> notDependOnPackages(String... packagePrefixes) {
        return new ArchCondition<JavaClass>("not depend on forbidden packages") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                for (Dependency dependency : item.getDirectDependenciesFromSelf()) {
                    JavaClass target = dependency.getTargetClass();
                    for (String packagePrefix : packagePrefixes) {
                        if (target.getPackageName().startsWith(packagePrefix)) {
                            String message = String.format(
                                "%s depends on forbidden package type %s",
                                item.getFullName(),
                                target.getFullName()
                            );
                            events.add(SimpleConditionEvent.violated(item, message));
                        }
                    }
                }
            }
        };
    }
}
