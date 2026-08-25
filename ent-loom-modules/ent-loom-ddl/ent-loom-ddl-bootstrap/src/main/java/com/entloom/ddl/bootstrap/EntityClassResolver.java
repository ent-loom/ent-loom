package com.entloom.ddl.bootstrap;

import java.util.List;

/**
 * 实体类解析器。
 *
 * <p>实现应将空输入解析为空列表，并返回去重、稳定排序后的可加载实体类。
 * 不可加载的类应跳过，不应阻断同一输入中的其他类。</p>
 */
public interface EntityClassResolver {
    List<Class<?>> resolve(List<String> basePackages);
}
