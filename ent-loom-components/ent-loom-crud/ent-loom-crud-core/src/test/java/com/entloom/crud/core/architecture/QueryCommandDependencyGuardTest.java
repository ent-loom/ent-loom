package com.entloom.crud.core.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * Query / Command 依赖边界守卫。
 */
public class QueryCommandDependencyGuardTest {

    @Test
    void querySideShouldNotDependOnCoreCommandTypes() {
        querySideRule().check(importedCoreClasses());
    }

    @Test
    void commandSideShouldNotDependOnCoreQueryTypes() {
        commandSideRule().check(importedCoreClasses());
    }

    @Test
    void coreShouldNotDependOnSpringStarterOrJdbcImplementations() {
        implementationBoundaryRule().check(importedCoreClasses());
    }

    @Test
    void coreShouldDeclarePhaseTwoPackageSkeleton() {
        for (String packageName : phaseTwoPackages()) {
            Path packageInfo = packageInfoPath(packageName);
            Assertions.assertTrue(
                Files.isRegularFile(packageInfo),
                "missing package-info.java for " + packageName
            );
        }
    }

    private static ArchRule querySideRule() {
        return classes()
        .that().resideInAPackage("com.entloom.crud.core..")
        .and().haveSimpleNameContaining("Query")
        .should(notDependOnCoreTypeContaining("Command"));
    }

    private static ArchRule commandSideRule() {
        return classes()
        .that().resideInAPackage("com.entloom.crud.core..")
        .and().haveSimpleNameContaining("Command")
        .should(notDependOnCoreTypeContaining("Query"));
    }

    private static JavaClasses importedCoreClasses() {
        return new ClassFileImporter().importPackages("com.entloom.crud.core");
    }

    private static ArchRule implementationBoundaryRule() {
        return classes()
        .that().resideInAPackage("com.entloom.crud.core..")
        .should(notDependOnPackages(
            "com.entloom.crud.spring",
            "com.entloom.crud.starter",
            "com.entloom.crud.engine.jdbc",
            "com.entloom.crud.excel",
            "org.springframework",
            "java.sql",
            "javax.sql",
            "org.apache.poi",
            "com.aliyun",
            "io.minio",
            "software.amazon.awssdk",
            "org.quartz"
        ));
    }

    private static List<String> phaseTwoPackages() {
        return Arrays.asList(
            "com.entloom.crud.core.runtime",
            "com.entloom.crud.core.governance",
            "com.entloom.crud.core.foundation.read",
            "com.entloom.crud.core.foundation.write",
            "com.entloom.crud.core.foundation.taskfile",
            "com.entloom.crud.core.capability.query",
            "com.entloom.crud.core.capability.command",
            "com.entloom.crud.core.capability.stats",
            "com.entloom.crud.core.capability.importing",
            "com.entloom.crud.core.capability.exporting"
        );
    }

    private static Path packageInfoPath(String packageName) {
        return Paths.get(
            "src/main/java",
            packageName.replace('.', '/') + "/package-info.java"
        );
    }

    private static ArchCondition<JavaClass> notDependOnCoreTypeContaining(String keyword) {
        return new ArchCondition<JavaClass>("not depend on core type containing '" + keyword + "'") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                for (Dependency dependency : item.getDirectDependenciesFromSelf()) {
                    JavaClass target = dependency.getTargetClass();
                    if (target.getPackageName().startsWith("com.entloom.crud.core")
                        && target.getSimpleName().contains(keyword)) {
                        String message = String.format(
                            "%s depends on %s",
                            item.getFullName(),
                            target.getFullName()
                        );
                        events.add(SimpleConditionEvent.violated(item, message));
                    }
                }
            }
        };
    }

    private static ArchCondition<JavaClass> notDependOnPackages(String... packagePrefixes) {
        return new ArchCondition<JavaClass>("not depend on implementation packages") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                for (Dependency dependency : item.getDirectDependenciesFromSelf()) {
                    JavaClass target = dependency.getTargetClass();
                    for (String packagePrefix : packagePrefixes) {
                        if (target.getPackageName().startsWith(packagePrefix)) {
                            String message = String.format(
                                "%s depends on implementation package type %s",
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
