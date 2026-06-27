package com.entloom.crud.core.idempotency;

import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 基于内存的幂等存储实现。
 */
public class InMemoryIdempotencyStore implements IdempotencyStore {
    /** 默认保留时长（毫秒）。 */
    private static final long DEFAULT_RETENTION_MILLIS = TimeUnit.HOURS.toMillis(48);

    /** 幂等记录映射。 */
    private final ConcurrentMap<String, StoredRecord> store = new ConcurrentHashMap<>();
    /** 保留时长（毫秒）。 */
    private final long retentionMillis;

    public InMemoryIdempotencyStore() {
        this(DEFAULT_RETENTION_MILLIS);
    }

    public InMemoryIdempotencyStore(long retentionMillis) {
        this.retentionMillis = retentionMillis;
    }

    /**
     * 尝试获取幂等执行资格。
     */
    @Override
    public IdempotencyAcquireResult tryAcquire(String key, String payloadHash) {
        long now = System.currentTimeMillis();
        StoredRecord candidate = new StoredRecord(key, payloadHash, IdempotencyState.PROCESSING, null, now + retentionMillis);

        while (true) {
            StoredRecord existing = store.get(key);
            if (existing == null) {
                if (store.putIfAbsent(key, candidate) == null) {
                    return IdempotencyAcquireResult.acquired();
                }
                continue;
            }
            if (existing.isExpired(now)) {
                if (store.replace(key, existing, candidate)) {
                    return IdempotencyAcquireResult.acquired();
                }
                continue;
            }
            if (!existing.getPayloadHash().equals(payloadHash)) {
                return IdempotencyAcquireResult.payloadConflict();
            }
            if (existing.getStatus() == IdempotencyState.SUCCEEDED) {
                return IdempotencyAcquireResult.replay(existing.getResult());
            }
            return IdempotencyAcquireResult.inProgress();
        }
    }

    @Override
    public void markSucceeded(String key, Object result) {
        long now = System.currentTimeMillis();
        StoredRecord record = store.get(key);
        if (record != null) {
            record.setStatus(IdempotencyState.SUCCEEDED);
            record.setResult(result);
            record.setExpiresAtMillis(now + retentionMillis);
        }
    }

    @Override
    public void release(String key) {
        store.remove(key);
    }

    /**
     * 内存存储中的扩展记录，增加过期时间字段。
     */
    private static final class StoredRecord extends IdempotencyRecord {
        /** 过期时间戳（毫秒）。 */
        private volatile long expiresAtMillis;

        private StoredRecord(String key, String payloadHash, IdempotencyState status, Object result, long expiresAtMillis) {
            super(key, payloadHash, status);
            setResult(result);
            this.expiresAtMillis = expiresAtMillis;
        }

        private boolean isExpired(long now) {
            return expiresAtMillis <= now;
        }

        private void setExpiresAtMillis(long expiresAtMillis) {
            this.expiresAtMillis = expiresAtMillis;
        }
    }
}
