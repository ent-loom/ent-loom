package com.entloom.crud.core.idempotency;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 幂等记录。
 */
@Getter
@Setter
@NoArgsConstructor
public class IdempotencyRecord {
    /** 幂等键。 */
    private String key;
    /** 载荷哈希。 */
    private String payloadHash;
    /** 状态值。 */
    private IdempotencyState status;
    /** 执行结果。 */
    private Object result;

    public IdempotencyRecord(String key, String payloadHash, IdempotencyState status) {
        this.key = key;
        this.payloadHash = payloadHash;
        this.status = status;
    }
}
