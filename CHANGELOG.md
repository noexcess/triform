# Changelog

All notable changes to Triform are documented here. Format follows [Keep a Changelog](https://keepachangelog.com); versioning is SemVer.

## [Unreleased]

### Added
- `triform-starter`: one dependency = full capability (zero annotations, zero component-scan wiring).
- Annotation-free consumer auto-configuration (`triform.rpc.consumer.enabled`, default true; classpath-wide `@RemoteFacade` scan with local-impl auto-skip).
- Governance SPI: `triform.rpc.resilience=none|resilience4j` (circuit breaker / bulkhead / rate limiter; timeout carried by HTTP client readTimeout).
- Configurable HTTP target mapping: `triform.rpc.http.service-names.<facadeFqn>=<serviceName>` (legacy `ticket-` derivation kept as fallback).
- Build-time wiring guards (provider must be a Spring Bean, no `@EnableDubbo` on boot classes, api modules must depend on the rpc layer, unique ports, no duplicate FQNs) + ArchUnit "api must not depend on impl" rule.
- Cross-platform smoke script (`scripts/smoke.py`) and docker-compose for micro infrastructure.
- `GeneralServiceBootstrap`: 3-line main for every service.

### Changed
- Modernized stack: JDK 17, Spring Boot 3.2.12, Spring Cloud 2023.0.5, Spring Cloud Alibaba 2023.0.3.2, Dubbo 3.3.4; sleuth replaced by micrometer-tracing (MDC `traceId` key unchanged).
- Neutral naming: packages `io.triform.*`, config keys `triform.rpc.*`, shared configs `triform-shared/defaults.yml`.
- `InternalRpcEndpoint` expose whitelist defaults to full classpath scan (token-gated); configurable via `triform.rpc.scan-packages`.

### Removed
- Host-project capabilities returned to the origin repository (security interceptors, web hardening, id/mq/datascope/menu/trafficlog, easyexcel/rocketmq/mybatis-plus deps) — Triform keeps only the rpc dependency-chain contracts (design doc 44).

### Fixed
- FacadeAutoExporter start-in-loop bug (only the first facade was exported; now batch-submit then start once, with per-interface dedup).
- Legacy `ticket.rpc` prefixes (no trailing dot) that escaped the neutral rename.
