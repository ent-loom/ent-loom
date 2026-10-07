package com.entloom.crud.core.governance.policy;

import com.entloom.crud.api.access.BusinessOperation;
import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.api.enums.CrudOperationKey;
import com.entloom.crud.core.exception.PermissionDeniedException;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.ResourceDescriptor;
import com.entloom.crud.core.util.RouteKeyFactory;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** 启动期冻结的 CRUD 适配映射；映射存在不代表主体已获授权。 */
public final class CrudOperationMappingRegistry {
    private final Map<Key, CrudOperationMapping> mappings;

    public CrudOperationMappingRegistry(Collection<EntityMeta> entities, BusinessOperationCatalog catalog,
                                        Collection<CrudOperationMapping> declarations) {
        Objects.requireNonNull(entities, "实体元数据不能为空");
        Objects.requireNonNull(catalog, "业务操作目录不能为空");
        Objects.requireNonNull(declarations, "CRUD 映射声明不能为空");
        Map<String, ResourceDescriptor> resources = new LinkedHashMap<String, ResourceDescriptor>();
        for (EntityMeta entity : entities) {
            if (entity == null) throw new ValidationException("实体元数据不能为空");
            ResourceDescriptor resource = entity.getResourceDescriptor();
            if (resources.put(resource.getResourceCode(), resource) != null) {
                throw new ValidationException("CRUD 映射资源 code 重复: " + resource.getResourceCode());
            }
        }
        Map<Key, CrudOperationMapping> compiled = new LinkedHashMap<Key, CrudOperationMapping>();
        for (CrudOperationMapping declaration : declarations) {
            if (declaration == null) throw new ValidationException("CRUD 映射声明不能为空");
            BusinessOperation target = declaration.getBusinessOperation();
            ResourceDescriptor resource = resources.get(target.getResourceCode());
            if (resource == null) {
                throw new ValidationException("CRUD 映射引用未知或非规范资源: " + target
                    + "，来源: " + declaration.getSource());
            }
            if (isAction(declaration.getOperationKey())
                && !EntityActionDeclarations.allows(resource.getEntityType(), declaration.getScene())) {
                throw new ValidationException("ACTION 映射超出实体动作边界: " + target + "/"
                    + declaration.getScene() + "，来源: " + declaration.getSource());
            }
            if (!catalog.snapshot().containsKey(target)) {
                throw new ValidationException("CRUD 映射引用未注册业务操作: " + target
                    + "，来源: " + declaration.getSource());
            }
            Key key = new Key(target.getResourceCode(), declaration.getOperationKey(), declaration.getScene());
            CrudOperationMapping previous = compiled.putIfAbsent(key, declaration);
            if (previous != null) {
                throw new ValidationException("CRUD 映射重复声明: " + key + "，来源: "
                    + previous.getSource() + " / " + declaration.getSource());
            }
        }
        this.mappings = Collections.unmodifiableMap(compiled);
    }

    /** 只接受规范资源 code；非 ACTION 的 scene 仍由现有 Scene Policy 独立约束。 */
    public BusinessOperation require(String resourceCode, CrudOperationKey operationKey, String scene) {
        Objects.requireNonNull(operationKey, "CRUD 操作不能为空");
        String normalizedScene = isAction(operationKey) ? RouteKeyFactory.normalizeScene(scene) : "";
        CrudOperationMapping mapping = mappings.get(new Key(resourceCode, operationKey, normalizedScene));
        if (mapping == null) {
            throw new PermissionDeniedException("CRUD 操作未映射: " + resourceCode + "/"
                + operationKey + "/" + normalizedScene);
        }
        return mapping.getBusinessOperation();
    }

    public Map<Key, CrudOperationMapping> snapshot() { return mappings; }

    private static boolean isAction(CrudOperationKey operationKey) {
        return CrudOperationKey.of(CommandOperation.ACTION).equals(operationKey);
    }

    public static final class Key {
        private final String resourceCode;
        private final CrudOperationKey operationKey;
        private final String scene;

        private Key(String resourceCode, CrudOperationKey operationKey, String scene) {
            this.resourceCode = resourceCode;
            this.operationKey = operationKey;
            this.scene = scene;
        }

        public String getResourceCode() { return resourceCode; }
        public CrudOperationKey getOperationKey() { return operationKey; }
        public String getScene() { return scene; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Key)) return false;
            Key key = (Key) other;
            return Objects.equals(resourceCode, key.resourceCode)
                && operationKey.equals(key.operationKey) && scene.equals(key.scene);
        }

        @Override
        public int hashCode() { return Objects.hash(resourceCode, operationKey, scene); }

        @Override
        public String toString() { return resourceCode + "/" + operationKey + "/" + scene; }
    }
}
