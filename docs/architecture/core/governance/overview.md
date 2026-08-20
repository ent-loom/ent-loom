# 权限治理架构

> 状态：Current

治理是内置 Gateway 的强制边界：先解析主体、权限和数据范围，再进入路由或执行器。框架提供
fail-closed 默认实现，业务通常需要替换真实主体、权限、范围和审计 SPI。

推荐阅读：

1. [治理 Pipeline](pipeline.md)：跨模块必须遵守的阶段、fail-closed 和扩展边界。
2. [治理 Core 实现](core-architecture.md)：默认实现、范围交集、保留键与审计语义。
3. [执行上下文与主体](../subject/context.md)：同步上下文和异步快照边界。
4. [业务接入模板](../../../guides/crud/integration-template.md)：业务实现和装配方式。

默认 SQL 如何应用最终治理范围见 [Default Engine](../../components/crud/default-engine.md)。
