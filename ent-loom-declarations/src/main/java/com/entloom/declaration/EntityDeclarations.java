package com.entloom.declaration;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** 静态实体文件目录；业务组件独立消费节点，不建立第二套业务运行模型。 */
public final class EntityDeclarations {
    private final Map<String, DeclarationNode> entities;

    public EntityDeclarations(Resource... resources) {
        Map<String, DeclarationNode> loaded = new LinkedHashMap<>();
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(0);
        Yaml yaml = new Yaml(new SafeConstructor(options));
        for (Resource resource : resources) {
            String filename = resource.getFilename();
            if (filename == null || !filename.matches("[A-Za-z][A-Za-z0-9_-]*\\.ya?ml")) {
                throw new IllegalArgumentException(resource + ": 文件名必须为资源码.yml 或资源码.yaml");
            }
            String code = filename.substring(0, filename.lastIndexOf('.'));
            DeclarationNode previous = loaded.get(code);
            if (previous != null) throw previous.error("重复实体声明: " + resource);
            try (InputStream stream = resource.getInputStream()) {
                DeclarationNode node = new DeclarationNode(resource.toString(), code, "", yaml.load(stream));
                node.members();
                loaded.put(code, node);
            } catch (IOException | org.yaml.snakeyaml.error.YAMLException exception) {
                throw new IllegalArgumentException(resource + " [实体=" + code + "]: 无法加载实体声明: " + exception.getMessage(), exception);
            }
        }
        entities = Collections.unmodifiableMap(loaded);
    }

    public static EntityDeclarations classpath() {
        try { return discover(new org.springframework.core.io.support.PathMatchingResourcePatternResolver()); }
        catch (IOException exception) { throw new IllegalArgumentException("无法扫描实体声明", exception); }
    }

    public static EntityDeclarations discover(ResourcePatternResolver resolver) throws IOException {
        Set<Resource> resources = new LinkedHashSet<>();
        Collections.addAll(resources, resolver.getResources("classpath*:ent/entities/*.yml"));
        Collections.addAll(resources, resolver.getResources("classpath*:ent/entities/*.yaml"));
        return new EntityDeclarations(resources.toArray(new Resource[0]));
    }

    public Map<String, DeclarationNode> entities() { return entities; }

    /** 安装或启用组件由装配层声明，公共加载器不依赖组件实现。 */
    public void validateComponents(Set<String> components) {
        entities.values().forEach(entity -> entity.members().forEach((component, node) -> {
            if (!components.contains(component)) throw node.error("组件未安装、未启用或配置项未知: " + component);
            node.members();
        }));
    }

    public void validateResources(String component, Set<String> resourceCodes) {
        entities.forEach((code, node) -> {
            if (node.members().containsKey(component) && !resourceCodes.contains(code)) throw node.error("实体未注册或未扫描: " + code);
        });
    }

    public void validateResources(Set<String> resourceCodes) {
        entities.forEach((code, node) -> {
            if (!resourceCodes.contains(code)) throw node.error("实体未注册或未扫描: " + code);
        });
    }
}
