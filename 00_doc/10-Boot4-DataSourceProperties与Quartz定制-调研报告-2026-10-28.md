# Boot 4 下 `DataSourceProperties` 缺失与 Quartz 定制的正确改法 —— 调研报告

> **调研对象**：① `org.springframework.boot.jdbc.autoconfigure.DataSourceProperties` 在 Boot 4.1.2 里由谁注册、什么条件下注册；② Boot 4 的 Quartz 如何获得 `DataSource` / `PlatformTransactionManager`；③ 本项目 `ScheduleConfig` 应当怎么改。
>
> **起因报错**（`D:\temp\app-boot-20261028-1845.log`，12994 行之外的实跑日志）：
> ```
> Field dataSourceProperties in com.xinyirun.scm.quartz.config.ScheduleConfig required a
> bean of type 'org.springframework.boot.jdbc.autoconfigure.DataSourceProperties' that could not be found.
> ```
> 触发点 `QuartzAutoConfiguration.quartzScheduler(QuartzAutoConfiguration.java:74)`。
>
> **证据等级**：官方文档 / 官方 API 文档 / 依赖 jar 字节码 = 一级（本文所有结论均由这三类支撑）；GitHub issue + StackOverflow = 二级（用于交叉印证）。
> **调研时间**：2026-10-28　**方法论**：context7 官方文档 → 官方 API 文档 → 依赖 jar `javap -v` 字节码 → GitHub issue / 社区实证 → 本项目仓库实读。不使用记忆与推测。

---

## 0. 修正记录（重要，先读）

> 本报告初版（2026-10-28 19:0x）曾写「项目里的 `SchedulerFactoryBeanCustomizer` 从未生效、被 Boot 的 customizer 覆盖」。**该结论是错的**，已用 `javap -v` 对比 Boot 3.5.5 与 4.1.2 两个版本推翻：

| 证据 | Boot 3.5.5（`spring-boot-autoconfigure-3.5.5.jar`） | Boot 4.1.2（`spring-boot-quartz-4.1.2.jar`） |
|---|---|---|
| `dataSourceCustomizer` 上的注解 | `@Bean` + **`@Order(value = 0)`** | `@Bean` + **`@Order(value = 0)`** |
| 参数 2 注解 | `@QuartzDataSource` | `@QuartzDataSource` |
| 参数 4 注解 | `@QuartzTransactionManager` | `@QuartzTransactionManager` |

模板项目用的是 `@Order(1)`，`1 > 0` ⇒ **项目自定义的 customizer 排在 Boot 之后执行，最后一个 `setDataSource` 生效**。
⇒ **项目从 Boot 2.0.6 之后一直用的是自建独立连接池**，不是主数据源。删掉它 = 真实的行为变更。

这个 `@Order(0)` 来自官方 issue **#12780**（2018-04-05，`SchedulerFactoryBeanCustomizer` 被 Quartz 自动配置覆盖）的三条整改项之一："Order Boot's `SchedulerFactoryBeanCustomizer` so that users' customizers can be positioned before and after it"，里程碑 2.0.6，已关闭。

---

## 1. 接口规范

### 1.1 `DataSourceProperties` 由谁注册 —— 官方 API 文档

`org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration`（**Since 4.0.0**；Boot 4 把 JDBC 自动配置从 `org.springframework.boot.autoconfigure.jdbc` 迁到 `org.springframework.boot.jdbc.autoconfigure`）：

```java
@AutoConfiguration(before = DataSourceInitializationAutoConfiguration.class)
@ConditionalOnClass({ DataSource.class, EmbeddedDatabaseType.class })
@ConditionalOnMissingBean(type = "io.r2dbc.spi.ConnectionFactory")
@EnableConfigurationProperties(DataSourceProperties.class)   // ← 常规注册点，仅此一处
@Import({ DataSourcePoolMetadataProvidersConfiguration.class, DataSourceCheckupRestoreConfiguration.class })
public class DataSourceAutoConfiguration { ... }
```

**结论（一级）**：`DataSourceProperties` 不是独立自动配置产物，只由 `DataSourceAutoConfiguration` 的 `@EnableConfigurationProperties` 带出。谁排除或跳过它，谁就得自己保证这个 bean 存在。

### 1.2 本项目实际用到的 API

