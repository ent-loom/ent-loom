package com.entloom.meta.contract.contribution;

import com.entloom.meta.contract.value.MetaValueSource;

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

    /**
     * 将来源映射为统一裁决优先级。
     *
     * @param source 候选来源
     * @return 对应优先级，无法映射时返回 null
     */
    public static Priority fromSource(MetaValueSource source) {
        if (source == null) {
            return null;
        }
        switch (source) {
            case BUSINESS_EXPLICIT_OVERRIDE:
            case NATIVE_EXPLICIT:
                return MODULE_EXPLICIT;
            case META_EXPLICIT:
                return META_EXPLICIT;
            case MODULE_PROJECT_CONVENTION:
                return MODULE_PROJECT_CONVENTION;
            case META_PROJECT_CONVENTION:
                return META_PROJECT_CONVENTION;
            case MODULE_BUILT_IN_CONVENTION:
                return MODULE_BUILT_IN_CONVENTION;
            case META_BUILT_IN_CONVENTION:
                return META_BUILT_IN_CONVENTION;
            case INFERRED:
                return META_INFERENCE;
            case BUSINESS_DEFAULT_CONFIG:
            case DEFAULT:
            case DEFAULT_OR_EXPLICIT_UNKNOWN:
                return FRAMEWORK_DEFAULT;
            default:
                return null;
        }
    }
}
