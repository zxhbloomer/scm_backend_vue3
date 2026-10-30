# 独立评审：#8 `@JSONField` 治理 / #9 fastjson2 边界

- 评审对象：`00_doc/26-收尾清单9项方案与实施计划-2026-10-29.md` §2.8（102-116 行）、§2.9（118-124 行）、§6（155-163 行）
- 评审方式：**只读**，未修改任何代码/pom；全部数字由命令实测或编译探针实证（探针源码在 `/tmp/jp/Probe*.java`，未落入仓库）
- 时间：2026-10-29
- 复现环境：项目根 `D:\2026_project\00_scm\backend\scm_backend`；bash + git-bash grep；JDK 21.0.10（`/d/2025_project/99_tools/jdk/jdk-21.0.10/bin`，注意 `JAVA_HOME` 指向的 `jdk-21.0.8` 不存在）；离线仓 `D:/2025_project/99_mvnrepository`，fastjson2 **2.0.66**（`pom.xml:61`），Jackson **3.1.7**（`tools.jackson.*` 包名）

---

## 逐项裁决

| # | 检查点 | 文档说法 | 我的实测值 | 是否成立 | 必须修正 |
|---|---|---|---|---|---|
| 1 | 全仓 `@JSONField` 精确数量 | 51 处 / 17 文件 | **51 行 / 17 文件**（`grep -rn --include=*.java '^[[:space:]]*@JSONField' . \| grep -v '/target/'` → `wc -l`=51，`cut -d: -f1 \| sort -u \| wc -l`=17） | ✅ 成立 | — |
| 2 | 51 处逐处原文 | 未列全量 | 已逐处打印，见下节「@JSONField 全量清单」51 行 | ✅ 成立 | — |
| 3 | 裸注解数量/文件/行号 | 删 12 处 / 6 文件；`ExcelTemplate.java:40,45`、`DataCol.java`4 处、`DataRow.java`2 处、`TitleCol.java`2 处、`TitleRow.java`1 处、`DataRowValidators.java:23` | **12 处 / 6 文件**，文件与行号**逐一准确**：ExcelTemplate 40,45；DataCol 31,36,42,45；DataRow 34,42；DataRowValidators 23；TitleCol 17,23；TitleRow 21 | ✅ 成立 | 文档未给 DataCol/DataRow/TitleCol/TitleRow 行号，建议回填（见 J-8） |
| 4 | 裸注解可否直接删（访问器 + fastjson2 依赖） | 删 | 12 个字段**全部有 Lombok 访问器**（ExcelTemplate/DataRow/TitleCol/TitleRow 显式 `@Getter/@Setter`，DataCol/DataRowValidators `@Data`）→ Jackson 可序列化；探针 P2：同构 bean 带/不带裸 `@JSONField`，fastjson2 序列化均为 `{"index":0}`、反序列化均得 `name=x,index=7` → **裸注解对 fastjson2 是 no-op** | ✅ 可删（12/12） | 删除时同步删 3 个文件的 `import ...JSONField`（J-6） |
| 5 | 6 处 `serialize=false` 保留 | 保留 | **6 处准确**：`ExcelTemplate.java:34(isInit),62,72,82`、`DataCol.java:51(validators)`、`DataRow.java:48(validators)` | ✅ 成立 | — |
| 6 | 6 处带 `name=` 只需「加注释护栏」 | 加注释护栏 | 位置准确（`UploadFileResultAo:26`、`AiWfNodeInputConfigVo:26,33`、`AiWfNodeParamRefVo:29,36`、`AiWfNodeIOVo:57`），但**护栏不足**：探针 P4 —— `{"inputConfig":{"user_inputs":[{"name":"q","max_length":50}],"ref_inputs":[...]}}` 经 fastjson2 解析 userInputs=1/refInputs=1/maxLength=50，经 Jackson 解析 **0/0/null（静默丢字段）**；出参方向 P1：`user_inputs`→`userInputs`、`node_param_name`→`nodeParamName`。前端只读 snake_case（`NodePropertyInput.vue:10,97,98,117`、`utils/workflowUtil.js:103,223,454-456`），`workflowApi.js:45` 仅兼容**父键** `inputConfig`；`/save` 入参为 `@RequestBody AiWorkflowVo`（`AiWorkflowVo.java:96`→`AiWorkflowNodeVo.java:53 private AiWfNodeInputConfigVo inputConfig`）→ **AI 工作流设计器保存/回显会丢 inputConfig** | ❌ 不成立 | **J-4**：这 6 处必须 `@JSONField(name=...)` + Jackson 侧 `@JsonProperty`（或 `@JsonAlias`）双写，不能只加注释 |
| 7 | `AiWorkflowRuntimeVo.java:68` 补 `@JsonFormat` | 补双注解（1 处） | 原文 `@JSONField(format = "yyyy-MM-dd HH:mm:ss")`，字段 `LocalDateTime cTime`，类 `@Data`；**该动作冗余**：`ScmJsonMapperFactory.mvcMapper()` 已用 `withConfigOverride(LocalDateTime.class, forPattern("yyyy-MM-dd HH:mm:ss"))`，探针 P3 实测 `mvcMapper()` 输出 `{"cTime":"2026-10-29 12:30:45"}`，与 fastjson2 一致。真实变化是**键名 `CTime`→`cTime`**（fastjson2：`{"CTime":...}`），前端已兜底（`components/70_ai/components/workflow/components/WorkflowRuntimeList.vue:19` 用 `runtime.cTime || runtime.CTime`、`:960-968 getTimeField()` 同时试 camel/snake/pascal；`utils/workflowApi.js:29` 同样三态兜底） | ❌ 说法不成立（冗余） | **J-5**：改为「记录键名 CTime→cTime，前端已兜底」；若要零变化则同 J-4 双注解 |
| 8 | `yyyy-MM-dd HH:mm:ss` 统一配置位置 | 隐含 mixin/全局 | 位于 `scm-common/src/main/java/com/xinyirun/scm/common/utils/json/ScmJsonMapperFactory.java:43` 常量 `DATE_TIME_PATTERN`，在 `mvcMapper()` 用 `defaultDateFormat(new SimpleDateFormat(...))` + `withConfigOverride(LocalDateTime/LocalDate.class, ...)`；**未使用 mixin**，`:82-83` 注释写明「Jackson 3.1.7 实测类级 mixin 对 java.time 不生效」。全仓 `@JsonFormat(pattern="yyyy-MM-dd HH:mm:ss")` 实测 **10 处**，全部在 `scm-bean` 的 cargo_right_transfer 系列（`BPoCargoRightTransferEntity:49`、`BSoCargoRightTransferEntity:49`、po/so 两套 `...AttachVo:45,51`、po/so 两套 `...Vo:199,205`，均另带 `timezone="GMT+8"`），与本项无关 | ✅ 成立（有全局配置） | 文档应写明「全局已覆盖」的理由，避免实施者再到处补注解 |
| 9 | 6 个 node config（26 处 `name=`）「不动」 | 纯 DB 配置对象 | 26 处准确（Mcp 7/Http 7/Mail 5/OpenPage 3/Keyword 2/Faq 2）；探针 P5：`AiWorkflowNodeVo.nodeConfig` 类型是 **fastjson2 `JSONObject`**，Jackson 反序列化后键名 `model_name/top_n` **原样保留**，再序列化仍为 snake_case → 这 26 处确实不经 Jackson 改名 | ✅ 成立 | 但需登记「VO 内嵌 fastjson2 `JSONObject`」这一边界渗透（见 J-3） |
| 10 | `import com.alibaba.fastjson2` 文件数 | 255 | **255 成立，但口径必须写明**：`grep -rl --include=*.java '^import com\.alibaba\.fastjson2' . \| grep -v '/target/' \| wc -l` = **255**（与文档一致）；若去掉 `^import` 锚定则为 **260**，多出的 5 个文件**全部只有被注释掉的 import**：`BpmProcessWorkServiceImpl.java:5-6`、`BReturnRelationServiceImpl.java:4`、`DataChangeRecorderInnerInterceptor.java:3`、`ReflectionUtil.java:3`、`ScheduleCalcQtyConsumer.java:3`。分模块（锚定口径）：scm-core 86、scm-ai 55、scm-bean 45、scm-core-bpm 13、scm-excel 11、scm-mq-consumer 7、scm-framework 7、scm-core-whapp 5、scm-core-app 5、scm-controller 5、scm-controller-bpm 4、scm-common 4、scm-security 2、scm-core-clickhouse 2、scm-redis/scm-quartz/scm-mq/scm-core-mongodb 各 1，合计 255 | ✅ 成立（**我方初判 260 有误，已撤回**） | **J-1** 降级为「补口径说明」：注明 255 = 活跃 `^import` 行数，另有 5 处注释残留 |
| 11 | pom 声明 14 处 | 14 处（根 `pom.xml:234-236` + 13 处模块声明） | **14 处准确**（`grep -rn 'fastjson2' --include=pom.xml`）：`pom.xml:235`、`scm-ai:115`、`scm-bean:45`、`scm-common:68`、`scm-core-bpm:172`、`scm-excel:76`、`scm-framework:140` 与 `:144`、`scm-mq:30`、`scm-mq-consumer:30`、`scm-report:68`、`scm-start:86`、`scm-start-parent/scm-bpm-start:79`、`scm-start-parent/scm-main-start:85`（artifactId 全为 `fastjson2-extension-spring6`；根 pom 为 depMgmt 234-236，模块 13 处分布在 12 个 pom 文件，scm-framework 两处） | ✅ 成立 | 附带事实：`scm-report`/`scm-start`/`scm-main-start`/`scm-bpm-start` 已声明但**零 import**（死声明），可在加注释时一并记录 |
| 12 | fastjson2 在 HTTP 出入口已归零 | §2.9「不改业务代码」隐含已归零 | **未归零**：181 个 `*Controller.java` 中 **13 个**仍含 fastjson2 —— `scm-ai/.../chat/AiConversationController.java`、`scm-ai/.../workflow/WorkflowController.java`、`scm-controller-bpm/.../antd/AntJsonToBpmnController.java`、`.../business/BpmProcessTemplatesController.java`、`.../react/ReactJsonToBpmnController.java`、`.../vue3/JsonToBpmnController.java`、`scm-controller/.../po/ap/BApController.java`、`.../po/poorder/BPoOrderController.java`、`.../so/ar/BArController.java`、`.../so/soorder/BSoOrderController.java`、`.../master/menu/SMenuSearchController.java`、`scm-framework/.../app/v1/AppBaseController.java`、`.../system/v1/SystemBaseController.java`。**真实违反**：`WorkflowController.java:184 run(..., @RequestBody List<JSONObject> inputs, ...)`、`AntJsonToBpmnController.java:38`、`ReactJsonToBpmnController.java:44`、`JsonToBpmnController.java:41` 共 **4 处 `@RequestBody` fastjson2 类型**；`SMenuSearchController.java:39,69` 用 `JSON.toJSON(...)` 决定出参结构。合法（白名单内）：`AppBaseController:106,234`、`SystemBaseController:125,266` | ❌ 不成立 | **J-3**：白名单须显式登记这 13 个文件（"存量豁免清单"），否则「禁止 `@RequestBody` 入参反序列化」的规则现状即违规 |
| 13 | 巡检脚本 `check-fastjson-boundary.ps1` 可行性 | 新增；`@RestController` 所在文件出现 `import com.alibaba.fastjson2` 且「用于返回值序列化」则告警 | `00_doc/tools/` 目录**当前不存在**（`ls` → No such file or directory），脚本本身**未能证实**。规则可行性：①「用于返回值序列化」不是 import 级可判定事实 → 纯 grep 无法落地，只能退化为「文件含 import 即告警」；②误报：13 个命中里 2 个是白名单合法用途（AppBaseController/SystemBaseController），另有 `BAp/BPoOrder/BAr/BSoOrder` 4 处是「入站 JSON 文本二次解析」合法用途 → 6/13 误报；③漏报：`*Api/*Endpoint/*Rest` 等非 Controller 命名、`@ControllerAdvice`/`ResponseBodyAdvice`/message converter、service/工具类间接使用、**全限定名 `com.alibaba.fastjson2.JSON.xxx()` 无 import**、**VO 字段类型为 fastjson2 类型**（`AiWorkflowNodeVo.java:59 private JSONObject nodeConfig`）、xml/yml 配置 | ⚠️ 部分可行（规则需重写） | **J-7**：改为可静态判定口径 + 显式白名单豁免表，并补漏报面；先建 `00_doc/tools/` 目录 |
| 14 | §6 白名单：DB JSON 文本（scm-ai 工作流节点配置、scm-excel 模板） | 允许 | 成立：`AiWorkflowNodeVo.nodeConfig` 为 fastjson2 `JSONObject`（P5 验证键名保留）、TypeHandler 见第 17 行；scm-excel 侧 JSON 模板解析路径在 `AppBaseController:106`/`SystemBaseController:125`（`JSON.parseObject(jsonConfig, ExcelTemplate.class)`） | ✅ 成立 | — |
| 15 | §6 白名单：外部响应解析（`UploadFileResultAo`） | 允许 | 定性正确（`AppBaseController:234`、`SystemBaseController:266` 用 fastjson2 解析文件中台响应体），**但不完整**：该类 `@Data extends BaseVo`，字段为 `fileUuid` 而注解 `name="fileId"`，且是 **7 个 HTTP 响应 VO 的父类**（`BCarriageOrderDetailVo`、`BMonitorSumVo`、`BMonitorVo`、`BReturnRelationVo`、`BReturnRelationBackupV2Vo`、`SDictTypeVo`、`SRoleVo`），其中 `SRoleVo`/`BPoContractImportVo` 等被 `super.uploadFile(...)` 作为**出参**返回。迁移后这些 VO 的 JSON 键 `fileId`→`fileUuid`（前端全仓 `.fileId` 仅 1 处、且是对外部 FS 服务 `/api/service/v1/upload` 的响应，与本类无关；`fileUuid` 前端零命中）→ 影响可接受但**必须记录** | ⚠️ 部分成立 | **J-3**：白名单该行补「该类亦为 7 个响应 VO 父类，键名 fileId→fileUuid 已知变化，无前端消费」 |
| 16 | §6 白名单：MQ 存量消息兼容解析 | 允许（仅解析） | 只覆盖了一半：生产端 `ScmMqProducer.java:3-4` import fastjson2，`:64` `String messageDataJson = JSON.toJSONString(mqSenderAo, JSONWriter.Feature.LargeObject)`（另一处 `:125` 无 Feature），并在 `:75` / `:136` `redisUtil.putToMap(...MQ_SEND_PREFIX..., messageDataJson)` 写入 Redis 快照；业务侧生产链实测 **非注释 `.send(` 20 处 / 17 文件**（`grep -rn '\.send(' scm-mq/src/main/java scm-mq-consumer/src/main/java`，剔除注释行：SFileBackupProducer:32、RecreateDailyInventoryQueueMqProducter:33、LogAiChatProducer:68、LogDataChangeProducer:34/46/58、LogQuartzProducer:27、LogPcSystemProducer:34、MonitorBackupProducer:27、MonitorRenewProducer:27、MonitorBackupV2Producer:27、MonitorRenewV2Producer:27、ScheduleCalcProducer:26、SyncDataErrorMsgQueueMqProducter:38/45/52/59/66、SyncPush2BusinessPlatformAllInOneMqProducter:76、TestMqProducter:36〔测试类〕） | ❌ 不完整 | **J-3**：白名单补「MQ **生产端**序列化（ScmMqProducer，20 处 `.send` / 17 文件）」 |
| 17 | §6 白名单：MyBatis TypeHandler 32 个 | 32 个 | **33 个**（`grep -rl 'extends BaseTypeHandler\|implements TypeHandler'`，排除 target）：scm-core 26、scm-ai 2（`FastjsonTypeHandler`、`FastjsonInputConfigTypeHandler`）、scm-core-app 2、scm-core-whapp 2、scm-core-bpm 1；**33 个全部** `import com.alibaba.fastjson2.*`（v2 包名），**无一改用 Jackson**。注意存在**同名类各 2 份**：`JsonObjectTypeHandler`（scm-core-app / scm-core-bpm）、`ContactListVoTypeHandler`（scm-core-app / scm-core-whapp）——文档「32」有可能是按类名去重或漏计其中之一 | ❌ 差 1 | **J-2**：32→33 并写明口径（按文件计 33；同名类去重则 31） |
| 18 | §6 禁止项：Redis 序列化（已改 Jackson 3） | 已改 | 成立：`scm-redis/.../RedisCacheAutoConfiguration.java:100` `new JacksonJsonRedisSerializer<>(om, Object.class)`，`:117/122/126` fastjson2 序列化器注册代码已注释；`FastJsonRedisSerializer.java` 类体仍存在但**无任何引用**（仅自身 + 注释） | ✅ 成立 | 附注：MQ 生产端仍把 fastjson2 生成的 JSON 串写入 Redis（见第 16 行），需在白名单注明「Redis 内的消息快照文本」 |
| 19 | 「不改业务代码」前提与现状一致性 | 只加注释 | `CallbackMappingJackson2HttpMessageConverter` 已 `extends JacksonJsonHttpMessageConverter`（javadoc:27 记「原先继承 fastjson2 FastJsonHttpMessageConverter」），`ActionInterceptorConfig.java:97,102,131` fastjson2 转换器注册已注释 → **HTTP 出口主体已换 Jackson**；但入站仍有 4 处 `@RequestBody` fastjson2 类型（见第 12 行），且 VO 内嵌 fastjson2 `JSONObject` | ⚠️ 前提有例外 | **J-3** 同上（登记豁免），并在 §2.9 注明「本项只做登记，不消除存量」 |

