#!/usr/bin/env bash
# =============================================================
# 新增微服务脚手架(基线/42 §8.2 L1)——一条命令替代 10 处手工改动
# 用法: bash scripts/new-service.sh --name verify2 --http-port 8092 --dubbo-port 20882
# 生成: service-api/xxx-api + service/xxx (契约/实现骨架, 手写部分)
# 修改: 根pom/service-pom/all-in-one-pom/gateway路由(幂等, 重复执行不重复插入)
# 之后: 手写契约接口与实现类(唯一增值部分); .run 配置按模板手工复制(可选)
# =============================================================
set -euo pipefail
cd "$(dirname "$0")/.."

NAME=""; HTTP_PORT=""; DUBBO_PORT=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --name) NAME="$2"; shift 2;;
    --http-port) HTTP_PORT="$2"; shift 2;;
    --dubbo-port) DUBBO_PORT="${2:-20880}"; shift 2;;
    *) echo "未知参数: $1"; exit 1;;
  esac
done
[[ -z "$NAME" || -z "$HTTP_PORT" ]] && { echo "用法: $0 --name <服务名> --http-port <端口> [--dubbo-port <端口>]"; exit 1; }
[[ "$NAME" =~ ^[a-z][a-z0-9-]*$ ]] || { echo "服务名须小写字母数字连字符: $NAME"; exit 1; }

API_MOD="service-$NAME-api"
SVC_MOD="service-$NAME"
SVC_NAME="ticket-$NAME"
PKG="io.triform.$NAME"
PKG_PATH="${PKG//.//}"
CAP="$(echo "$NAME" | awk -F- '{for(i=1;i<=NF;i++) printf "%s", toupper(substr($i,1,1)) substr($i,2)}')"
fail=0
for f in "service-api/$API_MOD" "service/$SVC_MOD"; do
  [ -e "$f" ] && { echo "✗ 已存在: $f"; fail=1; }
done
[ "$fail" = "1" ] && exit 1

echo "== 1/6 生成 service-api/__X__ 模块(契约骨架) =="
TPL_API=scripts/templates/service-api-module
API_DIR="service-api/$API_MOD"
mkdir -p "$API_DIR/src/main/java/$PKG_PATH/api/facade" "$API_DIR/src/main/java/$PKG_PATH/api/dto"
for tpl in "$TPL_API"/*.tpl; do
  out="$API_DIR/$(basename "$tpl" .tpl)"
  case "$(basename "$tpl" .tpl)" in
    Facade.java) out="$API_DIR/src/main/java/$PKG_PATH/api/facade/${CAP}Facade.java";;
    pom.xml) out="$API_DIR/pom.xml";;
    *) continue;;
  esac
  sed -e "s/__NAME__/$NAME/g" -e "s/__CAP__/$CAP/g" "$tpl" > "$out"
done
echo "  ✓ $API_MOD (${CAP}Facade 骨架, rpc-support 依赖已含)"

echo "== 2/6 生成 service/$SVC_MOD 模块(实现骨架) =="
TPL_SVC=scripts/templates/service-module
SVC_DIR="service/$SVC_MOD"
mkdir -p "$SVC_DIR/src/main/java/$PKG_PATH/impl" "$SVC_DIR/src/main/java/$PKG_PATH/boot" "$SVC_DIR/src/main/resources/db/migration"
for tpl in "$TPL_SVC"/*.tpl; do
  base="$(basename "$tpl" .tpl)"
  case "$base" in
    ServiceApplication.java) out="$SVC_DIR/src/main/java/$PKG_PATH/boot/Service${CAP}Application.java";;
    FacadeImpl.java) out="$SVC_DIR/src/main/java/$PKG_PATH/impl/${CAP}FacadeImpl.java";;
    application.yml) out="$SVC_DIR/src/main/resources/application.yml";;
    application-micro.yml) out="$SVC_DIR/src/main/resources/application-micro.yml";;
    pom.xml) out="$SVC_DIR/pom.xml";;
    *) continue;;
  esac
  sed -e "s/__NAME__/$NAME/g" -e "s/__CAP__/$CAP/g" -e "s/__HTTP_PORT__/$HTTP_PORT/g" -e "s/__DUBBO_PORT__/${DUBBO_PORT:-20880}/g" "$tpl" > "$out"
done
echo "  ✓ $SVC_MOD (启动类/实现/application/mono|micro)"

echo "== 3/6 注册 Maven 模块(幂等) =="
add_module() { local f="$1" m="$2"; python scripts/add-module.py "$f" "$m"; }
add_module service-api/pom.xml "$API_MOD"
add_module service/pom.xml "$SVC_MOD"

echo "== 4/6 module-all-in-one 聚合依赖(幂等) =="
python scripts/add-aio-dep.py "$NAME" all-in-one/pom.xml

echo "== 5/6 gateway 路由提示 =="
GW=gateway/src/main/resources/application.yml
grep -q "lb://$SVC_NAME" "$GW" && echo "  ✓ 路由已存在" || echo "  ⚠ 请在 $GW 参照 ticket-verify 条目加: id: $NAME / uri: lb://$SVC_NAME / predicates: Path=/$NAME/**"

echo "== 6/6 完成 =="
echo "✅ $NAME 服务骨架就绪。唯一增值部分: 在 $API_MOD 写契约方法, 在 $SVC_MOD 写实现。"
echo "   编译: mvn compile -pl $SVC_MOD -am"
echo "   启动: mono(被 all-in-one 聚合) / micro: 加 profile local,micro"
