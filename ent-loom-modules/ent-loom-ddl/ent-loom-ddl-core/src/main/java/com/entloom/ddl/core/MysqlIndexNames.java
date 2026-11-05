package com.entloom.ddl.core;

import com.entloom.ddl.api.DdlEntityMetadata;
import com.entloom.ddl.api.DdlFieldMetadata;
import com.entloom.ddl.api.DdlIndexMetadata;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** MySQL 索引统一命名：物理表列优先，超长名称保留前缀并附加稳定摘要。 */
final class MysqlIndexNames {
    private MysqlIndexNames() { }

    static String resolve(String tableName, DdlIndexMetadata index) {
        if (!index.name().isEmpty()) {
            if (index.name().codePointCount(0, index.name().length()) > 64) {
                throw new IllegalArgumentException("显式索引名不能超过 64 个字符: " + index.name());
            }
            return index.name();
        }
        StringBuilder identity = new StringBuilder();
        appendPart(identity, tableName);
        appendPart(identity, Boolean.toString(index.unique()));
        for (String column : index.fields()) {
            appendPart(identity, column);
        }
        appendPart(identity, index.expression());
        String name = (index.unique() ? "uk_" : "idx_") + MysqlCreateTableSqlBuilder.normalize(tableName);
        for (String column : index.fields()) {
            name += "_" + MysqlCreateTableSqlBuilder.normalize(column);
        }
        if (!index.expression().isEmpty()) {
            name += "_expr_" + digest(identity.toString());
        }
        return name.length() <= 64 ? name : name.substring(0, 55) + "_" + digest(identity.toString());
    }

    static void validate(DdlEntityMetadata entity) {
        Set<String> names = new HashSet<String>();
        // PRIMARY 为 MySQL 主键保留名，普通索引不能占用。
        names.add("primary");
        for (DdlFieldMetadata field : entity.fields()) {
            if (field.persisted() && field.unique()) {
                register(names, resolve(entity.tableName(), new DdlIndexMetadata("",
                        Collections.singletonList(field.columnName()), true, "")));
            }
        }
        for (DdlIndexMetadata index : entity.indexes()) {
            register(names, resolve(entity.tableName(), index));
        }
    }

    private static void register(Set<String> names, String name) {
        if (!names.add(name.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("同一张表的索引名称冲突: " + name);
        }
    }

    private static void appendPart(StringBuilder target, String value) {
        target.append(value.length()).append(':').append(value);
    }

    private static String digest(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(8);
            for (int i = 0; i < 4; i++) {
                result.append(Character.forDigit((bytes[i] >>> 4) & 15, 16));
                result.append(Character.forDigit(bytes[i] & 15, 16));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境不支持 SHA-256", exception);
        }
    }
}
