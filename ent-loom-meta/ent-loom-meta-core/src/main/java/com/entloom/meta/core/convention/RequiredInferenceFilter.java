package com.entloom.meta.core.convention;

/** 可选的项目级 required 推断策略。实现必须无副作用且可重复执行。 */
@FunctionalInterface
public interface RequiredInferenceFilter {
    /** 根据静态实体/字段元数据贡献 required 三态结果。 */
    RequiredInferenceContribution contribute(RequiredInferenceContext context);
}
