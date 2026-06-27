package com.entloom.crud.core.idempotency;

import com.entloom.crud.api.model.CommandResult;
import com.entloom.crud.core.exception.IdempotencyInProgressException;
import com.entloom.crud.core.exception.IdempotencyPayloadConflictException;
import com.entloom.crud.core.util.RouteKeyFactory;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.function.Supplier;

/**
 * 幂等管理器。
 */
public class IdempotencyManager {
    /** 幂等存储。 */
    private final IdempotencyStore store;
    /** 载荷规范化器。 */
    private final PayloadCanonicalizer canonicalizer;

    public IdempotencyManager(IdempotencyStore store) {
        this(store, new StablePayloadCanonicalizer());
    }

    public IdempotencyManager(IdempotencyStore store, PayloadCanonicalizer canonicalizer) {
        this.store = store;
        this.canonicalizer = canonicalizer == null ? new StablePayloadCanonicalizer() : canonicalizer;
    }

    /**
     * 执行带幂等的命令。
     *
     * @param key 幂等主键
     * @param payload 载荷
     * @param supplier 实际执行逻辑
     * @param <R> 返回类型
     * @return 执行结果
     */
    /**
     * 在幂等控制下执行目标逻辑。
     */
    @SuppressWarnings("unchecked")
    public <R> R executeWithIdempotency(String key, Object payload, Supplier<R> supplier) {
        String payloadHash = hashStable(payload);
        IdempotencyAcquireResult acquireResult = store.tryAcquire(key, payloadHash);
        switch (acquireResult.getDecision()) {
            case PAYLOAD_CONFLICT:
                throw new IdempotencyPayloadConflictException("相同幂等键(idempotencyKey)对应的载荷不一致");
            case IN_PROGRESS:
                throw new IdempotencyInProgressException("幂等请求处理中");
            case REPLAY:
                return (R) markReplay(acquireResult.getReplayResult());
            case ACQUIRED:
                break;
            default:
                throw new IllegalStateException("幂等获取结果异常: " + acquireResult.getDecision());
        }

        try {
            R result = supplier.get();
            store.markSucceeded(key, result);
            return result;
        } catch (RuntimeException ex) {
            store.release(key);
            throw ex;
        }
    }

    /**
     * 生成命令幂等主键。
     *
     * @param tenantId 租户
     * @param routeKey 路由 key
     * @param scene 场景
     * @param idempotencyKey 请求幂等键
     * @return 主键
     */
    public String buildStorageKey(String tenantId, String routeKey, String scene, String idempotencyKey) {
        return tenantId + "|" + routeKey + "|" + RouteKeyFactory.normalizeScene(scene) + "|" + idempotencyKey;
    }

    private String hashStable(Object payload) {
        String raw = canonicalizer.canonicalize(payload);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return toHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private Object markReplay(Object replayResult) {
        if (replayResult instanceof CommandResult<?>) {
            return copyReplayResult((CommandResult<?>) replayResult);
        }
        return replayResult;
    }

    private <T> CommandResult<T> copyReplayResult(CommandResult<?> source) {
        CommandResult<T> copy = new CommandResult<T>();
        copy.setSuccess(source.isSuccess());
        copy.setCode(source.getCode());
        copy.setMessage(source.getMessage());
        copy.setData((T) source.getData());
        copy.setIdempotentReplay(true);
        return copy;
    }

    private String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
