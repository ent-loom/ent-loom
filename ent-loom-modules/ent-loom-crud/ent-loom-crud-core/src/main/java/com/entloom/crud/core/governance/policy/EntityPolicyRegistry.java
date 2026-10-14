package com.entloom.crud.core.governance.policy;

import com.entloom.crud.api.access.AccessBranch;
import com.entloom.crud.api.access.BusinessOperation;
import com.entloom.crud.core.exception.PermissionDeniedException;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.governance.authorization.AuthorizationFields;
import com.entloom.crud.core.governance.identity.BusinessIdentityRegistry;
import com.entloom.crud.core.runtime.meta.EntityFieldMeta;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 将项目模板与实体字段绑定编译为不可变声明；声明存在不代表已授权。 */
public final class EntityPolicyRegistry {
    private final Map<Class<?>, EffectiveEntityPolicy> entities;
    private final Map<String, EffectiveEntityPolicy> resources;

    public EntityPolicyRegistry(
        Collection<EntityMeta> entityMetas,
        Collection<EntityPolicyTemplate> templates,
        Collection<EntityPolicyBinding> bindings,
        BusinessOperationCatalog operations,
        BusinessIdentityRegistry identities
    ) {
        Objects.requireNonNull(entityMetas, "实体元数据不能为空");
        Objects.requireNonNull(templates, "策略模板不能为空");
        Objects.requireNonNull(bindings, "实体绑定不能为空");
        Objects.requireNonNull(operations, "操作目录不能为空");
        Objects.requireNonNull(identities, "身份注册表不能为空");

        Map<Class<?>, EntityMeta> metas = new LinkedHashMap<Class<?>, EntityMeta>();
        Map<String, Class<?>> resourceTypes = new LinkedHashMap<String, Class<?>>();
        for (EntityMeta meta : entityMetas) {
            if (meta == null || meta.getEntityType() == null) {
                throw new ValidationException("实体元数据及类型不能为空");
            }
            if (metas.put(meta.getEntityType(), meta) != null) {
                throw new ValidationException("实体元数据重复: " + meta.getEntityType().getName());
            }
            String resourceCode = meta.getResourceDescriptor().getResourceCode();
            Class<?> previousType = resourceTypes.put(resourceCode, meta.getEntityType());
            if (previousType != null) {
                throw new ValidationException("规范资源 code 重复: " + resourceCode + "，实体: "
                    + previousType.getName() + " / " + meta.getEntityType().getName());
            }
        }
        Map<String, EntityPolicyTemplate> byCode = new LinkedHashMap<String, EntityPolicyTemplate>();
        for (EntityPolicyTemplate template : templates) {
            if (template == null) {
                throw new ValidationException("策略模板不能为空");
            }
            EntityPolicyTemplate previous = byCode.put(template.getCode(), template);
            if (previous != null) {
                throw new ValidationException("策略模板重复: " + template.getCode() + "，来源: "
                    + previous.getSource() + " / " + template.getSource());
            }
            for (EntityPolicyTemplate.Rule rule : template.getRules()) {
                PolicyIdentityBranch branch = rule.getBranch();
                if (branch.getBranch() == AccessBranch.AUTHENTICATED
                    && !identities.snapshot().containsKey(branch.getIdentityCode())) {
                    throw new ValidationException("模板引用未注册身份: " + branch.getIdentityCode()
                        + "，模板: " + template.getCode() + "，来源: " + template.getSource());
                }
            }
        }
        Map<Class<?>, EffectiveEntityPolicy> compiled = new LinkedHashMap<Class<?>, EffectiveEntityPolicy>();
        Map<Class<?>, String> bindingSources = new LinkedHashMap<Class<?>, String>();
        for (EntityPolicyBinding binding : bindings) {
            if (binding == null) {
                throw new ValidationException("实体策略绑定不能为空");
            }
            String previousSource = bindingSources.put(binding.getEntityType(), binding.getSource());
            if (previousSource != null) {
                throw new ValidationException("实体策略重复绑定: " + binding.getEntityType().getName()
                    + "，来源: " + previousSource + " / " + binding.getSource());
            }
            EntityMeta meta = metas.get(binding.getEntityType());
            if (meta == null) {
                throw new ValidationException("绑定实体未注册: " + binding.getEntityType().getName()
                    + "，来源: " + binding.getSource());
            }
            EntityPolicyTemplate template = byCode.get(binding.getTemplateCode());
            if (template == null) {
                throw new ValidationException("绑定模板未注册: " + binding.getTemplateCode()
                    + "，实体: " + binding.getEntityType().getName() + "，来源: " + binding.getSource());
            }
            compiled.put(binding.getEntityType(), compile(meta, template, binding, operations, identities));
        }
        this.entities = Collections.unmodifiableMap(compiled);
        Map<String, EffectiveEntityPolicy> byResource = new LinkedHashMap<String, EffectiveEntityPolicy>();
        for (EffectiveEntityPolicy policy : compiled.values()) {
            byResource.put(policy.resourceCode, policy);
        }
        this.resources = Collections.unmodifiableMap(byResource);
    }

