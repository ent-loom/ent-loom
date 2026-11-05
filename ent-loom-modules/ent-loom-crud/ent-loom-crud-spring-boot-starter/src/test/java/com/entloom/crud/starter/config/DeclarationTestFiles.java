package com.entloom.crud.starter.config;

import com.entloom.declaration.EntityDeclarations;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.yaml.snakeyaml.Yaml;

/** 测试夹具组装器：实体文件与应用共享配置分别加载。 */
final class DeclarationTestFiles {
    private DeclarationTestFiles() { }
    @SuppressWarnings("unchecked")
    static EntityDeclarations load(String suite, String text, EntityMetaRegistry metas) {
        Object loaded = new Yaml().load(text);
        Map<String, Object> root = loaded == null ? new LinkedHashMap<>() : (Map<String,Object>) loaded;
        Map<String,Object> access = access(root);
        Map<String,Object> entities = (Map<String,Object>) access.getOrDefault("entities", Map.of());
        Map<String,Map<String,Object>> files = new LinkedHashMap<>();
        if (metas != null) for (var meta : metas.getEntityMetas()) {
            String code = meta.getResourceDescriptor().getResourceCode();
            var resource = new ClassPathResource("declarations/" + suite + "/" + meta.getEntityType().getSimpleName() + ".yml");
            try {
                if (resource.exists()) {
                    byte[] bytes = resource.getInputStream().readAllBytes();
                    new EntityDeclarations(new ByteArrayResource(bytes, suite + "/" + code + ".yml") {
                        @Override public String getFilename() { return code + ".yml"; }
                    });
                    files.put(code, (Map<String,Object>) new Yaml().load(new String(bytes, StandardCharsets.UTF_8)));
                }
            } catch (java.io.IOException exception) { throw new IllegalStateException(exception); }
        }
        entities.forEach((code, value) -> {
            Map<String,Object> file = files.computeIfAbsent(code, key -> new LinkedHashMap<>());
            Map<String,Object> crud = (Map<String,Object>) file.computeIfAbsent("crud", key -> new LinkedHashMap<>());
            ((Map<String,Object>) value).forEach((key, item) -> {
                if (crud.containsKey(key)) throw new IllegalArgumentException("实体文件重复声明: " + code + "." + key);
                crud.put(key, item);
            });
        });
        List<org.springframework.core.io.Resource> resources = new ArrayList<>();
        files.forEach((code, value) -> resources.add(new ByteArrayResource(new Yaml().dump(value).getBytes(StandardCharsets.UTF_8), suite + "/" + code + ".yml") {
            @Override public String getFilename() { return code + ".yml"; }
        }));
        return new EntityDeclarations(resources.toArray(new org.springframework.core.io.Resource[0]));
    }
    @SuppressWarnings("unchecked")
    private static Map<String,Object> access(Map<String,Object> root) {
        if (root.containsKey("ent.loom.crud.access")) return (Map<String,Object>) root.get("ent.loom.crud.access");
        Object current = root;
        for (String key : List.of("ent", "loom", "crud", "access")) {
            if (!(current instanceof Map)) return Map.of();
            current = ((Map<String,Object>) current).get(key);
        }
        return current instanceof Map ? (Map<String,Object>) current : Map.of();
    }
    static String properties(String text) {
        Map<String,Object> root = new Yaml().load(text);
        if (root == null) return "{}";
        access(root).remove("entities");
        return new Yaml().dump(root);
    }
}
