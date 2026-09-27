package com.entloom.ddl.spring;

import com.entloom.base.util.reflect.EntityProperties;

import com.entloom.base.common.OptionalBoolean;
import com.entloom.ddl.annotations.EntDdlEntity;
import com.entloom.ddl.annotations.EntDdlField;
import com.entloom.ddl.annotations.EntDdlIndex;
import com.entloom.ddl.api.DdlEntityMetadata;
import com.entloom.ddl.api.DdlFieldMetadata;
import com.entloom.ddl.api.DdlGenerationDefaults;
import com.entloom.ddl.api.DdlIndexMetadata;
import com.entloom.ddl.enums.DdlTableSize;
import com.entloom.ddl.enums.GenerationStrategy;
import com.entloom.ddl.api.MetadataLoadRequest;
import com.entloom.ddl.api.MetadataLoader;
import com.entloom.ddl.enums.NamingStrategy;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Spring 侧注解元数据加载器。
 */
public final class SpringAnnotationMetadataLoader implements MetadataLoader {
    private final SpringPackageEntityClassResolver classResolver;
    private final DdlGenerationDefaults defaults;

    public SpringAnnotationMetadataLoader(SpringPackageEntityClassResolver classResolver) {
        this(classResolver, new DdlGenerationDefaults());
    }

    public SpringAnnotationMetadataLoader(SpringPackageEntityClassResolver classResolver,
                                         DdlGenerationDefaults defaults) {
        this.classResolver = classResolver;
        this.defaults = defaults == null ? new DdlGenerationDefaults() : defaults;
    }

    @Override
    public List<DdlEntityMetadata> load(MetadataLoadRequest request) {
        if (request == null) {
            return new ArrayList<DdlEntityMetadata>();
        }
        List<Class<?>> allClasses = discoverClasses(request);

        List<DdlEntityMetadata> entities = new ArrayList<DdlEntityMetadata>();
        for (Class<?> candidate : allClasses) {
            if (candidate == null) {
                continue;
            }
            EntDdlEntity entityAnn = candidate.getAnnotation(EntDdlEntity.class);
            if (entityAnn == null) {
                continue;
            }
            entities.add(buildEntity(candidate, entityAnn));
        }
        return entities;
    }

    /**
     * 合并显式类和包扫描结果，并固定实体发现顺序。
     */
    private List<Class<?>> discoverClasses(MetadataLoadRequest request) {
        Map<String, Class<?>> classesByName = new LinkedHashMap<String, Class<?>>();
        addClasses(classesByName, request.entityClasses());
        if (classResolver != null) {
            addClasses(classesByName, classResolver.resolve(request.basePackages()));
        }
        List<Class<?>> classes = new ArrayList<Class<?>>(classesByName.values());
        classes.sort(Comparator.comparing(Class::getName));
        return classes;
    }

    private static void addClasses(Map<String, Class<?>> classesByName, List<Class<?>> classes) {
        if (classes == null || classes.isEmpty()) {
            return;
        }
        for (Class<?> candidate : classes) {
            if (candidate != null) {
                classesByName.putIfAbsent(candidate.getName(), candidate);
            }
        }
    }

    private DdlEntityMetadata buildEntity(Class<?> entityClass, EntDdlEntity entityAnn) {
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
        List<DdlFieldMetadata> fields = new ArrayList<DdlFieldMetadata>();
        for (Field field : EntityProperties.fields(entityClass)) {
            if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) {
                continue;
            }
            EntDdlField ann = field.getAnnotation(EntDdlField.class);
            boolean persisted = EntityProperties.describe(field).persisted(
                ann == null ? OptionalBoolean.UNSET : ann.persisted());
            String columnName = ann == null || trim(ann.column()).isEmpty() ? toSnake(field.getName()) : ann.column().trim();
            boolean primaryKey = ann == null || ann.primaryKey() == OptionalBoolean.UNSET
                ? "id".equals(field.getName()) : ann.primaryKey() == OptionalBoolean.TRUE;
            boolean nullable = ann != null && ann.nullable() == OptionalBoolean.TRUE;
            if (primaryKey && (ann == null || ann.nullable() != OptionalBoolean.TRUE)) {
                nullable = false;
            }
            boolean unique = ann != null && ann.unique() == OptionalBoolean.TRUE;
            fields.add(new DdlFieldMetadata(
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
                    ann == null ? "" : ann.renameFrom(),
                    ann != null && ann.generationStrategy() != GenerationStrategy.UNSET
                        ? ann.generationStrategy()
                        : primaryKey ? defaults.generationStrategy() : GenerationStrategy.UNSET));
        }
        return fields;
    }

    private List<DdlIndexMetadata> resolveIndexes(Class<?> entityClass, List<DdlFieldMetadata> fields) {
        List<DdlIndexMetadata> indexes = new ArrayList<DdlIndexMetadata>();
        Map<String, String> fieldToColumn = new LinkedHashMap<String, String>();
        for (DdlFieldMetadata field : fields) {
            if (field.persisted()) {
                fieldToColumn.put(field.fieldName(), field.columnName());
            }
        }
        for (EntDdlIndex index : entityClass.getAnnotationsByType(EntDdlIndex.class)) {
            List<String> columns = new ArrayList<String>();
            for (String property : index.fields()) {
                String column = fieldToColumn.get(trim(property));
                if (column == null) {
                    throw new IllegalArgumentException(entityClass.getName() + " 索引引用了不存在或不持久化的 Java 属性: " + property);
                }
                columns.add(column);
            }
            indexes.add(new DdlIndexMetadata(index.name(), columns,
                    index.unique() == OptionalBoolean.TRUE, index.expression()));
        }
        return indexes;
    }

    private static String toTableName(String simpleName, NamingStrategy strategy) {
        if (strategy == NamingStrategy.AS_IS) {
            return simpleName;
        }
        if (simpleName.endsWith("Entity")) {
            simpleName = simpleName.substring(0, simpleName.length() - "Entity".length());
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
            if (Character.isUpperCase(c) && i > 0) {
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
