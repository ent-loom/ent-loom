# MySQL 8 验收

## 范围

使用 JDK 21、Maven Wrapper 与 MySQL 8.0.16+，复用现有集成测试；下单进入实际业务事务，分页使用真实 HTTP。

订单业务流程使用 MyBatis-Plus 与授权 DAO；文章自定义 Controller、商品通用 Controller 均由 ent-loom 内核执行 CRUD。验收覆盖自定义与通用 HTTP 接入的权限、校验及事务边界。

| 验收项 | 检查内容 |
| --- | --- |
| 自动建表 | 按实体生成 `commerce_order`、`commerce_order_item` 和 `merchant_product` 等表 |
| 存储结构 | InnoDB、自增主键、十进制金额、客户与商家归属索引、明细关联索引 |
| 跨商家拆单 | 同商家合并、跨商家独立订单、明细归属及订单合计一致 |
| 成交快照 | 商品改名、改价后保留原名称与单价；`0.10 × 3 + 19.99 × 2 = 40.28` 精确保存 |
| 事务回滚 | 第二个商家的订单或明细写入失败，整批订单与明细全部回滚 |
| 访问控制 | 公共商品白名单、客户与商家隔离、筛选与分页、无效令牌及伪造归属拒绝 |
| 自定义文章 Controller | 内核分页与增改删、商家范围、归属填充、实体校验、通用入口关闭及审计 |
| 商品维护与订单取消 | 字段保护、乐观锁、批量更新回滚、所属 CREATED 订单取消及动作审计 |

## 执行

先按 [README](../README.md) 将当前工作区框架构件安装至本地 Maven 仓库。以下命令在示例目录执行，使用独立空库；测试会删除验收库中的客户、商家商品、订单和明细数据。

可用 Docker 启动专用实例，也可自行准备 MySQL 8.0.16+ 空库及有该库 DDL、DML、TRIGGER 权限的账号（回滚用例需要执行 CHECK 约束）：

```bash
docker run --rm -d --name mini-commerce-mysql-acceptance \
  -p 127.0.0.1:3309:3306 \
  -e MYSQL_DATABASE=mini_commerce_acceptance \
  -e MYSQL_USER=commerce_acceptance \
  -e MYSQL_PASSWORD=acceptance_only \
  -e MYSQL_ROOT_PASSWORD=acceptance_root_only \
  mysql:8.4
```

确认初始化完成后执行测试；启动期间可重复运行以下探针，返回 `1` 即可继续：

```bash
docker exec -e MYSQL_PWD=acceptance_only mini-commerce-mysql-acceptance \
  mysql -ucommerce_acceptance mini_commerce_acceptance -Nse 'select 1'

export COMMERCE_TEST_JDBC_URL='jdbc:mysql://127.0.0.1:3309/mini_commerce_acceptance?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai'
export COMMERCE_TEST_JDBC_USER=commerce_acceptance
export COMMERCE_TEST_JDBC_PASSWORD=acceptance_only
export COMMERCE_TEST_DDL_MODE=CREATE_MODIFY_DELETE_ALL

../../mvnw -Dtest=MysqlSchemaAcceptanceTest,PlaceOrderIntegrationTest,CommerceOrderPageIntegrationTest,CommerceOrderAccessTest,MerchantProductUpdateIntegrationTest,MerchantArticleIntegrationTest test
```

`COMMERCE_TEST_JDBC_*` 仅控制上述验收用例的数据源，不读取开发 `.env` 中的数据库配置。未设置时，业务用例使用 H2，MySQL 表结构检查自动跳过。上述用例须串行执行，不配置并行测试。

验收结束后清理专用实例，并取消测试连接环境变量：

```bash
docker stop mini-commerce-mysql-acceptance
unset COMMERCE_TEST_JDBC_URL COMMERCE_TEST_JDBC_USER COMMERCE_TEST_JDBC_PASSWORD COMMERCE_TEST_DDL_MODE
```
