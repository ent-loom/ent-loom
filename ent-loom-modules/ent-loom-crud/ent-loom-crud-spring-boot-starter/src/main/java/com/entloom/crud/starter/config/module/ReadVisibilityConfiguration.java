package com.entloom.crud.starter.config.module;

import com.entloom.crud.core.governance.scope.ReadVisibilityContributor;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.starter.config.CrudProperties;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 将实体、可信入口和条件直接编译为读取可见性。 */
@Configuration(proxyBeanMethods = false)
public class ReadVisibilityConfiguration {
    @Bean
    public ReadVisibilityContributor configuredReadVisibilityContributor(CrudProperties properties, EntityMetaRegistry registry) {
        Map<Class<?>, Map<String, Map<String, Object>>> visibility = new LinkedHashMap<>();
        Map<String, Map<String, Object>> config = properties.getGovernance().getReadVisibility();
        if (!config.isEmpty()) {
            Map<String, Class<?>> entities = new LinkedHashMap<>();
            for (EntityMeta meta : registry.getEntityMetas()) {
                Class<?> previous = entities.put(meta.getEntityName(), meta.getEntityType());
                if (previous != null) throw new IllegalArgumentException("实体资源码重复: " + meta.getEntityName());
            }
            for (Map.Entry<String, Map<String, Object>> entity : config.entrySet()) {
                Class<?> type = entities.get(entity.getKey());
                if (type == null) throw new IllegalArgumentException("可见性配置引用了未注册实体: " + entity.getKey());
                if (entity.getValue() == null) throw new IllegalArgumentException("实体可见性入口不能为空: " + entity.getKey());
                Map<String, Map<String, Object>> entries = new LinkedHashMap<>();
                for (Map.Entry<String, Object> entry : entity.getValue().entrySet()) {
                    entries.put(entry.getKey(), conditions(entry.getValue()));
                }
                visibility.put(type, entries);
            }
        }
        return new ReadVisibilityContributor(registry, visibility);
    }

    private Map<String, Object> conditions(Object value) {
        if ("all".equals(value)) return Collections.emptyMap();
        if (!(value instanceof Map)) throw new IllegalArgumentException("入口可见性必须声明字段条件映射或 all");
        Map<String, Object> conditions = new LinkedHashMap<>();
        for (Map.Entry<?, ?> condition : ((Map<?, ?>) value).entrySet()) {
            if (!(condition.getKey() instanceof String)) throw new IllegalArgumentException("可见性字段名必须为字符串");
            conditions.put((String) condition.getKey(), conditionValue(condition.getValue()));
        }
        return conditions;
    }

    private Object conditionValue(Object value) {
        if (!(value instanceof Map)) return value;
        // Binder 将 Object 类型的 YAML 集合绑定为索引映射，转换回 IN 条件列表。
        Map<?, ?> indexed = (Map<?, ?>) value;
        List<Object> values = new ArrayList<>();
        for (int index = 0; index < indexed.size(); index++) {
            String key = String.valueOf(index);
            if (!indexed.containsKey(key)) throw new IllegalArgumentException("可见性条件仅支持标量或列表");
            values.add(indexed.get(key));
        }
        return values;
    }
}