---

## @JSONField 全量清单

路径缩写（`...` 代表各模块 `src/main/java/com/xinyirun/scm/`）：
- `ai=` `scm-ai/.../ai/bean/vo/workflow/`
- `nd=` `scm-ai/.../ai/workflow/node/`（下分 `faqextractor/`、`httprequest/`、`keywordextractor/`、`mailsend/`、`mcptool/`、`openpage/`）
- `xl=` `scm-excel/.../excel/bean/importconfig/template/`（下分 `data/`、`data/row/`、`title/`）
- `bn=` `scm-bean/.../bean/system/ao/fs/`

| 文件:行 | 原文 | 类别 | 处置 | 依据 |
|---|---|---|---|---|
| `xl/ExcelTemplate.java:34` | `@JSONField(serialize = false)` | 带参 | 保留 | 字段 `private boolean isInit`（仅 `@Getter`），需对 fastjson2 屏蔽 |
| `xl/ExcelTemplate.java:40` | `@JSONField` | 裸 | 可删 | 字段 `dataRows` 有 `@Setter/@Getter`；P2 证明裸注解对 fastjson2 no-op |
| `xl/ExcelTemplate.java:45` | `@JSONField` | 裸 | 可删 | 字段 `name` 同上 |
| `xl/ExcelTemplate.java:62` | `@JSONField(serialize = false)` | 带参 | 保留 | 标在**计算方法 getter** `getDataRowIndex()` 上 |
| `xl/ExcelTemplate.java:72` | `@JSONField(serialize = false)` | 带参 | 保留 | 计算方法 getter `getColSize()` |
| `xl/ExcelTemplate.java:82` | `@JSONField(serialize = false)` | 带参 | 保留 | 计算方法 getter `getDataCols()` |
| `xl/data/DataCol.java:31` | `@JSONField` | 裸 | 可删 | 类 `@Data`；字段 `name` |
| `xl/data/DataCol.java:36` | `@JSONField` | 裸 | 可删 | 字段 `index` |
| `xl/data/DataCol.java:42` | `@JSONField` | 裸 | 可删 | 字段 `convertor` |
| `xl/data/DataCol.java:45` | `@JSONField` | 裸 | 可删 | 字段 `listValidators` |
| `xl/data/DataCol.java:51` | `@JSONField(serialize = false)` | 带参 | 保留 | 字段 `validators` 需屏蔽 |
| `xl/data/DataRow.java:34` | `@JSONField` | 裸 | 可删 | 有 `@Getter/@Setter`；字段 `dataCols` |
| `xl/data/DataRow.java:42` | `@JSONField` | 裸 | 可删 | 字段 `dataRowValidators` |
| `xl/data/DataRow.java:48` | `@JSONField(serialize = false)` | 带参 | 保留 | 字段 `validators` |
| `xl/data/row/DataRowValidators.java:23` | `@JSONField` | 裸 | 可删 | 类 `@Data`；字段 `listValidators`；删后需删 `:3` import（J-6） |
| `xl/title/TitleCol.java:17` | `@JSONField` | 裸 | 可删 | 有 `@Getter/@Setter`；字段 `title`；删后需删 `:3` import（J-6） |
| `xl/title/TitleCol.java:23` | `@JSONField` | 裸 | 可删 | 字段 `colSpan`（TAB 缩进行） |
| `xl/title/TitleRow.java:21` | `@JSONField` | 裸 | 可删 | 有 `@Getter/@Setter`；字段 `cols`（TAB 缩进行）；删后需删 `:3` import（J-6） |
| `bn/UploadFileResultAo.java:26` | `@JSONField(name = "fileId")` | 带参 | **保留 + 加 Jackson 注解** | fastjson2 解析文件中台响应依赖它（`SystemBaseController:266`）；同时是 7 个响应 VO 父类，Jackson 侧键名已变 `fileUuid` → 需 `@JsonProperty("fileId")` 或 `@JsonAlias` 双写（J-4） |
| `ai/AiWfNodeInputConfigVo.java:26` | `@JSONField(name = "user_inputs")` | 带参 | **保留 + 加 Jackson 注解** | 字段 `userInputs`；HTTP 入/出参均经 Jackson（P1/P4 实证键名与绑定变化，前端只读 `user_inputs`） |
| `ai/AiWfNodeInputConfigVo.java:33` | `@JSONField(name = "ref_inputs")` | 带参 | **保留 + 加 Jackson 注解** | 字段 `refInputs`；同上 |
| `ai/AiWfNodeParamRefVo.java:29` | `@JSONField(name = "node_uuid")` | 带参 | **保留 + 加 Jackson 注解** | 字段 `nodeUuid`；同上 |
| `ai/AiWfNodeParamRefVo.java:36` | `@JSONField(name = "node_param_name")` | 带参 | **保留 + 加 Jackson 注解** | 字段 `nodeParamName`；同上 |
| `ai/AiWfNodeIOVo.java:57` | `@JSONField(name = "max_length")` | 带参 | **保留 + 加 Jackson 注解** | 字段 `maxLength`；同上 |
| `ai/AiWorkflowRuntimeVo.java:68` | `@JSONField(format = "yyyy-MM-dd HH:mm:ss")` | 带参 | 保留（**不必**补 `@JsonFormat`） | 全局 `ScmJsonMapperFactory:43` + `withConfigOverride(LocalDateTime)` 已保证格式（P3 实测）；实际变化仅键名 `CTime`→`cTime` |
| `nd/faqextractor/FaqExtractorNodeConfig.java:18` | `@JSONField(name = "top_n")` | 带参 | 保留不动 | DB JSON 配置对象，经 fastjson2 `JSONObject`/`checkAndGetConfig` 转换（P5：键名保留）；字段 `Integer topN` |
| `nd/faqextractor/FaqExtractorNodeConfig.java:24` | `@JSONField(name = "model_name")` | 带参 | 保留不动 | 同上；字段 `String modelName` |
| `nd/keywordextractor/KeywordExtractorNodeConfig.java:18` | `@JSONField(name = "top_n")` | 带参 | 保留不动 | 同上；`Integer topN` |
| `nd/keywordextractor/KeywordExtractorNodeConfig.java:24` | `@JSONField(name = "model_name")` | 带参 | 保留不动 | 同上；`String modelName` |
| `nd/httprequest/HttpRequestNodeConfig.java:48` | `@JSONField(name = "content_type")` | 带参 | 保留不动 | 同上；`String contentType` |
| `nd/httprequest/HttpRequestNodeConfig.java:54` | `@JSONField(name = "text_body")` | 带参 | 保留不动 | 同上；`String textBody` |
| `nd/httprequest/HttpRequestNodeConfig.java:60` | `@JSONField(name = "form_data_body")` | 带参 | 保留不动 | 同上；`List<Param> formDataBody` |
| `nd/httprequest/HttpRequestNodeConfig.java:66` | `@JSONField(name = "form_urlencoded_body")` | 带参 | 保留不动 | 同上；`List<Param> formUrlencodedBody` |
| `nd/httprequest/HttpRequestNodeConfig.java:72` | `@JSONField(name = "json_body")` | 带参 | 保留不动 | 同上；`JSONObject jsonBody`（fastjson2 类型） |
| `nd/httprequest/HttpRequestNodeConfig.java:85` | `@JSONField(name = "retry_times")` | 带参 | 保留不动 | 同上；`Integer retryTimes` |
| `nd/httprequest/HttpRequestNodeConfig.java:91` | `@JSONField(name = "clear_html")` | 带参 | 保留不动 | 同上；`Boolean clearHtml` |
| `nd/mailsend/MailSendNodeConfig.java:23` | `@JSONField(name = "config_code")` | 带参 | 保留不动 | 同上；`String configCode` |
| `nd/mailsend/MailSendNodeConfig.java:30` | `@JSONField(name = "to_mails")` | 带参 | 保留不动 | 同上；`String toMails` |
| `nd/mailsend/MailSendNodeConfig.java:36` | `@JSONField(name = "cc_mails")` | 带参 | 保留不动 | 同上；`String ccMails` |
| `nd/mailsend/MailSendNodeConfig.java:43` | `@JSONField(name = "bcc_mails")` | 带参 | 保留不动 | 同上；`String bccMails` |
| `nd/mailsend/MailSendNodeConfig.java:63` | `@JSONField(name = "single_send")` | 带参 | 保留不动 | 同上；`Boolean singleSend = true` |
| `nd/mcptool/McpToolNodeConfig.java:38` | `@JSONField(name = "tool_input")` | 带参 | 保留不动 | 同上；`String toolInput` |
| `nd/mcptool/McpToolNodeConfig.java:46` | `@JSONField(name = "model_name")` | 带参 | 保留不动 | 同上；`String modelName` |
| `nd/mcptool/McpToolNodeConfig.java:54` | `@JSONField(name = "show_process_output")` | 带参 | 保留不动 | 同上；`Boolean showProcessOutput = true` |
| `nd/mcptool/McpToolNodeConfig.java:62` | `@JSONField(name = "tool_names")` | 带参 | 保留不动 | 同上；`List<String> toolNames` |
| `nd/mcptool/McpToolNodeConfig.java:68` | `@JSONField(name = "shared_output")` | 带参 | 保留不动 | 同上；`Boolean sharedOutput = false` |
| `nd/mcptool/McpToolNodeConfig.java:76` | `@JSONField(name = "direct_call")` | 带参 | 保留不动 | 同上；`Boolean directCall = false` |
| `nd/mcptool/McpToolNodeConfig.java:86` | `@JSONField(name = "direct_params")` | 带参 | 保留不动 | 同上；`Map<String,Object> directParams` |
| `nd/openpage/OpenPageNodeConfig.java:29` | `@JSONField(name = "open_mode")` | 带参 | 保留不动 | 同上；`String openMode` |
| `nd/openpage/OpenPageNodeConfig.java:38` | `@JSONField(name = "page_mode")` | 带参 | 保留不动 | 同上；`String pageMode` |
| `nd/openpage/OpenPageNodeConfig.java:47` | `@JSONField(name = "show_process_output")` | 带参 | 保留不动 | 同上；`Boolean showProcessOutput = true` |

