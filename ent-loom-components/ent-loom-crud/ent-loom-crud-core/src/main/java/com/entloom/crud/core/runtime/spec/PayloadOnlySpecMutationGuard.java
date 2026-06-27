package com.entloom.crud.core.runtime.spec;

import com.entloom.crud.api.model.PageRequest;
import com.entloom.crud.api.model.QueryFilter;
import com.entloom.crud.api.model.QuerySort;
import com.entloom.crud.api.model.QueryTimeRange;
import com.entloom.crud.api.model.SubjectContext;
import com.entloom.crud.core.capability.exporting.ExportSpec;
import com.entloom.crud.core.capability.importing.ImportSpec;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.foundation.taskfile.FileRef;
import com.entloom.crud.core.governance.scope.CrudDataScope;
import java.util.List;
import java.util.Objects;

/**
 * 限制 payload customizer 只能修改 payload。
 */
public final class PayloadOnlySpecMutationGuard {
    private PayloadOnlySpecMutationGuard() {
    }

    public static ImportSpec mergeImportPayload(ImportSpec original, ImportSpec customized) {
        if (customized == original) {
            return original;
        }
        requireImportPayloadOnly(original, customized);
        return original.toBuilder().payload(customized.getPayload()).build();
    }

    public static ExportSpec mergeExportPayload(ExportSpec original, ExportSpec customized) {
        if (customized == original) {
            return original;
        }
        requireExportPayloadOnly(original, customized);
        return original.toBuilder().payload(customized.getPayload()).build();
    }

    private static void requireImportPayloadOnly(ImportSpec original, ImportSpec customized) {
        if (!Objects.equals(original.getOperation(), customized.getOperation())
            || !Objects.equals(original.getFormat(), customized.getFormat())
            || !Objects.equals(original.getMode(), customized.getMode())
            || !sameFileRef(original.getSourceFile(), customized.getSourceFile())
            || !Objects.equals(original.getTaskId(), customized.getTaskId())
            || !Objects.equals(original.getBatchSize(), customized.getBatchSize())
            || original.isAsync() != customized.isAsync()
            || !Objects.equals(original.getTransactionPolicy(), customized.getTransactionPolicy())
            || original.isIncludeExecutionMeta() != customized.isIncludeExecutionMeta()
            || !sameBaseSpec(original, customized)) {
            throw new ValidationException("ImportPayloadCustomizer 只能修改 payload，不能修改其他 spec 字段");
        }
    }

    private static void requireExportPayloadOnly(ExportSpec original, ExportSpec customized) {
        if (!Objects.equals(original.getOperation(), customized.getOperation())
            || !Objects.equals(original.getFormat(), customized.getFormat())
            || !Objects.equals(original.getFileName(), customized.getFileName())
            || !Objects.equals(original.getTaskId(), customized.getTaskId())
            || original.isAsync() != customized.isAsync()
            || !Objects.equals(original.getFields(), customized.getFields())
            || !sameFilters(original.getFilters(), customized.getFilters())
            || !sameSorts(original.getSorts(), customized.getSorts())
            || !sameTime(original.getTime(), customized.getTime())
            || !samePage(original.getPage(), customized.getPage())
            || !Objects.equals(original.getLimit(), customized.getLimit())
            || original.isIncludeExecutionMeta() != customized.isIncludeExecutionMeta()
            || !sameBaseSpec(original, customized)) {
            throw new ValidationException("ExportPayloadCustomizer 只能修改 payload，不能修改其他 spec 字段");
        }
    }

    private static boolean sameBaseSpec(BaseSpec left, BaseSpec right) {
        return Objects.equals(left.getRootType(), right.getRootType())
            && Objects.equals(left.getEntityClasses(), right.getEntityClasses())
            && Objects.equals(left.getScene(), right.getScene())
            && sameSubject(left.getSubject(), right.getSubject())
            && Objects.equals(left.getAttributes(), right.getAttributes())
            && sameScope(left.getGrantedScope(), right.getGrantedScope())
            && sameScope(left.getGovernanceScope(), right.getGovernanceScope())
            && Objects.equals(left.getAccessDecision(), right.getAccessDecision());
    }

    private static boolean sameFileRef(FileRef left, FileRef right) {
        if (left == null || right == null) {
            return left == right;
        }
        return Objects.equals(left.getFileId(), right.getFileId())
            && Objects.equals(left.getFileName(), right.getFileName())
            && Objects.equals(left.getContentType(), right.getContentType())
            && Objects.equals(left.getSize(), right.getSize())
            && Objects.equals(left.getStorageType(), right.getStorageType())
            && Objects.equals(left.getStorageKey(), right.getStorageKey())
            && Objects.equals(left.getExpiresAt(), right.getExpiresAt())
            && Objects.equals(left.getAttributes(), right.getAttributes());
    }

    private static boolean sameSubject(SubjectContext left, SubjectContext right) {
        if (left == null || right == null) {
            return left == right;
        }
        return Objects.equals(left.getSubjectId(), right.getSubjectId())
            && Objects.equals(left.getTenantId(), right.getTenantId())
            && Objects.equals(left.getOrgId(), right.getOrgId());
    }

    private static boolean sameScope(CrudDataScope left, CrudDataScope right) {
        if (left == null || right == null) {
            return left == right;
        }
        return left.isExplicitAll() == right.isExplicitAll()
            && Objects.equals(left.getDimensions(), right.getDimensions());
    }

    private static boolean sameFilters(List<QueryFilter> left, List<QueryFilter> right) {
        if (left == null || right == null) {
            return left == right;
        }
        if (left.size() != right.size()) {
            return false;
        }
        for (int i = 0; i < left.size(); i++) {
            QueryFilter leftFilter = left.get(i);
            QueryFilter rightFilter = right.get(i);
            if (leftFilter == null || rightFilter == null) {
                if (leftFilter != rightFilter) {
                    return false;
                }
                continue;
            }
            if (!Objects.equals(leftFilter.getField(), rightFilter.getField())
                || !Objects.equals(leftFilter.getOperator(), rightFilter.getOperator())
                || !Objects.equals(leftFilter.getValue(), rightFilter.getValue())) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameSorts(List<QuerySort> left, List<QuerySort> right) {
        if (left == null || right == null) {
            return left == right;
        }
        if (left.size() != right.size()) {
            return false;
        }
        for (int i = 0; i < left.size(); i++) {
            QuerySort leftSort = left.get(i);
            QuerySort rightSort = right.get(i);
            if (leftSort == null || rightSort == null) {
                if (leftSort != rightSort) {
                    return false;
                }
                continue;
            }
            if (!Objects.equals(leftSort.getField(), rightSort.getField())
                || !Objects.equals(leftSort.getDirection(), rightSort.getDirection())
                || !Objects.equals(leftSort.getTarget(), rightSort.getTarget())) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameTime(QueryTimeRange left, QueryTimeRange right) {
        if (left == null || right == null) {
            return left == right;
        }
        return Objects.equals(left.getField(), right.getField())
            && Objects.equals(left.getStart(), right.getStart())
            && Objects.equals(left.getEnd(), right.getEnd())
            && Objects.equals(left.getTimezone(), right.getTimezone());
    }

    private static boolean samePage(PageRequest left, PageRequest right) {
        if (left == null || right == null) {
            return left == right;
        }
        return left.getPage() == right.getPage() && left.getLimit() == right.getLimit();
    }
}
