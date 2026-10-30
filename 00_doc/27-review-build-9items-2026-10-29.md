# 27 - 独立评审：收尾清单 #1~#7（构建/依赖线）

- 评审对象：`00_doc/26-收尾清单9项方案与实施计划-2026-10-29.md` 的 #1 ~ #7
- 评审人：reviewer-build（只读评审）
- 时间：2026-10-29
- 方法：全仓 grep/sed 取原文 + `dependency:tree` 实测 + **在副本 `/d/temp/scm-verify`（`tar` 复制工作树，排除 target/.git）上做改动验证**；仓库内 pom/代码/配置 **零修改**
- 环境：`JAVA_HOME=/d/2025_project/99_tools/jdk/jdk-21.0.10`，`mvn 3.9.9`，`-o`（离线）+ `-Dmaven.repo.local=/d/2025_project/99_mvnrepository`

---

## 逐项裁决

| 项 | 方案是否成立 | 我的实测证据（文件:行 / 命令输出摘要） | 必须修正点 |
|---|---|---|---|
| #1 Druid 删硬钉 | **不成立（照做即构建失败）** | 原文 `pom.xml:210-214` 与 doc26 一致（`:213 <version>1.2.20</version>`）。副本中只删这 5 行后 `mvn -o -pl scm-common validate` → `'dependencies.dependency.version' for com.alibaba:druid:jar is missing. @ line 208 / 219 / 155`，**3 个工程读不了 POM**（scm-common/pom.xml:208-211、scm-start/pom.xml:219-222、scm-framework/pom.xml:155-158 都是无 version 的显式 druid 依赖）。starter 侧确认：`druid-spring-boot-4-starter-1.2.28.pom:38-44` 为 `druid:${project.version}`；其 dependencyManagement 只 import spring-boot-dependencies（`:22-32`），**不向消费方传递版本**。离线实测无 1.2.28：改 `1.2.28` 后 `mvn -o -pl scm-common compile` → `Cannot access nexus (127.0.0.1:28081) in offline mode and the artifact com.alibaba:druid:jar:1.2.28 has not been downloaded`；`ls …/com/alibaba/druid/` 仅 1.2.5/1.2.8/1.2.20/1.2.22/1.2.23/1.2.24（doc26 该条正确） | **B-1**：改为二选一——①`pom.xml:213` 直接把 `1.2.20`→`1.2.28`（保留 depMgmt，最小改动，首选）；②删 depMgmt 同时删掉上述 3 个模块的 `<dependency>com.alibaba:druid</dependency>` 显式声明。另须先联网/预热 nexus（B-1 后半） |
| #2 javax → jakarta | 方向成立，**验收标准不成立** | `TriggerController.java:10` 为唯一一处 `javax`（`grep -n javax` 仅 1 行）；文件内 `HttpServletRequest` 出现 3 次（:10 import、:25、:57）。**但"改后编译通过"不是有效闸门**：现状（未改）`mvn -o -pl scm-controller-bpm -am compile -DskipTests` → **BUILD SUCCESS**（58.9s），因为 `javax.servlet:javax.servlet-api:3.1.0` 由 `com.github.penggle:kaptcha:2.3.2` 以 **compile** 作用域传递进来（`dependency:tree -pl scm-security -Dverbose` → `kaptcha:2.3.2 +- javax.servlet-api:3.1.0:compile`）。改成 jakarta 后同为 BUILD SUCCESS（70.4s） | **B-6**：把验收从"编译通过"改成运行期验证（Spring MVC 只认 jakarta，javax 形参在运行期无法解析）；并记录 kaptcha 这条 javax 传递链 |
| #3 flowable 开关 + DDL | 方向成立，**DDL 取证不成立** | ①yml 实测：`scm-start/src/main/resources` 只有 `application.yml`(596B)、`application-dev.yml`(24KB)、`spy.properties`；`application.yml` **无 flowable 段**（只有 logging/spring.profiles/project.version）；`flowable:` 段在 `application-dev.yml:608`，到 `:621`（dmn/cmmn/idm/content/app/eventregistry 全 disabled）。②属性名有效：`flowable-spring-boot-autoconfigure-8.0.0.jar!/META-INF/spring-configuration-metadata.json` 含 `database-schema-update`。③**DDL 来源错**：`flowable-engine-8.0.0.jar` 的 `db/create` 下只有 engine(7 张)+history(5 张)，**不含 common 20 张**；common 在 `flowable-engine-common-8.0.0.jar!/org/flowable/common/db/create/flowable.mysql.create.common.sql`（实测建表 20 张，含 `ACT_GE_PROPERTY`、`ACT_GE_BYTEARRAY`、`ACT_RU_TASK`、`FLW_RU_BATCH*`…）。三文件合计 20+7+5=**32 张**，与实库 `information_schema.tables`（scm_manager 32 张、scm_tenant_20250519_001 32 张，表名逐一吻合）一致。④升级脚本实测 **8 条语句 / 5 个文件**（非 5 条）：`7102.to.7200.engine`(alter ACT_RU_ACTINST+update version)、`7102.to.7200.history`(alter ACT_HI_ACTINST)、`7201.to.7202.engine`(create index+update)、`7202.to.8000.engine`(update)、`7202.to.8000.history`(alter ACT_HI_PROCINST×2)。⑤实库已就位：两库 `common.schema.version`=`schema.version`=`8.0.0.0`，`schema.history=create(8.0.0.0)` | **B-4**（DDL 必须取 3 个文件/2 个 jar，否则缺 20 张表，连 `insert ... ACT_GE_PROPERTY` 都会失败）；**B-5**（升级脚本按实测 5 个文件 8 条语句重写，并注明"仅存量 7.x 库需要"）；**B-7**（插入位置写清：`application-dev.yml:608` 的 `flowable:` 下，或在 `application.yml` 新增顶层 `flowable:` 块） |
| #4 surefire 升 3.5.6 | 方案成立，**后果判断错** | 原文 `pom.xml:944-951`（`:947 <version>2.19.1</version>`、`:949 <skipTests>true</skipTests>`）。离线齐备：`maven-surefire-plugin/3.5.6/{jar,pom}`、`surefire-junit-platform/{3.2.5,3.5.3,3.5.6}`、`surefire-api/maven-surefire-common/surefire-booter/extensions-api/logger-api/shared-utils` 均有 3.5.6；BOM 托管 `spring-boot-dependencies-4.1.2.pom:148 maven-surefire-plugin.version=3.5.6`、`:3723-3726` pluginManagement。方案 A 实测 `mvn -o -pl scm-core-bpm,scm-excel,scm-controller-bpm test` → `surefire:3.5.6:test … Tests are skipped.` BUILD SUCCESS。方案 B 实测（删 `<configuration>`）→ 5 个模块全部 `No tests to run.` BUILD SUCCESS（**不是"可能红灯"**）。`*Test.java` 全仓 2 个：`scm-excel/src/main/.../ProjectMergeStrategyTest.java`（只有 `main()` 在 :267，不是用例）、`src/test/java/.../AiTokenViewsMapperContractTest.java`（真 JUnit5，但在**根聚合工程** packaging=pom，`mvn -o -N test` 不执行 surefire，BUILD SUCCESS 无测试阶段）；各模块 `src/test` 下 **0 个文件** | **B-8**：A/B 需改成"今日都绿、A 保留跳过、B 得到的是永远绿的假闸门（0 用例）"，并说明唯一 JUnit 用例是死代码（在根 pom 工程下不会被编译执行） |
| #5 HikariCP 删钉 | **成立** | 原文 `pom.xml:789-793`（`:792 <version>5.0.1</version>`）。副本删该 5 行后 `mvn -o -pl scm-start dependency:tree -Dincludes=com.zaxxer:HikariCP` → `com.zaxxer:HikariCP:jar:7.0.2:compile`，BUILD SUCCESS；BOM `:73 hikaricp.version=7.0.2`；离线仓库有 `com/zaxxer/HikariCP/7.0.2`。全仓引用面：`grep -rn "com.zaxxer.hikari\|HikariCP\|hikari" --include=*.java --include=*.yml --include=*.xml`（排除 target）**仅 `pom.xml:791` 一处** | 无需 must-fix；验收命令可保留（已实测通过）。建议补一句"数据源 type 是 DruidXADataSource，Hikari 实际不参与运行" |
| #6 restdocs 删除 | 方向成立，**行号与处数均错** | 全仓 `grep -rn restdocs --include=pom.xml .` 命中 **5 个 controller 模块**，**根 pom 0 命中**；`pom.xml:46-47` 实际是 `<parent>` 的 `<groupId>spring-boot-starter-parent</groupId>`/`<artifactId>`（`awk 44..48` 已打印）。5 处真实块：scm-controller:45-49、-api:45-49、-bpm:45-49、-tenant:45-49、-app:32-36（doc26 的 46-48/33-35 只是内层 3 行）。按 doc26 区间删除后 `mvn -o -pl scm-controller validate` → `'dependencies.dependency.artifactId' for null:null:jar is missing. @ line 45`（空 `<dependency>`，POM 无法读取）；按正确区间删除后全仓 `mvn -o validate` → BUILD SUCCESS。使用面：`grep -rn RestDocumentation --include=*.java`=**0**，`asciidoctor`/`snippets` 亦 0；当前实际解析版本 `spring-restdocs-mockmvc:4.0.2:test`（BOM `:204`/`:3571`） | **B-2**（行号改为 45-49 / 32-36，否则构建直接失败）；**B-3**（根 pom 一处是误记，处数 6→5） |
| #7 spring-social 删除 | **成立（略有行号偏差）** | 属性 `pom.xml:59` ✓；4 条 depMgmt 实际 **260-279**（`:259` 是 `<!-- spring social -->`，`:262` 只是第一条的 artifactId）。全仓引用：`grep -rn spring.social.version` 仅 `pom.xml:59,263,268,273,278`；`grep -rn spring-social --include=pom.xml` 仅这 4 条 + `:282` 注释掉的 autoconfigure；`grep -rn "org.springframework.social" --include=*.java` = **0**；`scm-security/.../ScmSessionStrategy.java:9-10` 只有注释提及（已用 jakarta 版自实现替代）。副本删除（属性+4 条+注释行）后 `mvn -o validate` BUILD SUCCESS，残留 `spring-social` 仅 1 处（`:281-285` 注释块） | 建议把 `:281-285` 注释块一并清掉（可选），并把 depMgmt 区间写成 260-279 |

