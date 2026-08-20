package com.entloom.meta.core.resolution;

import com.entloom.meta.contract.contribution.Contribution;
import com.entloom.meta.contract.contribution.MetaConflictPolicy;
import com.entloom.meta.contract.contribution.Priority;
import com.entloom.meta.contract.diagnostic.MetaDiagnostic;
import com.entloom.meta.contract.diagnostic.MetaDiagnosticCode;
import com.entloom.meta.contract.diagnostic.MetaDiagnosticLevel;
import com.entloom.meta.contract.value.MetaValueSource;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class PropertyContributionResolverTest {

    @Test
    void resolver_should_resolve_each_property_independently_and_keep_provenance() {
        Contribution<String> label = contribution("label-rule", "label", "Name", Priority.META_PROJECT_CONVENTION);
        Contribution<Boolean> readOnly = Contribution.<Boolean>builder()
            .target("Order.name")
            .property("readOnly")
            .value(Boolean.TRUE)
            .source(MetaValueSource.META_EXPLICIT)
            .ruleId("readonly-rule")
            .priority(Priority.META_EXPLICIT)
            .build();

        Map<String, Contribution<?>> resolved = new PropertyContributionResolver()
            .resolve(Arrays.<Contribution<?>>asList(readOnly, label))
            .value();

        Assertions.assertEquals("Name", resolved.get("Order.name.label").value());
        Assertions.assertEquals(MetaValueSource.META_PROJECT_CONVENTION, resolved.get("Order.name.label").source());
        Assertions.assertEquals("readonly-rule", resolved.get("Order.name.readOnly").ruleId().value());
    }

    @Test
    void same_priority_conflict_should_be_deterministic_and_diagnosable() {
        Contribution<String> first = contribution("z-rule", "label", "Z", Priority.META_PROJECT_CONVENTION);
        Contribution<String> second = contribution("a-rule", "label", "A", Priority.META_PROJECT_CONVENTION);

        com.entloom.meta.contract.diagnostic.MetaDiagnosticResult<Map<String, Contribution<?>>> result =
            new PropertyContributionResolver().resolve(Arrays.<Contribution<?>>asList(first, second));

        Assertions.assertEquals("A", result.value().get("Order.name.label").value());
        MetaDiagnostic diagnostic = find(result.diagnostics(), MetaDiagnosticCode.CONTRIBUTION_SAME_PRIORITY_CONFLICT);
        Assertions.assertEquals(MetaDiagnosticLevel.ERROR, diagnostic.level());
        Assertions.assertEquals("a-rule", diagnostic.ruleId());
        Assertions.assertEquals("z-rule", diagnostic.relatedRuleId());
    }

    @Test
    void conflict_policy_should_define_warn_and_ignore_only_for_same_priority_conflicts() {
        Contribution<String> first = contribution("a-rule", "label", "A", Priority.META_PROJECT_CONVENTION);
        Contribution<String> second = contribution("b-rule", "label", "B", Priority.META_PROJECT_CONVENTION);

        com.entloom.meta.contract.diagnostic.MetaDiagnosticResult<Map<String, Contribution<?>>> warning =
            new PropertyContributionResolver(MetaConflictPolicy.WARN)
                .resolve(Arrays.<Contribution<?>>asList(first, second));
        Assertions.assertEquals(MetaDiagnosticLevel.WARN,
            find(warning.diagnostics(), MetaDiagnosticCode.CONTRIBUTION_SAME_PRIORITY_CONFLICT).level());

        com.entloom.meta.contract.diagnostic.MetaDiagnosticResult<Map<String, Contribution<?>>> ignored =
            new PropertyContributionResolver(MetaConflictPolicy.IGNORE)
                .resolve(Arrays.<Contribution<?>>asList(first, second));
        Assertions.assertTrue(ignored.diagnostics().isEmpty());
    }

    @Test
    void resolver_should_report_type_and_structural_errors() {
        Contribution<String> text = contribution("text-rule", "label", "A", Priority.META_PROJECT_CONVENTION);
        Contribution<Integer> number = Contribution.<Integer>builder()
            .target("Order.name")
            .property("label")
            .value(Integer.valueOf(1))
            .source(MetaValueSource.META_EXPLICIT)
            .ruleId("number-rule")
            .priority(Priority.META_EXPLICIT)
            .build();
        Contribution<String> invalid = Contribution.<String>builder().value("ignored").build();

        com.entloom.meta.contract.diagnostic.MetaDiagnosticResult<Map<String, Contribution<?>>> result =
            new PropertyContributionResolver().resolve(Arrays.<Contribution<?>>asList(text, number, invalid));

        Assertions.assertNotNull(find(result.diagnostics(), MetaDiagnosticCode.CONTRIBUTION_TYPE_MISMATCH));
        Assertions.assertNotNull(find(result.diagnostics(), MetaDiagnosticCode.CONTRIBUTION_STRUCTURAL_CONFLICT));
    }

    private static <T> Contribution<T> contribution(String ruleId, String property, T value, Priority priority) {
        return Contribution.<T>builder()
            .target("Order.name")
            .property(property)
            .value(value)
            .source(MetaValueSource.META_PROJECT_CONVENTION)
            .ruleId(ruleId)
            .priority(priority)
            .build();
    }

    private static MetaDiagnostic find(
        java.util.List<MetaDiagnostic> diagnostics,
        MetaDiagnosticCode code
    ) {
        for (MetaDiagnostic diagnostic : diagnostics) {
            if (diagnostic.code() == code) {
                return diagnostic;
            }
        }
        Assertions.fail("Missing diagnostic: " + code);
        return null;
    }
}
