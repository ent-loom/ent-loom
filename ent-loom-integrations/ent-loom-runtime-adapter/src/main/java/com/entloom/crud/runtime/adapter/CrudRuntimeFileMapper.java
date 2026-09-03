package com.entloom.crud.runtime.adapter;

import com.entloom.crud.core.foundation.taskfile.CrudFilePurpose;
import com.entloom.crud.core.foundation.taskfile.CrudFileStorageType;
import com.entloom.crud.core.foundation.taskfile.FileRef;
import com.entloom.crud.core.foundation.taskfile.FileStreamWriteRequest;
import com.entloom.crud.core.foundation.taskfile.FileWriteRequest;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * CRUD 文件模型与 runtime 文件模型之间的映射。
 *
 * <p>CRUD 文件用途和主体归属目前位于 attributes，适配器将其提升到 runtime 的正式字段。</p>
 */
public final class CrudRuntimeFileMapper {
    private static final String PURPOSE = "purpose";
    private static final String SUBJECT_ID = "subjectId";
    private static final String SUBJECT_TYPE = "subjectType";
    private static final String TENANT_ID = "tenantId";
    private static final String ORG_ID = "orgId";
    private static final String STORAGE_TYPE = "storageType";

    private final RuntimeSubjectContextMapper subjectMapper;

    public CrudRuntimeFileMapper() {
        this(new RuntimeSubjectContextMapper());
    }

    public CrudRuntimeFileMapper(RuntimeSubjectContextMapper subjectMapper) {
        if (subjectMapper == null) {
            throw new IllegalArgumentException("subjectMapper 不能为空");
        }
        this.subjectMapper = subjectMapper;
    }

    public com.entloom.runtime.contract.file.FileWriteRequest toRuntime(FileWriteRequest source) {
        if (source == null) {
            throw new IllegalArgumentException("CRUD 文件写入请求不能为空");
        }
        Map<String, Object> attributes = source.getAttributes();
        return com.entloom.runtime.contract.file.FileWriteRequest.builder()
            .fileName(source.getFileName())
            .contentType(source.getContentType())
            .content(source.getContent())
            .purpose(requiredPurpose(attributes))
            .owner(owner(attributes))
            .expiresAt(readInstant(attributes.get("expiresAt")))
            .attributes(RuntimeAttributeCodec.toRuntimeAttributes(attributes))
            .build();
    }

    public com.entloom.runtime.contract.file.FileStreamWriteRequest toRuntime(FileStreamWriteRequest source) {
        if (source == null) {
            throw new IllegalArgumentException("CRUD 文件流写入请求不能为空");
        }
        Map<String, Object> attributes = source.getAttributes();
        return com.entloom.runtime.contract.file.FileStreamWriteRequest.builder()
            .fileName(source.getFileName())
            .contentType(source.getContentType())
            .inputStream(source.getInputStream())
            .size(source.getSize())
            .purpose(requiredPurpose(attributes))
            .owner(owner(attributes))
            .expiresAt(readInstant(attributes.get("expiresAt")))
            .attributes(RuntimeAttributeCodec.toRuntimeAttributes(attributes))
            .build();
    }

    public com.entloom.runtime.contract.file.FileRef toRuntime(FileRef source) {
        if (source == null) {
            throw new IllegalArgumentException("CRUD 文件引用不能为空");
        }
        Map<String, Object> attributes = source.getAttributes();
        if (source.getSize() == null) {
            throw new IllegalArgumentException("CRUD 文件大小不能为空: " + source.getFileId());
        }
        return com.entloom.runtime.contract.file.FileRef.builder()
            .fileId(source.getFileId())
            .fileName(source.getFileName())
            .contentType(source.getContentType())
            .size(source.getSize().longValue())
            .storageKey(source.getStorageKey())
            .purpose(requiredPurpose(attributes))
            .owner(owner(attributes))
            .expiresAt(source.getExpiresAt())
            .attributes(runtimeFileAttributes(source))
            .build();
    }

