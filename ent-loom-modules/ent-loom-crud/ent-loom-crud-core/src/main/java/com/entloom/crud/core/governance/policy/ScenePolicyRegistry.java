package com.entloom.crud.core.governance.policy;

import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.api.enums.CrudOperationKey;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.meta.ResourceDescriptor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 启动期构建并冻结的 Scene Policy 注册表。 */
public final class ScenePolicyRegistry {
    private final Map<ScenePolicyKey, ScenePolicy> policies;

    /** 合并实体注解与程序式声明，校验后冻结；资源使用元数据中的规范编码。 */
    public static ScenePolicyRegistry create(EntityMetaRegistry entities, Collection<ScenePolicy> source) {
        List<ScenePolicy> policies = new ArrayList<ScenePolicy>();
        Map<String, ResourceDescriptor> resources = new LinkedHashMap<String, ResourceDescriptor>();
        for (EntityMeta entity : entities.getEntityMetas()) {
            ResourceDescriptor resource = entity.getResourceDescriptor();
            resources.put(resource.getResourceCode(), resource);
            policies.addAll(EntityActionDeclarations.policies(resource));
        }
        if (source != null) policies.addAll(source);
        for (ScenePolicy policy : policies) {
            if (policy == null) continue;
            ScenePolicyKey key = policy.getKey();
            if (!CrudOperationKey.of(CommandOperation.ACTION).equals(key.getOperationKey())) continue;
            ResourceDescriptor resource = resources.get(key.getResource());
            if (resource == null) {
                throw new ValidationException("ACTION 策略资源未注册，请使用规范资源编码: " + key);
            }
            if (key.getScene().isEmpty() || !EntityActionDeclarations.allows(resource.getEntityType(), key.getScene())) {
                throw new ValidationException("ACTION 策略超出实体动作边界或 scene 为空: " + key);
            }
        }
        return new ScenePolicyRegistry(policies);
    }

    public ScenePolicyRegistry(Collection<ScenePolicy> source) {
        Map<ScenePolicyKey, ScenePolicy> registered = new LinkedHashMap<ScenePolicyKey, ScenePolicy>();
        if (source != null) {
            for (ScenePolicy policy : source) {
                if (policy == null) continue;
                if (registered.put(policy.getKey(), policy) != null) {
                    throw new ValidationException("Scene Policy 重复注册: " + policy.getKey());
                }
            }
        }
        this.policies = Collections.unmodifiableMap(registered);
    }

    public ScenePolicy find(ScenePolicyKey key) { return policies.get(key); }
    public Map<ScenePolicyKey, ScenePolicy> snapshot() { return policies; }
}
