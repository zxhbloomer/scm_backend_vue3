# 19 · 构建与依赖评审（reviewer-build，2026-10-29）

> 评审范围：`17-代码层剩余项审计与收尾清单-2026-10-28.md` §1.1 / §1.2 / §2.1 / §2.2（任务口径 §1.1/§1.2/§2.2/§2.3），并核对 `04` §7、`05`「勘误」节。
> 纪律：只评审，未改动任何 pom / java / yml；本文件是唯一新增文件。
> **环境限制（影响证据等级）**：本机 PATH 指向 `D:\2025_project\99_tools\apache-maven-3.9.10\bin` 与 `D:\2025_project\99_tools\jdk\jdk-21.0.8\bin`，两个目录实测**均不存在**（`Test-Path` = False），`mvn`、`java` 均无法执行 ⇒ **本轮无任何实跑**。全部结论来自静态取证：pom 逐行、本地仓库 `D:\2025_project\99_mvnrepository` 内 pom/jar 解包、成品 `scm-start/target/scm-start-0.0.1-SNAPSHOT.jar`（361,377,013 B，2026-10-28 22:24）的 `BOOT-INF/lib` 清单（578 项）。

## 0. 结论总表

| # | 项 | 17 号文档主张 | 评审结论 | 一句话依据 |
|---|---|---|---|---|
| 1 | surefire `pom.xml:944-951` | 版本 `3.5.6` + `skipTests=false`（§7.3） | **采纳（含 1 条前置补强 + 验收命令替换）** | 3.5.6 与 `surefire-junit-platform-3.5.6` 本地仓库齐全；API 面无断裂；但项目 0 处声明 `junit-platform-launcher`，需补 |
| 2 | HikariCP `pom.xml:789-793` | 整块删除交 BOM | **采纳** | 源码 0 引用；BOM=7.0.2 且本地仓库已有；5.0.1→7.0.2 无 API 断裂（实测 diff） |
| 3 | restdocs | 删「根 `pom.xml:46-47`」+ 5 模块 pom | **采纳改法，驳回行号** | 根 pom `restdocs` 命中 0；`46-47` 实为 `<parent>`；只需删 5 个模块 pom |
| 4 | spring-social | 删 `pom.xml:59` + `262-280` | **采纳改法，驳回行号** | 59 正确；4 条 depMgmt 实际 `260-279`，按 `262-280` 删会切坏 XML |

---

## 1. surefire（`pom.xml:944-951`）

### 1.1 问题

