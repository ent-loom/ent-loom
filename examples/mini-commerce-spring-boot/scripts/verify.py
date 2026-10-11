#!/usr/bin/env python3
"""在独立 MySQL 环境中验收商城业务路径。"""

import argparse
import json
import os
from pathlib import Path
import re
import shutil
import signal
import subprocess
import sys
import time
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen
import uuid
from decimal import Decimal

EXAMPLE = Path(__file__).resolve().parents[1]


def check(condition, message):
    if not condition:
        raise RuntimeError(message)


def run(command, env, **kwargs):
    return subprocess.run(command, cwd=EXAMPLE, env=env, check=True, **kwargs)


def request(url, body=None, method=None, headers=None):
    data = None if body is None else json.dumps(body).encode()
    req = Request(url, data=data, method=method or ("POST" if body is not None else "GET"),
                  headers={"Content-Type": "application/json", **(headers or {})})
    try:
        with urlopen(req, timeout=5) as response:
            return response.status, json.load(response)
    except HTTPError as error:
        return error.code, json.load(error)


def crud_create(base_url, entity, payload, request_id):
    status, response = request(base_url + f"/api/ent-crud/{entity}/create", {
        "options": {"requestId": request_id}, "payload": payload,
    })
    check(status == 200 and response.get("success") is True, f"{entity} CREATE 失败：{response}")
    return int(response["data"]["id"])


def crud_update(base_url, entity, payload, request_id):
    status, response = request(base_url + f"/api/ent-crud/{entity}/update", {
        "options": {"requestId": request_id}, "payload": payload,
    })
    check(status == 200 and response.get("success") is True, f"{entity} UPDATE 失败：{response}")


def sql_query(compose, env, sql):
    try:
        return run(compose + ["exec", "-T", "-e", "MYSQL_PWD=mini_commerce_verify", "mysql",
                              "mysql", "-umini_commerce", "-N", "-B", "mini_commerce", "-e", sql],
                   env, capture_output=True, text=True).stdout.strip()
    except subprocess.CalledProcessError as error:
        raise RuntimeError(f"SQL 执行失败：{sql}\n{error.stderr or error.stdout or error}") from error


