# 23 · 第 3 轮复核：容器内 null 与档位设置（Jackson 3.1.7 / fastjson2 2.0.66）

复核对象：`20-JSON出口null兼容与fastjson2边界治理-2026-10-29.md` §2.3（覆盖范围 / 基准逐档对照 / 档位修正说明）、§4 第 7 条、§7 末行、§8、§9。
复核方式：自己跑探针（Lead 的 5 个 + 我新写的 2 个）+ `javap` 直读 jar。**未采信 Lead 结论，也未修改 Lead 的探针文件与项目任何代码。**

复现环境（本机离线仓库）：

```
JDK   : D:\2025_project\99_tools\jdk\jdk-21.0.10\bin\{javac,java}.exe   ($env:JAVA_TOOL_OPTIONS='-Dfile.encoding=UTF-8')
CP    : ...\tools\jackson\core\jackson-databind\3.1.7\jackson-databind-3.1.7.jar;
        ...\tools\jackson\core\jackson-core\3.1.7\jackson-core-3.1.7.jar;
        ...\com\fasterxml\jackson\core\jackson-annotations\2.21\jackson-annotations-2.21.jar
FJ2   : ...\com\alibaba\fastjson2\fastjson2\2.0.66\fastjson2-2.0.66.jar
新增探针: D:\temp\json-null-probe-r3\R3Probe.java, R3Probe2.java
```

---

## Q1 · 「fastjson2 的 WriteNullBooleanAsFalse / WriteNullStringAsEmpty 只作用于 bean 属性，Map value 与集合元素一律保留」——**成立**

证据（`java -cp "$CP;$FJ2;out" Probe3` 原文）：

```
[1] 只 WriteMapNullValue : {"b":null,"l":[null,"x"],"lb":[null,true],"m":{"a":null,"b":true},"mb":{"x":null,"y":true},"n":null,"s":null}
[2] WriteMapNullValue + NullString/Boolean/List/Number :
    {"b":false,"l":[null,"x"],"lb":[null,true],"m":{"a":null,"b":true},"mb":{"x":null,"y":true},"n":0,"s":""}
```

即：全特性打开时 `Map<String,Boolean> mb` 的 `x:null` 仍是 `null`、`List<Boolean> lb` 的 `null` 元素仍保留、`Map` 的 `a:null` 仍保留；只有 bean 属性 `b/s/n` 被替换。

证据（`java ... Probe4`，严格按基线三档 feature 组装，原文）：

```
[A] NORMAL   : {"b":false,"l":null,"le":[null,true],"m":null,"mv":{"a":null,"b":true},"n":null,"o":null,"s":null}
[B] STRING_EMPTY : {"b":false,"le":[null,true],"mv":{"b":true},"s":""}
[C] NULL_NOT_OUT : {"le":[null,true],"mv":{"b":true}}
```

⇒ §2.3「基准真值」表三行与我复现逐字一致。结论 1 成立，且 `le[0]=null` 三档均保留 ⇒ 「集合元素 null 新旧有差异」确实不成立。

基线特性列表也复核过（`git show 6e89d9b:.../CallbackMappingJackson2HttpMessageConverter.java`，第 80-142 行）：NORMAL=`ReferenceDetection+WriteMapNullValue+WriteNullBooleanAsFalse+PrettyFormat`、STRING_EMPTY=`ReferenceDetection+WriteNullStringAsEmpty+WriteNullBooleanAsFalse+PrettyFormat`、NULL_NOT_OUT=`ReferenceDetection+PrettyFormat`；`WriteNullListAsEmpty` / `WriteNullNumberAsZero` 在源码中**是注释掉的** ⇒ 与本文件 §9 第 5 条一致，**成立**。

---

## Q2 · 「Include.NON_NULL 的抑制不优先于 assignNullSerializer」——**成立（但属实现细节，必须用单测锁死）**

证据（`java -cp "$CP;out" Probe5` 原文）：

```
[1] NON_NULL 无修饰          : {"b":null,"l":null,"le":[null,true],"m":null,"mv":{"a":null,"b":true},"n":null,"o":null,"s":null}
[2] NON_NULL + 修饰(nullSer) : {"b":false,"le":[null,true],"mv":{"a":null,"b":true},"s":""}
```

我自写探针 `R3Probe[I]` 独立复现，结论相同：

```
[I] NON_NULL + Mod(无 content 处理) : {"b":false,"le":[null,true],"mv":{"a":null,"b":true},"mvb":{"x":null,"y":true},"s":""}
```

机制证据（为什么能成立）：`javap -c PropertyBuilder` 的 `Include` switch 只对 `NON_EMPTY/NON_ABSENT` 等写 `_suppressableValue`，`NON_NULL` 仅置 `suppressNulls=true`（不写 `_suppressableValue`）；而 `javap -c BeanPropertyWriter.serializeAsProperty` 的空值分支顺序是：

