"""验证商城验收脚本的资源生命周期，不替代真实 MySQL 验收。"""

from pathlib import Path
from decimal import Decimal
import subprocess
import re
import tempfile
import unittest
from unittest.mock import Mock, patch

import verify


class VerificationLifecycleTest(unittest.TestCase):
    def execute(self, fail_detail=False, fail_logs=False, fail_start=False, fail_rollback=False,
                fail_merchant_rollback=None, fail_order_entry=None):
        with tempfile.TemporaryDirectory() as directory:
            example = Path(directory)
            target = example / "target"
            target.mkdir()
            (target / "mini-commerce-spring-boot-0.1.0-SNAPSHOT.jar").touch()
            (example / "scripts").mkdir()
            (example / "scripts" / "merchant-demo.sql").write_text(
                (verify.EXAMPLE / "scripts" / "merchant-demo.sql").read_text())
            order_seed = ("INSERT IGNORE INTO merchant_order "
                          "(id, order_id, customer_id, merchant_id, total_amount) VALUES\n"
                          "(501, 9001, 301, 1001, 10), (502, 9001, 301, 2001, 20),\n"
                          "(503, 9002, 302, 1001, 30), (504, 9003, 301, 1001, 40),\n"
                          "(505, 9004, 302, 2001, 50);\n")
            (example / "scripts" / "order-entry-demo.sql").write_text(order_seed)
            commands = []
            count_queries = 0
            processes = []
            generated_ids = {"product": 1001, "customer": 2001}
            merchant_rows = {}
            price_constraint = False
            merchant_requests = []
            order_rows = {}
            order_requests = []
            readiness_checks = 0
            schema_queries = 0

            def launch(command, **kwargs):
                self.assertIn("--server.port=0", command)
                self.assertIn("--spring.profiles.active=", command)
                self.assertIn("--management.endpoint.health.probes.enabled=true", command)
                self.assertIn("--ent.loom.crud.governance.access-entry=consumer", command)
                process = Mock()
                process.poll.return_value = None
                processes.append(process)
                kwargs["stdout"].write("Tomcat started on port 49152\n")
                kwargs["stdout"].flush()
                return process

            def run(command, env, **kwargs):
                nonlocal count_queries, price_constraint, schema_queries
                commands.append(command)
                if fail_start and "up" in command:
                    raise subprocess.CalledProcessError(1, command)
                if fail_logs and "logs" in command:
                    raise subprocess.CalledProcessError(1, command)
                if "port" in command:
                    output = "127.0.0.1:49153\n"
                elif "exec" in command and "information_schema.tables" in command[-1]:
                    self.assertGreaterEqual(readiness_checks, 2, "readiness UP 后才能检查业务表")
                    schema_queries += 1
                    self.assertIn("'merchant_order'", command[-1])
                    output = "customer\norder\norder_item\nproduct\nmerchant_product\n"
                    if schema_queries > 1:
                        output += "merchant_order\n"
                elif "exec" in command and "INSERT IGNORE INTO merchant_product" in command[-1]:
                    self.assertTrue(any("select id, customer_id" in part
                                        for previous in commands[:-1] for part in previous))
                    merchant_rows.update({
                        101: {"id": 101, "productId": 900010, "merchantId": 1001, "price": "10.000000", "version": 0},
                        102: {"id": 102, "productId": 900020, "merchantId": 1001, "price": "20.000000", "version": 0},
                        201: {"id": 201, "productId": 900010, "merchantId": 2001, "price": "30.000000", "version": 0},
                    })
                    output = ""
                elif "exec" in command and "INSERT IGNORE INTO merchant_order" in command[-1]:
                    self.assertEqual(command[-1], order_seed)
                    self.assertEqual(count_queries, 2, "分页补种必须在原订单 SQL 计数之后")
                    self.assertTrue(any(url.endswith("/merchantProduct/update")
                                        for url, _ in merchant_requests), "分页补种必须在商家批量验收之后")
                    order_rows.update({row_id: dict(zip(
                        ("id", "orderId", "customerId", "merchantId", "totalAmount"), row))
                        for row_id, row in [
                            (501, (501, 9001, 301, 1001, "10.000000")),
                            (502, (502, 9001, 301, 2001, "20.000000")),
                            (503, (503, 9002, 302, 1001, "30.000000")),
                            (504, (504, 9003, 301, 1001, "40.000000")),
                            (505, (505, 9004, 302, 2001, "50.000000")),
                        ]})
                    output = ""
                elif "exec" in command and "from merchant_order" in command[-1]:
                    sql = command[-1]
                    selected = [row for _, row in sorted(order_rows.items())]
                    for column, field in [("customer_id", "customerId"), ("merchant_id", "merchantId")]:
                        match = re.search(rf"\b{column} = (\d+)", sql)
                        if match:
                            selected = [row for row in selected if row[field] == int(match.group(1))]
                    if sql.startswith("select count(*)"):
                        output = str(len(selected)) + "\n"
                    else:
                        pagination = re.search(r"limit (\d+) offset (\d+)", sql)
                        if pagination:
                            size, offset = map(int, pagination.groups())
                            selected = selected[offset:offset + size]
                        if fail_order_entry == "sql-scope" and "customer_id = 301 and merchant_id = 1001" in sql:
                            selected.append(order_rows[503])
                        output = "\n".join("\t".join(str(row[field]) for field in
                                                  ("id", "orderId", "customerId", "merchantId", "totalAmount"))
                                           for row in selected)
                elif "exec" in command and "from merchant_product order by id" in command[-1]:
                    output = "\n".join("\t".join(str(row[field]) for field in
                                              ("id", "productId", "merchantId", "price", "version"))
                                       for _, row in sorted(merchant_rows.items())) + "\n"
                elif "exec" in command and "add constraint verify_merchant_price_failure" in command[-1]:
                    self.assertIn("check (price <> 77)", command[-1])
                    price_constraint = True
                    output = ""
                elif "exec" in command and "drop check verify_merchant_price_failure" in command[-1]:
                    price_constraint = False
                    output = ""
                elif any("select price from product" in part for part in command):
                    output = "21.000000\n"
                elif any("select count(*)" in part for part in command):
                    count_queries += 1
                    output = "2\t1\n" if fail_rollback and count_queries > 1 else "1\t1\n"
                elif any("order_item" in part for part in command):
                    output = "123\t1001\tEntity Book\t19.900000\t2\t39.800000\n"
                else:
                    output = "123\t2001\tCREATED\t39.800000\n"
                return subprocess.CompletedProcess(command, 0, stdout=output)

            def request(url, body=None, method=None, headers=None):
                nonlocal readiness_checks
                if url.endswith("/health/readiness"):
                    readiness_checks += 1
                    if readiness_checks == 1:
                        return 503, {"status": "OUT_OF_SERVICE"}
                    return 200, {"status": "UP"}
                self.assertGreaterEqual(schema_queries, 2, "六张表全部就绪前不能发送业务请求")
                if url.endswith("/api/ent-doc/contract"):
                    return 200, {
                        "contractVersion": "1.0.0",
                        "entities": [{"resourceCode": code, "fields": []}
                                     for code in ("customer", "product", "order", "orderItem", "merchantProduct", "merchantOrder")],
                    }
                if url.endswith("/orders/page"):
                    self.assertTrue(order_rows, "双入口分页验收必须先从脚本补种子订单")
                    entry = url.split("/api/")[1].split("/")[0]
                    self.assertIn(entry, ("consumer", "merchant"))
                    token = (headers or {}).get("Authorization")
                    order_requests.append((entry, token, dict(body), dict(headers or {})))
                    identities = ({"Bearer demo-customer-a": 301, "Bearer demo-customer-b": 302}
                                  if entry == "consumer" else
                                  {"Bearer demo-merchant-a": 1001, "Bearer demo-merchant-b": 2001})
                    identity = identities.get(token)
                    if identity is None:
                        if fail_order_entry == "denial-mutation":
                            order_rows[501]["totalAmount"] = "99.000000"
                        if fail_order_entry == "cross-entry" and token == "Bearer demo-merchant-a":
                            return 200, {"success": True, "data": {"items": []}}
                        if fail_order_entry == "no-token" and token is None:
                            return 200, {"success": True, "data": {"items": []}}
                        if fail_order_entry == "invalid-token" and token == "Bearer invalid":
                            return 200, {"success": True, "data": {"items": []}}
                        return 403, {"success": False, "code": "PERMISSION_DENIED"}
                    allowed_fields = {"pageNumber", "pageSize", "merchantId" if entry == "consumer" else "customerId"}
                    if set(body) - allowed_fields:
                        if fail_order_entry == entry + "-unknown-owner":
                            return 200, {"success": True, "data": {"items": []}}
                        if fail_order_entry == "unknown-owner-code":
                            return 400, {"success": False, "code": "PERMISSION_DENIED"}
                        return 400, {"success": False, "code": "VALIDATION_ERROR"}
                    field = "customerId" if entry == "consumer" else "merchantId"
                    if fail_order_entry == entry + "-header" and "X-Customer-Id" in (headers or {}):
                        identity = int(headers["X-Customer-Id" if entry == "consumer" else "X-Merchant-Id"])
                    selected = [row for _, row in sorted(order_rows.items()) if row[field] == identity]
                    if fail_order_entry == entry + "-scope":
                        selected = list(order_rows.values())
                    filter_field = "merchantId" if entry == "consumer" else "customerId"
                    if filter_field in body:
                        if fail_order_entry == entry + "-filter-or":
                            selected = [row for row in order_rows.values()
                                        if row[field] == identity or row[filter_field] == body[filter_field]]
                        else:
                            selected = [row for row in selected if row[filter_field] == body[filter_field]]
                    total = len(selected)
                    page_number, page_size = body.get("pageNumber", 1), body.get("pageSize", 20)
                    offset = (page_number - 1) * page_size
                    if fail_order_entry == "unstable-page" and page_number == 2:
                        offset = 0
                    items = [dict(row) for row in selected[offset:offset + page_size]]
                    if fail_order_entry == "page-total" and page_size == 1:
                        total = len(items)
                    if fail_order_entry == "row-fields" and items:
                        items[0]["totalAmount"] = "99.000000"
                    data = {"items": items, "total": total, "pageNumber": page_number,
                            "pageSize": page_size, "page": page_number, "limit": page_size}
                    if fail_order_entry == "page-alias":
                        data["limit"] = 10
                    return 200, {"success": True, "data": data}
                if url.endswith("/merchantOrder/page"):
                    self.assertTrue(order_rows)
                    if fail_order_entry == "generic-page":
                        return 404, {"success": False, "code": "ROUTE_NOT_FOUND"}
                    return 404, {"success": False, "code": "ENTITY_NOT_EXPOSED"}
                if "/api/merchant/products" in url:
                    self.assertTrue(merchant_rows, "商家验收必须先补种数据")
                    self.assertIn(url, ("http://127.0.0.1:49152/api/merchant/products",
                                        "http://127.0.0.1:49152/api/merchant/products/prices"))
                    token = (headers or {}).get("Authorization")
                    merchant_requests.append((url, token))
                    merchant_id = {"Bearer demo-merchant-a": 1001, "Bearer demo-merchant-b": 2001}.get(token)
                    if merchant_id is None:
                        return 403, {"success": False, "code": "PERMISSION_DENIED"}
                    if not url.endswith("/prices"):
                        self.assertIsNone(body)
                        return 200, {"success": True, "data": [dict(row) for row in merchant_rows.values()
                                                              if row["merchantId"] == merchant_id]}
                    self.assertEqual(set(body), {"items"})
                    pending = {row_id: dict(row) for row_id, row in merchant_rows.items()}
                    for change in body["items"]:
                        row = pending.get(change["id"])
                        failure = None
                        if row is None:
                            failure = "missing"
                        elif row["merchantId"] != merchant_id:
                            failure = "cross-merchant"
                        elif row["version"] != change["version"]:
                            failure = "version"
                        elif price_constraint and change["id"] == 102 and change["price"] == 77:
                            failure = "sql-error"
                        if failure:
                            if fail_merchant_rollback == failure:
                                # 模拟第一项写入泄漏，确保脚本可发现价格和版本未回滚。
                                merchant_rows[101] = pending[101]
                            if failure == "sql-error":
                                return 500, {"success": False, "code": "INTERNAL_ERROR"}
                            return 409, {"success": False, "code": "PRICE_UPDATE_REJECTED"}
                        row.update(price=format(Decimal(str(change["price"])), ".6f"), version=row["version"] + 1)
                    merchant_rows.update(pending)
                    return 200, {"success": True, "data": [change["id"] for change in body["items"]]}
                if url.endswith("/merchantProduct/update"):
                    self.assertEqual(headers, {"Authorization": "Bearer demo-merchant-a"})
                    merchant_requests.append((url, headers["Authorization"]))
                    return 404, {"success": False, "code": "ENTITY_NOT_EXPOSED"}
                if url.endswith("/create"):
                    payload = body["payload"]
                    entity = url.split("/api/ent-crud/")[1].split("/")[0]
                    entity_id = generated_ids[entity]
                    generated_ids[entity] += 1
                    return 200, {"success": True, "data": {"id": entity_id}}
                if url.endswith("/update"):
                    return 200, {"success": True, "data": {"rows": 1}}
                if url.endswith("/customer/delete"):
                    return 403, {"code": "PERMISSION_DENIED"}
                if url.endswith("/product/page"):
                    active = body.get("options", {}).get("filter", {}).get("active")
                    items = [] if active is False else [{"id": 1001}]
                    return 200, {"data": {"page": {"total": len(items)}, "items": items}}
                if url.endswith("/product/detail"):
                    return 404, {"code": "ROUTE_NOT_FOUND"}
                if url.endswith("/order/action/place"):
                    payload = body["payload"]
                    if payload["items"][0]["quantity"] == 3:
                        return 500, {"error": "Internal Server Error"}
                    if payload["customerId"] == 9999:
                        return 400, {"code": "CUSTOMER_NOT_FOUND"}
                    if payload["items"][0]["productId"] == 1002:
                        return 400, {"code": "PRODUCT_INACTIVE"}
                    if payload["items"][0]["productId"] == 9999:
                        return 400, {"code": "PRODUCT_NOT_FOUND"}
                    return 200, {"data": {"orderId": 123, "totalAmount": "39.80"}}
                self.assertTrue(url.endswith("/order/detail"), url)
                self.assertEqual("ENTITY", body["options"]["resultMode"])
                self.assertIn("orderItemList", body["options"]["expandRelations"])
                return 200, {"data": {"item": {
                    "id": 123, "customerId": 2001, "status": "CREATED",
                    "customer": {"displayName": "Ada Lovelace"},
                    "totalAmount": "39.80", "orderItemList": [{
                        "productId": 1001, "productName": "Entity Book",
                        "unitPrice": "19.90" if not fail_detail else "21.00",
                        "quantity": 2, "lineAmount": "39.80",
                    }]
                }}}

            with patch.object(verify, "EXAMPLE", example), \
                    patch.object(verify, "run", side_effect=run), \
                    patch.object(verify, "request", side_effect=request), \
                    patch.object(verify.time, "sleep"), \
                    patch.object(verify.subprocess, "Popen", side_effect=launch):
                result = verify.verify(skip_build=True)

            self.assertEqual(result, int(bool(fail_detail or fail_logs or fail_start or fail_rollback
                                              or fail_merchant_rollback or fail_order_entry)))
            if fail_rollback:
                self.assertTrue(any("drop check verify_quantity_failure" in part
                                    for command in commands for part in command))
            cleanup = [command for command in commands if "down" in command]
            self.assertEqual(len(cleanup), 1)
            self.assertIn("--volumes", cleanup[0])
            projects = {command[command.index("--project-name") + 1] for command in commands}
            self.assertEqual(len(projects), 1)
            self.assertTrue(next(iter(projects)).startswith("ent-loom-commerce-verify-"))
            if not fail_start:
                self.assertEqual(readiness_checks, 3)
                self.assertEqual(schema_queries, 2)
                for process in processes:
                    process.terminate.assert_called_once()
                    process.wait.assert_called_once()
            log_directory = next((target / "verification-logs").iterdir())
            self.assertTrue((log_directory / "compose.log").exists())
            self.assertTrue((log_directory / "cleanup.log").exists())
            if fail_merchant_rollback == "sql-error":
                self.assertTrue(any("drop check verify_merchant_price_failure" in part
                                    for command in commands for part in command))
                self.assertFalse(price_constraint)
            if fail_merchant_rollback:
                self.assertTrue((log_directory / f"merchant-{fail_merchant_rollback}-rollback.sql.tsv").exists())
                self.assertIn("回滚失败", (log_directory / "failure.log").read_text())
            if not (fail_detail or fail_start or fail_rollback or fail_merchant_rollback):
                for label in ("initial", "commit", "cross-merchant-rollback", "missing-rollback",
                              "version-rollback", "sql-error-rollback", "final"):
                    self.assertTrue((log_directory / f"merchant-{label}.sql.tsv").exists())
                for label in ("a-initial", "b-initial", "commit", "cross-merchant-rollback", "missing-rollback",
                              "version-rollback", "sql-error-rollback", "no-token-read", "no-token-write",
                              "invalid-token-read", "invalid-token-write", "generic-update-denied", "a-final", "b-final"):
                    self.assertTrue((log_directory / f"merchant-{label}.json").exists())
                self.assertEqual(Decimal(merchant_rows[101]["price"]), Decimal("11.50"))
                self.assertEqual(merchant_rows[101]["version"], 1)
                self.assertFalse(price_constraint)
                self.assertTrue(any(token == "Bearer demo-merchant-b" for _, token in merchant_requests))
                self.assertEqual((log_directory / "order-entry-seed.sql").read_text(), order_seed)
                if fail_order_entry:
                    self.assertTrue((log_directory / "failure.log").exists())
                    self.assertIn("子订单", (log_directory / "failure.log").read_text())
                else:
                    self.assertEqual(count_queries, 2, "新增子订单查询不改变原订单计数验收")
                    for label in ("consumer-a", "consumer-b", "consumer-a-merchant", "merchant-a", "merchant-b",
                                  "merchant-a-customer", "consumer-a-page-1", "consumer-a-page-2", "consumer-a-empty",
                                  "consumer-forged-header", "consumer-forged-header-filter",
                                  "merchant-forged-header", "merchant-forged-header-filter"):
                        for suffix in (".json", ".http.json", ".sql", ".sql.tsv", "-count.sql", "-count.sql.tsv"):
                            self.assertTrue((log_directory / f"order-entry-{label}{suffix}").exists())
                    denied_labels = [f"{entry}-{reason}" for entry in ("consumer", "merchant")
                                     for reason in ("cross-entry", "no-token", "invalid-token")]
                    denied_labels += [f"{entry}-{reason}" for entry in ("consumer", "merchant")
                                      for reason in ("unknown-owner", "unknown-owner-header", "unknown-owner-filter")]
                    for label in denied_labels + ["generic-page-denied"]:
                        for suffix in (".json", ".http.json", ".sql", ".sql.tsv"):
                            self.assertTrue((log_directory / f"order-entry-{label}{suffix}").exists())
                    for entry in ("consumer", "merchant"):
                        self.assertEqual(sum(actual == entry for actual, _, _, _ in order_requests),
                                         14 if entry == "consumer" else 11)
                        unknown_requests = [(body, headers) for actual, _, body, headers in order_requests
                                            if actual == entry and ("customerId" if entry == "consumer" else "merchantId") in body]
                        self.assertEqual(len(unknown_requests), 3)
                        self.assertEqual(sum("X-Customer-Id" in headers for _, headers in unknown_requests), 2)

    def test_success_cleans_application_and_volume(self):
        with patch.object(verify, "urlopen") as open_url:
            response = open_url.return_value.__enter__.return_value
            response.status = 200
            response.read.return_value = '{"success": true}'
            headers = {"Authorization": "Bearer demo-merchant-a"}
            self.assertEqual(verify.request("http://localhost/api/merchant/products", headers=headers),
                             (200, {"success": True}))
            sent = open_url.call_args.args[0]
            self.assertEqual(sent.get_method(), "GET")
            self.assertEqual(sent.get_header("Authorization"), headers["Authorization"])
            self.assertEqual(sent.get_header("Content-type"), "application/json")
            self.assertEqual(headers, {"Authorization": "Bearer demo-merchant-a"})
        self.execute()

    def test_assertion_failure_preserves_logs_and_cleans_resources(self):
        self.execute(fail_detail=True)

    def test_log_failure_does_not_skip_cleanup(self):
        self.execute(fail_logs=True)

    def test_compose_start_failure_still_cleans_partial_resources(self):
        self.execute(fail_start=True)

    def test_rollback_leak_fails_and_removes_temporary_constraint(self):
        with self.subTest(rollback="order"):
            self.execute(fail_rollback=True)
        for failure in ("cross-merchant", "missing", "version", "sql-error"):
            with self.subTest(rollback=failure):
                self.execute(fail_merchant_rollback=failure)

    def test_order_entry_scope_filter_and_pagination_regressions_fail(self):
        for failure in ("consumer-scope", "merchant-scope", "consumer-filter-or", "merchant-filter-or",
                        "unstable-page", "page-total", "page-alias", "row-fields", "sql-scope"):
            with self.subTest(failure=failure):
                self.execute(fail_order_entry=failure)

    def test_order_entry_denial_regressions_fail_and_clean_resources(self):
        for failure in ("cross-entry", "no-token", "invalid-token", "generic-page", "denial-mutation"):
            with self.subTest(failure=failure):
                self.execute(fail_order_entry=failure)

    def test_unknown_owner_is_rejected_and_headers_cannot_replace_scope(self):
        for failure in ("consumer-unknown-owner", "merchant-unknown-owner", "unknown-owner-code",
                        "consumer-header", "merchant-header"):
            with self.subTest(failure=failure):
                self.execute(fail_order_entry=failure)


if __name__ == "__main__":
    unittest.main()
