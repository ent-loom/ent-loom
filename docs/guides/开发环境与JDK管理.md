# 开发环境与 JDK 管理

> 状态：Current

本机策略：全局 JDK 8，ent-loom 项目 JDK 21。全局版本不限制其他开发者。兼容边界见
[Java 运行时与 Spring 兼容性版本线](../evolution/decisions/core/Java运行时与Spring兼容性.md)。

## 一次配置

```bash
brew install jenv
# 将以下两行加入 ~/.zshrc，不要固定写死 JAVA_HOME
export PATH="$HOME/.jenv/bin:$PATH"
eval "$(jenv init -)"
source ~/.zshrc
jenv enable-plugin export
source ~/.zshrc
jenv add "$(/usr/libexec/java_home -v 1.8)"
jenv add "$(/usr/libexec/java_home -v 21)"
jenv global 1.8
```

ent-loom 已提交 `.java-version=21`，进入项目目录后自动使用 JDK 21；其他 Java 8 项目使用全局 JDK 8。

## 验证

```bash
cd /path/to/java8-project && java -version && mvn -version
cd /path/to/ent-loom && java -version && ./mvnw -version && ./mvnw test
```

`.java-version` / jenv 选择 JDK；Maven Wrapper 固定 Maven 3.9.12；POM/Enforcer 约束编译目标和 JDK 下限；IDEA 为每个项目单独设置 SDK、Maven Importer 和 Runner JDK。

若 `java` 与 Maven 版本不一致，检查 jenv `export` 插件和 `~/.zshrc` 中是否仍有固定的 `JAVA_HOME`。