---

## 行号勘误

| doc26 位置 | doc26 写的 | 实测 | 性质 |
|---|---|---|---|
| §1 表 #1 | `pom.xml:210-214` | `210-214`（`:213` 版本行） | ✅ 一致 |
| §1 表 #4 | `pom.xml:946-949` | 插件块 `944-951`，版本在 `:947`，`skipTests` 在 `:949` | ⚠️ 区间不准（§2.4 的 944-951 才对） |
| §1 表 #5 | `pom.xml:790-792` | 块 `789-793`，版本 `:792` | ⚠️ 少算首尾标签（§2.5 的 789-793 才对） |
| §1 表 #6 | “5 个模块 + 根 `pom.xml:46-47`” | 只有 5 个模块；根 pom **无** restdocs，`46-47` 是 `<parent>` 的 groupId/artifactId | ❌ 处数错（6→5） |
| §2.6 表 | 模块行 `46-48` / `33-35` | `45-49` / `32-36`（含 `<dependency>`/`</dependency>`） | ❌ 会留空标签导致 POM 解析失败 |
| §1 表 #7 / §2.7 | 属性 `59`；depMgmt `262-280` | 属性 `59` ✓；depMgmt 块 `260-279`（注释行 `259`） | ⚠️ 区间偏移 2 |
| §2.3 表 | DDL 只从 `flowable-engine-8.0.0.jar` 的 `org/flowable/db/create/flowable.*.create.engine.sql` 提取 | 还需 `flowable-engine-common-8.0.0.jar!/org/flowable/common/db/create/flowable.mysql.create.common.sql` 与同 jar 的 `create.history.sql` | ❌ 来源不全 |
| §2.3 表 | 升级脚本“共 5 条” | 5 个 upgradestep 文件、8 条语句（4 alter + 1 index + 3 update version） | ❌ 计数错 |
| §2.3 表 | “公共 `application.yml` 加 `flowable.database-schema-update: false`” | `application.yml` 无 flowable 段；现有 flowable 段在 `application-dev.yml:608-621` | ⚠️ 插入位置需写死（两种写法均可，但必须写明行） |

