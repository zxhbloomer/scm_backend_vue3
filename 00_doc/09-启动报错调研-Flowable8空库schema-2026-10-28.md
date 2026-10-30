# 启动报错调研：Flowable 8 在"空库"上误走 upgrade 导致启动失败

- 调研日期：2026-10-28
- 调研对象：`D:\temp\20261028\新建 文本文档 (3).txt`（12994 行应用启动日志，2026-10-28 18:09–18:10）
- 约束：**本次只调研，未修改任何仓库代码/配置**
- 结论等级：根因已闭环（源码字节码 + 数据库实测 + JDBC 复现 + 官方论坛实证 四重交叉验证）

---

## 0. 结论速览（TL;DR）

| 项 | 结论 |
|---|---|
| 报错本质 | Flowable 8.0.0 认为 `ACT_GE_PROPERTY` **"已经存在"**，于是走了 `upgrade` 分支而不是 `create` 分支 |
| 为什么它认为存在 | `DatabaseMetaData.getTables(catalog=null, schema=null, "ACT_GE_PROPERTY", ["TABLE"])` 在 MySQL 上 **跨 schema 匹配**，命中同实例其它数据库里的 `ACT_GE_PROPERTY` |
| 为什么目标库没有 | `scm_manager` 的 71 张 `ACT_*` 已在 S0 阶段清空（工作树内 `00_doc/sql/S0-02-清空.sql` 执行结果），`scm_tenant_20250519_001` 同样为 0 张 |
| 直接后果 | 版本号读不到 → 版本被判为 `null` → 回落到"升级起点版本" `6.1.2.0` → 从 6.1.2.0 开始跑 20 多个升级脚本 → 空表全炸 → `couldn't upgrade db schema` → 引擎 Bean 创建失败 → 应用启动失败 |
| 与 Boot 4.1.2 / Jackson 3 / S1 改造的关系 | **无关**。S1 改造只导致 POM 与 Java 编译层问题，本次是**数据库状态 + Flowable 元数据探测**问题 |
| 触发它的真正变量 | ①`scm_manager` 被清空；②同实例上还有 11 个库留着 `ACT_GE_PROPERTY`；③JDBC 账号 `root` 对所有库有权限 |
| 不算问题的地方 | p6spy / Druid / dynamic-datasource 都只是包装层，日志 L7310 显示切换 primary 正常；MySQL 8.4.6 本身正常 |

---

## 1. 报错现场（日志原文）

### 1.1 唯一根因链（所有 `Caused by` 去重后只剩这一条）

```
L7307  SchemaOperationsEngineBuild : Executing schema management with setting true from engine bpmn
L7310  DynamicRoutingDataSource    : dynamic-datasource switch to the primary datasource
L7311  p6spy : ...|url jdbc:mysql://127.0.0.1:3306/scm_manager?...|select VALUE_ from ACT_GE_PROPERTY where NAME_ = ?|select VALUE_ from ACT_GE_PROPERTY where NAME_ = 'common.schema.version'
L7312  ERROR o.f.c.e.impl.db.CommonDbSchemaManager : Could not get property from table ACT_GE_PROPERTY
L7314  java.sql.SQLSyntaxErrorException: Table 'scm_manager.act_ge_property' doesn't exist
L7508  INFO  o.f.c.e.impl.db.CommonDbSchemaManager : upgrading flowable common schema from 6.1.2.0 to 8.0.0.0
L7509  INFO  ... : Upgrade needed: 6120 -> 6200. Looking for schema update resource for component 'common'
L7510  INFO  ... : performing upgrade on common with resource org/flowable/common/db/upgrade/flowable.mysql.upgradestep.6120.to.6200.common.sql
L7511  INFO  ... : Found MySQL: majorVersion=8 minorVersion=4
（随后 28 条 "problem during schema upgrade, statement ..."）
最终 Caused by:
  org.flowable.common.engine.api.FlowableException: couldn't upgrade db schema: insert into ACT_GE_PROPERTY values ('common.schema.version', '6.2.0.0', 1)
  Caused by: java.sql.SQLSyntaxErrorException: Table 'scm_manager.act_ge_property' doesn't exist
```

