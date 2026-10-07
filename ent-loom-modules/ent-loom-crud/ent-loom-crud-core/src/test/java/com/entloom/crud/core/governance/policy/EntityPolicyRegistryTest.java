package com.entloom.crud.core.governance.policy;

import com.entloom.crud.api.access.AccessSubjectType;
import com.entloom.crud.api.access.BusinessIdentity;
import com.entloom.crud.api.access.BusinessOperation;
import com.entloom.crud.core.exception.PermissionDeniedException;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.governance.identity.BusinessIdentityRegistration;
import com.entloom.crud.core.governance.identity.BusinessIdentityRegistry;
import com.entloom.crud.core.runtime.meta.EntityFieldMeta;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.ResourceDescriptor;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityPolicyRegistryTest {
    private static final PolicyIdentityBranch TEACHER = PolicyIdentityBranch.identity("teacher");

    @Test
    void compiles_one_template_for_multiple_entities_with_independent_relation_fields() {
        EntityPolicyTemplate template = template("record", "ProjectPolicies#record", "student");
        EntityPolicyRegistry registry = registry(
            Arrays.asList(meta(Order.class, "order", "studentId"), meta(Leave.class, "leave", "pupilId")),
            Collections.singletonList(template),
            Arrays.asList(binding(Order.class, "record", "student", "studentId", "Order#policy"),
                binding(Leave.class, "record", "student", "pupilId", "Leave#policy")),
            catalog("order", "leave"));

        EntityPolicyRegistry.EffectiveRule order = registry.requireDeclaration(Order.class,
            new BusinessOperation("order", "view"), TEACHER);
        EntityPolicyRegistry.EffectiveRule leave = registry.requireDeclaration(Leave.class,
            new BusinessOperation("leave", "view"), TEACHER);
        assertEquals("studentId", order.getRelationFields().get("student"));
        assertEquals("pupilId", leave.getRelationFields().get("student"));
        assertEquals("ProjectPolicies#record", order.getTemplateSource());
        assertEquals("Order#policy", order.getBindingSource());
        assertThrows(UnsupportedOperationException.class, () -> order.getRelationFields().clear());
        assertThrows(PermissionDeniedException.class, () -> registry.requireDeclaration(Order.class,
            new BusinessOperation("leave", "view"), TEACHER));
        assertThrows(PermissionDeniedException.class, () -> registry.requireDeclaration(String.class,
            new BusinessOperation("order", "view"), TEACHER));
    }

    @Test
    void public_and_identity_rules_are_distinct() {
        EntityPolicyTemplate template = new EntityPolicyTemplate("record", "ProjectPolicies#record", Arrays.asList(
            rule("view", TEACHER, "student"), rule("view", PolicyIdentityBranch.publicBranch())));
        EntityPolicyRegistry registry = registry(Collections.singletonList(meta(Order.class, "order", "studentId")),
            Collections.singletonList(template),
            Collections.singletonList(binding(Order.class, "record", "student", "studentId", "Order#policy")),
            catalog("order"));
        assertTrue(registry.requireDeclaration(Order.class, new BusinessOperation("order", "view"),
            PolicyIdentityBranch.publicBranch()).getRelationFields().isEmpty());
        assertEquals("studentId", registry.requireDeclaration(Order.class, new BusinessOperation("order", "view"),
            TEACHER).getRelationFields().get("student"));
        assertThrows(PermissionDeniedException.class, () -> registry.requireDeclaration(Order.class,
            new BusinessOperation("order", "view"), PolicyIdentityBranch.identity("parent")));
    }

    @Test
    void registered_entity_without_binding_is_denied() {
        EntityPolicyRegistry registry = registry(
            Arrays.asList(meta(Order.class, "order", "studentId"), meta(Leave.class, "leave", "pupilId")),
            Collections.singletonList(template("record", "Project#record", "student")),
            Collections.singletonList(binding(Order.class, "record", "student", "studentId", "Order#policy")),
            catalog("order", "leave"));
        assertThrows(PermissionDeniedException.class, () -> registry.requireDeclaration(Leave.class,
            new BusinessOperation("leave", "view"), TEACHER));
    }

    @Test
    void rejects_unknown_identity_action_and_missing_relation() {
        assertError("未注册身份", Collections.singletonList(template("record", "Project#record", "student",
                PolicyIdentityBranch.identity("parent"))),
            Collections.singletonList(binding(Order.class, "record", "student", "studentId", "Order#policy")),
            catalog("order"));
        assertError("未注册操作", Collections.singletonList(new EntityPolicyTemplate("record", "Project#record",
                Collections.singletonList(rule("refund", TEACHER, "student")))),
            Collections.singletonList(binding(Order.class, "record", "student", "studentId", "Order#policy")),
            catalog("order"));
        assertError("缺少关系角色绑定", Collections.singletonList(template("record", "Project#record", "student")),
            Collections.singletonList(new EntityPolicyBinding(Order.class, "record", "Order#policy", Collections.emptyMap())),
            catalog("order"));
    }

    @Test
    void rejects_unknown_field_surplus_role_and_duplicate_sources() {
        EntityPolicyTemplate template = template("record", "Project#record", "student");
        assertError("未知字段", Collections.singletonList(template),
            Collections.singletonList(binding(Order.class, "record", "student", "missing", "Order#policy")),
            catalog("order"));
        Map<String, String> surplus = new LinkedHashMap<String, String>();
        surplus.put("student", "studentId");
        surplus.put("unused", "id");
        assertError("未被有效规则使用", Collections.singletonList(template),
            Collections.singletonList(new EntityPolicyBinding(Order.class, "record", "Order#policy", surplus)),
            catalog("order"));
        ValidationException duplicateTemplate = assertThrows(ValidationException.class, () -> registry(
            Collections.singletonList(meta(Order.class, "order", "studentId")),
            Arrays.asList(template, template("record", "Other#record", "student")),
            Collections.emptyList(), catalog("order")));
        assertTrue(duplicateTemplate.getMessage().contains("Project#record"));
        assertTrue(duplicateTemplate.getMessage().contains("Other#record"));
        ValidationException duplicateBinding = assertThrows(ValidationException.class, () -> registry(
            Collections.singletonList(meta(Order.class, "order", "studentId")), Collections.singletonList(template),
            Arrays.asList(binding(Order.class, "record", "student", "studentId", "Order#policy"),
                binding(Order.class, "record", "student", "studentId", "Other#policy")), catalog("order")));
        assertTrue(duplicateBinding.getMessage().contains("Order#policy"));
        assertTrue(duplicateBinding.getMessage().contains("Other#policy"));
    }

    @Test
    void rejects_unknown_entity_template_and_duplicate_template_rule() {
        EntityPolicyTemplate template = template("record", "Project#record", "student");
        assertError("实体未注册", Collections.singletonList(template),
            Collections.singletonList(binding(Leave.class, "record", "student", "studentId", "Leave#policy")),
            catalog("order"));
        assertError("模板未注册", Collections.singletonList(template),
            Collections.singletonList(binding(Order.class, "missing", "student", "studentId", "Order#policy")),
            catalog("order"));
        assertThrows(ValidationException.class, () -> new EntityPolicyTemplate("record", "Project#record",
            Arrays.asList(rule("view", TEACHER), rule("view", TEACHER, "student"))));
    }

    @Test
    void rejects_two_entities_with_the_same_canonical_resource() {
        ValidationException error = assertThrows(ValidationException.class, () -> registry(
            Arrays.asList(meta(Order.class, "order", "studentId"), meta(Leave.class, "order", "pupilId")),
            Collections.emptyList(), Collections.emptyList(), catalog("order")));
        assertTrue(error.getMessage().contains(Order.class.getName()));
        assertTrue(error.getMessage().contains(Leave.class.getName()));
    }

    @Test
    void replacement_uses_only_the_new_relation_contract() {
        EntityPolicyOverride replacement = EntityPolicyOverride.replace(rule("view", TEACHER, "school"),
            "Order#teacherView");
        EntityPolicyBinding binding = bindingWithOverrides(Collections.singletonMap("school", "studentId"), replacement);
        EntityPolicyRegistry registry = registry(Collections.singletonList(meta(Order.class, "order", "studentId")),
            Collections.singletonList(template("record", "Project#record", "student")),
            Collections.singletonList(binding), catalog("order"));

        EntityPolicyRegistry.EffectiveRule effective = registry.requireDeclaration(Order.class,
            new BusinessOperation("order", "view"), TEACHER);
        assertEquals(Collections.singletonMap("school", "studentId"), effective.getRelationFields());
        assertEquals("Project#record", effective.getTemplateSource());
        assertEquals("Order#teacherView", effective.getRuleSource());
    }

    @Test
    void add_and_remove_are_explicit_per_operation_and_identity_branch() {
        EntityPolicyTemplate template = new EntityPolicyTemplate("record", "Project#record", Arrays.asList(
            rule("view", TEACHER, "student"), rule("view", PolicyIdentityBranch.publicBranch())));
        EntityPolicyBinding binding = bindingWithOverrides(Collections.singletonMap("student", "studentId"),
            EntityPolicyOverride.add(rule("refund", TEACHER, "student"), "Order#refund"),
            EntityPolicyOverride.remove("view", PolicyIdentityBranch.publicBranch(), "Order#hidePublic"));
        EntityPolicyRegistry registry = registry(Collections.singletonList(meta(Order.class, "order", "studentId")),
            Collections.singletonList(template), Collections.singletonList(binding), catalogActions("order", "view", "refund"));

        assertEquals("Order#refund", registry.requireDeclaration(Order.class,
            new BusinessOperation("order", "refund"), TEACHER).getRuleSource());
        assertEquals("Project#record", registry.requireDeclaration(Order.class,
            new BusinessOperation("order", "view"), TEACHER).getRuleSource());
        assertThrows(PermissionDeniedException.class, () -> registry.requireDeclaration(Order.class,
            new BusinessOperation("order", "view"), PolicyIdentityBranch.publicBranch()));
        assertThrows(PermissionDeniedException.class, () -> registry.requireDeclaration(Order.class,
            new BusinessOperation("order", "refund"), PolicyIdentityBranch.publicBranch()));
    }

    @Test
    void invalid_override_targets_and_duplicate_sources_fail_at_compile_time() {
        EntityPolicyTemplate template = template("record", "Project#record", "student");
        EntityPolicyOverride existingAdd = EntityPolicyOverride.add(rule("view", TEACHER, "student"), "Order#add");
        EntityPolicyOverride missingReplace = EntityPolicyOverride.replace(rule("refund", TEACHER), "Order#replace");
        EntityPolicyOverride missingRemove = EntityPolicyOverride.remove("refund", TEACHER, "Order#remove");
        ValidationException addError = overrideError(template, catalog("order"), existingAdd);
        assertTrue(addError.getMessage().contains("Project#record"));
        assertTrue(addError.getMessage().contains("Order#add"));
        assertTrue(overrideError(template, catalog("order"), missingReplace).getMessage().contains("Order#replace"));
        assertTrue(overrideError(template, catalog("order"), missingRemove).getMessage().contains("Order#remove"));

        ValidationException duplicate = overrideError(template, catalog("order"),
            EntityPolicyOverride.replace(rule("view", TEACHER), "Order#first"),
            EntityPolicyOverride.remove("view", TEACHER, "Order#second"));
        assertTrue(duplicate.getMessage().contains("Order#first"));
        assertTrue(duplicate.getMessage().contains("Order#second"));
    }

    @Test
    void added_rule_still_requires_registered_identity_action_and_relation_field() {
        EntityPolicyTemplate template = template("record", "Project#record", "student");
        assertTrue(overrideError(template, catalogActions("order", "view", "refund"),
            EntityPolicyOverride.add(rule("refund", PolicyIdentityBranch.identity("parent"), "student"),
                "Order#parentRefund")).getMessage().contains("未注册身份"));
        assertTrue(overrideError(template, catalog("order"),
            EntityPolicyOverride.add(rule("refund", TEACHER, "student"), "Order#refund"))
            .getMessage().contains("未注册操作"));
        assertTrue(overrideError(template, catalogActions("order", "view", "refund"),
            EntityPolicyOverride.add(rule("refund", TEACHER, "school"), "Order#refund"))
            .getMessage().contains("缺少关系角色绑定"));
    }

    private static ValidationException overrideError(EntityPolicyTemplate template,
                                                     BusinessOperationCatalog catalog,
                                                     EntityPolicyOverride... overrides) {
        return assertThrows(ValidationException.class, () -> registry(
            Collections.singletonList(meta(Order.class, "order", "studentId")),
            Collections.singletonList(template),
            Collections.singletonList(bindingWithOverrides(Collections.singletonMap("student", "studentId"), overrides)),
            catalog));
    }

    private static EntityPolicyBinding bindingWithOverrides(Map<String, String> relations,
                                                              EntityPolicyOverride... overrides) {
        return new EntityPolicyBinding(Order.class, "record", "Order#policy", relations, Arrays.asList(overrides));
    }

    private static void assertError(String fragment, Collection<EntityPolicyTemplate> templates,
                                    Collection<EntityPolicyBinding> bindings, BusinessOperationCatalog catalog) {
        ValidationException error = assertThrows(ValidationException.class, () -> registry(
            Collections.singletonList(meta(Order.class, "order", "studentId")), templates, bindings, catalog));
        assertTrue(error.getMessage().contains(fragment), error.getMessage());
    }

    private static EntityPolicyRegistry registry(Collection<EntityMeta> metas, Collection<EntityPolicyTemplate> templates,
                                                 Collection<EntityPolicyBinding> bindings, BusinessOperationCatalog catalog) {
        BusinessIdentityRegistry identities = new BusinessIdentityRegistry(Collections.singletonList(
            new BusinessIdentityRegistration<TeacherIdentity>("teacher", TeacherIdentity.class,
                (subject, identityId) -> subject.getType() == AccessSubjectType.HUMAN
                    ? new TeacherIdentity(identityId) : null)));
        return new EntityPolicyRegistry(metas, templates, bindings, catalog, identities);
    }

    private static EntityPolicyTemplate template(String code, String source, String role) {
        return template(code, source, role, TEACHER);
    }

    private static EntityPolicyTemplate template(String code, String source, String role, PolicyIdentityBranch branch) {
        return new EntityPolicyTemplate(code, source, Collections.singletonList(rule("view", branch, role)));
    }

    private static EntityPolicyTemplate.Rule rule(String operation, PolicyIdentityBranch branch, String... roles) {
        return new EntityPolicyTemplate.Rule(operation, branch, Arrays.asList(roles));
    }

    private static EntityPolicyBinding binding(Class<?> type, String template, String role, String field, String source) {
        return new EntityPolicyBinding(type, template, source, Collections.singletonMap(role, field));
    }

    private static EntityMeta meta(Class<?> type, String resource, String relationField) {
        Map<String, EntityFieldMeta> fields = new LinkedHashMap<String, EntityFieldMeta>();
        fields.put("id", new EntityFieldMeta("id", String.class, "id", false, false, true, true));
        fields.put(relationField, new EntityFieldMeta(relationField, String.class, relationField, false, false, true, true));
        return new EntityMeta(type, new ResourceDescriptor(type, resource, null, Collections.emptyList()),
            resource, "id", null, fields);
    }

    private static BusinessOperationCatalog catalog(String... resources) {
        java.util.List<ResourceDescriptor> descriptors = new java.util.ArrayList<ResourceDescriptor>();
        java.util.List<BusinessOperationDeclaration> declarations = new java.util.ArrayList<BusinessOperationDeclaration>();
        for (String resource : resources) {
            descriptors.add(new ResourceDescriptor(null, resource, null, Collections.emptyList()));
            declarations.add(new BusinessOperationDeclaration(new BusinessOperation(resource, "view"), "Project#view"));
        }
        return new BusinessOperationCatalog(descriptors, declarations);
    }

    private static BusinessOperationCatalog catalogActions(String resource, String... actions) {
        ResourceDescriptor descriptor = new ResourceDescriptor(null, resource, null, Collections.emptyList());
        java.util.List<BusinessOperationDeclaration> declarations = new java.util.ArrayList<BusinessOperationDeclaration>();
        for (String action : actions) {
            declarations.add(new BusinessOperationDeclaration(new BusinessOperation(resource, action), "Project#" + action));
        }
        return new BusinessOperationCatalog(Collections.singletonList(descriptor), declarations);
    }

    private static final class Order {
    }

    private static final class Leave {
    }

    private static final class TeacherIdentity implements BusinessIdentity {
        private final String id;

        private TeacherIdentity(String id) {
            this.id = id;
        }

        @Override
        public String getIdentityCode() {
            return "teacher";
        }

        @Override
        public String getIdentityId() {
            return id;
        }
    }
}
