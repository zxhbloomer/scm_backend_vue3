# 25 - 代码评审：JSON 出口 null 兼容（r4，2026-10-29）

评审对象：`NullCompatSerializerModifier.java`（新增）、`ScmJsonMapperFactory.java`（改）、`CallbackMappingJackson2HttpMessageConverter.java`（改）。
依据：`00_doc/20-...md` §2.3 / §2.4 / §9 / §12。探针均在 `D:\temp\json-null-probe\`（`Verify.java`、`ReviewProbe.java`、`ProbeDate.java`、`ProbeDateFJ.java`）。

| 编号 | 评审点 | 证据（命令 + 输出原文） | 结论 | 建议改法（file:line） |
|---|---|---|---|---|
| 1 | 三档 null 行为是否与 fastjson2 基准一致（§2.3 表 / §12 表） | `javac/java Verify`（classpath=scm-common/target/classes+jackson 3.1.7）<br>`NORMAL : {"b":false,"l":null,"le":[null,true],"m":null,"mv":{"a":null,"b":true},"n":null,"o":null,"s":null}`<br>`STRING_EMPTY : {"b":false,"le":[null,true],"mv":{"b":true},"s":""}`<br>`NULL_NOT_OUT : {"le":[null,true],"mv":{"b":true}}`<br>`legacy(NONE) : {"b":null,...,"s":null}` | 通过 | — |
| 2 | **LocalDateTime 出站格式仍为 ISO-T，未达 `yyyy-MM-dd HH:mm:ss`** | `ProbeDate`：`A factory : {"dt":"2026-10-29T13:05:07"}`；`B builder+mixin : {"dt":"2026-10-29T13:05:07"}`；`C configOverride : {"dt":"2026-10-29 13:05:07"}`；`D field-level : {"dt":"2026-10-29 13:05:07"}`。fastjson2 基准 `ProbeDateFJ`：`NORMAL : {"d":"2026-10-29","dt":"2026-10-29 13:05:07",...}` | **需修（阻断）** | Jackson 3.1.7 不认「类级 `@JsonFormat` mixin」对 java.time 类型，mixin 是死代码（`ScmJsonMapperFactory.java:81-82,134-142`）。改 `builder.withConfigOverride(LocalDateTime.class, o -> o.setFormat(JsonFormat.Value.forPattern(DATE_TIME_PATTERN)))`（探针 C 已验证有效），删除两个 Mixin 类；同步修正 `:29-34,140` javadoc 与 `CallbackMappingJackson2HttpMessageConverter.java:31` 的日期声明 |
| 3 | 线程安全 / 跨 mapper 污染（`assignNullSerializer` 是否会串档） | `ReviewProbe` 同一 bean 类先跑 NORMAL 再跑 legacy：`[1] legacy ... {"b":null,...,"s":null}`、`[6] legacy AFTER NORMAL same class: {"b":null,...,"s":null}` | 通过 | 各 mapper 独立 `JsonMapper`+独立序列化缓存，`BeanPropertyWriter` 实例不跨 mapper 共享；`NULL_AS_FALSE/EMPTY` 为无状态静态单例，可安全复用 |
| 4 | `mvcMapper()`（RestTemplate:58 / MQ:38 / AI MQ:82）行为是否未变 | `ReviewProbe`：`[1] legacy` 与 `[10] legacy Map content : {"lm":[{"a":null,"b":2}],"m":{"a":null,"b":1},"mAlways":{"a":null},"mAnnot":{"a":null}}`、`[14] legacy root Map : {"a":null,"b":1}`；代码 `ScmJsonMapperFactory.java:54-65` 委托 `NullCompat.NONE` | 通过 | — |
| 5 | `MapContentNonNullIntrospector` 覆盖显式 `content` 注解 | `ReviewProbe`：`[8] STRING_EMPTY : {"lm":[{"b":2}],"m":{"b":1},"mAlways":{},"mAnnot":{}}` —— `mAnnot` 被标注 `@JsonInclude(value=ALWAYS, content=ALWAYS)` 仍被强制 `NON_NULL` | 需修（低） | `ScmJsonMapperFactory.java:127-130` 仅在 `inclusion.getContentInclusion()==JsonInclude.Include.USE_DEFAULTS` 时才 `withContentInclusion(NON_NULL)`，尊重业务显式声明 |
| 6 | `writeInternal` 忽略构造期注入的 mapper（RestTemplate 场景） | `RestTemplateConfiguration.java:58` 传 `ScmJsonMapperFactory.mvcMapper()`(NONE)，但 `CallbackMappingJackson2HttpMessageConverter.java:117-121` 命中 1/2 档时改用静态 MVC mapper；同档还绕过父类 `selectMapper/customizeWriter/writePrefix`（`javap AbstractJacksonHttpMessageConverter` 证实这些受保护钩子存在；全仓 `setPrefixJson|setJsonPrefix` 0 处使用） | 需修（低） | `:54-66` 三档改为实例字段（默认构造注入静态三档，二参构造保留调用方 mapper 并对 NONE 档不做分档），或在 javadoc 显式声明「RestTemplate 出站不参与 1/2 档分档」 |
| 7 | `type` null 兜底 + `getJavaType(declaredType, object.getClass())` 安全性 | `javap -p` 输出 `protected tools.jackson.databind.JavaType getJavaType(java.lang.reflect.Type, java.lang.Class<?>)`；代码 `:128-129` 已对 `type==null` / `type.getType()==null` 双重兜底；`JsonResultTypeConstants` 为 `int` 常量，`Integer == int` 走拆箱按值比较（`JsonResultTypeConstants.java:12-18`） | 通过 | — |
| 8 | 替换默认 introspector 是否丢失注解能力（`@JsonFormat` mixin 除外） | `ReviewProbe` NORMAL/STRING_EMPTY/NULL_NOT_OUT 三档下：字段级 `@JsonFormat("yyyy/MM/dd")`→`"2026/10/29"`、`@JsonProperty("renamed")`→`"renamed"`、`@JsonIgnore` 字段不出现、`@JsonInclude(ALWAYS)` 空串档输出 `"keepNull":""`、`java.util.Date`→`"2025-10-29 13:05:07"` | 通过 | 继承 `JacksonAnnotationIntrospector` 未丢能力；日期问题见 #2（与 introspector 无关，legacy 档同样复现） |
| 9 | 越界检查（只允许 3 个文件） | `git status --short \| wc -l` → `612`（基线 601）；`find . -newermt '2026-10-29 10:29'` → 仅 `00_doc/20..24*.md` + 上述 3 个 java 文件 | 通过 | 仅 20/21/22/23/24 号 md 与 3 个目标文件；无其它代码/配置越界 |
| 10 | 代码规范（4 空格、无 tab、UTF-8、命名、无用 import） | `file` → 三份均 `UTF-8 text`；`grep -cP '\t'` → `0`；非 4 倍数缩进仅 javadoc/续行对齐（33/53 空格）；逐行核对 11+14+17 个 import 全部被使用 | 通过（提示） | 建议 `ScmJsonMapperFactory.java:3-24` 的 import 分组把 `java.*` 前置，与仓库其余文件一致 |
| 11 | 编译 | `mvn -o -pl scm-common,scm-framework -am compile -DskipTests` → `scm-common SUCCESS / scm-framework SUCCESS / BUILD SUCCESS / Total time: 54.844 s` | 通过 | — |

**未覆盖项**：接口级 JSON diff（§4 七条）需启动应用取 token，本轮未执行；`STRING_EMPTY` 档 `List<String>`/`String[]` 元素差异沿用 §2.3 已知边界记录，未复验。

## verdict
needs_revision

阻断项仅 1 条：`ScmJsonMapperFactory` 的 LocalDateTime/LocalDate Mixin 在 Jackson 3.1.7 下不生效，实测出站仍为 `2026-10-29T13:05:07`，与升级前 fastjson2 的 `2026-10-29 13:05:07` 不一致（探针 C 已证 `withConfigOverride` 可修）。#5、#6 为低风险改进项，可同批处理。