合计核对：12 裸 + 6 `serialize=false` + 6 `name=` + 1 `format=` + 26 node-config `name=` = **51**。全部行号与原文由
`grep -rn --include=*.java -A1 '^[[:space:]]*@JSONField' . | grep -v '/target/'` 逐行核对，可复现。

---

## 复现命令与输出摘要

```bash
cd "D:/2026_project/00_scm/backend/scm_backend"

# 1) 总数 / 文件数
grep -rn --include=*.java '^[[:space:]]*@JSONField' . | grep -v '/target/' | wc -l          # 51
grep -rn --include=*.java '^[[:space:]]*@JSONField' . | grep -v '/target/' \
  | cut -d: -f1 | sort | uniq -c | sort -rn                                                  # 17 文件
# 2) 全部原文
grep -rn --include=*.java '^[[:space:]]*@JSONField' . | grep -v '/target/'
# 3) 分类
grep -rn --include=*.java '^[[:space:]]*@JSONField' . | grep -v '/target/' | grep -c 'serialize *= *false'   # 6
grep -rn --include=*.java '^[[:space:]]*@JSONField' . | grep -v '/target/' | grep -cE '^\S+:[0-9]+:\s*@JSONField\s*$'  # 12（裸）
# 4) fastjson2 import 文件数（必须带 ^import 锚定）
grep -rl --include=*.java '^import com\.alibaba\.fastjson2' . | grep -v '/target/' | wc -l   # 255
grep -rl --include=*.java 'import com\.alibaba\.fastjson2' . | grep -v '/target/' | wc -l    # 260（含 5 个仅注释行）
# 5) pom 声明
grep -rn 'fastjson2' --include=pom.xml . | grep -v '/target/' | grep artifactId            # 14
# 6) Controller 层是否归零
grep -rl --include=*Controller.java 'com\.alibaba\.fastjson2' . | grep -v '/target/' | wc -l # 13（总 Controller 181）
# 7) TypeHandler
grep -rl --include=*.java 'extends BaseTypeHandler\|implements TypeHandler' . | grep -v '/target/' | wc -l  # 33
# 8) MQ 生产端
grep -n 'fastjson2\|JSON\.toJSONString' scm-mq/src/main/java/com/xinyirun/scm/mq/rabbitmq/producer/ScmMqProducer.java
# 9) Redis 序列化
grep -rn 'RedisSerializer' scm-redis/src/main/java --include=*.java
# 10) 日期统一配置
grep -n 'DATE_TIME_PATTERN\|withConfigOverride\|defaultDateFormat' \
  scm-common/src/main/java/com/xinyirun/scm/common/utils/json/ScmJsonMapperFactory.java
# 11) 巡检脚本目录
ls 00_doc/tools/            # 不存在
```

