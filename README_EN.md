# Triform

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)

> **one codebase, three run forms. nothing else.**

[中文说明](README.md)

**One codebase, two config keys, three run forms** — monolith (mono), microservices over Dubbo, microservices over HTTP.

**Drop-in capability**: add one `triform-starter` dependency, keep your boot class a plain `@SpringBootApplication` with a 3-line main, constructor-inject `@RemoteFacade` interfaces — zero Triform annotations, zero component-scan wiring, zero glue config (proven by the standalone `triform-lab` project).

Use `io.triform:triform-starter-nacos` (recommended; adds Nacos discovery/config for micro) or plain `triform-starter` for monolith-only / custom registries.

```java
// ① Contract: @RemoteFacade marks an interface as remotely consumable — the only marker you remember
@RemoteFacade
public interface GreetingFacade { Result<String> greet(String name); }

// ② Provider: plain @Service; exported automatically in micro mode (zero transport annotations)
@Service
public class GreetingFacadeImpl implements GreetingFacade { ... }

// ③ Consumer: constructor injection — mono injects the local bean, micro injects a remote proxy
public GreetController(GreetingFacade greetingFacade) { ... }
```

| Form | Config | Cross-service call goes over |
|---|---|---|
| mono | `deployment.mode=mono` | in-process local bean |
| micro · Dubbo | `deployment.mode=micro` | Dubbo (interface-level, Nacos) |
| micro · HTTP | + `triform.rpc.transport=http` | Feign(OkHttp) → provider `/internal/rpc/**` (token-gated) |

## Onboarding configuration (mechanism is zero-wiring; environment facts are yours)

| Form | Required config |
|---|---|
| mono | **none** (Dubbo excluded; service registration auto-disabled by the base) |
| micro · dubbo | 1–2 items: `NACOS_SERVER_ADDR` (has a default) + service port |
| micro · http | + 3 env vars: `TRIFORM_RPC_TRANSPORT=http`, provider `TRIFORM_RPC_HTTP_EXPOSE=true`, both `TRIFORM_RPC_INTERNAL_TOKEN`; optional target mapping `triform.rpc.http.service-names.<facadeFqn>=<serviceName>` |

## End-to-end onboarding

See [docs/getting-started.md](docs/getting-started.md).

## Three onboarding rules (learned the hard way)

1. **Put the boot class in the module root package** — `@SpringBootApplication` scans its own package only.
2. **Facade methods return `Result<T>`** — the wire envelope; HTTP transport deserializes it.
3. **Aggregator shells are the exception** — an all-in-one shell spans modules and needs explicit `@ComponentScan`; ordinary service boot classes do not.

## Modules

- `triform-core` — unified Result/exceptions, `IamContext` + session contracts, deployment guards, config entry chain
- `triform-rpc` — `@RemoteFacade`, consumer auto-proxying (mono local / micro Dubbo / micro HTTP), programmatic provider export, context propagation, 3-line main bootstrap
- `triform-rpc-resilience4j` (optional) — circuit breaker / bulkhead / rate limiter via `triform.rpc.resilience=resilience4j`
- `triform-testing` — build-time wiring guards

Triform deliberately does **only** transport & deployment forms. Auth, web hardening, id generation, MQ, data-scope, menus — those belong to the host project.

## When Triform is / isn't for you

**A good fit** if any of these hurt today:
- The same business code must ship as both monolith and microservices, currently kept in sync by hand;
- Transport annotations (`@DubboService`/`@FeignClient`) leak into contracts and business code, making transport changes invasive;
- New projects want to start as a monolith without "splitting later = rewriting".

**Not a fit** — use more mature options instead:
- Pure microservices forever, never shipping a monolith → plain Spring Cloud;
- Module governance inside a single application → Spring's modular tooling;
- An out-of-the-box admin scaffold (code generation, admin features) → admin scaffold projects;
- Service mesh / polyglot interop → mesh solutions.

**Semantic boundary**: switching forms switches deployment & transport, not business semantics automatically — transactions, object references, timeouts, scheduler counts differ across forms. See the host repo's `docs/semantics.md` equivalent before promoting to production.

## License

Apache-2.0 (provenance audit: [docs/PROVENANCE.md](docs/PROVENANCE.md)).

**Author**: [noexcess](https://github.com/noexcess) · repo: `github.com/noexcess/triform`

## Publishing coordinates

- Maven Central (planned): `io.github.noexcess:triform-*`
- Local/current: `io.triform:triform-*` (**Java packages stay `io.triform.*` regardless of groupId** — source coordinate and publication coordinate are deliberately separated so the 1.x/2.x lines share one package namespace)
