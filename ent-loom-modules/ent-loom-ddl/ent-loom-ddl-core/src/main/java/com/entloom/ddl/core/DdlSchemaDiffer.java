package com.entloom.ddl.core;

import com.entloom.ddl.api.DdlColumnMetadata;
import com.entloom.ddl.api.DdlEntityMetadata;
import com.entloom.ddl.api.DdlFieldMetadata;
import com.entloom.ddl.api.DdlIndexMetadata;
import com.entloom.ddl.api.DdlTableSnapshot;
import com.entloom.ddl.enums.GenerationStrategy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MySQL 表结构差异计算器。
 *
 * <p>E3 只自动处理新增字段、新增索引、表注释和有限字段修改。删除、主键变化、
 * 已有索引重建以及不兼容类型变化都会形成中文拒绝原因。</p>
 */
public final class DdlSchemaDiffer {
    private static final Pattern SIZED_TYPE = Pattern.compile("([a-z]+)\\((\\d+)(?:,(\\d+))?\\)");
    private static final Set<String> SAFE_WIDENING = new HashSet<String>(Arrays.asList(
            "tinyint:int", "tinyint:bigint", "int:bigint", "float:double"));

    private final MysqlTypeMapper typeMapper;

    public DdlSchemaDiffer() {
        this(new MysqlTypeMapper());
    }

    public DdlSchemaDiffer(MysqlTypeMapper typeMapper) {
        this.typeMapper = typeMapper == null ? new MysqlTypeMapper() : typeMapper;
    }

    public DdlSchemaDiff diff(DdlEntityMetadata entity, DdlTableSnapshot current) {
        if (entity == null) {
            throw new IllegalArgumentException("entity must not be null");
        }
        if (current == null) {
            throw new IllegalArgumentException("current table snapshot must not be null");
        }
        if (!current.exists()) {
            return new DdlSchemaDiff(false,
                    Collections.<DdlFieldMetadata>emptyList(),
                    Collections.<DdlFieldChange>emptyList(),
                    Collections.<DdlIndexMetadata>emptyList(),
                    Collections.<String>emptyList());
        }

        List<String> errors = new ArrayList<String>();
        if (!entity.tableName().equals(current.tableName())) {
            errors.add("当前表名与实体表名不一致: " + current.tableName());
        }

        Map<String, DdlColumnMetadata> currentColumns = new LinkedHashMap<String, DdlColumnMetadata>();
        for (DdlColumnMetadata column : current.columns()) {
            currentColumns.put(identifierKey(column.columnName()), column);
        }
        Map<String, DdlFieldMetadata> desiredColumns = new LinkedHashMap<String, DdlFieldMetadata>();
        for (DdlFieldMetadata field : entity.fields()) {
            if (field.persisted()) {
                desiredColumns.put(identifierKey(field.columnName()), field);
            }
        }

        List<DdlFieldMetadata> addedFields = new ArrayList<DdlFieldMetadata>();
        List<DdlFieldChange> changedFields = new ArrayList<DdlFieldChange>();
        Set<String> consumedCurrentColumns = new HashSet<String>();
        for (DdlFieldMetadata desired : desiredColumns.values()) {
            DdlColumnMetadata existing = currentColumns.get(identifierKey(desired.columnName()));
            String existingName = desired.columnName();
            if (existing == null && !desired.renameFrom().isEmpty()) {
                existing = currentColumns.get(identifierKey(desired.renameFrom()));
                if (existing != null) {
                    existingName = existing.columnName();
                }
            }
            if (existing == null) {
                addedFields.add(desired);
                continue;
            }
            consumedCurrentColumns.add(identifierKey(existingName));
            compareField(existingName, existing, desired, changedFields, errors);
        }

        for (DdlColumnMetadata currentColumn : currentColumns.values()) {
            String currentKey = identifierKey(currentColumn.columnName());
            if (!consumedCurrentColumns.contains(currentKey)
                    && !desiredColumns.containsKey(currentKey)) {
                errors.add("删除字段暂不支持，发现数据库多余字段: " + currentColumn.columnName());
            }
        }

        List<String> desiredPrimaryKeys = desiredPrimaryKeys(entity);
        if (!sameIdentifiers(desiredPrimaryKeys, current.primaryKeyColumns())) {
            errors.add("主键变化暂不支持自动迁移: 当前=" + current.primaryKeyColumns()
                    + ", 目标=" + desiredPrimaryKeys);
        }

        boolean commentChanged = !normalizeText(entity.comment()).equals(normalizeText(current.comment()));
        List<DdlIndexMetadata> addedIndexes = compareIndexes(entity, current, errors);
        return new DdlSchemaDiff(commentChanged, addedFields, changedFields, addedIndexes, errors);
    }