```
13: getfield _suppressableValue ; 16: ifnull 31
21: getfield _suppressableValue ; 24: invokevirtual SerializationContext.includeFilterSuppressNulls ; 27: ifeq 31 ; 30: return
32: getfield _nullSerializer    ; 35: ifnull 57      ← 走到这里才会判 null 序列化器
48: getfield _nullSerializer
```

⇒ NON_NULL 下 `_suppressableValue==null`，第一个 return 不触发，于是落到 `_nullSerializer` 分支 → 输出 `false`/`""`。
**这正是「抑制不优先」的成因，属 Jackson 内部实现细节（一旦上游改了 `_suppressableValue` 的赋值策略就会翻车），因此 §2.3 必须配一条单测锁死该行为，不能只靠文档口述。**

---

## Q3 · 「STRING_EMPTY 档 = Include.NON_NULL + nullSerializer(Boolean→false, String→"")」——**成立；原 `Include.ALWAYS` 确为错**

证据（`java ... R3Probe` 原文，`[F]` 是完整档位拼装）：

```
[F] STRING_EMPTY 组合(NON_NULL+Mod+intro) : {"b":false,"le":[null,true],"mv":{"b":true},"mvb":{"y":true},"s":""}
```

与基准 `Probe4[B]`（`{"b":false,"le":[null,true],"mv":{"b":true},"s":""}`）**逐字一致**（`mvb` 是我探针多出的字段）。
同探针对照 `[H]`（NORMAL 档 = ALWAYS + 仅 Boolean→false）与基准 `Probe4[A]` 逐字一致 ⇒ 分档实现正确：

```
[H] NORMAL 组合(ALWAYS+Mod(false only)) : {"b":false,"l":null,"le":[null,true],"m":null,"mv":{"a":null,"b":true},"mvb":{"x":null,"y":true},"n":null,"o":null,"s":null}
[G] NULL_NOT_OUT 组合(NON_NULL+intro)   : {"le":[null,true],"mv":{"b":true},"mvb":{"y":true}}
```

`[G]` 与基准 `Probe4[C]` 逐字一致 ⇒ NULL_NOT_OUT 档同样闭合。

---

## Q4 · 「Map value 的两条修法」——**API 都存在，但 `withConfigOverride(Map.class)` 不等价，只能保留 introspector 一条路径（需修正文档）**

(a) API 存在性（`javap` 原文，全部命中）：

```
javap -cp jackson-databind-3.1.7.jar tools.jackson.databind.cfg.MutableConfigOverride
  public MutableConfigOverride setInclude(com.fasterxml.jackson.annotation.JsonInclude$Value);
  public MutableConfigOverride setIncludeAsProperty(com.fasterxml.jackson.annotation.JsonInclude$Value);
javap -cp jackson-databind-3.1.7.jar tools.jackson.databind.cfg.MapperBuilder
  public B withConfigOverride(java.lang.Class<?>, java.util.function.Consumer<tools.jackson.databind.cfg.MutableConfigOverride>);
javap -cp jackson-databind-3.1.7.jar tools.jackson.databind.AnnotationIntrospector
  public com.fasterxml.jackson.annotation.JsonInclude$Value findPropertyInclusion(MapperConfig<?>, Annotated);
javap -cp jackson-annotations-2.21.jar 'com.fasterxml.jackson.annotation.JsonInclude$Value'
  public static JsonInclude$Value construct(JsonInclude$Include, JsonInclude$Include);   // (valueInclusion, contentInclusion)
  public JsonInclude$Value withContentInclusion(JsonInclude$Include);
```

⇒ 「`setIncludeAsProperty` 收 `JsonInclude$Value`、而 `construct(value, content)` 能表达 content inclusion」这一点**成立**（`withConfigOverride` 的参数是 `Consumer`，文档示例用的 lambda 也正确）。

(b) **但 `withConfigOverride(Map.class, …)` 只对「声明类型恰为 `Map`」的属性生效**，对 `HashMap`/`TreeMap` 声明、对 `List<Map>` 的元素都无效 —— 证据（`java ... R3Probe2` 原文）：

```
[A] default                       : {"asHashMap":{"a":null,"b":1},"asMap":{"a":null,"b":1},"asTree":{"a":null,"b":1},"inner":{"ib":null,"is":null},"listOfMap":[{"a":null,"b":1}],"mapOfBean":{"k":{"ib":null,"is":null}}}
[C] configOverride(Map.class)     : {"asHashMap":{"a":null,"b":1},"asMap":{"b":1},"asTree":{"a":null,"b":1},"inner":{"ib":null,"is":null},"listOfMap":[{"a":null,"b":1}],"mapOfBean":{"k":{"ib":null,"is":null}}}
```

`asMap` 生效、`asHashMap`/`asTree`/`listOfMap` 均**未生效**。补充（`R3Probe`）：

