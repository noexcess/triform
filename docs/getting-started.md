# Getting Started: 一条龙接入（从下载到新增微服务）

> 前置：JDK 17+、Maven 3.6+。Nacos 仅 micro 形态需要（本地起：仓库根 `docker compose up -d nacos`）。

## 第 0 步：下载并构建底座

```bash
git clone <triform 仓库地址>
cd triform
mvn install            # 或 mvn -s .mvn-settings.xml install
```

构建产物进入本地 Maven 仓库：`io.triform:triform-starter`（传递带入 core + rpc + nacos-discovery）与 `triform-rpc-resilience4j`（可选治理）。此后你的项目与底座的关系 = 普通第三方库。

## 第 1 步：新建一个普通 SpringBoot 项目

用你惯用的方式建 `spring-boot-starter-parent: 3.2.x` 项目，pom 只加一个依赖：

```xml
<!-- 推荐(mono/micro 通吃, 含 Nacos): -->
<dependency>
    <groupId>io.triform</groupId>
    <artifactId>triform-starter-nacos</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
<!-- 仅单体形态或自选注册中心时可用纯 triform-starter(micro 下缺 Nacos 客户端会有启动 WARN 指路) -->
```

启动类（**放模块根包**，如 `com.yourco.app.LabApplication`——`@SpringBootApplication` 默认扫描自身包及子包，boot 塞进 `xxx.boot` 子包会漏扫其他包）：

```java
@SpringBootApplication
public class LabApplication {
    public static void main(String[] args) {
        GeneralServiceBootstrap.run(LabApplication.class, args);   // 或 SpringApplication.run，等价
    }
}
```

到此 mono 形态已经完整可用——**没有任何 triform 注解、没有任何扫描配置**。

## 第 2 步：定义跨服务契约（唯一手写物 ①）

新建一个契约模块（如 `your-app-api`），只依赖 triform-starter。
**顺手引入索引处理器**：api 模块加 `io.triform:triform-indexer`（provided 依赖），编译期自动生成 `META-INF/triform-facades` 索引——消费方启动毫秒级发现契约，且索引永不遗漏。不引入也能用，但注意发现优先级：**索引 ∪ scan-packages 任一命中即不再全扫**——所以"新旧 API 混用"时，未索引且未配 scan-packages 的旧模块会漏发现（启动少注册代理、注入处报 NoSuchBeanDefinition）。混用期请给旧模块也配处理器/手工索引，或把旧包写进 `triform.rpc.scan-packages`。完全没有索引与 scan-packages 时才回退全扫（数秒级 WARN）。

```java
@RemoteFacade
public interface OrderFacade {
    Result<String> submit(String name);     // 约定：Facade 方法返回 Result<T>（HTTP 传输的信封）
}
```

## 第 3 步：实现提供方（唯一手写物 ②）

在提供方服务里写一个普通 `@Service`：

```java
@Service
public class OrderFacadeImpl implements OrderFacade {
    public Result<String> submit(String name) {
        return Result.ok("submitted: " + name);
    }
}
```

消费方在自己的 pom 里依赖 `your-app-api`，然后**构造器注入**即可（Controller / Service 任意层）：

```java
public class OrderController(OrderFacade orderFacade) {
    public Result<String> submit(...) { return orderFacade.submit(...); }
}
```

注意：**本进程 classpath 上有实现类的接口自动视为本地服务**（Registrar 跳过代理注册），所以 mono 下注入的是本地 Bean、跨进程时注入远程代理——业务代码完全一致。

## 第 4 步：选形态（两个键）

### 4a. 单体（mono）——默认，0 项配置

不配即 mono。多个服务想装一个进程？做一个聚合模块依赖各服务模块（参考 triform 仓库的 `all-in-one`），聚合壳需要**显式 `@ComponentScan` 扫多模块包**（这是壳的职责，业务服务自身不需要）。

### 4b. 微服务 · Dubbo——`deployment.mode=micro` + 1~2 项

