package com.entloom.meta.adapter.doc;

import com.entloom.declaration.DeclarationNode;
import com.entloom.declaration.EntityDeclarations;
import com.entloom.declaration.EntityDeclarationComponent;
import com.entloom.doc.core.spi.DocOverrideProvider;
import com.entloom.doc.core.spi.DocEntityOverride;
import com.entloom.doc.core.spi.DocFieldOverride;
import com.entloom.base.util.reflect.EntityProperties;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.LinkedHashSet;

/** DOC 自行编译展示差异，复用已有文档覆盖模型与字段语义校验。 */
public final class DocEntityDeclarations implements EntityDeclarationComponent, DocOverrideProvider {
    private final Map<String, DeclarationNode> nodes = new LinkedHashMap<>();
    private final Set<String> consumed = new LinkedHashSet<>();
    public DocEntityDeclarations(EntityDeclarations declarations) {
        declarations.entities().forEach((resource, root) -> {
            var doc = root.members().get("doc");
            if (doc == null) return;
            doc.only("name", "description", "group", "remark", "hidden", "visible-for", "fields");
            nodes.put(resource, doc);
        });
    }
    public void validateConsumed() { validateResources(consumed); }

    public void validateResources(Set<String> resources) {
        nodes.forEach((resource, node) -> {
            if (!resources.contains(resource)) throw node.error("文档实体未注册或未扫描: " + resource);
        });
    }
    @Override public String componentCode() { return "doc"; }
    @Override public DocEntityOverride overrideFor(Class<?> entityClass, String resourceCode) {
        var node = nodes.get(resourceCode);
        if (node == null) return null;
        consumed.add(resourceCode);
        var values = node.members();
        var builder = DocEntityOverride.builder();
        if (values.containsKey("name")) builder.entityName(values.get("name").text());
        if (values.containsKey("description")) builder.description(values.get("description").text());
        if (values.containsKey("group")) builder.group(values.get("group").text());
        if (values.containsKey("remark")) builder.remark(values.get("remark").text());
        if (values.containsKey("hidden")) builder.hidden(values.get("hidden").bool());
        if (values.containsKey("visible-for")) builder.visibleFor(values.get("visible-for").strings());
        Set<String> properties = new LinkedHashSet<>();
        EntityProperties.fields(entityClass).forEach(field -> properties.add(field.getName()));
        if (values.containsKey("fields")) values.get("fields").members().forEach((field, plan) -> {
            if (!properties.contains(field)) throw plan.error("文档引用未知字段: " + field);
            plan.only("name", "description", "example", "examples", "group", "remark", "hidden", "visible-for", "target-entity-label", "relation-remark");
            var options = plan.members();
            var fieldBuilder = DocFieldOverride.builder(field);
            if (options.containsKey("target-entity-label")) fieldBuilder.targetEntityLabel(options.get("target-entity-label").text());
            if (options.containsKey("relation-remark")) fieldBuilder.relationRemark(options.get("relation-remark").text());
            if (options.containsKey("name")) fieldBuilder.name(options.get("name").text());
            if (options.containsKey("description")) fieldBuilder.description(options.get("description").text());
            if (options.containsKey("example")) fieldBuilder.example(options.get("example").text());
            if (options.containsKey("examples")) fieldBuilder.examples(options.get("examples").strings());
            if (options.containsKey("group")) fieldBuilder.group(options.get("group").text());
            if (options.containsKey("remark")) fieldBuilder.remark(options.get("remark").text());
            if (options.containsKey("hidden")) fieldBuilder.hidden(options.get("hidden").bool());
            if (options.containsKey("visible-for")) fieldBuilder.visibleFor(options.get("visible-for").strings());
            builder.field(fieldBuilder.build());
        });
        return builder.build();
    }
}