**注意 L7508 这一行**：它说明 Flowable 认为"要升级的起点是 6.1.2.0"——一个 2021 年的版本号。空库不可能有 6.1.2.0 的 schema，这个版本号是 `getUpgradeStartVersion()` 在**版本号为 null 时的默认兜底值**。

### 1.2 最终异常文本与社区完全一致

`org.flowable.common.engine.api.FlowableException: couldn't upgrade db schema: insert into ACT_GE_PROPERTY values ('common.schema.version', '6.2.0.0', 1)` —— 与 flowable-engine issue #3661 里贴出的异常逐字相同（见 §6 引用）。

---

## 2. 机制解剖（源码/字节码级）

### 2.1 Flowable 8 的判定分支

`org.flowable.common.engine.impl.db.AbstractSqlScriptBasedDbSchemaManager`：

```
public void schemaUpdate(String version) {
    if (isUpdateNeeded()) {            // ← 关键分叉
        ... dbSchemaUpgrade(component, indexOf(getSchemaVersion()), version)
    } else {
        schemaCreate();
    }
}

public boolean isUpdateNeeded() {
    return isTablePresent(this.table);   // this.table = "ACT_GE_PROPERTY"
}
```

`javap -p -c` 实测（`flowable-engine-common-8.0.0.jar`）：

- `CommonDbSchemaManager` 构造器硬编码三件事：`table = "ACT_GE_PROPERTY"`、`component = "common"`、`prop = "common.schema.version"`
- `ServiceSqlScriptBasedDbSchemaManager.schemaUpdate(String)`：分支处 `aload_0; getfield table; invokevirtual isTablePresent` 后 `ifne` → 命中 true 就走 `astore 2/3` + `invokevirtual dbSchemaUpgrade`；**false 才走 `schemaCreate()`**
- `AbstractSqlScriptBasedDbSchemaManager.isTablePresent(String)`：

```
catalog = getDatabaseCatalog()
schema  = getDatabaseSchema()
if (!getDatabaseConfiguration().isTablePrefixIsSchema()) tableName = prependDatabaseTablePrefix(tableName)
try (ResultSet rs = metadata.getTables(catalog, schema, tableName, JDBC_METADATA_TABLE_TYPES)) {
    return rs.next();
}
```

`JDBC_METADATA_TABLE_TYPES = {"TABLE"}`。**MySQL 下 `getDatabaseCatalog()`/`getDatabaseSchema()` 默认为 null** → 即 `getTables(null, null, "ACT_GE_PROPERTY", {"TABLE"})`。

### 2.2 关键实证：JDBC 直接复现（`D:\temp\meta-probe\MetaProbe.java`）

用与运行环境同版本的驱动 `mysql-connector-j-9.7.0.jar` 连 `scm_manager`：

```
catalog   = scm_manager
schema    = null
dbTerm    = 8.4
getTables(null,        null, "ACT_GE_PROPERTY", {"TABLE"}) -> rows=12   [bytedesk.ACT_GE_PROPERTY][flowable_probe.ACT_GE_PROPERTY][flowable_tenantclone.act_ge_property] ...
getTables("scm_manager", null, "ACT_GE_PROPERTY", {"TABLE"}) -> rows=0
```

**这就是"假阳性"的直接证据**：传 `null` catalog 时驱动返回了**全实例 12 个 schema** 的命中行；传真实 catalog 时返回 0 行。Flowable 传的是 `null`，于是 `isTablePresent` 返回 `true`。

### 2.3 数据库现状（mysqlsh 实测，`root@127.0.0.1:3306`）

