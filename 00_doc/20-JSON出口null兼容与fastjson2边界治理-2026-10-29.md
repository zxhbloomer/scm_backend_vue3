# 20 - JSON 出口 null 兼容 与 fastjson2/@JSONField 边界治理

- 文档编号：20
- 创建日期：2026-10-29
- 状态：**方案待审核（代码 0 改动）**
- 关联：`17-代码层剩余项审计与收尾清单`、`05-代码变更登记册`（F-3 已知差异 F3-01）、`04-升级实施总清单`
- 取证方式：源码 grep + 打包 jar 解压（`scm-start/target/scm-start-0.0.1-SNAPSHOT.jar`）+ Jackson 3.1.7 class 常量池读取（`D:\2025_project\99_mvnrepository\tools\jackson\core\jackson-databind\3.1.7`）

---

## 0. 需求（用户 2026-10-29 提出）

| 编号 | 需求 |
|---|---|
| R-1 | fastjson2 三个 jar 仍在打包产物里，怎么处理 |
| R-2 | `@JSONField` 57 处 / 11 个 bean 文件怎么办 |
| R-3 | 「必做项」（null 兜底）具体怎么写，给出代码 |
| R-4 | 「建议项」（循环引用护栏）具体怎么写，给出代码 |
| R-5 | 不修改代码，只出方案；文档记录需求 / 方案 / 执行 / 进度 |

---

## 1. 问题清单（全部实测）

| 编号 | 问题 | 证据 |
|---|---|---|
| P-JSON-1 | Jackson 3 无 `WriteNullBooleanAsFalse` / `WriteNullStringAsEmpty` 等价开关，`STRING_EMPTY_BOOLEAN_FALSE` 退化为「原样输出 null」 | 转换器 `CallbackMappingJackson2HttpMessageConverter.java:42-45` 自述；`:100-112` 只特判 `NULL_NOT_OUT` |
| P-JSON-2 | 受影响接口 2 个 | `SystemAreaController.java:71`、`MasterMenuController.java:57` |
| P-JSON-3 | fastjson2 与 Jackson 双栈并存，同一对象两条路径输出可能不同 | jar 内 `fastjson2-2.0.66.jar`、`fastjson2-extension-2.0.66.jar`、`fastjson2-extension-spring6-2.0.66.jar`；根 `pom.xml:61,234-236` + 10 个模块 pom 声明 |
| P-JSON-4 | `@JSONField` 是 fastjson2 专有注解，Jackson 不解析 | 全仓 **51 处注解 / 17 个文件**（其中裸注解 12 处 / 6 文件） |
| P-JSON-5 | 转换器 javadoc 结论错误：称「无法按类型替换 null」 | 实测 Jackson 3.1.7 存在 `BeanPropertyWriter.assignNullSerializer(...)`、`ValueSerializerModifier.modifySerializer(...)`，**可以**按属性类型替换 null |
| P-JSON-6 | MQ **出站**在用 fastjson2（不只是入站解析） | `scm-mq/.../rabbitmq/producer/ScmMqProducer.java:64,81`、`:125,142` 用 `JSON.toJSONString(...)` + `MessageBuilder.withBody(...)` |
| P-JSON-7 | 32 个 MyBatis TypeHandler 用 fastjson2 读写 DB 的 JSON 列 | `scm-core/.../mybatis/typehandlers/`（`JsonObjTypeHandler`、`JsonArrayTypeHandler`、`OperationFunctionInfoVoTypeHandler` 等）+ `scm-ai/.../FastjsonTypeHandler.java`、`scm-core-bpm/.../JsonObjectTypeHandler.java` |
| P-JSON-8 | `scm-framework/pom.xml:140,144` 同一 artifact 重复声明两次 | 复核发现，属清理项 |

### 1.1 fastjson2 用量实测（决定能不能删）

> 口径说明（2026-10-29 复核后修正）：下表分「构建内」与「全部」两列。构建内＝排除 `target`、`scm-core-whapp`、`scm-start-parent` 三个死模块；全部＝只排除 `target`。此前文档该表把两种口径混用，已改正。

| 指标 | 构建内（生效范围） | 全部（含死模块） |
|---|---|---|
| `import com.alibaba.fastjson*` 行数 | **391** | 400（255→260 文件） |
| 使用 `JSON.toJSONString / parseObject / parseArray` 的文件 | **125** | 128 |
| `JSONArray / JSONObject` 出现次数 | **1136** | 1153 |
| `@JSONField` **注解行**（`^\s*@JSONField`） | **51 处 / 17 个文件** | 同左 |
| 其中「无参数裸注解」 | **12 处 / 6 个文件** | 同左 |
| 声明 `fastjson2-extension-spring6` 的 pom | **13 个文件 / 14 次**（`scm-framework/pom.xml:140,144` 重复声明同一 artifact 两次） | 15 文件（含 `scm-start-parent` 2 个死模块） |
| 使用 fastjson2 的 MyBatis TypeHandler | **32 个** | 34（`scm-core-whapp` 2 个） |

**结论：本阶段不能删除 fastjson2**，只能「划边界 + 防扩散」。

### 1.2 @JSONField 的落点分类（逐处核过）

| 类别 | 文件（注解行数） | 用途 | 是否经 Jackson 出站 |
|---|---|---|---|
| A 仅 fastjson2 路径（安全） | `scm-ai` 6 个 node config：`HttpRequestNodeConfig`(7)、`McpToolNodeConfig`(7)、`MailSendNodeConfig`(5)、`OpenPageNodeConfig`(3)、`FaqExtractorNodeConfig`(2)、`KeywordExtractorNodeConfig`(2)；VO：`AiWfNodeInputConfigVo`(2)、`AiWfNodeParamRefVo`(2)、`AiWfNodeIOVo`(1) | 节点配置 JSON 从 DB 解析 | 否（`AiWfNodeInputConfigVo` 仅作 `AiConversationController.java:804` 私有方法参数，无 `@RequestBody`；`AiWfNodeParamRefVo`/node config 无 controller 引用） |
| A | `scm-excel`：`ExcelTemplate`(6) / `DataCol`(5) / `DataRow`(3) / `TitleCol`(2) / `TitleRow`(1) / `DataRowValidators`(1) | Excel 模板 JSON 解析 | 否（只 `JSON.parseObject(jsonConfig, ExcelTemplate.class)`，见 `SystemBaseController.java:125`、`AppBaseController.java:106`） |
| A（含隐患） | `scm-bean/.../UploadFileResultAo.java:26` `@JSONField(name="fileId")` 挂在 `fileUuid` 上（1 处） | 解析文件中台响应 | 否（`SystemBaseController.java:266`、`AppBaseController.java:234` 用 fastjson2 中转，随后 `copyProperties` 到返回 bean） |
| B 已由全局策略兜住 | `AiWorkflowRuntimeVo.java:68` `@JSONField(format=...)` 挂 `LocalDateTime cTime`（1 处） | HTTP 出参（`WorkflowController.java:299`） | **是**，但 `ScmJsonMapperFactory.java:72-75` 的 `@JsonFormat` mixin 已给同一格式，输出一致 |
| C 无参数裸注解（可删，共 12 处） | `ExcelTemplate`(40,45)、`DataCol`(31,36,42,45)、`DataRow`(34,42)、`TitleCol`(17,23)、`TitleRow`(21)、`DataRowValidators`(23) | 无实际作用（字段均有 Lombok 访问器） | — |

