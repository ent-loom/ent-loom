# MySQL 8 验收

## 范围

使用 JDK 21、Maven Wrapper 与真实 MySQL 8，复用现有业务用例；无需新增依赖。下单用例通过 MockMvc 进入实际业务事务，双端分页通过随机端口发送真实 HTTP 请求，均不使用测试外层事务。

| 验收项 | 检查内容 |
| --- | --- |
| 自动建表 | 空库生成统一的 `order`、`order_item` 和 `merchant_product`；字段匹配实体，不创建旧 `merchant_order` |
| 存储结构 | InnoDB、自增主键、十进制金额、客户与商家归属索引、明细关联索引 |
| 跨商家拆单 | 同商家合并、跨商家独立订单、明细归属及订单合计一致 |
| 成交快照 | 商品改名、改价后保留原名称与单价；`0.10 × 3 + 19.99 × 2 = 40.28` 精确保存 |
| 事务回滚 | 第二个商家的订单或明细写入失败，整批订单与明细全部回滚 |
| 双身份隔离 | 客户与商家范围、筛选交集、分页总数、稳定排序、无效令牌、伪造归属字段与请求头 |

## 执行

先按 README 将当前工作区框架构件安装至本地 Maven 仓库。以下命令在示例目录执行，使用独立空库；测试会删除验收库中的客户、商家商品、订单和明细数据。

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

../../mvnw -Dtest=MysqlSchemaAcceptanceTest,PlaceOrderIntegrationTest,OrderPageIntegrationTest test
```

`COMMERCE_TEST_JDBC_*` 仅控制上述验收用例的数据源，不读取开发 `.env` 中的数据库配置。未设置时，下单与双端分页仍使用各自的 H2 库，MySQL 表结构检查自动跳过。上述用例须串行执行，不配置并行测试。

验收结束后清理专用实例，并取消测试连接环境变量：

```bash
docker stop mini-commerce-mysql-acceptance
unset COMMERCE_TEST_JDBC_URL COMMERCE_TEST_JDBC_USER COMMERCE_TEST_JDBC_PASSWORD
```
