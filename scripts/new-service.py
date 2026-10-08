#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Windows/mac/linux 通用脚手架(与 new-service.sh 完全等价):
   python scripts/new-service.py --name demo2 --http-port 8092 [--dubbo-port 20882]
"""
import argparse, io, os, re, sys, subprocess

AP = argparse.ArgumentParser()
AP.add_argument('--name', required=True)
AP.add_argument('--http-port', required=True)
AP.add_argument('--dubbo-port', default='20880')
a = AP.parse_args()
NAME, HTTP_PORT, DUBBO_PORT = a.name, a.http_port, a.dubbo_port
if not re.fullmatch(r'[a-z][a-z0-9-]*', NAME):
    print('服务名须小写字母数字连字符: ' + NAME); sys.exit(1)

API_MOD = 'service-%s-api' % NAME
SVC_MOD = 'service-%s' % NAME
SVC_NAME = 'ticket-%s' % NAME
PKG = 'io.triform.%s' % NAME
PKG_PATH = PKG.replace('.', '/')
CAP = ''.join(p[:1].upper() + p[1:] for p in NAME.split('-'))

for f in ('service-api/' + API_MOD, 'service/' + SVC_MOD):
    if os.path.exists(f):
        print('✗ 已存在: ' + f); sys.exit(1)

def render(tpl_path, out_path, pairs):
    t = io.open(tpl_path, encoding='utf-8').read()
    for k, v in pairs.items():
        t = t.replace('__%s__' % k, v)
    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    io.open(out_path, 'w', encoding='utf-8', newline='').write(t)

PAIRS = {'NAME': NAME, 'CAP': CAP, 'HTTP_PORT': HTTP_PORT, 'DUBBO_PORT': DUBBO_PORT}

print('== 1/6 生成 %s (契约骨架) ==' % API_MOD)
API_DIR = 'service-api/' + API_MOD
render('scripts/templates/service-api-module/Facade.java.tpl',
       API_DIR + '/src/main/java/%s/api/facade/%sFacade.java' % (PKG_PATH, CAP), PAIRS)
render('scripts/templates/service-api-module/pom.xml.tpl', API_DIR + '/pom.xml', PAIRS)
print('  ✓ %s (%sFacade 骨架, rpc-support 依赖已含)' % (API_MOD, CAP))

print('== 2/6 生成 %s (实现骨架) ==' % SVC_MOD)
SVC_DIR = 'service/' + SVC_MOD
M = 'scripts/templates/service-module/'
render(M + 'ServiceApplication.java.tpl', SVC_DIR + '/src/main/java/%s/boot/Service%sApplication.java' % (PKG_PATH, CAP), PAIRS)
render(M + 'FacadeImpl.java.tpl',         SVC_DIR + '/src/main/java/%s/impl/%sFacadeImpl.java' % (PKG_PATH, CAP), PAIRS)
render(M + 'application.yml.tpl',         SVC_DIR + '/src/main/resources/application.yml', PAIRS)
render(M + 'application-micro.yml.tpl',   SVC_DIR + '/src/main/resources/application-micro.yml', PAIRS)
render(M + 'pom.xml.tpl',                 SVC_DIR + '/pom.xml', PAIRS)
print('  ✓ %s (启动类/实现/application/mono|micro)' % SVC_MOD)

print('== 3/6 注册 Maven 模块(幂等) ==')
for f, m in (('service-api/pom.xml', API_MOD), ('service/pom.xml', SVC_MOD)):
    subprocess.run([sys.executable, 'scripts/add-module.py', f, m], check=True)

print('== 4/6 all-in-one 聚合依赖(幂等) ==')
subprocess.run([sys.executable, 'scripts/add-aio-dep.py', NAME, 'all-in-one/pom.xml'], check=True)

print('== 5/6 gateway 路由提示 ==')
GW = 'gateway/src/main/resources/application.yml'
gw = io.open(GW, encoding='utf-8').read() if os.path.exists(GW) else ''
if ('lb://' + SVC_NAME) in gw:
    print('  ✓ 路由已存在')
else:
    print('  ⚠ 请在 %s 参照现有条目加: id: %s / uri: lb://%s / predicates: Path=/%s/**' % (GW, NAME, SVC_NAME, NAME))

print('== 6/6 完成 ==')
print('✅ %s 服务骨架就绪。唯一增值部分: 在 %s 写契约方法, 在 %s 写实现。' % (NAME, API_MOD, SVC_MOD))
print('   编译: mvn compile -pl %s -am' % SVC_MOD)
print('   启动: mono(被 all-in-one 聚合) / micro: 加 profile local,micro')
