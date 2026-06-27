package com.entloom.crud.core.capability.command.aggregate;

/**
 * 聚合关系写入配置。
 */
public class AggregateRelationSpec {
    private final String relationField;
    private final ChildSyncMode syncMode;

    public AggregateRelationSpec(String relationField, ChildSyncMode syncMode) {
        if (relationField == null || relationField.trim().isEmpty()) {
            throw new IllegalArgumentException("relationField 不能为空");
        }
        if (syncMode == null) {
            throw new IllegalArgumentException("syncMode 不能为空");
        }
        this.relationField = relationField;
        this.syncMode = syncMode;
    }

    public String getRelationField() {
        return relationField;
    }

    public ChildSyncMode getSyncMode() {
        return syncMode;
    }
}
