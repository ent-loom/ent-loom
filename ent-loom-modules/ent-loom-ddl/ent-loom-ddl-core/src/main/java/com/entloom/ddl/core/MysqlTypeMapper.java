package com.entloom.ddl.core;

import com.entloom.ddl.api.DdlFieldMetadata;
import com.entloom.ddl.api.DdlCharacterSetMetadata;
import com.entloom.ddl.api.DdlColumnMetadata;
import com.entloom.ddl.api.DdlEntityMetadata;
import com.entloom.ddl.api.DdlIndexMetadata;
import com.entloom.ddl.api.DdlTableSnapshot;
import com.entloom.ddl.enums.SqlType;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Instant;
import java.util.Date;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * MySQL 统一类型解析、文本容量校验与单表目标类型归一。
 */
public final class MysqlTypeMapper {
    private static final Pattern TEXT_DEFINITION = Pattern.compile(
            "(?i)^(varchar|char)\\s*\\(\\s*(\\d+)\\s*\\)(?:\\s+character\\s+set\\s+([a-z0-9_]+))?(?:\\s+collate\\s+([a-z0-9_]+))?$"
            + "|^(text|mediumtext|longtext)(?:\\s+character\\s+set\\s+([a-z0-9_]+))?(?:\\s+collate\\s+([a-z0-9_]+))?$");
    private static final Pattern CHARACTER_HEAD = Pattern.compile("(?i)^(varchar|char)\\s*\\(\\s*(\\d+)\\s*\\)");
    private static final Pattern KNOWN_NON_TEXT = Pattern.compile(
            "(?i)^(?:tinyint|smallint|mediumint|int|integer|bigint|decimal|numeric|float|double|boolean|date|datetime|timestamp|time|json|blob|binary|varbinary)\\b.*");

    public String toSqlType(DdlFieldMetadata field) {
        return resolve(field, DdlCharacterSetMetadata.unknown()).sqlType();
    }

