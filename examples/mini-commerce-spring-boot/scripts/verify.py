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


def request(url, body=None, method=None):
    data = None if body is None else json.dumps(body).encode()
    req = Request(url, data=data, method=method or ("POST" if body is not None else "GET"),
                  headers={"Content-Type": "application/json"})
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
    return run(compose + ["exec", "-T", "-e", "MYSQL_PWD=mini_commerce_verify", "mysql",
                          "mysql", "-umini_commerce", "-N", "-B", "mini_commerce", "-e", sql],
               env, capture_output=True, text=True).stdout.strip()


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
            java, "-jar", str(jar), "--spring.profiles.active=verify",
            "--spring.config.import=", "--server.port=0", "--server.address=127.0.0.1",
            f"--spring.datasource.url=jdbc:mysql://127.0.0.1:{mysql_port}/mini_commerce"
            "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai",
            "--spring.datasource.username=mini_commerce",
            "--spring.datasource.password=mini_commerce_verify",
        ], cwd=EXAMPLE, env=env, stdout=app_log, stderr=subprocess.STDOUT)
        deadline = time.monotonic() + 120
        base_url = None
        while time.monotonic() < deadline:
            check(app.poll() is None, "应用在就绪前退出")
            content = (logs / "application.log").read_text(errors="replace")
            match = re.search(r"Tomcat started on port (\d+)", content)
            if match:
                base_url = f"http://127.0.0.1:{match.group(1)}"
                try:
                    if request(base_url + "/actuator/health")[1].get("status") == "UP":
                        break
                except (URLError, TimeoutError):
                    pass
            time.sleep(1)
        else:
            raise RuntimeError("应用健康检查超时")

        contract_status, contract = request(base_url + "/api/ent-doc/contract")
        (logs / "entity-documentation-contract.json").write_text(
            json.dumps(contract, ensure_ascii=False, indent=2)
        )
        check(contract_status == 200 and contract.get("contractVersion") == "1.0.0",
              f"实体文档契约响应不匹配：{contract_status} {contract}")
        entities = contract.get("entities")
        check(isinstance(entities, list) and {entity.get("resourceCode") for entity in entities}
              == {"customer", "product"}, f"实体文档实体范围不匹配：{contract}")
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

        status, saleable = request(base_url + "/api/ent-crud/product/page/saleable", {
            "options": {"page": 1, "limit": 1, "sorts": [{"field": "price", "direction": "ASC"}]},
        })
        check(status == 200 and saleable.get("data", {}).get("page", {}).get("total") == 1
              and saleable["data"]["items"][0]["id"] == product_id,
              f"可售商品场景未命中或分页结果错误：{saleable}")
        status, excluded = request(base_url + "/api/ent-crud/product/page/saleable", {
            "options": {"filter": {"active": False}},
        })
        check(status == 200 and excluded.get("data", {}).get("page", {}).get("total") == 0,
              f"可售商品场景未与调用方条件取交集：{excluded}")
        status, all_products = request(base_url + "/api/ent-crud/product/page", {})
        check(status == 200 and all_products.get("data", {}).get("page", {}).get("total") == 2,
              f"默认商品分页不应排除停用商品：{all_products}")

        status, place = request(base_url + "/api/ent-crud/order/action/place", {
            "payload": {"customerId": customer_id,
                        "items": [{"productId": product_id, "quantity": 2}]},
        })
        (logs / "place-order.json").write_text(json.dumps(place, ensure_ascii=False, indent=2))
        check(status == 200 and "orderId" in place.get("data", {}), f"下单失败：{place}")
        order_id = int(place["data"]["orderId"])
        check(Decimal(str(place["data"]["totalAmount"])) == Decimal("39.80"), "订单总额不匹配")

        status, detail_response = request(base_url + "/api/ent-crud/order/detail/detail", {
            "options": {"filter": {"id": order_id}},
        })
        detail = detail_response.get("data", {}).get("item", {})
        (logs / "order-detail.json").write_text(json.dumps(detail, ensure_ascii=False, indent=2))
        check(status == 200, f"订单详情失败：{detail}")
        item = detail["items"][0]
        check(detail["id"] == order_id and detail["customerId"] == customer_id
              and detail["status"] == "CREATED"
              and Decimal(str(detail["totalAmount"])) == Decimal("39.80"),
              "订单详情头信息不匹配")
        check(item["productId"] == product_id and item["productName"] == "Entity Book"
              and Decimal(str(item["unitPrice"])) == Decimal("19.90")
              and item["quantity"] == 2
              and Decimal(str(item["lineAmount"])) == Decimal("39.80"),
              "订单详情明细不匹配")

        crud_update(base_url, "product", {"id": product_id, "price": 21.00}, project + "-price-update")
        status, after_response = request(base_url + "/api/ent-crud/order/detail/detail", {
            "options": {"filter": {"id": order_id}},
        })
        after_price_change = after_response.get("data", {}).get("item", {})
        check(status == 200 and Decimal(str(after_price_change["items"][0]["unitPrice"])) == Decimal("19.90")
              and Decimal(str(after_price_change["totalAmount"])) == Decimal("39.80")
              and Decimal(str(after_price_change["items"][0]["lineAmount"])) == Decimal("39.80"),
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
        counts_sql = ("select (select count(*) from commerce_order), "
                      "(select count(*) from commerce_order_item)")
        before_failure = sql_query(compose, env, counts_sql)
        sql_query(compose, env, "alter table commerce_order_item add constraint verify_quantity_failure "
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
            sql_query(compose, env, "alter table commerce_order_item drop check verify_quantity_failure")

        order_sql = run(compose + ["exec", "-T", "-e", "MYSQL_PWD=mini_commerce_verify", "mysql",
                                   "mysql", "-umini_commerce", "-N", "-B", "mini_commerce", "-e",
                                   "select id, customer_id, status, total_amount from commerce_order "
                                   f"where id = {order_id}"], env, capture_output=True, text=True).stdout.strip()
        (logs / "order.sql.tsv").write_text(order_sql + "\n")
        check(order_sql.split("\t") == [str(order_id), str(customer_id), "CREATED", "39.80"],
              f"订单 SQL 字段不匹配：{order_sql}")
        item_sql = run(compose + ["exec", "-T", "-e", "MYSQL_PWD=mini_commerce_verify", "mysql",
                                  "mysql", "-umini_commerce", "-N", "-B", "mini_commerce", "-e",
                                  "select order_id, product_id, product_name, unit_price, quantity, line_amount "
                                  f"from commerce_order_item where order_id = {order_id}"],
                       env, capture_output=True, text=True).stdout.strip()
        (logs / "order-item.sql.tsv").write_text(item_sql + "\n")
        check(item_sql.split("\t") == [str(order_id), str(product_id), "Entity Book", "19.90", "2", "39.80"],
              f"订单明细 SQL 字段不匹配：{item_sql}")

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
        print(f"验收通过并完成清理：商品 -> 客户 -> 下单 -> 详情 -> SQL，订单 ID={order_id}", flush=True)
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
