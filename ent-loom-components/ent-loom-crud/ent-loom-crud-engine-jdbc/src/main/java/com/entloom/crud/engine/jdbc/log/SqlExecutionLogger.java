package com.entloom.crud.engine.jdbc.log;

import com.entloom.crud.core.runtime.context.CrudExecutionContext;
import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 统一 SQL 日志输出器。
 */
public class SqlExecutionLogger {
    /** 日志记录器。 */
    private static final Logger log = LoggerFactory.getLogger(SqlExecutionLogger.class);
    /** 默认慢 SQL 阈值（毫秒）。 */
    private static final long DEFAULT_SLOW_SQL_THRESHOLD_MS = 1000L;
    /** 日期格式模板。 */
    private static final String DATE_PATTERN = "yyyy-MM-dd HH:mm:ss";
    /** SQL 日志级别。 */
    private volatile SqlLogLevel mode = SqlLogLevel.SAFE;
    /** 采样率。 */
    private volatile double sampleRate = 0.1d;
    /** 输出模式。 */
    private volatile Output output = Output.TEMPLATE;
    /** 是否格式化输出。 */
    private volatile boolean pretty;

    public void setMode(SqlLogLevel mode) {
        this.mode = mode == null ? SqlLogLevel.SAFE : mode;
    }

    /**
     * 设置采样率并限制取值范围。
     */
    public void setSampleRate(double sampleRate) {
        if (sampleRate < 0d) {
            this.sampleRate = 0d;
            return;
        }
        if (sampleRate > 1d) {
            this.sampleRate = 1d;
            return;
        }
        this.sampleRate = sampleRate;
    }

    public void setOutput(Output output) {
        this.output = output == null ? Output.TEMPLATE : output;
    }

    public void setPretty(boolean pretty) {
        this.pretty = pretty;
    }

    /**
     * 输出 SQL 执行日志。
     *
     * @param context 执行上下文
     * @param op 操作类型
     * @param phase 阶段
     * @param sql SQL 模板
     * @param args 参数
     * @param rows 行数
     * @param costMs 耗时
     */
    public void logSql(
        CrudExecutionContext context,
        String op,
        String phase,
        String sql,
        List<Object> args,
        int rows,
        long costMs
    ) {
        if (!shouldLogSuccess(context, op, phase, sql, costMs)) {
            return;
        }
        doLog(false, context, op, phase, sql, args, rows, costMs, null);
    }

    public void logSqlFailure(
        CrudExecutionContext context,
        String op,
        String phase,
        String sql,
        List<Object> args,
        long costMs,
        Throwable error
    ) {
        doLog(true, context, op, phase, sql, args, -1, costMs, error);
    }

    private void doLog(
        boolean warn,
        CrudExecutionContext context,
        String op,
        String phase,
        String sql,
        List<Object> args,
        int rows,
        long costMs,
        Throwable error
    ) {
        String digest = digest(sql);
        String routeKey = compactRouteKey(context.getRouteKey());
        String execSql = mode == SqlLogLevel.FULL
            ? (pretty ? formatExecutableSql(toExecutableSql(sql, args)) : toExecutableSql(sql, args))
            : null;
        String errorType = error == null ? "na" : error.getClass().getSimpleName();

        LogMessage message = mode == SqlLogLevel.FULL
            ? buildFullMessage(warn, routeKey, context, op, phase, sql, args, execSql, rows, costMs, digest, errorType)
            : buildSafeMessage(warn, routeKey, context, op, phase, sql, rows, costMs, digest, errorType);

        if (warn) {
            logWarn(message, error);
            return;
        }
        log.info(message.template, message.args);
    }

    private LogMessage buildSafeMessage(
        boolean warn,
        String routeKey,
        CrudExecutionContext context,
        String op,
        String phase,
        String sql,
        int rows,
        long costMs,
        String digest,
        String errorType
    ) {
        if (warn) {
            return new LogMessage(
                "[ent-loom-crud][sql] rk={} sc={} op={} ph={} ms={} rows={} dig={} sql={} err={}",
                routeKey,
                context.getScene(),
                op,
                phase,
                costMs,
                rows,
                digest,
                sql,
                errorType
            );
        }
        return new LogMessage(
            "[ent-loom-crud][sql] rk={} sc={} op={} ph={} ms={} rows={} dig={} sql={}",
            routeKey,
            context.getScene(),
            op,
            phase,
            costMs,
            rows,
            digest,
            sql
        );
    }