| 成员 | 签名 |
|---|---|
| `initializeDataSourceBuilder()` | `DataSourceBuilder<?>`（按 `spring.datasource.url/username/password/type` 组装，`type` 存在时直接用该池实现） |
| `determineUrl()` / `determineDriverClassName()` | `String` |

### 1.3 Quartz 的官方定制契约（Boot 4 `io/quartz.adoc`）

| 机制 | 类型 | 官方原文 |
|---|---|---|
| 走主库 | 无代码 | "A JDBC-based store can be configured if a `javax.sql.DataSource` bean is available and `spring.quartz.job-store-type=jdbc`" |
| 用独立库 | `@QuartzDataSource` 标注的 `DataSource` | "…declare a `DataSource` bean, annotating its `@Bean` method with `@QuartzDataSource`. Doing so ensures that the Quartz-specific `DataSource` is used by both the `SchedulerFactoryBean` and for schema initialization." |
| 独立事务管理器 | `@QuartzTransactionManager` 标注的 `PlatformTransactionManager` | 同上句式 |
| 编程式 | `SchedulerFactoryBeanCustomizer` | "`SchedulerFactoryBeanCustomizer` beans for programmatic customization" |

`@QuartzDataSource` / `@QuartzTransactionManager` 本质是 `@Qualifier` 元注解（供 `ObjectProvider` 按 qualifier 取）。

---

## 2. Boot 4.1.2 里的真实机制（字节码实证）

`QuartzAutoConfiguration$JdbcStoreTypeConfiguration`：

```java
@Bean
@Order(0)                                   // ← #12780 的整改产物
SchedulerFactoryBeanCustomizer dataSourceCustomizer(
        QuartzProperties properties,
        DataSource dataSource,                                   // 主 DataSource（无歧义时才可注入）
        ObjectProvider<DataSource> quartzDataSource,              // @QuartzDataSource 的
        ObjectProvider<PlatformTransactionManager> transactionManager,
        ObjectProvider<PlatformTransactionManager> quartzTransactionManager) {
    return factory -> {
        factory.setDataSource(getDataSource(dataSource, quartzDataSource));
        factory.setTransactionManager(getTransactionManager(transactionManager, quartzTransactionManager));
    };
}

private static DataSource getDataSource(DataSource primary, ObjectProvider<DataSource> p) {
    DataSource q = p.getIfAvailable();
    return q != null ? q : primary;          // 有 @QuartzDataSource 用它，否则用主 DataSource
}
```

`SchedulerFactoryBean` 应用 customizer 时按 `AnnotationAwareOrderComparator` **升序依次调用、后者覆盖前者** ⇒ `@Order(0)` 的 Boot 自定义器先跑，`@Order(1)` 的项目自定义器后跑并胜出。

---

## 3. 本项目为什么在 Boot 4 下炸 —— 三步链，全部实证

### 3.1 druid 1.2.28 新增了 `spring.datasource.type` 判等条件

| 版本 | 类 | 类级注解 |
|---|---|---|
| `druid-spring-boot-3-starter:1.2.20` | `com.alibaba.druid.spring.boot3.autoconfigure.DruidDataSourceAutoConfigure` | **无任何 `@ConditionalOnProperty`** → 恒生效 |
| `druid-spring-boot-4-starter:1.2.28` | `com.alibaba.druid.spring.boot4.autoconfigure.DruidDataSourceAutoConfigure` | `@ConditionalOnProperty(name="spring.datasource.type", havingValue="com.alibaba.druid.pool.DruidDataSource", matchIfMissing=true)` + `@ConditionalOnClass` + `@AutoConfigureBefore(org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration)` + `@EnableConfigurationProperties({DruidStatProperties, org.springframework.boot.jdbc.autoconfigure.DataSourceProperties})` + `@Import(4 个 stat 配置)` |

druid 上游 master 的同名类（`druid-spring-boot-starter`）**也有**这个条件，`dataSource()` 上还有 `@ConditionalOnMissingBean({DruidDataSourceWrapper, DruidDataSource, DataSource})`（源码可在 alibaba/druid 仓库核验）—— 说明这是上游有意设计，不是 boot4 专有的意外。

### 3.2 本项目 yml 让这个条件判否

`scm-start/src/main/resources/application-dev.yml`
- **L197** `type: com.alibaba.druid.pool.xa.DruidXADataSource`（全局 `spring.datasource.type`）
- **L246** 每个 dynamic-datasource 的 `type:` 同样是 `DruidXADataSource`

