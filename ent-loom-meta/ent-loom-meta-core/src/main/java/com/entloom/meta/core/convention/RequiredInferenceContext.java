package com.entloom.meta.core.convention;

import java.lang.reflect.Field;
import java.util.Objects;

/** CREATE 场景下的必填推断输入；不携带请求或运行时上下文。 */
public final class RequiredInferenceContext {
    /** 首阶段唯一支持的场景。 */
    public enum Scenario { /** 创建。 */ CREATE }

    private final Class<?> entityClass;
    private final Field field;
    private final Scenario scenario;

    public RequiredInferenceContext(Class<?> entityClass, Field field, Scenario scenario) {
        this.entityClass = Objects.requireNonNull(entityClass, "entityClass 不能为空");
        this.field = Objects.requireNonNull(field, "field 不能为空");
        this.scenario = Objects.requireNonNull(scenario, "scenario 不能为空");
    }

    public Class<?> entityClass() { return entityClass; }
    public Field field() { return field; }
    public Scenario scenario() { return scenario; }
}
