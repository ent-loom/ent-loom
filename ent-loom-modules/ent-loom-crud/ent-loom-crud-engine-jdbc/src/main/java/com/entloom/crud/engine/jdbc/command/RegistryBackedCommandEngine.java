package com.entloom.crud.engine.jdbc.command;

import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.core.capability.command.engine.CommandEngine;
import com.entloom.crud.core.runtime.engine.EngineCapability;
import com.entloom.crud.core.runtime.engine.EngineFeature;
import com.entloom.crud.core.exception.RouteNotFoundException;
import com.entloom.crud.core.capability.command.handler.CrudCommandHandler;
import com.entloom.crud.core.security.SqlSecurityGuard;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import java.util.Objects;

/**
 * 基于命令注册表分发的默认命令引擎实现。
 */
public class RegistryBackedCommandEngine implements CommandEngine {
    /** JDBC 默认命令引擎能力声明。 */
    private static final EngineCapability CAPABILITY = EngineCapability.builder("jdbc-command-engine")
        .operations(
            CommandOperation.CREATE,
            CommandOperation.UPDATE,
            CommandOperation.DELETE,
            CommandOperation.SAVE_OR_UPDATE,
            CommandOperation.CREATE_BATCH,
            CommandOperation.UPDATE_BATCH,
            CommandOperation.DELETE_BATCH,
            CommandOperation.SAVE_OR_UPDATE_BATCH
        )
        .features(
            EngineFeature.ID_TARGET_WRITE,
            EngineFeature.TARGET_FILTER_WRITE,
            EngineFeature.BATCH_COMMAND,
            EngineFeature.GOVERNANCE_SCOPE,
            EngineFeature.LOGIC_DELETE
        )
        .build();

    /** 命令注册表。 */
    private final CrudCommandRegistry commandRegistry;
    /** SQL 编译前安全守卫。 */
    private final SqlSecurityGuard sqlSecurityGuard;

    public RegistryBackedCommandEngine(CrudCommandRegistry commandRegistry, SqlSecurityGuard sqlSecurityGuard) {
        this.commandRegistry = Objects.requireNonNull(commandRegistry, "commandRegistry 不能为空");
        this.sqlSecurityGuard = Objects.requireNonNull(sqlSecurityGuard, "sqlSecurityGuard 不能为空");
    }

    @Override
    public EngineCapability capability() {
        return CAPABILITY;
    }

    @Override
    public <P, R> R action(CommandSpec<P> spec) {
        if (spec.getOp() == CommandOperation.ACTION) {
            throw new RouteNotFoundException("执行操作必须由自定义命令处理器处理");
        }
        capability().requireOperation(spec.getOperationKey());
        sqlSecurityGuard.beforeCompile(spec);
        CrudCommandHandler<P, R> handler = commandRegistry.resolve(spec.getRootType());
        if (handler == null) {
            throw new RouteNotFoundException("未找到根类型对应的默认命令处理器: " + spec.getRootType().getName());
        }
        return handler.action(spec);
    }
}
