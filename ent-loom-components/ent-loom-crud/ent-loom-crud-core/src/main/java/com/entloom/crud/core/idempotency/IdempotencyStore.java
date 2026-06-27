package com.entloom.crud.core.idempotency;

/**
 * 幂等存储 SPI。
 */
public interface IdempotencyStore {
    /**
     * 原子尝试占位。
     *
     * @param key 幂等主键
     * @param payloadHash 载荷哈希
     * @return 占位结果
     */
    IdempotencyAcquireResult tryAcquire(String key, String payloadHash);

    /**
     * 标记成功。
     *
     * @param key 幂等主键
     * @param result 结果摘要
     */
    void markSucceeded(String key, Object result);

    /**
     * 释放处理中占位。
     *
     * @param key 幂等主键
     */
    void release(String key);
}