| # | 问题 | 证据 |
|---|---|---|
| P1-1 | 文档 §1.1 断言「删 `<version>2.19.1</version>` 交 BOM，Boot 4.1.2 托管 3.x 线」——**错误**。BOM 只管依赖不管插件，且 `spring-boot-starter-parent` 也没托管 surefire | `spring-boot-starter-parent-4.1.2.pom` 全文 `surefire` 命中 **0**；其 pluginManagement 只有 `maven-compiler-plugin:111`、`maven-failsafe-plugin:118`、`maven-jar-plugin:133`、`maven-war-plugin:145`、`maven-resources-plugin:157` |
| P1-2 | 文档 §7.3 称「本地仓库 `surefire-junit-platform` 最低是 3.5.6（3.2.2 都没有）」——**数字错** | `D:\2025_project\99_mvnrepository\org\apache\maven\surefire\surefire-junit-platform\` = **3.2.5, 3.5.3, 3.5.6**（最低 3.2.5；结论「2.19.1/2.22.2 无 platform provider」仍成立） |
| P1-3 | **真实风险**：provider 自带 platform 1.x，项目 classpath 是 platform 2.0.3（JUnit 6），双版本混用 | `surefire-3.5.6.pom:111 <versions.junit5>5.12.2</versions.junit5>`；项目侧 `spring-boot-dependencies-4.1.2.pom:121 junit-jupiter.version=6.0.3` + `:3387-3389` import `junit-bom`；全仓 grep `junit-platform-launcher` **0 命中**（无模块声明 launcher） |
| P1-4 | 验收命令选错模块 | 17 §1.1「`mvn -pl scm-bean test`」：`scm-bean/pom.xml` 内 `test` 关键字命中 **0 次**，无 `starter-test`、无 `src/test` ⇒ 永远 0 用例、假绿 |

### 1.2 方案（采纳，附补强）

| 动作 | 内容 | 依据 |
|---|---|---|
| A. 采纳版本 | `pom.xml:947` `<version>2.19.1</version>` → `<version>3.5.6</version>`；`pom.xml:949` `skipTests` → `false` | 本地仓库 `maven-surefire-plugin\` = 2.19.1,2.22.2,3.1.0,3.1.2,3.2.2,3.2.5,3.5.2,3.5.3,3.5.4,**3.5.6**；`surefire-junit-platform\3.5.6\` 下 jar/pom 齐全 ⇒ **3.5.6 离线可用、带 junit-platform provider** |
| B. 兼容性静态判定 | **可用**：provider 只引用 24 个 engine/launcher 公开类，逐个核对 `junit-platform-engine-6.0.3.jar` / `junit-platform-launcher-6.0.3.jar` **全部存在**；provider 字节码中出现的 junit 方法名（`getUniqueIdObject`/`getParentIdObject`/`getLegacyReportingName`/`registerTestExecutionListeners`/`includeTags`/`includeEngines`/`getConfigurationParameters` 等 28 个）在 6.0.3 三个 jar 中**全部命中**；未命中的仅 provider 自身内部方法（如 `getParentIdObjectMethod`） | 本地仓库解包实测 |
| C. **前置补强（必须做）** | 在 7 个活跃测试模块（`scm-core`, `-api`, `-app`, `-bpm`, `-tenant`, `-whapp`, `scm-ai`）的 test 依赖里加 `org.junit.platform:junit-platform-launcher`（**不写版本**，由 BOM 给 6.0.3），使 launcher/engine/commons 全栈 2.0.3 自洽 | 依据 P1-3：不加则 fork JVM 里 launcher 只能用 provider 自带的 1.12.2 去调 2.0.3 的 engine |
| D. 验收命令替换 | 改为 `mvn -o -pl scm-core test`（`scm-core/pom.xml:42-64` 有 `starter-test` + `junit-jupiter-api` + `junit-jupiter-engine`，均 `<scope>test</scope>`），并临时放 1 个 canary 用例（如 `assertTrue(true)`），跑完删除；输出须出现 `Tests run: 1`，不得出现 `no tests to run` 或 provider 报错 | P1-4 |

### 1.3 退路（若实跑报 provider/版本不兼容）

| 顺序 | 退路 | 本地可用性 | 说明 |
|---|---|---|---|
| R1 | 根 `pom.xml` `<properties>` 加 `<junit-jupiter.version>5.13.4</junit-jupiter.version>` 覆盖属性，把 JUnit 降到 5.13.4 / platform 1.13.4，surefire 仍 3.5.6 | 本地已有 `junit-jupiter(-api/-engine/-params) 5.13.4`、`junit-platform-{commons,engine,launcher} 1.13.4`、`junit-bom 5.13.4` | 与 surefire 3.5.6 的 platform 1.x 基线同线；BOM 用 `${junit-jupiter.version}` import `junit-bom`（`:3387-3389`），root 的父链是 starter-parent→spring-boot-dependencies，属性可覆盖 |
| R2 | 若 R1 仍失败，改 surefire `3.6.x`（需联网；本地无） | 无 | 仅在能联网时考虑 |

> 附（低风险，不建议本轮动）：同段 `pom.xml:938-942` `maven-jar-plugin 2.4` 亦是 2013 年版本，成品 jar 已能打出，保持原样；若将来升级需与 `spring-boot-maven-plugin` 的 archive 配置一起验。

---

## 2. HikariCP（`pom.xml:789-793`）

### 2.1 问题

| # | 问题 | 证据 |
|---|---|---|
| P2-1 | 文档「全仓 0 处使用 Hikari」**表述过强**：源码确实 0 引用，但运行期 classpath 有真实集成类 | `spring-boot-jdbc-4.1.2.jar` 内 **11 个类**引用 `com/zaxxer`（含 Boot 4 新增 `HikariCheckpointRestoreLifecycle`、`DataSourceCheckpointRestoreConfiguration$Hikari`、`HikariJdbcConnectionDetailsBeanPostProcessor`、`DataSourceBuilder$HikariDataSourceProperties`）；`dynamic-datasource` 4.6.0 共 **4 个类**（`HikariDataSourceCreator` 等） |
| P2-2 | 文档未说明硬钉被谁消费（传递链） | `mybatis-plus-spring-boot4-starter-3.5.18.pom:71` → `spring-boot-starter-jdbc` → Boot 默认池 `HikariCP`；项目 0 个 pom 声明 `starter-jdbc`（全仓 grep 0 命中） |
| P2-3 | 文档 §1.2「`spring-boot-jdbc-4.1.2` 是按 7.x 编译的」**不可证** | 无编译期证据；能证的只是 BOM 托管 7.0.2（`spring-boot-dependencies-4.1.2.pom:73 hikaricp.version=7.0.2`、`:821-825` 托管 `com.zaxxer:HikariCP`） |

### 2.2 方案（采纳）

| 动作 | 内容 | 依据 |
|---|---|---|
| A. 采纳改法 | 删 `pom.xml:789-793`（5 行整块，行号**正确**） | 全仓 `*.java/*.yml/*.xml` grep `Hikari` 仅命中 `pom.xml:791`（声明处本身） |
| B. 离线可解析 | 删除后 BOM 给 **7.0.2**，本地仓库 `com\zaxxer\HikariCP\` 已有 `7.0.2` ⇒ 离线可解析，无需联网 | 同上 |
| C. 隐藏风险**已排除**（API 面实测） | 把 Boot 侧 11 类 + dynamic-datasource 侧 4 类引用的标识符，逐个与 `HikariCP-5.0.1.jar` / `HikariCP-7.0.2.jar` 比对：唯一「7.0.2 有、5.0.1 无」的是 `ofMillis`（`java.time.Duration` 方法，与 Hikari 无关）⇒ **5.0.1→7.0.2 无源码级 API 断裂**；`dynamic-datasource` 三个 jar 的 diff 结果为空 | 本地仓库解包实测 |
| D. 运行期其余前置条件 | 7.0.2 class 文件 major=55（Java 11）与 JVM 21 兼容；7.0.2 依赖 `slf4j-api 2.0.17`，成品 jar 内 `slf4j-api-2.0.20` ⇒ 兼容 | `HikariCP-7.0.2.pom:51,106-107`；jar 清单 `slf4j-api-2.0.20.jar` |
| E. 验收 | 重打包后 `BOOT-INF/lib` 只有 `HikariCP-7.0.2.jar`（当前是 `HikariCP-5.0.1.jar`）；启动日志无 Hikari 初始化，`application-dev.yml:197` 与 `:246` 的 `type: com.alibaba.druid.pool.xa.DruidXADataSource` 保持生效 | 成品 jar 清单；yml 行号实测 |
| F. 残余风险（登记，不阻塞） | 若将来 Druid 分支失效回落到 Boot 默认池，池实现将从 5.0.1 变 7.0.2（行为差异：连接超时/keepalive 默认值等），须随 G3 冒烟一起看 | — |

---

## 3. restdocs

### 3.1 问题

| # | 问题 | 证据 |
|---|---|---|
| P3-1 | 文档 §2.1 与 `04` §7.2 写「根 `pom.xml:46-47`（depMgmt）仍声明 `spring-restdocs-mockmvc`」——**行号与判断双错** | `Select-String -Path pom.xml -Pattern restdocs` → **0 命中**（根 pom 早已按 `05` A-1 删除）；`pom.xml:46-47` 实际是 `<parent>` 的 `groupId/artifactId` 两行 |
| P3-2 | 真实残留位置应为 5 个模块 pom | `scm-controller:45-49`、`scm-controller-api:45-49`、`scm-controller-app:32-36`、`scm-controller-bpm:45-49`、`scm-controller-tenant:45-49`，均 `<scope>test</scope>` |

### 3.2 方案（采纳，行号改正）

| 动作 | 内容 | 依据 |
|---|---|---|
| A. 删 5 处 | 删上述 5 个模块 pom 的 `<dependency>` 整块（每块 5 行；app 模块为 32-36） | 见 P3-2 |
| B. 「未被用到」成立 | 全仓 `*.java` grep `restdocs` **0 命中**；成品 jar 无 restdocs 包 | 实测 |
| C. 「不影响编译」成立 | 5 个模块中仅 `scm-controller` 有 `src/test` 目录，其下**文件数 0**；其余 4 个模块无 `src/test`；全仓 `src/test` 下 java 只有根目录 2 个孤儿文件（`AiTokenViewsMapperContractTest.java`、`MapperContractTestFramework.java`，root packaging=pom 不参与编译） | 实测 |
| D. 文档同步 | 根 pom 无需任何动作；`17` §2.1、`04` §7.2 的「根 `pom.xml:46-47`」应改为「5 个 controller 模块 pom，根 pom 已无」 | 见 P3-1 |

---

## 4. spring-social

### 4.1 问题

| # | 问题 | 证据 |
|---|---|---|
| P4-1 | 文档写的 `pom.xml:262-280` **会切坏 XML**：`262` 是第一条依赖内部的 `<artifactId>` 行，`280` 是另一段注释块的开头 | 实测：`259 <!-- spring social -->`、`260 <dependency>`、`261 <groupId>org.springframework.social</groupId>`、`262 <artifactId>spring-social-config</artifactId>`、`263 <version>`、`264 </dependency>` … 四条到 `279 </dependency>`；`280-284` 是已注释的 `spring-social-autoconfigure` 块 |
| P4-2 | 相邻的 `285-289 <org.springframework.plugin:spring-plugin-core:2.0.0.RELEASE>` 与 social 无关，不能被连带删 | `pom.xml:285-289` 实测 |

### 4.2 方案（采纳，行号改正）

| 动作 | 内容 | 依据 |
|---|---|---|
| A. 删属性 | `pom.xml:59 <spring.social.version>1.1.6.RELEASE</spring.social.version>` | 行号**正确** |
| B. 删四条 depMgmt | 正确区间是 **`259-279`**（含 `<!-- spring social -->` 注释行 + 4 个 `<dependency>` 整块），不是 `262-280` | 见 P4-1 |
| C. 「模块已无引用」成立 | 全仓 grep `spring\.social|spring-social|springframework\.social`：仅剩 4 条 depMgmt（260-279）+ 59 属性 + **1 处 Javadoc 说明性注释**（`scm-security\src\main\java\com\xinyirun\scm\security\code\ScmSessionStrategy.java:10`，写明「spring-social 1.1.6 基于 javax.servlet 与 Spring 4…」，属设计说明，**保留**）；`scm-security/pom.xml` grep `social` 0 命中；成品 jar 无 `spring-social-*` | 实测 |
| D. 范围护栏 | 只删 59 与 259-279；不动 280-284（注释块）与 285-289（`spring-plugin-core`） | P4-2 |

---

## 5. 文档错误清单（执行前必须改正，否则会改错位置）

| 文档:行 | 原文 | 实测正确说法 | 后果 |
|---|---|---|---|
| `17` §1.1 L51 | 「删 `<version>` 交 BOM，Boot 4.1.2 托管 3.x 线」 | BOM 不托管插件；starter-parent 4.1.2 未托管 surefire ⇒ 必须显式写 `3.5.6`（§7.3 已自我修正，但 §1.1 未同步，两处冲突） | 按 §1.1 执行会退回 Maven 默认版本（无 platform provider 的旧线） |
| `17` §7.3 L235 | 「`surefire-junit-platform` 最低是 3.5.6（3.2.2 都没有）」 | 有 `3.2.5 / 3.5.3 / 3.5.6`，最低 3.2.5 | 数字错，结论不变 |
| `17` §1.1 L52 | 验收 `mvn -pl scm-bean test` | `scm-bean` 无测试依赖、无 `src/test`（`test` 命中 0）⇒ 应改 `mvn -o -pl scm-core test` | 验收永远「0 用例」假绿 |
| `17` §2.1 / `04` §7.2 | 根 `pom.xml:46-47`（depMgmt）仍有 restdocs | 根 pom `restdocs` 0 命中；46-47 是 `<parent>`；残留仅 5 个模块 pom | 会去改父 POM 块 |
| `17` §2.2 / `05` E-2 L440 | `262-280` | `259-279`（59 属性正确） | **删出残缺 XML** |
| `17` §7.2 L227 | 「七个模块 … exclusion：`junit:junit`、`junit-vintage-engine`」 | 仅 `scm-ai/pom.xml:131-135` 排除 `junit:junit`；`scm-core*`/`-whapp` 只排 `junit-vintage-engine`（如 `scm-core/pom.xml:46-52`）；另 `scm-start/pom.xml:51` 也声明了 `starter-test`（共 8 个活跃声明） | 描述不准，重跑用例时会看到 junit4 仍在测试 classpath |
| `17` §1.2 L67 | 「`spring-boot-jdbc-4.1.2` 是按 7.x 编译的」 | 不可证（无编译期证据）→ 改为「Boot 的 Hikari 集成类引用的 API 在 5.0.1 与 7.0.2 中均存在（实测 diff）」 | 结论方向不变，证据等级需降 |

---

## 6. 环境恢复后的复核命令（照抄即验）

```powershell
# 0) 先确认工具链（当前缺失）
mvn -v ; java -version

# 1) surefire 生效 + provider/canary 实跑（临时 canary 用例，跑完删）
mvn -o -pl scm-core test
mvn -o -pl scm-core dependency:tree -Dincludes=org.junit.platform,org.junit.jupiter,com.zaxxer

# 2) 全量构建
mvn -o -T 1C clean install -DskipTests

# 3) 成品 jar 三查（Windows 自带 tar 即可）
tar -tf scm-start/target/scm-start-0.0.1-SNAPSHOT.jar | Select-String -Pattern "HikariCP|druid|restdocs|spring-social|javax.servlet-api"
```

期望：`Tests run: N`（N≥1）且无 provider 报错；依赖树里 `junit-platform-launcher` 只有一个版本；jar 内 **只有** `HikariCP-7.0.2.jar`、`druid-1.2.28.jar`（Druid 见 `17` §1.3，非本次范围）、**无** restdocs / spring-social。

---

## 7. 逐项结论

| # | 项 | 结论 |
|---|---|---|
| 1 | surefire → `3.5.6` + `skipTests=false` | **采纳**；附前置补强（7 个模块加 BOM 托管的 `junit-platform-launcher`）与验收命令替换；兼容性无类/方法级断裂证据，最终以 §6 实跑为准，退路 R1 = 属性降 `junit-jupiter 5.13.4`（本地齐全） |
| 2 | HikariCP 硬钉整块删除 | **采纳**；行号 789-793 正确；「0 处使用」应改为「源码 0 引用、classpath 有集成类但不触发」；升级无 API 断裂（实测 diff），离线可解析 7.0.2 |
| 3 | restdocs 删除 | **采纳改法，驳回行号**；根 pom 已无该项，只删 5 个模块 pom；不影响编译 |
| 4 | spring-social 删除 | **采纳改法，驳回行号**；59 正确，`262-280` → `259-279`；模块与 jar 均已无引用 |