    private void compareField(String existingName,
                              DdlColumnMetadata existing,
                              DdlFieldMetadata desired,
                              List<DdlFieldChange> changedFields,
                              List<String> errors) {
        String desiredType = desired.columnDefinition().isEmpty()
                ? typeMapper.toSqlType(desired)
                : desired.columnDefinition();
        String currentType = normalizeType(existing.sqlType());
        String normalizedDesiredType = normalizeType(desiredType);
        if (!currentType.equals(normalizedDesiredType)
                && !isCompatibleWidening(currentType, normalizedDesiredType)) {
            errors.add("字段类型变化不兼容，拒绝修改字段 " + existingName
                    + ": 当前=" + existing.sqlType() + ", 目标=" + desiredType);
        }
        if (existing.nullable() && !desired.nullable()) {
            errors.add("字段由可空改为非空可能造成数据丢失，拒绝修改字段: " + desired.columnName());
        }
        boolean autoIncrementAdded = desired.generationStrategy() == GenerationStrategy.AUTO_INCREMENT
                && !existing.autoIncrement();
        boolean changed = !currentType.equals(normalizedDesiredType)
                || existing.nullable() != desired.nullable()
                || !normalizeDefault(existing.defaultValue()).equals(normalizeDefault(desired.defaultValue()))
                || !normalizeText(existing.comment()).equals(normalizeText(desired.comment()))
                || autoIncrementAdded
                || !existingName.equals(desired.columnName());
        if (changed && !containsFieldError(errors, desired.columnName())) {
            changedFields.add(new DdlFieldChange(existingName, desired, existing.autoIncrement()));
        }
    }

