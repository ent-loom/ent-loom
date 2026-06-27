package com.entloom.crud.core.idempotency;

/**
 * 幂等 payload 规范化器。
 */
public interface PayloadCanonicalizer {
    /**
     * 将 payload 转成稳定字符串，用于哈希。
     *
     * @param payload 请求载荷
     * @return 稳定字符串
     */
    String canonicalize(Object payload);
}
