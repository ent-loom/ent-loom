package com.entloom.crud.core.governance.policy;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 场景准入策略。portal 集合为空表示不限制可信入口类型。
 */
public final class ScenePolicy {
    private final ScenePolicyKey key;
    private final String capability;
    private final Set<String> allowedPortals;

    public ScenePolicy(ScenePolicyKey key, String capability, Set<String> allowedPortals) {
        if (key == null) throw new IllegalArgumentException("key 不能为空");
        if (capability == null || capability.trim().isEmpty()) throw new IllegalArgumentException("capability 不能为空");
        this.key = key;
        this.capability = capability.trim();
        Set<String> portals = new LinkedHashSet<String>();
        if (allowedPortals != null) {
            for (String portal : allowedPortals) {
                if (portal != null && !portal.trim().isEmpty()) portals.add(portal.trim().toLowerCase(java.util.Locale.ROOT));
            }
        }
        this.allowedPortals = Collections.unmodifiableSet(portals);
    }

    public ScenePolicyKey getKey() { return key; }
    public String getCapability() { return capability; }
    public Set<String> getAllowedPortals() { return allowedPortals; }
}
