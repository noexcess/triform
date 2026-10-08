# Triform

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)

> **one codebase, three run forms. nothing else.**

**同一份业务代码，两个配置键，三种运行形态**——单体（mono）、微服务·Dubbo、微服务·HTTP。

**作者**: [noexcess](https://github.com/noexcess) · 仓库: `github.com/noexcess/triform`（Gitee 同名镜像）

**接入即能力**：业务项目引一个 `triform-starter` 依赖，启动类纯 `@SpringBootApplication` + 3 行 main，注入 `@RemoteFacade` 接口即用——零接线、零扫描配置（triform-lab 独立项目实证；契约上唯一要记的标记是 @RemoteFacade）。

```xml
<!-- micro(推荐, 含 Nacos 发现/配置): -->
<dependency>
    <groupId>io.triform</groupId>
    <artifactId>triform-starter-nacos</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
<!-- 或只要单体(mono)/自选注册中心: triform-starter(纯 core+rpc) -->
```

```java
// ① 契约：标 @RemoteFacade 的接口即「可被跨服务消费」——唯一需要记的标记
@RemoteFacade
public interface GreetingFacade { Result<String> greet(String name); }

// ② 提供方：只有 @Service；micro 下自动导出为 Dubbo/HTTP provider，零传输注解
@Service
public class GreetingFacadeImpl implements GreetingFacade { ... }

// ③ 消费方：构造器注入（Controller/Service 均可），mono 注入本地 Bean、micro 注入远程代理
public GreetController(GreetingFacade greetingFacade) { ... }
```

| 形态 | 配置 | 跨服务调用实际走 |
|---|---|---|
| mono 单体 | `deployment.mode=mono` | 进程内本地 Bean |
| micro · Dubbo | `deployment.mode=micro` | Dubbo 协议（接口级注册到 Nacos） |
| micro · HTTP | 同上 + `triform.rpc.transport=http` | Feign(OkHttp) 调提供方 `/internal/rpc/**`（令牌鉴权） |

## 接入配置表（机制零接线，环境事实由宿主声明）

| 形态 | 宿主需配置 | 说明 |
|---|---|---|
| **mono** | **0 项** | Dubbo 自动排除、不连 Nacos、注册自动关闭 |
| **micro · dubbo** | 1~2 项：`NACOS_SERVER_ADDR`（有默认值）+ 服务端口 | dubbo 端口/注册中心默认值已内置 |
| **micro · http** | 再 +3 个环境变量：`TRIFORM_RPC_TRANSPORT=http`、提供方 `TRIFORM_RPC_HTTP_EXPOSE=true`、双方 `TRIFORM_RPC_INTERNAL_TOKEN` | 目标服务名零配置：注册中心元数据自动发现（显式 `service-names` 仅作覆盖） |
| 集中配置（可选） | 宿主只留 Nacos 地址，其余进配置中心 | `triform-shared.yml → triform-defaults.yml → Nacos` 导入链 |

mono **无需关注册**：底座在非 micro 模式自动关闭服务注册（最低优先级默认值，显式配置可覆盖）。

## 一条龙接入（下载→编译→引入→配置→新增服务）

见 [docs/getting-started.md](docs/getting-started.md)。

## 三条接入规约（踩坑沉淀）

1. **启动类放模块根包**：`@SpringBootApplication` 默认扫描启动类所在包及子包——boot 放 `lab.boot` 子包会漏扫 `lab.web`（独立项目实测踩过）。
2. **Facade 方法返回 `Result<T>`**：这是底座的线上信封约定——HTTP 传输按 Result 反序列化，返回裸类型会在消费端反序列化失败。
3. **聚合壳例外**：all-in-one 这类跨模块打包壳需要显式 `@ComponentScan`（扫多模块包）；业务服务自身的启动类不需要。

## 30 秒上手

一键三形态演示（克隆后即可跑，mono 零基础设施）：

```bash
python scripts/demo.py --form mono                 # 单体
python scripts/demo.py --form dubbo --nacos <addr>  # 微服务·Dubbo
python scripts/demo.py --form http  --nacos <addr>  # 微服务·HTTP（仅换环境变量）
```

```bash
# 构建（主线 JDK 17 / Spring Boot 3.2.12 / Dubbo 3.3.4）
mvn -s .mvn-settings.xml install

# ① 单体形态：全部服务一个进程（all-in-one 聚合壳已配显式扫描）
mvn -s .mvn-settings.xml -f all-in-one/pom.xml spring-boot:run
curl "http://localhost:9085/app/greet?name=triform"     # → {"code":0,...}

# ② 微服务形态(Dubbo)：两个服务独立进程（无网关即可互调）
mvn -s .mvn-settings.xml -f service/sample-echo/pom.xml spring-boot:run -Dspring-boot.run.profiles=micro
mvn -s .mvn-settings.xml -f service/sample-app/pom.xml spring-boot:run -Dspring-boot.run.profiles=micro
curl "http://localhost:9082/app/greet?name=triform"     # → 与 mono 返回一致

# ③ 微服务形态(HTTP)：换 3 个环境变量，业务代码零改动（.run 的 [LAB-1h/2h] 已内置）
```

独立最小验证见 `triform-lab`（lab-api / lab-provider / lab-consumer 三个模块 + `.run` 四配置）：两个只依赖 starter 的微服务，dubbo 与 http 双传输实测 `code=0`。

## 新增一个微服务

```bash
bash scripts/new-service.sh --name <服务名> --http-port <端口> [--dubbo-port <端口>]
```

自动生成契约骨架 + 服务骨架并注册根 pom / 聚合 pom / all-in-one 依赖 / 网关路由（幂等可复跑）。之后手写契约（返回 `Result<T>`）与实现即可——micro 下导出自动完成。

## 构建期守卫

`triform-testing` 把「靠人记」的接线陷阱变成构建失败：provider 必须是 Spring Bean、启动类禁止 `@EnableDubbo`（防双导出路径）、api 模块必须依赖 rpc 层、端口唯一性、同 FQN 类重复。CI 默认执行；all-in-one 测试里另有 ArchUnit「api 不依赖 impl」护栏。

## 治理（可选）

加 `triform-rpc-resilience4j` 依赖 + `triform.rpc.resilience=resilience4j` 一键开启熔断（10 次起判/50%失败率/开10s）、舱壁（50 并发）、限流（200 QPS）；缺省零治理零依赖。超时由 HTTP 客户端 readTimeout 承担。

## 什么时候需要 / 不需要 triform

**适合**——如果你正被这些痛点之一困扰：
- 同一套业务要交付两种形态：大客户微服务、小客户单体包，现在靠手工同步两套代码或打包配置；
- 微服务代码里到处是 `@DubboService`/`@FeignClient`，想换传输方式要改契约和业务代码；
- 新项目想先单体快跑、又不想将来"拆微服务=重写"。

**不适合**——以下场景请直接用更成熟的方案：
- 纯微服务、永远不会交付单体 → 标准 Spring Cloud 全家桶即可；
- 只想在**单个应用内部**做模块治理 → Spring 生态的模块化方案更合适；
- 要的是**带管理后台的开箱脚手架**（代码生成、后台功能）→ 各类管理脚手架项目更对口；
- 需要服务网格、多语言互调等重治理能力 → 服务网格类方案。

**语义边界**：形态切换切换的是部署与传输，不自动等价业务语义——事务传播、对象引用、超时失败、定时任务次数在三形态下有差异，切生产前请读 [docs/semantics.md](docs/semantics.md)。

## Roadmap

- [x] 三形态机制 + 编程式导出 + classpath 自动判别
- [x] starter 化：一个依赖 + 零注解 + 零扫描（lab 双传输实证）
- [x] 治理 SPI：`triform.rpc.resilience=none|resilience4j`
- [x] 瘦身定稿：core 只保留 rpc 依赖链契约（设计基线/44）
- [x] 评审修复与统一发现（FacadeDiscovery 单口径 + 启动期契约校验 + indexer 注解处理器 + 一键 demo）
- [x] 三形态语义边界文档（docs/semantics.md）
- [ ] JDK17 主线上传 GitHub（命名/groupId/来源审计定稿后）
- [ ] 跨平台演示环境（docker-compose 已备，一键脚本待接）

## License

Apache-2.0（来源审计：[docs/PROVENANCE.md](docs/PROVENANCE.md)）。

## 发布坐标

- Maven Central（计划）: `io.github.noexcess:triform-*`
- 本地/当前: `io.triform:triform-*`（**Java 包名恒为 `io.triform.*`，不随 groupId 变化**——源码坐标系与发布坐标系分离，1.x/2.x 双版本线共享同一套包名）
