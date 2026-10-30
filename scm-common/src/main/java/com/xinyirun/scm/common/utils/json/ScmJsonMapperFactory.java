package com.xinyirun.scm.common.utils.json;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.cfg.MapperConfig;
import tools.jackson.databind.introspect.Annotated;
import tools.jackson.databind.introspect.JacksonAnnotationIntrospector;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.Map;

/**
 * SCM 统一 Jackson 3 mapper 工厂（HTTP 出/入站、MQ 生产/消费共用）。
 *
 * <p>背景：升级到 Spring Boot 4 / Jackson 3 后，日期默认输出 ISO-8601
 * （{@code 2026-10-28T19:37:10}），而升级前 fastjson2 输出
 * {@code 2026-10-28 19:37:10}。为满足“前端零改动”的约束，这里统一把
 * 出站日期固定回 {@code yyyy-MM-dd HH:mm:ss}，同时让入站反序列化同时兼容
 * 旧格式（空格分隔）、ISO-8601（含 Z 的偏移量）以及数字时间戳，避免存量前端、
 * 第三方调用方与 MQ 存量消息（Jackson2 默认写的是 epoch 数字）解析失败。</p>
 *
 * <p>线程安全：{@link JsonMapper} 构建后不可变，可作为单例复用。</p>
 *
 * @author zhangxh
 */
public final class ScmJsonMapperFactory {

    /** 出站日期时间格式（与升级前 fastjson2 一致）。 */
    public static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    /** 出站日期格式（与升级前 fastjson2 一致）。 */
    public static final String DATE_PATTERN = "yyyy-MM-dd";

    private ScmJsonMapperFactory() {
    }

    /**
     * 默认 mapper：输出 null 字段（等价升级前 fastjson2 的 WriteMapNullValue）。
     */
    public static JsonMapper mvcMapper() {
        return mvcMapper(JsonInclude.Include.ALWAYS);
    }

    /**
     * 可指定 null 字段包含策略的 mapper（空值替换档位为 {@link NullCompat#NONE}）。
     *
     * @param inclusion 包含策略，传 {@link JsonInclude.Include#NON_NULL} 即“不输出 null 字段”
     */
    public static JsonMapper mvcMapper(JsonInclude.Include inclusion) {
        return mvcMapper(inclusion, NullCompat.NONE);
    }

    /**
     * 指定 null 字段包含策略 + 空值替换档位的 mapper。
     *
     * @param inclusion  包含策略
     * @param nullCompat 空值替换档位，对应升级前 fastjson2 的三类 json_result_type
     */
    public static JsonMapper mvcMapper(JsonInclude.Include inclusion, NullCompat nullCompat) {
        SimpleModule dateModule = new SimpleModule("scm-lenient-date");
        dateModule.addDeserializer(LocalDateTime.class, new LenientLocalDateTimeDeserializer());
        dateModule.addDeserializer(Date.class, new LenientDateDeserializer());

        JsonMapper.Builder builder = JsonMapper.builder()
                .changeDefaultPropertyInclusion(v -> v.withValueInclusion(inclusion))
                .defaultDateFormat(new SimpleDateFormat(DATE_TIME_PATTERN))
                // LocalDateTime / LocalDate 的格式必须用 configOverride 设置：
                // Jackson 3.1.7 实测 @JsonFormat 的「类级 mixin」对 java.time 类型不生效
                // （只有属性级注解与 configOverride 生效），用 mixin 会退回 ISO-8601。
                .withConfigOverride(LocalDateTime.class,
                        o -> o.setFormat(JsonFormat.Value.forPattern(DATE_TIME_PATTERN)))
                .withConfigOverride(LocalDate.class,
                        o -> o.setFormat(JsonFormat.Value.forPattern(DATE_PATTERN)))
                .addModule(dateModule);

        if (nullCompat != NullCompat.NONE) {
            builder.addModule(NullCompatSerializerModifier.module(
                    nullCompat == NullCompat.BOOLEAN_FALSE_STRING_EMPTY));
        }
        // 升级前 fastjson2 在不带 WriteMapNullValue 的档位里会把 Map 的 null value 整条省略，
        // 这里用属性级 contentInclusion=NON_NULL 对齐；只作用于 Map 属性，不影响 bean 属性与集合元素。
        if (inclusion == JsonInclude.Include.NON_NULL) {
            builder.annotationIntrospector(new MapContentNonNullIntrospector());
        }
        return builder.build();
    }

    /**
     * 空值替换档位（与升级前 fastjson2 的三类 json_result_type 一一对应）。
     */
    public enum NullCompat {

        /** 不替换任何 null（升级前 Jackson2 行为；RestTemplate / MQ 等出站共用）。 */
        NONE,

        /** null Boolean → false（等价 WriteNullBooleanAsFalse，NORMAL 档）。 */
        BOOLEAN_FALSE,