`DruidXADataSource` ≠ `DruidDataSource` ⇒ **druid 自动配置整体跳过** ⇒ 它的 `@EnableConfigurationProperties(DataSourceProperties.class)` 不执行。

### 3.3 最后一块拼图：项目排除了 `DataSourceAutoConfiguration`

`scm-start/.../SystemServerStart.java` 排除了 `DataSourceAutoConfiguration`（**仓库既有配置，非本次升级引入**）。

⇒ Boot 3 时代 druid 1.2.20 无条件生效、顺手把 `DataSourceProperties` 注册了，掩盖了这次排除；升级后这层兜底消失 → `ScheduleConfig` 注入失败。

---

## 4. `@QuartzDataSource`（官方首选机制）为什么在本项目**不能**直接用

这是本次调研最关键的一条负面结论，两条硬证据：

**证据 A** —— `dynamic-datasource-spring-boot4-starter:4.6.0` 的 `DynamicDataSourceAutoConfiguration.dataSource(List<DynamicDataSourceProvider>)` 上只有 `@Bean` + `@ConditionalOnMissingBean`，**没有 `@Primary`**（字节码注解块逐字核对）。

**证据 B** —— 用户自定义 `@Bean` 参与 `@ConditionalOnMissingBean` 判定时，用户 bean 先注册、自动配置后评估。

⇒ 后果：
1. 若声明一个 `@QuartzDataSource DataSource quartzDataSource` 的 bean，`@ConditionalOnMissingBean` 会把 **dynamic-datasource 的主数据源顶掉** —— 全站业务数据源消失，比现在的报错严重得多；
2. 即使不出现 1，`dataSourceCustomizer(..., DataSource dataSource, ...)` 这个**无 qualifier 的 `DataSource` 注入点**在有 2 个 `DataSource` 且无 `@Primary` 时也会抛 `NoUniqueBeanDefinitionException`。

⇒ **本项目的正确修法不是"改成 @QuartzDataSource"，而是"把缺的 `DataSourceProperties` 补上，让原有 customizer 在 Boot 4 下继续按原语义工作"。**

---

## 5. 注意事项（四个坑）

| # | 坑 | 后果 |
|---|---|---|
| 5.1 | `spring.datasource.type` 被 druid 1.2.2x 当成了**自动配置开关** | 条件判否 → `DataSourceProperties` 无人注册（本次事故） |
| 5.2 | 随意 `@EnableConfigurationProperties(DataSourceProperties.class)` 而不绑定前缀 | 它会读 `spring.datasource.*`，与 dynamic-datasource 共享同一份配置，**语义正确但归属不清**；配合 `@QuartzDataSource` 使用则触发 §4 |
| 5.3 | 为了让 druid 复活去改 `spring.datasource.type = DruidDataSource` | 会激活 druid 四件套 stat 配置（`DruidSpringAopConfiguration` / `DruidStatViewServletConfiguration` / `DruidWebStatFilterConfiguration` / `DruidFilterConfiguration`），且 `type` 是全局语义，XA 意图丢失 |
| 5.4 | 误以为 `SchedulerFactoryBeanCustomizer` 里 `setDataSource` 一定要写在 `@Order(0)` 之前 | 排序规则是**升序执行、后者覆盖**；`@Order(1)` 恰恰是"最后一个说了算" |

---

## 6. 落地代码

### 6.1 现状（Boot 3.5.5 下可运行，git HEAD 原文）

`scm-quartz/src/main/java/com/xinyirun/scm/quartz/config/ScheduleConfig.java`

```java
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;      // L7
import org.springframework.boot.autoconfigure.quartz.SchedulerFactoryBeanCustomizer; // L8

@Autowired
private DataSourceProperties dataSourceProperties;                          // L30-31 ← 报错点

@Order(1)
@Bean
public SchedulerFactoryBeanCustomizer schedulerFactoryBeanCustomizer() {     // L33-41
    DataSource dataSource = dataSourceProperties.initializeDataSourceBuilder().build();
    return schedulerFactoryBean -> {
        schedulerFactoryBean.setDataSource(dataSource);
        schedulerFactoryBean.setTransactionManager(new DataSourceTransactionManager(dataSource));
    };
}
```