**合计：51 处注解 / 17 个文件**（此前文档写「57 处 / 11 个文件」有误：57 含 6 行 javadoc 里提到注解名的注释行，11 只数了 bean 目录漏了 `scm-ai` 的 6 个 node config 中的部分文件）。

**结论：`@JSONField` 今天没有造成任何 HTTP 输出差异**（B 类被全局 mixin 兜住，A 类不走 Jackson）。风险是「未来有人把这些类改成走 Jackson」。

> 补充（复核新发现）：`AiWorkflowRuntimeVo.java:45` `inputData`、`:52` `outputData` 两个字段类型是 fastjson2 的 `JSONObject`，而该类是 HTTP 出参。Jackson 把 `JSONObject`（实现了 `Map`）当普通 Map 序列化，**功能上可行**，但属于「双栈混用」；同型字段全仓 72 处，登记为观察项。

---

## 2. 方案

### 2.1 fastjson2（R-1）：三档，本阶段只做第一档

| 档 | 内容 | 代价 | 建议 |
|---|---|---|---|
| **F1 本阶段（推荐）** | ① 在根 pom `pom.xml:61,234-236` 的声明处加注释，写明「仅用于字符串↔对象手工转换，禁止用于 HTTP 出/入站」；② 新增巡检脚本放置于 `tools/check-fastjson-boundary.ps1`，检查新增的 `com.alibaba.fastjson2` import 只允许出现在白名单目录；③ 在 `00_doc` 登记「fastjson2 允许用途白名单」 | 0 行业务代码；1 个脚本 + pom 注释 | ✅ |
| F2 中期 | 把 125 个文件里的 `JSON.toJSONString/parseObject` 收敛到 `ScmJsonMapperFactory` 或新建 `JsonUtil` 统一封装 | 125 个文件，回归面大 | 暂缓 |
| F3 终局 | 全量迁 Jackson，删除 fastjson2 依赖与 3 个 jar | 工作量大、回归成本高 | 本阶段不做 |

**fastjson2 允许用途白名单（F1 落地口径）**

| 允许 | 禁止 |
|---|---|
| DB 中 JSON 文本字段 ↔ Java 对象（workflow 节点配置、Excel 模板、**32 个 MyBatis TypeHandler 读写 JSON 列**） | HTTP 出站（`@RestController` 返回值） |
| 调用外部 HTTP 服务后的响应体解析（如文件中台） | HTTP 入站（`@RequestBody`，当前全仓 0 处） |
| MQ **出站**序列化（`ScmMqProducer.java:64,81,125,142`，**现状即在用**，本阶段保留不动）+ 消息解析 | Redis 序列化（已改 Jackson，见 `RedisCacheAutoConfiguration.java:79-100`；`scm-redis/.../FastJsonRedisSerializer.java:33` 是**死代码**，需分类处置） |

### 2.2 @JSONField（R-2）：分类治理，改 7 个文件

| 动作 | 文件（行号） | 改动 | 处数 |
|---|---|---|---|
| 删除（C 类裸注解） | `scm-excel/.../template/ExcelTemplate.java:40,45`、`.../template/data/DataCol.java:31,36,42,45`、`.../template/data/DataRow.java:34,42`、`.../template/title/TitleCol.java:17,23`、`.../template/title/TitleRow.java:21`、`.../template/data/row/DataRowValidators.java:23` | 删 `@JSONField` 行（字段均有 Lombok 访问器，删除无副作用） | **12** |
| 加注释护栏（A 类） | `scm-bean/.../UploadFileResultAo.java:26`、`scm-ai/.../AiWfNodeInputConfigVo.java`(2)、`AiWfNodeParamRefVo.java`(2)、`AiWfNodeIOVo.java`(1) | 在注解上方补一行：`// 仅 fastjson2 解析路径生效；本类不得直接作为 @RestController 出/入参` | 6 |
| 补双注解（B 类，可选） | `scm-ai/.../AiWorkflowRuntimeVo.java:68` | 保留 `@JSONField` 的同时补 `@com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")`，让两个栈都显式生效（当前靠全局 mixin 兜底） | 1 |
| 不改 | 6 个 workflow node config（`HttpRequestNodeConfig` 7、`McpToolNodeConfig` 7、`MailSendNodeConfig` 5、`OpenPageNodeConfig` 3、`FaqExtractorNodeConfig` 2、`KeywordExtractorNodeConfig` 2） | 纯配置对象 | 26 |

### 2.3 必做项（R-3）：null 兜底序列化器

**目标行为矩阵（对齐升级前 fastjson2）**

| `json_result_type` | null Boolean | null String | null 其它 |
|---|---|---|---|
| NORMAL(0) | `false` | `null` | `null` |
| STRING_EMPTY_BOOLEAN_FALSE(1) | `false` | `""` | `null` |
| NULL_NOT_OUT(2) | 字段不输出 | 字段不输出 | 字段不输出 |

**改动文件（3 个）**

| 文件 | 性质 | 改动 |
|---|---|---|
| `scm-common/src/main/java/com/xinyirun/scm/common/utils/json/NullCompatSerializerModifier.java` | 新增 | `ValueSerializerModifier` 子类，覆写 **`changeProperties(...)`**（不是 `modifySerializer`）给属性挂 `assignNullSerializer` |
| `scm-common/src/main/java/com/xinyirun/scm/common/utils/json/ScmJsonMapperFactory.java` | 修改 | 新增 `NullCompat` 枚举与重载 `mvcMapper(JsonInclude.Include, NullCompat)`；原有无参/单参重载委托为 `NullCompat.NONE`（不改变 RestTemplate 等既有行为） |
| `scm-framework/.../messageconverter/CallbackMappingJackson2HttpMessageConverter.java` | 修改 | 预建 3 个 mapper 按 `json_result_type` 选择；`writeInternal` 的 `type` 加 null 兜底；修正 `:42-45` 的错误 javadoc |

> 单测类（`NullCompatSerializerModifierTest`）**已从方案中删除**：2026-10-29 用户否决「随意往方案里加东西」，且本项目 surefire 处于 `skipTests=true` 空转状态，写了也不会被执行。测试体系另案（T0.4/G2），不在本次改动范围。

**新增文件代码草案**

