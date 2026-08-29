package com.entloom.e5.statictest.fixture;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 客户档案新增请求，仅声明允许写入的字段。 */
public final class CustomerProfileCreateRequest {
    /** 客户展示名称。 */
    private final String displayName;
    /** 客户信用额度。 */
    private final BigDecimal creditLimit;
    /** 客户注册时间。 */
    private final LocalDateTime registeredAt;
    /** 客户头像地址，可为空。 */
    private final String avatarUrl;

    public CustomerProfileCreateRequest(String displayName, BigDecimal creditLimit, LocalDateTime registeredAt, String avatarUrl) {
        this.displayName = displayName;
        this.creditLimit = creditLimit;
        this.registeredAt = registeredAt;
        this.avatarUrl = avatarUrl;
    }

    public String getDisplayName() { return displayName; }
    public BigDecimal getCreditLimit() { return creditLimit; }
    public LocalDateTime getRegisteredAt() { return registeredAt; }
    public String getAvatarUrl() { return avatarUrl; }
}
