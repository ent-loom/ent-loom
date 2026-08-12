package com.entloom.crud.engine.jdbc.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.entloom.crud.core.idempotency.IdempotencyAcquireResult;
import com.entloom.crud.core.idempotency.IdempotencyState;
import com.entloom.crud.core.idempotency.IdempotencyStore;
import com.entloom.crud.engine.jdbc.dialect.JdbcDialect;
import com.entloom.crud.engine.jdbc.dialect.JdbcDialectResolver;
import com.entloom.crud.engine.jdbc.dialect.StandardJdbcDialect;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * 基于 JDBC 的共享幂等存储实现。
 */
public class JdbcIdempotencyStore implements IdempotencyStore {
    /** 默认表名。 */
    private static final String DEFAULT_TABLE_NAME = "entloom_idempotency_record";
    /** 默认保留时长。 */
    private static final Duration DEFAULT_RETENTION = Duration.ofHours(48);
    /** 处理中状态值。 */
    private static final String STATUS_PROCESSING = IdempotencyState.PROCESSING.name();
    /** 成功状态值。 */
    private static final String STATUS_SUCCEEDED = IdempotencyState.SUCCEEDED.name();

    /** JDBC 模板。 */
    private final JdbcTemplate jdbcTemplate;
    /** 对象映射器。 */
    private final ObjectMapper objectMapper;
    /** 时钟。 */
    private final Clock clock;
    /** 保留时长。 */
    private final Duration retention;
    /** 表名。 */
    private final String tableName;
    /** 数据库方言。 */
    private final JdbcDialect dialect;

    public JdbcIdempotencyStore(JdbcTemplate jdbcTemplate) {
        this(
            jdbcTemplate,
            DEFAULT_TABLE_NAME,
            Clock.systemUTC(),
            DEFAULT_RETENTION,
            JdbcDialectResolver.resolve(jdbcTemplate)
        );
    }

    public JdbcIdempotencyStore(JdbcTemplate jdbcTemplate, String tableName, Clock clock, Duration retention) {
        this(jdbcTemplate, tableName, clock, retention, JdbcDialectResolver.resolve(jdbcTemplate));
    }

    public JdbcIdempotencyStore(
        JdbcTemplate jdbcTemplate,
        String tableName,
        Clock clock,
        Duration retention,
        JdbcDialect dialect
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.tableName = validateTableName(tableName);
        this.clock = clock;
        this.retention = retention == null ? DEFAULT_RETENTION : retention;
        this.dialect = dialect == null ? StandardJdbcDialect.GENERIC : dialect;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enableDefaultTyping(ObjectMapper.DefaultTyping.NON_FINAL);
    }

    public void initializeSchema() {
        try {
            jdbcTemplate.execute(dialect.createIdempotencyTableSql(tableName));
        } catch (DataAccessException ex) {
            if (!dialect.isTableAlreadyExists(ex)) {
                throw ex;
            }
        }
    }

    /**
     * 尝试获取幂等执行资格。
     */
    @Override
    public IdempotencyAcquireResult tryAcquire(String key, String payloadHash) {
        String keyHash = hashKey(key);
        Instant now = clock.instant();
        Timestamp nowTs = Timestamp.from(now);
        Timestamp expiresAtTs = Timestamp.from(now.plus(retention));

        for (int attempt = 0; attempt < 3; attempt++) {
            StoredRow existing = findByKeyHash(keyHash);
            if (existing == null) {
                if (insertProcessing(keyHash, key, payloadHash, nowTs, expiresAtTs)) {
                    return IdempotencyAcquireResult.acquired();
                }
                continue;
            }
            if (existing.isExpired(now)) {
                if (replaceExpired(existing, key, payloadHash, nowTs, expiresAtTs)) {
                    return IdempotencyAcquireResult.acquired();
                }
                continue;
            }
            if (!existing.getPayloadHash().equals(payloadHash)) {
                return IdempotencyAcquireResult.payloadConflict();
            }
            if (STATUS_SUCCEEDED.equals(existing.getStatus())) {
                return IdempotencyAcquireResult.replay(deserialize(existing.getResultBody()));
            }
            return IdempotencyAcquireResult.inProgress();
        }

        throw new IllegalStateException("多次重试后仍未获取到幂等记录");
    }

