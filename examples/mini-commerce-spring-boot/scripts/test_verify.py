"""验证商城验收脚本的资源生命周期，不替代真实 MySQL 验收。"""

from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import Mock, patch

import verify


class VerificationLifecycleTest(unittest.TestCase):
    def execute(self, fail_detail=False, fail_logs=False, fail_start=False):
        with tempfile.TemporaryDirectory() as directory:
            example = Path(directory)
            target = example / "target"
            target.mkdir()
            (target / "mini-commerce-spring-boot-0.1.0-SNAPSHOT.jar").touch()
            commands = []
            process = Mock()
            process.poll.return_value = None

            def launch(command, **kwargs):
                self.assertIn("--server.port=0", command)
                kwargs["stdout"].write("Tomcat started on port 49152\n")
                kwargs["stdout"].flush()
                return process

            def run(command, env, **kwargs):
                commands.append(command)
                if fail_start and "up" in command:
                    raise subprocess.CalledProcessError(1, command)
                if fail_logs and "logs" in command:
                    raise subprocess.CalledProcessError(1, command)
                if "port" in command:
                    output = "127.0.0.1:49153\n"
                elif any("commerce_order_item" in part for part in command):
                    output = "123\t1001\tEntity Book\t19.90\t2\t39.80\n"
                else:
                    output = "123\t2001\tCREATED\t39.80\n"
                return subprocess.CompletedProcess(command, 0, stdout=output)

            def request(url, body=None, method=None):
                if url.endswith("/health"):
                    return 200, {"status": "UP"}
                if url.endswith("/create"):
                    payload = body["payload"]
                    return 200, {"success": True, "data": {"id": payload["id"]}}
                if url.endswith("/update"):
                    return 200, {"success": True, "data": {"rows": 1}}
                if url.endswith("/orders"):
                    if body["customerId"] == 9999:
                        return 400, {"code": "CUSTOMER_NOT_FOUND"}
                    if body["items"][0]["productId"] == 1002:
                        return 400, {"code": "PRODUCT_INACTIVE"}
                    if body["items"][0]["productId"] == 9999:
                        return 400, {"code": "PRODUCT_NOT_FOUND"}
                    return 201, {"orderId": 123, "totalAmount": "39.80"}
                return 200, {"id": 123, "customerId": 2001, "status": "CREATED",
                             "totalAmount": "39.80", "items": [{
                                 "productId": 1001, "productName": "Entity Book",
                                 "unitPrice": "19.90" if not fail_detail else "21.00",
                                 "quantity": 2, "lineAmount": "39.80",
                             }]}

            with patch.object(verify, "EXAMPLE", example), \
                    patch.object(verify, "run", side_effect=run), \
                    patch.object(verify, "request", side_effect=request), \
                    patch.object(verify.subprocess, "Popen", side_effect=launch):
                result = verify.verify(skip_build=True)

            self.assertEqual(result, int(fail_detail or fail_logs or fail_start))
            cleanup = [command for command in commands if "down" in command]
            self.assertEqual(len(cleanup), 1)
            self.assertIn("--volumes", cleanup[0])
            projects = {command[command.index("--project-name") + 1] for command in commands}
            self.assertEqual(len(projects), 1)
            self.assertTrue(next(iter(projects)).startswith("ent-loom-commerce-verify-"))
            if not fail_start:
                process.terminate.assert_called_once()
                process.wait.assert_called_once()
            log_directory = next((target / "verification-logs").iterdir())
            self.assertTrue((log_directory / "compose.log").exists())
            self.assertTrue((log_directory / "cleanup.log").exists())

    def test_success_cleans_application_and_volume(self):
        self.execute()

    def test_assertion_failure_preserves_logs_and_cleans_resources(self):
        self.execute(fail_detail=True)

    def test_log_failure_does_not_skip_cleanup(self):
        self.execute(fail_logs=True)

    def test_compose_start_failure_still_cleans_partial_resources(self):
        self.execute(fail_start=True)


if __name__ == "__main__":
    unittest.main()