```java
package com.xinyirun.scm.common.utils.json;

import java.util.List;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.SerializationConfig;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.BeanPropertyWriter;
import tools.jackson.databind.ser.ValueSerializerModifier;

/**
 * 用 Jackson 3 复刻 fastjson2 的空值特性：
 * null Boolean -> false、null String -> ""（可选）。
 *
 * <p>实现原理：Jackson 3 没有 WriteNullBooleanAsFalse/WriteNullStringAsEmpty 开关，
 * 但可以为单个属性挂「null 序列化器」（BeanPropertyWriter#assignNullSerializer），
 * 属性类型在构建 BeanSerializer 时就已知，因此可以精确到字段类型。</p>
 */
public final class NullCompatSerializerModifier extends ValueSerializerModifier {

    /** null Boolean 输出 false。 */
    public static final ValueSerializer<Object> NULL_AS_FALSE = new FixedValueSerializer(false, null);
    /** null String 输出空串。 */
    public static final ValueSerializer<Object> NULL_AS_EMPTY = new FixedValueSerializer(null, "");

    private final boolean nullStringAsEmpty;

    public NullCompatSerializerModifier(boolean nullStringAsEmpty) {
        this.nullStringAsEmpty = nullStringAsEmpty;
    }

    @Override
    public List<BeanPropertyWriter> changeProperties(SerializationConfig config,
                                                     BeanDescription.Supplier beanDescRef,
                                                     List<BeanPropertyWriter> beanProperties) {
        for (BeanPropertyWriter w : beanProperties) {
            if (w.hasNullSerializer()) {
                // 业务已显式指定，尊重；且 assignNullSerializer 对已存在的实例再赋值会抛 IllegalStateException
                continue;
            }
            JavaType t = w.getType();
            Class<?> raw = (t == null) ? null : t.getRawClass();
            if (raw == null) {
                continue;
            }
            if (raw == Boolean.class || raw == boolean.class) {
                w.assignNullSerializer(NULL_AS_FALSE);
            } else if (nullStringAsEmpty && raw == String.class) {
                w.assignNullSerializer(NULL_AS_EMPTY);
            }
        }
        return beanProperties;
    }

    /** 把本 modifier 打成 Jackson 3 模块。 */
    public static SimpleModule module(boolean nullStringAsEmpty) {
        SimpleModule m = new SimpleModule("scm-null-compat");
        m.setSerializerModifier(new NullCompatSerializerModifier(nullStringAsEmpty));
        return m;
    }

    /** 固定输出的 null 序列化器：writeBoolean(false) 或 writeString("")。 */
    static final class FixedValueSerializer extends ValueSerializer<Object> {

        private final Boolean boolValue;
        private final String stringValue;

        FixedValueSerializer(Boolean boolValue, String stringValue) {
            this.boolValue = boolValue;
            this.stringValue = stringValue;
        }

        @Override
        public void serialize(Object value, JsonGenerator gen, SerializationContext ctxt) {
            if (boolValue != null) {
                gen.writeBoolean(boolValue);
            } else {
                gen.writeString(stringValue == null ? "" : stringValue);
            }
        }
    }
}
```

**`ScmJsonMapperFactory` 改动草案（增量）**

```java
    /** null 兼容档位。 */
    public enum NullCompat {
        /** 不做任何替换（升级前 Jackson2 行为；RestTemplate 等外部出站用）。 */
        NONE,
        /** null Boolean -> false（fastjson2 NORMAL 等价）。 */
        BOOLEAN_FALSE,
        /** null Boolean -> false 且 null String -> ""（fastjson2 STRING_EMPTY_BOOLEAN_FALSE 等价）。 */
        BOOLEAN_FALSE_STRING_EMPTY
    }

    public static JsonMapper mvcMapper() {
        return mvcMapper(JsonInclude.Include.ALWAYS, NullCompat.NONE);
    }

    public static JsonMapper mvcMapper(JsonInclude.Include inclusion) {
        return mvcMapper(inclusion, NullCompat.NONE);
    }

    public static JsonMapper mvcMapper(JsonInclude.Include inclusion, NullCompat nullCompat) {
        SimpleModule dateModule = new SimpleModule("scm-lenient-date");
        dateModule.addDeserializer(LocalDateTime.class, new LenientLocalDateTimeDeserializer());
        dateModule.addDeserializer(Date.class, new LenientDateDeserializer());

        JsonMapper.Builder builder = JsonMapper.builder()
                .changeDefaultPropertyInclusion(v -> v.withValueInclusion(inclusion))
                .defaultDateFormat(new SimpleDateFormat(DATE_TIME_PATTERN))
                .addMixIn(LocalDateTime.class, LocalDateTimeMixin.class)
                .addMixIn(LocalDate.class, LocalDateMixin.class)
                .addModule(dateModule);

        if (nullCompat == NullCompat.BOOLEAN_FALSE) {
            builder.addModule(NullCompatSerializerModifier.module(false));
        } else if (nullCompat == NullCompat.BOOLEAN_FALSE_STRING_EMPTY) {
            builder.addModule(NullCompatSerializerModifier.module(true));
        }
        return builder.build();
    }
```

**转换器改动草案（`CallbackMappingJackson2HttpMessageConverter`）**

```java
    /** NORMAL(0)：fastjson2 WriteMapNullValue + WriteNullBooleanAsFalse。 */
    private static final JsonMapper MAPPER_NORMAL =
            ScmJsonMapperFactory.mvcMapper(JsonInclude.Include.ALWAYS,
                    ScmJsonMapperFactory.NullCompat.BOOLEAN_FALSE);

    /** STRING_EMPTY_BOOLEAN_FALSE(1)：其余 null 省略，String->"" / Boolean->false 照出（null 序列化器优先于抑制，实测）。 */
    private static final JsonMapper MAPPER_STRING_EMPTY =
            ScmJsonMapperFactory.mvcMapper(JsonInclude.Include.NON_NULL,
                    ScmJsonMapperFactory.NullCompat.BOOLEAN_FALSE_STRING_EMPTY);

    /** NULL_NOT_OUT(2)：不输出 null 字段（null 被省略，无需替换）。 */
    private static final JsonMapper MAPPER_NON_NULL =
            ScmJsonMapperFactory.mvcMapper(JsonInclude.Include.NON_NULL,
                    ScmJsonMapperFactory.NullCompat.NONE);
```

> **档位修正（2026-10-29 实测，原草案此处有误）**：`MAPPER_STRING_EMPTY` 原写 `Include.ALWAYS`，但基准 STRING_EMPTY 档会把**非 String/Boolean 的 null 字段整条省略**（探针 `Probe4[B]`：`n`/`o`/`l`/`m` 均不出现），故必须改 `Include.NON_NULL`；实测 `Include.NON_NULL` 抑制不掉已挂 null 序列化器的属性，Boolean 仍出 `false`、String 仍出 `""`（`Probe5[2]`）。
> 同时 `mvcMapper(...)` 在 `inclusion == NON_NULL` 时自动给 Map/Collection 属性挂 `contentInclusion=NON_NULL` 的 introspector，用于抹平最后一处差异（Map 内 null value 被省略，`Probe4[B]/[C]`）；`NORMAL` 档**不得**挂（基准 `Probe4[A]` 保留 `mv.a=null`）。

    public CallbackMappingJackson2HttpMessageConverter() {
        this(MAPPER_NORMAL);   // 原为 ScmJsonMapperFactory.mvcMapper()
    }

    @Override
    protected void writeInternal(Object object, ResolvableType type, HttpOutputMessage outputMessage,
                                 Map<String, Object> hints) throws IOException, HttpMessageNotWritableException {
        Integer jsonResultType = resolveJsonResultType(object);
        JsonMapper target = null;
        if (jsonResultType != null) {
            if (jsonResultType == JsonResultTypeConstants.NULL_NOT_OUT) {
                target = MAPPER_NON_NULL;
            } else if (jsonResultType == JsonResultTypeConstants.STRING_EMPTY_BOOLEAN_FALSE) {
                target = MAPPER_STRING_EMPTY;
            }
        }
        if (target == null) {
            super.writeInternal(object, type, outputMessage, hints);
            return;
        }
        // type 可能为 null（Spring 7.0.10 AbstractJacksonHttpMessageConverter#writeInternal 的入参可空，
        // 如 /error、部分异常处理器路径），必须兜底，否则 NPE
        Class<?> typeClass = (type != null && type.getType() != null) ? type.getType() : object.getClass();
        JavaType javaType = getJavaType(typeClass, object.getClass());
        try {
            target.writerFor(javaType).writeValue(outputMessage.getBody(), object);
        } catch (JacksonException ex) {
            throw new HttpMessageNotWritableException("I/O error while writing " + javaType, ex);
        }
    }