    private static boolean containsFieldError(List<String> errors, String columnName) {
        for (String error : errors) {
            if (error.contains(columnName)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> desiredPrimaryKeys(DdlEntityMetadata entity) {
        List<String> primaryKeys = new ArrayList<String>();
        for (DdlFieldMetadata field : entity.fields()) {
            if (field.persisted() && field.primaryKey()) {
                primaryKeys.add(field.columnName());
            }
        }
        return primaryKeys;
    }

    private static List<DdlIndexMetadata> compareIndexes(DdlEntityMetadata entity,
                                                          DdlTableSnapshot current,
                                                          List<String> errors) {
        Map<String, DdlIndexMetadata> desired = new LinkedHashMap<String, DdlIndexMetadata>();
        for (DdlFieldMetadata field : entity.fields()) {
            if (field.persisted() && field.unique()) {
                DdlIndexMetadata index = new DdlIndexMetadata(
                        MysqlCreateTableSqlBuilder.fieldUniqueIndexName(entity.tableName(), field.columnName()),
                        Collections.singletonList(field.columnName()), true, "");
                putIndex(desired, index, errors);
            }
        }
        for (DdlIndexMetadata index : entity.indexes()) {
            putIndex(desired, index, errors);
        }

        Map<String, DdlIndexMetadata> existing = new LinkedHashMap<String, DdlIndexMetadata>();
        for (DdlIndexMetadata index : current.indexes()) {
            existing.put(identifierKey(MysqlCreateTableSqlBuilder.resolvedIndexName(index)), index);
        }

        List<DdlIndexMetadata> added = new ArrayList<DdlIndexMetadata>();
        for (Map.Entry<String, DdlIndexMetadata> entry : desired.entrySet()) {
            DdlIndexMetadata currentIndex = existing.get(entry.getKey());
            if (currentIndex == null) {
                added.add(entry.getValue());
            } else if (!sameIndex(currentIndex, entry.getValue())) {
                errors.add("已有索引定义变化暂不支持重建，拒绝修改索引: " + entry.getKey());
            }
        }
        for (String existingName : existing.keySet()) {
            if (!desired.containsKey(existingName)) {
                errors.add("删除索引暂不支持，发现数据库多余索引: " + existing.get(existingName).name());
            }
        }
        return added;
    }

    private static void putIndex(Map<String, DdlIndexMetadata> indexes,
                                 DdlIndexMetadata index,
                                 List<String> errors) {
        String name = identifierKey(MysqlCreateTableSqlBuilder.resolvedIndexName(index));
        DdlIndexMetadata previous = indexes.put(name, index);
        if (previous != null && !sameIndex(previous, index)) {
            errors.add("目标索引名称对应多个不同定义: " + name);
        }
    }

    private static boolean sameIndex(DdlIndexMetadata left, DdlIndexMetadata right) {
        return left.unique() == right.unique()
                && sameIdentifiers(left.fields(), right.fields())
                && normalizeText(left.expression()).equals(normalizeText(right.expression()));
    }

    private static boolean sameIdentifiers(List<String> left, List<String> right) {
        if (left.size() != right.size()) {
            return false;
        }
        for (int i = 0; i < left.size(); i++) {
            if (!identifierKey(left.get(i)).equals(identifierKey(right.get(i)))) {
                return false;
            }
        }
        return true;
    }

    private static String identifierKey(String value) {
        return normalizeText(value).toLowerCase(Locale.ROOT);
    }

    private static boolean isCompatibleWidening(String current, String desired) {
        Matcher currentMatch = SIZED_TYPE.matcher(current);
        Matcher desiredMatch = SIZED_TYPE.matcher(desired);
        if (currentMatch.matches() && desiredMatch.matches()) {
            String currentBase = currentMatch.group(1);
            String desiredBase = desiredMatch.group(1);
            if (!currentBase.equals(desiredBase)) {
                return SAFE_WIDENING.contains(currentBase + ":" + desiredBase);
            }
            int currentSize = Integer.parseInt(currentMatch.group(2));
            int desiredSize = Integer.parseInt(desiredMatch.group(2));
            if ("decimal".equals(currentBase)) {
                int currentScale = currentMatch.group(3) == null ? 0 : Integer.parseInt(currentMatch.group(3));
                int desiredScale = desiredMatch.group(3) == null ? 0 : Integer.parseInt(desiredMatch.group(3));
                int currentIntegerDigits = currentSize - currentScale;
                int desiredIntegerDigits = desiredSize - desiredScale;
                return desiredScale >= currentScale && desiredIntegerDigits >= currentIntegerDigits;
            }
            return desiredSize >= currentSize;
        }
        return SAFE_WIDENING.contains(current + ":" + desired);
    }

    private static String normalizeType(String value) {
        return normalizeText(value)
                .replace("character varying", "varchar")
                .replace("integer", "int")
                .replace("numeric", "decimal")
                .replaceAll("\\s+", "");
    }

    private static String normalizeDefault(String value) {
        String normalized = normalizeText(value);
        if (normalized.length() >= 2 && normalized.startsWith("'") && normalized.endsWith("'")) {
            normalized = normalized.substring(1, normalized.length() - 1).replace("''", "'");
        }
        return normalized.toUpperCase();
    }

    private static String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }
}
