package com.entloom.crud.starter.config;

import com.entloom.crud.api.access.BusinessIdentityIdType;
import com.entloom.crud.core.governance.authorization.AuthorizationFields;
import java.util.Comparator;
import java.util.Locale;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;

/** 声明式业务访问与简单身份定义；配置不替代可信认证和归属事实。 */
@Data
public class CrudAccessProperties {
    private Map<String, Identity> identities = new LinkedHashMap<>();
    private String defaultPolicy;
    private Policy defaults = new Policy();
    private Map<String, Policy> policies = new LinkedHashMap<>();
    private Map<String, Relation> relations = new LinkedHashMap<>();
    @Data
    public static class Identity {
        /** 缺省为身份码 + Id。 */
        private String idField;
        /** 显式声明 ID 类型，避免把数字业务标识隐式转换为文本。 */
        private BusinessIdentityIdType idType;
    }

    /** 全局默认、共享策略与实体差异共用同一声明结构。 */
    @Data
    public static class Policy {
        private List<String> actions;
        private Map<String, Subject> access = new LinkedHashMap<>();
    }

    /** 同一访问主体的动作、范围、规则与字段集中声明，省略项独立继承。 */
    public static class Subject extends LinkedHashMap<String, Object> {
        // 使用同一个 Map 边界接收 Spring Binder 的索引列表及实体解析结果，避免 Object 属性丢失列表。
        public Object getActions() { return get("actions"); }
        public void setActions(Object actions) { put("actions", actions); }
        public String getScope() {
            Object scope = get("scope");
            if (scope == null) return null;
            if (!(scope instanceof String)) throw new IllegalArgumentException("scope 必须为字符串");
            return (String) scope;
        }
        public void setScope(String scope) { put("scope", scope); }
        public Map<String, Object> getRules() {
            if (!containsKey("rules")) return null;
            Map<String, Object> rules = new LinkedHashMap<>();
            objectMap(get("rules"), "rules").forEach((field, value) -> rules.put(field, indexedList(value)));
            return rules;
        }
        public void setRules(Map<String, Object> rules) { put("rules", rules); }
        public Map<AuthorizationFields.Use, Field> getFields() {
            var result = new LinkedHashMap<AuthorizationFields.Use, Field>();
            if (!containsKey("fields")) return result;
            objectMap(get("fields"), "fields").forEach((use, raw) -> {
                var key = AuthorizationFields.Use.valueOf(use.toUpperCase(Locale.ROOT));
                Field field;
                if (raw instanceof Field typed) field = typed;
                else {
                    var attributes = objectMap(raw, "fields." + use);
                    if (!Set.of("mode", "fields").containsAll(attributes.keySet()))
                        throw new IllegalArgumentException("fields." + use + ": 仅允许 mode/fields");
                    field = new Field();
                    if (attributes.containsKey("mode")) field.setMode(CrudEntityDeclarations.FieldMode.valueOf(
                        attributes.get("mode").toString().toUpperCase(Locale.ROOT)));
                    if (attributes.containsKey("fields")) {
                        Object names = indexedList(attributes.get("fields"));
                        if ("".equals(names)) names = List.of();
                        if (!(names instanceof List<?> list) || list.stream().anyMatch(name -> !(name instanceof String)))
                            throw new IllegalArgumentException("fields." + use + ".fields 必须为字符串列表");
                        field.setFields(((List<?>) names).stream().map(String.class::cast).toList());
                    }
                }
                if (result.putIfAbsent(key, field) != null) throw new IllegalArgumentException("重复字段用途: " + use);
            });
            return result;
        }
        public void setFields(Map<AuthorizationFields.Use, Field> fields) {
            Map<String, Object> values = new LinkedHashMap<>();
            fields.forEach((use, field) -> values.put(use.name(), field));
            put("fields", values);
        }
        private static Map<String, Object> objectMap(Object raw, String path) {
            if (!(raw instanceof Map<?, ?> map)) throw new IllegalArgumentException(path + " 必须为对象");
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, value) -> result.put(key.toString(), value));
            return result;
        }
        private static Object indexedList(Object value) {
            if (value instanceof Map<?, ?> map && !map.isEmpty()
                && map.keySet().stream().allMatch(key -> key.toString().matches("[0-9]+"))) {
                return map.entrySet().stream().sorted(Comparator.comparingInt(
                    entry -> Integer.parseInt(entry.getKey().toString()))).map(Map.Entry::getValue).toList();
            }
            return value;
        }
    }

    /** 字段始终使用用途对象，不支持列表简写。 */
    @Data
    public static class Field {
        private CrudEntityDeclarations.FieldMode mode = CrudEntityDeclarations.FieldMode.EXCLUDE;
        private List<String> fields = List.of();
    }

    @Data
    public static class Relation {
        /** 缺省为关系名称 + Id。 */
        private String field;
        /** 缺省为关系名称 + Id，读取已核验身份的属性。 */
        private String identityField;
    }
}