`application-micro.yml`：

```yaml
deployment:
  mode: micro

spring:
  application:
    name: your-service            # Nacos 注册名
  cloud:
    nacos:
      discovery:
        server-addr: ${NACOS_SERVER_ADDR:127.0.0.1:8848}

dubbo:
  application:
    name: your-service
    register-mode: interface       # 推荐：接口级注册
  registry:
    address: nacos://${NACOS_SERVER_ADDR:127.0.0.1:8848}
  protocol:
    name: dubbo
    port: 20901                    # 每服务一个独立端口
```

启动：`mvn spring-boot:run -Dspring-boot.run.profiles=micro`。消费方无需任何配置——自动发现 provider 并注册 Dubbo 代理（IDEA .run 里配 `local,micro` 两个 profile 即可）。

### 4c. 微服务 · HTTP（Feign）——再加 3 个环境变量

```
双方:  TRIFORM_RPC_INTERNAL_TOKEN=<双方一致的令牌>
提供方: TRIFORM_RPC_HTTP_EXPOSE=true
双方:  TRIFORM_RPC_TRANSPORT=http
```

目标服务名**无需配置**：消费方三级解析（显式配置 `triform.rpc.http.service-names.<facadeFqn>` > 注册中心元数据自动发现 > legacy 包名约定）。同一 Facade 被多个服务声明时启动后首次调用即 fail-fast，按提示显式配置即可。

业务代码与 4b 完全相同——这就是“一键切换”。

### 治理（可选）

```xml
<dependency>
    <groupId>io.triform</groupId>
    <artifactId>triform-rpc-resilience4j</artifactId>
</dependency>
```

```yaml
triform:
  rpc:
    resilience: resilience4j     # 熔断 10 次起判/50%/开 10s；舱壁 50；限流 200 QPS
```

## 第 5 步：新增一个微服务

**方式 A（推荐，triform 仓库内）**：

```bash
bash   scripts/new-service.sh  --name demo2 --http-port 8092 [--dubbo-port 20882]  # mac/linux/git-bash
python scripts/new-service.py  --name demo2 --http-port 8092 [--dubbo-port 20882]  # Windows 原生(等价)
```

生成契约骨架 + 服务骨架，并自动注册根 pom / 聚合 pom / all-in-one / 网关路由（幂等可复跑）。你手写契约与实现两个文件即完成。

**方式 B（你自己的项目里，跨仓库）**：按第 2、3 步再建一对“api 模块 + 服务模块”即可——服务之间**只依赖对方的 api 契约模块**，不要依赖实现模块。

## 语义边界（切形态前必读）

传输切换 ≠ 业务语义自动一致：事务传播/对象引用/超时失败/定时任务次数在三形态下有差异，见 [semantics.md](semantics.md)。

## 验证与排障

- 启动日志看两行：`[rpc] 契约装配完成: 传输=..., 候选 N 个, 注册远程代理 M 个` 与 `[rpc-export]`（micro 提供方）；
- `/actuator/prometheus`（需暴露）与 info 端点显示当前 transport/mode；
- 构建期守卫：provider 不是 Spring Bean、启动类误带 @EnableDubbo、api 缺依赖、端口冲突、跨模块同 FQN——全部构建失败并直接告诉你改哪里；
- mono 与 micro 共用会话/数据时注意：Nacos 与 Redis 等基础设施属于宿主环境事实，与底座无关。

## 常见坑（都真实踩过）

| 坑 | 规约 |
|---|---|
| boot 类放 `xxx.boot` 子包 | 放模块**根包** |
| Facade 返回裸类型 | 返回 `Result<T>`（HTTP 传输信封） |
| 聚合壳不加扫描 | 壳显式 `@ComponentScan`，业务服务不用 |
| 多服务声明同一 Facade（灰度/双跑） | 首次调用 fail-fast，显式配 `triform.rpc.http.service-names.*` 指定归属 |