```

> **风险提示（评审 Top2）**：`CallbackMappingJackson2HttpMessageConverter` 无参构造由 `mvcMapper()`（NONE 档）改为 `MAPPER_NORMAL`（BOOLEAN_FALSE 档），`ActionInterceptorConfig.java:83` 注册的是这个无参实例，因此**所有 MVC 出口**（含 `/error`、参数校验失败体、文件下载失败的异常体）都会从「null 原样输出」变成「null Boolean → false」。这正是对齐 fastjson2 的目标行为，但 §4 的 JSON diff 必须把这些"非业务接口"也纳入抽样。

**覆盖范围与基准逐档对照（2026-10-29 实测修订，原「不覆盖 / 登记未知差异」结论作废）**

基准真值（探针 `D:\temp\json-null-probe\Probe4.java`，fastjson2 **2.0.66** 按基线三档特性实跑；样本字段：`b`=Boolean null、`s`=String null、`n`=Integer null、`o`=Object null、`l`/`m`=字段本身为 null 的 List/Map、`mv`=Map 内含 null value、`le`=List 内含 null 元素）：

| 档 | 基准输出（原文） |
|---|---|
| NORMAL（`ReferenceDetection`+`WriteMapNullValue`+`WriteNullBooleanAsFalse`） | `{"b":false,"l":null,"le":[null,true],"m":null,"mv":{"a":null,"b":true},"n":null,"o":null,"s":null}` |
| STRING_EMPTY（`ReferenceDetection`+`WriteNullStringAsEmpty`+`WriteNullBooleanAsFalse`） | `{"b":false,"le":[null,true],"mv":{"b":true},"s":""}` |
| NULL_NOT_OUT（仅 `ReferenceDetection`） | `{"le":[null,true],"mv":{"b":true}}` |

⇒ 关键事实（2026-10-29 第 4 轮复核后再修正，以 `Probe7` 为准）：
1. **`WriteNullBooleanAsFalse` 只作用于 bean 属性**：Map 的 value、任何集合/数组元素都不替换（`le[0]`、`List<Boolean>` 元素始终 `null`）。
2. **`WriteNullStringAsEmpty` 的作用面更大、且不对称**：bean 属性 + **`List<String>` / `String[]` 的元素**都会被写成 `""`；而 `Set<String>`、`Collection<String>`、嵌套容器 `List<List<String>>`、`Map` 的 value **不替换**。`Probe7` 原文（STRING_EMPTY 档）：`{"as":["","x"],"ls":["","x"],"ss":[null,"x"],"lls":[[null]],"lb":[null,true],"lo":[null,"x"],"ms":{"b":"y"},"s":""}`。
3. 集合 `null → []` 仍不需要实现：基线三档**都未启用** `WriteNullListAsEmpty`（`CallbackMappingJackson2HttpMessageConverter.java:107-111`、`:131-139` 里是注释态）。

真正剩下的差异是**两条**：

| # | 差异 | 是否可消除 |
|---|---|---|
| 1 | **Map 内 null value**：基准在 STRING_EMPTY / NULL_NOT_OUT 两档省略，Jackson 默认保留 | ✅ 可消除（下表 content NON_NULL，`Probe6[4]` 已跑通） |
| 2 | **STRING_EMPTY 档下 `List<String>` / `String[]` 的 null 元素**：基准写 `""`，Jackson 只能写 `null`（开 `APPLY_JSON_INCLUDE_FOR_CONTAINERS` 则变成 `[]`，都不是 `""`） | ❌ 无法用公开 API 复刻 ⇒ **登记为已知边界** |

> 差异 2 的**今日影响面 = 0**：全仓只有两个接口走 STRING_EMPTY 档，payload 分别是 `SAreasCascaderTreeVo`（只有 `Long value`）与 `MMenuDataVo`（只有 `Long[] root_ids` / `List<Long>`），都不含 `List<String>` / `String[]`。
> 参考量级（供将来评估）：全仓 bean/vo 中 `String[]` 字段 98 处、`List|Set|Collection<String>` 54 处，但它们在 NORMAL 档（不替换 String 元素）⇒ 无差异。

| 场景 | Jackson 3.1.7 实测行为 | 与基准是否一致 |
|---|---|---|
| bean 上声明为 `Boolean` / `String` 的属性 | `assignNullSerializer` 生效（探针 `Probe5[2]`、`Probe6`） | ✅ 一致 |
| bean 上声明为 `Object` 的属性（值 null） | 无法判定该出 `false` 还是 `""`，保持 null | ✅ 一致（NORMAL 档基准也是 null；另两档整条省略） |
| `Map<String,Object>` 的 value 为 null | 默认保留；`contentInclusion=NON_NULL` 可省略（`Probe6[4]`） | ✅ 用 content NON_NULL 对齐 |
| `List<Boolean>` / `Set` / `List<Object>` 里的 null 元素 | 保留 | ✅ 一致（基准也保留） |
| **`List<String>` / `String[]` 的 null 元素（仅 STRING_EMPTY 档）** | 只能 `null`，或开全局开关变 `[]` | ⚠️ **无解，登记边界**（今日 0 命中） |
| `@JsonInclude(NON_NULL)` 档 + 挂了 null 序列化器的 Boolean/String | **null 序列化器优先于抑制**：Boolean→`false`、String→`""`，其余 null 属性省略（`Probe5[2]`） | ✅ 正好实现 STRING_EMPTY 档 |

**Map value 对齐的修法（`javap` 已证实 API 存在，零业务代码改动）**

| 手段 | 落点 | 说明 |
|---|---|---|
| `AnnotationIntrospector.findPropertyInclusion(MapperConfig, Annotated)` | `ScmJsonMapperFactory` 内的自定义 introspector | 对 Map/Collection 类型属性返回 `JsonInclude.Value`，`contentInclusion = NON_NULL` |
| `MapperBuilder.withConfigOverride(Map.class, o -> o.setIncludeAsProperty(...))` | 同上 | `MutableConfigOverride.setIncludeAsProperty(JsonInclude$Value)` / `setInclude(...)` 均存在 |

> 仅对 **STRING_EMPTY / NULL_NOT_OUT 两档**启用 content NON_NULL；NORMAL 档必须保持"Map 内 null 照旧输出"（基准 [A] 档如此）。

> `RestTemplateConfiguration.java:57-67` 显式传 `ScmJsonMapperFactory.mvcMapper()`（=`NullCompat.NONE`），外部调用报文不受影响。

### 2.4 建议项（R-4）：循环引用护栏

**问题**：fastjson2 的 `ReferenceDetection` 会对重复引用输出 `$ref`；Jackson 无全局等价物，A→B→A 会 `StackOverflowError` → 500。

**改动文件（2 个）**

| 文件 | 改动 |
|---|---|
| `scm-common/.../ScmJsonMapperFactory.java` | **只在 MVC 出口那 3 个 mapper 的 builder 上**追加 `.enable(SerializationFeature.FAIL_ON_SELF_REFERENCES)`（`tools.jackson.databind.SerializationFeature`，实测存在；也可选 `WRITE_SELF_REFERENCES_AS_NULL`）。**不得加在公共 `mvcMapper()` 里**（理由见下） |
| `00_doc/21-编码规范-JSON对象图.md`（新增，或并入本文件 §2.4） | 规定：响应 bean 禁止父子双向引用；必须双向的用 `@JsonIdentityInfo` 或拆 DTO |

> **必须按出口启用，不能全局 enable（2026-10-29 第 4 轮复核采纳）**：`mvcMapper()` 的现有调用方共 4 处，除 MVC 外还有出站/消息链路——
> `RestTemplateConfiguration.java:58`（RestTemplate 对外报文）、`scm-mq/.../ConsumerConfiguration.java:38`（MQ 入站）、`scm-ai/.../RabbitMQConfig.java:82`（AI MQ）。
> 若把 `FAIL_ON_SELF_REFERENCES` 写进公共 `mvcMapper()`，这三条链路一旦遇到自引用对象就从「原样序列化」变成直接抛错（→ MQ 消费失败 / 对外调用失败），属于**行为倒退**。

```java
        // 仅 MVC 出口的 3 个 mapper 使用；公共 mvcMapper() 保持不变
        JsonMapper.Builder builder = JsonMapper.builder()
                .changeDefaultPropertyInclusion(v -> v.withValueInclusion(inclusion))
                // 直接自引用（getter 返回 this）立即报错，而不是递归到栈溢出
                .enable(SerializationFeature.FAIL_ON_SELF_REFERENCES)
                ...