    private static EffectiveEntityPolicy compile(
        EntityMeta meta,
        EntityPolicyTemplate template,
        EntityPolicyBinding binding,
        BusinessOperationCatalog operations,
        BusinessIdentityRegistry identities
    ) {
        String resourceCode = meta.getResourceDescriptor().getResourceCode();
        AuthorizationFields fieldLimit = compileFieldLimit(meta);
        Set<String> neededRoles = new LinkedHashSet<String>();
        Map<BusinessOperation, Map<PolicyIdentityBranch, EffectiveRule>> rules =
            new LinkedHashMap<BusinessOperation, Map<PolicyIdentityBranch, EffectiveRule>>();
        for (SelectedRule selected : selectRules(template, binding)) {
            EntityPolicyTemplate.Rule rule = selected.rule;
            PolicyIdentityBranch branch = rule.getBranch();
            if (branch.getBranch() == AccessBranch.AUTHENTICATED
                && !identities.snapshot().containsKey(branch.getIdentityCode())) {
                throw new ValidationException("实体差异引用未注册身份: " + branch.getIdentityCode()
                    + "，实体: " + meta.getEntityType().getName() + "，规则来源: " + selected.source);
            }
            BusinessOperation operation = new BusinessOperation(resourceCode, rule.getOperationCode());
            if (!operations.snapshot().containsKey(operation)) {
                throw new ValidationException("策略引用未注册操作: " + operation + "，实体: "
                    + meta.getEntityType().getName() + "，模板来源: " + template.getSource()
                    + "，规则来源: " + selected.source + "，绑定来源: " + binding.getSource());
            }
            neededRoles.addAll(rule.getRequiredRelations());
            Map<String, String> relationFields = new LinkedHashMap<String, String>();
            for (String role : rule.getRequiredRelations()) {
                String field = binding.getRelationFields().get(role);
                if (field == null) {
                    throw new ValidationException("缺少关系角色绑定: " + role + "，实体: "
                        + meta.getEntityType().getName() + "，模板来源: " + template.getSource()
                        + "，规则来源: " + selected.source + "，绑定来源: " + binding.getSource());
                }
                if (!meta.getAllowedFields().contains(field)) {
                    throw new ValidationException("关系角色绑定未知字段: " + role + " -> " + field
                        + "，实体: " + meta.getEntityType().getName() + "，来源: " + binding.getSource());
                }
                relationFields.put(role, field);
            }
            Map<PolicyIdentityBranch, EffectiveRule> byBranch = rules.get(operation);
            if (byBranch == null) {
                byBranch = new LinkedHashMap<PolicyIdentityBranch, EffectiveRule>();
                rules.put(operation, byBranch);
            }
            byBranch.put(rule.getBranch(), new EffectiveRule(operation, rule.getBranch(), relationFields, fieldLimit,
                template.getSource(), binding.getSource(), selected.source));
        }
        for (String role : binding.getRelationFields().keySet()) {
            if (!neededRoles.contains(role)) {
                throw new ValidationException("实体绑定未被有效规则使用的关系角色: " + role + "，实体: "
                    + meta.getEntityType().getName() + "，来源: " + binding.getSource());
            }
        }
        return new EffectiveEntityPolicy(resourceCode, rules);
    }

