package com.entloom.crud.core.capability.importing;

import com.entloom.crud.api.enums.AccessDecision;
import com.entloom.crud.api.enums.CrudOperationKey;
import com.entloom.crud.api.enums.ImportOperation;
import com.entloom.crud.api.model.SubjectContext;
import com.entloom.crud.core.foundation.taskfile.FileRef;
import com.entloom.crud.core.foundation.write.CrudWriteTransactionPolicy;
import com.entloom.crud.core.governance.scope.CrudDataScope;
import com.entloom.crud.core.runtime.spec.BaseSpec;
import com.entloom.crud.core.runtime.spec.GovernableSpec;
import com.entloom.crud.core.runtime.spec.OperationKeySpec;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 导入能力协议对象。
 */
public final class ImportSpec extends BaseSpec implements GovernableSpec<ImportSpec>, OperationKeySpec {
    private final ImportOperation operation;
    private final String format;
    private final ImportMode mode;
    private final FileRef sourceFile;
    private final String taskId;
    private final Integer batchSize;
    private final boolean async;
    private final CrudWriteTransactionPolicy transactionPolicy;
    private final Map<String, Object> payload;
    private final boolean includeExecutionMeta;

    private ImportSpec(Builder builder) {
        super(builder);
        this.operation = builder.operation == null ? ImportOperation.SUBMIT : builder.operation;
        this.format = builder.format;
        this.mode = builder.mode == null ? ImportMode.UPSERT : builder.mode;
        this.sourceFile = builder.sourceFile;
        this.taskId = builder.taskId;
        this.batchSize = builder.batchSize;
        this.async = builder.async;
        this.transactionPolicy = builder.transactionPolicy == null
            ? CrudWriteTransactionPolicy.PER_BATCH
            : builder.transactionPolicy;
        this.payload = Collections.unmodifiableMap(copyPayload(builder.payload));
        this.includeExecutionMeta = builder.includeExecutionMeta;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder().from(this);
    }

    public ImportOperation getOperation() {
        return operation;
    }

    @Override
    public CrudOperationKey getOperationKey() {
        return CrudOperationKey.of(operation);
    }

    public String getFormat() {
        return format;
    }

    public ImportMode getMode() {
        return mode;
    }

    public FileRef getSourceFile() {
        return sourceFile;
    }

    public String getTaskId() {
        return taskId;
    }

    public Integer getBatchSize() {
        return batchSize;
    }

    public boolean isAsync() {
        return async;
    }

    public CrudWriteTransactionPolicy getTransactionPolicy() {
        return transactionPolicy;
    }

    public Map<String, Object> getPayload() {
        return copyPayload(payload);
    }

    public boolean isIncludeExecutionMeta() {
        return includeExecutionMeta;
    }

    @Override
    public ImportSpec withSubject(SubjectContext subject) {
        return toBuilder().subject(subject).build();
    }

    @Override
    public ImportSpec withAttributes(Map<String, Object> attributes) {
        return toBuilder().attributes(attributes).build();
    }

    @Override
    public ImportSpec withGovernance(
        AccessDecision accessDecision,
        CrudDataScope grantedScope,
        CrudDataScope governanceScope
    ) {
        return toBuilder()
            .accessDecision(accessDecision)
            .grantedScope(grantedScope)
            .governanceScope(governanceScope)
            .build();
    }

    private Builder copyImportTo(Builder builder) {
        copyBaseTo(builder);
        return builder
            .operation(operation)
            .format(format)
            .mode(mode)
            .sourceFile(sourceFile)
            .taskId(taskId)
            .batchSize(batchSize)
            .async(async)
            .transactionPolicy(transactionPolicy)
            .payload(payload)
            .includeExecutionMeta(includeExecutionMeta);
    }

    private static Map<String, Object> copyPayload(Map<String, Object> source) {
        return source == null ? new HashMap<String, Object>() : new HashMap<String, Object>(source);
    }

    public static final class Builder extends BaseSpec.AbstractBuilder<Builder> {
        private ImportOperation operation = ImportOperation.SUBMIT;
        private String format;
        private ImportMode mode = ImportMode.UPSERT;
        private FileRef sourceFile;
        private String taskId;
        private Integer batchSize;
        private boolean async;
        private CrudWriteTransactionPolicy transactionPolicy = CrudWriteTransactionPolicy.PER_BATCH;
        private Map<String, Object> payload = new HashMap<String, Object>();
        private boolean includeExecutionMeta;

        @Override
        protected Builder self() {
            return this;
        }

        public Builder operation(ImportOperation operation) {
            this.operation = operation == null ? ImportOperation.SUBMIT : operation;
            return this;
        }

        public Builder format(String format) {
            this.format = format;
            return this;
        }

        public Builder mode(ImportMode mode) {
            this.mode = mode == null ? ImportMode.UPSERT : mode;
            return this;
        }

        public Builder sourceFile(FileRef sourceFile) {
            this.sourceFile = sourceFile;
            return this;
        }

        public Builder taskId(String taskId) {
            this.taskId = taskId;
            return this;
        }

        public Builder batchSize(Integer batchSize) {
            this.batchSize = batchSize;
            return this;
        }

        public Builder async(boolean async) {
            this.async = async;
            return this;
        }

        public Builder transactionPolicy(CrudWriteTransactionPolicy transactionPolicy) {
            this.transactionPolicy = transactionPolicy == null ? CrudWriteTransactionPolicy.PER_BATCH : transactionPolicy;
            return this;
        }

        public Builder payload(Map<String, Object> payload) {
            this.payload = copyPayload(payload);
            return this;
        }

        public Builder includeExecutionMeta(boolean includeExecutionMeta) {
            this.includeExecutionMeta = includeExecutionMeta;
            return this;
        }

        public Builder from(ImportSpec source) {
            if (source != null) {
                source.copyImportTo(this);
            }
            return this;
        }

        public ImportSpec build() {
            return new ImportSpec(this);
        }
    }
}