    private LogMessage buildFullMessage(
        boolean warn,
        String routeKey,
        CrudExecutionContext context,
        String op,
        String phase,
        String sql,
        List<Object> args,
        String execSql,
        int rows,
        long costMs,
        String digest,
        String errorType
    ) {
        if (output == Output.EXEC) {
            String template = warn
                ? "[ent-loom-crud][sql] rk={} sc={} op={} ph={} ms={} rows={} dig={} execSql=\n{} err={}"
                : "[ent-loom-crud][sql] rk={} sc={} op={} ph={} ms={} rows={} dig={} execSql=\n{}";
            return warn
                ? new LogMessage(template, routeKey, context.getScene(), op, phase, costMs, rows, digest, execSql, errorType)
                : new LogMessage(template, routeKey, context.getScene(), op, phase, costMs, rows, digest, execSql);
        }

        if (output == Output.BOTH) {
            String template = warn
                ? "[ent-loom-crud][sql] rk={} sc={} op={} ph={} ms={} rows={} dig={} sql={} args={} execSql=\n{} err={}"
                : "[ent-loom-crud][sql] rk={} sc={} op={} ph={} ms={} rows={} dig={} sql={} args={} execSql=\n{}";
            return warn
                ? new LogMessage(template, routeKey, context.getScene(), op, phase, costMs, rows, digest, sql, args, execSql, errorType)
                : new LogMessage(template, routeKey, context.getScene(), op, phase, costMs, rows, digest, sql, args, execSql);
        }

        String template = warn
            ? "[ent-loom-crud][sql] rk={} sc={} op={} ph={} ms={} rows={} dig={} sql={} args={} err={}"
            : "[ent-loom-crud][sql] rk={} sc={} op={} ph={} ms={} rows={} dig={} sql={} args={}";
        return warn
            ? new LogMessage(template, routeKey, context.getScene(), op, phase, costMs, rows, digest, sql, args, errorType)
            : new LogMessage(template, routeKey, context.getScene(), op, phase, costMs, rows, digest, sql, args);
    }

    private void logWarn(LogMessage message, Throwable error) {
        if (error == null) {
            log.warn(message.template, message.args);
            return;
        }
        Object[] args = new Object[message.args.length + 1];
        System.arraycopy(message.args, 0, args, 0, message.args.length);
        args[args.length - 1] = error;
        log.warn(message.template, args);
    }

    private boolean shouldLogSuccess(
        CrudExecutionContext context,
        String op,
        String phase,
        String sql,
        long costMs
    ) {
        if (mode == SqlLogLevel.FULL) {
            return true;
        }
        if (costMs >= DEFAULT_SLOW_SQL_THRESHOLD_MS) {
            return true;
        }
        if (sampleRate >= 1d) {
            return true;
        }
        if (sampleRate <= 0d) {
            return false;
        }
        String sampleKey = compactRouteKey(context.getRouteKey()) + "|" + context.getScene() + "|" + op + "|" + phase + "|" + sql;
        long hash = positiveHash(sampleKey);
        double normalized = (hash % 1_000_000L) / 1_000_000d;
        return normalized < sampleRate;
    }

    /**
     * 将模板 SQL 和参数渲染为可执行 SQL。
     */
    String toExecutableSql(String sql, List<Object> args) {
        if (sql == null || sql.isEmpty() || args == null || args.isEmpty()) {
            return sql;
        }
        StringBuilder rendered = new StringBuilder(sql.length() + args.size() * 16);
        int argIndex = 0;
        boolean inSingleQuote = false;
        for (int i = 0; i < sql.length(); i++) {
            char ch = sql.charAt(i);
            if (ch == '\'') {
                rendered.append(ch);
                if (inSingleQuote && i + 1 < sql.length() && sql.charAt(i + 1) == '\'') {
                    rendered.append(sql.charAt(++i));
                    continue;
                }
                inSingleQuote = !inSingleQuote;
                continue;
            }
            if (ch == '?' && !inSingleQuote && argIndex < args.size()) {
                rendered.append(renderValue(args.get(argIndex++)));
                continue;
            }
            rendered.append(ch);
        }
        return rendered.toString();
    }

