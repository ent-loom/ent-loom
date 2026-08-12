package com.entloom.crud.core.idempotency;

import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import com.entloom.crud.core.util.RouteKeyFactory;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Getter;

/**
 * 命令幂等策略。
 */
@Getter
public class IdempotencyPolicy {
    /** 模式配置。 */
    private final Mode mode;
    /** 必填幂等操作集合。 */
    private final Set<CommandOperation> requiredOps;
    /** 必填幂等场景集合。 */
    private final Set<String> requiredScenes;

    public IdempotencyPolicy() {
        this(Mode.OPTIONAL, EnumSet.noneOf(CommandOperation.class), Collections.<String>emptySet());
    }

    public IdempotencyPolicy(Mode mode, Set<CommandOperation> requiredOps, Set<String> requiredScenes) {
        this.mode = mode == null ? Mode.OPTIONAL : mode;
        this.requiredOps = copyOps(requiredOps);
        this.requiredScenes = copyScenes(requiredScenes);
    }

    /**
     * 当前命令是否必须提供幂等键。
     *
     * @param spec 命令 spec
     * @return true 表示必须提供
     */
    /**
     * 判断当前命令是否要求提供幂等键。
     */
    public boolean isRequired(CommandSpec<?> spec) {
        if (spec == null || spec.isDryRun() || spec.getOp() == null) {
            return false;
        }
        if (mode == Mode.REQUIRED) {
            return true;
        }
        if (requiredOps.contains(spec.getOp())) {
            return true;
        }
        return requiredScenes.contains(RouteKeyFactory.normalizeScene(spec.getScene()));
    }

    /**
     * 当前命令是否启用幂等执行。
     *
     * @param spec 命令 spec
     * @return true 表示走幂等存储
     */
    public boolean shouldApply(CommandSpec<?> spec) {
        return spec != null && !spec.isDryRun() && hasText(spec.getIdempotencyKey());
    }

    private Set<CommandOperation> copyOps(Set<CommandOperation> source) {
        if (source == null || source.isEmpty()) {
            return Collections.unmodifiableSet(EnumSet.noneOf(CommandOperation.class));
        }
        return Collections.unmodifiableSet(EnumSet.copyOf(source));
    }

    /**
     * 复制场景集合并过滤空值。
     */
    private Set<String> copyScenes(Set<String> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptySet();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String scene : source) {
            String value = RouteKeyFactory.normalizeScene(scene);
            if (!value.isEmpty()) {
                normalized.add(value);
            }
        }
        return Collections.unmodifiableSet(normalized);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    /**
     * 幂等键要求模式。
     */
    public enum Mode {
        /** 所有命令都必须携带幂等键。 */
        REQUIRED,
        /** 仅在策略命中时要求幂等键。 */
        OPTIONAL
    }
}
