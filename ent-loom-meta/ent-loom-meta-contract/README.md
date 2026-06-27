# ent-loom-meta-contract

跨模块通用 Meta 契约模块。

本模块只放解析后的 Descriptor 接口、共享枚举和后续 SPI，不放具体注解。子框架可以依赖本模块获得统一术语和接口，而不需要强依赖 `ent-loom-meta-annotations`。
