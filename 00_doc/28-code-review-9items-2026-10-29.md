# 代码层收尾 9 项 —— 落地代码独立评审

- 评审对象：`00_doc/26-收尾清单9项方案与实施计划-2026-10-29.md`（下称"方案"）所称"9 项已全部落地"的实际代码/配置
- 评审方式：只读评审（未改动任何业务代码/pom/yml/sql），全部结论均由本机命令实证
- 评审时间：2026-10-29；评审人：reviewer-9items
- 环境：JDK 21.0.10、Maven 3.9.9（`-o` 离线）、MySQL Shell 8.0（容器库 `127.0.0.1:3306`）

> 注：任务单里给的文档名 `26-代码层收尾9项方案与实施清单-2026-10-29.md` 并不存在，实际文件是 `00_doc/26-收尾清单9项方案与实施计划-2026-10-29.md`（已按后者评审，见 R-07）。

---

## 1 逐项核对表（方案要求 vs 实际代码 vs 结论）

| # | 项 | 方案要求 | 实际落地（行号为实测） | 结论 |
|---|---|---|---|---|
| 1-① | Druid 硬钉 | `1.2.20 → 1.2.28`，保留 depMgmt 段 | `pom.xml:212` `<version>1.2.28</version>`（`:624` 为 druid-spring-boot-4-starter 同版本）；`dependency:tree` → `com.alibaba:druid:jar:1.2.28:compile` | 通过 |
| 1-② | 删 HikariCP 硬钉 | 删 5.0.1 交 BOM 7.0.2 | 声明已删（`pom.xml:769` 保留说明注释）；`dependency:tree` → `com.zaxxer:HikariCP:jar:7.0.2:compile` | 通过 |
| 1-③ | surefire | `2.19.1 → 3.5.6`，保留 `skipTests=true`（方案 A） | `pom.xml:923` 版本 3.5.6 + `:925` `<skipTests>true</skipTests>`；实测 `surefire:3.5.6:test` 正常执行 | 通过（但有 R-08 建议） |
| 1-④ | 删 spring-social | 删属性 + 4 条 depMgmt | `grep -rn "spring-social" --include=pom.xml` 仅剩 `pom.xml:262` 已注释行 | 通过 |
| 2 | 5 个 controller 模块删 restdocs | 5 个 pom 全删 | `grep -rn "spring-restdocs" --include=pom.xml` = 0 命中；编译绿 | 通过 |
| 3 | BPM 控制器 `javax→jakarta` | 仅 1 行 import | `TriggerController.java:10` `import jakarta.servlet.http.HttpServletRequest;`；全仓 `javax.servlet` 仅剩 `SystemSpringHttpSessionListener.java:7-12` 六行被注释掉的 import + `ScmSessionStrategy.java:10` 一处 Javadoc 文字，无任何可执行引用 | 通过（运行期见 §3-⑦） |
| 4-A | 删 12 处裸 `@JSONField` | 6 文件 12 处 | `git diff` 实测 −12/+10，恰为本批改动；全仓已无裸 `@JSONField`（`grep -E "@JSONField\s*$"` = 0），`scm-excel` 仅剩 6 处 `@JSONField(serialize = false)` | 通过（遗留 3 个死 import，R-06） |
| 4-B | 6 处 `@JSONField(name=)` 改双注解 | 5 个文件 6 处 | `AiWfNodeInputConfigVo:27-28,35-36`、`AiWfNodeParamRefVo:30-31,38-39`、`AiWfNodeIOVo:58-59`、`UploadFileResultAo:28` 均 `@JSONField(name=…) + @JsonProperty(…)` | 通过（行为已实证，§3-⑧） |
| 4-C | `AiWfNodeIOVo.option_labels` 是否漏改 | 方案未列 | 全仓 `grep -rin "option_labels\|optionLabels"` = **0 命中**，该字段不存在，不存在漏改 | 通过（提示词前提有误） |
| 5 | fastjson2 边界注释 | 13 pom / 14 处 | `grep -rc` 汇总 = 14 处（scm-framework 2 处）；新增 `00_doc/tools/check-fastjson-boundary.ps1`，实测 exit 0、14 命中/13 文件全豁免 | 通过（R-07/R-09 细节问题） |
| 6-A | 建表脚本 = 官方 jar 原样 | 3 份 create 逐字一致 | `diff -q` 与 `unzip -p` 抽取件：**3 份全部 IDENTICAL**（engine/history 来自 flowable-engine-8.0.0.jar，common 来自 flowable-engine-common-8.0.0.jar） | 通过 |
| 6-B | 32 张表 | 合计 32 张 ACT_ 表 | 官方 create 抽取 = 32 张；真库 `scm_tenant_20250519_001` = 32 张且**集合与脚本清单 diff 完全一致** | 通过（但"32 张 ACT_"表述不准，R-02） |
| 6-C | 04 升级脚本 | 拼接官方 upgrade 文件 | 实际 **6 个文件 / 11 条语句**（common 7101→7102；engine 7102→7200；history 7102→7200；engine 7201→7202；engine 7202→8000；history 7202→8000），逐语句 grep 官方 6 文件**全部逐字存在** | 通过（文档写 5/8，R-05；未演练，R-04） |
| 6-D | `database-schema-update: false` | 关闭自动建表/升级 | `application-dev.yml:609` 注释 + `:610 database-schema-update: false`（flowable 段首行） | 通过（口径不符，R-01） |
| 6-E | 真库版本 | schema.version = 8.0.0.0 | `scm_tenant_20250519_001`：32 表 / `schema.version=8.0.0.0` / `schema.history=create(8.0.0.0)` / `common.schema.version=7.1.0.2`（与官方"common 最后一步 7101→7102"一致） | 通过 |
| 7 | 上一批 JSON null 兼容 3 文件 | 仅回归 | `NullCompatSerializerModifier.java`、`ScmJsonMapperFactory.java`(mtime 11:40)、`CallbackMappingJackson2HttpMessageConverter.java` 本批未再改；编译绿 | 通过 |
| 8 | 全仓复核 | 632 项 / 无残留 | `git status --short` = **632 行**（本评审报告落盘后为 633，多出的 1 项即本文件）；`javax.servlet` 无有效引用（6 行注释 import + 1 处 Javadoc 文字）；`spring-restdocs`/`HikariCP:5`/`1.2.20` 在 pom 中 0 命中 | 通过 |

