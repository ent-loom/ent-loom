package com.entloom.meta.core.architecture;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.io.File;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * Meta 模块边界守卫。
 */
class MetaModuleBoundaryGuardTest {

    @Test
    void meta_core_should_not_depend_on_sub_framework_or_business_packages() {
        ArchRule rule = classes()
            .that().resideInAPackage("com.entloom.meta.core..")
            .should(notDependOnPackages(
                "com.entloom.crud",
                "com.entloom.doc",
                "com.entloom.meta.adapter",
                "com.jiangbing",
                "com.qingyiwei",
                "cn.qingyiwei",
                "org.springframework",
                "java.sql",
                "javax.sql"
            ));

        rule.check(importedMetaClasses());
    }

    @Test
    void contract_descriptors_should_not_expose_runtime_or_sub_framework_model_terms() {
        ArchRule rule = classes()
            .that().resideInAPackage("com.entloom.meta.contract.descriptor..")
            .should(notDependOnPackages(
                "com.entloom.crud",
                "com.entloom.doc",
                "com.entloom.meta.adapter",
                "com.jiangbing",
                "com.qingyiwei",
                "cn.qingyiwei"
            ))
            .andShould(notExposeDescriptorTerms(
                "crud",
                "ddl",
                "tablename",
                "columnname",
                "resourcecode",
                "entitymeta",
                "relationedge",
                "joinkind",
                "relationscope",
                "targetentitylabel",
                "relationremark"
            ));

        rule.check(importedMetaClasses());
    }

    @Test
    void meta_core_pom_should_keep_only_allowed_direct_dependencies() throws Exception {
        Set<String> dependencies = directDependencyArtifacts(new File("pom.xml"));

        Assertions.assertEquals(
            new LinkedHashSet<String>(Arrays.asList(
                "com.entloom:ent-loom-base",
                "com.entloom:ent-loom-meta-enums",
                "com.entloom:ent-loom-meta-annotations",
                "com.entloom:ent-loom-meta-contract",
                "org.junit.jupiter:junit-jupiter-api",
                "com.tngtech.archunit:archunit"
            )),
            dependencies
        );
    }

    private static JavaClasses importedMetaClasses() {
        return new ClassFileImporter().importPackages("com.entloom.meta.core", "com.entloom.meta.contract");
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

    private static ArchCondition<JavaClass> notExposeDescriptorTerms(String... forbiddenTerms) {
        return new ArchCondition<JavaClass>("not expose runtime or sub-framework descriptor terms") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                checkName(item, item.getSimpleName(), forbiddenTerms, events);
                for (JavaField field : item.getFields()) {
                    checkName(item, field.getName(), forbiddenTerms, events);
                }
                for (JavaMethod method : item.getMethods()) {
                    checkName(item, method.getName(), forbiddenTerms, events);
                }
            }
        };
    }

    private static void checkName(JavaClass owner, String name, String[] forbiddenTerms, ConditionEvents events) {
        String normalized = name.toLowerCase();
        for (String forbiddenTerm : forbiddenTerms) {
            if (normalized.contains(forbiddenTerm)) {
                String message = String.format(
                    "%s exposes forbidden descriptor term '%s' in %s",
                    owner.getFullName(),
                    forbiddenTerm,
                    name
                );
                events.add(SimpleConditionEvent.violated(owner, message));
            }
        }
    }

    private static Set<String> directDependencyArtifacts(File pomFile) throws Exception {
        Document document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(pomFile);
        NodeList dependencies = document.getDocumentElement()
            .getElementsByTagName("dependency");
        Set<String> artifacts = new LinkedHashSet<String>();
        for (int i = 0; i < dependencies.getLength(); i++) {
            Node dependency = dependencies.item(i);
            String groupId = childText(dependency, "groupId");
            String artifactId = childText(dependency, "artifactId");
            artifacts.add(resolveProjectGroup(groupId) + ":" + artifactId);
        }
        return artifacts;
    }

    private static String childText(Node node, String childName) {
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (childName.equals(child.getNodeName())) {
                return child.getTextContent().trim();
            }
        }
        return "";
    }

    private static String resolveProjectGroup(String value) {
        return "${project.groupId}".equals(value) ? "com.entloom" : value;
    }
}