    /**
     * 将幂等记录标记为成功。
     */
    @Override
    public void markSucceeded(String key, Object result) {
        String sql =
            "update " + tableName +
                " set status=?, result_body=?, expires_at=?, updated_at=? where storage_key_hash=?";
        Instant now = clock.instant();
        int updated = jdbcTemplate.update(
            sql,
            STATUS_SUCCEEDED,
            serialize(result),
            Timestamp.from(now.plus(retention)),
            Timestamp.from(now),
            hashKey(key)
        );
        if (updated != 1) {
            throw new IllegalStateException("标记成功时缺少幂等记录: " + key);
        }
    }

    @Override
    public void release(String key) {
        jdbcTemplate.update("delete from " + tableName + " where storage_key_hash=?", hashKey(key));
    }

    private StoredRow findByKeyHash(String keyHash) {
        List<StoredRow> rows = jdbcTemplate.query(
            "select storage_key_hash, storage_key, payload_hash, status, result_body, expires_at from " + tableName +
                " where storage_key_hash=?",
            new StoredRowMapper(),
            keyHash
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    private boolean insertProcessing(
        String keyHash,
        String key,
        String payloadHash,
        Timestamp nowTs,
        Timestamp expiresAtTs
    ) {
        String sql =
            "insert into " + tableName +
                "(storage_key_hash, storage_key, payload_hash, status, result_body, expires_at, created_at, updated_at) " +
                "values (?, ?, ?, ?, ?, ?, ?, ?)";
        try {
            return jdbcTemplate.update(sql, keyHash, key, payloadHash, STATUS_PROCESSING, null, expiresAtTs, nowTs, nowTs) == 1;
        } catch (DataIntegrityViolationException ex) {
            return false;
        }
    }

    private boolean replaceExpired(
        StoredRow existing,
        String key,
        String payloadHash,
        Timestamp nowTs,
        Timestamp expiresAtTs
    ) {
        String sql =
            "update " + tableName +
                " set storage_key=?, payload_hash=?, status=?, result_body=?, expires_at=?, created_at=?, updated_at=? " +
                "where storage_key_hash=? and expires_at=?";
        return jdbcTemplate.update(
            sql,
            key,
            payloadHash,
            STATUS_PROCESSING,
            null,
            expiresAtTs,
            nowTs,
            nowTs,
            existing.getStorageKeyHash(),
            existing.getExpiresAt()
        ) == 1;
    }

    private String serialize(Object result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("幂等重放结果序列化失败", e);
        }
    }

    private Object deserialize(String resultBody) {
        if (resultBody == null || resultBody.trim().isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(resultBody, Object.class);
        } catch (IOException e) {
            throw new IllegalStateException("幂等重放结果反序列化失败", e);
        }
    }

    /**
     * 计算幂等键的哈希值。
     */
    private String hashKey(String key) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(key.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private String validateTableName(String tableName) {
        if (tableName == null || !tableName.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("非法的幂等表名: " + tableName);
        }
        return tableName;
    }

    /**
     * 幂等记录查询结果映射对象。
     */
    private static final class StoredRow {
        /** 存储键哈希。 */
        private final String storageKeyHash;
        /** 幂等键。 */
        private final String key;
        /** 载荷哈希。 */
        private final String payloadHash;
        /** 状态值。 */
        private final String status;
        /** 结果内容。 */
        private final String resultBody;
        /** 过期时间。 */
        private final Timestamp expiresAt;

        private StoredRow(
            String storageKeyHash,
            String key,
            String payloadHash,
            String status,
            String resultBody,
            Timestamp expiresAt
        ) {
            this.storageKeyHash = storageKeyHash;
            this.key = key;
            this.payloadHash = payloadHash;
            this.status = status;
            this.resultBody = resultBody;
            this.expiresAt = expiresAt;
        }

        private boolean isExpired(Instant now) {
            return expiresAt.toInstant().compareTo(now) <= 0;
        }

        private String getStorageKeyHash() {
            return storageKeyHash;
        }

        private String getKey() {
            return key;
        }

        private String getPayloadHash() {
            return payloadHash;
        }

        private String getStatus() {
            return status;
        }

        private String getResultBody() {
            return resultBody;
        }

        private Timestamp getExpiresAt() {
            return expiresAt;
        }
    }

    /**
     * 幂等记录行映射器。
     */
    private static final class StoredRowMapper implements RowMapper<StoredRow> {
        @Override
        public StoredRow mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new StoredRow(
                rs.getString("storage_key_hash"),
                rs.getString("storage_key"),
                rs.getString("payload_hash"),
                rs.getString("status"),
                rs.getString("result_body"),
                rs.getTimestamp("expires_at")
            );
        }
    }
}