---

## 遗漏风险

| # | 风险 | 证据 / 说明 |
|---|---|---|
| R-1 | Druid `1.2.20 → 1.2.28` 的 API 兼容性**未能证实** | 离线仓库无 1.2.28 jar（`ls …/com/alibaba/druid/`），无法核对 `application-dev.yml:197,246` 使用的 `com.alibaba.druid.pool.xa.DruidXADataSource` 是否仍在；联网下载后须先 `unzip -l` 验证该类存在 |
| R-2 | `javax.servlet-api:3.1.0` 仍留在运行期 classpath | 来源 `kaptcha:2.3.2`（compile 作用域，会打进运行 jar）。#2 改完后旧 API 与 Jakarta EE 11 并存；kaptcha 验证码（`SysCaptchaController`）本身是 javax 生态，属本次 7 项之外的独立风险 |
| R-3 | Hikari 7.0.2 实际不参与运行，删钉只影响"改回默认池"的那天 | `application-dev.yml:196 type: com.alibaba.druid.pool.xa.DruidXADataSource`；`scm-quartz/.../DruidConnectionProvider.java:3,102` 用 Druid；全仓无 `com.zaxxer.hikari` 引用 |
| R-4 | restdocs 删除后无副作用，但 5 个模块的 `spring-boot-starter-test` 同时被注释掉 | `scm-controller/pom.xml:40-44`、`-api:40-44`、`-bpm:40-44`、`-tenant:40-44`；后续补测试需一并恢复，否则"删了 restdocs 也永远加不回测试" |
| R-5 | #3 顺序正确但缺"启动后自检" | `database-schema-update: false` 下脚本没执行时应用**不报错**，只会在第一次用到流程时炸（静默失败）。建议加一条自检：`select VALUE_ from ACT_GE_PROPERTY where NAME_ in ('schema.version','common.schema.version')` 应返回 8.0.0.0 |
| R-6 | 该风险反向支持 #3 的方向 | doc09 已证实 Flowable 8 的 `isTablePresent` 走 `getTables(null,null,'ACT_GE_PROPERTY',…)`，在同实例多库（本机 11+ 个库有该表）下**假阳性**，即 `database-schema-update: true` 不可靠 |
| R-7 | `scm-start-parent/` 下遗留 Boot3 数据源栈 | `scm-start-parent/scm-bpm-start/pom.xml:148,158`、`scm-main-start/pom.xml:186,200` 仍声明 `druid` + `druid-spring-boot-3-starter`；虽被 `pom.xml:26-27` 注释排除构建，但日后启用会再次引入版本冲突 |
| R-8 | #1 的验收命令用错模块 | doc26 写 `mvn -o -pl scm-common dependency:tree \| grep druid`，改为 `-pl scm-start`（实际打包 druid 的模块）更贴合，且必须先联网（离线必然 `Cannot access nexus`） |

