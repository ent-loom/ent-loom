package com.entloom.crud.core.runtime.router;

import com.entloom.crud.core.capability.command.handler.CommandActionContract;
import java.util.List;

/**
 * ACTION 场景契约解析器。
 */
public interface CommandActionSceneResolver {
    /**
     * 将入参 scene 解析为已注册路由的 canonical scene。
     */
    String canonicalizeActionScene(Class<?> rootType, List<Class<?>> entityClasses, String scene);

    /**
     * 解析 ACTION 路由契约。
     */
    CommandActionContract resolveActionContract(Class<?> rootType, List<Class<?>> entityClasses, String scene);
}

