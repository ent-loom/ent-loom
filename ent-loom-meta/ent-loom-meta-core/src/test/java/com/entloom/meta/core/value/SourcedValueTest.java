package com.entloom.meta.core.value;

import com.entloom.meta.contract.value.MetaValueSource;
import com.entloom.meta.contract.value.MetaValueState;
import com.entloom.meta.contract.value.SourcedValue;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class SourcedValueTest {

    @Test
    void rule_id_should_be_observable_but_not_change_value_equality() {
        SourcedValue<String> first = SourcedValue.of(
            "Name", MetaValueSource.META_EXPLICIT, MetaValueState.EXPLICIT, true, "rule-a");
        SourcedValue<String> second = SourcedValue.of(
            "Name", MetaValueSource.META_EXPLICIT, MetaValueState.EXPLICIT, true, "rule-b");

        Assertions.assertEquals("rule-a", first.ruleId());
        Assertions.assertEquals("rule-b", second.ruleId());
        Assertions.assertEquals(first, second);
        Assertions.assertEquals(first.hashCode(), second.hashCode());
    }
}
