package com.entloom.crud.core.util;

/**
 * 命名转换工具。
 */
public final class NamingUtils {
    private NamingUtils() {
    }

    /**
     * 将驼峰转为下划线命名。
     *
     * @param input 输入文本
     * @return 下划线命名
     */
    /**
     * 将驼峰命名转换为下划线命名。
     */
    public static String camelToSnake(String input) {
        if (input == null || input.trim().isEmpty()) {
            return input;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);
            if (Character.isUpperCase(ch)) {
                if (i > 0) {
                    sb.append('_');
                }
                sb.append(Character.toLowerCase(ch));
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    /**
     * 将下划线命名转为驼峰命名。
     *
     * @param input 输入文本
     * @return 驼峰命名
     */
    /**
     * 将下划线命名转换为驼峰命名。
     */
    public static String snakeToCamel(String input) {
        if (input == null || input.trim().isEmpty() || input.indexOf('_') < 0) {
            return input;
        }
        StringBuilder sb = new StringBuilder();
        boolean upperNext = false;
        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);
            if (ch == '_') {
                upperNext = sb.length() > 0;
                continue;
            }
            if (sb.length() == 0) {
                sb.append(Character.toLowerCase(ch));
                upperNext = false;
                continue;
            }
            if (upperNext) {
                sb.append(Character.toUpperCase(ch));
                upperNext = false;
            } else {
                sb.append(Character.toLowerCase(ch));
            }
        }
        return sb.toString();
    }
}