```

> **RestTemplate 不受「默认构造改档」影响（已核实）**：`ActionInterceptorConfig.java:83` 用无参构造 `new CallbackMappingJackson2HttpMessageConverter()`（= MVC 出口，会被改档）；而 `RestTemplateConfiguration.java:58` 用的是 `new CallbackMappingJackson2HttpMessageConverter(ScmJsonMapperFactory.mvcMapper())` 显式传 `NullCompat.NONE` 的 mapper ⇒ 对外报文口径不变。**只要不改公共 `mvcMapper()` 本身，RestTemplate 就安全。**

若希望「自引用输出 null 而不是 500」：换成 `.enable(SerializationFeature.WRITE_SELF_REFERENCES_AS_NULL)`。

对于 A→B→A 环形引用，Jackson 没有全局开关，只能逐类：

```java
@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
public class BomNodeVo { ... }
```

**现状评估**：全仓自引用仅 `TreeNode.java:37`（已注释）、`transient` 字段 0 个 ⇒ 今天无实际风险，属护栏建设。

---

## 3. 执行顺序（待批准后执行）

| 步骤 | 动作 | 验证 |
|---|---|---|
| E1 | 新增 `NullCompatSerializerModifier` | `mvn -o -q -pl scm-common compile` |
| E2 | 改 `ScmJsonMapperFactory`（加 enum + 重载，旧重载委托 NONE） | 同上 + 现有调用方 0 改动 |
| E3 | 改 `CallbackMappingJackson2HttpMessageConverter`（3 个 mapper + 修正 javadoc） | `mvn -o -q -pl scm-framework -am compile` |
| E4 | 建议项：加 `FAIL_ON_SELF_REFERENCES` | 全量 `mvn -o -DskipTests package` |
| E5 | `@JSONField` 三处治理（删 2 处裸注解 + 注释护栏 + B 类补双注解） | 同 E4 |
| E6 | fastjson2 边界：pom 注释 + 巡检脚本 + 白名单登记 | 脚本本地跑通 |
| E7 | 启动冒烟 + 接口 JSON diff | 见 §4 |

## 4. 验收清单（接口级 JSON diff，升级前后逐字段）

| # | 接口 | 关注点 |
|---|---|---|
| 1 | `SystemAreaController#getAreasCascader`（`POST /api/v1/common/areas/area/casca`，`SystemAreaController.java:67-71`） | null String → `""`、null Boolean → `false` |
| 2 | `MasterMenuController#cascaderList`（`POST /api/v1/menus/cascader/list`，`MasterMenuController.java:53-57`） | 同上 |
| 3 | `UserController` 两个 `NULL_NOT_OUT` 接口（`:64`、`:81`） | null 字段仍然不出现 |
| 4 | 任一 `LocalDateTime`/`Date` 列表接口 | 仍是 `yyyy-MM-dd HH:mm:ss` |
| 5 | 文件上传（`uploadFile`） | `fileId` 等字段名不变 |
| 6 | `/scm/error`、任一 `@Valid` 校验失败响应体（评审 Top2 追加） | 默认转换器档位由 NONE 改 BOOLEAN_FALSE 后，这些非业务出口的 null Boolean 也会变 `false`，需前后对比确认可接受 |
| 7 | 含 `Map<String,Object>` / `List<String>` / `String[]` / `Set<String>` / `List<Boolean>` 的接口（第 4 轮复核扩充形状） | ① Map 的 null value 在 STRING_EMPTY / NULL_NOT_OUT 两档应被省略（验 content NON_NULL 是否生效）；② `List<String>`/`String[]` 的 null 元素在 STRING_EMPTY 档会从 `""` 退化为 `null`（已知边界，今日 0 命中，抽样记录即可）；③ `Set`/`List<Boolean>`/`List<Object>` 元素两边都保留 null，不需比 |

## 5. 进度

| 项 | 状态 | 备注 |
|---|---|---|
| 需求确认（R-1~R-5） | ✅ 2026-10-29 | 用户提出 |
| 现状取证 | ✅ 2026-10-29 | 源码 + jar + Jackson 3.1.7 常量池 |
| 方案评审 | ✅ 2026-10-29 完成（2 名不同成员） | `reviewer-json` needs_revision（23 条）→ 已逐条复核并改正 §1/§2.1/§2.2（见 §6）；`reviewer-jackson` needs_revision（Top3）→ 已逐条 javap 复验并改正 §2.3/§4（见 §7）。两份评审报告：`00_doc/21-review-json-2026-10-29.md`、`00_doc/22-review-jackson-2026-10-29.md` |
| 方案定稿 | ✅ 2026-10-29 用户批准（只批 3 个文件） | 第 1~4 轮评审已闭合：采纳 10 项、登记 1 项已知边界、驳回 2 项（见 §10） |
| E1~E3 必做项实施 | ✅ 2026-10-29 完成 | 3 个文件（新增 1、改 2），全仓 `mvn -o compile` BUILD SUCCESS，见 §12 |
| E3.5 代码评审（reviewer-code） | ✅ 完成并整改 | verdict needs_revision（1 阻断 + 2 低）；阻断 = `LocalDateTime` 的 `@JsonFormat` mixin 在 Jackson 3.1.7 无效（**既存缺陷**），已改 `withConfigOverride` 并复验，见 §13 |
| E4 循环引用护栏 | ☐ 未开始 | 未获批准 |
| E5 `@JSONField` 治理 | ☐ 未开始 | 未获批准；现状 51 处 |
| E6 fastjson2 边界 | ☐ 未开始 | 未获批准；现状 255 个文件 import、14 处 pom 声明 |
| E7 接口级 JSON diff | ☐ 未开始 | 需启动应用取 token |