```
SELECT table_schema, table_name FROM information_schema.tables WHERE table_name='ACT_GE_PROPERTY';

bytedesk                          ACT_GE_PROPERTY
flowable_probe                    ACT_GE_PROPERTY
flowable_tenantclone              act_ge_property
flowable_upgrade_probe            ACT_GE_PROPERTY
scm_manager_20261028              act_ge_property
scm_tenant_20250519_001-20250618  act_ge_property
scm_tenant_20250519_001-20250619-2 act_ge_property
scm_tenant_20250519_001-20250627  act_ge_property
scm_tenant_20250519_001_0829      act_ge_property
scm_tenant_20250519_001_20261028  act_ge_property
wflow                             act_ge_property
wflow_pro                         ACT_GE_PROPERTY
```

- **`scm_manager` 与 `scm_tenant_20250519_001` 均不在列表里**——它们已被 S0 清空（`ACT_*` 各 0 张）
- 同实例仍有 **11 个库**带着 `ACT_GE_PROPERTY`，其中 `flowable_probe`(39 张)、`flowable_tenantclone`(71)、`flowable_upgrade_probe`(30)、`bytedesk`(56)、`wflow`(30)、`wflow_pro`(30) 是本次升级过程中**我自己建的探针库/历史库**
- `lower_case_table_names = 2`，MySQL `8.4.6` → 用 `LIKE 'ACT_GE_PROPERTY'` 匹配时大小写不敏感，`act_ge_property` 与 `ACT_GE_PROPERTY` 都能命中

### 2.4 两个脚本的原文（定位"6.2.0.0"这个数字的来源）

`flowable-engine-common-8.0.0.jar` 内：

**create 脚本** `org/flowable/common/db/create/flowable.mysql.create.common.sql`（546 条非空语句）：
```sql
create table ACT_GE_PROPERTY ( NAME_ varchar(64), VALUE_ varchar(300), REV_ integer, primary key (NAME_) ) ...;
insert into ACT_GE_PROPERTY values ('common.schema.version', '8.0.0.0', 1);   -- ← 若走 create，版本会直接写成 8.0.0.0
insert into ACT_GE_PROPERTY values ('next.dbid', '1', 1);
```

**upgrade 脚本** `.../upgradestep.6120.to.6200.common.sql`（25 条非空语句）：
```sql
insert into ACT_GE_PROPERTY values ('common.schema.version', '6.2.0.0', 1);   -- ← 第 1 条就是它，正是日志里那句
alter table ACT_RU_TASK add column SCOPE_ID_ varchar(255);
...
```

即：**报错文本 = 该升级脚本的第 1 条语句**。空库上，这条 `insert` 与后续 24 条 `alter/create index` 全部失败（共 28 条记录失败），Flowable 记录第一条后抛出 `couldn't upgrade db schema`。

### 2.5 完整因果图

```
S0 清空 scm_manager 的 71 张 ACT_*            ┐
同实例另有 11 个库保留 ACT_GE_PROPERTY        ├→ isTablePresent("ACT_GE_PROPERTY") = true（假阳性）
JDBC 账号 root 可访问全部库，Flowable 传 null catalog ┘
                                    │
                                    ▼
                     getSchemaVersion() → select ... where NAME_='common.schema.version'（在 scm_manager 执行）
                                    │   → Table 'scm_manager.act_ge_property' doesn't exist → 返回 null
                                    ▼
                     getUpgradeStartVersion() 兜底 = "6.1.2.0"
                                    ▼
                     dbSchemaUpgrade("common", 6.1.2.0 → 8.0.0.0) → 跑 6120→6200 起的所有升级脚本
                                    ▼
                     第一条 insert 就失败 → 28 条失败 → FlowableException → ProcessEngine Bean 创建失败 → 启动失败
```

---

## 3. 为什么"现在"才炸（时间线）

| 时间点 | 状态 | 结果 |
|---|---|---|
| S0 之前（Boot 3.5.5 + Flowable 7.1.0） | `scm_manager.ACT_GE_PROPERTY` 存在，`common.schema.version = 7.1.0.2` | `isTablePresent=true` → 读版本 = 7.1.0.2（**在同一个库成功读到**）→ 走 7.1.0.2→8.0.0.0 的合法升级 |
| S0 执行后 | `scm_manager` 与 tenant 库的 71 张 `ACT_*` 全部 DROP | `isTablePresent` 仍因**其它库**返回 true，但版本查询在 `scm_manager` 落空 → null → 6.1.2.0 兜底 → 崩 |
| 本次启动（日志） | 同上 | 崩溃 |

