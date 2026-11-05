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
 * <p>只自动处理新增字段、新增索引、表注释和有限字段修改。兼容的数据库多余列
 * 保留并告警；阻碍插入的多余列、主键变化、已有索引重建及不兼容类型变化会被拒绝。
 * 显式全量同步模式允许这些差异进入执行计划。</p>
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
        return diff(entity, current, false);
    }

    public DdlSchemaDiff diff(DdlEntityMetadata entity, DdlTableSnapshot current, boolean synchronizeAll) {
        if (entity == null || current == null) { throw new IllegalArgumentException("实体与表快照不能为空"); }
        return diff(entity, current, synchronizeAll,
                typeMapper.resolveTable(entity, current, current.characterSet()));
    }

    public DdlSchemaDiff diff(DdlEntityMetadata entity, DdlTableSnapshot current, boolean synchronizeAll,
                       MysqlTableResolution resolution) {
        if (entity == null) {
            throw new IllegalArgumentException("entity must not be null");
        }
        if (current == null) {
            throw new IllegalArgumentException("current table snapshot must not be null");
        }
        MysqlIndexNames.validate(entity);
        if (!current.exists()) {
            return new DdlSchemaDiff(false,
                    Collections.<DdlFieldMetadata>emptyList(),
                    Collections.<DdlFieldChange>emptyList(),
                    Collections.<DdlIndexMetadata>emptyList(),
                    Collections.<String>emptyList());
        }

        List<String> errors = new ArrayList<String>();
        List<String> warnings = new ArrayList<String>(resolution.warnings());
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
        List<String> removedColumns = new ArrayList<String>();
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
            if (!consumedCurrentColumns.add(identifierKey(existingName))) {
                errors.add("数据库列被多个实体字段重复映射: " + existingName);
                continue;
            }
            compareField(existingName, existing, desired, changedFields, errors, synchronizeAll, resolution.column(desired));
        }

        for (DdlColumnMetadata currentColumn : currentColumns.values()) {
            String currentKey = identifierKey(currentColumn.columnName());
            if (!consumedCurrentColumns.contains(currentKey)
                    && !desiredColumns.containsKey(currentKey)) {
                if (synchronizeAll) {
                    removedColumns.add(currentColumn.columnName());
                } else if (currentColumn.nullable() || currentColumn.hasDefaultValue()
                        || currentColumn.autoIncrement() || currentColumn.generated()) {
                    warnings.add("数据库多余字段已保留，不执行删除: " + currentColumn.columnName());
                } else {
                    errors.add("数据库多余字段会影响插入: " + currentColumn.columnName()
                            + "（NOT NULL、无默认值且非数据库自动生成列）"
                            + "；实体插入不会提供该字段，请通过显式迁移调整列约束或清理旧列");
                }
            }
        }

        List<String> desiredPrimaryKeys = desiredPrimaryKeys(entity);
        boolean primaryKeyChanged = !sameIdentifiers(desiredPrimaryKeys, current.primaryKeyColumns());
        if (primaryKeyChanged && !synchronizeAll) {
            errors.add("主键变化暂不支持自动迁移: 当前=" + current.primaryKeyColumns()
                    + ", 目标=" + desiredPrimaryKeys);
        }

        boolean commentChanged = !normalizeText(entity.comment()).equals(normalizeText(current.comment()));
        List<String> removedIndexes = new ArrayList<String>();
        List<DdlIndexMetadata> addedIndexes = compareIndexes(entity, current, errors, removedIndexes, synchronizeAll);
        return new DdlSchemaDiff(commentChanged, addedFields, changedFields, addedIndexes, errors, warnings,
                removedColumns, removedIndexes, synchronizeAll && primaryKeyChanged);
    }

    private void compareField(String existingName,
                              DdlColumnMetadata existing,
                              DdlFieldMetadata desired,
                              List<DdlFieldChange> changedFields,
                              List<String> errors,
                              boolean synchronizeAll, MysqlColumnType resolvedType) {
        String desiredType = resolvedType.sqlType();
        String currentType = normalizeType(existing.sqlType());
        String normalizedDesiredType = normalizeType(desiredType);
        int initialErrorCount = errors.size();
        if (!synchronizeAll && !currentType.equals(normalizedDesiredType)
                && !isCompatibleWidening(currentType, normalizedDesiredType)) {
            errors.add("字段类型变化不兼容，拒绝修改字段 " + existingName
                    + "\n    数据库实际类型: " + existing.sqlType()
                    + "\n    实体声明类型:   " + desiredType
                    + "\n    拒绝原因: 此类型转换不在自动迁移支持范围内"
                    + "\n    处理建议: 核对实体声明与数据库结构；确认需要转换时，使用版本化迁移");
        }
        if (!synchronizeAll && existing.nullable() && !desired.nullable()) {
            errors.add("字段由可空改为非空可能造成数据丢失，拒绝修改字段: " + desired.columnName());
        }
        boolean autoIncrementRemoved = existing.autoIncrement() && desired.generationStrategy() == GenerationStrategy.NONE;
        if (!synchronizeAll && autoIncrementRemoved) {
            errors.add("取消数据库自增暂不支持自动迁移，请使用版本化迁移: " + desired.columnName());
        }
        boolean autoIncrementAdded = (desired.generationStrategy() == GenerationStrategy.AUTO_INCREMENT
                || desired.generationStrategy() == GenerationStrategy.IDENTITY)
                && !existing.autoIncrement();
        boolean changed = !currentType.equals(normalizedDesiredType)
                || existing.nullable() != desired.nullable()
                || !normalizeDefault(existing.defaultValue()).equals(normalizeDefault(desired.defaultValue()))
                || !normalizeText(existing.comment()).equals(normalizeText(desired.comment()))
                || autoIncrementAdded
                || autoIncrementRemoved
                || !existingName.equals(desired.columnName());
        if (changed && errors.size() == initialErrorCount) {
            changedFields.add(new DdlFieldChange(existingName, desired, existing.autoIncrement()));
        }
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
                                                          List<String> errors,
                                                          List<String> removedIndexes,
                                                          boolean synchronizeAll) {
        Map<String, DdlIndexMetadata> desired = new LinkedHashMap<String, DdlIndexMetadata>();
        for (DdlFieldMetadata field : entity.fields()) {
            if (field.persisted() && field.unique()) {
                DdlIndexMetadata index = new DdlIndexMetadata(
                        MysqlCreateTableSqlBuilder.fieldUniqueIndexName(entity.tableName(), field.columnName()),
                        Collections.singletonList(field.columnName()), true, "");
                putIndex(entity.tableName(), desired, index, errors);
            }
        }
        for (DdlIndexMetadata index : entity.indexes()) {
            putIndex(entity.tableName(), desired, index, errors);
        }

        Map<String, DdlIndexMetadata> existing = new LinkedHashMap<String, DdlIndexMetadata>();
        for (DdlIndexMetadata index : current.indexes()) {
            existing.put(identifierKey(MysqlIndexNames.resolve(entity.tableName(), index)), index);
        }

        List<DdlIndexMetadata> added = new ArrayList<DdlIndexMetadata>();
        for (Map.Entry<String, DdlIndexMetadata> entry : desired.entrySet()) {
            DdlIndexMetadata currentIndex = existing.get(entry.getKey());
            if (currentIndex == null) {
                added.add(entry.getValue());
            } else if (!sameIndex(currentIndex, entry.getValue())) {
                if (synchronizeAll) {
                    removedIndexes.add(MysqlIndexNames.resolve(entity.tableName(), currentIndex));
                    added.add(entry.getValue());
                } else {
                    errors.add("已有索引定义变化暂不支持重建，拒绝修改索引: " + entry.getKey());
                }
            }
        }
        for (String existingName : existing.keySet()) {
            if (!desired.containsKey(existingName)) {
                if (synchronizeAll) {
                    removedIndexes.add(MysqlIndexNames.resolve(entity.tableName(), existing.get(existingName)));
                } else {
                    errors.add("删除索引暂不支持，发现数据库多余索引: " + existing.get(existingName).name());
                }
            }
        }
        return added;
    }

    private static void putIndex(String tableName, Map<String, DdlIndexMetadata> indexes,
                                 DdlIndexMetadata index,
                                 List<String> errors) {
        String name = identifierKey(MysqlIndexNames.resolve(tableName, index));
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
            // 只有明确验证过的长度类型进入白名单，不能对任意带括号类型推断安全扩容。
            return ("varchar".equals(currentBase) || "varbinary".equals(currentBase))
                    && desiredSize >= currentSize;
        }
        return SAFE_WIDENING.contains(current + ":" + desired);
    }

    private static String normalizeType(String value) {
        return normalizeText(value).toLowerCase(Locale.ROOT)
                .replace("character varying", "varchar")
                .replace("integer", "int")
                .replace("numeric", "decimal")
                .replaceAll("\\s+", "")
                // 整数括号是显示宽度，不改变取值范围；unsigned 属性仍参与比较。
                .replaceAll("\\b(tinyint|smallint|mediumint|int|bigint)\\(\\d+\\)", "$1");
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
