package com.entloom.meta.core.resolution;

import com.entloom.meta.contract.contribution.Contribution;
import com.entloom.meta.contract.contribution.MetaConflictPolicy;
import com.entloom.meta.contract.diagnostic.MetaDiagnostic;
import com.entloom.meta.contract.diagnostic.MetaDiagnosticCode;
import com.entloom.meta.contract.diagnostic.MetaDiagnosticCollector;
import com.entloom.meta.contract.diagnostic.MetaDiagnosticResult;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * 按实体/字段属性独立裁决 Contribution。
 */
public final class PropertyContributionResolver {
    private static final Comparator<Contribution<?>> ORDER = new Comparator<Contribution<?>>() {
        @Override
        public int compare(Contribution<?> left, Contribution<?> right) {
            int priority = Integer.compare(right.priority().weight(), left.priority().weight());
            if (priority != 0) {
                return priority;
            }
            return left.ruleId().compareTo(right.ruleId());
        }
    };

    private final MetaConflictPolicy conflictPolicy;

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