**重要**：这不是"清空导致表没了"这么简单——如果 `isTablePresent` 能正确返回 false，Flowable 会自己执行 create 脚本把 57 张表建出来并把版本写成 8.0.0.0，一切正常。真正的坑是**跨库元数据假阳性**，而它的触发条件是"同实例还有别的库带着 ACT_GE_PROPERTY"。

---

## 4. 与本轮 S1 改造的关系（排除项）

- S1 只改了 POM 坐标/版本与 Java 源码 API，**未触碰任何 Flowable schema 配置**（`FlowableConfig.java` 仅注册事件监听器）
- `application-dev.yml` 的 `flowable:` 段（599 行起）**未设置 `database-schema-update`**，因此取默认值 `true`；`workflow.db-type: mysql`；dmn/cmmn/idm/app/eventregistry/content 均 `false`，async-executor 关，`check-process-definitions: false`
- 日志证明 `setting true from engine bpmn`，即默认值生效，与"S1 未改配置"一致
- 因此：**本次启动失败不是升级到 Boot 4.1.2 / Flowable 8 引入的回归**，而是 S0 数据清空 + 本机多库共存叠加出来的环境问题。（对生产环境的含义：只要目标库的 ACT_ 表完整，正常 upgrade 路径成立；Flowable 8 的兼容性结论不受本问题影响）

---

## 5. 候选修复方案（本次只列出，不实施）

| # | 方案 | 做法 | 有效性 | 副作用/成本 | 评价 |
|---|---|---|---|---|---|
| **A** | **专用数据库账号 + 最小权限**（推荐，根治） | 建 `flowable@%` 账号，只 `GRANT ALL ON scm_manager.*` 与 `scm_tenant_20250519_001.*`，不给其它库权限；数据源换用它 | `getTables(null,null,...)` 只能看到授权库 → 空库时返回 false → 自动 create | 需改 `application-dev.yml` 数据源账号（配置层，非代码）；`root` 仍需保留给运维 | ★★★★★ 官方论坛同一问题的解法就是"每个 schema 一个专用账号" |
| **B** | 清掉本机无关库的 `ACT_*` | DROP `flowable_probe`、`flowable_tenantclone`、`flowable_upgrade_probe`、`bytedesk`、`wflow`、`wflow_pro`、`scm_manager_20261028`、4 个历史库等（或只 DROP 其 `ACT_*` 表） | 假阳性消失 → 自动 create | 删数据；`bytedesk`/`wflow` 可能不是本项目所有，需确认 | ★★★☆☆ 适合本机开发环境，治标 |
| **C** | 恢复 S0 备份 → 走正常 upgrade | 把 `D:\temp\bpm-backup-20261028` 的 `scm_manager` 备份导回（版本 7.1.0.2），再让 Flowable 8 升级到 8.0.0.0 | 路径与生产完全一致，最贴近真实 | 与"清空重做"的既定决策冲突；数据回来又是 748 条 wms 脏变量 | ★★☆☆☆ 仅在需要"模拟生产升级"时用 |
| **D** | 手工预建 schema | 直接对 `scm_manager`/tenant 执行 jar 内 `flowable.mysql.create.common.sql` + engine/history 等各组件 create 脚本，或只建 `ACT_GE_PROPERTY` 并写入 `('common.schema.version','8.0.0.0',1)` | 立即能启动 | 易漏表/漏组件；版本号手写有写错风险 | ★★☆☆☆ 应急手段 |
| **E** | 显式指定 schema/catalog | 配置 `flowable.database-schema` / `flowable.database-catalog`，或 JDBC URL 加 `nullCatalogMeansCurrent=true` | 理论上能让 `getTables` 只查当前库 | **未经验证**：MySQL 的 catalog/schema 语义与 JDBC4 有偏差，Connector/J 9.x 实测 `null` catalog 会跨库；需先做小实验再定 | ★★☆☆☆ 待实测 |

