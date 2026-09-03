package com.entloom.crud.core.foundation.taskfile;

import com.entloom.crud.core.exception.ValidationException;

/**
 * CRUD 任务状态流转规则。
 *
 * <p>Local、InMemory 和 runtime adapter 必须遵循同一组最小生命周期语义。</p>
 */
final class CrudTaskStateMachine {
    private CrudTaskStateMachine() {
    }

    static void assertCreatable(CrudTask task) {
        if (task.getStatus() != CrudTaskStatus.PENDING) {
            throw new ValidationException("任务创建时状态必须为 PENDING: " + task.getStatus());
        }
    }

    static void assertTransition(String taskId, CrudTaskStatus current, CrudTaskStatus target) {
        if (target == null || current == target) {
            return;
        }
        if (current == CrudTaskStatus.PENDING
            && (target == CrudTaskStatus.RUNNING
                || target == CrudTaskStatus.FAILED
                || target == CrudTaskStatus.CANCELED)) {
            return;
        }
        if (current == CrudTaskStatus.RUNNING && isTerminal(target)) {
            return;
        }
        throw new ValidationException(
            "任务状态不允许流转: taskId=" + taskId + ", " + current + " -> " + target
        );
    }

    static boolean isTerminal(CrudTaskStatus status) {
        return status == CrudTaskStatus.SUCCEEDED
            || status == CrudTaskStatus.FAILED
            || status == CrudTaskStatus.CANCELED;
    }
}
