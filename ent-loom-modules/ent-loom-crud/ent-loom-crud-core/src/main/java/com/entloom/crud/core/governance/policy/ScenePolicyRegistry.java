package com.entloom.crud.core.governance.policy;

import com.entloom.crud.core.exception.ValidationException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 启动期构建并冻结的 Scene Policy 注册表。 */
public final class ScenePolicyRegistry {
    private final Map<ScenePolicyKey, ScenePolicy> policies;

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
