package com.entloom.crud.engine.jdbc.command;

import com.entloom.crud.core.capability.command.handler.CrudCommandHandler;
import java.util.HashMap;
import java.util.Map;
import lombok.Setter;

/**
 * 默认 CRUD 命令处理器注册表。
 */
@Setter
public class CrudCommandRegistry {
    /** 处理器映射。 */
    private final Map<Class<?>, CrudCommandHandler<?, ?>> handlers = new HashMap<>();
    /** 默认处理器。 */
    private CrudCommandHandler<?, ?> defaultHandler;

    /**
     * 注册默认处理器。
     *
     * @param rootType 根实体类型
     * @param handler 处理器
     */
    public void register(Class<?> rootType, CrudCommandHandler<?, ?> handler) {
        handlers.put(rootType, handler);
    }

    /**
     * 按根实体解析处理器。
     *
     * @param rootType 根实体类型
     * @param <P> 入参类型
     * @param <R> 返回类型
     * @return 处理器
     */
    @SuppressWarnings("unchecked")
    public <P, R> CrudCommandHandler<P, R> resolve(Class<?> rootType) {
        CrudCommandHandler<?, ?> handler = handlers.get(rootType);
        return (CrudCommandHandler<P, R>) (handler == null ? defaultHandler : handler);
    }
}