**建议组合**：本机开发环境先做 **A（专用账号）+ B（清理探针库）** 立即恢复；生产推行 A，因为生产同样存在"同实例多库"的可能性（历史库 `-20250618/-20250619-2/-20250627/_0829` 都在同一个 MySQL 上）。

---

## 6. 引用来源

**官方/一手**
1. Flowable 8.0.0 jars 反编译与资源原文（本地 `D:\2025_project\99_mvnrepository\org\flowable\flowable-engine-common\8.0.0\flowable-engine-common-8.0.0.jar`）：
   - `AbstractSqlScriptBasedDbSchemaManager#isUpdateNeeded/isTablePresent`
   - `ServiceSqlScriptBasedDbSchemaManager#schemaUpdate(String)`
   - `org/flowable/common/db/create/flowable.mysql.create.common.sql`
   - `org/flowable/common/db/upgrade/flowable.mysql.upgradestep.6120.to.6200.common.sql`
2. 本地 JDBC 复现程序：`D:\temp\meta-probe\MetaProbe.java`（mysql-connector-j 9.7.0，结果见 §2.2）
3. 应用启动日志：`D:\temp\20261028\新建 文本文档 (3).txt`（L7307–L7514 等）

**社区实证（第三方，非本项目）**
4. Flowable Forum — *Flowable version mismatch*：<https://forum.flowable.org/t/flowable-version-mismatch/6375>
   原话："it try to find the ACT_GE_PROPERTY in all the available schema for the database user. So it finds the older version there and in next step it try to get/update the table in current schema where it fails." / "It comes to ServiceSqlScriptBasedDbSchemaManager class. isUpdateNeeded internally check old db schema and returns true(Table exists). Then getSchemaVersion() again tries to find the same table in new schema and fails." / 解法："It worked fine when I created a database users for each schema which do not have access to other schema."
5. flowable/flowable-engine issue #3661：<https://github.com/flowable/flowable-engine/issues/3661> — 贴出的最终异常 `couldn't upgrade db schema: insert into ACT_GE_PROPERTY values ('common.schema.version', '6.2.0.0', 1)` 与本次逐字一致
6. Flowable Forum — *Can not auto creation db tables*：<https://forum.flowable.org/t/can-not-auto-creation-db-tables/2922> — 同类 `Table 'xxx.act_ge_property' doesn't exist` 场景

---

## 7. 待确认（需用户拍板，之后再动代码/配置）

1. 本机是否采用 **A + B** 组合立即恢复启动？
2. `bytedesk` / `wflow` / `wflow_pro` 是否本项目资产？能否清理（B 的前提）
3. 生产/测试环境是否也存在"一个 MySQL 实例多库、共用 root"的情况？若存在，应把 A 列为上线前必做项
4. 方案 E 是否需要实测（我可以做一个不改仓库代码的独立小实验）

---

## 8. 附：本机 MySQL 库清单与"无关库"的界定（2026-10-28 18:20 实测）

### 8.1 全部 schema（只列与本问题相关的列）

