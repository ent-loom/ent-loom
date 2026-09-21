# 开发环境与 JDK 管理

> 状态：Current

完整 Maven Reactor 固定使用 JDK 21+。`.java-version` 只提示本地版本，不会替终端、IDE 或 CI 自动切换 JDK；兼容路线见
[Java 运行时与 Spring 兼容性](../evolution/decisions/core/Java运行时与Spring兼容性.md)。

## 验证当前环境

```bash
java -version
./mvnw -version
```

两个命令都应显示 Java 21 或更高版本。Windows 使用 `.\mvnw.cmd -version`；在 IDEA 中将 Project SDK、Maven Importer 和 Runner JDK 设为同一个 JDK 21。

Maven Wrapper 提供项目要求的 Maven 版本；POM 和 Enforcer 负责编译目标与 JDK 下限。若 `java` 与 Maven 显示的 JDK 不一致，请检查 `JAVA_HOME`、IDEA 的 Maven Runner 和终端启动脚本。

## 构建项目

```bash
./mvnw -B test
```

Windows：

```powershell
.\mvnw.cmd -B test
```
