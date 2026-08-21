# 开发环境与 JDK 管理

> 状态：Current

本机采用“全局 JDK 8、ent-loom 项目 JDK 21”。全局版本是本机策略，不限制其他开发者。兼容边界见
[Java 运行时与 Spring 兼容性版本线](../evolution/decisions/core/Java运行时与Spring兼容性.md)。

## jenv 配置

```bash
brew install jenv
```

将以下内容加入 `~/.zshrc`，不要固定写死 `JAVA_HOME`：

```bash
export PATH="$HOME/.jenv/bin:$PATH"
eval "$(jenv init -)"
```

首次配置后执行：

```bash
source ~/.zshrc
jenv enable-plugin export
source ~/.zshrc
jenv add "$(/usr/libexec/java_home -v 1.8)"
jenv add "$(/usr/libexec/java_home -v 21)"
jenv global 1.8
```

ent-loom 根目录已提交 `.java-version=21`，无需重复设置；其他 Java 8 项目不设置本地版本文件即可使用全局 JDK 8。

## 验证与职责

```bash
# Java 8 项目
cd /path/to/java8-project
java -version
mvn -version

# ent-loom
cd /path/to/ent-loom
java -version
./mvnw -version
./mvnw test
```

| 配置 | 职责 |
|---|---|
| `.java-version` / jenv | 按目录选择 JDK |
| `mvnw` / `mvnw.cmd` | 固定 Maven 3.9.12 |
| `pom.xml` / Enforcer | 编译目标与 JDK 下限 |
| IDEA Project SDK、Maven Runner | IDE 使用的 JDK |

若 `java` 与 Maven 版本不一致，检查 jenv `export` 插件和 `~/.zshrc` 中是否仍有固定的 `JAVA_HOME`。
