# 来源审计（T16 / Apache-2.0 前置）

> 审计日期：2026-10-07 · 审计范围：triform 全部源码与依赖

## 1. 源码来源

- 全部源码由本团队在本仓库历史中编写（演进过程见《设计基线》10~44），**无 yudao / RuoYi / 任何第三方项目拷贝**。
- 机械核查（2026-10-07）：`yudao|ruoyi|芋道` 关键字 0 命中；`Copyright`/`@author` 他人版权头 0 命中；历史包名 `com.p0` 主源码 0 残留。
- 历史渊源说明：本底座前身是团队自有项目 ticket-platform 的公共模块（自研），2026-10 抽取并中立化（包名 `io.triform`）。业务仓库 ticket-platform 通过 Maven 依赖消费本底座（同血统，见设计基线/43 §11）。

## 2. 依赖许可证（均为 Apache-2.0 兼容）

| 依赖 | 许可证 | 范围 |
|---|---|---|
| Spring Boot / Spring Framework / Spring Cloud | Apache-2.0 | 编译+运行 |
| Dubbo / Nacos client | Apache-2.0 | 编译+运行 |
| OpenFeign / OkHttp | Apache-2.0 | HTTP 传输 |
| resilience4j | Apache-2.0 | 可选治理 |
| micrometer / micrometer-tracing / brave | Apache-2.0 | 可观测 |
| Jackson / Tomcat / SnakeYAML / Logback | Apache-2.0 / EPL+LGPL 双许可 | 传递依赖 |
| Lombok | MIT | provided |
| JUnit / Spring Boot Test | EPL-2.0 等 | 仅 test 范围 |

无 GPL/AGPL/CC 类不兼容来源；无运行期 Copyleft 依赖。

## 3. 遗留约定

- **T15 定稿（2026-10-09）**：项目名 `triform` 保留（语义=三形态；Java/Spring 生态无撞名）；仓库 `github.com/noexcess/triform`（Gitee 同名镜像）；**发布 groupId 上传后改 `io.github.noexcess`**（本地保持 `io.triform`）；**Java 包名恒为 `io.triform.*`**——源码坐标系与发布坐标系分离，保障 1.x/2.x 双版本线共享包名（cherry-pick 翻译面不因发布坐标变化）。
- 敏感信息已于 2026-10-07 清理（triform-defaults.yml 全部占位符化，仓库内 grep 无真实 IP/口令）。
