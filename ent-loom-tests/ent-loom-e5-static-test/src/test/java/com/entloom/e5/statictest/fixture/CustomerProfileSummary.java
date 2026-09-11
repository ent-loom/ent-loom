package com.entloom.e5.statictest.fixture;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 客户档案摘要；由定制查询场景聚合档案和备注数据返回。 */
public final class CustomerProfileSummary {
    private final Long id;
    private final String displayName;
    private final BigDecimal creditLimit;
    private final LocalDateTime registeredAt;
    private final long noteCount;
    private final LocalDateTime latestNoteAt;

    public CustomerProfileSummary(
        Long id,
        String displayName,
        BigDecimal creditLimit,
        LocalDateTime registeredAt,
        long noteCount,
        LocalDateTime latestNoteAt
    ) {
        this.id = id;
        this.displayName = displayName;
        this.creditLimit = creditLimit;
        this.registeredAt = registeredAt;
        this.noteCount = noteCount;
        this.latestNoteAt = latestNoteAt;
    }

    public Long getId() { return id; }
    public String getDisplayName() { return displayName; }
    public BigDecimal getCreditLimit() { return creditLimit; }
    public LocalDateTime getRegisteredAt() { return registeredAt; }
    public long getNoteCount() { return noteCount; }
    public LocalDateTime getLatestNoteAt() { return latestNoteAt; }
}