编译探针（`/tmp/jp`，JDK 21 + fastjson2 2.0.66 + Jackson 3.1.7，classpath 含 `scm-ai/target/classes:scm-common/target/classes`）实测输出：

```
P2 裸注解等价性  : withAnnotation={"index":0}  without={"index":0}；反序列化均 name=x,index=7
P1 序列化        : fastjson2 {"ref_inputs":[{...,"node_param_name":"pname","node_uuid":"uuid-1"}],"user_inputs":[{"max_length":100}]}
                   Jackson3  {"refInputs":[{...,"nodeParamName":"pname","nodeUuid":"uuid-1"}],"userInputs":[{"maxLength":100,...}]}
P3 RuntimeVo     : fastjson2 {"CTime":"2026-10-29 12:30:45"} / Jackson默认 {"cTime":"2026-10-29T12:30:45"} / mvcMapper {"cTime":"2026-10-29 12:30:45"}
P4 反序列化      : fastjson2 userInputs=1 refInputs=1 maxLength=50；Jackson3 userInputs=0 refInputs=0 maxLength=null
P5 JSONObject字段: jackson->JSONObject keys=[model_name, top_n]，回写 {"model_name":"m1","top_n":5}；整 VO 入站键名保留
```

---

## verdict

**`needs_revision`**

