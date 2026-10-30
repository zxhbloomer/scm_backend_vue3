# 19 · 评审：17 号文档 §1.5 / §5 —— `flowable.database-schema-update` 统一与 DDL 脚本落地

- 评审日期：2026-10-29 | 评审角色：数据库与 Flowable 评审员（reviewer-dbops）
- 评审对象：`00_doc/17-代码层剩余项审计与收尾清单-2026-10-28.md` §1.5（L135-147）、§5（L184-189）
- 交叉文档：`00_doc/08-S0-备份与清空规格` 、`00_doc/09-启动报错调研-Flowable8空库schema`
- 约束遵守：未修改任何代码/配置；数据库仅执行 SELECT（`mysqlsh` root@127.0.0.1:3306）
- 取证方式：jar 内容逐条解包 + `javap` 字节码 + `information_schema` / `ACT_GE_PROPERTY` 实测

---

## 0. 结论

| 评审项 | 结论 | 一句话理由 |
|---|---|---|
| §1.5「统一开关 = 公共层 `false`」的方向 | **采纳** | `false` 走的是版本校验（`schemaCheckVersion`），能绕开 09 号文档实测的"跨库元数据假阳性"；`true` 才是那个会在清库后崩的选项 |
| §1.5「统一脚本 = 自建 `01/02` 两份」 | **驳回** | `02` 缺 3 条版本号 UPDATE（跑完仍抛 `FlowableWrongDbException`）；`01` 表数口径错（实际 30 张 `ACT_*` + 2 张 `FLW_*`，不是"32 张 `ACT_*`"），且自建单文件无法可靠覆盖 98 条索引 |
| §5「先落脚本、再改开关」 | **需修正表述** | 约束对象是"下次清库/新环境首次启动"，不是本次改动；dev 现库表齐版号齐，先改开关不会立刻坏 |
| 整体 | **需补证（needs_revision）** | 结论方向对，脚本内容与覆盖面不可直接执行 |

---

## 1. 问题（每条附证据）

