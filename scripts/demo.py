#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""triform 一键三形态演示(E4)——访客克隆仓库后的一条命令:
    python scripts/demo.py --form mono                # 单体: 零基础设施
    python scripts/demo.py --form dubbo --nacos <addr> # 微服务·Dubbo
    python scripts/demo.py --form http  --nacos <addr> # 微服务·HTTP(Feign)
每步打印 PASS/FAIL; 结束自动清理进程(按端口)。
"""
import argparse, json, os, socket, subprocess, sys, time, urllib.request

MVN = r"D:\program\apache-maven-3.6.3\bin\mvn.cmd" if os.name == "nt" else "mvn"
SETTINGS = os.path.join(os.path.dirname(__file__), "..", ".mvn-settings.xml")
IS_WIN = os.name == "nt"
PASS, FAIL = 0, 0


def check(name, ok, detail=""):
    global PASS, FAIL
    print(("  [PASS] " if ok else "  [FAIL] ") + name + ((" — " + detail) if detail else ""))
    PASS, FAIL = PASS + (1 if ok else 0), FAIL + (0 if ok else 1)


def wait_port(port, timeout=90):
    deadline = time.time() + timeout
    while time.time() < deadline:
        try:
            with socket.create_connection(("127.0.0.1", port), timeout=2):
                return True
        except OSError:
            time.sleep(2)
    return False


def http_get(url, timeout=15):
    try:
        with urllib.request.urlopen(url, timeout=timeout) as r:
            return r.status, r.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        return e.code, str(e.reason)


def spawn(args, env_extra, log):
    env = dict(os.environ)
    env.update(env_extra or {})
    logf = open(log, "w", encoding="utf-8")
    return subprocess.Popen(args, stdout=logf, stderr=subprocess.STDOUT, env=env, shell=IS_WIN)


def kill_port(port):
    try:
        if IS_WIN:
            raw = subprocess.run(["netstat", "-ano"], capture_output=True).stdout or b""
            out = raw.decode("utf-8", "replace")
            for line in out.splitlines():
                if (":%d " % port) in line and "LISTENING" in line:
                    pid = line.split()[-1]
                    subprocess.run(["taskkill", "/F", "/T", "/PID", pid], capture_output=True)
        else:
            out = subprocess.run(["bash", "-c", "lsof -ti:%d" % port], capture_output=True, text=True).stdout
            for pid in out.split():
                subprocess.run(["kill", "-9", pid], capture_output=True)
    except Exception:
        pass


def base_env(nacos):
    e = {"JAVA_HOME": os.environ.get("JAVA_HOME", "")}
    if nacos:
        e["NACOS_SERVER_ADDR"] = nacos
    return e


def run(form, nacos, mvn_args):
    root = os.path.normpath(os.path.join(os.path.dirname(__file__), ".."))
    logs = os.path.join(root, "logs")
    os.makedirs(logs, exist_ok=True)
    common = [MVN, "-s", SETTINGS]

    if form == "mono":
        print("=== mono: 全部服务一个进程, 跨服务调用=本地 Bean(零基础设施) ===")
        spawn(common + ["-f", "all-in-one/pom.xml", "spring-boot:run"],
              base_env(None), os.path.join(logs, "demo-mono.log"))
        check("all-in-one 启动(9085)", wait_port(9085))
        time.sleep(3)
        status, body = http_get("http://localhost:9085/app/greet?name=demo")
        data = json.loads(body).get("data", "") if body else ""
        check("GET /app/greet", status == 200 and "sample-echo" in data, body[:80])
        kill_port(9085)
        return

    env_extra = base_env(nacos)
    env_extra["TRIFORM_RPC_HTTP_EXPOSE"] = "true"
    env_extra["TRIFORM_RPC_INTERNAL_TOKEN"] = "demo-internal-token"
    if form == "http":
        env_extra["TRIFORM_RPC_TRANSPORT"] = "http"

    title = {"dubbo": "micro·Dubbo: 独立进程经注册中心互调",
             "http": "micro·HTTP(Feign): 仅换环境变量, 业务代码与 dubbo 完全相同"}[form]
    print("=== %s ===" % title)
    if not nacos:
        print("  [提示] 该形态需要 Nacos —— 本地: docker compose up -d nacos, 或 --nacos <addr>")
    spawn(common + ["-f", "service/sample-echo/pom.xml", "spring-boot:run",
                    "-Dspring-boot.run.profiles=micro"], env_extra, os.path.join(logs, "demo-echo.log"))
    time.sleep(3)
    spawn(common + ["-f", "service/sample-app/pom.xml", "spring-boot:run",
                    "-Dspring-boot.run.profiles=micro"], env_extra, os.path.join(logs, "demo-app.log"))
    check("sample-echo 启动(9081)", wait_port(9081))
    check("sample-app 启动(9082)", wait_port(9082))
    time.sleep(6)
    status, body = http_get("http://localhost:9082/app/greet?name=demo")
    data = json.loads(body).get("data", "") if body else ""
    check("GET :9082/app/greet(app→echo 经 %s)" % ("dubbo" if form == "dubbo" else "http"), 
          status == 200 and "sample-echo" in data, body[:80])
    kill_port(9081)
    kill_port(9082)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--form", required=True, choices=["mono", "dubbo", "http"])
    ap.add_argument("--nacos", default=os.environ.get("NACOS_SERVER_ADDR", "127.0.0.1:8848"))
    a = ap.parse_args()
    run(a.form, a.nacos, None)
    print("=== 结果: %s(PASS %d / FAIL %d) ===" % ("PASS" if FAIL == 0 else "FAIL", PASS, FAIL))
    sys.exit(0 if FAIL == 0 else 1)


if __name__ == "__main__":
    main()
