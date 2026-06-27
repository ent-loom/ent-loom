package com.entloom.crud.core.idempotency;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * 默认稳定序列化实现。
 */
public class StablePayloadCanonicalizer implements PayloadCanonicalizer {

    @Override
    public String canonicalize(Object payload) {
        StringBuilder sb = new StringBuilder();
        appendStableValue(payload, sb, new IdentityHashMap<Object, Boolean>());
        return sb.toString();
    }

    /**
     * 追加任意值的稳定序列化内容。
     */
    private void appendStableValue(Object value, StringBuilder sb, IdentityHashMap<Object, Boolean> visiting) {
        if (value == null) {
            sb.append("null");
            return;
        }
        if (value instanceof CharSequence) {
            appendQuoted(String.valueOf(value), sb);
            return;
        }
        if (value instanceof Number || value instanceof Boolean) {
            sb.append(String.valueOf(value));
            return;
        }
        if (value instanceof Enum<?>) {
            appendQuoted(((Enum<?>) value).name(), sb);
            return;
        }
        if (value.getClass().isArray()) {
            appendArray(value, sb, visiting);
            return;
        }
        if (value instanceof Collection<?>) {
            appendCollection((Collection<?>) value, sb, visiting);
            return;
        }
        if (value instanceof Map<?, ?>) {
            appendMap((Map<?, ?>) value, sb, visiting);
            return;
        }
        appendBean(value, sb, visiting);
    }

    /**
     * 追加数组的稳定序列化内容。
     */
    private void appendArray(Object array, StringBuilder sb, IdentityHashMap<Object, Boolean> visiting) {
        if (visiting.put(array, Boolean.TRUE) != null) {
            throw new IllegalArgumentException("幂等哈希不支持循环引用载荷");
        }
        try {
            sb.append('[');
            int length = Array.getLength(array);
            for (int i = 0; i < length; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                appendStableValue(Array.get(array, i), sb, visiting);
            }
            sb.append(']');
        } finally {
            visiting.remove(array);
        }
    }

    /**
     * 追加集合的稳定序列化内容。
     */
    private void appendCollection(Collection<?> collection, StringBuilder sb, IdentityHashMap<Object, Boolean> visiting) {
        if (visiting.put(collection, Boolean.TRUE) != null) {
            throw new IllegalArgumentException("幂等哈希不支持循环引用载荷");
        }
        try {
            sb.append('[');
            boolean first = true;
            for (Object item : collection) {
                if (!first) {
                    sb.append(',');
                }
                appendStableValue(item, sb, visiting);
                first = false;
            }
            sb.append(']');
        } finally {
            visiting.remove(collection);
        }
    }

    /**
     * 追加映射对象的稳定序列化内容。
     */
    private void appendMap(Map<?, ?> map, StringBuilder sb, IdentityHashMap<Object, Boolean> visiting) {
        if (visiting.put(map, Boolean.TRUE) != null) {
            throw new IllegalArgumentException("幂等哈希不支持循环引用载荷");
        }
        try {
            List<MapEntry> entries = new ArrayList<MapEntry>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                entries.add(new MapEntry(String.valueOf(entry.getKey()), entry.getValue()));
            }
            Collections.sort(entries, Comparator.comparing(MapEntry::getKey));

            sb.append('{');
            for (int i = 0; i < entries.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                MapEntry entry = entries.get(i);
                appendQuoted(entry.getKey(), sb);
                sb.append(':');
                appendStableValue(entry.getValue(), sb, visiting);
            }
            sb.append('}');
        } finally {
            visiting.remove(map);
        }
    }

    /**
     * 追加 Bean 对象的稳定序列化内容。
     */
    private void appendBean(Object bean, StringBuilder sb, IdentityHashMap<Object, Boolean> visiting) {
        if (visiting.put(bean, Boolean.TRUE) != null) {
            throw new IllegalArgumentException("幂等哈希不支持循环引用载荷");
        }
        try {
            PropertyDescriptor[] descriptors = Introspector.getBeanInfo(bean.getClass(), Object.class).getPropertyDescriptors();
            List<PropertyDescriptor> readable = new ArrayList<PropertyDescriptor>();
            for (PropertyDescriptor descriptor : descriptors) {
                if (descriptor.getReadMethod() != null) {
                    readable.add(descriptor);
                }
            }
            Collections.sort(readable, Comparator.comparing(PropertyDescriptor::getName));
            if (readable.isEmpty()) {
                appendQuoted(String.valueOf(bean), sb);
                return;
            }

            sb.append('{');
            boolean first = true;
            for (PropertyDescriptor descriptor : readable) {
                Method readMethod = descriptor.getReadMethod();
                if (!readMethod.isAccessible()) {
                    readMethod.setAccessible(true);
                }
                Object propertyValue = readMethod.invoke(bean);
                if (!first) {
                    sb.append(',');
                }
                appendQuoted(descriptor.getName(), sb);
                sb.append(':');
                appendStableValue(propertyValue, sb, visiting);
                first = false;
            }
            sb.append('}');
        } catch (IntrospectionException e) {
            appendQuoted(String.valueOf(bean), sb);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("访问载荷属性失败，无法进行幂等哈希", e);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException("读取载荷属性失败，无法进行幂等哈希", e);
        } finally {
            visiting.remove(bean);
        }
    }

    /**
     * 追加带转义的字符串内容。
     */
    private void appendQuoted(String value, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '\\':
                    sb.append("\\\\");
                    break;
                case '"':
                    sb.append("\\\"");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    sb.append(ch);
                    break;
            }
        }
        sb.append('"');
    }

    private static final class MapEntry {
        /** 幂等键。 */
        private final String key;
        /** 取值。 */
        private final Object value;

        private MapEntry(String key, Object value) {
            this.key = key;
            this.value = value;
        }

        private String getKey() {
            return key;
        }

        private Object getValue() {
            return value;
        }
    }
}
