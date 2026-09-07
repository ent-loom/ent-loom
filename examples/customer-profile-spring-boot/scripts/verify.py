#!/usr/bin/env python3
"""独立 MySQL 环境中的 CREATE -> DETAIL -> SQL 验收。"""

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
from urllib.error import URLError
from urllib.request import Request, urlopen
import uuid

EXAMPLE = Path(__file__).resolve().parents[1]


def check(condition, message):
    if not condition:
        raise RuntimeError(message)


def run(command, env, **kwargs):
    return subprocess.run(command, cwd=EXAMPLE, env=env, check=True, **kwargs)


def request(url, body=None):
    data = None if body is None else json.dumps(body).encode()
    req = Request(url, data=data, headers={"Content-Type": "application/json"})
    with urlopen(req, timeout=5) as response:
        return json.load(response)


def verify(repository=None, skip_build=False):
    project = "ent-loom-verify-" + uuid.uuid4().hex[:12]
    logs = EXAMPLE / "target" / "verification-logs" / project
    logs.mkdir(parents=True)
    env = os.environ.copy()
    # 验收参数固定且独立，不消费本机 .env 的数据库账号或端口。
    env.update(MYSQL_DATABASE="customer_profile", MYSQL_USER="customer_profile",
               MYSQL_PASSWORD="customer_profile_verify", MYSQL_ROOT_PASSWORD="verify_root",
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
        jar = EXAMPLE / "target" / "customer-profile-spring-boot-0.1.0-SNAPSHOT.jar"
        check(jar.exists(), f"未找到应用：{jar}")
        java = str(Path(env["JAVA_HOME"]) / "bin" / "java") if env.get("JAVA_HOME") else "java"
        app_log = (logs / "application.log").open("w")
        app = subprocess.Popen([
            java, "-jar", str(jar), "--spring.profiles.active=verify",
            "--spring.config.import=", "--server.port=0", "--server.address=127.0.0.1",
            f"--spring.datasource.url=jdbc:mysql://127.0.0.1:{mysql_port}/customer_profile"
            "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai",
            "--spring.datasource.username=customer_profile",
            "--spring.datasource.password=customer_profile_verify",
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
                    if request(base_url + "/actuator/health").get("status") == "UP":
                        break
                except (URLError, TimeoutError):
                    pass
            time.sleep(1)
        else:
            raise RuntimeError("应用健康检查超时")

        customer_id = time.time_ns() // 1_000_000
        create = request(base_url + "/api/ent-crud/customer_profile/create", {
            "options": {"requestId": project + "-create"},
            "payload": {"id": customer_id, "displayName": "Ada Lovelace", "email": "ada@example.com"},
        })
        (logs / "create.json").write_text(json.dumps(create, ensure_ascii=False, indent=2))
        check(create.get("success") is True, f"CREATE 失败：{create}")
        created_id = int(create["data"]["id"])
        check(created_id == customer_id, "CREATE 返回的 ID 不匹配")
        detail = request(base_url + "/api/ent-crud/customer_profile/detail", {
            "options": {"requestId": project + "-detail",
                        "filterMap": {"id": {"op": "EQ", "value": created_id}}},
        })
        (logs / "detail.json").write_text(json.dumps(detail, ensure_ascii=False, indent=2))
        check(detail.get("success") is True, f"DETAIL 失败：{detail}")
        item = detail["data"]["item"]
        check(int(item["id"]) == created_id and item["display_name"] == "Ada Lovelace"
              and item["email"] == "ada@example.com", "DETAIL 字段不匹配")
        sql = run(compose + ["exec", "-T", "-e", "MYSQL_PWD=customer_profile_verify", "mysql",
                            "mysql", "-ucustomer_profile", "-N", "-B", "customer_profile", "-e",
                            "select id, display_name, email from customer_profile "
                            f"where id = {created_id}"], env, capture_output=True, text=True).stdout.strip()
        (logs / "sql.tsv").write_text(sql + "\n")
        check(sql.split("\t") == [str(created_id), "Ada Lovelace", "ada@example.com"],
              f"SQL 字段不匹配：{sql}")
    except Exception as error:
        failed = True
        (logs / "failure.log").write_text(str(error) + "\n")
        print(f"验收失败：{error}；请查看 {logs}", file=sys.stderr)
    finally:
        # 日志收集和各项清理独立执行，任一步失败不能阻止后续资源回收。
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
        print(f"验收通过并完成清理：CREATE -> DETAIL -> SQL，ID={created_id}", flush=True)
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