    /** 无数据库构建时由调用方显式提供目标列的字符集事实。 */
    public MysqlColumnType resolve(DdlFieldMetadata field, DdlCharacterSetMetadata characterSet) {
        Objects.requireNonNull(field, "field must not be null");
        Objects.requireNonNull(characterSet, "characterSet must not be null");
        SqlType hint = field.sqlType();
        boolean explicit = hint != SqlType.AUTO;
        if (explicit && (field.javaType() != String.class || !(hint == SqlType.VARCHAR || hint == SqlType.CHAR
                || hint == SqlType.TEXT || hint == SqlType.MEDIUMTEXT || hint == SqlType.LONGTEXT))) {
            throw error(field, characterSet, "未支持的 sqlType=" + hint + " 与 Java 类型组合 " + field.javaType().getTypeName());
        }
        String raw = field.columnDefinition();
        if (raw.matches("(?is)^(?:varchar|char|text|mediumtext|longtext)\\b.*")
                && raw.matches("(?is).*\\b(?:null|default|comment)\\b.*")) {
            throw error(field, characterSet, "columnDefinition 仅支持物理类型及修饰，NULL/DEFAULT/COMMENT 请使用独立属性");
        }
        if (field.javaType() != String.class) {
            boolean javaTextual = field.javaType() == Character.class || field.javaType() == char.class
                    || Enum.class.isAssignableFrom(field.javaType());
            if (!raw.isEmpty() && TEXT_DEFINITION.matcher(raw).matches() && !javaTextual) {
                throw error(field, characterSet, "已知文本物理定义与 Java 类型不兼容: " + raw);
            }
            String target = raw.isEmpty() ? mapJavaType(field) : raw;
            boolean textual = TEXT_DEFINITION.matcher(target).matches();
            if (!raw.isEmpty() && target.matches("(?is).*\\b(?:character\\s+set|collate)\\b.*") && characterSet.known()) {
                throw error(field, characterSet, "非 String 原始定义的字符集修饰尚未支持，不能保证保留已有属性");
            }
            return new MysqlColumnType(target, characterSet, textual,
                    textual && target.matches("(?i)^(?:text|mediumtext|longtext)\\b.*"),
                    raw.isEmpty() ? "" : "原始 columnDefinition 仅有限校验: " + field.fieldName());
        }
        int physical = physicalLength(field);
        if (field.length() == 0 || physical == 0 || field.precision() >= 0 || field.scale() >= 0) {
            throw error(field, characterSet, "文本长度必须为正整数，且不适用 precision/scale");
        }
        if ((hint == SqlType.TEXT || hint == SqlType.MEDIUMTEXT || hint == SqlType.LONGTEXT) && physical >= 0) {
            throw error(field, characterSet, "TEXT 家族不接受物理 length=" + physical);
        }
        String target = raw;
        if (target.isEmpty()) {
            if (hint == SqlType.TEXT || hint == SqlType.MEDIUMTEXT || hint == SqlType.LONGTEXT) {
                target = hint.name().toLowerCase(Locale.ROOT);
            } else if (hint == SqlType.AUTO && physical < 0 && field.textCapacity().businessMaxLength() > 0) {
                requireCharacterSet(field, characterSet);
                int characters = field.textCapacity().businessMaxLength();
                long bytes = (long) characters * characterSet.maxBytesPerCharacter();
                if (characters <= 65535 && bytes + 2L <= 65535L) {
                    target = "varchar(" + characters + ")";
                } else if (bytes <= 65535L) {
                    target = "text";
                } else if (bytes <= 16777215L) {
                    target = "mediumtext";
                } else if (bytes <= 4294967295L) {
                    target = "longtext";
                } else {
                    throw error(field, characterSet, "业务文本容量超出 LONGTEXT，所需字节=" + bytes);
                }
            } else {
                int length = physical > 0 ? physical : field.textCapacity().businessMaxLength() > 0
                        ? field.textCapacity().businessMaxLength() : field.length() > 0 ? field.length()
                        : hint == SqlType.CHAR ? 1 : 200;
                target = (hint == SqlType.CHAR ? "char(" : "varchar(") + length + ")";
            }
        }
        Matcher match = TEXT_DEFINITION.matcher(target);
        if (!match.matches()) {
            if (KNOWN_NON_TEXT.matcher(target).matches()) {
                throw error(field, characterSet, "已知物理类型与 String 不兼容: " + target);
            }
            if (explicit && target.matches("(?i)^(?:varchar|char|text|mediumtext|longtext)\\b.*")
                    && !target.toLowerCase(Locale.ROOT).startsWith(hint.name().toLowerCase(Locale.ROOT))) {
                throw error(field, characterSet, "columnDefinition 与 sqlType 冲突: " + target);
            }
            // 不解析任意 SQL 片段，不将未知定义报告为容量验证通过。
            Matcher head = CHARACTER_HEAD.matcher(target);
            if (head.find()) {
                validateKnownText(field, characterSet, head.group(1).toLowerCase(Locale.ROOT),
                        parseLength(field, characterSet, head.group(2)), physical, target, false);
            } else if (target.matches("(?i)^(?:varchar|char)\\b.*")) {
                throw error(field, characterSet, "非法文本物理长度定义: " + target);
            } else if (target.matches("(?i)^(?:text|mediumtext|longtext)\\b.*")) {
                if (target.matches("(?i)^(?:text|mediumtext|longtext)\\s*\\(.*")) {
                    throw error(field, characterSet, "TEXT 家族不接受长度参数: " + target);
                }
                validateKnownText(field, characterSet, target.split("\\s+", 2)[0].toLowerCase(Locale.ROOT), -1, physical, target, false);
            }
            return new MysqlColumnType(target, characterSet, false,
                    target.matches("(?i)^(?:tinytext|text|mediumtext|longtext)\\b.*"),
                    "字段 " + field.fieldName() + " 的原始 columnDefinition 仅有限校验，未确认业务容量及读写兼容性: " + target);
        }
        String family = (match.group(1) == null ? match.group(5) : match.group(1)).toLowerCase(Locale.ROOT);
        boolean text = family.endsWith("text");
        String declaredCharset = match.group(1) == null ? match.group(6) : match.group(3);
        String declaredCollation = match.group(1) == null ? match.group(7) : match.group(4);
        if (declaredCharset != null || declaredCollation != null) {
            requireCharacterSet(field, characterSet);
            if ((declaredCharset != null && !declaredCharset.equalsIgnoreCase(characterSet.characterSet()))
                    || (declaredCollation != null && !declaredCollation.equalsIgnoreCase(characterSet.collation()))) {
                throw error(field, characterSet, "columnDefinition 字符集/排序规则与有效上下文冲突，不支持自动转换");
            }
        }
        long length = text ? -1 : parseLength(field, characterSet, match.group(2));
        validateKnownText(field, characterSet, family, length, physical, target, true);
        String normalized = text ? family : family + "(" + length + ")";
        return new MysqlColumnType(normalized, characterSet, true, text, "");
    }