        /** null Boolean → false 且 null String → ""（等价 STRING_EMPTY_BOOLEAN_FALSE 档）。 */
        BOOLEAN_FALSE_STRING_EMPTY
    }

    /**
     * 给 Map 类型属性加 {@code contentInclusion = NON_NULL}，用于省略 Map 里的 null value。
     *
     * <p>只处理属性（bean 字段），不触碰集合元素；其余注解处理全部沿用
     * {@link JacksonAnnotationIntrospector}。</p>
     */
    private static final class MapContentNonNullIntrospector extends JacksonAnnotationIntrospector {

        @Override
        public JsonInclude.Value findPropertyInclusion(MapperConfig<?> config, Annotated annotated) {
            JsonInclude.Value inclusion = super.findPropertyInclusion(config, annotated);
            JavaType type = (annotated == null) ? null : annotated.getType();
            if (type == null || !Map.class.isAssignableFrom(type.getRawClass())) {
                return inclusion;
            }
            if (inclusion == null) {
                return JsonInclude.Value.construct(JsonInclude.Include.ALWAYS, JsonInclude.Include.NON_NULL);
            }
            // 业务已显式声明 contentInclusion 时不动它，避免覆盖业务意图
            JsonInclude.Include declaredContent = inclusion.getContentInclusion();
            if (declaredContent != null && declaredContent != JsonInclude.Include.USE_DEFAULTS) {
                return inclusion;
            }
            return inclusion.withContentInclusion(JsonInclude.Include.NON_NULL);
        }
    }

    /**
     * 宽松 LocalDateTime 反序列化：兼容 {@code yyyy-MM-dd HH:mm:ss}、
     * {@code yyyy-MM-dd'T'HH:mm:ss[.SSS]}、带时区偏移的 ISO-8601、纯日期、数字 epoch 毫秒。
     */
    public static class LenientLocalDateTimeDeserializer extends ValueDeserializer<LocalDateTime> {
        @Override
        public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) {
            if (p.hasToken(JsonToken.VALUE_NUMBER_INT) || p.hasToken(JsonToken.VALUE_NUMBER_FLOAT)) {
                return Instant.ofEpochMilli(p.getLongValue()).atZone(ZoneId.systemDefault()).toLocalDateTime();
            }
            String text = p.getValueAsString();
            if (text == null) {
                return null;
            }
            text = text.trim();
            if (text.isEmpty()) {
                return null;
            }
            try {
                return LocalDateTime.parse(text.replace(' ', 'T'));
            } catch (DateTimeParseException ignored) {
                // 继续尝试下一种格式
            }
            try {
                return OffsetDateTime.parse(text).toLocalDateTime();
            } catch (DateTimeParseException ignored) {
                // 继续尝试下一种格式
            }
            try {
                return Instant.parse(text).atZone(ZoneId.systemDefault()).toLocalDateTime();
            } catch (DateTimeParseException ignored) {
                // 继续尝试下一种格式
            }
            try {
                return LocalDate.parse(text).atStartOfDay();
            } catch (DateTimeParseException ignored) {
                // 全部失败
            }
            throw new IllegalArgumentException("无法解析为日期时间：" + text);
        }
    }

    /**
     * 宽松 java.util.Date 反序列化：兼容 ISO-8601（含 Z）、{@code yyyy-MM-dd HH:mm:ss}、
     * 纯日期、数字 epoch 毫秒。
     */
    public static class LenientDateDeserializer extends ValueDeserializer<Date> {
        @Override
        public Date deserialize(JsonParser p, DeserializationContext ctxt) {
            if (p.hasToken(JsonToken.VALUE_NUMBER_INT) || p.hasToken(JsonToken.VALUE_NUMBER_FLOAT)) {
                return new Date(p.getLongValue());
            }
            String text = p.getValueAsString();
            if (text == null) {
                return null;
            }
            text = text.trim();
            if (text.isEmpty()) {
                return null;
            }
            try {
                return Date.from(OffsetDateTime.parse(text).toInstant());
            } catch (DateTimeParseException ignored) {
                // 继续尝试下一种格式
            }
            try {
                return Date.from(Instant.parse(text));
            } catch (DateTimeParseException ignored) {
                // 继续尝试下一种格式
            }
            try {
                return Date.from(LocalDateTime.parse(text.replace(' ', 'T'))
                        .atZone(ZoneId.systemDefault()).toInstant());
            } catch (DateTimeParseException ignored) {
                // 继续尝试下一种格式
            }
            try {
                return Date.from(LocalDate.parse(text).atStartOfDay(ZoneId.systemDefault()).toInstant());
            } catch (DateTimeParseException ignored) {
                // 全部失败
            }
            throw new IllegalArgumentException("无法解析为日期：" + text);
        }
    }
}
