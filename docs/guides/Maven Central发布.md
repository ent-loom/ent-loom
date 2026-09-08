# Maven Central 发布

> 状态：Current
> 最近核验：2026-09-02

本文说明在 IntelliJ IDEA 中发布 ent-loom 系列 Maven 构件的最小闭环。`ent-loom` 和 `ent-runtime` 是两个独立 Git 仓库，哪个仓库有变更就单独发布哪个。

## 一次配置

发布前确认本机已经完成：

- Central 命名空间已验证：`io.github.ent-loom`。
- `/Users/<用户名>/.m2/settings.xml` 已配置 Central User Token，服务器 ID 为 `central`，文件权限为 `600`。
- 本机已有 GPG 签名密钥，公钥已发布到公共密钥服务器。
- `release` profile 固定使用公钥指纹 `C73D6B8B4361B8F0593B0B9CF9814D6512123D10`；该公钥必须能从 Central 支持的 PGP 服务器查询到。
- Token、GPG 私钥和 GPG 口令只保存在本机，不提交到 Git。

## IDEA 设置

在 `Settings -> Build Tools -> Maven` 中确认：

- Maven 使用项目自带的 Maven Wrapper。
- Maven Importer JDK 使用 JDK 21。
- Maven Runner JDK 使用 JDK 21。

发布命令建议在 IDEA Terminal 中执行，并从对应仓库根目录运行。

## 发布流程

### 1. 升级版本

已发布版本不可覆盖，先将仓库内版本统一升级。例如：

```bash
# ent-loom
./mvnw versions:set -DnewVersion=1.0.1 -DgenerateBackupPoms=false

# ent-runtime
./mvnw versions:set -DnewVersion=0.1.1 -DgenerateBackupPoms=false
```

执行后检查所有模块的 parent、dependency 和项目版本是否一致。

### 2. 本地验证

```bash
./mvnw clean verify
```

`ent-loom-runtime-adapter` 是默认关闭的可选模块。需要验证它时，必须使用 JDK 21 并显式启用 profile；其 `ent-runtime` 依赖版本须已发布到 Maven Central，或已安装到本机 Maven 仓库：

```bash
./mvnw -Pruntime-adapter -pl ent-loom-integrations/ent-loom-runtime-adapter -am clean verify
```

确认测试通过，再检查 `git diff` 和 `git status`，确保没有 Token、密码、私钥、`settings.xml` 或 `target/` 被纳入提交。

### 3. 提交代码

```bash
git add -A
git commit -m "release: ent-loom 1.0.1"
```

`ent-runtime` 使用对应的版本和提交信息。建议在发布前创建本地 Tag：

```bash
# ent-loom
git tag ent-loom-v1.0.1

# ent-runtime
git tag ent-runtime-v0.1.1
```

Tag 不得覆盖已有版本。

### 4. 签名并上传

```bash
./mvnw clean deploy -Prelease
```

发布 `ent-loom-runtime-adapter` 时，在同一命令中启用两个 profile：

```bash
./mvnw clean deploy -Prelease,runtime-adapter
```

命令会生成源码包、Javadoc、GPG 签名并上传到 Central。`release` profile 不依赖本机默认密钥，而是使用已登记的 `gpg.keyname`；其他发布者可通过 `-Dgpg.keyname=<已登记的公钥指纹>` 覆盖。GPG 需要口令时，只在 IDEA Terminal 本地输入，不发送给任何人。

### 5. Central 页面确认发布

打开 [Central Deployments](https://central.sonatype.com/publishing/deployments)，找到刚上传的部署：

1. 确认状态为 `VALIDATED`，并核对版本和组件数量。
2. 点击 `Publish`。
3. 勾选“该版本发布后不可删除”的确认项。
4. 再点击 `Publish`。

当前配置是手动确认发布，因此每次上传都需要执行这一步。提交后状态会变为 `PUBLISHING`，不要重复点击发布。

### 6. 验证并推送

等待状态变为 `PUBLISHED` 后，再推送提交和 Tag：

```bash
git push origin main
git push origin ent-loom-v1.0.1
```

`ent-runtime` 推送其对应 Tag。也可以在 Central Search 或 Maven Central 文件目录中验证构件；`mvnrepository.com` 是第三方索引，更新可能晚于 Central 几小时到 1～2 天。

## 状态说明

| 状态 | 含义 | 操作 |
|---|---|---|
| `VALIDATED` | 校验通过，可以发布 | 点击 `Publish` 并确认 |
| `PUBLISHING` | 正在同步到中央仓库 | 等待并刷新，不重复发布 |
| `PUBLISHED` | 发布完成 | 验证依赖并推送代码、Tag |
| `FAILED` | 校验或发布失败 | 查看失败原因，修复后使用新部署 |

## 两个仓库

| 仓库 | 根目录 | 本次目标发布版本 |
|---|---|---|
| `ent-loom` | `/Users/<用户名>/IdeaProjects/ent-workspace/ent-loom` | `1.0.1` |
| `ent-runtime` | `/Users/<用户名>/IdeaProjects/ent-workspace/ent-runtime` | `0.1.1` |

每次只在发生变更的仓库中执行版本升级、验证、上传和 Central 发布。两个仓库同时变更时，分别完成各自的流程。