| # | 严重度 | 问题 | 证据 |
|---|---|---|---|
| **P1** | **blocker** | `02-upgrade-from-7.1.sql` 只写 5 条 DDL，漏掉 3 条 `update ACT_GE_PROPERTY set VALUE_`，执行后 `schema.version` 仍是 `7.1.0.2` ⇒ 开关为 `false` 时启动必抛 `FlowableWrongDbException`（Expected 8.0.0.0） | 官方脚本原文 3/3 都带 UPDATE：`upgradestep.7102.to.7200.engine.sql`（`...='7.2.0.0'`）、`upgradestep.7201.to.7202.engine.sql`（`...='7.2.0.2'`）、`upgradestep.7202.to.8000.engine.sql`（`...='8.0.0.0'`）（`flowable-engine-8.0.0.jar`）；校验逻辑 `ProcessDbSchemaManager.schemaCheckVersion` 字节码硬编码 `ldc "8.0.0.0"` 与 `getDbVersion()` 比对、不等 `throw FlowableWrongDbException` |
| **P2** | **blocker** | `01-create-all.sql` 口径"32 张 `ACT_*`"是错的：8.0 create 脚本实际建 **7+5+20 = 32 个表**，其中 `FLW_RU_BATCH`、`FLW_RU_BATCH_PART` 不叫 `ACT_*` ⇒ 真实构成是 **30 张 `ACT_*` + 2 张 `FLW_*`**。若按"32 张 ACT_*"核对/抄写，必漏 2 张 FLW 表，后续批量（批量部署/批量删除）流程直接炸 | create 脚本 `create table` 计数：engine=7、history=5、common=20（后者含 `FLW_RU_BATCH`/`FLW_RU_BATCH_PART`）；DB 实测两库各 **30 张 `ACT_*` + 2 张 `FLW_*`**（`information_schema.tables`，2026-10-29） |
| **P3** | high | 用"自建摘录"替代官方脚本不可控：官方 3 份 create 共 **98 条索引** + **4 条 `ACT_GE_PROPERTY` 初始行**（`common.schema.version=8.0.0.0`、`next.dbid=1`、`schema.version=8.0.0.0`、`schema.history=create(8.0.0.0)`）。手抄必漏，且漏了版本行就是 P1 同类故障 | 索引计数：common 66 / history 18 / engine 14 = 98；insert 计数：common 2 + engine 2；`create.engine.sql` L323-327 `values ('schema.version','8.0.0.0',1)` / `('schema.history','create(8.0.0.0)',1)` |
| **P4** | high | 「公共层 `application.yml`」= 全 profile 生效，而仓库内**只有 dev 一份 profile yml**，生产 yml 外置 ⇒ 该开关随 jar 直接进生产。生产库若未先按 02 升级，一发布即启动失败；且 8.0 库**无法回退 7.1**，只能恢复备份 | `scm-start/src/main/resources/` 仅 `application.yml`（596 B, 22 行）+ `application-dev.yml`（24 KB）；全仓 grep `database-schema-update` 命中 **0**、`databaseSchemaUpdate` 仅 `scm-framework/.../ActivitiConfig.java:65` 一行已注释；回退限制见 17 号 §3「T5.2 … 回退只能恢复备份」 |
| **P5** | high | 多租户覆盖面未写进方案：Flowable schema 是**每库一套**，不是"主库一套"。方案头注释只写 `--target: scm_manager / scm_tenant_*`，没有"逐库执行 + 逐库校验"的清单与执行人 | `s_tenant_manager` 仅 1 行（`tenant=scm_tenant_20250519_001`）；两库各自有 30+2 张引擎表且 `schema.version=8.0.0.0`、`schema.history=create(8.0.0.0)`（即两库都被引擎各自建过表）。生产租户数未知 → 需补证 |
| **P6** | medium | 与 08 号文档决策正面冲突且未闭环：08 号 A3 写"允许 DROP 引擎表，**由 8.0.0 启动时自动重建**"，而本方案改为 `false` 后不再自动重建；`S0-02-清空.sql` 若被再次执行，dev 必起不来 | 08 号 L11（决策记录 A3）、L16（S0-02 清空 71+8 张引擎表）、§3 执行时序五步（③ 停应用 → ④ 清空 → ⑤ 校验，无"建表"步） |
| **P7** | medium | 与 09 号文档 §9 结论冲突未闭环：09 §9 结论是"加 `nullCatalogMeansCurrent=true`（3 处）+ 无需建账号/建表"，**实测全仓未落地**；本方案改 `false` 后该条不再需要，但两文档并存会误导执行者 | 09 L297-338（推荐方案 D'、§9.2 三处改写）；实测 `application-dev.yml` L194/L243 URL 无该参数、`s_tenant_manager.url` 无该参数；全仓 grep `nullCatalogMeansCurrent` 仅命中 09 号文档 7 行，代码/配置 0 处 |
| **P8** | medium | 「顺序不能反（脚本必须先落地）」表述过强：当前 dev 两库表齐、版本齐，先改开关**不会立刻坏**；真正的硬约束是"清库或新环境首次启动之前必须在场" | DB 实测 `act_ge_property` 两库均 `schema.version=8.0.0.0`；表清单 30+2 齐全；`ACT_RE_PROCDEF`/`ACT_RE_DEPLOYMENT`/`ACT_RU_EXECUTION`/`ACT_HI_PROCINST` `COUNT(*)=0` |
| **P9** | low | 方案未声明脚本的**版本基线**：02 只对 `schema.version=7.1.0.2` 成立；若某环境停在 7.0.x 或已是 7.2.x，需另出路径。生产现值未取证（本次仅允许连 dev 两库） | 官方升级链只有 7102→7200→(7201)→7202→8000 五份 mysql 脚本（jar 解包）；7.1.0.2 的来源见 08 号 L3「Flowable 7.1.0 / schema 7.1.0.2」 |
| **P10** | low | 方案未写"改 `false` 后 dev 清库的报错文本"，排障时会被误判成"表不存在" | `EngineSqlScriptBasedDbSchemaManager.schemaCheckVersion` 字节码常量：`No flowable tables in DB. Set property "databaseSchemaUpdate" "true" or value="create-drop" (use create-drop for testing only!)` |

### 1.1 「统一」口径的替代方案比较（回答评审要点 1）

| 方案 | 做法 | 有效性 | 代价 | 评价 |
|---|---|---|---|---|
| **A（本方案）** | 公共层 `false` + 自建 2 份脚本 | 方向正确，脚本不可用 | 需维护脚本副本；每库手工执行 | 改造成本最低、口径最统一 ⇒ **推荐，但脚本换成官方的（见 S2）** |
| **B** | 生产交运维脚本 + 只对 dev 保留 `true` | 可行 | `true` 在多库实例有空库假阳性（09 §2.2 实测：`getTables(null,null,...)` 空库返回 12 行）⇒ dev 清库仍是雷 | 不算"更简单"，只是把坑留给 dev |
| **C** | 只对 dev 保留 `upgrade`（`true`）+ 补 `nullCatalogMeansCurrent=true`（09 §9.2 三处） | 09 已实测可自愈空库 | 需改 3 处（2 处 yml + 1 处 DB 里的 `s_tenant_manager.url`）；生产仍推荐 `false` | **零脚本**，dev 最省事；但与"统一"目标相悖，且参数只规避不根治 |
| **D** | 直接用 Flowable 官方 SQL（不自建） | 完全等价于引擎行为 | 需归档 8 份文件（3 create + 5 upgrade）| **推荐**，见 S2 |

