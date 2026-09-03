package com.entloom.crud.runtime.adapter;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 适配器边界上的属性编码工具。
 *
 * <p>runtime 属性只有字符串值，因此这里使用带版本和类型标记的轻量协议。未带协议前缀
 * 的历史值仍按字符串读取，便于已有任务逐步迁移。列表、数组和常见 Java 时间类型均使用
 * 长度分隔的递归编码，不能双向转换的时间类型在编码端直接拒绝。</p>
 */
final class RuntimeAttributeCodec {
    private static final String VERSION = "v1:";
    private static final String NULL = VERSION + "null:";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private RuntimeAttributeCodec() {
    }

    static Map<String, String> toRuntimeAttributes(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> target = new LinkedHashMap<String, String>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            target.put(entry.getKey(), encode(entry.getValue()));
        }
        return target;
    }

    static Map<String, Object> toCrudAttributes(Map<String, String> source) {
        return toCrudAttributes(source, defaultClassLoader());
    }

    static Map<String, Object> toCrudAttributes(Map<String, String> source, ClassLoader classLoader) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Object> target = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, String> entry : source.entrySet()) {
            if (entry.getKey() != null) {
                target.put(entry.getKey(), decode(entry.getValue(), classLoader));
            }
        }
        return target;
    }

    static String encode(Object value) {
        if (value == null) {
            return NULL;
        }
        if (value instanceof String || value instanceof CharSequence) {
            return VERSION + "string:" + encodeText(text(value));
        }
        if (value instanceof Boolean) {
            return VERSION + "boolean:" + value;
        }
        if (value instanceof Byte) {
            return VERSION + "byte:" + value;
        }
        if (value instanceof Short) {
            return VERSION + "short:" + value;
        }
        if (value instanceof Integer) {
            return VERSION + "integer:" + value;
        }
        if (value instanceof Long) {
            return VERSION + "long:" + value;
        }
        if (value instanceof Float) {
            return VERSION + "float:" + value;
        }
        if (value instanceof Double) {
            return VERSION + "double:" + value;
        }
        if (value instanceof BigInteger) {
            return VERSION + "bigInteger:" + value;
        }
        if (value instanceof BigDecimal) {
            return VERSION + "bigDecimal:" + value;
        }
        if (value instanceof Character) {
            return VERSION + "char:" + encodeText(text(value));
        }
        if (value instanceof Enum<?>) {
            Enum<?> item = (Enum<?>) value;
            return VERSION + "enum:" + encodeText(item.getDeclaringClass().getName())
                + ":" + encodeText(item.name());
        }
        if (value instanceof TemporalAccessor) {
            ensureSupportedTemporalType(value);
            return VERSION + "temporal:" + encodeText(value.getClass().getName())
                + ":" + encodeText(value.toString());
        }
        if (value instanceof List<?>) {
            return encodeList((List<?>) value);
        }
        if (value.getClass().isArray()) {
            return encodeArray(value);
        }
        throw new IllegalArgumentException("不支持的运行时属性类型: " + value.getClass().getName());
    }

    static Object decode(String value) {
        return decode(value, defaultClassLoader());
    }

    static Object decode(String value, ClassLoader classLoader) {
        if (value == null || !value.startsWith(VERSION)) {
            return value;
        }
        if (NULL.equals(value)) {
            return null;
        }
        String[] parts = value.split(":", 3);
        if (parts.length < 3 || !"v1".equals(parts[0])) {
            throw new IllegalArgumentException("运行时属性编码格式无效: " + value);
        }
        String type = parts[1];
        String payload = parts[2];
        try {
            switch (type) {
                case "string":
                    return decodeText(payload);
                case "boolean":
                    return Boolean.valueOf(payload);
                case "byte":
                    return Byte.valueOf(payload);
                case "short":
                    return Short.valueOf(payload);
                case "integer":
                    return Integer.valueOf(payload);
                case "long":
                    return Long.valueOf(payload);
                case "float":
                    return Float.valueOf(payload);
                case "double":
                    return Double.valueOf(payload);
                case "bigInteger":
                    return new BigInteger(payload);
                case "bigDecimal":
                    return new BigDecimal(payload);
                case "char":
                    String character = decodeText(payload);
                    if (character.length() != 1) {
                        throw new IllegalArgumentException("字符编码长度不是 1");
                    }
                    return Character.valueOf(character.charAt(0));
                case "enum":
                    return decodeEnum(payload, classLoader);
                case "temporal":
                    return decodeTemporal(payload, classLoader);
                case "list":
                    return decodeList(payload, classLoader);
                case "array":
                    return decodeArray(payload, classLoader);
                default:
                    throw new IllegalArgumentException("未知运行时属性类型: " + type);
            }
        } catch (ClassNotFoundException ex) {
            throw new IllegalArgumentException("运行时属性类型无法加载: " + value, ex);
        } catch (RuntimeException ex) {
            if (ex instanceof IllegalArgumentException
                && ex.getMessage() != null
                && ex.getMessage().startsWith("运行时属性")) {
                throw ex;
            }
            throw new IllegalArgumentException("运行时属性解码失败: " + value, ex);
        }
    }

    private static String encodeList(List<?> source) {
        StringBuilder builder = new StringBuilder(VERSION).append("list:");
        builder.append(source.size()).append(':');
        appendItems(builder, source);
        return builder.toString();
    }

    private static String encodeArray(Object source) {
        int length = Array.getLength(source);
        StringBuilder builder = new StringBuilder(VERSION)
            .append("array:")
            .append(encodeText(source.getClass().getComponentType().getName()))
            .append(':')
            .append(length)
            .append(':');
        for (int i = 0; i < length; i++) {
            appendItem(builder, encode(Array.get(source, i)));
        }
        return builder.toString();
    }

    private static void appendItems(StringBuilder builder, List<?> source) {
        for (Object item : source) {
            appendItem(builder, encode(item));
        }
    }

    private static void appendItem(StringBuilder builder, String encoded) {
        builder.append(encoded.length()).append(':').append(encoded);
    }

    private static List<Object> decodeList(String payload, ClassLoader classLoader) {
        int separator = payload.indexOf(':');
        if (separator < 0) {
            throw new IllegalArgumentException("列表编码缺少元素数量");
        }
        int count = parseCount(payload.substring(0, separator));
        int offset = separator + 1;
        List<Object> result = new ArrayList<Object>(count);
        for (int i = 0; i < count; i++) {
            EncodedItem item = readItem(payload, offset);
            result.add(decode(item.value, classLoader));
            offset = item.nextOffset;
        }
        requireEnd(payload, offset);
        return result;
    }

    private static Object decodeArray(String payload, ClassLoader classLoader) throws ClassNotFoundException {
        int typeSeparator = payload.indexOf(':');
        if (typeSeparator < 1) {
            throw new IllegalArgumentException("数组编码缺少组件类型");
        }
        Class<?> componentType = primitiveOrClass(decodeText(payload.substring(0, typeSeparator)), classLoader);
        int countSeparator = payload.indexOf(':', typeSeparator + 1);
        if (countSeparator < 0) {
            throw new IllegalArgumentException("数组编码缺少元素数量");
        }
        int count = parseCount(payload.substring(typeSeparator + 1, countSeparator));
        int offset = countSeparator + 1;
        Object result = Array.newInstance(componentType, count);
        for (int i = 0; i < count; i++) {
            EncodedItem item = readItem(payload, offset);
            Array.set(result, i, decode(item.value, classLoader));
            offset = item.nextOffset;
        }
        requireEnd(payload, offset);
        return result;
    }

    private static EncodedItem readItem(String payload, int offset) {
        int separator = payload.indexOf(':', offset);
        if (separator < 0) {
            throw new IllegalArgumentException("集合编码缺少元素长度");
        }
        int length = parseCount(payload.substring(offset, separator));
        int valueStart = separator + 1;
        int valueEnd = valueStart + length;
        if (valueEnd > payload.length()) {
            throw new IllegalArgumentException("集合编码元素长度非法");
        }
        return new EncodedItem(payload.substring(valueStart, valueEnd), valueEnd);
    }

    private static int parseCount(String value) {
        try {
            int count = Integer.parseInt(value);
            if (count < 0) {
                throw new IllegalArgumentException("集合编码数量不能小于 0");
            }
            return count;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("集合编码数量非法: " + value, ex);
        }
    }

    private static void requireEnd(String payload, int offset) {
        if (offset != payload.length()) {
            throw new IllegalArgumentException("集合编码包含多余内容");
        }
    }

    private static Object decodeEnum(String payload, ClassLoader classLoader) throws ClassNotFoundException {
        String[] parts = payload.split(":", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("枚举编码格式无效");
        }
        Class<?> type = Class.forName(decodeText(parts[0]), false, effectiveClassLoader(classLoader));
        if (!type.isEnum()) {
            throw new IllegalArgumentException("编码类型不是枚举: " + type.getName());
        }
        @SuppressWarnings({"rawtypes", "unchecked"})
        Object result = Enum.valueOf((Class<? extends Enum>) type, decodeText(parts[1]));
        return result;
    }

    private static Object decodeTemporal(String payload, ClassLoader classLoader) throws ClassNotFoundException {
        String[] parts = payload.split(":", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("时间类型编码格式无效");
        }
        String type = decodeText(parts[0]);
        String text = decodeText(parts[1]);
        Class.forName(type, false, effectiveClassLoader(classLoader));
        if (Instant.class.getName().equals(type)) {
            return Instant.parse(text);
        }
        if (LocalDate.class.getName().equals(type)) {
            return LocalDate.parse(text);
        }
        if (LocalDateTime.class.getName().equals(type)) {
            return LocalDateTime.parse(text);
        }
        if (LocalTime.class.getName().equals(type)) {
            return LocalTime.parse(text);
        }
        if (OffsetDateTime.class.getName().equals(type)) {
            return OffsetDateTime.parse(text);
        }
        if (OffsetTime.class.getName().equals(type)) {
            return OffsetTime.parse(text);
        }
        if (ZonedDateTime.class.getName().equals(type)) {
            return ZonedDateTime.parse(text);
        }
        if (Year.class.getName().equals(type)) {
            return Year.parse(text);
        }
        if (YearMonth.class.getName().equals(type)) {
            return YearMonth.parse(text);
        }
        throw new IllegalArgumentException("不支持的时间属性类型: " + type);
    }

    private static void ensureSupportedTemporalType(Object value) {
        Class<?> type = value.getClass();
        if (type != Instant.class
            && type != LocalDate.class
            && type != LocalDateTime.class
            && type != LocalTime.class
            && type != OffsetDateTime.class
            && type != OffsetTime.class
            && type != ZonedDateTime.class
            && type != Year.class
            && type != YearMonth.class) {
            throw new IllegalArgumentException("不支持的时间属性类型: " + type.getName());
        }
    }

    private static Class<?> primitiveOrClass(String name, ClassLoader classLoader) throws ClassNotFoundException {
        if ("boolean".equals(name)) {
            return Boolean.TYPE;
        }
        if ("byte".equals(name)) {
            return Byte.TYPE;
        }
        if ("short".equals(name)) {
            return Short.TYPE;
        }
        if ("int".equals(name)) {
            return Integer.TYPE;
        }
        if ("long".equals(name)) {
            return Long.TYPE;
        }
        if ("float".equals(name)) {
            return Float.TYPE;
        }
        if ("double".equals(name)) {
            return Double.TYPE;
        }
        if ("char".equals(name)) {
            return Character.TYPE;
        }
        if ("void".equals(name)) {
            return Void.TYPE;
        }
        return Class.forName(name, false, effectiveClassLoader(classLoader));
    }

    private static String encodeText(String value) {
        return ENCODER.encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeText(String value) {
        return new String(DECODER.decode(value), StandardCharsets.UTF_8);
    }

    private static String text(Object value) {
        return value instanceof String ? (String) value : value.toString();
    }

    private static ClassLoader effectiveClassLoader(ClassLoader classLoader) {
        return classLoader == null ? defaultClassLoader() : classLoader;
    }

    private static ClassLoader defaultClassLoader() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        return loader == null ? RuntimeAttributeCodec.class.getClassLoader() : loader;
    }

    private static final class EncodedItem {
        private final String value;
        private final int nextOffset;

        private EncodedItem(String value, int nextOffset) {
            this.value = value;
            this.nextOffset = nextOffset;
        }
    }
}