    private static long parseLength(DdlFieldMetadata field, DdlCharacterSetMetadata characterSet, String length) {
        try { return Long.parseLong(length); }
        catch (NumberFormatException exception) { throw error(field, characterSet, "物理文本长度超出合法范围: " + length); }
    }

    private static void validateKnownText(DdlFieldMetadata field, DdlCharacterSetMetadata characterSet,
                                           String family, long length, int physical, String target, boolean checkBytes) {
        boolean text = family.endsWith("text");
        boolean explicit = field.sqlType() != SqlType.AUTO;
        if (explicit && !family.equalsIgnoreCase(field.sqlType().name())) {
            throw error(field, characterSet, "columnDefinition=" + field.columnDefinition() + " 与 sqlType=" + field.sqlType() + " 冲突");
        }
        if (!field.columnDefinition().isEmpty() && physical >= 0 && (text || physical != length)) {
            throw error(field, characterSet, "columnDefinition=" + field.columnDefinition() + " 与物理 length=" + physical + " 冲突");
        }
        if (!text && (length <= 0 || length > (family.equals("char") ? 255 : 65535))) {
            throw error(field, characterSet, "非法 " + family + " 字符长度=" + length);
        }
        int business = field.textCapacity().businessMaxLength();
        if (!text && business > length) {
            throw error(field, characterSet, "文本容量不足，目标=" + target + "，业务最大字符数=" + business);
        }
        if (checkBytes && ((!text && family.equals("varchar") && (field.sqlType() != SqlType.AUTO || !field.columnDefinition().isEmpty() || business > 0)) || (text && business > 0))) {
            requireCharacterSet(field, characterSet);
        }
        if (checkBytes && !text && family.equals("varchar") && characterSet.known()
                && length * characterSet.maxBytesPerCharacter() + 2L > 65535L) {
            throw error(field, characterSet, "VARCHAR 单列字节容量超限，目标=" + target);
        }
        if (checkBytes && text && business > 0 && (long) business * characterSet.maxBytesPerCharacter()
                > (family.equals("text") ? 65535L : family.equals("mediumtext") ? 16777215L : 4294967295L)) {
            throw error(field, characterSet, "文本字节容量不足，目标=" + target + "，业务最大字符数=" + business);
        }
        if (text && !field.defaultValue().isEmpty() && !field.defaultValue().equalsIgnoreCase("NULL")) {
            throw error(field, characterSet, "TEXT 家族的非 NULL 默认值需要表达式合同，首期不支持");
        }
    }

    private static int physicalLength(DdlFieldMetadata field) {
        return field.textCapacity().physicalLength() >= 0 ? field.textCapacity().physicalLength()
                : field.textCapacity().businessMaxLength() < 0 ? field.length() : -1;
    }

    private static void requireCharacterSet(DdlFieldMetadata field, DdlCharacterSetMetadata characterSet) {
        if (!characterSet.known()) {
            throw error(field, characterSet, "缺少有效字符集上下文，无法完成容量校验；请读取目标库/表/列或显式传入上下文");
        }
    }

    private static IllegalArgumentException error(DdlFieldMetadata field, DdlCharacterSetMetadata characterSet, String reason) {
        return new IllegalArgumentException("字段 " + field.fieldName() + ": " + reason
                + "；业务来源=" + field.textCapacity().businessSource() + "(" + field.textCapacity().businessMaxLength() + ")"
                + "；物理来源=" + field.textCapacity().physicalSource() + "(length=" + field.length()
                + ", sqlType=" + field.sqlType() + ", columnDefinition=" + field.columnDefinition() + ")"
                + "；有效字符集=" + (characterSet.known() ? characterSet.characterSet() : "未知"));
    }

    public boolean needsCharacterSet(DdlEntityMetadata entity) {
        for (DdlFieldMetadata field : entity.fields()) {
            if (field.persisted() && field.javaType() == String.class && (field.sqlType() != SqlType.AUTO
                    || field.textCapacity().businessMaxLength() > 0 || !field.columnDefinition().isEmpty())) { return true; }
        }
        return false;
    }

