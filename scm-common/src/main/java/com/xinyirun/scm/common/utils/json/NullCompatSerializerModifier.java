package com.xinyirun.scm.common.utils.json;

import java.util.List;

import tools.jackson.core.JacksonException;
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
 * 用 Jackson 3 复刻 fastjson2 的空值特性：null Boolean → {@code false}、null String → {@code ""}（可选）。
 *
 * <p>背景：Jackson 3 没有 {@code WriteNullBooleanAsFalse} / {@code WriteNullStringAsEmpty} 等价开关，
 * 写出阶段已丢失字段类型信息。但属性类型在构建 BeanSerializer 时是已知的，因此可以为单个属性挂
 * 「null 序列化器」（{@link BeanPropertyWriter#assignNullSerializer}），把 null 精确替换成按类型定值。</p>
 *
 * <p>作用面（实测结论）：只作用于 <b>bean 属性</b>；{@code Map} 的 value、{@code List}/数组的元素、
 * {@code Set} 内元素都不会被替换。原文见 {@code 00_doc/20-JSON出口null兼容与fastjson2边界治理-2026-10-29.md}。</p>
 *
 * @author zhangxh
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
                // 业务已显式指定，尊重；且对已有 null 序列化器的属性再赋值会抛 IllegalStateException
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

    /**
     * 把本 modifier 打成 Jackson 3 模块。
     *
     * @param nullStringAsEmpty 是否同时把 null String 写成空串
     */
    public static SimpleModule module(boolean nullStringAsEmpty) {
        SimpleModule m = new SimpleModule("scm-null-compat");
        m.setSerializerModifier(new NullCompatSerializerModifier(nullStringAsEmpty));
        return m;
    }

    /** 固定输出的 null 序列化器：写 {@code false} 或写 {@code ""}。 */
    static final class FixedValueSerializer extends ValueSerializer<Object> {

        private final Boolean boolValue;
        private final String stringValue;

        FixedValueSerializer(Boolean boolValue, String stringValue) {
            this.boolValue = boolValue;
            this.stringValue = stringValue;
        }

        @Override
        public void serialize(Object value, JsonGenerator gen, SerializationContext ctxt) throws JacksonException {
            if (boolValue != null) {
                gen.writeBoolean(boolValue.booleanValue());
            } else {
                gen.writeString(stringValue == null ? "" : stringValue);
            }
        }
    }
}
