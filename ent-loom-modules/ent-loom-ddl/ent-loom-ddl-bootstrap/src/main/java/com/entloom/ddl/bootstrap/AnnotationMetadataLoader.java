package com.entloom.ddl.bootstrap;

import com.entloom.base.common.OptionalBoolean;
import com.entloom.ddl.annotations.EntDbEntity;
import com.entloom.ddl.annotations.EntDbField;
import com.entloom.ddl.annotations.EntDbIndex;
import com.entloom.ddl.api.DdlEntityMetadata;
import com.entloom.ddl.api.DdlFieldMetadata;
import com.entloom.ddl.api.DdlIndexMetadata;
import com.entloom.ddl.enums.DdlTableSize;
import com.entloom.ddl.api.MetadataLoadRequest;
import com.entloom.ddl.api.MetadataLoader;
import com.entloom.ddl.enums.NamingStrategy;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 注解元数据加载器。
 */
public final class AnnotationMetadataLoader implements MetadataLoader {
    private final EntityClassResolver classResolver;

    public AnnotationMetadataLoader() {
        this(new NoopEntityClassResolver());
    }

    public AnnotationMetadataLoader(EntityClassResolver classResolver) {
        this.classResolver = classResolver == null ? new NoopEntityClassResolver() : classResolver;
    }

    @Override
    public List<DdlEntityMetadata> load(MetadataLoadRequest request) {
        if (request == null) {
            return new ArrayList<DdlEntityMetadata>();
        }
        Set<Class<?>> allClasses = new LinkedHashSet<Class<?>>();
        allClasses.addAll(request.entityClasses());
        allClasses.addAll(classResolver.resolve(request.basePackages()));

        List<DdlEntityMetadata> entities = new ArrayList<DdlEntityMetadata>();
        for (Class<?> candidate : allClasses) {
            if (candidate == null) {
                continue;
            }
            EntDbEntity entityAnn = candidate.getAnnotation(EntDbEntity.class);
            if (entityAnn == null) {
                continue;
            }
            entities.add(buildEntity(candidate, entityAnn));
        }
        return entities;
    }

    private DdlEntityMetadata buildEntity(Class<?> entityClass, EntDbEntity entityAnn) {
        String tableName = trim(entityAnn.table()).isEmpty()
                ? toTableName(entityClass.getSimpleName(), entityAnn.namingStrategy())
                : entityAnn.table().trim();
        List<DdlFieldMetadata> fields = resolveFields(entityClass);
        List<DdlIndexMetadata> indexes = resolveIndexes(entityClass, fields);
        DdlTableSize size = toApiTableSize(entityAnn.size());
        return new DdlEntityMetadata(entityClass.getName(),
                entityAnn.schema(),
                tableName,
                entityAnn.comment(),
                size,
                fields,
                indexes);
    }

    private List<DdlFieldMetadata> resolveFields(Class<?> entityClass) {
        List<Field> declared = collectDeclaredFields(entityClass);
        List<DdlFieldMetadata> fields = new ArrayList<DdlFieldMetadata>();
        for (Field field : declared) {
            if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) {
                continue;
            }
            EntDbField ann = field.getAnnotation(EntDbField.class);
            boolean persisted = ann == null || ann.persisted() != OptionalBoolean.FALSE;
            String columnName = ann == null || trim(ann.column()).isEmpty() ? toSnake(field.getName()) : ann.column().trim();
            boolean primaryKey = ann != null && ann.primaryKey() == OptionalBoolean.TRUE;
            if (!primaryKey && "id".equals(field.getName())) {
                primaryKey = true;
            }
            boolean nullable = ann == null
                    ? !field.getType().isPrimitive()
                    : (ann.nullable() == OptionalBoolean.UNSET ? !field.getType().isPrimitive() : ann.nullable() == OptionalBoolean.TRUE);
            boolean unique = ann != null && ann.unique() == OptionalBoolean.TRUE;
            DdlFieldMetadata metadata = new DdlFieldMetadata(
                    field.getName(),
                    columnName,
                    field.getType(),
                    ann == null ? "" : ann.columnDefinition(),
                    nullable,
                    unique,
                    persisted,
                    primaryKey,
                    ann == null ? -1 : ann.length(),
                    ann == null ? -1 : ann.precision(),
                    ann == null ? -1 : ann.scale(),
                    ann == null ? "" : ann.defaultValue(),
                    ann == null ? "" : ann.comment(),
                    ann == null ? "" : ann.renameFrom());
            fields.add(metadata);
        }
        return fields;
    }

    private List<DdlIndexMetadata> resolveIndexes(Class<?> entityClass, List<DdlFieldMetadata> fields) {
        List<DdlIndexMetadata> indexes = new ArrayList<DdlIndexMetadata>();
        EntDbIndex[] classIndexes = entityClass.getAnnotationsByType(EntDbIndex.class);
        for (EntDbIndex classIndex : classIndexes) {
            indexes.add(new DdlIndexMetadata(classIndex.name(),
                    toList(classIndex.fields()),
                    classIndex.unique() == OptionalBoolean.TRUE,
                    classIndex.expression()));
        }

        Map<String, String> fieldToColumn = new LinkedHashMap<String, String>();
        for (DdlFieldMetadata field : fields) {
            fieldToColumn.put(field.fieldName(), field.columnName());
        }
        for (Field field : collectDeclaredFields(entityClass)) {
            EntDbIndex[] fieldIndexes = field.getAnnotationsByType(EntDbIndex.class);
            if (fieldIndexes.length == 0) {
                continue;
            }
            String defaultColumn = fieldToColumn.containsKey(field.getName())
                    ? fieldToColumn.get(field.getName())
                    : toSnake(field.getName());
            for (EntDbIndex fieldIndex : fieldIndexes) {
                List<String> indexFields = toList(fieldIndex.fields());
                if (indexFields.isEmpty()) {
                    indexFields.add(defaultColumn);
                }
                indexes.add(new DdlIndexMetadata(fieldIndex.name(),
                        indexFields,
                        fieldIndex.unique() == OptionalBoolean.TRUE,
                        fieldIndex.expression()));
            }
        }
        return indexes;
    }

    private static List<String> toList(String[] values) {
        List<String> list = new ArrayList<String>();
        if (values == null || values.length == 0) {
            return list;
        }
        for (String value : values) {
            if (trim(value).isEmpty()) {
                continue;
            }
            list.add(value.trim());
        }
        return list;
    }

    private static List<Field> collectDeclaredFields(Class<?> type) {
        List<Field> result = new ArrayList<Field>();
        Class<?> cursor = type;
        while (cursor != null && cursor != Object.class) {
            Field[] declared = cursor.getDeclaredFields();
            for (Field field : declared) {
                result.add(field);
            }
            cursor = cursor.getSuperclass();
        }
        return result;
    }

    private static String toTableName(String simpleName, NamingStrategy strategy) {
        if (strategy == NamingStrategy.AS_IS) {
            return simpleName;
        }
        return toSnake(simpleName);
    }

    private static DdlTableSize toApiTableSize(com.entloom.ddl.enums.DdlTableSize size) {
        if (size == null) {
            return DdlTableSize.UNSET;
        }
        return DdlTableSize.valueOf(size.name());
    }

    private static String toSnake(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        StringBuilder sb = new StringBuilder();
        char[] chars = value.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            boolean upper = Character.isUpperCase(c);
            if (upper && i > 0) {
                sb.append('_');
            }
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
