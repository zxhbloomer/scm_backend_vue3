# AGENTS.md — SCM 后端项目记忆

DSH 的项目记忆入口。技术事实以本仓库代码与配置为准；与旧版 CLAUDE.md / AGENTS.md 冲突时以本文件为准。

## 技术栈与运行入口

| 项 | 值 |
|---|---|
| 构建 | Maven 多模块，根 `scm-parent`，groupId `com.xinyirun`，version `0.0.1-SNAPSHOT` |
| 框架 | Spring Boot 4.1.2（`pom.xml` parent），Java 21（`pom.xml` `java.version`） |
| 启动类 | `com.xinyirun.scm.starter.SystemServerStart`（`scm-start`） |
| 端口 | 8088，context-path `/scm`（`scm-start/src/main/resources/application-dev.yml:3-6`） |
| Profiles | `dev`（默认）、`centos-ys`、`prod`、`zlprod`（`application.yml` 切换） |
| Flowable | 8.x，`database-schema-update: false`，表由 `00_doc/sql/flowable-8.0.0-ddl/` 脚本建 |

## 模块结构

依赖方向：controller → core → bean/common。

| 层次 | 模块 |
|---|---|
| 数据模型 | `scm-bean`（Entity/DTO/VO） |
| 公共基础 | `scm-common`（注解、常量、工具、异常） |
| 业务 | `scm-core`、`scm-core-api`、`scm-core-app`、`scm-core-bpm`、`scm-core-tenant`、`scm-core-clickhouse` |
| 接口 | `scm-controller`、`scm-controller-api`、`scm-controller-app`、`scm-controller-bpm`、`scm-controller-tenant` |
| 基础设施 | `scm-security`、`scm-redis`、`scm-excel`、`scm-quartz`、`scm-mq`、`scm-mq-consumer`、`scm-framework`、`scm-report` |
| AI | `scm-ai`（Spring AI + MCP + 工作流） |
| 启动 | `scm-start`；代码生成器 `00autoCreateCode`（`CodeGenerator`） |

已在根 `pom.xml` 注释掉、但目录仍在：`scm-core-whapp`、`scm-core-mongodb`、`scm-controller-whapp`、`scm-start-parent`。

## 常用命令

```bash
# 构建全部模块（跳过测试）
mvn -T 1C clean install -DskipTests

# 本地启动（dev profile）
mvn -pl scm-start -am spring-boot:run -Dspring-boot.run.profiles=dev

# 打包后启动
mvn -pl scm-start -am package -DskipTests
java -jar scm-start/target/scm-start-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev

# 单模块
mvn -pl <module> test
mvn -pl <module> install

# 代码生成（按数据库表产出 entity/mapper/xml）
cd 00autoCreateCode && mvn exec:java -Dexec.mainClass="CodeGenerator"
```

## 编码规范

- 4 空格缩进、UTF-8、禁 tab；import 有序、不留未使用代码；Lombok（`@Getter`/`@Setter`/`@Slf4j`）。
- 命名：包小写；类/枚举 PascalCase；字段/方法 camelCase；Controller `*Controller`、Service `*Service`、Mapper `*Mapper`、Entity `*Entity`、入参出参 `*Request`/`*Response`；沿用既有 `I*` 接口前缀（如 `IMessageSendService`）。

### 后端对象规范（硬性）

- 实体类与数据库表一一对应，不得随意加字段；加字段需先批准。
- 查询结果一律用 VO 接收；**Mapper 与 Service 的查询结果不得用实体类接收**，发现即视为不合规。实体类只用于新增、修改、删除。
- 不新建 bo，只用 Vo。页面级扩展数据挂到对应业务 Vo 的子对象字段（子 Vo 单独文件、与业务 Vo 同目录，字段加 `@JsonInclude(NON_NULL)`）。

## 关键架构约定

- 多数据源 + 多租户：`@DataSourceAnnotion("tenant")` 切租户库，`DynamicDataSourceContextHolder` 管上下文，租户配置读 `s_tenant_manager`。
- 自定义注解（`scm-common/annotations`）：`@SysLogAnnotion`/`@OperationLogAnnotion` 操作日志、`@DataSourceAnnotion` 数据源、`@DataScopeAnnotion` 数据权限、`@RepeatSubmitAnnotion` 防重、`@LimitAnnotion` 限流。
- Controller 基类（`scm-framework`）：系统端继承 `SystemBaseController`（Session），App 端继承 `AppBaseController`（JWT）；新建 Controller 必须继承对应基类。
- MyBatis Plus 拦截器：`MyBatisAutoFillHandel`（create_time/update_time/operator 自动填充）、`DataChangeInterceptor`（数据变更记录）、`DataScopeInterceptor`（数据权限 SQL）。
- 新增业务模块流程：`scm-bean` 建 Entity/VO → `scm-core` 建 Mapper+XML+Service → `scm-controller` 建 Controller；或先用代码生成器出骨架。

## 本地环境

| 依赖 | 地址 |
|---|---|
| MySQL（主库） | 127.0.0.1:3306 / `scm_manager`；租户库 `scm_tenant_20250519_001` |
| Redis | 127.0.0.1:6379（db=13），Session 存 Redis |
| RabbitMQ | 127.0.0.1:5672（admin/123456，vhost `/`） |
| Milvus | localhost:19530 |
| Docker 定义 | MySQL `D:\2025_project\00_docker\mysql\docker-compose.yml`；Milvus `D:\2025_project\00_docker\Milvus\docker-compose.yml` |

**数据库边界（最高优先级）**：只允许操作 `scm_manager` 与 `scm_tenant_20250519_001`；本机其它库与项目无关，禁止增删改。业务表数据默认只读，任何写操作（含为备份建临时表）先逐条确认再执行。

## 工作方式

- 文档驱动：方案与记录写在 `00_doc/`（按 `NN-主题-日期.md` 命名），先定方案、他人评审、再实施，完成后回填完成情况。
- 不改无关代码、不做未被要求的重构；提交只在用户当次明确指令时进行，默认不 commit、不 push。
- 回答用中文，只写「问题」「方案」，表格清单、简短，不铺陈过程。

## Agent skills

### Issue tracker

Issues 走 GitHub Issues（repo `zxhbloomer/scm_backend_vue3`），一律用 `gh` CLI 操作。见 `docs/agents/issue-tracker.md`。

### Triage labels

五个标准 triage 角色，label 字符串与角色名一致：`needs-triage`、`needs-info`、`ready-for-agent`、`ready-for-human`、`wontfix`；wayfinding 用 `wayfinder:map` 加 `wayfinder:research|prototype|grilling|task`。见 `docs/agents/triage-labels.md`。

### Domain docs

single-context。见 `docs/agents/domain.md`。
