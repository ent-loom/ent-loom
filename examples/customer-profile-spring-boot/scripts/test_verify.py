"""验证验收脚本的资源生命周期，不替代真实 MySQL 验收。"""

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
            (target / "customer-profile-spring-boot-0.1.0-SNAPSHOT.jar").touch()
            commands = []
            environments = []
            process = Mock()
            process.poll.return_value = None

            def launch(command, **kwargs):
                self.assertIn("--server.port=0", command)
                self.assertIn("--spring.config.import=", command)
                kwargs["stdout"].write("Tomcat started on port 49152\n")
                kwargs["stdout"].flush()
                return process

            def run(command, env, **kwargs):
                commands.append(command)
                environments.append(env)
                if fail_start and "up" in command:
                    raise subprocess.CalledProcessError(1, command)
                if fail_logs and "logs" in command:
                    raise subprocess.CalledProcessError(1, command)
                output = "127.0.0.1:49153\n" if "port" in command else "123\tAda Lovelace\tada@example.com\n"
                return subprocess.CompletedProcess(command, 0, stdout=output)

            def request(url, body=None):
                if url.endswith("/health"):
                    return {"status": "UP"}
                if url.endswith("/create"):
                    return {"success": True, "data": {"id": 123}}
                self.assertEqual(body["options"]["filterMap"]["id"]["value"], 123)
                return {"success": True, "data": {"item": {
                    "id": 123, "display_name": "wrong" if fail_detail else "Ada Lovelace",
                    "email": "ada@example.com"}}}

            with patch.object(verify, "EXAMPLE", example), \
                    patch.object(verify, "run", side_effect=run), \
                    patch.object(verify, "request", side_effect=request), \
                    patch.object(verify.subprocess, "Popen", side_effect=launch), \
                    patch.object(verify.time, "time_ns", return_value=123_000_000):
                result = verify.verify(skip_build=True)

            self.assertEqual(result, int(fail_detail or fail_logs or fail_start))
            cleanup = [command for command in commands if "down" in command]
            self.assertEqual(len(cleanup), 1)
            self.assertIn("--volumes", cleanup[0])
            projects = {command[command.index("--project-name") + 1] for command in commands}
            self.assertEqual(len(projects), 1)
            self.assertTrue(next(iter(projects)).startswith("ent-loom-verify-"))
            self.assertTrue(all(env["MYSQL_PORT"] == "127.0.0.1:0" for env in environments))
            if not fail_start:
                process.terminate.assert_called_once()
                process.wait.assert_called_once()
            log_directory = next((target / "verification-logs").iterdir())
            self.assertTrue((log_directory / "compose.log").exists())
            self.assertTrue((log_directory / "cleanup.log").exists())
            if fail_detail or fail_start:
                self.assertTrue((log_directory / "failure.log").exists())
            if not (fail_detail or fail_start):
                self.assertEqual((log_directory / "sql.tsv").read_text(),
                                 "123\tAda Lovelace\tada@example.com\n")

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