---

## 2 问题清单

### R-01 「统一建表策略」实际只覆盖 dev（严重度：high）

- 位置：`scm-start/src/main/resources/application-dev.yml:608-610`；`scm-start/src/main/resources/application.yml`（**无 `flowable:` 段**）
- 问题：开关只写在 dev profile。`javap -c` 反编译 `flowable-spring-boot-autoconfigure-8.0.0.jar::FlowableProperties` 构造器可见 `ldc "true" → putfield databaseSchemaUpdate`，即 **Flowable 8.0.0 默认值就是 `true`（自动建表/自动升级）**。仓库内只有 `application.yml` + `application-dev.yml` 两个文件，`prod / centos-ys / zlprod` 由部署侧提供 → 这些环境启动仍会自动 DDL，与方案"建表统一交给 `00_doc/sql/flowable-8.0.0-ddl/`"的目标相反。
- 必须怎么改：
  1. 把 `database-schema-update: false` 放公共 `application.yml` 的 `flowable:` 段（dev 如需可覆盖，但不要只留在 dev）；
  2. 在方案 §5 部署清单里加一行：其它 profile 的 yml 必须含该键，并附自检 SQL（见 R-02）；
  3. 加一条启动自检日志/断言（如启动后打印 `flowable.database-schema-update` 生效值），避免"以为关了其实没关"。

### R-02 方案 §2.3 的验收 SQL 期望值写错：`LIKE 'ACT\_%'` 只有 30 张（严重度：medium）