def verify_merchant(base_url, compose, env, logs):
    """订单验收结束后补种商家数据，逐场景核对独立 SQL 连接看到的提交结果。"""
    seed = (EXAMPLE / "scripts" / "merchant-demo.sql").read_text()
    (logs / "merchant-seed.sql").write_text(seed)
    (logs / "merchant-seed.sql.tsv").write_text(sql_query(compose, env, seed) + "\n")
    headers_a = {"Authorization": "Bearer demo-merchant-a"}
    headers_b = {"Authorization": "Bearer demo-merchant-b"}
    products_url = base_url + "/api/merchant/products"
    snapshot_sql = "select id, product_id, merchant_id, price, version from merchant_product order by id"

    def snapshot(label):
        (logs / f"merchant-{label}.sql").write_text(snapshot_sql + ";\n")
        result = sql_query(compose, env, snapshot_sql)
        (logs / f"merchant-{label}.sql.tsv").write_text(result + "\n")
        return result

    def saved_request(label, url, body=None, headers=None):
        status, response = request(url, body, headers=headers)
        (logs / f"merchant-{label}.json").write_text(
            json.dumps(response, ensure_ascii=False, indent=2))
        return status, response

    def check_prices(result, prices, version):
        rows = [line.split("\t") for line in result.splitlines()]
        expected = [(101, 900010, 1001, Decimal(prices[0]), version),
                    (102, 900020, 1001, Decimal(prices[1]), version),
                    (201, 900010, 2001, Decimal("30"), 0)]
        check(len(rows) == 3 and all(len(row) == 5 for row in rows),
              f"商家报价 SQL 行数或字段不匹配：{result}")
        actual = [(int(row[0]), int(row[1]), int(row[2]), Decimal(row[3]), int(row[4]))
                  for row in rows]
        check(actual == expected, f"商家报价 SQL 价格、版本或归属不匹配：{result}")

    def check_list(label, headers, merchant_id, expected):
        status, response = saved_request(label, products_url, headers=headers)
        items = response.get("data")
        check(status == 200 and response.get("success") is True and isinstance(items, list),
              f"商家读取失败：{status} {response}")
        check(len(items) == len(expected) and {item.get("id") for item in items} == set(expected),
              f"商家报价读取范围不匹配：{response}")
        for item in items:
            product_id, price, version = expected[item["id"]]
            check(item.get("merchantId") == merchant_id and item.get("productId") == product_id
                  and Decimal(str(item.get("price"))) == Decimal(price)
                  and item.get("version") == version, f"商家报价字段不匹配：{item}")

    check_prices(snapshot("initial"), ("10", "20"), 0)
    check_list("a-initial", headers_a, 1001, {101: (900010, "10", 0), 102: (900020, "20", 0)})
    check_list("b-initial", headers_b, 2001, {201: (900010, "30", 0)})
    changes = {"items": [{"id": 101, "price": 11.50, "version": 0},
                         {"id": 102, "price": 22.00, "version": 0}]}
    status, response = saved_request("commit", products_url + "/prices", changes, headers_a)
    committed = snapshot("commit")
    check(status == 200 and response.get("success") is True and response.get("data") == [101, 102],
          f"商家整批改价失败：{status} {response}")
    check_prices(committed, ("11.50", "22.00"), 1)

    for label, second_id, second_version in [("cross-merchant-rollback", 201, 0),
                                             ("missing-rollback", 999999, 0),
                                             ("version-rollback", 102, 0)]:
        status, response = saved_request(label, products_url + "/prices", {
            "items": [{"id": 101, "price": 99, "version": 1},
                      {"id": second_id, "price": 99, "version": second_version}],
        }, headers_a)
        after = snapshot(label)
        check(status == 409 and response.get("code") == "PRICE_UPDATE_REJECTED",
              f"{label} 拒绝响应不匹配：{status} {response}")
        check(after == committed, f"{label} 整批回滚失败，价格、版本或归属变化：{after}")

    # 第一项合法写入，第二项才触发数据库约束错误；MySQL CHECK 不引用自增主键。
    constraint = ("alter table merchant_product add constraint verify_merchant_price_failure "
                  "check (price <> 77)")
    (logs / "merchant-sql-error-constraint.sql").write_text(constraint + ";\n")
    sql_query(compose, env, constraint)
    try:
        status, response = saved_request("sql-error-rollback", products_url + "/prices", {
            "items": [{"id": 101, "price": 66, "version": 1},
                      {"id": 102, "price": 77, "version": 1}],
        }, headers_a)
        after = snapshot("sql-error-rollback")
        check(status == 500, f"商家第二项 SQL 错误未按预期发生：{status} {response}")
        check(after == committed, f"商家第二项 SQL 错误后整批回滚失败：{after}")
    finally:
        sql_query(compose, env, "alter table merchant_product drop check verify_merchant_price_failure")

    for label, headers in [("no-token", None), ("invalid-token", {"Authorization": "Bearer invalid"})]:
        for operation, body in [("read", None), ("write", changes)]:
            url = products_url if body is None else products_url + "/prices"
            status, response = saved_request(f"{label}-{operation}", url, body, headers)
            check(status == 403, f"商家 {label} {operation} 应被拒绝：{status} {response}")
    status, response = saved_request("generic-update-denied", base_url + "/api/ent-crud/merchantProduct/update", {
        "payload": {"id": 101, "price": 1, "version": 1, "merchantId": 2001},
    }, headers_a)
    check(status == 404 and response.get("code") == "ENTITY_NOT_EXPOSED",
          f"商家报价通用更新路由应关闭：{status} {response}")
    check(snapshot("final") == committed, "拒绝无凭证、无效凭证或通用路由更新后报价发生变化")
    check_list("a-final", headers_a, 1001, {101: (900010, "11.50", 1), 102: (900020, "22.00", 1)})
    check_list("b-final", headers_b, 2001, {201: (900010, "30", 0)})


