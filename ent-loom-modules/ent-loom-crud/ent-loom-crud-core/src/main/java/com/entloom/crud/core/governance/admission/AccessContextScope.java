package com.entloom.crud.core.governance.admission;

import com.entloom.crud.api.access.VisitorContext;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/** 仅供同步调用使用的当前准入作用域；异步工作必须显式传递任务快照。 */
public final class AccessContextScope {
    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<Scope>();

    private AccessContextScope() {
    }

    public static Optional<AccessAdmission> currentAdmission() {
        Scope scope = CURRENT.get();
        return scope == null ? Optional.empty() : Optional.ofNullable(scope.admission);
    }

    public static Optional<VisitorContext> currentVisitor() {
        return currentAdmission().map(AccessAdmission::getVisitorContext);
    }

    /** 治理要求与身份分别保存；MVC 异步线程可以要求授权而不持有任何身份。 */
    public static boolean isAuthorizationRequired() {
        Scope scope = CURRENT.get();
        return scope != null && scope.authorizationRequired;
    }

    public static VisitorContext requireVisitor() {
        return currentVisitor().orElseThrow(() -> new IllegalStateException("当前没有访问者上下文"));
    }

    public static <T> T withAdmission(AccessAdmission admission, Supplier<T> action) {
        Objects.requireNonNull(action, "同步操作不能为空");
        try (Scope ignored = enter(admission)) {
            return action.get();
        }
    }

    /** 供同步框架生命周期使用；必须在打开它的线程内关闭。 */
    public static Scope enter(AccessAdmission admission) {
        Scope scope = new Scope(Objects.requireNonNull(admission, "准入结果不能为空"), CURRENT.get(), true);
        CURRENT.set(scope);
        return scope;
    }

    /** 嵌套进入未声明接口时屏蔽外层准入；关闭后恢复原作用域。 */
    public static Scope suspend() {
        Scope scope = new Scope(null, CURRENT.get(), false);
        CURRENT.set(scope);
        return scope;
    }

    /** 仅传递必须授权的边界，不传递认证主体、身份或准入结果。 */
    public static Scope requireAuthorization() {
        Scope scope = new Scope(null, CURRENT.get(), true);
        CURRENT.set(scope);
        return scope;
    }

    public static void withAdmission(AccessAdmission admission, Runnable action) {
        Objects.requireNonNull(action, "同步操作不能为空");
        withAdmission(admission, () -> {
            action.run();
            return null;
        });
    }

    public static final class Scope implements AutoCloseable {
        private final AccessAdmission admission;
        private final boolean authorizationRequired;
        private final Scope previous;
        private final Thread owner;
        private boolean closed;

        private Scope(AccessAdmission admission, Scope previous, boolean authorizationRequired) {
            this.admission = admission;
            this.previous = previous;
            this.authorizationRequired = authorizationRequired;
            this.owner = Thread.currentThread();
        }

        @Override
        public void close() {
            if (Thread.currentThread() != owner) {
                throw new IllegalStateException("访问者作用域只能在创建线程关闭");
            }
            if (closed) return;
            if (CURRENT.get() != this) {
                throw new IllegalStateException("访问者作用域必须按嵌套顺序关闭");
            }
            closed = true;
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }
}