| 库名 | 表数 | 大小 | 引擎表(ACT_) | flowable 版本 | 建库时间 | 归属 | 会触发假阳性 |
|---|---|---|---|---|---|---|---|
| `scm_tenant_20250519_001-20250627` | 370 | **540.9 MB** | 71 | 7.1.0.2 | 2025-06-27 | 本项目历史快照 | **是** |
| `scm_tenant_20250519_001-20250618` | 370 | **533.9 MB** | 71 | 7.1.0.2 | 2025-06-22 | 本项目历史快照 | **是** |
| `scm_tenant_20250519_001-20250619-2` | 369 | **524.1 MB** | 71 | 7.1.0.2 | 2025-06-22 | 本项目历史快照 | **是** |
| `scm_tenant_20250519_001_20261028` | 482 | 97.1 MB | 71 | 7.1.0.2 | 2026-10-28 16:43 | 本项目（S0 当天演练快照） | **是** |
| `scm_tenant_20250519_001_0829` | 449 | 89.9 MB | 71 | 7.1.0.2 | 2025-08-29 | 本项目历史快照 | **是** |
| `scm_tenant_20250519_001` | 415 | 74.7 MB | **0** | — | 2025-06-04 | **本项目当前租户库（目标库）** | 否（已清空） |
| `flowable_tenantclone` | 71 | 45.5 MB | 71 | 7.1.0.2 | 2026-10-28 14:42 | **本次评审克隆的探针库** | **是** |
| `scm_manager_20261028` | 87 | 26.7 MB | 71 | 7.1.0.2 | 2026-10-28 16:46 | 本项目（S0 当天演练快照） | **是** |
| `bytedesk` | 233 | 7.9 MB | 56 | 7.1.0.2 | 2025-09-16 | **外部开源客服系统 Bytedesk** | **是** |
| `wflow` | 50 | 3.0 MB | 30 | **6.7.2.0** | 2025-08-13 | **外部参考项目 wflow** | **是** |
| `wflow_pro` | 50 | 2.6 MB | 30 | **6.7.2.0** | 2024-12-31 | **外部参考项目 wflow** | **是** |
| `flowable_probe` | 45 | 2.4 MB | 39 | 8.0.0.0 | 2026-10-28 14:17 | **本次 Flowable 8 探针库** | **是** |
| `flowable_upgrade_probe` | 32 | 2.3 MB | 30 | 7.1.0.2->8.0.0 | 2026-10-28 14:48 | **本次升级演练探针库** | **是** |
| `scm_manager` | 19 | 0.8 MB | **0** | — | 2025-06-04 | **本项目 manager 库（目标库）** | 否（已清空） |
| `instockdb` / `jimureport` / `metersphere` / `finhack` / `fs` / `beaver` / `rycrm-*` / `minimalist` | 3–105 | 0.4–19.8 MB | 0 | — | 2024-12 ~ 2026-04 | 其它项目 | 否（无 ACT_ 表） |

### 8.2 `bytedesk` 是什么

- **代码侧证据**：全仓只有一处提到它 —— `pom.xml:992` 的注释 `<!-- Maven 仓库配置 - 参考bytedesk项目 -->`。本项目只是**参考过它的 Maven 仓库配置**，代码不依赖它。
- **数据库侧证据**：233 张表，业务表全是 `bytedesk_*` 前缀（`bytedesk_core_message` / `bytedesk_kbase_*` / `bytedesk_service_agent_*` / `bytedesk_ticket` / `bytedesk_call_*` / `bytedesk_plugin_kanban_*` / `bytedesk_voc_*` …），即**开源多渠道客服系统 Bytedesk（宛在）** 的数据模型；另带 `QRTZ_*`（Quartz）与 56 张 Flowable 引擎表（7.1.0.2）。
- **数据量**：所有 `bytedesk_*` 业务表 `table_rows` **全为 0**，仅 `databasechangelog`(2)、`QRTZ_JOB_DETAILS`(7)、`QRTZ_TRIGGERS`(7) 有内容。
- **时间**：2025-09-16 13:16–14:16 一次性建完 → 当时本地起过一次做评估/对照，之后一直空置。
- **判断**：与 SCM 无关的**外部项目残留库**，不含本项目任何数据。

### 8.3 `wflow` / `wflow_pro` 是什么

- **它们才是本项目审批流的"前身"**：`scm-core-bpm` 里保留了它的影子 ——
  - `scm-core-bpm/src/main/java/com/xinyirun/scm/core/bpm/config/WflowGlobalVarDef.java`（`WFLOW_NODE_PROPS` / `WFLOW_FORMS` / `WFLOW_TASK_AGREE` 等变量名全部来自它）
  - `scm-core-bpm/.../utils/WFlowToBpmnCreator.java`，类注释就写着「**wflow -> bpmnModel 转换**」
  - `MemoryProcessNodeCatchServiceImpl` 中注释掉的 `WflowModels` 实体引用