- 位置：方案 §2.3「验收」段的 SQL；本次核对 SQL
- 问题：真库 32 张表里有 **2 张不带 `ACT_` 前缀**：`FLW_RU_BATCH`、`FLW_RU_BATCH_PART`（8.0 create-common 里的批处理表，官方来自 `upgradestep.6501.to.6502.common.sql`）。所以 `WHERE table_name LIKE 'ACT\_%'` 实测返回 **30**，方案/清单写的"期望 32"照抄会误判为不合格。
- 必须怎么改：验收 SQL 改为
  `SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='<db>' AND (table_name LIKE 'ACT\_%' OR table_name LIKE 'FLW\_%');` → 期望 **32**；
  并要求与脚本表清单做集合 diff（本次实测 `diff` 输出"完全一致"）。

### R-03 验收只核租户库，但引擎启动阶段连的是主库 `scm_manager`（严重度：medium）

- 位置：方案 §2.3「在租户库执行/核对」；`scm-start/src/main/resources/application-dev.yml:194`（主数据源 = `scm_manager`）
- 问题：`spy.properties` 抓到的启动日志（`/d/spring_log/wms/tenants/master/info.log` 12:21:07）里，Flowable 首次访问是
  `url jdbc:mysql://127.0.0.1:3306/scm_manager… select VALUE_ from ACT_GE_PROPERTY where NAME_='common.schema.version'`
  → **无租户上下文时引擎落在 `scm_manager`**。本次两库都恰好是 32 表 / `schema.version=8.0.0.0`（实测 `scm_manager` 同为 8.0.0.0），所以结论仍成立；但验收清单只写租户库，新环境照做会漏掉主库；`database-schema-update: false` 下表不存在时引擎校验 `schema.version` 会直接抛异常导致启动失败。
- 必须怎么改：§2.3/§5 的核对与执行清单同时列出 `scm_manager` 与各租户库；给出"逐个库循环校验"的脚本片段。

### R-04 `04-upgrade-from-7.1.sql` 从未被执行验证，且覆盖起点被文档说宽了（严重度：medium）

- 位置：`00_doc/sql/flowable-8.0.0-ddl/04-upgrade-from-7.1.sql`；方案 §2.3
- 事实：两库 32 张表的 `create_time` 全部是 **2026-10-28 18:37/18:38**，`schema.history=create(8.0.0.0)`，且今天的启动日志里**没有任何 DDL**（`grep -c "create table ACT_"` = 0）→ 说明现库是 create 路径产物，04 升级路径零验证。另外官方 8.0.0 jar 内 common 最后一个升级步是 `7101.to.7102`、engine **没有** `7200.to.7201`，脚本实际起点是 **7.1.0.2**，而方案写"适用 7.1.x（7100/7101/7102）"。
- 必须怎么改：在临时库（如已有 `flowable_upgrade_probe`）用 7.1 官方 create 建库 → 跑 04 → 断言 `schema.version='8.0.0.0'` 且表集合与 32 张清单一致，把结果贴进方案 §4；脚本注释把"适用版本"改为 **≥7.1.0.2**。

### R-05 方案 §2.3 对 04 脚本的语句数/文件数未回填（严重度：low）

- 位置：方案 §2.3
- 问题：文档写"5 个文件 / 8 条语句"，实际 **6 个文件 / 11 条语句**（实现正确，文档滞后）。
- 必须怎么改：回填为 6/11，并列出 6 个 upgradestep 名称。

### R-06 删除裸注解后残留 3 行无用 import（严重度：low）

- 位置：`scm-excel/src/main/java/com/xinyirun/scm/excel/bean/importconfig/template/data/row/DataRowValidators.java:3`、`…/bean/importconfig/template/title/TitleCol.java:3`、`…/bean/importconfig/template/title/TitleRow.java:3`
- 问题：这 3 个文件已无任何 `@JSONField` 使用，`import com.alibaba.fastjson2.annotation.JSONField;` 成为死 import。且**方案 §2.8 自相矛盾**：先说"同步删 import（3 文件）：ExcelTemplate.java / DataCol.java / DataRow.java"，紧接着说"这 3 个文件仍有带参 `@JSONField`，不删 import"——真正该删的是上列另 3 个文件。
- 必须怎么改：删这 3 行 import；把方案该句改为 `DataRowValidators / TitleCol / TitleRow`。

