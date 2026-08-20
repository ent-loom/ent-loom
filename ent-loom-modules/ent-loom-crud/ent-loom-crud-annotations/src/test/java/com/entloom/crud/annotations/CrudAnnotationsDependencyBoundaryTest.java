package com.entloom.crud.annotations;

import java.io.File;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * CRUD 原生注解的最小编译依赖合同。
 */
class CrudAnnotationsDependencyBoundaryTest {
    private static final Set<String> EXPECTED_COMPILE_DEPENDENCIES = new LinkedHashSet<String>(Arrays.asList(
        "ent-loom-crud-api",
        "ent-loom-meta-enums"
    ));

    @Test
    void annotations_should_keep_only_real_compile_dependencies() throws Exception {
        Assertions.assertEquals(
            EXPECTED_COMPILE_DEPENDENCIES,
            readCompileDependencies(Paths.get("pom.xml").toFile())
        );
    }

    private Set<String> readCompileDependencies(File pomFile) throws Exception {
        Document document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(pomFile);
        NodeList dependencies = document.getElementsByTagName("dependency");
        Set<String> compileDependencies = new LinkedHashSet<String>();
        for (int index = 0; index < dependencies.getLength(); index++) {
            Element dependency = (Element) dependencies.item(index);
            String scope = childText(dependency, "scope");
            if (scope == null || scope.isEmpty() || "compile".equals(scope)) {
                compileDependencies.add(childText(dependency, "artifactId"));
            }
        }
        return compileDependencies;
    }

    private String childText(Element parent, String name) {
        NodeList children = parent.getElementsByTagName(name);
        return children.getLength() == 0 ? "" : children.item(0).getTextContent().trim();
    }
}
