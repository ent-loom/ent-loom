package com.entloom.crud.core.foundation.taskfile;

import com.entloom.crud.api.enums.CrudErrorCode;
import com.entloom.crud.core.exception.CrudException;
import com.entloom.crud.core.exception.ValidationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 默认本地磁盘文件服务。
 *
 * <p>该实现将文件内容与元数据分离持久化，适合作为框架默认实现；生产环境可替换为对象存储实现。</p>
 */
public class LocalFileService implements FileService {
    private static final long DEFAULT_MAX_BYTES = 50L * 1024L * 1024L;
    private static final Duration DEFAULT_RETENTION = Duration.ofHours(48);
    private static final String ATTR_PREFIX = "attr.";

    private final Path rootDirectory;
    private final Path contentDirectory;
    private final Path metadataDirectory;
    private final long maxBytes;
    private final Duration retention;

    public LocalFileService(String rootDirectory) {
        this(rootDirectory, DEFAULT_MAX_BYTES, DEFAULT_RETENTION);
    }

    public LocalFileService(String rootDirectory, long maxBytes, Duration retention) {
        String root = rootDirectory == null || rootDirectory.trim().isEmpty()
            ? Paths.get(System.getProperty("java.io.tmpdir"), "entloom-crud", "files").toString()
            : rootDirectory.trim();
        this.rootDirectory = Paths.get(root).toAbsolutePath().normalize();
        this.contentDirectory = this.rootDirectory.resolve("content");
        this.metadataDirectory = this.rootDirectory.resolve("metadata");
        this.maxBytes = maxBytes <= 0 ? DEFAULT_MAX_BYTES : maxBytes;
        this.retention = retention == null || retention.isZero() || retention.isNegative() ? DEFAULT_RETENTION : retention;
        ensureDirectories();
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
        return save(FileStreamWriteRequest.builder()
            .fileName(request.getFileName())
            .contentType(request.getContentType())
            .inputStream(new ByteArrayInputStream(content))
            .size(Long.valueOf(content.length))
            .attributes(request.getAttributes())
            .build());
    }

    @Override
    public FileRef save(FileStreamWriteRequest request) {
        if (request == null) {
            throw new ValidationException("文件流写入请求不能为空");
        }
        InputStream inputStream = request.getInputStream();
        if (inputStream == null) {
            throw new ValidationException("文件输入流不能为空");
        }
        Long declaredSize = request.getSize();
        if (declaredSize != null && declaredSize.longValue() > maxBytes) {
            throw new CrudException(CrudErrorCode.SYNC_LIMIT_EXCEEDED, "文件大小超过本地文件服务上限: " + maxBytes);
        }
        String fileName = requiredText(request.getFileName(), "文件名不能为空");
        String contentType = requiredText(request.getContentType(), "文件 Content-Type 不能为空");
        String fileId = newId();
        Instant now = Instant.now();
        Instant expiresAt = now.plus(retention);
        Map<String, Object> attributes = request.getAttributes();
        Path contentPath = contentPath(fileId);
        try {
            StreamCopyResult result = copyToFile(inputStream, contentPath);
            attributes.put("checksumSha256", result.checksumSha256);
            attributes.put("createdAt", now.toString());
            FileRef ref = FileRef.builder()
                .fileId(fileId)
                .fileName(fileName)
                .contentType(contentType)
                .size(Long.valueOf(result.size))
                .storageType(CrudFileStorageType.LOCAL)
                .storageKey("content/" + fileId + ".bin")
                .expiresAt(expiresAt)
                .attributes(attributes)
                .build();
            storeMetadata(ref);
            return ref;
        } catch (IOException ex) {
            deleteQuietly(contentPath);
            throw new CrudException(CrudErrorCode.INTERNAL_ERROR, "写入本地文件失败", ex);
        } catch (RuntimeException ex) {
            deleteQuietly(contentPath);
            throw ex;
        }
    }

    @Override
    public FileRef getRequired(String fileId) {
        String id = requiredText(fileId, "文件 ID 不能为空");
        Path metadataPath = metadataPath(id);
        if (!Files.exists(metadataPath)) {
            throw new CrudException(CrudErrorCode.FILE_NOT_FOUND, "文件不存在: " + id);
        }
        FileRef ref = loadMetadata(metadataPath);
        assertNotExpired(ref);
        return ref;
    }

    @Override
    public byte[] read(FileRef fileRef) {
        if (fileRef == null || isBlank(fileRef.getFileId())) {
            throw new ValidationException("读取文件时 fileRef.fileId 不能为空");
        }
        FileRef ref = getRequired(fileRef.getFileId());
        try {
            byte[] content = Files.readAllBytes(resolveContentPath(ref));
            Object expectedChecksum = ref.getAttributes().get("checksumSha256");
            if (expectedChecksum != null && !expectedChecksum.equals(sha256Hex(content))) {
                throw new CrudException(CrudErrorCode.FILE_METADATA_INVALID, "文件摘要校验失败: " + ref.getFileId());
            }
            return Arrays.copyOf(content, content.length);
        } catch (IOException ex) {
            throw new CrudException(CrudErrorCode.FILE_NOT_FOUND, "读取本地文件失败: " + ref.getFileId(), ex);
        }
    }

    @Override
    public InputStream openStream(FileRef fileRef) {
        if (fileRef == null || isBlank(fileRef.getFileId())) {
            throw new ValidationException("读取文件流时 fileRef.fileId 不能为空");
        }
        FileRef ref = getRequired(fileRef.getFileId());
        try {
            return Files.newInputStream(resolveContentPath(ref));
        } catch (IOException ex) {
            throw new CrudException(CrudErrorCode.FILE_NOT_FOUND, "打开本地文件流失败: " + ref.getFileId(), ex);
        }
    }