### R-07 巡检脚本引用不存在的文档名 + 豁免表按文件名匹配（严重度：low）

- 位置：`00_doc/tools/check-fastjson-boundary.ps1:2`（注释）与 `:13-26`（`$exempt`）
- 问题：注释引用 `00_doc/26-代码层收尾9项方案与实施清单-2026-10-29.md`，该文件不存在（实际 `26-收尾清单9项方案与实施计划-2026-10-29.md`）；`$exempt` 只比对 `$f.Name`，同名不同包的新 controller 会被静默豁免。
- 必须怎么改：修正路径；`$exempt` 改存相对路径并用 `$_.Path -eq` 比对。

### R-08 `skipTests=true` 是硬编码，命令行无法打开测试闸门（严重度：low）

- 位置：`pom.xml:923-926`
- 证据：`mvn -o -pl scm-excel test -DskipTests=false` 仍输出 `[INFO] Tests are skipped.`（插件配置优先于同名属性）
- 必须怎么改：`<skipTests>${skipTests}</skipTests>`，并在 `<properties>` 里设 `<skipTests>true</skipTests>`；默认行为不变，但 `-DskipTests=false` 可用于将来接入真实用例。

### R-09 根 pom 同一 fastjson2 声明点两条重复注释（严重度：low）

- 位置：`pom.xml:233`（详细版）+ `pom.xml:234`（统一版）
- 必须怎么改：二选一，与其余 13 处保持同一句。

### R-10 模块数写错（严重度：low）

- 位置：方案 §4「26 模块 BUILD SUCCESS」
- 事实：`mvn -o compile` 的 Reactor 实为 **25** 个模块（`scm-start-parent` 在 `pom.xml:26` 被注释掉）。
- 必须怎么改：改为 25。

### 方案自身需修正的点（非实现错误，汇总）

1. §2.8 关于"同步删 import（3 文件）"与"这 3 个文件仍有带参 @JSONField，不删"自相矛盾（见 R-06）。
2. §2.3 语句数 5/8 与实际 6/11 不符（R-05）；验收 SQL 期望 32 与实际 30（R-02）；"32 张 ACT_ 表"表述应为"30 张 ACT_ + 2 张 FLW_"。
3. §2.3 只覆盖租户库（R-03）、未演练升级路径（R-04）、适用版本 7.1.x 说过宽（R-04）。
4. §4 模块数 26 应为 25（R-10）。
5. 任务提示词中的 `option_labels` 前提不成立（该字段全仓 0 命中，见 §1 #4-C）。

---

## 3 我实际跑过的命令与关键输出

① 编译（离线）
```
$MVN -o compile -DskipTests
[INFO] Reactor Summary for scm-parent 0.0.1-SNAPSHOT:   → 25 个模块全部 SUCCESS
[INFO] BUILD SUCCESS
```

② 依赖版本
```
$MVN -o -pl scm-start dependency:tree -Dincludes=com.alibaba:druid,com.zaxxer:HikariCP
[INFO] |        \- com.zaxxer:HikariCP:jar:7.0.2:compile
[INFO] \- com.alibaba:druid:jar:1.2.28:compile
[INFO] BUILD SUCCESS
```

③ DDL 与官方 jar 逐字比对
```
unzip -p flowable-engine-8.0.0.jar org/flowable/db/create/flowable.mysql.create.engine.sql  → diff 01-create-engine.sql   → IDENTICAL
同法 history / common(来自 flowable-engine-common-8.0.0.jar) → IDENTICAL
→ 3 份 create 逐字一致；合计 32 张表
```
```
04-upgrade-from-7.1.sql：`-- ====` 分节 6 个；非注释语句 11 条：
  create index ACT_IDX_EVENT_SUBSCR_EXEC_ID/PROC_ID| update common.schema.version='7.1.0.2'
  alter ACT_RU_ACTINST add COMPLETED_BY_ | update schema.version='7.2.0.0'
  alter ACT_HI_ACTINST add COMPLETED_BY_ | create index ACT_IDX_BYTEAR_DEPL
  update schema.version='7.2.0.2' | update schema.version='8.0.0.0'
  alter ACT_HI_PROCINST add END_USER_ID_ / STATE_
→ 逐语句 grep 官方 6 个 upgradestep 文件：全部"[OK] 逐字存在"
```