    /**
     * 格式化可执行 SQL 的展示内容。
     */
    String formatExecutableSql(String sql) {
        if (sql == null || sql.isEmpty()) {
            return sql;
        }
        String formatted = sql;
        formatted = formatted.replaceAll("(?i)\\bfrom\\b", "\nFROM");
        formatted = formatted.replaceAll("(?i)\\bwhere\\b", "\nWHERE");
        formatted = formatted.replaceAll("(?i)\\bgroup by\\b", "\nGROUP BY");
        formatted = formatted.replaceAll("(?i)\\bhaving\\b", "\nHAVING");
        formatted = formatted.replaceAll("(?i)\\border by\\b", "\nORDER BY");
        formatted = formatted.replaceAll("(?i)\\blimit\\b", "\nLIMIT");
        formatted = formatted.replaceAll("(?i)\\boffset\\b", "\nOFFSET");
        formatted = formatted.replaceAll("(?i)\\band\\b", "\n  AND");
        formatted = formatted.replaceAll("(?i)\\bor\\b", "\n  OR");
        return formatted.trim();
    }

    /**
     * 渲染 SQL 参数值。
     */
    private String renderValue(Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        if (value instanceof Date) {
            return "'" + new SimpleDateFormat(DATE_PATTERN, Locale.CHINA).format((Date) value) + "'";
        }
        if (value instanceof TemporalAccessor) {
            return "'" + String.valueOf(value) + "'";
        }
        if (value instanceof Collection) {
            Collection<?> collection = (Collection<?>) value;
            if (collection.isEmpty()) {
                return "NULL";
            }
            List<String> parts = new ArrayList<>(collection.size());
            for (Object item : collection) {
                parts.add(renderValue(item));
            }
            return String.join(", ", parts);
        }
        if (value.getClass().isArray()) {
            int len = Array.getLength(value);
            if (len == 0) {
                return "NULL";
            }
            List<String> parts = new ArrayList<>(len);
            for (int i = 0; i < len; i++) {
                parts.add(renderValue(Array.get(value, i)));
            }
            return String.join(", ", parts);
        }
        return "'" + escapeSqlLiteral(String.valueOf(value)) + "'";
    }

    private String escapeSqlLiteral(String raw) {
        if (raw == null) {
            return null;
        }
        return raw.replace("'", "''");
    }

    /**
     * 压缩路由键用于日志输出。
     */
    private String compactRouteKey(String routeKey) {
        if (routeKey == null || routeKey.trim().isEmpty()) {
            return "na";
        }
        int opSep = routeKey.indexOf('|');
        if (opSep < 0) {
            return simpleClassName(routeKey);
        }
        String entitiesPart = routeKey.substring(0, opSep);
        String suffixPart = routeKey.substring(opSep);
        String[] entities = entitiesPart.split(">");
        StringBuilder compact = new StringBuilder();
        for (int i = 0; i < entities.length; i++) {
            if (i > 0) {
                compact.append('>');
            }
            compact.append(simpleClassName(entities[i]));
        }
        compact.append(suffixPart);
        return compact.toString();
    }

    private String simpleClassName(String token) {
        String normalized = token == null ? "" : token.trim();
        int idx = normalized.lastIndexOf('.');
        return idx >= 0 ? normalized.substring(idx + 1) : normalized;
    }

    private String digest(String sql) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String hex = toHex(md.digest(sql.getBytes(StandardCharsets.UTF_8)));
            return hex.substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            return "digest_na";
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private long positiveHash(String value) {
        String digest = digest(value == null ? "" : value);
        return Long.parseUnsignedLong(digest, 16);
    }

    public enum Output {
        TEMPLATE,
        EXEC,
        BOTH
    }

    private static final class LogMessage {
        /** 模板内容。 */
        private final String template;
        /** 参数列表。 */
        private final Object[] args;

        private LogMessage(String template, Object... args) {
            this.template = template;
            this.args = args;
        }
    }
}