## 6. 评审记录（reviewer-json，2026-10-29）

独立成员 `reviewer-json` 给出的 verdict = **needs_revision**，23 条意见。我逐条独立复核（自写 grep + 读源码，未采信其数字）后的处置：

| 意见 | 性质 | 我的复核结果 | 处置 |
|---|---|---|---|
| R-01~R-03 数字（391/125/1136/11 pom）不可复现 | 成立 | 我重测：构建内 391/125/1136/13 文件(14 次)；全部 400/260/128/1153/15 文件；原文未标口径且 pom 数列错 | **已改** §1.1（分口径两列） |
| R-04~R-07 `@JSONField` 57→51、文件 11→17、裸注解 2~3→12 | 成立 | 我重测逐文件：合计 **51 行 / 17 文件**；裸注解 **12 处 / 6 文件**（与评审点位逐一对应） | **已改** §1.1、§1.2、§2.2 |
| R-08 A 类「不走 HTTP」证据不足 | 部分成立 | `@RequestBody` 全仓 0 命中成立；`AiWfNodeInputConfigVo` 仅 `AiConversationController.java:804` 私有方法参数成立 | **已补**证据到 §1.2 |
| R-09 B 类 mixin 兜底 | 成立 | `ScmJsonMapperFactory.java:72-75` 同 `yyyy-MM-dd HH:mm:ss` | 已在 §1.2 引用 |
| R-10 `fastjson2-extension-spring6` 不会与自有 converter 抢注册 | 成立 | jar 内无 `spring.factories` / `AutoConfiguration.imports` | 已确认，无改动 |
| R-11 漏项：MQ 出站仍 fastjson2 | **成立（重要）** | `ScmMqProducer.java:64,81,125,142` 实测 | **已加** P-JSON-6 + 白名单 |
| R-12 漏项：Redis 死代码 `FastJsonRedisSerializer` | 成立 | `scm-redis/.../FastJsonRedisSerializer.java:33` 存在 | **已加**白名单注 |
| R-13 漏项：MyBatis TypeHandler | **成立（重要）** | 实测 **32 个** TypeHandler 用 fastjson2（`scm-core` 29 + `scm-ai` 2 + `scm-core-bpm` 1 + 已死 `scm-core-whapp` 2） | **已加** P-JSON-7 + 白名单 |
| R-14 漏项：`AiWorkflowRuntimeVo.java:45,52` 的 `JSONObject` 字段走 HTTP 出参 | 成立 | 实测字段类型为 fastjson2 `JSONObject`，Jackson 按 Map 可序列化；同型字段全仓 72 处 | **已加** §1.2 观察项 |
| R-15 `scm-framework/pom.xml:140,144` 重复声明同一 artifact | 成立 | 实测确为重复 | **已加** P-JSON-8 |
| R-16~R-20 表述/编排类（表格自相矛盾、口径混用） | 成立 | — | **已改** |
| R-21~R-23 建议补基准证据 | 部分采纳 | `ActionInterceptorConfig.java:102-131` 是更早一代 fastjson 配置的**整块注释**，不是生效基准；真实基准 = 基线 commit `6e89d9b` 的 `CallbackMappingJackson2HttpMessageConverter.java:80-142`（三个分支各 `setWriterFeatures`：NORMAL→`ReferenceDetection+WriteMapNullValue+WriteNullBooleanAsFalse+PrettyFormat`、STRING_EMPTY_BOOLEAN_FALSE→`ReferenceDetection+WriteNullStringAsEmpty+WriteNullBooleanAsFalse+PrettyFormat`、NULL_NOT_OUT→`ReferenceDetection+PrettyFormat`；**三档都没有 `WriteNullListAsEmpty`/`WriteNullNumberAsZero`（源码里是注释掉的）**，且已用 `Probe4` 跑出真值表（§2.3 / §8） | 见 §2.3「基准逐档对照」 |

**未采纳/降级**：评审给出的「全部口径 400 import / 260 文件 / 1253 次」与我实测 1153 次不一致（其正则多算了 `JSONObject.` 与 `JSON.toJSONString` 之外的写法），以本文件标口径的数字为准。

## 7. 评审记录（reviewer-jackson，2026-10-29）

独立成员 `reviewer-jackson` verdict = **needs_revision**，产出 `00_doc/22-review-jackson-2026-10-29.md`（全部用 `javap` 直读 jar）。以下每条我都用 `javap -classpath jackson-databind-3.1.7.jar` 自己复验过：

| 意见 | 我的复验（javap 输出） | 处置 |
|---|---|---|
| **Top1** `((BeanSerializer)s).properties()` 不成立 | `javap tools.jackson.databind.ser.BeanSerializer` → **无 `properties()`**；`tools.jackson.databind.ser.bean.BeanSerializerBase.properties()` → `Iterator<PropertyWriter>`（不是 `BeanPropertyWriter[]`）⇒ 原草案**编译失败**，评审正确 | **已改**：改用 `ValueSerializerModifier#changeProperties(SerializationConfig, BeanDescription$Supplier, List<BeanPropertyWriter>)`（javap 确认存在），入参即 writer 列表；同时去掉 `BeanSerializer` import、加 `JavaType`/`List` |
| **Top2** `type.getType()` 会 NPE | `javap org.springframework.http.converter.AbstractJacksonHttpMessageConverter` → `writeInternal(Object, org.springframework.core.ResolvableType, HttpOutputMessage, Map)`，`ResolvableType` 入参可空 ⇒ 评审正确 | **已改**：`typeClass = (type != null && type.getType() != null) ? type.getType() : object.getClass()`；并写明默认构造改档后**所有 MVC 出口**（含 `/error`、校验错误体）同步变化，§4 新增验收 #6 |
| **Top3** 覆盖范围需写明 | 逻辑上成立（`changeProperties` 只给 bean 属性挂序列化器） | **已改**：§2.3 新增「本方案不覆盖的范围」5 行表；§4 新增验收 #7；单测类**未纳入**（用户 2026-10-29 否决加东西，已从改动文件表删除） |
| `modifySerializer` 第二参是 `BeanDescription.Supplier` | javap 确认 | 无需改 |
| `hasNullSerializer()` 护栏必需 | javap 确认 `assignNullSerializer(ValueSerializer<Object>)` 与 `hasNullSerializer()` 均存在 | 保留（注释已写明重复赋值抛 `IllegalStateException`） |
| `serialize` 不写 throws 可编译 | javap：`NullSerializer.serialize(Object, JsonGenerator, SerializationContext) throws JacksonException`，而 `JacksonException extends RuntimeException` | 保留现写法 |
| `SimpleModule.setSerializerModifier` / `addModule` 可用 | javap 确认 | 保留 |
| `FAIL_ON_SELF_REFERENCES` / `WRITE_SELF_REFERENCES_AS_NULL` 只护直接自引用 | 与本文件 §2.4 结论一致 | 保留 §2.4 结论 |
| RestTemplate 行号应为 `:57-58`（非 `:57-67`） | 与我先前读到的注册段一致 | 已在 §2.1 说明中引用，不再展开 |
| 嵌套元素（`JsonResultAo` 内层 bean）会被调用；NULL_NOT_OUT 分支与 RestTemplate 不受影响 | 与设计一致 | 无需改 |
| ~~NON_NULL 抑制优先于 null 序列化器~~ | **2026-10-29 实测推翻**（探针 `Probe5[2]`）：`Include.NON_NULL` 下挂了 `assignNullSerializer` 的 Boolean 仍输出 `false`、String 仍输出 `""`，其余 null 属性省略 ⇒ **null 序列化器优先于抑制** | **已改**：§2.3 覆盖范围表 + 档位修正说明 |