    public MysqlTableResolution resolveTable(DdlEntityMetadata entity, DdlTableSnapshot current,
                                             DdlCharacterSetMetadata defaultCharacterSet) {
        if (entity == null) { throw new IllegalArgumentException("entity must not be null"); }
        Objects.requireNonNull(defaultCharacterSet, "defaultCharacterSet must not be null");
        Map<String, MysqlColumnType> columns = new LinkedHashMap<String, MysqlColumnType>();
        List<String> warnings = new ArrayList<String>();
        for (DdlFieldMetadata field : entity.fields()) {
            if (!field.persisted()) { continue; }
            DdlColumnMetadata existing = null;
            if (current != null && current.exists()) {
                for (DdlColumnMetadata candidate : current.columns()) {
                    if (candidate.columnName().equalsIgnoreCase(field.columnName())) { existing = candidate; break; }
                }
                if (existing == null && !field.renameFrom().isEmpty()) {
                    for (DdlColumnMetadata candidate : current.columns()) {
                        if (candidate.columnName().equalsIgnoreCase(field.renameFrom())) { existing = candidate; break; }
                    }
                }
            }
            DdlCharacterSetMetadata effective = existing != null && (existing.characterSet().known()
                    || existing.sqlType().matches("(?i)^(?:varchar|char|tinytext|text|mediumtext|longtext)\\b.*"))
                    ? existing.characterSet() : defaultCharacterSet;
            MysqlColumnType type = resolve(field, effective);
            if (existing != null && existing.characterSet().known() && field.javaType() == String.class
                    && !type.warning().isEmpty()) {
                throw error(field, effective, "未知原始定义不能可靠保留原列字符集/排序规则，请使用可识别文本定义");
            }
            columns.put(field.fieldName(), type);
            if (!type.warning().isEmpty()) { warnings.add(type.warning()); }
            if (type.textFamily()) {
                if (field.primaryKey() || field.unique()) {
                    throw error(field, effective, "TEXT 家族不支持当前主键/唯一约束，不能自动删改约束");
                }
                for (DdlIndexMetadata index : entity.indexes()) {
                    for (String column : index.fields()) {
                        if (column.equalsIgnoreCase(field.columnName())) {
                            throw error(field, effective, "TEXT 家族与列索引冲突: " + index.name());
                        }
                    }
                    if (!index.expression().isEmpty()) {
                        warnings.add("字段 " + field.fieldName() + " 为 TEXT 家族；表达式索引 " + index.name() + " 交由 MySQL 验证");
                    }
                }
            }
        }
        return new MysqlTableResolution(columns, defaultCharacterSet, warnings);
    }

    private String mapJavaType(DdlFieldMetadata field) {
        Class<?> type = field.javaType();

        if (type == String.class) {
            int length = field.length() > 0 ? field.length() : 200;
            return "varchar(" + length + ")";
        }
        if (type == Character.class || type == char.class) {
            return "char(1)";
        }
        if (type == Long.class || type == Long.TYPE) {
            return "bigint";
        }
        if (type == Integer.class || type == Integer.TYPE
                || type == Short.class || type == Short.TYPE
                || type == Byte.class || type == Byte.TYPE) {
            return "int";
        }
        if (type == Boolean.class || type == Boolean.TYPE) {
            return "tinyint(1)";
        }
        if (type == BigDecimal.class || type == BigInteger.class) {
            int precision = field.precision() > 0 ? field.precision() : (field.length() > 0 ? field.length() : 20);
            int scale = field.scale() >= 0 ? field.scale() : 6;
            return "decimal(" + precision + "," + scale + ")";
        }
        if (type == Double.class || type == Double.TYPE) {
            return "double";
        }
        if (type == Float.class || type == Float.TYPE) {
            return "float";
        }
        if (type == LocalDateTime.class || type == Instant.class || type == Date.class
            || "java.sql.Timestamp".equals(type.getName())) {
            return "datetime";
        }
        if (type == LocalDate.class || "java.sql.Date".equals(type.getName())) {
            return "date";
        }
        if (type == LocalTime.class || "java.sql.Time".equals(type.getName())) {
            return "time";
        }
        if (type == byte[].class) {
            return "blob";
        }
        if (Enum.class.isAssignableFrom(type)) {
            return "varchar(64)";
        }
        throw new IllegalArgumentException("不支持的数据库列类型: " + field.fieldName() + " (" + type.getTypeName() + ")");
    }
}
