package com.entloom.ddl.declaration;

import com.entloom.declaration.DeclarationNode;
import com.entloom.declaration.EntityDeclarations;
import com.entloom.declaration.EntityDeclarationComponent;
import com.entloom.ddl.api.DdlEntityMetadata;
import com.entloom.ddl.api.DdlIndexMetadata;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/** DDL 独立解释索引节点；Java 属性映射为最终持久化列后交给既有 DDL 模型校验。 */
public final class DdlEntityDeclarations implements EntityDeclarationComponent {
    private final EntityDeclarations declarations;
    public DdlEntityDeclarations(EntityDeclarations declarations) {
        this.declarations = declarations;
        declarations.entities().values().forEach(root -> {
            DeclarationNode node = root.members().get("ddl");
            if (node == null) return;
            node.only("indexes");
            DeclarationNode indexes = node.members().get("indexes");
            if (indexes != null) indexes.elements().forEach(index -> {
                index.only("name", "fields", "expression", "unique");
                var values = index.members();
                if (values.containsKey("fields") == values.containsKey("expression")) throw index.error("索引必须且只能定义 fields 或 expression");
                if (values.containsKey("fields") && values.get("fields").strings().isEmpty()) throw index.error("索引 fields 不能为空");
                if (values.containsKey("expression")) values.get("expression").text();
                if (values.containsKey("name")) values.get("name").text();
                if (values.containsKey("unique")) values.get("unique").bool();
            });
        });
    }
    @Override public String componentCode() { return "ddl"; }

    public void validateResources(java.util.Set<String> resources) {
        declarations.validateResources("ddl", resources);
    }

    public DdlEntityMetadata apply(String resource, DdlEntityMetadata model) {
        var root = declarations.entities().get(resource);
        if (root == null || !root.members().containsKey("ddl")) return model;
        var node = root.members().get("ddl").members().get("indexes");
        if (node == null) return model;
        Map<String, String> columns = new LinkedHashMap<>();
        model.fields().forEach(field -> { if (field.persisted()) columns.put(field.fieldName(), field.columnName()); });
        List<DdlIndexMetadata> indexes = new ArrayList<>(model.indexes());
        for (DeclarationNode index : node.elements()) {
            var values = index.members();
            List<String> fields = new ArrayList<>();
            if (values.containsKey("fields")) {
                for (String field : values.get("fields").strings()) {
                    if (!columns.containsKey(field)) throw values.get("fields").error("索引引用不存在或不持久化的 Java 属性: " + field);
                    fields.add(columns.get(field));
                }
            }
            indexes.add(new DdlIndexMetadata(values.containsKey("name") ? values.get("name").text() : null, fields,
                values.containsKey("unique") && values.get("unique").bool(),
                values.containsKey("expression") ? values.get("expression").text() : null));
        }
        try {
            return new DdlEntityMetadata(model.entityClassName(), model.schema(), model.tableName(), model.comment(), model.tableSize(), model.fields(), indexes);
        } catch (IllegalArgumentException exception) { throw node.error(exception.getMessage()); }
    }
}
