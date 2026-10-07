package com.entloom.crud.core.capability.dao;

import com.entloom.crud.api.access.BusinessOperation;
import com.entloom.crud.api.access.VisitorContext;
import com.entloom.crud.core.exception.PermissionDeniedException;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.governance.authorization.AuthorizationScope;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 逐目标授权批次的 Core 输入合同。
 *
 * <p>每个目标保留独立的请求、范围和字段能力；本类型不跨目标合并授权结果，也不提供存储
 * 执行或事务语义。存储适配器必须显式消费该合同，不能把它降级成单一目标或旧 DAO 范围。</p>
 */
public final class EntityDaoBatchAuthorizationContext {
    /** Core 逐目标授权批次的默认目标数量上限。 */
    public static final int DEFAULT_MAX_TARGETS = 500;

    private final List<EntityDaoAuthorizationContext> authorizations;
    private final List<String> targetIds;
    private final VisitorContext visitor;
    private final BusinessOperation operation;

    private EntityDaoBatchAuthorizationContext(
        Collection<? extends EntityDaoAuthorizationContext> source
    ) {
        Objects.requireNonNull(source, "批量 DAO 授权上下文不能为空");
        requireSupportedSize(source.size());
        List<EntityDaoAuthorizationContext> contexts = new ArrayList<EntityDaoAuthorizationContext>();
        List<String> ids = new ArrayList<String>();
        LinkedHashSet<String> seen = new LinkedHashSet<String>();
        EntityDaoAuthorizationContext first = null;
        for (EntityDaoAuthorizationContext context : source) {
            if (context == null) {
                throw new ValidationException("批量 DAO 授权上下文不能包含空项");
            }
            String targetId = context.requireSingleTarget();
            if (first == null) {
                first = context;
            } else {
                if (context.getVisitor() != first.getVisitor()) {
                    throw new PermissionDeniedException("批量 DAO 授权不能混用多个访问者");
                }
                if (!context.getOperation().equals(first.getOperation())) {
                    throw new PermissionDeniedException("批量 DAO 授权不能混用多个业务操作");
                }
            }
            if (!seen.add(targetId)) {
                throw new PermissionDeniedException("批量 DAO 授权目标不能重复: " + targetId);
            }
            contexts.add(context);
            ids.add(targetId);
        }
        this.authorizations = Collections.unmodifiableList(contexts);
        this.targetIds = Collections.unmodifiableList(ids);
        this.visitor = first.getVisitor();
        this.operation = first.getOperation();
    }

    /** 创建不可变的逐目标授权批次。 */
    public static EntityDaoBatchAuthorizationContext of(
        Collection<? extends EntityDaoAuthorizationContext> authorizations
    ) {
        return new EntityDaoBatchAuthorizationContext(authorizations);
    }

    /**
     * 在执行逐目标授权前校验批次大小，避免无界输入触发大量授权调用。
     *
     * <p>该方法供同包授权桥接在创建逐目标上下文前复用；存储适配器仍需自行声明更严格的
     * 执行上限，不能把本上限解释为事务或数据库批量能力。</p>
     */
    static void requireSupportedSize(int size) {
        if (size < 1) {
            throw new ValidationException("批量 DAO 授权至少需要一个目标");
        }
        if (size > DEFAULT_MAX_TARGETS) {
            throw new ValidationException(
                "批量 DAO 授权目标数量超过上限 " + DEFAULT_MAX_TARGETS
            );
        }
    }

    /** 返回按调用方输入顺序保存的独立授权上下文。 */
    public List<EntityDaoAuthorizationContext> getAuthorizations() {
        return authorizations;
    }

    /** 返回按调用方输入顺序保存的唯一目标 ID。 */
    public List<String> getTargetIds() {
        return targetIds;
    }

    /**
     * 返回每个目标自己的授权范围，保留输入顺序。
     *
     * <p>范围不会在不同目标之间做 AND 或 OR；存储适配器必须按目标消费这张计划，不能把
     * 一个目标的全量或无数据语义传播给其它目标。</p>
     */
    public Map<String, AuthorizationScope> getScopesByTarget() {
        LinkedHashMap<String, AuthorizationScope> scopes = new LinkedHashMap<String, AuthorizationScope>();
        for (EntityDaoAuthorizationContext authorization : authorizations) {
            scopes.put(authorization.getTargetId(), authorization.getScope());
        }
        return Collections.unmodifiableMap(scopes);
    }

    /** 返回明确授予全量范围的目标；全量由 {@link AuthorizationScope.Kind#ALL} 表示。 */
    public List<String> getAllScopeTargetIds() {
        return targetIdsByScope(AuthorizationScope.Kind.ALL);
    }

    /** 返回明确授予无数据范围的目标；无数据不是动态授权拒绝。 */
    public List<String> getNoDataTargetIds() {
        return targetIdsByScope(AuthorizationScope.Kind.NONE);
    }

    /**
     * 将统一存储收窄条件分别与每个目标的授权范围取交集。
     *
     * <p>结果仍按目标保存；不同目标的范围不会相互交集，避免把逐目标授权错误压成空范围或
     * 放大成并集。</p>
     */
    public Map<String, AuthorizationScope> intersectScopes(AuthorizationScope limit) {
        Objects.requireNonNull(limit, "批量授权收窄范围不能为空");
        LinkedHashMap<String, AuthorizationScope> scopes = new LinkedHashMap<String, AuthorizationScope>();
        for (EntityDaoAuthorizationContext authorization : authorizations) {
            scopes.put(
                authorization.getTargetId(),
                AuthorizationScope.intersect(authorization.getScope(), limit)
            );
        }
        return Collections.unmodifiableMap(scopes);
    }

    public VisitorContext getVisitor() {
        return visitor;
    }

    public BusinessOperation getOperation() {
        return operation;
    }

    public int size() {
        return authorizations.size();
    }

    /** 验证批次中的每个目标都属于同一个规范资源。 */
    public void requireResource(String resourceCode) {
        for (EntityDaoAuthorizationContext authorization : authorizations) {
            authorization.requireResource(resourceCode);
        }
    }

    private List<String> targetIdsByScope(AuthorizationScope.Kind kind) {
        List<String> selected = new ArrayList<String>();
        for (EntityDaoAuthorizationContext authorization : authorizations) {
            if (authorization.getScope().getKind() == kind) {
                selected.add(authorization.getTargetId());
            }
        }
        return Collections.unmodifiableList(selected);
    }
}
