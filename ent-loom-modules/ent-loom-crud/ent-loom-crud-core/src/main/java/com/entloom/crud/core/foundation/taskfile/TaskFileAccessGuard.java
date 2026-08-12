package com.entloom.crud.core.foundation.taskfile;

import com.entloom.crud.api.enums.CrudErrorCode;
import com.entloom.crud.api.model.SubjectContext;
import com.entloom.crud.core.exception.CrudException;
import com.entloom.crud.core.runtime.spec.BaseSpec;
import java.time.Instant;
import java.util.Objects;

/**
 * Import / Export 任务与文件访问守卫。
 */
public class TaskFileAccessGuard {
    public void assertTaskAccessible(CrudTask task, BaseSpec spec) {
        if (task == null || spec == null) {
            deny("任务访问上下文不能为空");
        }
        CrudTaskContextSnapshot snapshot = task.getContextSnapshot();
        if (snapshot == null) {
            deny("任务缺少上下文快照: " + task.getTaskId());
        }
        if (snapshot.getRootType() != null && spec.getRootType() != null && !snapshot.getRootType().equals(spec.getRootType())) {
            deny("任务实体不匹配: " + task.getTaskId());
        }
        if (!sameText(snapshot.getScene(), spec.getScene())) {
            deny("任务场景不匹配: " + task.getTaskId());
        }
        assertSubjectAccessible(snapshot.getSubject(), spec.getSubject(), task.getTaskId());
    }

    public void assertFilePurpose(FileRef file, String expectedPurpose) {
        if (file == null) {
            deny("文件引用不能为空");
        }
        Object actualPurpose = file.getAttributes().get("purpose");
        if (expectedPurpose != null && !expectedPurpose.equals(String.valueOf(actualPurpose))) {
            deny("文件用途不匹配: " + file.getFileId());
        }
    }

    public void assertDownloadableFile(FileRef file, String expectedPurpose) {
        assertFilePurpose(file, expectedPurpose);
        if (file.getExpiresAt() != null && file.getExpiresAt().isBefore(Instant.now())) {
            throw new CrudException(CrudErrorCode.FILE_EXPIRED, "文件已过期: " + file.getFileId());
        }
        if (isBlank(file.getFileName())) {
            invalid("文件名缺失: " + file.getFileId());
        }
        if (isBlank(file.getContentType())) {
            invalid("文件 Content-Type 缺失: " + file.getFileId());
        }
        if (file.getSize() == null || file.getSize().longValue() < 0L) {
            invalid("文件大小元数据非法: " + file.getFileId());
        }
        Object format = file.getAttributes().get("format");
        if (format == null || isBlank(String.valueOf(format))) {
            invalid("文件格式元数据缺失: " + file.getFileId());
        }
    }

    private void assertSubjectAccessible(SubjectContext owner, SubjectContext current, String taskId) {
        if (owner == null || current == null) {
            return;
        }
        if (!sameText(owner.getSubjectId(), current.getSubjectId())
            || !sameText(owner.getTenantId(), current.getTenantId())
            || !sameText(owner.getOrgId(), current.getOrgId())) {
            deny("任务主体不匹配: " + taskId);
        }
    }

    private static boolean sameText(String left, String right) {
        String l = normalize(left);
        String r = normalize(right);
        return Objects.equals(l, r);
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static void deny(String message) {
        throw new CrudException(CrudErrorCode.PERMISSION_DENIED, message);
    }

    private static void invalid(String message) {
        throw new CrudException(CrudErrorCode.FILE_METADATA_INVALID, message);
    }
}