> 同一文件 L92-104 的注释里还留着作者最初的设计：`org.quartz.jobStore.dataSource = wmsQuartzDS` + `DruidConnectionProvider` + 独立的 url/user/password —— **项目从一开始就想要"Quartz 专用数据源"**，这也印证"独立池"是有意为之，不能随手删。

### 6.2 推荐改法（方案 1，**行为零变化**）

只改两类：import 换包 + 补一个 `DataSourceProperties` bean。

```java
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;      // Boot 4 新包
import org.springframework.boot.quartz.autoconfigure.SchedulerFactoryBeanCustomizer; // Boot 4 新包
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Primary;   // 见下方说明，视需要

@Configuration
public class ScheduleConfig {

    /**
     * Boot 4：DataSourceAutoConfiguration 被排除、druid 4 因 spring.datasource.type=DruidXADataSource
     * 判否，两侧都不再注册 DataSourceProperties，这里显式补一个（只绑定属性，不建连接池）。
     */
    @Bean
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties quartzDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Order(1)   // 大于 Boot 的 @Order(0)，确保"最后一个说了算"
    @Bean
    public SchedulerFactoryBeanCustomizer schedulerFactoryBeanCustomizer(
            DataSourceProperties quartzDataSourceProperties) {
        DataSource dataSource = quartzDataSourceProperties.initializeDataSourceBuilder().build();
        return schedulerFactoryBean -> {
            schedulerFactoryBean.setDataSource(dataSource);
            schedulerFactoryBean.setTransactionManager(new DataSourceTransactionManager(dataSource));
        };
    }
}
```

要点：
- `DataSourceProperties` **只是属性载体，不产生连接**；连接池仍由 customizer 里 `build()` 那一次创建 —— 与升级前完全一致（一个专用 Druid XA 池）。
- `@ConfigurationProperties("spring.datasource")` 复用现有 yml，**不需要新增任何配置项**。（官方 how-to 建议 `@Bean` 上用自定义前缀"避免配置被拆散"，本项目只有一个消费方，复用 `spring.datasource` 更简洁。）
- 该 bean 名不能叫 `dataSourceProperties` 以外的冲突名；它不是 `DataSource`，不参与 `@ConditionalOnMissingBean(DataSource)` 判定，**不会顶掉 dynamic-datasource 的主数据源**。

---

## 7. 四种改法对比与取舍

| | 改法 | 与 Boot 4 官方机制的关系 | 运行时行为 | 风险 | 评价 |
|---|---|---|---|---|---|
| **方案 1（推荐）** | 补 `@ConfigurationProperties("spring.datasource") DataSourceProperties` bean，customizer 保持 `@Order(1)` | 官方 how-to（自定义 `DataSourceProperties`）+ 官方 customizer | **与升级前完全一致**：独立 Druid XA 池指向 `scm_manager` + 独立 `DataSourceTransactionManager` | 极低（只多一个属性 bean） | ✅ 零行为变化 |
| 方案 2 | 改 `@QuartzDataSource` 独立池 | 官方首选机制 | 与方案 1 等价 | **高**：会顶掉 dynamic-datasource 主数据源 / 或引发 `DataSource` 注入歧义（§4） | ❌ 不可用 |
| 方案 3 | 删掉 customizer，Quartz 用主 `DataSource` | 官方默认路径 | **行为变更**：Quartz 从独立池改用 `DynamicRoutingDataSource`；事务管理器换成主 tx manager | 中（连接池隔离消失，Quartz 与业务争连接） | 仅当确认"隔离无意义"才选 |
| 方案 4 | 改 `spring.datasource.type = DruidDataSource` 让 druid 复活 | 依赖上游条件副作用 | 间接变化 | **高**：激活 druid stat 四件套 + 全局 `type` 语义被改 + XA 意图丢失 | ❌ 不推荐 |

**推荐方案 1**，理由：
1. 与当前生产行为 **1:1 等价**（独立池、独立 tx manager），升级不该顺手改架构；
2. 用的是官方 how-to 明确记载的写法，不依赖 druid 的条件"副作用"；
3. 不引入第二个 `DataSource` bean，不触碰 dynamic-datasource 的 `@ConditionalOnMissingBean` 边界。

### 验证清单（改完必须跑）