## 8. 实测证据（2026-10-29，探针在 `D:\temp\json-null-probe\`）

| 探针 | 目的 | 结论 |
|---|---|---|
| `Probe.java` | Jackson 3.1.7：`@JsonSerialize(contentUsing)` 与 `AnnotationIntrospector.findContentSerializer` 能否替换容器内 null | 内容序列化器**只对非 null 内容生效**，null 元素/值绕过它 ⇒ 无法用内容序列化器替换容器内 null |
| `Probe2.java` | `@JsonInclude(content=NON_NULL)` 与 `SerializationFeature.APPLY_JSON_INCLUDE_FOR_CONTAINERS` | 只能**省略**容器内 null；List 需该全局开关才省略元素，且不能替换成 `""`/`false` |
| `Probe3.java` | fastjson2 2.0.66：五组特性组合下 bean 属性 vs Map value vs 集合元素 | bean `Boolean→false`、`String→""`、`Number→0`；**Map value 与集合元素始终保留 null** |
| `Probe4.java` | fastjson2 2.0.66：**严格按基线三档特性**组装，取真值表 | NORMAL：bean null 照出、其余 null 照出、Map value null 保留；STRING_EMPTY：仅 Boolean/String 补默认值、其余 null 整条省略、Map value null 省略；NULL_NOT_OUT：所有 null 字段省略、Map value null 省略。**三档集合元素 null 均保留** |
| `Probe5.java` | Jackson 3.1.7：`Include.NON_NULL` 与 `assignNullSerializer` 的先后顺序 | null 序列化器**优先**：`{"b":false,"le":[null,true],"mv":{"a":null,"b":true},"s":""}` ⇒ STRING_EMPTY 档可用 `NON_NULL + nullSerializer` 精确复刻，残余差异仅 Map 内 null value |

> 运行环境：`D:\2025_project\99_tools\jdk\jdk-21.0.10\bin\javac|java`；jar 取自本地离线仓库（`tools\jackson\core\jackson-databind\3.1.7`、`com\alibaba\fastjson2\fastjson2\2.0.66`、`com\fasterxml\jackson\core\jackson-annotations\2.21`）。

## 9. 修订纪要（2026-10-29，第 3 轮）

| # | 原结论 | 实测后修正 |
|---|---|---|
| 1 | 「fastjson2 的 `WriteNullBooleanAsFalse` 对 Map value、集合元素同样生效，Jackson 3 复刻不了 ⇒ 登记已知差异或改业务字段」 | **作废**。fastjson2 也只作用于 bean 属性；容器内 null 两边一致，无需取舍 |
| 2 | `MAPPER_STRING_EMPTY` 用 `Include.ALWAYS` | 改 `Include.NON_NULL`（基准该档会省略非 Boolean/String 的 null 字段） |
| 3 | 「`@JsonInclude(NON_NULL)` 抑制优先于 null 序列化器」 | 实测相反：null 序列化器优先，故 STRING_EMPTY 档可复用 NON_NULL |
| 4 | Map 内 null value 无解 | 有解且零业务改动：`contentInclusion=NON_NULL`（`AnnotationIntrospector.findPropertyInclusion` 或 `MutableConfigOverride.setIncludeAsProperty`），仅用于 STRING_EMPTY / NULL_NOT_OUT 两档 |
| 5 | 基线 STRING_EMPTY 分支特性含 `WriteNullListAsEmpty` | 实际**注释掉了**，基线三档均无该特性（`CallbackMappingJackson2HttpMessageConverter.java:107-111`） ⇒ 集合 `null → []` 不需要实现 |

## 10. 第 4 轮复核处置（r3，2026-10-29）

两名独立成员各出一份报告：`00_doc/23-review-jackson-r3-2026-10-29.md`、`00_doc/24-review-json-r3-2026-10-29.md`（均 needs_revision）。我逐条自己复跑/读码后处置：

| 来源 | 意见 | 我的复核 | 处置 |
|---|---|---|---|
| reviewer-json | `WriteNullStringAsEmpty` 会进入 `List<String>` / `String[]` 元素 | 自跑 `Probe7` 确认：STRING_EMPTY 档 `"ls":["","x"]`、`"as":["","x"]`；而 `Set`/嵌套容器/`List<Boolean>`/`List<Object>` 仍 `null` | **采纳**，§2.3 事实 1/2 重写，新增「差异 2」 |
| reviewer-json | 残留差异不止一条（还有 STRING_EMPTY 档的 String 元素） | 成立；Jackson 侧无公开 API 复刻（`Probe8` 已证：只能 `null` 或 `[]`） | **采纳**，登记为已知边界（今日 0 命中） |
| reviewer-json | §4 第 1/2 条接口名写错 | 代码核实：`SystemAreaController.java:67-71` = `POST /api/v1/common/areas/area/casca`；`MasterMenuController.java:53-57` = `POST /api/v1/menus/cascader/list` | **采纳**，已改 |
| reviewer-json | §4 第 7 条抽样形状只写 `List<Boolean>`（恰是恒等形状） | 成立 | **采纳**，已扩充 5 种形状 |
| reviewer-json | §6 的 `ActionInterceptorConfig.java:110-131` 应为 `:102-131` | 成立（`sed -n '102,131p'`） | **采纳**，已改 |
| reviewer-jackson | 循环引用护栏必须按出口启用，不能放进公共 `mvcMapper()` | 成立：`mvcMapper()` 另有 3 个调用方（`RestTemplateConfiguration.java:58`、`scm-mq/.../ConsumerConfiguration.java:38`、`scm-ai/.../RabbitMQConfig.java:82`） | **采纳**，§2.4 已改并写明理由 |
| reviewer-jackson | 默认构造改档会连带改 RestTemplate 对外报文 | **不成立**：`RestTemplateConfiguration.java:58` 显式传 `ScmJsonMapperFactory.mvcMapper()`（NONE），不走无参构造；无参构造只被 `ActionInterceptorConfig.java:83`（MVC 出口）使用 | **驳回**，§2.4 已写明 |
| reviewer-jackson | 「只删 2 处 → 裸 500 无提示」「§3 E1 行为无差异」「20 号 :157-158」等 | 该份评审针对的不是本轮发出的 4 条结论（引用项在本文件中不存在） | **驳回**（错位复核） |

> 另记：reviewer-jackson 在报告里自述执行了 `npx cc-safety-net rule tool add ops_bash` 注册工具规则。该动作超出只读评审授权，已记录并禁止后续评审改动本机工具配置。

