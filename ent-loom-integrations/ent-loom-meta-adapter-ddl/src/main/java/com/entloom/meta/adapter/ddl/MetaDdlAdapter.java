package com.entloom.meta.adapter.ddl;

import com.entloom.ddl.api.DdlEntityMetadata;
import com.entloom.meta.annotations.EntEntity;
import com.entloom.meta.contract.descriptor.EntEntityDescriptor;
import com.entloom.meta.contract.diagnostic.DefaultMetaDiagnosticPolicy;
import com.entloom.meta.contract.diagnostic.MetaDiagnostic;
import com.entloom.meta.contract.diagnostic.MetaDiagnosticCollector;
import com.entloom.meta.contract.diagnostic.MetaDiagnosticPolicy;
import com.entloom.meta.core.parser.EntMetaParser;
import com.entloom.meta.core.parser.ReflectiveEntMetaParser;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 将通用 Meta Descriptor 投影为 DDL Runtime Model。
 *
 * <p>适配器同时接受 Meta-only、DDL-only 和 Meta + DDL override 实体。DDL
 * 显式值优先于 Meta 值；冲突不会被静默吞掉，而是通过 {@link #diagnostics()}
 * 暴露给上层 Registry 或启动入口。</p>
 */
public final class MetaDdlAdapter {
    private final MetaDiagnosticCollector diagnostics = new MetaDiagnosticCollector();
    private final DdlNativeAnnotationParser nativeParser = new DdlNativeAnnotationParser();
    private final DdlRuntimeModelMerger merger = new DdlRuntimeModelMerger();
    private final List<DdlEntityMetadata> models;

    public MetaDdlAdapter(Collection<Class<?>> entityClasses) {
        this(entityClasses, new ReflectiveEntMetaParser(), DefaultMetaDiagnosticPolicy.failFast());
    }

    public MetaDdlAdapter(Collection<Class<?>> entityClasses, EntMetaParser parser) {
        this(entityClasses, parser, DefaultMetaDiagnosticPolicy.failFast());
    }

    public MetaDdlAdapter(
        Collection<Class<?>> entityClasses,
        EntMetaParser parser,
        MetaDiagnosticPolicy diagnosticPolicy
    ) {
        if (parser == null) {
            throw new IllegalArgumentException("parser 不能为空");
        }
        List<DdlEntityMetadata> parsed = new ArrayList<DdlEntityMetadata>();
        for (Class<?> entityClass : stableClasses(entityClasses)) {
            boolean hasMeta = entityClass.getAnnotation(EntEntity.class) != null;
            DdlNativeEntityModel nativeModel = nativeParser.parse(entityClass);
            if (!hasMeta && nativeModel == null) {
                continue;
            }

            EntEntityDescriptor metaDescriptor = null;
            if (hasMeta) {
                com.entloom.meta.contract.diagnostic.MetaDiagnosticResult<EntEntityDescriptor> result =
                    parser.parseWithDiagnostics(entityClass);
                diagnostics.addAll(result.diagnostics());
                metaDescriptor = result.value();
            }
            com.entloom.meta.contract.diagnostic.MetaDiagnosticResult<DdlEntityMetadata> merged =
                merger.merge(entityClass, metaDescriptor, nativeModel);
            diagnostics.addAll(merged.diagnostics());
            if (merged.value() != null) {
                parsed.add(merged.value());
            }
        }
        parsed.sort(Comparator.comparing(DdlEntityMetadata::entityClassName));
        this.models = Collections.unmodifiableList(new ArrayList<DdlEntityMetadata>(parsed));
        MetaDiagnosticPolicy policy = diagnosticPolicy == null
            ? DefaultMetaDiagnosticPolicy.failFast()
            : diagnosticPolicy;
        policy.evaluate(diagnostics.diagnostics());
    }

    /**
     * 返回稳定排序且不可变的 DDL 元数据。
     */
    public List<DdlEntityMetadata> models() {
        return models;
    }

    /**
     * 返回适配和冲突诊断。
     */
    public List<MetaDiagnostic> diagnostics() {
        return diagnostics.diagnostics();
    }

    private List<Class<?>> stableClasses(Collection<Class<?>> entityClasses) {
        Map<String, Class<?>> unique = new LinkedHashMap<String, Class<?>>();
        if (entityClasses != null) {
            for (Class<?> entityClass : entityClasses) {
                if (entityClass != null) {
                    unique.putIfAbsent(entityClass.getName(), entityClass);
                }
            }
        }
        List<Class<?>> result = new ArrayList<Class<?>>(unique.values());
        result.sort(Comparator.comparing(Class::getName));
        return result;
    }
}
