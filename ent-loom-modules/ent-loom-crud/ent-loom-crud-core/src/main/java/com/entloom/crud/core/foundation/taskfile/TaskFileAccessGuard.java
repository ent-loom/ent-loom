package com.entloom.crud.core.foundation.taskfile;

import com.entloom.crud.api.enums.CrudErrorCode;
import com.entloom.crud.api.model.SubjectContext;
import com.entloom.crud.core.exception.CrudException;
import com.entloom.crud.core.runtime.spec.BaseSpec;
import java.util.Objects;

/**
 * Import / Export 任务与文件访问守卫。
 */
public class TaskFileAccessGuard {
    private final java.time.Clock clock;

    public TaskFileAccessGuard() {
        this(java.time.Clock.systemUTC());
    }

    public TaskFileAccessGuard(java.time.Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock 不能为空");
    }

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

    /**
     * 校验导入源文件的用途和归属主体。
     *
     * <p>文件元数据由文件服务保存，不能使用请求中的 fileId 以外字段作为授权依据。</p>
     */
    public void assertImportSourceFileAccessible(FileRef file, BaseSpec spec) {
        assertFilePurpose(file, "IMPORT_SOURCE");
        if (spec == null || spec.getSubject() == null) {
            deny("导入源文件缺少当前主体上下文");
        }
        SubjectContext current = spec.getSubject();
        assertOwnedAttribute(file, "subjectId", current.getSubjectId());
        assertOwnedAttribute(file, "tenantId", current.getTenantId());
        assertOwnedAttribute(file, "orgId", current.getOrgId());
        assertNotExpired(file);
    }

    public void assertDownloadableFile(FileRef file, String expectedPurpose) {
        assertFilePurpose(file, expectedPurpose);
        assertNotExpired(file);
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

    /**
     * 校验任务下载文件，确保文件既属于当前任务，也属于任务快照中的主体。
     *
     * <p>仅校验请求主体不足以保护结果文件，因为调用方仍可能替换任务之外的文件引用。</p>
     */
    public void assertDownloadableFile(CrudTask task, BaseSpec spec, FileRef file, String expectedPurpose) {
        assertTaskAccessible(task, spec);
        if (file == null || !references(task, file)) {
            deny("文件不是任务关联文件: " + (task == null ? null : task.getTaskId()));
        }
        SubjectContext taskSubject = task.getContextSnapshot().getSubject();
        assertOwnedAttribute(file, "subjectId", taskSubject == null ? null : taskSubject.getSubjectId());
        assertOwnedAttribute(file, "tenantId", taskSubject == null ? null : taskSubject.getTenantId());
        assertOwnedAttribute(file, "orgId", taskSubject == null ? null : taskSubject.getOrgId());
        assertDownloadableFile(file, expectedPurpose);
    }

    private static boolean references(CrudTask task, FileRef file) {
        if (task == null || file == null || isBlank(file.getFileId())) {
            return false;
        }
        return sameFile(task.getSourceFile(), file)
            || sameFile(task.getResultFile(), file)
            || sameFile(task.getErrorFile(), file);
    }

    private static boolean sameFile(FileRef left, FileRef right) {
        return left != null && right != null
            && !isBlank(left.getFileId())
            && left.getFileId().equals(right.getFileId());
    }

    private void assertNotExpired(FileRef file) {
        if (file.getExpiresAt() != null && !file.getExpiresAt().isAfter(clock.instant())) {
            throw new CrudException(CrudErrorCode.FILE_EXPIRED, "文件已过期: " + file.getFileId());
        }
    }

    private void assertSubjectAccessible(SubjectContext owner, SubjectContext current, String taskId) {
        if (owner == null || current == null) {
            deny("任务主体上下文不能为空: " + taskId);
        }
        if (!sameText(owner.getSubjectId(), current.getSubjectId())
            || !sameText(owner.getTenantId(), current.getTenantId())
            || !sameText(owner.getOrgId(), current.getOrgId())) {
            deny("任务主体不匹配: " + taskId);
        }
    }

    private void assertOwnedAttribute(FileRef file, String attribute, String expected) {
        if (isBlank(expected)) {
            return;
        }
        Object actual = file.getAttributes().get(attribute);
        if (!Objects.equals(normalize(expected), normalize(actual == null ? null : String.valueOf(actual)))) {
            deny("文件主体归属不匹配: " + file.getFileId());
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