    /** 统一计算用途静态上限，供策略及声明式字段模式编译复用。 */
    public static AuthorizationFields compileFieldLimit(EntityMeta meta) {
        Map<AuthorizationFields.Use, Collection<String>> fields =
            new EnumMap<AuthorizationFields.Use, Collection<String>>(AuthorizationFields.Use.class);
        for (AuthorizationFields.Use use : AuthorizationFields.Use.values()) {
            fields.put(use, new LinkedHashSet<String>());
        }
        for (Map.Entry<String, EntityFieldMeta> entry : meta.getFieldMetas().entrySet()) {
            String name = entry.getKey();
            EntityFieldMeta field = entry.getValue();
            fields.get(AuthorizationFields.Use.RETURN).add(name);
            if (field.isRelation()) {
                fields.get(AuthorizationFields.Use.EXPAND).add(name);
            } else {
                fields.get(AuthorizationFields.Use.STATS).add(name);
            }
            if (field.isWritable() && !field.isRelation() && !field.isScopeField() && !field.isImmutable()
                && !name.equals(meta.getIdField()) && !name.equals(meta.getLogicDeleteField())
                && !"version".equals(name)) {
                fields.get(AuthorizationFields.Use.WRITE).add(name);
            }
            if (field.isFilterable() && !field.isRelation()) {
                fields.get(AuthorizationFields.Use.FILTER).add(name);
            }
            if (field.isSortable() && !field.isRelation()) {
                fields.get(AuthorizationFields.Use.SORT).add(name);
            }
        }
        return AuthorizationFields.of(fields);
    }

    private static Collection<SelectedRule> selectRules(EntityPolicyTemplate template, EntityPolicyBinding binding) {
        Map<RuleKey, SelectedRule> selected = new LinkedHashMap<RuleKey, SelectedRule>();
        for (EntityPolicyTemplate.Rule rule : template.getRules()) {
            selected.put(new RuleKey(rule.getOperationCode(), rule.getBranch()),
                new SelectedRule(rule, template.getSource()));
        }
        Map<RuleKey, String> overrideSources = new LinkedHashMap<RuleKey, String>();
        for (EntityPolicyOverride override : binding.getOverrides()) {
            RuleKey key = new RuleKey(override.getOperationCode(), override.getBranch());
            String previousOverride = overrideSources.put(key, override.getSource());
            if (previousOverride != null) {
                throw new ValidationException("实体规则差异重复: " + key + "，实体: "
                    + binding.getEntityType().getName() + "，来源: "
                    + previousOverride + " / " + override.getSource());
            }
            SelectedRule existing = selected.get(key);
            if (override.getKind() == EntityPolicyOverride.Kind.ADD) {
                if (existing != null) {
                    throw new ValidationException("新增规则已存在: " + key + "，实体: "
                        + binding.getEntityType().getName() + "，来源: "
                        + existing.source + " / " + override.getSource());
                }
                selected.put(key, new SelectedRule(override.getRule(), override.getSource()));
            } else if (existing == null) {
                throw new ValidationException("差异规则在模板中不存在: " + key + "，实体: "
                    + binding.getEntityType().getName() + "，来源: " + override.getSource());
            } else if (override.getKind() == EntityPolicyOverride.Kind.REPLACE) {
                selected.put(key, new SelectedRule(override.getRule(), override.getSource()));
            } else {
                selected.remove(key);
            }
        }
        return new ArrayList<SelectedRule>(selected.values());
    }

    /** 资源是否选择新策略；不代表当前操作已获授权。 */
    public boolean containsResource(String resourceCode) {
        return resources.containsKey(resourceCode);
    }

    /** 仅取得已编译的静态声明；动态权限、关系事实与操作授权由后续治理内核判定。 */
    public EffectiveRule requireDeclaration(Class<?> entityType, BusinessOperation operation, PolicyIdentityBranch branch) {
        Objects.requireNonNull(entityType, "实体类型不能为空");
        Objects.requireNonNull(operation, "业务操作不能为空");
        Objects.requireNonNull(branch, "身份分支不能为空");
        EffectiveEntityPolicy policy = entities.get(entityType);
        if (policy == null || !policy.resourceCode.equals(operation.getResourceCode())) {
            throw new PermissionDeniedException("实体没有适用的业务策略: " + entityType.getName());
        }
        return requireRule(policy, operation, branch);
    }

