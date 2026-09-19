package com.entloom.meta.core.parser;

import com.entloom.base.common.OptionalBoolean;
import com.entloom.meta.annotations.EntEntity;
import com.entloom.meta.annotations.EntField;
import com.entloom.meta.annotations.meta.EntMetaId;
import com.entloom.meta.contract.descriptor.EntFieldDescriptor;
import com.entloom.meta.contract.descriptor.MetaDescriptorProperties;
import com.entloom.meta.contract.value.MetaValueSource;
import com.entloom.meta.core.convention.RequiredInferenceContribution;
import com.entloom.meta.core.convention.RequiredInferenceContext;
import com.entloom.meta.core.convention.RequiredInferenceFilter;
import com.entloom.meta.core.convention.RequiredInferenceValue;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 输入必填推断的语义边界回归。 */
class RequiredInferenceTest {
    @Test
    void projectFilterShouldUseResolverPriorityAndKeepRuleId() {
        RequiredInferenceFilter filter = context -> new RequiredInferenceContribution(
            context.field().getName().equals("groupOnly") ? RequiredInferenceValue.TRUE : RequiredInferenceValue.FALSE,
            "project.required", "项目创建约定");
        ReflectiveEntMetaParser parser = new ReflectiveEntMetaParser(Collections.emptyList(), filter);

        EntFieldDescriptor explicit = field(parser, "optional");
        EntFieldDescriptor project = field(parser, "groupOnly");
        EntFieldDescriptor validation = field(parser, "name");

        assertEquals(false, explicit.required());
        assertEquals(MetaValueSource.META_EXPLICIT, explicit.sourcedValue(MetaDescriptorProperties.REQUIRED).source());
        assertEquals(true, project.required());
        assertEquals(MetaValueSource.MODULE_PROJECT_CONVENTION,
            project.sourcedValue(MetaDescriptorProperties.REQUIRED).source());
        assertEquals("project.required", project.sourcedValue(MetaDescriptorProperties.REQUIRED).ruleId());
        assertEquals("项目创建约定", project.sourcedValue(MetaDescriptorProperties.REQUIRED).reason());
        assertEquals(false, validation.required());
    }

    @Test
    void unsetShouldPassThroughToValidationInference() {
        RequiredInferenceFilter filter = context -> new RequiredInferenceContribution(
            RequiredInferenceValue.UNSET, "project.unset", "不处理");
        EntFieldDescriptor field = field(new ReflectiveEntMetaParser(Collections.emptyList(), filter), "name");
        assertEquals(true, field.required());
        assertEquals(MetaValueSource.INFERRED, field.sourcedValue(MetaDescriptorProperties.REQUIRED).source());
    }

    @Test
    void contextShouldAlwaysBeCreate() {
        RequiredInferenceFilter filter = context -> {
            assertEquals(RequiredInferenceContext.Scenario.CREATE, context.scenario());
            return new RequiredInferenceContribution(RequiredInferenceValue.UNSET, "scenario.create", "仅创建场景");
        };
        field(new ReflectiveEntMetaParser(Collections.emptyList(), filter), "name");
    }
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
        assertRequired("defaulted", true, MetaValueSource.INFERRED);
        assertRequired("generated", true, MetaValueSource.INFERRED);
        assertRequired("createdAt", true, MetaValueSource.INFERRED);
        assertRequired("flag", false, MetaValueSource.META_EXPLICIT);
        assertRequired("primitive", false, MetaValueSource.META_EXPLICIT);
        assertRequired("status", false, MetaValueSource.META_EXPLICIT);
        assertRequired("number", false, MetaValueSource.META_EXPLICIT);
    }

    private void assertRequired(String name, Boolean expected, MetaValueSource source) {
        EntFieldDescriptor field = field(new ReflectiveEntMetaParser(), name);
        assertEquals(expected, field.required(), name);
        assertEquals(source, field.sourcedValue(MetaDescriptorProperties.REQUIRED).source(), name);
    }

    private EntFieldDescriptor field(ReflectiveEntMetaParser parser, String name) {
        return parser.parse(Input.class).fields().stream()
            .filter(f -> f.fieldName().equals(name)).findFirst().get();
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
        @EntField(kind = com.entloom.meta.enums.EntFieldKind.ID)
        @EntMetaId(generator = EntMetaId.IdGenerator.UUID)
        @javax.validation.constraints.NotNull
        private String generated;
        /** 创建时间按既有只读约定排除。 */
        @javax.validation.constraints.NotNull
        private java.time.LocalDateTime createdAt;
        /** 无约束布尔，显式覆盖实体默认策略。 */
        @EntField(required = OptionalBoolean.FALSE)
        private Boolean flag;
        /** 基本类型的零值不代表请求中必须出现，显式覆盖实体默认策略。 */
        @EntField(required = OptionalBoolean.FALSE)
        private int primitive;
        /** 状态，参见 {@link Status}。 */
        @EntField(required = OptionalBoolean.FALSE)
        private Status status;
        /** 无约束数值。 */
        @EntField(required = OptionalBoolean.FALSE)
        private Integer number;
    }
}