def verify_order_entries(base_url, compose, env, logs):
    """最后补种子订单，独立核对双入口 scope、AND 筛选、分页与拒绝结果。"""
    seed = (EXAMPLE / "scripts" / "order-entry-demo.sql").read_text()
    (logs / "order-entry-seed.sql").write_text(seed)
    (logs / "order-entry-seed.sql.tsv").write_text(sql_query(compose, env, seed) + "\n")
    expected_rows = [
        (501, 9001, 301, 1001, Decimal("10")),
        (502, 9001, 301, 2001, Decimal("20")),
        (503, 9002, 302, 1001, Decimal("30")),
        (504, 9003, 301, 1001, Decimal("40")),
        (505, 9004, 302, 2001, Decimal("50")),
    ]
    select_sql = "select id, order_id, customer_id, merchant_id, total_amount from merchant_order"

    def saved_sql(label, sql):
        (logs / f"order-entry-{label}.sql").write_text(sql + ";\n")
        result = sql_query(compose, env, sql)
        (logs / f"order-entry-{label}.sql.tsv").write_text(result + "\n")
        return result

    def rows(result):
        fields = [line.split("\t") for line in result.splitlines()]
        check(all(len(row) == 5 for row in fields), f"子订单 SQL 字段不匹配：{result}")
        return [tuple(int(value) for value in row[:4]) + (Decimal(row[4]),) for row in fields]

    def snapshot(label):
        result = saved_sql(label, select_sql + " order by id")
        check(rows(result) == expected_rows, f"子订单种子或拒绝后 SQL 数据不匹配：{result}")
        return result

    def saved_request(label, path, body, token, extra_headers=None):
        headers = dict(extra_headers or {})
        if token:
            headers["Authorization"] = "Bearer " + token
        status, response = request(base_url + path, body, headers=headers)
        (logs / f"order-entry-{label}.json").write_text(
            json.dumps(response, ensure_ascii=False, indent=2))
        (logs / f"order-entry-{label}.http.json").write_text(json.dumps({
            "path": path, "request": body, "headers": extra_headers or {}, "status": status,
        }, ensure_ascii=False, indent=2))
        return status, response

    def check_page(label, entry, token, scope, expected_ids, total, body=None, headers=None):
        body = {} if body is None else body
        page_number, page_size = body.get("pageNumber", 1), body.get("pageSize", 20)
        where = " where " + " and ".join(f"{field} = {value}" for field, value in scope)
        status, response = saved_request(label, f"/api/{entry}/orders/page", body, token, headers)
        sql_total = saved_sql(label + "-count", "select count(*) from merchant_order" + where)
        sql_rows = rows(saved_sql(label, select_sql + where + " order by id"
                                  + f" limit {page_size} offset {(page_number - 1) * page_size}"))
        check(sql_total == str(total) and [row[0] for row in sql_rows] == expected_ids,
              f"{label} 子订单独立 SQL scope、筛选或分页结果不匹配：{sql_total} {sql_rows}")
        data = response.get("data")
        check(status == 200 and response.get("success") is True and isinstance(data, dict),
              f"{label} 子订单分页失败：{status} {response}")
        items = data.get("items")
        check(isinstance(items, list) and [item.get("id") for item in items] == expected_ids
              and data.get("total") == total, f"{label} 子订单分页范围或总数不匹配：{response}")
        check(data.get("pageNumber") == page_number and data.get("pageSize") == page_size
              and data.get("page") == page_number and data.get("limit") == page_size,
              f"{label} 子订单页码、大小或别名不匹配：{response}")
        for item, sql_row in zip(items, sql_rows):
            actual = tuple(item.get(field) for field in ("id", "orderId", "customerId", "merchantId"))
            check(actual == sql_row[:4] and Decimal(str(item.get("totalAmount"))) == sql_row[4],
                  f"{label} 子订单 HTTP 与独立 SQL 字段不匹配：{item} {sql_row}")

    initial = snapshot("initial")
    check_page("consumer-a", "consumer", "demo-customer-a", [("customer_id", 301)], [501, 502, 504], 3)
    check_page("consumer-b", "consumer", "demo-customer-b", [("customer_id", 302)], [503, 505], 2)
    check_page("consumer-a-merchant", "consumer", "demo-customer-a",
               [("customer_id", 301), ("merchant_id", 1001)], [501, 504], 2, {"merchantId": 1001})
    check_page("merchant-a", "merchant", "demo-merchant-a", [("merchant_id", 1001)], [501, 503, 504], 3)
    check_page("merchant-b", "merchant", "demo-merchant-b", [("merchant_id", 2001)], [502, 505], 2)
    check_page("merchant-a-customer", "merchant", "demo-merchant-a",
               [("merchant_id", 1001), ("customer_id", 301)], [501, 504], 2, {"customerId": 301})
    for page_number, order_id in [(1, 501), (2, 502)]:
        check_page(f"consumer-a-page-{page_number}", "consumer", "demo-customer-a",
                   [("customer_id", 301)], [order_id], 3, {"pageNumber": page_number, "pageSize": 1})
    check_page("consumer-a-empty", "consumer", "demo-customer-a",
               [("customer_id", 301), ("merchant_id", 999)], [], 0, {"merchantId": 999})

    forged_headers = {
        "X-Customer-Id": "302", "X-Merchant-Id": "2001", "X-Subject-Id": "customer-user-b",
        "X-Access-Entry": "management", "X-Crud-Access-Entry": "management",
    }
    for entry, token, scope, expected_ids, unknown, filter_body in [
        ("consumer", "demo-customer-a", ("customer_id", 301), [501, 502, 504],
         {"customerId": 302}, {"merchantId": 1001}),
        ("merchant", "demo-merchant-a", ("merchant_id", 1001), [501, 503, 504],
         {"merchantId": 2001}, {"customerId": 301}),
    ]:
        check_page(f"{entry}-forged-header", entry, token, [scope], expected_ids, 3, headers=forged_headers)
        filter_scope = ("merchant_id", 1001) if entry == "consumer" else ("customer_id", 301)
        check_page(f"{entry}-forged-header-filter", entry, token, [scope, filter_scope], [501, 504], 2,
                   filter_body, forged_headers)
        for reason, body, headers in [
            ("unknown-owner", unknown, None),
            ("unknown-owner-header", unknown, forged_headers),
            ("unknown-owner-filter", {**unknown, **filter_body}, forged_headers),
        ]:
            label = f"{entry}-{reason}"
            status, response = saved_request(label, f"/api/{entry}/orders/page", body, token, headers)
            after = snapshot(label)
            check(status == 400 and response.get("code") == "VALIDATION_ERROR",
                  f"{label} 子订单未知归属字段应被严格 DTO 拒绝：{status} {response}")
            check(after == initial, f"{label} 拒绝后子订单发生变化")

    for entry, wrong_token in [("consumer", "demo-merchant-a"), ("merchant", "demo-customer-a")]:
        for reason, token in [("cross-entry", wrong_token), ("no-token", None), ("invalid-token", "invalid")]:
            label = f"{entry}-{reason}"
            status, response = saved_request(label, f"/api/{entry}/orders/page", {}, token)
            after = snapshot(label)
            check(status == 403, f"{label} 子订单分页应被拒绝：{status} {response}")
            check(after == initial, f"{label} 拒绝后子订单发生变化")
    status, response = saved_request("generic-page-denied", "/api/ent-crud/merchantOrder/page", {},
                                     "demo-customer-a")
    after = snapshot("generic-page-denied")
    check(status == 404 and response.get("code") == "ENTITY_NOT_EXPOSED",
          f"子订单通用分页路由应关闭：{status} {response}")
    check(after == initial, "通用路由拒绝后子订单发生变化")


