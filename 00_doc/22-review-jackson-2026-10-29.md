# 22 · 技术审核：`20-JSON出口null兼容与fastjson2边界治理` §2.3 / §2.4 / §3

- 审核范围：§2.3（null 兜底序列化器）、§2.4（循环引用护栏）、§3 E1~E4；只读审核，未改动任何代码/文档/pom/DB。
- 取证方式：`javap -p/-c` 直读 jar 字节码（`D:\2025_project\99_tools\jdk\jdk-21.0.10\bin\javap.exe`）。
  证据 jar：`tools/jackson/core/jackson-databind/3.1.7`、`jackson-core/3.1.7`、`org/springframework/spring-web/7.0.10`。

| 编号 | 审核点 | jar/源码证据 | 结论 | 建议改法 |
|---|---|---|---|---|
| A1 | `ValueSerializerModifier#modifySerializer` 第二参是 `BeanDescription.Supplier` 还是 `BeanDescription` | `javap ValueSerializerModifier`：`modifySerializer(SerializationConfig, BeanDescription$Supplier, ValueSerializer<?>) → ValueSerializer<?>` | 正确 | 无需改 |
| A2 | 是否存在 `BeanSerializer#properties()`，草案 `BeanPropertyWriter[] props = ((BeanSerializer)s).properties()` 能否编译 | `javap BeanSerializer` 无 `properties()`；父类 `ser.bean.BeanSerializerBase.properties()` 返回 **`java.util.Iterator<PropertyWriter>`**（`_props` 为 `protected final BeanPropertyWriter[]`，包外不可读） | 需修正（编译失败） | 弃用 `modifySerializer`+强转，改写 `public List<BeanPropertyWriter> changeProperties(SerializationConfig, BeanDescription.Supplier, List<BeanPropertyWriter> props)`——入参直接就是可变 writer 列表，返回后即生效 |
| A3 | `BeanPropertyWriter#hasNullSerializer()` / `#assignNullSerializer(...)` / `#getType()` 是否存在及参数 | 三者均存在：`hasNullSerializer():Z`、`assignNullSerializer(ValueSerializer<Object>):V`、`getType():JavaType`；`assignNullSerializer` 字节码：`_nullSerializer` 非空且非同一实例时抛 `IllegalStateException("Cannot override _nullSerializer…")` | 正确（`hasNullSerializer()` 护栏是必需项，不能删） | 保留 `if (w.hasNullSerializer()) continue;`，并注释“重复 assign 会抛 IllegalStateException” |
| A4 | `ValueSerializer#serialize(Object, JsonGenerator, SerializationContext)` 签名、是否需 throws | `ValueSerializer.serialize(T, JsonGenerator, SerializationContext) throws JacksonException`；`JacksonException extends java.lang.RuntimeException`；`JsonGenerator.writeBoolean/writeString` 亦 `throws JacksonException` | 正确（不写 throws 可编译；`SerializerProvider` 已更名 `SerializationContext`） | 无需改；可选在 catch 时用 `JacksonException` 而非 `IOException` |
| A5 | `SimpleModule#setSerializerModifier` 是否存在；`Module` 是否改名；`addModule` 可用性 | `SimpleModule extends tools.jackson.databind.JacksonModule`，`setSerializerModifier(ValueSerializerModifier)` 存在；`tools.jackson.databind.Module` 已不存在（改名 `JacksonModule`）；`MapperBuilder.addModule(JacksonModule)`/`addModules(...)` 存在 | 正确 | 无需改；注意勿把 `Module` 写成 import，写明 `JacksonModule`/`SimpleModule` |
| A6 | `FAIL_ON_SELF_REFERENCES` / `WRITE_SELF_REFERENCES_AS_NULL` 是否存在、差异、对 A→B→A 是否有效 | 两个枚举值均存在；`BeanPropertyWriter.serializeAsProperty/_handleSelfReference` 仅在**属性值 == 被序列化 bean 实例（`if_acmpne` 身份比较）**时才报 `"Direct self-reference leading to cycle"` 或写 null | 正确（判断成立：A→B→A 不受保护，仍 StackOverflowError→500） | 保留 §2.4 结论；文档需显式写“该 feature 仅覆盖直接自引用”，A→B→A 只能 `@JsonIdentityInfo`/拆 DTO |
| A7 | 直接 `assignNullSerializer` 是否污染全局缓存 / 线程安全 | `modifySerializer`/`changeProperties` 在 `BeanSerializerFactory` 构建期调用，产物写入 mapper 级 `SerializerCache`；writer 在此期间可安全变更，发布后无写入路径 | 有风险但可接受（mapper 私有、构造后不可变；每个 `NullCompat` 档独立 mapper，不串味） | 在类注释声明“仅在 mapper 构建期改 writer，运行期只读”；禁止在运行期对已缓存的 BeanSerializer 调 `assignNullSerializer` |
| A8 | `Page<>`/`JsonResultAo<>` 等包装类的**嵌套元素**是否也被改 | 集合元素序列化器在 contextualize 期经 `SerializationContext.findContentValueSerializer(...)` 解析，元素类型各自走 `BeanSerializerFactory.createSerializer` → `_createSerializer2`，构建末尾统一遍历 `SerializerFactoryConfig.serializerModifiers()` | 正确（嵌套 bean 会被调用） | 无需改；验收用例补：`JsonResultAo<Page<Vo>>` 内层 Vo 的 null Boolean |
| A9 | 是否影响 `NULL_NOT_OUT` 分支 | 草案 `MAPPER_NON_NULL = mvcMapper(NON_NULL, NullCompat.NONE)`，`NONE` 不 addModule → 该 mapper 无 modifier | 正确（完全不受影响） | 无需改；建议加注释“NULL_NOT_OUT 靠 inclusion 省略，不挂 null 序列化器” |
| A10 | 是否影响 `RestTemplateConfiguration` 对外报文 | `scm-framework/.../config/resttemplate/RestTemplateConfiguration.java:57-58` 显式 `new CallbackMappingJackson2HttpMessageConverter(ScmJsonMapperFactory.mvcMapper())`；旧重载委托 `NullCompat.NONE` | 正确（结论成立；行号应写 `:57-58`，非 `:57-67`） | 修正文档行号引用 |
| A11 | `@JsonInclude(NON_NULL)` 与 `assignNullSerializer` 优先级是否冲突 | `PropertyBuilder` 中 `Include.NON_NULL → _suppressNulls=true`；`BeanPropertyWriter.serializeAsProperty` 先判 `_suppressableValue!=null && includeFilterSuppressNulls(...)` → 直接 return，之后才判 `_nullSerializer` | 正确（不冲突：被抑制的属性根本走不到 null 序列化器） | 无需改 |
| B1 | 转换器 `writeInternal` 草案签名与 `getJavaType` 是否可用 | `spring-web-7.0.10 AbstractJacksonHttpMessageConverter`：`writeInternal(Object, ResolvableType, HttpOutputMessage, Map) throws IOException, HttpMessageNotWritableException`、`getJavaType(Type, Class):JavaType` 均存在 | 有风险 | `ResolvableType type` 可为 `null`，草案 `type.getType()` 会 NPE：改 `Type t = (type != null ? type.getType() : object.getClass());`，并保留 `super.writeInternal` 兜底 |
| B2 | 默认构造从 `mvcMapper()` 换成 `MAPPER_NORMAL` 的全局影响 | `ActionInterceptorConfig.java:83` 使用无参构造 → 全部 MVC 出口改走 NORMAL 档 | 有风险（预期内，但影响面大于 JsonResultAo） | §4 diff 必须覆盖：Spring 默认 `/error` 输出体、参数校验错误体、非 `JsonResultAo` 包装的 Controller 返回值 |
| B3 | 与 fastjson2 的对齐缺口 | fastjson2 2.0.66 `JSONWriter$Feature` 实测含 `WriteNullBooleanAsFalse/WriteNullStringAsEmpty`；Jackson 侧 modifier 只能改“声明类型为 Boolean/String 的 bean 属性” | 有风险（未完全对齐） | 明确不覆盖：`Map<String,Object>` 的 null 值、声明为 `Object` 的字段、`List<Boolean>` 集合内的 null 元素；在 §2.3 行为矩阵下补“不支持范围”说明 |
| C1 | §3 执行顺序 E1~E4 | E1 仅 `mvn compile` 即可拦住 A2 的编译错误；E2 旧重载 `mvcMapper()` / `mvcMapper(Include)` 已存在，新增双参重载无歧义 | 需修正 | E1 改为“实现 `changeProperties` 版本”，并新增 `NullCompatSerializerModifierTest`（null Boolean→false、null String→""、NON_NULL 不输出、已有 nullSerializer 不被覆盖）；E4 的 `FAIL_ON_SELF_REFERENCES` 建议与 E1 解耦、独立开关，避免 E4 失败回滚 E1~E3 |

## verdict

needs_revision

（阻塞项：A2 草案代码编译不过；其余为可修正的风险项与验收补强项。A1/A3/A4/A5/A6/A8/A9/A10/A11 结论正确。）
