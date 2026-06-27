package com.entloom.crud.core.runtime.router;

import com.entloom.crud.api.enums.CrudOperationKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * CRUD 路由键值对象。
 */
public final class CrudRouteKey {
    private final List<String> entityTypeNames;
    private final CrudOperationKey operationKey;
    private final String scene;

    public CrudRouteKey(List<String> entityTypeNames, CrudOperationKey operationKey) {
        this(entityTypeNames, operationKey, null);
    }

    public CrudRouteKey(List<String> entityTypeNames, CrudOperationKey operationKey, String scene) {
        if (entityTypeNames == null || entityTypeNames.isEmpty()) {
            throw new IllegalArgumentException("entityTypeNames 不能为空");
        }
        List<String> normalized = new ArrayList<String>(entityTypeNames.size());
        for (String entityTypeName : entityTypeNames) {
            if (entityTypeName == null || entityTypeName.trim().isEmpty()) {
                throw new IllegalArgumentException("entityTypeName 不能为空");
            }
            normalized.add(entityTypeName.trim());
        }
        if (operationKey == null) {
            throw new IllegalArgumentException("operationKey 不能为空");
        }
        this.entityTypeNames = Collections.unmodifiableList(normalized);
        this.operationKey = operationKey;
        this.scene = normalizeScene(scene);
    }

    public List<String> getEntityTypeNames() {
        return entityTypeNames;
    }

    public String getOperation() {
        return operationKey.getOperation();
    }

    public CrudOperationKey getOperationKey() {
        return operationKey;
    }

    public String getScene() {
        return scene;
    }

    public boolean matchesRootTypeAndOperation(Class<?> rootType, String operation) {
        if (rootType == null || operation == null) {
            return false;
        }
        return Objects.equals(this.operationKey.getOperation(), operation)
            && !entityTypeNames.isEmpty()
            && Objects.equals(entityTypeNames.get(0), rootType.getName());
    }

    public boolean matchesRootTypeOperationAndScene(Class<?> rootType, String operation, String scene) {
        return matchesRootTypeAndOperation(rootType, operation) && Objects.equals(this.scene, normalizeScene(scene));
    }

    @Override
    public String toString() {
        String route = entityTypeNames.stream().collect(Collectors.joining(">")) + "|" + operationKey;
        if (scene.isEmpty()) {
            return route;
        }
        return route + "|" + scene;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CrudRouteKey)) {
            return false;
        }
        CrudRouteKey that = (CrudRouteKey) o;
        return Objects.equals(entityTypeNames, that.entityTypeNames)
            && Objects.equals(operationKey, that.operationKey)
            && Objects.equals(scene, that.scene);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entityTypeNames, operationKey, scene);
    }

    private static String normalizeScene(String scene) {
        return scene == null ? "" : scene.trim().toLowerCase(Locale.ROOT);
    }
}