## 11. 探针补记（第 4 轮新增）

| 探针 | 目的 | 结论 |
|---|---|---|
| `Probe6.java` | Jackson 3.1.7：`ALWAYS/NON_NULL + nullSerializer`，再加 `AnnotationIntrospector.findPropertyInclusion` 给 Map 属性挂 `contentInclusion=NON_NULL` | `[4]` 输出 `{"b":false,"le":[null,true],"lv":[null,"x"],"mv":{"b":true},"s":""}`，与基准 STRING_EMPTY 档**逐字段相同** ⇒ 修法与档位设置均可落地 |
| `Probe7.java` | fastjson2 2.0.66：元素级 null 的作用面 | `WriteNullStringAsEmpty` 覆盖 `List<String>`/`String[]` 元素；`WriteNullBooleanAsFalse` 不覆盖 `List<Boolean>`；`Set`/嵌套容器/`Map` value 都不覆盖 ⇒ **不对称**，须按类型分别判定 |

## 12. 实施记录（2026-10-29 编码完成，待代码评审）

| # | 文件 | 变更 | 行数变化 |
|---|---|---|---|
| 1 | `scm-common/src/main/java/com/xinyirun/scm/common/utils/json/NullCompatSerializerModifier.java` | **新增**：覆写 `changeProperties(...)`，给 `Boolean`（→`false`）与 `String`（→`""`，可选）属性挂 null 序列化器；`hasNullSerializer()` 做护栏 | +96 |
| 2 | `scm-common/src/main/java/com/xinyirun/scm/common/utils/json/ScmJsonMapperFactory.java` | 新增 `NullCompat` 枚举与 `mvcMapper(Include, NullCompat)` 重载；`Include.NON_NULL` 时挂 `MapContentNonNullIntrospector`（Map 属性 `contentInclusion=NON_NULL`）；原 `mvcMapper()` / `mvcMapper(Include)` 一律委托 `NullCompat.NONE`，**RestTemplate / MQ / AI MQ 行为不变** | +74 |
| 3 | `scm-framework/src/main/java/com/xinyirun/scm/framework/config/messageconverter/CallbackMappingJackson2HttpMessageConverter.java` | 预建 `MAPPER_NORMAL` / `MAPPER_STRING_EMPTY` / `MAPPER_NON_NULL`；`writeInternal` 按 `json_result_type` 选档；`type` 加 null 兜底（`java.lang.reflect.Type declaredType`）；修正 `:42-45` 的错误 javadoc | +34 / -18 |

**编译验证**

| 命令 | 结果 |
|---|---|
| `mvn -o -pl scm-common,scm-framework -am compile -DskipTests` | BUILD SUCCESS |
| `mvn -o compile -DskipTests`（全仓 26 模块） | **BUILD SUCCESS**（0 error） |

**行为验证**（`D:\temp\json-null-probe\Verify.java`，classpath 直连 `scm-common/target/classes`）

| 档 | 新代码输出 | 升级前基准（Probe4） | 结论 |
|---|---|---|---|
| NORMAL | `{"b":false,"l":null,"le":[null,true],"m":null,"mv":{"a":null,"b":true},"n":null,"o":null,"s":null}` | 同 | ✅ 逐字段一致 |
| STRING_EMPTY | `{"b":false,"le":[null,true],"mv":{"b":true},"s":""}` | 同 | ✅ 逐字段一致（含 Map 内 null value 被省略） |
| NULL_NOT_OUT | `{"le":[null,true],"mv":{"b":true}}` | 同 | ✅ 逐字段一致 |
| `mvcMapper()`（legacy/RestTemplate/MQ） | `{"b":null,...,"s":null}` | 与改动前相同 | ✅ 未受影响 |

**尚未完成**

| 项 | 原因 |
|---|---|
| 接口级 JSON diff（§4 的 7 条） | 需启动应用并登录取 token，属 E7 |
| E5 `@JSONField` 治理、E6 fastjson2 边界 | 不在本次批准范围（本次只批 3 个文件） |
| 代码评审 | 已派独立成员执行，结论待回填 §13 |

## 13. 代码评审与整改（2026-10-29，reviewer-code）

独立成员 `reviewer-code` 只读评审，报告 `00_doc/25-code-review-json-null-compat-2026-10-29.md`，verdict = **needs_revision**（1 阻断 + 2 低风险）。我逐条自己复现后整改：

| # | 评审意见 | 我的复现 | 整改 |
|---|---|---|---|
| 1（阻断） | `ScmJsonMapperFactory` 的类级 `@JsonFormat` **mixin** 对 `LocalDateTime` 在 Jackson 3.1.7 不生效，日期退化为 ISO-8601，与升级前 fastjson2 的 `yyyy-MM-dd HH:mm:ss` 不一致 | 复跑 `ProbeDate`：`A factory : {"dt":"2026-10-29T13:05:07"}`、`B builder+mixin` 同样 ISO、**只有 `C configOverride` / `D 属性级` 输出正确** ⇒ 评审正确。**这是升级时就存在的既存缺陷，非本次改动引入** | 已改为 `withConfigOverride(LocalDateTime.class, o -> o.setFormat(JsonFormat.Value.forPattern(...)))`，`LocalDate` 同理；删除两个 mixin 类 |
| 2（低） | `MapContentNonNullIntrospector` 会覆盖业务显式声明的 `@JsonInclude(content=...)` | 逻辑核对成立 | 已加判断：`contentInclusion` 非 `USE_DEFAULTS` 时不覆盖 |
| 3（低） | `writeInternal` 命中 1/2 档时丢弃构造期注入的 mapper（RestTemplate 场景），并绕过父类 `customizeWriter` | 成立 | 已加 `jsonResultTypeAware` 标志（无参构造 true / 显式 mapper 构造 false），并在写之前调用 `customizeWriter(writer, javaType, contentType)` |

**整改后复验**

| 验证 | 结果 |
|---|---|
| `mvn -o -pl scm-common,scm-framework -am compile -DskipTests` | BUILD SUCCESS |
| `mvn -o compile -DskipTests`（全仓 26 模块） | **BUILD SUCCESS** |
| `ProbeDate`（日期） | `A factory : {"dt":"2026-10-29 13:05:07"}`、root LDT 同 ⇒ 已恢复旧口径 |
| `ProbeDate2`（四种日期类型 × 四个 mapper） | `LocalDateTime`/`Date`/`Timestamp` 均 `yyyy-MM-dd HH:mm:ss`，`LocalDate` 为 `yyyy-MM-dd`；三档与 legacy mapper 全部一致 |
| `Verify`（三档 null） | NORMAL / STRING_EMPTY / NULL_NOT_OUT 输出与 Probe4 基准**逐字段一致**；legacy `mvcMapper()` 仍全部输出 null（未受影响） |

**遗留（待用户决定）**

| 项 | 说明 |
|---|---|
| 日期格式修复的影响面 | 修复前 MVC 出口的 `LocalDateTime` 实际输出 `2026-10-29T13:05:07`（ISO），修复后回到 `2026-10-29 13:05:07`。若前端已按 ISO 适配过，需回归确认；按"与旧系统一致"的口径，修复后才是正确值 |
| 接口级 JSON diff（§4 七条） | 仍需启动应用取 token，属 E7，未做 |