```
[D] withConfigOverride(LinkedHashMap) : {"mv":{"a":null,"b":true}, ...}   ← 连具体实现类都挂不上
[E] withConfigOverride(Map) setInclude: {"mv":{"b":true}, ...}            ← 与 [C] 同效
```

⇒ 文档 §2.3「Map value 对齐的修法」表把两条并列写成等价手段，**不成立**；`withConfigOverride` 若要覆盖全仓需为每个具体 Map 类 + 每个泛型容器元素逐个配，不可行。

(c) **introspector 路径真实有效且覆盖面正确** —— 证据（`R3Probe2` 原文）：

```
[B] introspector content=NON_NULL : {"asHashMap":{"b":1},"asMap":{"b":1},"asTree":{"b":1},"inner":{"ib":null,"is":null},"listOfMap":[{"b":1}],"mapOfBean":{"k":{"ib":null,"is":null}}}
```

三种声明（`Map`/`HashMap`/`TreeMap`）、`List<Map>` 内元素全部省略 null value，且**未误伤 bean 属性 null**（`inner.ib` 仍为 `null`，因为只改 content inclusion 不动 value inclusion）。

(d) 组合后与基准对齐：见 Q3 的 `[F]`/`[G]` 与 `Probe4[B]`/`[C]` 逐字一致 ⇒ 「残留唯一差异可用 content inclusion 抹平」**成立**。

应改成什么：

- 删掉 `withConfigOverride(Map.class, …)` 这条并列方案，或明确标注「仅对声明类型为 `Map` 的属性生效，不可作为等效实现」；主实现保留 `AnnotationIntrospector.findPropertyInclusion` 路径，并用 `base.withContentInclusion(NON_NULL)`（保留原 value inclusion）。
- 自建 introspector 必须 `extends JacksonAnnotationIntrospector` 且 `super.findPropertyInclusion(...)` 结果必须保留，否则会丢业务注解（`base==null` 时用 `JsonInclude.Value.empty()`）。
- 补一条验收：`Map`/`HashMap`/`TreeMap` 三种声明 + `List<Map>` 元素在 STRING_EMPTY、NULL_NOT_OUT 两档都必须省略 null value；NORMAL 档必须保留（`R3Probe[H]` 已给出基准）。

---

## Q5 · 文档写「给 Map/Collection 属性挂 contentInclusion=NON_NULL」——**Collection 必须排除**

证据（`java -cp "$CP;out" Probe2` 原文）：

```
[A] default                                : {"l":[null,"x"],"l2":[null,"x"],"m":{"b":1},"m2":{"a":null,"b":1}}
[B] APPLY_JSON_INCLUDE_FOR_CONTAINERS(全局) : {"l":["x"],"l2":[null,"x"],"m":{"b":1},"m2":{"a":null,"b":1}}
```

`List` 上的 `content=NON_NULL` 在默认配置下不生效（元素 null 保留），只有开启 `SerializationFeature.APPLY_JSON_INCLUDE_FOR_CONTAINERS` 才会删元素。基准三档都保留集合元素 null（`le[0]=null`），因此：

- 现在给 Collection 挂 content NON_NULL 是**休眠的偏差**：今天不生效，将来谁开了 `APPLY_JSON_INCLUDE_FOR_CONTAINERS` 就会让 `le[0]` 消失，与基准不符。
- 应改成：introspector 里只判 `Map.class.isAssignableFrom(raw)`，**不要**判 `Iterable`/集合，并在注释写明「集合元素 null 基准保留，故不得注入 content NON_NULL」。

---

## Q6 · 其它需在文档落地的两处小修正

1. §7 末行「~~NON_NULL 抑制优先~~ → 实测推翻」表述正确，但建议补一句成因（`_suppressableValue` 只在 NON_EMPTY 类分支被写入），否则读者无法判断该结论的稳定性（见 Q2）。
2. 自建 introspector 覆盖**所有** `Map` 子类，包括 fastjson2 `JSONObject`（`extends LinkedHashMap`）等第三方 Map；这与基准一致（基准同档对所有 Map 都省略 null value），但要写进 §4 #7 的验收口径，避免上线后被当成「新 bug」。

---

## verdict

needs_revision

- 成立的：Q1（fastjson2 特性只作用 bean 属性）、Q2（nullSerializer 优先于 NON_NULL 抑制）、Q3（STRING_EMPTY 档 = NON_NULL + nullSerializer；ALWAYS 为错）、Q4(d)（introspector 可抹平 Map value 差异，且组合输出与基准三档逐字一致）。
- 必须修：§2.3 把 `withConfigOverride(Map.class, …)` 当作与 introspector 等价的方案（Q4b，实测只对声明类型恰为 `Map` 的属性生效）；§2.3 的「Map/Collection」表述需把 Collection 排除（Q5）；Q2 的行为需单测锁死。
