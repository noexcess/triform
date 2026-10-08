# Concepts: how the three forms work

## The asymmetry problem

A provider in mono is just a Spring bean; in micro it must also be an RPC endpoint. A consumer in mono injects the local bean; in micro it needs a proxy. Traditional stacks solve this with transport annotations on the contract (`@DubboService`, `@FeignClient`) plus per-mode code paths. Triform absorbs the asymmetry in two mechanisms:

1. **Consumer side — `DubboConsumerRegistrar`** (auto-configured, no annotation needed):
   - scans the classpath for `@RemoteFacade` interfaces;
   - skips any interface that already has an implementation class on the process classpath (it IS the provider here → mono gets the local bean);
   - otherwise registers a proxy bean: `DubboRefFactoryBean` (dubbo) or `HttpRefFactoryBean` (http) depending on `triform.rpc.transport`.

2. **Provider side — `FacadeAutoExporter`** (micro only):
   - scans Spring beans implementing `@RemoteFacade` interfaces and exports them programmatically via `ServiceConfig` (batch submit, then `DubboBootstrap.start()` once).
   - mono excludes Dubbo entirely, so the same `@Service` class is simply a local bean.

## Configuration keys

| Key | Values | Effect |
|---|---|---|
| `deployment.mode` | mono / micro | master switch |
| `triform.rpc.transport` | dubbo (default) / http | consumer proxy type |
| `triform.rpc.consumer.enabled` | true (default) / false | consumer auto-registration |
| `triform.rpc.http-expose` | true / false (default) | provider: expose `/internal/rpc/**` |
| `triform.rpc.internal-token` | string | internal RPC auth token |
| `triform.rpc.resilience` | none (default) / resilience4j | governance SPI |
| `triform.rpc.http.service-names.<facadeFqn>` | service name | HTTP 目标显式覆盖（可选）。缺省三级解析：本配置 > 注册中心元数据 `triform-facades`（提供方自动写入）> legacy 包名约定；多命中 fail-fast |
| `triform.rpc.scan-packages` | packages (default: all) | expose whitelist scan / 候选发现与实现扫描的附加范围 |
| `META-INF/triform-facades` | 索引文件（推荐由 `triform-indexer` 注解处理器编译期生成，也可手工维护） | 候选发现首选路径（毫秒级）；缺失回退全扫（数秒级 WARN，进程级缓存） |

## Context propagation

`IamContext` (user/session/permissions) travels with every call in all three forms: mono = same-thread ThreadLocal; micro-dubbo = Dubbo filter attachments (`IamContextConsumerFilter`/`ProviderFilter`); micro-http = HTTP headers (`HttpRpcContextFilter`/`HttpRpcOutboundInterceptor`). Business code never branches on mode.

## What Triform does NOT do

Auth (how the context gets loaded), web hardening, id generation, MQ, data-scope, menus — host-project concerns. The base keeps only what the rpc chain needs (see `docs/PROVENANCE.md` and design doc 44).