    private StreamCopyResult copyToFile(InputStream inputStream, Path contentPath) throws IOException {
        MessageDigest digest = sha256Digest();
        long total = 0L;
        byte[] buffer = new byte[8192];
        try (InputStream in = inputStream; OutputStream out = Files.newOutputStream(contentPath)) {
            int len;
            while ((len = in.read(buffer)) != -1) {
                total += len;
                if (total > maxBytes) {
                    throw new CrudException(CrudErrorCode.SYNC_LIMIT_EXCEEDED, "文件大小超过本地文件服务上限: " + maxBytes);
                }
                digest.update(buffer, 0, len);
                out.write(buffer, 0, len);
            }
        }
        return new StreamCopyResult(total, toHex(digest.digest()));
    }

    private void storeMetadata(FileRef ref) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("fileId", ref.getFileId());
        properties.setProperty("fileName", ref.getFileName());
        properties.setProperty("contentType", ref.getContentType());
        properties.setProperty("size", String.valueOf(ref.getSize()));
        properties.setProperty("storageType", ref.getStorageType().name());
        properties.setProperty("storageKey", ref.getStorageKey());
        properties.setProperty("expiresAt", ref.getExpiresAt().toString());
        for (Map.Entry<String, Object> entry : ref.getAttributes().entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                properties.setProperty(ATTR_PREFIX + entry.getKey(), String.valueOf(entry.getValue()));
            }
        }
        try (OutputStream output = Files.newOutputStream(metadataPath(ref.getFileId()))) {
            properties.store(output, "ent-loom-crud file metadata");
        }
    }

    private FileRef loadMetadata(Path metadataPath) {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(metadataPath)) {
            properties.load(input);
        } catch (IOException ex) {
            throw new CrudException(CrudErrorCode.FILE_METADATA_INVALID, "读取文件元数据失败", ex);
        }
        Map<String, Object> attributes = new HashMap<String, Object>();
        for (String name : properties.stringPropertyNames()) {
            if (name.startsWith(ATTR_PREFIX)) {
                attributes.put(name.substring(ATTR_PREFIX.length()), properties.getProperty(name));
            }
        }
        return FileRef.builder()
            .fileId(properties.getProperty("fileId"))
            .fileName(properties.getProperty("fileName"))
            .contentType(properties.getProperty("contentType"))
            .size(Long.valueOf(properties.getProperty("size", "0")))
            .storageType(CrudFileStorageType.valueOf(properties.getProperty("storageType", CrudFileStorageType.LOCAL.name())))
            .storageKey(properties.getProperty("storageKey"))
            .expiresAt(Instant.parse(properties.getProperty("expiresAt")))
            .attributes(attributes)
            .build();
    }

    private Path resolveContentPath(FileRef ref) {
        String storageKey = ref.getStorageKey();
        if (isBlank(storageKey)) {
            throw new CrudException(CrudErrorCode.FILE_METADATA_INVALID, "文件 storageKey 不能为空: " + ref.getFileId());
        }
        Path path = rootDirectory.resolve(storageKey).toAbsolutePath().normalize();
        if (!path.startsWith(contentDirectory)) {
            throw new CrudException(CrudErrorCode.FILE_METADATA_INVALID, "文件 storageKey 非法: " + ref.getFileId());
        }
        return path;
    }

    private Path metadataPath(String fileId) {
        String normalizedFileId = requiredText(fileId, "文件 ID 不能为空");
        if (normalizedFileId.indexOf('/') >= 0 || normalizedFileId.indexOf('\\') >= 0) {
            throw new ValidationException("文件 ID 不允许包含路径分隔符: " + fileId);
        }
        Path path = metadataDirectory.resolve(normalizedFileId + ".properties").toAbsolutePath().normalize();
        if (!path.startsWith(metadataDirectory) || !metadataDirectory.equals(path.getParent())) {
            throw new ValidationException("文件 ID 不允许访问元数据目录之外的路径: " + fileId);
        }
        return path;
    }

    private Path contentPath(String fileId) {
        return contentDirectory.resolve(requiredText(fileId, "文件 ID 不能为空") + ".bin").toAbsolutePath().normalize();
    }

    private void ensureDirectories() {
        try {
            Files.createDirectories(contentDirectory);
            Files.createDirectories(metadataDirectory);
        } catch (IOException ex) {
            throw new CrudException(CrudErrorCode.INTERNAL_ERROR, "初始化本地文件目录失败: " + rootDirectory, ex);
        }
    }

    private static void assertNotExpired(FileRef ref) {
        if (ref.getExpiresAt() != null && ref.getExpiresAt().isBefore(Instant.now())) {
            throw new CrudException(CrudErrorCode.FILE_EXPIRED, "文件已过期: " + ref.getFileId());
        }
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

    private static String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String sha256Hex(byte[] content) {
        try {
            return toHex(sha256Digest().digest(content));
        } catch (Exception ex) {
            throw new CrudException(CrudErrorCode.INTERNAL_ERROR, "计算文件摘要失败", ex);
        }
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (Exception ex) {
            throw new CrudException(CrudErrorCode.INTERNAL_ERROR, "初始化文件摘要失败", ex);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte item : bytes) {
            builder.append(String.format("%02x", Byte.valueOf(item)));
        }
        return builder.toString();
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // 清理失败不覆盖主异常。
        }
    }

    private static final class StreamCopyResult {
        private final long size;
        private final String checksumSha256;

        private StreamCopyResult(long size, String checksumSha256) {
            this.size = size;
            this.checksumSha256 = checksumSha256;
        }
    }
}
