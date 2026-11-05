package com.entloom.crud.core.capability.command.engine;

import com.entloom.crud.api.access.VisitorContext;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import com.entloom.crud.core.capability.dao.*;
import com.entloom.crud.core.exception.PermissionDeniedException;
import com.entloom.crud.core.governance.authorization.AuthorizationResult;
import com.entloom.crud.core.governance.authorization.AuthorizationScope;
import com.entloom.crud.core.governance.admission.AccessContextScope;

/** 统一动作入口注入的可信授权；业务 Handler 以此绑定 DAO 范围，再执行固定业务规则。 */
public final class AuthorizedActionContext {
    private final Class<?> entityType;
    private final VisitorContext visitor;
    private final AuthorizationResult authorization;
    private final java.util.Map<Class<?>, RelatedDaoAccess> relatedAccess;

    AuthorizedActionContext(Class<?> entityType, VisitorContext visitor, AuthorizationResult authorization,
        java.util.List<RelatedDaoAccess> relatedAccess) {
        this.entityType = entityType;
        this.visitor = visitor;
        this.authorization = authorization;
        java.util.Map<Class<?>, RelatedDaoAccess> related = new java.util.LinkedHashMap<>();
        for (RelatedDaoAccess access : relatedAccess) related.put(access.getEntityType(), access);
        this.relatedAccess = java.util.Collections.unmodifiableMap(related);
    }

    public Class<?> entityType() { return entityType; }
    public VisitorContext visitor() { return visitor; }
    public AuthorizationResult authorization() { return authorization; }
    static final String ATTRIBUTE = AuthorizedActionContext.class.getName();

    public static AuthorizedActionContext require(CommandSpec<?> spec) {
        Object value = spec.getAttributes().get(ATTRIBUTE);
        if (!(value instanceof AuthorizedActionContext)) throw new PermissionDeniedException("业务动作缺少可信授权上下文");
        AuthorizedActionContext context = (AuthorizedActionContext) value;
        context.requireCurrent();
        if (context.entityType() != spec.getRootType()) throw new PermissionDeniedException("业务动作实体与当前授权不匹配");
        return context;
    }

    /** 固定业务写入由 Handler 决定字段，数据范围进入实际 SQL；不等价于授予通用 update。 */
    public <T, ID> EntityDao<T, ID> dao(EntityDaoFactory factory, EntityType<T, ID> type) {
        if (type.getEntityClass() != entityType) throw new PermissionDeniedException("业务动作 DAO 实体与授权不匹配");
        return dao(factory, type, RowConstraint.unrestricted());
    }

    /** 业务条件只能收窄根实体范围；关联实体须由 Handler 显式绑定可信聚合关系。 */
    public <T, ID> EntityDao<T, ID> dao(EntityDaoFactory factory, EntityType<T, ID> type, RowConstraint businessScope) {
        requireCurrent();
        return factory.scopedAction(type, this, EntityAccessScope.of(businessScope));
    }

    public void requireDaoScope(Class<?> target, RowConstraint scope) {
        requireCurrent();
        if (target != entityType) related(target).requireScope(scope);
    }

    public void requireDaoMethod(Class<?> target, String method) {
        requireCurrent();
        if (target != entityType) related(target).requireMethod(method);
    }

    private RelatedDaoAccess related(Class<?> target) {
        RelatedDaoAccess access = relatedAccess.get(target);
        if (access == null) throw new PermissionDeniedException("业务动作未声明关联 DAO: " + target.getName());
        return access;
    }

    public void requireCurrent() {
        if (visitor != AccessContextScope.currentVisitor().orElse(null)) throw new PermissionDeniedException("业务动作 DAO 超出当前访问作用域");
        if (authorization.getRequest().getVisitor() != visitor || authorization.getDecision() != com.entloom.crud.api.enums.AccessDecision.ALLOW)
            throw new PermissionDeniedException("业务动作未获授权");
        authorization.requireApplicable(authorization.getRequest());
    }

    public RowConstraint rowConstraint() { return constraint(authorization.getScope()); }

    private static RowConstraint constraint(AuthorizationScope scope) {
        switch (scope.getKind()) {
            case ALL: return RowConstraint.unrestricted();
            case EQUAL: return RowConstraint.eq(scope.getField(), scope.getValues().get(0));
            case IN: return RowConstraint.in(scope.getField(), scope.getValues());
            case AND: return RowConstraint.and(scope.getChildren().stream().map(AuthorizedActionContext::constraint).toArray(RowConstraint[]::new));
            case OR: return RowConstraint.or(scope.getChildren().stream().map(AuthorizedActionContext::constraint).toArray(RowConstraint[]::new));
            default: throw new PermissionDeniedException("业务动作无可访问数据");
        }
    }
}