    /** 定制业务按规范资源 code 取得同一有效规则，无需构造 CRUD Spec 或实体类型。 */
    public EffectiveRule requireDeclaration(BusinessOperation operation, PolicyIdentityBranch branch) {
        Objects.requireNonNull(operation, "业务操作不能为空");
        Objects.requireNonNull(branch, "身份分支不能为空");
        EffectiveEntityPolicy policy = resources.get(operation.getResourceCode());
        if (policy == null) {
            throw new PermissionDeniedException("资源没有适用的业务策略: " + operation.getResourceCode());
        }
        return requireRule(policy, operation, branch);
    }

    private static EffectiveRule requireRule(EffectiveEntityPolicy policy, BusinessOperation operation,
                                             PolicyIdentityBranch branch) {
        Map<PolicyIdentityBranch, EffectiveRule> byBranch = policy.rules.get(operation);
        EffectiveRule rule = byBranch == null ? null : byBranch.get(branch);
        if (rule == null) {
            throw new PermissionDeniedException("业务操作或身份分支未声明: " + operation + "/" + branch);
        }
        return rule;
    }

    public static final class EffectiveRule {
        private final BusinessOperation operation;
        private final PolicyIdentityBranch branch;
        private final Map<String, String> relationFields;
        private final AuthorizationFields fieldLimit;
        private final String templateSource;
        private final String bindingSource;
        private final String ruleSource;

        private EffectiveRule(BusinessOperation operation, PolicyIdentityBranch branch, Map<String, String> relationFields,
                              AuthorizationFields fieldLimit, String templateSource, String bindingSource,
                              String ruleSource) {
            this.operation = operation;
            this.branch = branch;
            this.relationFields = Collections.unmodifiableMap(new LinkedHashMap<String, String>(relationFields));
            this.fieldLimit = fieldLimit;
            this.templateSource = templateSource;
            this.bindingSource = bindingSource;
            this.ruleSource = ruleSource;
        }

        public BusinessOperation getOperation() {
            return operation;
        }

        public PolicyIdentityBranch getBranch() {
            return branch;
        }

        public Map<String, String> getRelationFields() {
            return relationFields;
        }

        public AuthorizationFields getFieldLimit() {
            return fieldLimit;
        }

        public String getTemplateSource() {
            return templateSource;
        }

        public String getBindingSource() {
            return bindingSource;
        }

        public String getRuleSource() {
            return ruleSource;
        }
    }

    private static final class SelectedRule {
        private final EntityPolicyTemplate.Rule rule;
        private final String source;

        private SelectedRule(EntityPolicyTemplate.Rule rule, String source) {
            this.rule = rule;
            this.source = source;
        }
    }

    private static final class RuleKey {
        private final String operationCode;
        private final PolicyIdentityBranch branch;

        private RuleKey(String operationCode, PolicyIdentityBranch branch) {
            this.operationCode = operationCode;
            this.branch = branch;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof RuleKey)) {
                return false;
            }
            RuleKey key = (RuleKey) other;
            return operationCode.equals(key.operationCode) && branch.equals(key.branch);
        }

        @Override
        public int hashCode() {
            return Objects.hash(operationCode, branch);
        }

        @Override
        public String toString() {
            return operationCode + "/" + branch;
        }
    }

    private static final class EffectiveEntityPolicy {
        private final String resourceCode;
        private final Map<BusinessOperation, Map<PolicyIdentityBranch, EffectiveRule>> rules;

        private EffectiveEntityPolicy(String resourceCode,
                                      Map<BusinessOperation, Map<PolicyIdentityBranch, EffectiveRule>> source) {
            this.resourceCode = resourceCode;
            Map<BusinessOperation, Map<PolicyIdentityBranch, EffectiveRule>> copy =
                new LinkedHashMap<BusinessOperation, Map<PolicyIdentityBranch, EffectiveRule>>();
            for (Map.Entry<BusinessOperation, Map<PolicyIdentityBranch, EffectiveRule>> entry : source.entrySet()) {
                copy.put(entry.getKey(), Collections.unmodifiableMap(new LinkedHashMap<PolicyIdentityBranch, EffectiveRule>(entry.getValue())));
            }
            this.rules = Collections.unmodifiableMap(copy);
        }
    }
}
