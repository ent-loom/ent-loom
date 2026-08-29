# AGENTS.md

- 使用中文沟通, 简洁陈列风格.
- 目标: 做业务友好型系列框架.面相实体编程框架.
- 技术栈: Java 21 主线；Core/Boot 2 目标 Java 8；Boot 3/4 目标 Java 17；Maven, Spring Boot, MySQL 8
- JDK策略: 主开发与完整 Maven Reactor 固定使用 JDK 21；当前完整仓库支持口径为 JDK 21+。
- 兼容路线: 框架无关 Core 与独立 Boot 2 兼容线目标为 Java 8，Boot 3/4 集成线目标为 Java 17；通过独立模块/构件验证，不降低根 POM。
- 版本依据: Maven Wrapper 和 Maven Enforcer 负责构建约束；`.java-version` 仅作本地 JDK 21 提示；兼容边界见 `docs/evolution/decisions/core/Java运行时与Spring兼容性.md`。
- 开发过程要求项:
  - 实现层
      - 文档/备注/日志 默认中文为主;
      - 做选择时,默认按 "先较小闭环,再较佳实践" 选择;
      - 状态/类型优先考虑 enum枚举类;枚举类中每个项独立一行,默认携带中文备注名称;
      - 新增实体(以及字段),要有充足的中文注释,枚举型要有link枚举注释;
  - 测试层 - 低风险且局部的少量改动可不新增测试.