1. `mvn -o -T 1C clean install -DskipTests -B` 全绿；
2. `java -jar scm-start/target/scm-start-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev` 出现 `Started SystemServerStart`；
3. 启动日志里 Quartz 的 `SchedulerFactoryBean` 使用的 `DataSource` 是独立 `DruidDataSource`（不是 `DynamicRoutingDataSource`）——与 Boot 3.5.5 基线日志对照；
4. 触发一次 `QRTZ_*` 相关定时任务，确认 `QRTZ_TRIGGERS`/`QRTZ_FIRED_TRIGGERS` 读写正常。

---

## 8. 引用来源

| # | 来源 | 用途 |
|---|---|---|
| 1 | https://docs.spring.io/spring-boot/reference/io/quartz.html（context7 `/spring-projects/spring-boot` `v4.1.0`） | Quartz 四种定制机制的官方表述（§1.3） |
| 2 | https://docs.spring.io/spring-boot/how-to/data-access.html | 自定义 `DataSource` / `DataSourceProperties` 的官方 how-to（§6.2） |
| 3 | https://docs.spring.io/spring-boot/reference/data/sql.html | `spring.datasource.type` 语义（§5.1） |
| 4 | https://docs.spring.io/spring-boot/api/java/org/springframework/boot/jdbc/autoconfigure/DataSourceAutoConfiguration.html | `@EnableConfigurationProperties(DataSourceProperties.class)` 唯一注册点（§1.1） |
| 5 | https://github.com/spring-projects/spring-boot/issues/12780 | 官方确认"customizer 被 Quartz 自动配置覆盖"并整改为 `@Order(0)`（§0、§2） |
| 6 | https://github.com/spring-projects/spring-boot/issues/31087 | 2.6.x 升级同类案例（customizer 与自动配置的数据源争用） |
| 7 | https://stackoverflow.com/questions/49637490/spring-boot-2-0-quartz-use-non-primary-datasource | 社区实测："data source in SchedulerConfig … has been overridden"，与该 issue 互相印证 |
| 8 | https://github.com/alibaba/druid/blob/master/druid-spring-boot-starter/src/main/java/com/alibaba/druid/spring/boot/autoconfigure/DruidDataSourceAutoConfigure.java | druid 上游源码，确认 `@ConditionalOnProperty(spring.datasource.type)` 与 `@ConditionalOnMissingBean` 均为上游设计（§3.1） |
| 9 | `spring-boot-quartz-4.1.2.jar` / `spring-boot-autoconfigure-3.5.5.jar` → `javap -p -v …QuartzAutoConfiguration$JdbcStoreTypeConfiguration` | 两版本 `@Order(0)` + `@QuartzDataSource`/`@QuartzTransactionManager` 参数注解对比（§0、§2） |
| 10 | `druid-spring-boot-4-starter-1.2.28.jar` / `druid-spring-boot-3-starter-1.2.20.jar` → `javap -p -v …DruidDataSourceAutoConfigure` | 1.2.28 新增条件、1.2.20 无（§3.1） |
| 11 | `dynamic-datasource-spring-boot4-starter-4.6.0.jar` → `javap -p -v …DynamicDataSourceAutoConfiguration` | 主数据源 bean 只有 `@ConditionalOnMissingBean`、无 `@Primary`（§4） |
| 12 | 本项目 `application-dev.yml` L183-199/L290-305、`ScheduleConfig.java`、`SystemServerStart.java` | 项目侧事实（一级，自读） |

---

## 9. 缺口声明（如实记录没查到的）

- **druid 官方说明**：alibaba/druid 的 release note 未提"1.2.2x 新增 `spring.datasource.type` 判等条件"这一变更，因此只能说"上游代码如此"，不能定性为 bug 或 regression。
- **社区同构案例**：以 `DataSourceProperties bean missing after Spring Boot 4 upgrade` 等关键词检索，未命中与本项目完全同构（排除 `DataSourceAutoConfiguration` + druid XA + dynamic-datasource 三条件叠加）的案例；§3 的因果链完全依赖本机 jar 字节码 + yml 实读 + 实跑报错。
- **`@QuartzDataSource` 与 `DynamicRoutingDataSource` 的官方交互**：官方文档未描述"容器中已存在非 `@Primary` 的第二数据源"时的行为，§4 的结论由两个字节码事实推导（`@ConditionalOnMissingBean` 顺序语义 + 无 `@Primary`），未经运行时复现；若将来确需独立 `@QuartzDataSource`，应先做一次隔离实验再定。
