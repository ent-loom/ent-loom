package com.entloom.crud.core.governance.policy;

import com.entloom.crud.api.enums.CrudOperationKey;
import java.util.Locale;
import java.util.Objects;

/**
 * Scene Policy 唯一键。
 */
public final class ScenePolicyKey {
    private final String accessEntry;
    private final String resource;
    private final CrudOperationKey operationKey;
    private final String scene;

    public ScenePolicyKey(String accessEntry, String resource, CrudOperationKey operationKey, String scene) {
        this.accessEntry = normalize(accessEntry, "base");
        this.resource = require(resource, "resource");
        this.operationKey = Objects.requireNonNull(operationKey, "operationKey 不能为空");
        this.scene = normalize(scene, "");
    }

    public String getAccessEntry() { return accessEntry; }
    public String getResource() { return resource; }
    public CrudOperationKey getOperationKey() { return operationKey; }
    public String getScene() { return scene; }

    @Override
    public boolean equals(Object value) {
        if (this == value) return true;
        if (!(value instanceof ScenePolicyKey)) return false;
        ScenePolicyKey other = (ScenePolicyKey) value;
        return accessEntry.equals(other.accessEntry) && resource.equals(other.resource)
            && operationKey.equals(other.operationKey) && scene.equals(other.scene);
    }

    @Override
    public int hashCode() { return Objects.hash(accessEntry, resource, operationKey, scene); }

    @Override
    public String toString() { return accessEntry + ":" + resource + ":" + operationKey + ":" + scene; }

    private static String require(String value, String name) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(name + " 不能为空");
        return value.trim();
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim().toLowerCase(Locale.ROOT);
    }
}