---

## verdict

**`needs_revision`** —— 7 项中 #5、#7 可直接执行（实测通过）；#2 方向对但验收标准无效；#1、#6 照 doc26 原文执行会**直接导致构建失败**；#3 的 DDL 取证不完整（缺 20 张表）。

### must-fix 清单

| 编号 | 项 | 必须修正 |
|---|---|---|
| B-1 | #1 | 不能只删 `pom.xml:210-214`。改为 `:213` 的 `1.2.20`→`1.2.28`（首选），或删 depMgmt 同时删 `scm-common/pom.xml:208-211`、`scm-framework/pom.xml:155-158`、`scm-start/pom.xml:219-222` 三处显式 druid 声明。并注明必须先联网/预热 nexus，离线必然失败 |
| B-2 | #6 | 行号改为 `scm-controller`/`-api`/`-bpm`/`-tenant` = `45-49`、`-app` = `32-36`（连 `<dependency>`/`</dependency>` 一起删），否则 `null:null:jar is missing` 导致 POM 无法读取 |
| B-3 | #6 | 删掉"根 `pom.xml:46-47`"这一处误记，处数改为 **5 处**（根 pom 无 restdocs） |
| B-4 | #3 | DDL 必须从 3 个文件合成：`flowable-engine-common-8.0.0.jar!/org/flowable/common/db/create/flowable.mysql.create.common.sql`(20 张) + `flowable-engine-8.0.0.jar!/org/flowable/db/create/flowable.mysql.create.engine.sql`(7 张) + `…create.history.sql`(5 张) = 32 张（与实库一致） |
| B-5 | #3 | 升级脚本按实测重写：5 个 upgradestep 文件 / 8 条语句（并注明仅存量 7.x 库需要，因现存两库均已是 8.0.0.0） |
| B-6 | #2 | 验收标准由"编译通过"改为运行期验证（现状 javax 也能编译通过，因 `kaptcha:2.3.2 → javax.servlet-api:3.1.0` 在 classpath）；并在变更记录里登记这条 javax 传递链 |
| B-7 | #3 | 写明开关落点：`application-dev.yml:608` 的 `flowable:` 块内，或在 `application.yml` 新增顶层 `flowable:` 块（现 `application.yml` 无该段） |
| B-8 | #4 | 修正后果判断：方案 A/B 今日都 BUILD SUCCESS；B 得到的是"0 用例的假闸门"（唯一 JUnit 用例在根聚合工程 `src/test`，packaging=pom 不执行；`scm-excel` 的 `*Test.java` 只有 `main()`） |

> 说明：本次评审未修改任何 pom/代码/配置；所有"改后验证"均在 `/d/temp/scm-verify` 副本内完成，仓库内只做 grep/sed 打印与 `dependency:tree`（只读）。