④ 真库核对（只读）
```
mysqlsh --sql --uri root@127.0.0.1:3306
scm_tenant_20250519_001:  ACT_% = 30 ；ACT_% OR FLW_% = 32 ；与脚本表清单 diff → 完全一致
ACT_GE_PROPERTY: schema.version=8.0.0.0 | schema.history=create(8.0.0.0) | common.schema.version=7.1.0.2
scm_manager:             ACT_% OR FLW_% = 32 ；schema.version=8.0.0.0（引擎无租户上下文时连的库）
create_time: 两库均为 2026-10-28 18:37/18:38（今天日志无任何 create/alter）
```

⑤ 巡检脚本
```
pwsh -NoProfile -ExecutionPolicy Bypass -File 00_doc/tools/check-fastjson-boundary.ps1
扫描 Controller 文件 181 个，命中 fastjson2 14 处  → [通过] 无新增 Controller 层 fastjson2 用法。 exit=0
```

⑥ 全仓复核
```
git status --short                      → 632 行（报告落盘前；落盘后 633）
grep -rn "javax.servlet" --include=*.java  → 仅 SystemSpringHttpSessionListener.java:7-12（6 行 // 注释 import）+ ScmSessionStrategy.java:10（Javadoc 文字）
grep -rn "spring-restdocs" --include=pom.xml → 0 ；"HikariCP:5" → 0 ；"1.2.20" → 0
grep -rin "option_labels|optionLabels" --include=*.java → 0
grep -rl "^import com.alibaba.fastjson2" --include=*.java → 255 文件（与方案一致）
```

⑦ surefire 闸门
```
$MVN -o -pl scm-excel test -DskipTests=false
[INFO] --- surefire:3.5.6:test (default-test) @ scm-excel ---
[INFO] Tests are skipped.        → 闸门无法从命令行打开（R-08）
```

⑧ `@JsonProperty` 行为实证（Jackson 3 真机探针，`tools.jackson.databind.json.JsonMapper`，classpath 取自 scm-ai 模块）
```
SER:   {"ref_inputs":[{"name":"n","node_param_name":"p1","node_uuid":"u1"}],
        "user_inputs":[{"limit":null,"max_length":10,...,"name":"q",...,"type":1,"uuid":null}]}
DESER user_inputs=1 max_length=7 ref_inputs=1 node_uuid=U      ← snake_case 入参可正常绑定
UploadFileResultAo: {"fileId":"F1",...}  ；反序列化 {"fileId":"F2"} → fileUuid=F2
```
→ E5 双注解在 Jackson 3 下确实生效；DB 侧仍走 `FastjsonInputConfigTypeHandler`（fastjson2），`@JSONField(name=)` 保留，存量 JSON 键名不变，无回归。

⑨ 运行期旁证（应用当前在 8088 运行）
```
GET http://127.0.0.1:8088/scm/actuator/health          → 200
GET http://127.0.0.1:8088/scm/ex/trigger/getRequest    → 500 {"message":"缺少租户信息"}（业务拦截器抛出，非 NoClassDefFoundError）
启动日志 12:21:07 ProcessEngine default created；今日无 DDL
```

### 未验证项（本评审未能取证，需实施方补）
- **重启后 `database-schema-update: false` 是否生效**：当前运行实例启动于 12:21，而 `application-dev.yml` 落盘时间为 12:57（DDL 脚本 12:55/12:56）→ 运行实例早于本次改动；评审方不重启业务进程。建议实施方重启后执行：`grep -i "schema.version\|updated schema\|performing create" <启动日志>` 应无升级/建表动作。
- **其它 profile（prod/centos-ys/zlprod）**：仓库内无对应 yml，无法核对，正是 R-01 的风险点。

---

## verdict
needs_revision