文档在「数量盘点」（51/17、12 裸/6 文件、6 处 `serialize=false`、6 处 `name=`、14 处 pom 声明、import 255 文件）上**高度准确，可复现**；问题集中在**对迁移后果的判断**（第 6、7 行：只加注释护栏不足、`@JsonFormat` 属冗余）与**白名单完整性**（第 12、16、17 行：MQ 生产端、Controller 层存量、TypeHandler 32→33）。

### must-fix

| 编号 | 级别 | 必须修正 |
|---|---|---|
| **J-1** | 低（**降级并更正**） | ~~255→260~~ **撤回本方初判**：`grep -rl '^import com\.alibaba\.fastjson2'` 实测 **255**，与文档一致；260 是去掉 `^import` 锚定后的数字，多出的 5 个文件仅有**注释掉的 import**（`BpmProcessWorkServiceImpl.java:5-6`、`BReturnRelationServiceImpl.java:4`、`DataChangeRecorderInnerInterceptor.java:3`、`ReflectionUtil.java:3`、`ScheduleCalcQtyConsumer.java:3`）。仅建议文档补一句口径说明，避免后续复核再次误判（`00_doc/20-...md:42` 的「255→260」写法建议同步纠正） |
| **J-2** | 中 | §6/§2.8「TypeHandler 32 个」→ **33 个**；并注明「33 个全部用 `com.alibaba.fastjson2.*`（v2），JSON 列转换未迁 Jackson」，以及同名类重复（`JsonObjectTypeHandler`×2、`ContactListVoTypeHandler`×2）导致的口径差异 |
| **J-3** | **高** | §6 白名单缺 3 类现存用途，必须补齐，否则「禁止 `@RequestBody` 入参反序列化 / 禁止新增 HTTP 出口」的规则**在现状下即违规**：<br>① MQ **生产端**序列化（`ScmMqProducer.java:64,125` `JSON.toJSONString`，`:75,:136` 写 Redis 快照；生产链 20 处非注释 `.send` / 17 文件）；<br>② Controller 层存量 fastjson2 **13 个文件**，其中 4 处 `@RequestBody` fastjson2 类型（`WorkflowController:184`、`AntJsonToBpmnController:38`、`ReactJsonToBpmnController:44`、`JsonToBpmnController:41`）、2 处 `JSON.toJSON` 决定出参（`SMenuSearchController:39,69`）——需以「存量豁免清单」形式登记并注明遗留；<br>③ VO 内嵌 fastjson2 类型（`AiWorkflowNodeVo:59 private JSONObject nodeConfig`）；<br>④ `UploadFileResultAo` 行补充「亦为 7 个响应 VO 父类，键名 fileId→fileUuid 已知变化」 |
| **J-4** | **高** | §2.8「6 处加注释护栏」不足，改为**双注解**：`UploadFileResultAo:26`、`AiWfNodeInputConfigVo:26,33`、`AiWfNodeParamRefVo:29,36`、`AiWfNodeIOVo:57` 一律保留 `@JSONField(name=...)` 并新增 Jackson 侧 `@JsonProperty("<同名>")`（或 `@JsonAlias`，Jackson 3 包名 `tools.jackson.annotation.*`）。依据：P4 实证 Jackson 入站 `user_inputs/ref_inputs` 绑定为 **0/0**（静默丢）、P1 出站改名为 camelCase；前端只读 snake_case（`NodePropertyInput.vue:10,97,98,117` 等），`/save` 走 `@RequestBody AiWorkflowVo` → **AI 工作流设计器保存/回显断链**。若只加注释，等于把已知功能回归留到线上 |
| **J-5** | 中 | §2.8「`AiWorkflowRuntimeVo:68` 补 `@JsonFormat`」**删除该动作**：全局 `ScmJsonMapperFactory:43` + `withConfigOverride(LocalDateTime)` 已保证 `yyyy-MM-dd HH:mm:ss`（P3 实证 mvcMapper 输出与 fastjson2 一致）。改写为：「真实变化为键名 `CTime`→`cTime`，前端已兜底（`WorkflowRuntimeList.vue:19`、`:960-968`；`workflowApi.js:29`），无需改动」。若要键名零变化，则按 J-4 双注解处理 |
| **J-6** | 低 | 删除 12 处裸注解时**同步删除** 3 个文件的 `import com.alibaba.fastjson2.annotation.JSONField;`：`DataRowValidators.java:3`、`TitleCol.java:3`、`TitleRow.java:3`（另 3 个文件因保留 `serialize=false` 需留 import） |
| **J-7** | 中 | §2.9 巡检脚本规则不可按原文落地（「用于返回值序列化」非静态可判定）。改为可判定口径：(a) 「含 `@RestController`/`*Controller.java` 的文件出现 `import com.alibaba.fastjson2` **或** `com.alibaba.fastjson2.` 全限定名」即告警；(b) 维护显式白名单豁免表（AppBaseController、SystemBaseController、BAp/BPoOrder/BAr/BSoOrder 4 处 JSON 文本二次解析），豁免项带 TODO 编号；(c) 增加第二类检查：「非 Controller 但含 fastjson2」仅统计不告警，防止误报洪水。并注明 `00_doc/tools/` 目录需新建（现不存在） |
| **J-8** | 低 | §2.8 表格补全行号：`DataCol.java:31,36,42,45`、`DataRow.java:34,42`、`TitleCol.java:17,23`、`TitleRow.java:21`（现仅写「4 处裸」等，无法直接执行） |

### 已证实无误、无需修改的部分

- 51 处 / 17 文件、12 裸 / 6 文件、6 处 `serialize=false`、6 处 `name=`、26 处 node-config、14 处 pom 声明（1 根 + 13 模块）、`import` 文件 **255**——**全部与实测一致**。
- 12 处裸注解删除**安全**（P2 no-op + 12/12 有 Lombok 访问器）。
- 6 处 `serialize=false` 保留判断**正确**（这些类不走 Jackson）。
- 26 处 node-config `@JSONField` 不动**正确**（P5：经 Jackson 的 `JSONObject` 通道键名原样保留）。
- §6「Redis 序列化已改 Jackson 3」**正确**（`JacksonJsonRedisSerializer`，fastjson2 序列化器仅剩注释）。
