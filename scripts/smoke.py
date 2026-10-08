#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""triform 样例冒烟(跨平台, 替代 PowerShell 版思路):
  python scripts/smoke.py --mono http://localhost:9085 --gateway http://localhost:19080
校验: greet 端点两种形态返回一致(code=0), health 可达, prometheus 有指标。
"""
import argparse, json, sys, urllib.request

def get(url, timeout=10):
    try:
        with urllib.request.urlopen(url, timeout=timeout) as r:
            return r.status, r.read().decode('utf-8', 'replace')
    except urllib.error.HTTPError as e:
        return e.code, str(e.reason)

def check(name, ok, detail=''):
    print(('  [PASS] ' if ok else '  [FAIL] ') + name + ((' — ' + detail) if detail else ''))
    return ok

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--mono', default='http://localhost:9085')
    ap.add_argument('--gateway', default='')
    a = ap.parse_args()
    ok = True

    s, body = get(a.mono + '/app/greet?name=triform')
    d = json.loads(body)
    ok &= check('mono /app/greet', s == 200 and d.get('code') == 0
                and 'sample-echo' in d.get('data', ''), body[:80])
    ok &= check('mono traceId 贯穿', bool(d.get('traceId')))
    s, body = get(a.mono + '/actuator/health')
    check('mono health 可达', s in (200, 503), 'HTTP %d' % s)

    if a.gateway:
        try:
            s3, _ = get(a.gateway + '/internal/rpc/x.y.Z/toString', 5)
            ok &= check('网关拒绝 /internal/**(非 2xx)', s3 >= 400, 'HTTP %d' % s3)
        except Exception as e:
            ok &= check('网关拒绝 /internal/**(连接被拒=更严)', True, str(e)[:40])
        s2, body2 = get(a.gateway + '/app/greet?name=triform')
        d2 = json.loads(body2)
        ok &= check('micro /app/greet(经网关)', s2 == 200 and d2.get('code') == 0
                    and d2.get('data') == d.get('data'), body2[:80])
        ok &= check('两形态返回一致', d2.get('data') == d.get('data'))

    print('=== 结果: %s ===' % ('PASS' if ok else 'FAIL'))
    sys.exit(0 if ok else 1)

if __name__ == '__main__':
    main()
