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
import java.util.Set;
import java.util.LinkedHashSet;
import com.entloom.crud.core.util.RouteKeyFactory;

/** 启动期构建并冻结的 Scene Policy 注册表。 */
public final class ScenePolicyRegistry {
    private final Map<ScenePolicyKey, ScenePolicy> policies;
    private final Map<String, Set<String>> actions;
    private final Map<String, Set<CrudOperationKey>> standardOperations;

    /** 合并已编译实体声明与程序式策略，校验后冻结；资源使用元数据中的规范编码。 */
    public static ScenePolicyRegistry create(EntityMetaRegistry entities, Collection<ScenePolicy> source) {
        return create(entities, source, Collections.emptyMap(), Collections.emptyMap());
    }

    /** 文件声明与程序化策略进入同一运行模型；显式空白名单关闭对应能力。 */
    public static ScenePolicyRegistry create(EntityMetaRegistry entities, Collection<ScenePolicy> source,
        Map<String, Set<String>> actions, Map<String, Set<CrudOperationKey>> standardOperations) {
        List<ScenePolicy> policies = new ArrayList<ScenePolicy>();
        Map<String, ResourceDescriptor> resources = new LinkedHashMap<String, ResourceDescriptor>();
        for (EntityMeta entity : entities.getEntityMetas()) {
            ResourceDescriptor resource = entity.getResourceDescriptor();
            resources.put(resource.getResourceCode(), resource);
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
            if (key.getScene().isEmpty() || (actions.containsKey(key.getResource()) && !actions.get(key.getResource()).contains(key.getScene()))) {
                throw new ValidationException("ACTION 策略超出实体动作边界或 scene 为空: " + key);
            }
        }
        return new ScenePolicyRegistry(policies, actions, standardOperations);
    }

    public ScenePolicyRegistry(Collection<ScenePolicy> source) {
        this(source, Collections.emptyMap(), Collections.emptyMap());
    }

    public ScenePolicyRegistry(Collection<ScenePolicy> source, Map<String, Set<String>> actions,
        Map<String, Set<CrudOperationKey>> standardOperations) {
        this.actions = freeze(actions);
        this.standardOperations = freeze(standardOperations);
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

    private static <T> Map<String, Set<T>> freeze(Map<String, Set<T>> source) {
        Map<String, Set<T>> copy = new LinkedHashMap<>();
        source.forEach((resource, values) -> copy.put(resource, Collections.unmodifiableSet(new LinkedHashSet<>(values))));
        return Collections.unmodifiableMap(copy);
    }

    public boolean allowsAction(String resource, String scene) {
        return !actions.containsKey(resource) || actions.get(resource).contains(RouteKeyFactory.normalizeScene(scene));
    }

    public boolean declaresAction(String resource, String scene) {
        if (!allowsAction(resource, scene)) return false;
        String normalized = RouteKeyFactory.normalizeScene(scene);
        return policies.keySet().stream().anyMatch(key -> key.getResource().equals(resource)
            && CrudOperationKey.of(CommandOperation.ACTION).equals(key.getOperationKey()) && key.getScene().equals(normalized));
    }

    public boolean allowsStandard(String resource, CrudOperationKey operation) {
        return !standardOperations.containsKey(resource) || standardOperations.get(resource).contains(operation);
    }

    public ScenePolicy find(ScenePolicyKey key) { return policies.get(key); }
    public Map<ScenePolicyKey, ScenePolicy> snapshot() { return policies; }
}