    public FileRef toCrud(com.entloom.runtime.contract.file.FileRef source) {
        if (source == null) {
            throw new IllegalArgumentException("runtime 文件引用不能为空");
        }
        Map<String, Object> attributes = new LinkedHashMap<String, Object>(
            RuntimeAttributeCodec.toCrudAttributes(source.getAttributes())
        );
        attributes.put(PURPOSE, requireCrudPurpose(source.getPurpose()).name());
        attributes.put(SUBJECT_ID, source.getOwner().getSubjectId());
        attributes.put(SUBJECT_TYPE, source.getOwner().getSubjectType());
        if (source.getOwner().getTenantId() != null) {
            attributes.put(TENANT_ID, source.getOwner().getTenantId());
        }
        if (source.getOwner().getOrgId() != null) {
            attributes.put(ORG_ID, source.getOwner().getOrgId());
        }
        CrudFileStorageType storageType = readStorageType(source.getAttributes().get(STORAGE_TYPE));
        attributes.put(STORAGE_TYPE, storageType.name());
        return FileRef.builder()
            .fileId(source.getFileId())
            .fileName(source.getFileName())
            .contentType(source.getContentType())
            .size(Long.valueOf(source.getSize()))
            .storageType(storageType)
            .storageKey(source.getStorageKey())
            .expiresAt(source.getExpiresAt())
            .attributes(attributes)
            .build();
    }

    private com.entloom.runtime.contract.context.SubjectContext owner(Map<String, Object> attributes) {
        if (attributes == null) {
            throw new IllegalArgumentException("文件主体归属属性不能为空");
        }
        com.entloom.crud.api.model.SubjectContext subject = new com.entloom.crud.api.model.SubjectContext();
        subject.setSubjectId(requiredAttribute(attributes, SUBJECT_ID));
        subject.setTenantId(textAttribute(attributes.get(TENANT_ID)));
        subject.setOrgId(textAttribute(attributes.get(ORG_ID)));
        String subjectType = textAttribute(attributes.get(SUBJECT_TYPE));
        com.entloom.runtime.contract.context.SubjectContext runtime = subjectMapper.toRuntime(subject);
        if (subjectType == null || subjectType.equals(subjectMapper.getDefaultSubjectType())) {
            return runtime;
        }
        return com.entloom.runtime.contract.context.SubjectContext.builder()
            .subjectId(runtime.getSubjectId())
            .subjectType(subjectType)
            .tenantId(runtime.getTenantId())
            .orgId(runtime.getOrgId())
            .attributes(runtime.getAttributes())
            .build();
    }

    private static Map<String, String> runtimeFileAttributes(FileRef source) {
        Map<String, Object> attributes = source.getAttributes();
        Map<String, String> result = new LinkedHashMap<String, String>(RuntimeAttributeCodec.toRuntimeAttributes(attributes));
        result.put(STORAGE_TYPE, source.getStorageType() == null
            ? CrudFileStorageType.EXTERNAL.name()
            : source.getStorageType().name());
        return result;
    }

    private static String requiredPurpose(Map<String, Object> attributes) {
        String purpose = requiredAttribute(attributes, PURPOSE);
        return requireCrudPurpose(purpose).name();
    }

    private static CrudFilePurpose requireCrudPurpose(String purpose) {
        try {
            return CrudFilePurpose.valueOf(purpose.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("不支持的 CRUD 文件用途: " + purpose, ex);
        }
    }

    private static String requiredAttribute(Map<String, Object> attributes, String name) {
        String value = attributes == null ? null : textAttribute(attributes.get(name));
        if (value == null) {
            throw new IllegalArgumentException("文件属性不能为空: " + name);
        }
        return value;
    }

    private static String textAttribute(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static java.time.Instant readInstant(Object value) {
        String text = textAttribute(value);
        return text == null ? null : java.time.Instant.parse(text);
    }

    private static CrudFileStorageType readStorageType(String value) {
        if (value == null || value.trim().isEmpty()) {
            return CrudFileStorageType.EXTERNAL;
        }
        try {
            return CrudFileStorageType.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("不支持的 CRUD 文件存储类型: " + value, ex);
        }
    }
}
