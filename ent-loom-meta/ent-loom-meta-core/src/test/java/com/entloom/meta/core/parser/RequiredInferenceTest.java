package com.entloom.meta.core.parser;

import com.entloom.base.common.OptionalBoolean;
import com.entloom.meta.annotations.EntEntity;
import com.entloom.meta.annotations.EntField;
import com.entloom.meta.annotations.meta.EntMetaId;
import com.entloom.meta.contract.descriptor.EntFieldDescriptor;
import com.entloom.meta.contract.descriptor.MetaDescriptorProperties;
import com.entloom.meta.contract.value.MetaValueSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 输入必填推断的语义边界回归。 */
class RequiredInferenceTest {
    @Test
    void inferOnlyFromDefaultGroupConstraintsAndRespectInputExclusions() {
        assertRequired("name", true, MetaValueSource.INFERRED);
        assertRequired("items", true, MetaValueSource.INFERRED);
        assertRequired("amount", true, MetaValueSource.INFERRED);
        assertRequired("getterValue", true, MetaValueSource.INFERRED);
        assertRequired("defaultGroup", true, MetaValueSource.INFERRED);
        assertRequired("groupOnly", null, MetaValueSource.DEFAULT_OR_EXPLICIT_UNKNOWN);
        assertRequired("optional", false, MetaValueSource.META_EXPLICIT);
        assertRequired("forced", true, MetaValueSource.META_EXPLICIT);
        assertRequired("defaulted", false, MetaValueSource.INFERRED);
        assertRequired("generated", false, MetaValueSource.INFERRED);
        assertRequired("createdAt", false, MetaValueSource.INFERRED);
        assertRequired("flag", null, MetaValueSource.DEFAULT_OR_EXPLICIT_UNKNOWN);
        assertRequired("primitive", null, MetaValueSource.DEFAULT_OR_EXPLICIT_UNKNOWN);
        assertRequired("status", null, MetaValueSource.DEFAULT_OR_EXPLICIT_UNKNOWN);
        assertRequired("number", null, MetaValueSource.DEFAULT_OR_EXPLICIT_UNKNOWN);
    }

    private void assertRequired(String name, Boolean expected, MetaValueSource source) {
        EntFieldDescriptor field = new ReflectiveEntMetaParser().parse(Input.class).fields().stream()
            .filter(f -> f.fieldName().equals(name)).findFirst().get();
        assertEquals(expected, field.required(), name);
        assertEquals(source, field.sourcedValue(MetaDescriptorProperties.REQUIRED).source(), name);
    }

    private interface Create { }
    private enum Status {
        /** 已启用。 */
        ACTIVE
    }

    @EntEntity(entity = "required_input")
    public static class Input {
        /** 名称。 */
        @javax.validation.constraints.NotBlank
        private String name;
        /** 明细。 */
        @jakarta.validation.constraints.NotEmpty
        private java.util.List<String> items;
        /** 金额。 */
        @jakarta.validation.constraints.NotNull
        private java.math.BigDecimal amount;
        /** getter 校验属性。 */
        private String getterValue;
        @javax.validation.constraints.NotNull
        public String getGetterValue() { return getterValue; }
        /** 显式默认组。 */
        @javax.validation.constraints.NotNull(groups = javax.validation.groups.Default.class)
        private Long defaultGroup;
        /** 仅创建组，不扩散成全局必填。 */
        @jakarta.validation.constraints.NotNull(groups = Create.class)
        private Long groupOnly;
        /** 显式可选覆盖约束提示。 */
        @EntField(required = OptionalBoolean.FALSE)
        @javax.validation.constraints.NotNull
        private String optional;
        /** 显式声明优先。 */
        @EntField(required = OptionalBoolean.TRUE, readOnly = OptionalBoolean.TRUE)
        private String forced;
        /** 有默认值时无需用户提供。 */
        @EntField(createDefaultValue = "false")
        @javax.validation.constraints.NotNull
        private Boolean defaulted;
        /** 自动生成主键。 */
        @EntField(com.entloom.meta.enums.EntFieldKind.ID)
        @EntMetaId(generator = EntMetaId.IdGenerator.UUID)
        @javax.validation.constraints.NotNull
        private String generated;
        /** 创建时间按既有只读约定排除。 */
        @javax.validation.constraints.NotNull
        private java.time.LocalDateTime createdAt;
        /** 无约束布尔。 */
        private Boolean flag;
        /** 基本类型的零值不代表请求中必须出现。 */
        private int primitive;
        /** 状态，参见 {@link Status}。 */
        private Status status;
        /** 无约束数值。 */
        private Integer number;
    }
}
