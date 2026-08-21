package com.entloom.meta.contract.contribution;

import com.entloom.meta.contract.diagnostic.MetaDiagnostic;
import com.entloom.meta.contract.diagnostic.MetaDiagnosticCode;
import com.entloom.meta.contract.diagnostic.MetaDiagnosticCollector;
import com.entloom.meta.contract.diagnostic.MetaDiagnosticResult;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * 公共属性级 Contribution 裁决器。
 *
 * <p>Meta、CRUD 等模块共享同一实现，避免模块 Adapter 自行解释优先级。</p>
 */
public final class PropertyContributionResolver {
    private static final Comparator<Contribution<?>> ORDER = new Comparator<Contribution<?>>() {
        @Override
        public int compare(Contribution<?> left, Contribution<?> right) {
            int priority = Integer.compare(right.priority().weight(), left.priority().weight());
            if (priority != 0) {
                return priority;
            }
            int ruleId = left.ruleId().compareTo(right.ruleId());
            if (ruleId != 0) {
                return ruleId;
            }
            int value = stableValue(left.value()).compareTo(stableValue(right.value()));
            if (value != 0) {
                return value;
            }
            return sourceName(left).compareTo(sourceName(right));
        }
    };

    private final MetaConflictPolicy conflictPolicy;

    /**
     * 生成只用于同级候选裁决的稳定值键。
     *
     * <p>数组、Set、Map 会按结构递归规范化，普通 Collection 保留迭代顺序。无法确认内容稳定性的
     * 自定义对象只按类型作为不透明值处理，不调用默认 {@code Object.toString()}，避免把对象地址带入
     * 裁决结果；这类对象应由调用方保证同一 ruleId 只产生一个候选。</p>
     */
    private static String stableValue(Object value) {
        return stableValue(value, new IdentityHashMap<Object, Boolean>());
    }

