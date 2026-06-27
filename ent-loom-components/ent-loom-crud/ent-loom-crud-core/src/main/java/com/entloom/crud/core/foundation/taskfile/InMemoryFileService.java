package com.entloom.crud.core.foundation.taskfile;

import com.entloom.crud.api.enums.CrudErrorCode;
import com.entloom.crud.core.exception.CrudException;
import com.entloom.crud.core.exception.ValidationException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 小文件默认内存实现。
 *
 * <p>该实现只用于本地开发和 MVP 默认链路，生产环境应替换为对象存储或业务文件服务。</p>
 */
public class InMemoryFileService implements FileService {
    private static final long DEFAULT_MAX_BYTES = 10L * 1024L * 1024L;
    private static final Duration DEFAULT_RETENTION = Duration.ofHours(48);

    private final Map<String, StoredFile> files = new ConcurrentHashMap<String, StoredFile>();
    private final long maxBytes;
    private final Duration retention;

    public InMemoryFileService() {
        this(DEFAULT_MAX_BYTES, DEFAULT_RETENTION);
    }

    public InMemoryFileService(long maxBytes, Duration retention) {
        this.maxBytes = maxBytes <= 0 ? DEFAULT_MAX_BYTES : maxBytes;
        this.retention = retention == null || retention.isNegative() || retention.isZero() ? DEFAULT_RETENTION : retention;
    }

    @Override
    public FileRef save(FileWriteRequest request) {
        if (request == null) {
            throw new ValidationException("文件写入请求不能为空");
        }
        byte[] content = request.getContent();
        if (content == null) {
            throw new ValidationException("文件内容不能为空");
        }
        if (content.length > maxBytes) {
            throw new CrudException(CrudErrorCode.SYNC_LIMIT_EXCEEDED, "文件大小超过默认内存文件服务上限: " + maxBytes);
        }
        String fileName = requiredText(request.getFileName(), "文件名不能为空");
        String contentType = requiredText(request.getContentType(), "文件 Content-Type 不能为空");
        String fileId = newFileId();
        Instant now = Instant.now();
        Instant expiresAt = now.plus(retention);
        Map<String, Object> attributes = request.getAttributes();
        attributes.put("checksumSha256", sha256Hex(content));
        attributes.put("createdAt", now.toString());
        FileRef ref = FileRef.builder()
            .fileId(fileId)
            .fileName(fileName)
            .contentType(contentType)
            .size(Long.valueOf(content.length))
            .storageType(CrudFileStorageType.LOCAL)
            .storageKey(fileId)
            .expiresAt(expiresAt)
            .attributes(attributes)
            .build();
        files.put(fileId, new StoredFile(ref, content));
        return ref;
    }

    @Override
    public FileRef getRequired(String fileId) {
        StoredFile stored = files.get(requiredText(fileId, "文件 ID 不能为空"));
        if (stored == null) {
            throw new CrudException(CrudErrorCode.FILE_NOT_FOUND, "文件不存在: " + fileId);
        }
        assertNotExpired(stored.ref);
        return stored.ref;
    }

    @Override
    public byte[] read(FileRef fileRef) {
        if (fileRef == null || isBlank(fileRef.getFileId())) {
            throw new ValidationException("读取文件时 fileRef.fileId 不能为空");
        }
        StoredFile stored = files.get(fileRef.getFileId());
        if (stored == null) {
            throw new CrudException(CrudErrorCode.FILE_NOT_FOUND, "文件不存在: " + fileRef.getFileId());
        }
        assertNotExpired(stored.ref);
        return Arrays.copyOf(stored.content, stored.content.length);
    }

    private void assertNotExpired(FileRef ref) {
        if (ref.getExpiresAt() != null && ref.getExpiresAt().isBefore(Instant.now())) {
            throw new CrudException(CrudErrorCode.FILE_EXPIRED, "文件已过期: " + ref.getFileId());
        }
    }

    private static String newFileId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String requiredText(String value, String message) {
        if (isBlank(value)) {
            throw new ValidationException(message);
        }
        return value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String sha256Hex(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(content);
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                builder.append(String.format("%02x", Byte.valueOf(item)));
            }
            return builder.toString();
        } catch (Exception ex) {
            throw new CrudException(CrudErrorCode.INTERNAL_ERROR, "计算文件摘要失败", ex);
        }
    }

    private static final class StoredFile {
        private final FileRef ref;
        private final byte[] content;

        private StoredFile(FileRef ref, byte[] content) {
            this.ref = ref;
            this.content = Arrays.copyOf(content, content.length);
        }
    }
}