def verify(repository=None, skip_build=False):
    project = "ent-loom-commerce-verify-" + uuid.uuid4().hex[:12]
    logs = EXAMPLE / "target" / "verification-logs" / project
    logs.mkdir(parents=True)
    env = os.environ.copy()
    # 验收参数固定且独立，不消费本机 .env 的数据库账号或端口。
    env.update(MYSQL_DATABASE="mini_commerce", MYSQL_USER="mini_commerce",
               MYSQL_PASSWORD="mini_commerce_verify", MYSQL_ROOT_PASSWORD="verify_root",
               MYSQL_PORT="127.0.0.1:0")
    compose = ["docker-compose"] if shutil.which("docker-compose") else ["docker", "compose"]
    compose += ["--project-name", project, "--env-file", str(EXAMPLE / ".env.example"),
                "-f", str(EXAMPLE / "compose.yaml")]
    app = None
    compose_started = False
    failed = False
    app_log = None
    print(f"验收项目：{project}；日志：{logs}", flush=True)
    try:
        if not skip_build:
            wrapper = EXAMPLE.parents[1] / ("mvnw.cmd" if os.name == "nt" else "mvnw")
            maven = str(wrapper) if wrapper.exists() else shutil.which("mvn")
            check(maven, "未找到 Maven Wrapper 或 Maven")
            command = [maven, "-B", "-DskipTests"]
            if repository:
                command.append("-Dmaven.repo.local=" + str(Path(repository).resolve()))
            with (logs / "build.log").open("w") as output:
                run(command + ["package"], env, stdout=output, stderr=subprocess.STDOUT, timeout=900)

        compose_started = True
        with (logs / "compose-start.log").open("w") as output:
            run(compose + ["up", "-d", "--wait", "--wait-timeout", "120", "mysql"],
                env, stdout=output, stderr=subprocess.STDOUT, timeout=300)
        address = run(compose + ["port", "mysql", "3306"], env,
                      capture_output=True, text=True).stdout.strip()
        mysql_port = int(address.rsplit(":", 1)[1])
        jar = EXAMPLE / "target" / "mini-commerce-spring-boot-0.1.0-SNAPSHOT.jar"
        check(jar.exists(), f"未找到应用：{jar}")
        java = str(Path(env["JAVA_HOME"]) / "bin" / "java") if env.get("JAVA_HOME") else "java"
        app_log = (logs / "application.log").open("w")
        app = subprocess.Popen([
            java, "-jar", str(jar), "--spring.profiles.active=",
            "--spring.config.import=", "--server.port=0", "--server.address=127.0.0.1",
            "--management.endpoint.health.probes.enabled=true",
            "--ent.loom.crud.governance.access-entry=consumer",
            f"--spring.datasource.url=jdbc:mysql://127.0.0.1:{mysql_port}/mini_commerce"
            "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai",
            "--spring.datasource.username=mini_commerce",
            "--spring.datasource.password=mini_commerce_verify",
        ], cwd=EXAMPLE, env=env, stdout=app_log, stderr=subprocess.STDOUT)
        deadline = time.monotonic() + 120
        base_url = None
        expected_tables = {"customer", "customer_address", "product", "order", "order_item", "merchant_product", "merchant_poster", "merchant_order"}
        schema_sql = ("select table_name from information_schema.tables where table_schema = 'mini_commerce' "
                      "and table_name in ('customer', 'customer_address', 'product', 'order', 'order_item', 'merchant_product', 'merchant_poster', 'merchant_order') "
                      "order by table_name")
        (logs / "schema-readiness.sql").write_text(schema_sql + ";\n")
        while time.monotonic() < deadline:
            check(app.poll() is None, "应用在就绪前退出")
            content = (logs / "application.log").read_text(errors="replace")
            match = re.search(r"Tomcat started on port (\d+)", content)
            if match:
                base_url = f"http://127.0.0.1:{match.group(1)}"
                try:
                    # Tomcat 可在 ContextRefreshedEvent 的 DDL 完成前接收 HTTP 请求；
                    # readiness 由 ApplicationReadyEvent 切换，再检查框架创建的业务表。
                    status, readiness = request(base_url + "/actuator/health/readiness")
                    (logs / "application-readiness.json").write_text(
                        json.dumps(readiness, ensure_ascii=False, indent=2))
                    if status == 200 and readiness.get("status") == "UP":
                        tables = sql_query(compose, env, schema_sql)
                        (logs / "schema-readiness.sql.tsv").write_text(tables + "\n")
                        if set(tables.splitlines()) == expected_tables:
                            break
                except (URLError, TimeoutError):
                    pass
            time.sleep(1)
        else:
            raise RuntimeError("应用 readiness 或八张业务表就绪检查超时")

        contract_status, contract = request(base_url + "/api/ent-doc/contract")
        (logs / "entity-documentation-contract.json").write_text(
            json.dumps(contract, ensure_ascii=False, indent=2)
        )
        check(contract_status == 200 and contract.get("contractVersion") == "1.0.0",
              f"实体文档契约响应不匹配：{contract_status} {contract}")
        entities = contract.get("entities")
        check(isinstance(entities, list) and {entity.get("resourceCode") for entity in entities}
              == {"customer", "customerAddress", "product", "order", "orderItem", "merchantProduct", "merchantPoster", "merchantOrder"},
              f"实体文档实体范围不匹配：{contract}")
        contract_text = json.dumps(contract, ensure_ascii=False)
        for forbidden in ("entityClass", "tableName", "column", "visibleFor"):
            check(forbidden not in contract_text, f"实体文档泄露敏感字段：{forbidden}")

        product_id = crud_create(base_url, "product", {
            "name": "Entity Book", "price": 19.90, "active": True,
        }, project + "-product")
        inactive_product_id = crud_create(base_url, "product", {
            "name": "Inactive Product", "price": 9.90, "active": False,
        }, project + "-inactive-product")
        customer_id = crud_create(base_url, "customer", {
            "displayName": "Ada Lovelace", "email": "ada@example.com",
        }, project + "-customer")

        status, saleable = request(base_url + "/api/ent-crud/product/page", {
            "options": {"filter": {"active": True}, "page": 1, "limit": 1,
                        "sorts": [{"field": "price", "direction": "ASC"}]},
        })
        check(status == 200 and saleable.get("data", {}).get("page", {}).get("total") == 1
              and saleable["data"]["items"][0]["id"] == product_id,
              f"启用商品过滤或分页结果错误：{saleable}")
        status, inactive = request(base_url + "/api/ent-crud/product/page", {
            "options": {"filter": {"active": False}},
        })
        check(status == 200 and inactive.get("data", {}).get("page", {}).get("total") == 0
              and inactive["data"]["items"] == [],
              f"反向筛选不应绕过消费者读取范围：{inactive}")
        status, all_products = request(base_url + "/api/ent-crud/product/page", {})
        check(status == 200 and all_products.get("data", {}).get("page", {}).get("total") == 1
              and all_products["data"]["items"][0]["id"] == product_id,
              f"默认商品分页应只返回启用商品：{all_products}")
        status, hidden_product = request(base_url + "/api/ent-crud/product/detail", {
            "options": {"filter": {"id": inactive_product_id}},
        })
        check(status == 404 and hidden_product.get("code") == "ROUTE_NOT_FOUND",
              f"消费者不应读取停用商品详情：{hidden_product}")

        status, place = request(base_url + "/api/ent-crud/order/action/place", {
            "payload": {"customerId": customer_id,
                        "items": [{"productId": product_id, "quantity": 2}]},
        })
        (logs / "place-order.json").write_text(json.dumps(place, ensure_ascii=False, indent=2))
        check(status == 200 and "orderId" in place.get("data", {}), f"下单失败：{place}")
        order_id = int(place["data"]["orderId"])
        check(Decimal(str(place["data"]["totalAmount"])) == Decimal("39.80"), "订单总额不匹配")

        status, denied = request(base_url + "/api/ent-crud/customer/delete", {
            "payload": {"id": customer_id},
        })
        check(status == 403 and denied.get("code") == "PERMISSION_DENIED",
              f"客户删除应被拒绝以保护订单关联：{status} {denied}")

        status, detail_response = request(base_url + "/api/ent-crud/order/detail", {
            "options": {"resultMode": "ENTITY", "filter": {"id": order_id},
                        "expandRelations": ["customer", "orderItemList"]},
        })
        detail = detail_response.get("data", {}).get("item", {})
        (logs / "order-detail.json").write_text(json.dumps(detail, ensure_ascii=False, indent=2))
        check(status == 200, f"订单详情失败：{detail}")
        item = detail["orderItemList"][0]
        check(detail["id"] == order_id and detail["customerId"] == customer_id
              and detail["customer"]["displayName"] == "Ada Lovelace"
              and detail["status"] == "CREATED"
              and Decimal(str(detail["totalAmount"])) == Decimal("39.80"),
              "订单详情头信息不匹配")
        check(item["productId"] == product_id and item["productName"] == "Entity Book"
              and Decimal(str(item["unitPrice"])) == Decimal("19.90")
              and item["quantity"] == 2
              and Decimal(str(item["lineAmount"])) == Decimal("39.80"),
              "订单详情明细不匹配")

        crud_update(base_url, "product", {"id": product_id, "price": 21.00}, project + "-price-update")
        status, after_response = request(base_url + "/api/ent-crud/order/detail", {
            "options": {"resultMode": "ENTITY", "filter": {"id": order_id}, "expandRelations": ["orderItemList"]},
        })
        after_price_change = after_response.get("data", {}).get("item", {})
        check(status == 200 and Decimal(str(after_price_change["orderItemList"][0]["unitPrice"])) == Decimal("19.90")
              and Decimal(str(after_price_change["totalAmount"])) == Decimal("39.80")
              and Decimal(str(after_price_change["orderItemList"][0]["lineAmount"])) == Decimal("39.80"),
              "订单没有保留价格快照")
        check(Decimal(sql_query(compose, env, f"select price from product where id = {product_id}"))
              == Decimal("21.00"), "商品价格未实际更新")

        for label, customer, product, expected_code in [
            ("inactive-product", customer_id, inactive_product_id, "PRODUCT_INACTIVE"),
            ("missing-product", customer_id, 9999, "PRODUCT_NOT_FOUND"),
            ("missing-customer", 9999, product_id, "CUSTOMER_NOT_FOUND"),
        ]:
            status, error = request(base_url + "/api/ent-crud/order/action/place", {
                "payload": {"customerId": customer,
                            "items": [{"productId": product, "quantity": 1}]},
            })
            (logs / f"failure-{label}.json").write_text(json.dumps(error, ensure_ascii=False, indent=2))
            check(status == 400 and error.get("code") == expected_code,
                  f"{label} 失败响应不匹配：{status} {error}")

        # 仅在独立验收库中增加约束，使订单头写入后，明细写入失败。
        counts_sql = ("select (select count(*) from `order`), "
                      "(select count(*) from order_item)")
        before_failure = sql_query(compose, env, counts_sql)
        sql_query(compose, env, "alter table order_item add constraint verify_quantity_failure "
                               "check (quantity <> 3)")
        try:
            status, error = request(base_url + "/api/ent-crud/order/action/place", {
                "payload": {"customerId": customer_id,
                            "items": [{"productId": product_id, "quantity": 3}]},
            })
            check(status == 500, f"明细写入故障未按预期发生：{status} {error}")
            after_failure = sql_query(compose, env, counts_sql)
            check(after_failure == before_failure, "明细写入失败后存在订单或明细残留")
            (logs / "rollback.sql.tsv").write_text(before_failure + "\n" + after_failure + "\n")
        finally:
            sql_query(compose, env, "alter table order_item drop check verify_quantity_failure")

        order_sql = run(compose + ["exec", "-T", "-e", "MYSQL_PWD=mini_commerce_verify", "mysql",
                                   "mysql", "-umini_commerce", "-N", "-B", "mini_commerce", "-e",
                                   "select id, customer_id, status, total_amount from `order` "
                                   f"where id = {order_id}"], env, capture_output=True, text=True).stdout.strip()
        (logs / "order.sql.tsv").write_text(order_sql + "\n")
        order_fields = order_sql.split("\t")
        check(len(order_fields) == 4
              and order_fields[:3] == [str(order_id), str(customer_id), "CREATED"]
              and Decimal(order_fields[3]) == Decimal("39.80"),
              f"订单 SQL 字段不匹配：{order_sql}")
        item_sql = run(compose + ["exec", "-T", "-e", "MYSQL_PWD=mini_commerce_verify", "mysql",
                                  "mysql", "-umini_commerce", "-N", "-B", "mini_commerce", "-e",
                                  "select order_id, product_id, product_name, unit_price, quantity, line_amount "
                                  f"from order_item where order_id = {order_id}"],
                       env, capture_output=True, text=True).stdout.strip()
        (logs / "order-item.sql.tsv").write_text(item_sql + "\n")
        item_fields = item_sql.split("\t")
        check(len(item_fields) == 6
              and item_fields[:3] == [str(order_id), str(product_id), "Entity Book"]
              and Decimal(item_fields[3]) == Decimal("19.90") and item_fields[4] == "2"
              and Decimal(item_fields[5]) == Decimal("39.80"),
              f"订单明细 SQL 字段不匹配：{item_sql}")

        verify_merchant(base_url, compose, env, logs)
        verify_order_entries(base_url, compose, env, logs)

    except Exception as error:
        failed = True
        (logs / "failure.log").write_text(str(error) + "\n")
        print(f"验收失败：{error}；请查看 {logs}", file=sys.stderr)
    finally:
        if app is not None:
            try:
                if app.poll() is None:
                    app.terminate()
                    try:
                        app.wait(timeout=20)
                    except subprocess.TimeoutExpired:
                        app.kill()
                        app.wait(timeout=10)
            except Exception as error:
                failed = True
                print(f"停止应用失败：{error}", file=sys.stderr)
        if app_log is not None:
            app_log.close()
        if compose_started:
            for filename, arguments in [("compose.log", ["logs", "--no-color"]),
                                        ("cleanup.log", ["down", "--volumes", "--remove-orphans"])]:
                try:
                    with (logs / filename).open("w") as output:
                        run(compose + arguments, env, stdout=output, stderr=subprocess.STDOUT, timeout=120)
                except Exception as error:
                    failed = True
                    print(f"{filename} 执行失败：{error}", file=sys.stderr)
    if not failed:
        print(f"验收通过并完成清理：订单快照、商家隔离、整批改价、事务回滚与双入口子订单分页，订单 ID={order_id}", flush=True)
    return 1 if failed else 0


def interrupted(signum, frame):
    raise KeyboardInterrupt("验收被终止，正在清理资源")


if __name__ == "__main__":
    signal.signal(signal.SIGTERM, interrupted)
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--maven-repository", help="工作区构件所在的隔离 Maven 本地仓库")
    parser.add_argument("--skip-build", action="store_true", help="使用已构建的示例 JAR")
    args = parser.parse_args()
    sys.exit(verify(args.maven_repository, args.skip_build))