- **数据库侧**：50 张表 = 30 张 Flowable 引擎表（**6.7.2.0**）+ 12 张 `wflow_*` 业务表（`wflow_users` 10 行 / `wflow_departments` 7 / `wflow_roles` 3 / `wflow_model_groups` 3，其余 0 行）
- **时间**：`wflow_pro` 2024-12-31 建；`wflow` 2025-08-13 建
- **判断**：外部的 wflow 工作流参考项目，只有演示数据；**不是本项目库**，但删之前值得留一份 `mysqldump`（它是 BPM 模块的设计来源，做回归对照时可能有用）。

### 8.4 "3 个探针库"是什么（本次调试产物）

| 库 | 表数 | 大小 | 产生时间 | 来源 |
|---|---|---|---|---|
| `flowable_probe` | 45 | 2.4 MB | 2026-10-28 14:17 | Flowable 8 独立探针工程 `D:\temp\flowable8-probe` |
| `flowable_tenantclone` | 71 | 45.5 MB | 14:42 | BPM 评审时从租户库克隆的演练库（`D:\temp\review-bpm\clone-tenant.sql`） |
| `flowable_upgrade_probe` | 32 | 2.3 MB | 14:48 | 7.1.0.2 -> 8.0.0 升级/回滚演练 |

→ 三个都是**今天我自己造的，删掉零风险**。

### 8.5 修正：A 与 B 是「替代」关系，不是「叠加」（对 §5 建议的更正）

| | 单独是否足够修复本 bug | 机理 | 代价 |
|---|---|---|---|
| **A 专用账号** | **足够** | 受限账号的 `information_schema` 只暴露被授权库 → `getTables(null,null,'ACT_GE_PROPERTY')` 只可能命中目标库 → 空库时返回 false → 走 create | 建账号 + 改数据源配置（**多租户动态数据源要一并授权**，否则切租户时复现） |
| **B 清理库** | **足够** | 其余 11 个 `ACT_GE_PROPERTY` 消失 → 只剩目标库（0 张）→ 返回 false | 删数据：1.6 GB 历史快照 + 2 个今天快照 + 3 个外部库 |
| A + B | 不是必需 | 二者各自都能独立解除假阳性 | 叠加只是「纵深防御」 |

**准确建议（替换 §5 的组合建议）**：

1. **首选 A**（唯一根治，且能防「以后又冒出一个带 ACT_ 的库」）；前提是把 tenant 库一起授权，并验证切租户路径。
2. **B 只做最小集**：`flowable_probe`、`flowable_tenantclone`、`flowable_upgrade_probe`（我造的，零风险）。但删完命中数只从 12 → 9，**仍不够**；要单独靠 B 立即恢复启动，必须连 `bytedesk`/`wflow`/`wflow_pro`/5 个快照一起清——那就不是「零风险」了。
3. 因此：**要立刻能启动且不该删别人数据 → 选 A；不许改配置 → 选 B 全量（需你确认删 5 个本项目快照 + 3 个外部库）**。二者选一，不必都做。

> 注：A 的「受限账号 metadata 只返回授权库」依据 MySQL `information_schema` 按权限过滤的机制推断，尚未在本机实测（实测需建账号）。需要的话我可以做一次不改仓库代码的验证。

---

## 9. 【新增·推荐方案 D'】一个 JDBC URL 参数即可，无需建账号、不删任何库

> **前置最高指示（2026-10-28 用户明确）**：本项目**只允许操作两个库** —— 主库 `scm_manager`（系统库，经表 `s_tenant_manager` 解析租户库）与租户库 `scm_tenant_20250519_001`。本机其它任何库与本项目无关，**严禁增删改**。
> 因此 **§5 的 B 方案（清理无关库）作废**，§8.5 中依赖 B 的结论一并作废。

### 9.1 实测：`nullCatalogMeansCurrent=true` 能让假阳性归零

