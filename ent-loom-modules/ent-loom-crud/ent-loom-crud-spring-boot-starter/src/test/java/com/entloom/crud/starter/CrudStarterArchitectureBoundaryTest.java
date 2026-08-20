package com.entloom.crud.starter;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * CRUD Starter 主包与反向依赖边界守卫。
 */
class CrudStarterArchitectureBoundaryTest {
    @Test
    void all_starter_classes_should_remain_in_starter_namespace() {
        for (JavaClass javaClass : importedStarterClasses()) {
            Assertions.assertTrue(
                javaClass.getPackageName().startsWith("com.entloom.crud.starter"),
                "Starter class escaped namespace: " + javaClass.getFullName()
            );
        }
    }

    @Test
    void starter_should_not_depend_on_legacy_or_meta_runtime_packages() {
        for (JavaClass source : importedStarterClasses()) {
            for (Dependency dependency : source.getDirectDependenciesFromSelf()) {
                String targetPackage = dependency.getTargetClass().getPackageName();
                Assertions.assertFalse(
                    targetPackage.startsWith("com.entloom.crud.spring"),
                    source.getFullName() + " depends on legacy package " + targetPackage
                );
                Assertions.assertFalse(
                    targetPackage.startsWith("com.entloom.meta.core"),
                    source.getFullName() + " depends on Meta Core " + targetPackage
                );
                Assertions.assertFalse(
                    targetPackage.startsWith("com.entloom.meta.starter"),
                    source.getFullName() + " depends on Meta Starter " + targetPackage
                );
            }
        }
    }

    private JavaClasses importedStarterClasses() {
        return new ClassFileImporter().importPackages("com.entloom.crud.starter");
    }
}