    private static String stableValue(Object value, IdentityHashMap<Object, Boolean> visiting) {
        if (value == null) {
            return "null";
        }
        Class<?> valueType = value.getClass();
        if (value instanceof Enum<?>) {
            Enum<?> enumValue = (Enum<?>) value;
            return valueType.getName() + "#" + enumValue.name();
        }
        if (value instanceof CharSequence
            || value instanceof Character
            || value instanceof Boolean
            || value instanceof Number
            || value instanceof Class<?>
            || value instanceof java.util.Date
            || value instanceof java.time.temporal.TemporalAccessor
            || value instanceof java.time.temporal.TemporalAmount
            || value instanceof java.util.UUID) {
            return valueType.getName() + "#" + String.valueOf(value);
        }
        if (valueType.isArray()) {
            if (visiting.put(value, Boolean.TRUE) != null) {
                return valueType.getName() + "#<cycle>";
            }
            try {
                List<String> elements = new ArrayList<String>(Array.getLength(value));
                for (int i = 0; i < Array.getLength(value); i++) {
                    elements.add(stableValue(Array.get(value, i), visiting));
                }
                return valueType.getName() + "[" + join(elements) + "]";
            } finally {
                visiting.remove(value);
            }
        }
        if (value instanceof Map<?, ?>) {
            if (visiting.put(value, Boolean.TRUE) != null) {
                return valueType.getName() + "#<cycle>";
            }
            try {
                List<String> entries = new ArrayList<String>(((Map<?, ?>) value).size());
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                    entries.add(stableValue(entry.getKey(), visiting)
                        + "=" + stableValue(entry.getValue(), visiting));
                }
                Collections.sort(entries);
                return valueType.getName() + "{" + join(entries) + "}";
            } finally {
                visiting.remove(value);
            }
        }
        if (value instanceof Set<?>) {
            if (visiting.put(value, Boolean.TRUE) != null) {
                return valueType.getName() + "#<cycle>";
            }
            try {
                List<String> elements = new ArrayList<String>(((Set<?>) value).size());
                for (Object element : (Set<?>) value) {
                    elements.add(stableValue(element, visiting));
                }
                Collections.sort(elements);
                return valueType.getName() + "{" + join(elements) + "}";
            } finally {
                visiting.remove(value);
            }
        }
        if (value instanceof Collection<?>) {
            if (visiting.put(value, Boolean.TRUE) != null) {
                return valueType.getName() + "#<cycle>";
            }
            try {
                List<String> elements = new ArrayList<String>(((Collection<?>) value).size());
                for (Object element : (Collection<?>) value) {
                    elements.add(stableValue(element, visiting));
                }
                return valueType.getName() + "[" + join(elements) + "]";
            } finally {
                visiting.remove(value);
            }
        }
        return valueType.getName() + "#<opaque>";
    }

    private static String join(List<String> values) {
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            if (result.length() > 0) {
                result.append('|');
            }
            result.append(value.length()).append(':').append(value);
        }
        return result.toString();
    }

    private static String sourceName(Contribution<?> contribution) {
        return contribution.source() == null ? "" : contribution.source().name();
    }

    public PropertyContributionResolver() {
        this(MetaConflictPolicy.FAIL);
    }

    public PropertyContributionResolver(MetaConflictPolicy conflictPolicy) {
        this.conflictPolicy = conflictPolicy == null ? MetaConflictPolicy.FAIL : conflictPolicy;
    }

    public MetaDiagnosticResult<Map<String, Contribution<?>>> resolve(
        Collection<? extends Contribution<?>> contributions
    ) {
        MetaDiagnosticCollector diagnostics = new MetaDiagnosticCollector();
        Map<String, List<Contribution<?>>> groups = new TreeMap<String, List<Contribution<?>>>();
        if (contributions == null) {
            return MetaDiagnosticResult.of(Collections.<String, Contribution<?>>emptyMap(), diagnostics.diagnostics());
        }
        for (Contribution<?> contribution : contributions) {
            if (!validate(contribution, diagnostics)) {
                continue;
            }
            String key = contribution.targetKey() + "." + contribution.property();
            List<Contribution<?>> group = groups.get(key);
            if (group == null) {
                group = new ArrayList<Contribution<?>>();
                groups.put(key, group);
            }
            group.add(contribution);
        }

        Map<String, Contribution<?>> resolved = new TreeMap<String, Contribution<?>>();
        for (Map.Entry<String, List<Contribution<?>>> entry : groups.entrySet()) {
            List<Contribution<?>> candidates = entry.getValue();
            Collections.sort(candidates, ORDER);
            validateTypes(entry.getKey(), candidates, diagnostics);
            Contribution<?> winner = candidates.get(0);
            addSamePriorityDiagnostic(entry.getKey(), candidates, diagnostics);
            resolved.put(entry.getKey(), winner);
        }
        return MetaDiagnosticResult.of(resolved, diagnostics.diagnostics());
    }

    private boolean validate(Contribution<?> contribution, MetaDiagnosticCollector diagnostics) {
        if (contribution == null
            || contribution.targetKey() == null
            || contribution.property() == null
            || contribution.ruleId() == null
            || contribution.priority() == null) {
            diagnostics.add(MetaDiagnostic.error(MetaDiagnosticCode.CONTRIBUTION_STRUCTURAL_CONFLICT)
                .property(contribution == null ? null : contribution.property())
                .location(contribution == null ? null : contribution.targetKey())
                .ruleId(contribution == null || contribution.ruleId() == null
                    ? null
                    : contribution.ruleId().value())
                .message("Contribution 必须包含 target、property、ruleId 和 priority")
                .build());
            return false;
        }
        return true;
    }

    private void validateTypes(
        String key,
        List<Contribution<?>> candidates,
        MetaDiagnosticCollector diagnostics
    ) {
        Class<?> expectedType = null;
        Contribution<?> expected = null;
        for (Contribution<?> candidate : candidates) {
            if (candidate.value() == null) {
                continue;
            }
            if (expectedType == null) {
                expectedType = candidate.value().getClass();
                expected = candidate;
                continue;
            }
            Class<?> actualType = candidate.value().getClass();
            if (!expectedType.isAssignableFrom(actualType) && !actualType.isAssignableFrom(expectedType)) {
                diagnostics.add(MetaDiagnostic.error(MetaDiagnosticCode.CONTRIBUTION_TYPE_MISMATCH)
                    .property(candidate.property())
                    .location(key)
                    .source(candidate.source())
                    .ruleId(candidate.ruleId().value())
                    .relatedSource(expected.source())
                    .relatedRuleId(expected.ruleId().value())
                    .message("同一属性的 Contribution 类型不一致: "
                        + expectedType.getName() + " / " + actualType.getName())
                    .build());
            }
        }
    }

    private void addSamePriorityDiagnostic(
        String key,
        List<Contribution<?>> candidates,
        MetaDiagnosticCollector diagnostics
    ) {
        Contribution<?> winner = candidates.get(0);
        for (int i = 1; i < candidates.size(); i++) {
            Contribution<?> candidate = candidates.get(i);
            if (candidate.priority() != winner.priority()) {
                break;
            }
            if (Objects.equals(winner.value(), candidate.value())) {
                continue;
            }
            if (conflictPolicy == MetaConflictPolicy.IGNORE) {
                continue;
            }
            MetaDiagnostic.Builder builder = conflictPolicy == MetaConflictPolicy.WARN
                ? MetaDiagnostic.warn(MetaDiagnosticCode.CONTRIBUTION_SAME_PRIORITY_CONFLICT)
                : MetaDiagnostic.error(MetaDiagnosticCode.CONTRIBUTION_SAME_PRIORITY_CONFLICT);
            diagnostics.add(builder
                .property(winner.property())
                .location(key)
                .source(winner.source())
                .ruleId(winner.ruleId().value())
                .relatedSource(candidate.source())
                .relatedRuleId(candidate.ruleId().value())
                .message("同级 Contribution 值冲突，已按 RuleId 稳定选择: " + winner.ruleId())
                .build());
        }
    }
}
