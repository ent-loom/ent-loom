package com.entloom.meta.core.convention;

import java.util.Objects;

/** 一条可诊断的 required 推断结果。 */
public final class RequiredInferenceContribution {
    private final RequiredInferenceValue value;
    private final String ruleId;
    private final String reason;

    public RequiredInferenceContribution(RequiredInferenceValue value, String ruleId, String reason) {
        this.value = Objects.requireNonNull(value, "value 不能为空");
        this.ruleId = Objects.requireNonNull(ruleId, "ruleId 不能为空");
        this.reason = Objects.requireNonNull(reason, "reason 不能为空");
    }

    public RequiredInferenceValue value() { return value; }
    public String ruleId() { return ruleId; }
    public String reason() { return reason; }
}
