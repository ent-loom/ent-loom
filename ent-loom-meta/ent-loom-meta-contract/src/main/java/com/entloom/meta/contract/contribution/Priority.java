package com.entloom.meta.contract.contribution;

/**
 * Meta Contribution 优先级。
 */
public enum Priority {
    /** Module 显式注解。 */
    MODULE_EXPLICIT(700),
    /** Meta 显式注解。 */
    META_EXPLICIT(600),
    /** Module 项目约定。 */
    MODULE_PROJECT_CONVENTION(500),
    /** Meta 项目约定。 */
    META_PROJECT_CONVENTION(400),
    /** Module 内置约定。 */
    MODULE_BUILT_IN_CONVENTION(300),
    /** Meta 内置约定。 */
    META_BUILT_IN_CONVENTION(250),
    /** Meta 或 Java 推断。 */
    META_INFERENCE(200),
    /** 框架默认值。 */
    FRAMEWORK_DEFAULT(100);

    private final int weight;

    Priority(int weight) {
        this.weight = weight;
    }

    public int weight() {
        return weight;
    }
}
