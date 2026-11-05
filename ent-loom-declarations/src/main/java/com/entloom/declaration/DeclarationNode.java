package com.entloom.declaration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 不解释业务含义的只读声明节点；错误始终携带文件、资源码与配置路径。 */
public final class DeclarationNode {
    private final String source;
    private final String resource;
    private final String path;
    private final Object value;

    DeclarationNode(String source, String resource, String path, Object value) {
        this.source = source;
        this.resource = resource;
        this.path = path;
        this.value = freeze(value);
    }

    public String source() { return source + " [实体=" + resource + ", 路径=" + path + "]"; }
    public IllegalArgumentException error(String message) { return new IllegalArgumentException(source() + ": " + message); }

    public Map<String, DeclarationNode> members() {
        if (!(value instanceof Map)) throw error("必须为对象，不能为 null");
        Map<String, DeclarationNode> result = new LinkedHashMap<>();
        ((Map<?, ?>) value).forEach((key, child) -> {
            if (!(key instanceof String) || ((String) key).trim().isEmpty()) throw error("配置项名称必须为非空字符串");
            result.put((String) key, new DeclarationNode(source, resource, path.isEmpty() ? (String) key : path + "." + key, child));
        });
        return Collections.unmodifiableMap(result);
    }

    public void only(String... names) {
        List<String> allowed = java.util.Arrays.asList(names);
        members().forEach((name, node) -> { if (!allowed.contains(name)) throw node.error("未知配置项"); });
    }

    public boolean bool() {
        if (!(value instanceof Boolean)) throw error("必须为布尔值，不能为 null");
        return (Boolean) value;
    }

    public int integer() {
        if (!(value instanceof Integer)) throw error("必须为整数，不能为 null");
        return (Integer) value;
    }

    public java.util.List<DeclarationNode> elements() {
        if (!(value instanceof List)) throw error("必须为列表，不能为 null");
        List<DeclarationNode> nodes = new ArrayList<>();
        int index = 0;
        for (Object child : (List<?>) value) nodes.add(new DeclarationNode(source, resource, path + "[" + index++ + "]", child));
        return Collections.unmodifiableList(nodes);
    }

    public String text() {
        if (!(value instanceof String) || ((String) value).trim().isEmpty()
            || !value.equals(((String) value).trim())) throw error("必须为非空字符串且不能包含首尾空格");
        return (String) value;
    }

    public List<String> strings() {
        if (!(value instanceof List)) throw error("必须为字符串列表，不能为 null");
        List<String> result = new ArrayList<>();
        int index = 0;
        for (Object child : (List<?>) value) {
            String text = new DeclarationNode(source, resource, path + "[" + index++ + "]", child).text();
            if (result.contains(text)) throw error("重复值: " + text);
            result.add(text);
        }
        return Collections.unmodifiableList(result);
    }

    private static Object freeze(Object value) {
        if (value instanceof Map) {
            Map<Object, Object> copy = new LinkedHashMap<>();
            ((Map<?, ?>) value).forEach((key, child) -> copy.put(key, freeze(child)));
            return Collections.unmodifiableMap(copy);
        }
        if (value instanceof List) {
            List<Object> copy = new ArrayList<>();
            for (Object child : (List<?>) value) copy.add(freeze(child));
            return Collections.unmodifiableList(copy);
        }
        return value;
    }
}