---

## 2. 方案（推荐落地口径，仅建议，不改代码）

| # | 项 | 具体做法 |
|---|---|---|
| **S1** | 开关 | 采纳公共层 `flowable.database-schema-update: false`（`scm-start/src/main/resources/application.yml`，当前 22 行、无 flowable 段）。**但分两步发布**：dev 随时可改；生产必须"逐库执行 02 → 再发版"，并把该顺序写进发布单（因 P4：`false` 随 jar 进生产，且 8.0 不可回退） |
| **S2** | 脚本来源 | **不自建**。`00_doc/sql/flowable-8.0.0-ddl/` 内直接归档官方 8 份原文件 + 来源说明（jar 坐标 + 文件路径 + 校验和），命名按执行顺序：<br>`01-create-common.sql` → `02-create-engine.sql` → `03-create-history.sql`（空库/新库）<br>`11-7102-to-7200-engine.sql` → `12-7102-to-7200-history.sql` → `13-7201-to-7202-engine.sql` → `14-7202-to-8000-engine.sql` → `15-7202-to-8000-history.sql`（7.1.0.2 → 8.0.0.0） |
| **S3** | 若坚持自建单文件 | 必须同时含：30 张 `ACT_*` + 2 张 `FLW_*` 的 create、98 条索引、4 条 `ACT_GE_PROPERTY` 初始行（含 `schema.version='8.0.0.0'`、`schema.history='create(8.0.0.0)'`）；文件头注明"由 flowable 8.0.0 jar 提取，禁止手改" |
| **S4** | 验收口径（可执行 SELECT） | 空库/新库（4 条全中）：① `ACT_*` 表 = 30；② `FLW_*` 表 = 2；③ `schema.version = 8.0.0.0` 且 `common.schema.version = 8.0.0.0`；④ `schema.history` 存在。<br>增量库另加（5 条）：`ACT_RU_ACTINST.COMPLETED_BY_`、`ACT_HI_ACTINST.COMPLETED_BY_`、`ACT_HI_PROCINST.END_USER_ID_`、`ACT_HI_PROCINST.STATE_`、索引 `ACT_IDX_BYTEAR_DEPL` + `schema.version = 8.0.0.0` |
| **S5** | dev 清库流程改造 | 08 号 §3 时序改为：① 基线 → ② 备份 → ③ 停应用 → ④ 清空 → **④.5 执行 `01~03` create 脚本** → ⑤ 校验 → ⑥ 启动；同时在 `00_doc/sql/` 增补 "清库后必建表" 提示，避免下次清库直接起不来（P6） |
| **S6** | 文档闭环 | ① 09 号 §9 顶部加"**已被 17 号 §1.5 的 `false` 方案替代，保留作根因记录**"；② 08 号 A3 决策改为"引擎表由 DDL 脚本重建，不依赖启动自动建表"；③ 17 号 §1.5 表格中"32 张 `ACT_*`"→"30 张 `ACT_*` + 2 张 `FLW_*`"、"5 条增量 DDL"→"5 条 DDL + 3 条版本号 UPDATE"（P1/P2/P7） |
| **S7** | 补证项（执行前须答） | ① 生产/测试各环境 `act_ge_property.schema.version` 现值（决定 02 是否适用，P9）；② 生产 MySQL 实例是否"一实例多库共用高权限账号"（决定 `true` 是否绝对禁用，09 §8.5）；③ 生产租户库数量与执行人（P5） |

---

## 3. 评审要点 2 的直接回答（改 `false` 对 dev 的实际后果）

| 场景 | 后果 | 证据 |
|---|---|---|
| 现在改（两库表齐、版本齐） | **不坏**。`false` 只做版本校验：`schema.version=8.0.0.0` = 期望值，`isEngineTablePresent`/`isHistoryTablePresent` 均真 ⇒ 通过 | DB 实测两库 `schema.version=8.0.0.0`、30+2 表齐全；`ProcessDbSchemaManager.schemaCheckVersion` 三条判定（版本相等、engine 表在、history 表在） |
| 清库后不跑 01 | **起不来**，抛 `FlowableException: No flowable tables in DB. Set property "databaseSchemaUpdate" "true" or value="create-drop"…` | `EngineSqlScriptBasedDbSchemaManager.schemaCheckVersion` 字节码字符串常量 + `isMissingTablesException` 分支 |
| 跑了 02 但漏 UPDATE | **起不来**，抛 `FlowableWrongDbException`（Expected `8.0.0.0`, but was `7.1.0.2`） | `ProcessDbSchemaManager.schemaCheckVersion` 字节码 `ldc "8.0.0.0"` + `FlowableWrongDbException.<init>` (P1) |
| 保持默认 `true`（现状） | 当前能跑（表已存在）；**一旦清库即重演 09 号事故**（空库 + 同实例多库 ⇒ `isTablePresent` 假阳性 ⇒ 跑 20+ 个升级脚本 ⇒ 28 条失败） | 09 §1.1 日志链 L7307-L7511、§2.2 JDBC 实测 12 行 vs 0 行；`spring-configuration-metadata.json` 中 `flowable.database-schema-update` `defaultValue: "true"` |

