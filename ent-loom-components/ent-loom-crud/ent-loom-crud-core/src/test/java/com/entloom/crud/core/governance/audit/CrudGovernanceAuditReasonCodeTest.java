package com.entloom.crud.core.governance.audit;

import com.entloom.crud.api.enums.CrudErrorCode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class CrudGovernanceAuditReasonCodeTest {
    @Test
    void fromCrudErrorCode_should_map_all_crud_error_codes() {
        for (CrudErrorCode errorCode : CrudErrorCode.values()) {
            Assertions.assertNotNull(CrudGovernanceAuditReasonCode.fromCrudErrorCode(errorCode));
        }
    }

    @Test
    void fromCrudErrorCode_should_map_task_not_found() {
        Assertions.assertEquals(
            CrudGovernanceAuditReasonCode.TASK_NOT_FOUND,
            CrudGovernanceAuditReasonCode.fromCrudErrorCode(CrudErrorCode.TASK_NOT_FOUND)
        );
    }
}
