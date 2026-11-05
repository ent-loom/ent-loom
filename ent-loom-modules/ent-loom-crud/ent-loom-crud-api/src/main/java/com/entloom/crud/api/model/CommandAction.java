package com.entloom.crud.api.model;

import java.util.Locale;
import java.util.Objects;

/**
 * 类型化业务动作定义：Controller 调用与 Handler 注册共用同一契约。
 * 定义类应为无状态类并提供无参构造器；请求与响应为明确的普通类型，空结果使用 Void。
 */
public abstract class CommandAction<P, R> {
    private final Class<?> entityClass;
    private final String scene;
    private final Class<P> requestType;
    private final Class<R> responseType;

    protected CommandAction(Class<?> entityClass, String scene, Class<P> requestType, Class<R> responseType) {
        this.entityClass = Objects.requireNonNull(entityClass, "动作实体不能为空");
        this.scene = scene == null ? "" : scene.trim().toLowerCase(Locale.ROOT);
        if (this.scene.isEmpty()) throw new IllegalArgumentException("动作 scene 不能为空");
        this.requestType = Objects.requireNonNull(requestType, "动作请求类型不能为空");
        this.responseType = Objects.requireNonNull(responseType, "动作响应类型不能为空");
        if (requestType.isPrimitive() || responseType.isPrimitive()) {
            throw new IllegalArgumentException("动作请求与响应须使用引用类型，空值使用 Void");
        }
    }

    public final Class<?> getEntityClass() { return entityClass; }
    public final String getScene() { return scene; }
    public final Class<P> getRequestType() { return requestType; }
    public final Class<R> getResponseType() { return responseType; }
}
