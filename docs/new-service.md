# Adding a microservice

## Scaffold (recommended)

```bash
bash scripts/new-service.sh --name demo2 --http-port 8092 [--dubbo-port 20882]
```

Generates `service-api/demo2-api` (contract skeleton) and `service/demo2` (3-line-main boot, ymls, impl skeleton), and registers root pom / aggregator poms / all-in-one dependency / gateway route — idempotent, safe to re-run. You hand-write only:

1. the contract: a `@RemoteFacade` interface returning `Result<T>` + DTOs;
2. the implementation: a plain `@Service` class.

In micro mode `FacadeAutoExporter` exports it automatically; in mono it is simply a local bean.

## Adding a cross-service call (existing services)

Two files: the contract in the provider's `*-api` module, the `@Service` implementation in the provider. Consumers add a dependency on the provider's `*-api` module and constructor-inject the interface. No annotations, no config, identical in all three forms.

## Wiring guards (build fails, not silent breakage)

`triform-testing` enforces at build time:

- a Facade implementation must be a Spring Bean (otherwise nothing exports it);
- boot classes must NOT carry `@EnableDubbo` (the programmatic exporter is the single export path);
- `*-api` modules containing `@RemoteFacade` must depend on `triform-rpc`;
- HTTP and Dubbo ports must be unique across modules (declared sharing list allowed);
- no duplicate FQNs across own modules (mono loads one of them, unpredictably);
- (in `all-in-one` tests) ArchUnit: `..api..` must not depend on implementation packages.

## Run configurations

Copy any `.run/*.run.xml` and adjust module/main-class. Naming convention: `[BE-n]` backend, `[BE-nh]` HTTP-transport variant, `[FE-n]` frontends, `[ALL]` one-click full stack. The scaffold prints the ports to register.
