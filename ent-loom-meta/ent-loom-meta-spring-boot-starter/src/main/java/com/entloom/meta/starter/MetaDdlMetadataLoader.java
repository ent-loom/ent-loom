package com.entloom.meta.starter;

import com.entloom.crud.core.runtime.meta.EntityIdPolicy;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.ddl.api.DdlEntityMetadata;
import com.entloom.ddl.api.DdlFieldMetadata;
import com.entloom.ddl.api.DdlGenerationDefaults;
import com.entloom.ddl.api.MetadataLoadRequest;
import com.entloom.ddl.api.MetadataLoader;
import com.entloom.ddl.enums.GenerationStrategy;
import com.entloom.ddl.spring.SpringPackageEntityClassResolver;
import com.entloom.meta.adapter.ddl.MetaDdlAdapter;
import com.entloom.meta.contract.diagnostic.MetaDiagnosticPolicy;
import com.entloom.meta.core.parser.EntMetaParser;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;

/** 优先使用显式 DDL 来源，缺省复用 Meta 来源，并在执行 SQL 前校验主键契约。 */
final class MetaDdlMetadataLoader implements MetadataLoader {
    private final List<Class<?>> metaClasses;
    private final SpringPackageEntityClassResolver classResolver;
    private final EntMetaParser parser;
    private final DdlGenerationDefaults defaults;
    private final MetaDiagnosticPolicy diagnosticPolicy;
    private final ObjectProvider<EntityMetaRegistry> registryProvider;

    MetaDdlMetadataLoader(List<Class<?>> metaClasses, SpringPackageEntityClassResolver classResolver,
                          EntMetaParser parser, DdlGenerationDefaults defaults,
                          MetaDiagnosticPolicy diagnosticPolicy, ObjectProvider<EntityMetaRegistry> registryProvider) {
        this.metaClasses = new ArrayList<Class<?>>(metaClasses);
        this.classResolver = classResolver;
        this.parser = parser;
        this.defaults = defaults;
        this.diagnosticPolicy = diagnosticPolicy;
        this.registryProvider = registryProvider;
    }

    @Override
    public List<DdlEntityMetadata> load(MetadataLoadRequest request) {
        if (request == null) {
            return Collections.emptyList();
        }
        Set<Class<?>> classes = new LinkedHashSet<Class<?>>();
        if (request.entityClasses().isEmpty() && request.basePackages().isEmpty()) {
            classes.addAll(metaClasses);
        } else {
            classes.addAll(request.entityClasses());
            classes.addAll(classResolver.resolve(request.basePackages()));
        }
        List<DdlEntityMetadata> models = new MetaDdlAdapter(classes, parser, diagnosticPolicy, defaults).models();
        EntityMetaRegistry registry = registryProvider.getIfAvailable();
        if (registry != null) {
            for (EntityMeta crud : registry.getEntityMetas()) {
                for (DdlEntityMetadata ddl : models) {
                    if (crud.getEntityType().getName().equals(ddl.entityClassName())) {
                        validateIdentity(crud, ddl);
                    }
                }
            }
        }
        return models;
    }

    private void validateIdentity(EntityMeta crud, DdlEntityMetadata ddl) {
        List<DdlFieldMetadata> primaryKeys = new ArrayList<DdlFieldMetadata>();
        for (DdlFieldMetadata field : ddl.fields()) {
            if (field.primaryKey()) {
                primaryKeys.add(field);
            }
        }
        Map<String, String> crudKeys = new LinkedHashMap<String, String>();
        for (String field : crud.getIdField().split(",")) {
            String name = field.trim();
            crudKeys.put(name, crud.resolveColumn(name));
        }
        Map<String, String> ddlKeys = new LinkedHashMap<String, String>();
        boolean databaseGenerated = false;
        for (DdlFieldMetadata field : primaryKeys) {
            ddlKeys.put(field.fieldName(), field.columnName());
            databaseGenerated |= field.generationStrategy() == GenerationStrategy.AUTO_INCREMENT
                || field.generationStrategy() == GenerationStrategy.IDENTITY;
        }
        if (!crud.getTable().equals(ddl.tableName()) || !crudKeys.equals(ddlKeys)) {
            throw conflict(crud, "CRUD 与 DDL 的表名、主键字段或列名不一致");
        }
        if (primaryKeys.size() > 1 && databaseGenerated) {
            throw conflict(crud, "联合主键不支持数据库自动生成");
        }
        if ((crud.getIdPolicy() == EntityIdPolicy.GENERATED) != databaseGenerated) {
            throw conflict(crud, "CRUD=" + crud.getIdPolicy() + ", DDL 数据库生成=" + databaseGenerated);
        }
    }

    private IllegalStateException conflict(EntityMeta crud, String detail) {
        return new IllegalStateException("主键契约冲突: " + crud.getEntityType().getName() + "，" + detail);
    }
}
