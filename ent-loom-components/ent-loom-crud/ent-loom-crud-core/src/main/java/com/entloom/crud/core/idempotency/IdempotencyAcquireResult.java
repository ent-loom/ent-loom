package com.entloom.crud.core.idempotency;

import lombok.Getter;

/**
 * 幂等占位结果。
 */
@Getter
public final class IdempotencyAcquireResult {
    /** 获取决策。 */
    private final Decision decision;
    /** 重放结果。 */
    private final Object replayResult;

    private IdempotencyAcquireResult(Decision decision, Object replayResult) {
        this.decision = decision;
        this.replayResult = replayResult;
    }

    public static IdempotencyAcquireResult acquired() {
        return new IdempotencyAcquireResult(Decision.ACQUIRED, null);
    }

    public static IdempotencyAcquireResult replay(Object replayResult) {
        return new IdempotencyAcquireResult(Decision.REPLAY, replayResult);
    }

    public static IdempotencyAcquireResult inProgress() {
        return new IdempotencyAcquireResult(Decision.IN_PROGRESS, null);
    }

    public static IdempotencyAcquireResult payloadConflict() {
        return new IdempotencyAcquireResult(Decision.PAYLOAD_CONFLICT, null);
    }

    /**
     * 幂等占位判定结果。
     */
    public enum Decision {
        /** 成功获取执行资格。 */
        ACQUIRED,
        /** 命中历史结果，直接回放。 */
        REPLAY,
        /** 已有同键请求处理中。 */
        IN_PROGRESS,
        /** 同键请求载荷不一致。 */
        PAYLOAD_CONFLICT
    }
}