用 `mysql-connector-j-9.7.0` 连 `scm_manager`，同一个 `getTables` 调用在三种 URL 下的结果（`D:\temp\meta-probe\MetaProbe2.java`）：

| URL 参数 | `getTables(null, null, "ACT_GE_PROPERTY", {"TABLE"})` | `getTables("scm_manager", null, ...)` |
|---|---|---|
| 默认（不写该参数） | **12 行**（跨 12 个 schema） | 0 行 |
| **`nullCatalogMeansCurrent=true`** | **0 行 ← 修复** | 0 行 |
| `nullCatalogMeansCurrent=false` | 12 行 | 0 行 |

即：加上这一个参数后，Flowable 的 `isTablePresent()` 在空库上会正确返回 `false` → 走 `create` 分支 → Flowable 8 自己建表并写入 `common.schema.version = 8.0.0.0` → 启动正常。

**结论：不再需要「专用数据库账号」（方案 A），不需要删除任何库（方案 B），也不需要手工建表（方案 D）。**

### 9.2 需要改的 3 个位置（均在允许范围内）

| # | 位置 | 当前值 | 改为 |
|---|---|---|---|
| 1 | `scm-start/src/main/resources/application-dev.yml` **L194** | `jdbc:mysql://127.0.0.1:3306/scm_manager?...&rewriteBatchedStatements=true` | 末尾追加 `&nullCatalogMeansCurrent=true` |
| 2 | 同文件 **L243** | 同上（相同 URL，第二个数据源块） | 同上 |
| 3 | `scm_manager.s_tenant_manager` 唯一 1 行（`id=1`, tenant=`scm_tenant_20250519_001`） | `url` 列指向租户库的 JDBC URL | 同样追加 `&nullCatalogMeansCurrent=true` |

> 第 3 项必须做：租户库连接串不在 yml 里，而是存于主库 `s_tenant_manager.url`；不动它，切换到租户数据源时会以同样的假阳性再次启动失败。

### 9.3 为什么这个参数是对的（原理）

- `DatabaseMetaData.getTables(catalog, schema, pattern, types)` 中，MySQL Connector/J 对 `catalog == null` 的语义由 `nullCatalogMeansCurrent` 决定：
  - `true` → 视为"当前库"（= `scm_manager`），只在该库内匹配 → 空库返回 0 行（**正确语义**）
  - `false` → 视为"所有库"（跨 schema），返回 12 行（**假阳性来源**）
- Flowable 的 `AbstractSqlScriptBasedDbSchemaManager.isTablePresent()` 传的正是 `getDatabaseCatalog()`（MySQL 下为 `null`）→ 踩中 `false` 语义。
- 该参数是**驱动层**参数，Druid、p6spy 包装不影响（本次实测即用裸驱动验证；线上链路是 Druid→p6spy→驱动，参数仍由驱动解析）。

### 9.4 未验证项 / 风险

1. 仅在本机 `mysql-connector-j 9.7.0` + MySQL `8.4.6` 实测通过；上线前应在目标环境的驱动版本上复测一次。
2. 参数只影响元数据查询，不影响业务 SQL，但**建议仍做一次全链路冒烟**（登录、列表、BPM 发起/审批、Redis、MQ）。
3. 若后续有人在本机新建带 `ACT_*` 的库，该参数依然有效（因为语义已固定为"只看当前库"），这是它优于方案 A/X 的地方。

---

## 附注（2026-10-29）：§9.2 的 `nullCatalogMeansCurrent` 三处从未落地

实测（grep 全仓 `*.java/*.yml/*.xml/*.properties`，排除 target/.git）：`nullCatalogMeansCurrent` **代码与配置 0 命中**，仅文档内有 7 行引用。⇒ 本方案提出时写在三处 JDBC URL 的改动**一次都没有实施**。

处置：该路径**已被 `17-代码层剩余项审计与收尾清单` §1.5 取代**（改为公共层 `flowable.database-schema-update: false` + 归档官方 DDL 脚本）。执行者请勿按本文 §9.2 再改 JDBC URL，避免两头改冲突。