> 附：`database-schema-update: false` 并非"什么都不做"——字节码显示 `AbstractEngineConfiguration` 在 `databaseSchemaUpdate != null` 时仍构造 `SchemaOperationsEngineBuild`，`executeSchemaUpdate` 对 `"false"` 调 `SchemaManager::schemaCheckVersion`（BootstrapMethods `#192 = REF_invokeInterface SchemaManager.schemaCheckVersion`），`CommonDbSchemaManager.schemaCheckVersion()` 为空实现、仅引擎管理器做校验。这一点 17 号 §1.5 L146 未写，容易被当成"关掉就静默"。

---

## 4. 取证清单（可复现）

| 手段 | 命令/位置 | 关键结果 |
|---|---|---|
| DB 表数 | `information_schema.tables WHERE table_name LIKE 'ACT\_%'` | `scm_manager` 30、`scm_tenant_20250519_001` 30；`ACT_ID_*` 0 |
| DB 版本 | `SELECT * FROM <db>.ACT_GE_PROPERTY` | 两库 6 行；`common.schema.version=8.0.0.0`、`schema.version=8.0.0.0`、`schema.history=create(8.0.0.0)`、`next.dbid=1` |
| DB 运行时表 | `COUNT(*)` | `ACT_RE_PROCDEF`/`ACT_RE_DEPLOYMENT`/`ACT_RU_EXECUTION`/`ACT_HI_PROCINST` 均 0 |
| DB 索引 | `information_schema.statistics` | `ACT_IDX_BYTEAR_DEPL` 两库均存在于 `ACT_GE_BYARRAY(ACT_GE_BYTEARRAY)` |
| DB 租户 | `SELECT * FROM scm_manager.s_tenant_manager` | 1 行，`tenant=scm_tenant_20250519_001`，`url` 无 `nullCatalogMeansCurrent` |
| jar create | `flowable-engine-8.0.0.jar` / `flowable-engine-common-8.0.0.jar` | 表 7+5+20=32、索引 14+18+66=98、insert 4 条 |
| jar upgrade | 同上 | 只有 5 份涉及 7.1→8.0：`7102.to.7200.{engine,history}`、`7201.to.7202.engine`、`7202.to.8000.{engine,history}`；DDL 共 4 条 alter + 1 条 create index + 3 条版本 UPDATE |
| 字节码 | `javap -p -c -v` on `SchemaOperationsEngineBuild`、`ProcessDbSchemaManager`、`EngineSqlScriptBasedDbSchemaManager`、`CommonDbSchemaManager`、`AbstractEngineConfiguration` | 见 §1 P1/P10、§3 附注 |
| 属性元数据 | `flowable-spring-boot-autoconfigure-8.0.0.jar!/META-INF/spring-configuration-metadata.json` | `flowable.database-schema-update`，`type=String`，`defaultValue="true"`；旧键 `engine.process.schema.update` 已 `level=error` 弃用 |
| 仓库配置 | `scm-start/src/main/resources/application.yml`（22 行）、`application-dev.yml`（L608-623 flowable 段、L194/L243 URL） | 无 `database-schema-update`；无 `nullCatalogMeansCurrent` |
| 代码 | `scm-core-bpm/.../config/FlowableConfig.java:26-30` | 只 `setEventListeners`，无 schema 相关设置 |

---

## 5. 与任务卡的对应

| 评审要点 | 回答位置 |
|---|---|
| 1) 统一口径是否最优 / 有无更简单替代 | §0 结论表、§1.1 四方案比较（推荐 A + 官方脚本；C 为"零脚本"备选） |
| 2) 改 `false` 对 dev 的实际后果、清库后会不会起不来 | §3 四场景表（现在不坏；清库后必坏，且报错文本确定） |
| 3) 仅 SELECT 核实 | §4 取证清单（全部为 SELECT / 读文件 / 读 jar，无任何写操作） |
