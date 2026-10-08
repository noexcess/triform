#!/usr/bin/env bash
# 一键三形态演示(E4) —— sh 版薄壳, 实现在 demo.py(跨平台):
#   bash scripts/demo.sh --form mono | dubbo | http [--nacos <addr>]
exec python3 "$(dirname "$0")/demo.py" "$@" 2>/dev/null || python "$(dirname "$0")/demo.py" "$@"
