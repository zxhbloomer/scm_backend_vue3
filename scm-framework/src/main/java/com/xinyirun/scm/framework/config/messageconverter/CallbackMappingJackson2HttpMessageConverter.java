package com.xinyirun.scm.framework.config.messageconverter;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.xinyirun.scm.bean.system.ao.result.JsonResultAo;
import com.xinyirun.scm.common.constant.JsonResultTypeConstants;
import com.xinyirun.scm.common.utils.json.ScmJsonMapperFactory;
import org.springframework.core.ResolvableType;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * SCM 统一 JSON 出口（Spring Boot 4 / Jackson 3 版）。
 *
 * <p>历史说明：本类原先继承 fastjson2 的 FastJsonHttpMessageConverter，通过 JSONWriter.Feature
 * 在单例转换器上按请求切换序列化特性，属于线程不安全写法。切换到 Jackson 3 后改为：</p>
 * <ul>
 *   <li>基础序列化由 JacksonJsonHttpMessageConverter（不可变 JsonMapper）完成，线程安全；</li>
 *   <li>按响应的 json_result_type 只派生 ObjectWriter（不可变、按请求），不再修改共享状态；</li>
 *   <li>日期格式由 {@link ScmJsonMapperFactory} 统一固定为 yyyy-MM-dd HH:mm:ss，与升级前一致。</li>
 * </ul>
 *
 * <table border="1">
 *   <caption>json_result_type 映射（2026-10-29 起与升级前 fastjson2 逐档对齐）</caption>
 *   <tr><th>json_result_type</th><th>fastjson2 旧行为</th><th>Jackson 3 现行为</th></tr>
 *   <tr><td>NORMAL(0)</td><td>WriteMapNullValue + WriteNullBooleanAsFalse</td>
 *       <td>Include.ALWAYS + null Boolean → false</td></tr>
 *   <tr><td>STRING_EMPTY_BOOLEAN_FALSE(1)</td><td>null String → ""、null Boolean → false，其余 null 省略</td>
 *       <td>Include.NON_NULL + null Boolean → false + null String → ""</td></tr>
 *   <tr><td>NULL_NOT_OUT(2)</td><td>不输出 null 字段</td><td>Include.NON_NULL</td></tr>
 * </table>
 *
 * <p><b>已知边界</b>（2026-10-29 实测，详见 00_doc/20 号文档 §2.3）：
 * ① STRING_EMPTY 档下 {@code List<String>} / {@code String[]} 的 null 元素，升级前被写成 {@code ""}，
 * 这里只能写 {@code null}（Jackson 3 无对应开关）；今日走该档的两个接口 payload 不含该形状，无实际影响。
 * ② 集合元素、声明为 {@code Object} 的字段不替换 null，与升级前 fastjson2 行为一致。</p>
 *
 * @author zhangxh
 */
public class CallbackMappingJackson2HttpMessageConverter extends JacksonJsonHttpMessageConverter {

    /** NORMAL(0)：输出 null 字段，null Boolean → false。 */
    private static final JsonMapper MAPPER_NORMAL =
            ScmJsonMapperFactory.mvcMapper(JsonInclude.Include.ALWAYS,
                    ScmJsonMapperFactory.NullCompat.BOOLEAN_FALSE);

    /** STRING_EMPTY_BOOLEAN_FALSE(1)：其余 null 省略，null Boolean → false、null String → ""。 */
    private static final JsonMapper MAPPER_STRING_EMPTY =
            ScmJsonMapperFactory.mvcMapper(JsonInclude.Include.NON_NULL,
                    ScmJsonMapperFactory.NullCompat.BOOLEAN_FALSE_STRING_EMPTY);

    /** NULL_NOT_OUT(2)：不输出 null 字段。 */
    private static final JsonMapper MAPPER_NON_NULL =
            ScmJsonMapperFactory.mvcMapper(JsonInclude.Include.NON_NULL,
                    ScmJsonMapperFactory.NullCompat.NONE);

    /**
     * 是否按 json_result_type 切换档位。仅 MVC 出口（无参构造）为 true；
     * 显式注入 mapper 的场景（RestTemplate 等出站）保持 false，完全走父类逻辑。
     */
    private final boolean jsonResultTypeAware;

    /**
     * 默认构造：MVC 出口，使用 NORMAL 档（输出 null 字段，null Boolean → false）。
     */
    public CallbackMappingJackson2HttpMessageConverter() {
        this(MAPPER_NORMAL, true);
    }

    /**
     * 指定 mapper 构造（RestTemplate 等出站场景复用同一套日期口径）。
     *
     * @param mapper 不可变 JsonMapper
     */
    public CallbackMappingJackson2HttpMessageConverter(JsonMapper mapper) {
        this(mapper, false);
    }

    private CallbackMappingJackson2HttpMessageConverter(JsonMapper mapper, boolean jsonResultTypeAware) {
        super(mapper);
        this.jsonResultTypeAware = jsonResultTypeAware;
        // 中文不乱码
        setDefaultCharset(StandardCharsets.UTF_8);
        List<MediaType> mediaTypes = new ArrayList<>();
        mediaTypes.add(MediaType.APPLICATION_JSON);
        mediaTypes.add(MediaType.valueOf("application/*+json"));
        setSupportedMediaTypes(mediaTypes);
    }

    /**
     * 只接管 Java 对象到 JSON 的转换，不接管 CharSequence。
     *
     * <p>Spring Boot 4 的转换器列表里，StringHttpMessageConverter 与本转换器都可能
     * 声称能写 String；若本转换器排在前面，controller 返回的 String 会被当成 JSON
     * 字符串写成带引号的形式。这里显式让出 CharSequence，保证原样输出。</p>
     */
    @Override
    public boolean canWrite(Class<?> clazz, MediaType mediaType) {
        if (clazz != null && CharSequence.class.isAssignableFrom(clazz)) {
            return false;
        }
        return super.canWrite(clazz, mediaType);
    }

    /**
     * 按 json_result_type 选择对应档位的 JsonMapper 单独写出；其余情况交给父类。
     *
     * <p>Jackson 3 的 ObjectWriter 不提供按写实例改包含策略的入口，因此这里预建三个不可变
     * JsonMapper 按需选择（都是线程安全的，且都是单例）。</p>
     */
    @Override
    protected void writeInternal(Object object, ResolvableType type, HttpOutputMessage outputMessage,
                                 Map<String, Object> hints) throws IOException, HttpMessageNotWritableException {
        JsonMapper targetMapper = selectJsonResultTypeMapper(object);
        if (targetMapper == null) {
            super.writeInternal(object, type, outputMessage, hints);
            return;
        }
        // type 可能为 null（如 /error 等异常处理路径），必须兜底，否则 NPE
        Type declaredType = (type != null && type.getType() != null) ? type.getType() : object.getClass();
        JavaType javaType = getJavaType(declaredType, object.getClass());
        try {
            ObjectWriter writer = targetMapper.writerFor(javaType);
            // 保留父类扩展点，避免绕过业务的 ObjectWriter 定制
            writer = customizeWriter(writer, javaType, outputMessage.getHeaders().getContentType());
            writer.writeValue(outputMessage.getBody(), object);
        } catch (JacksonException ex) {
            throw new HttpMessageNotWritableException("I/O error while writing " + javaType, ex);
        }
    }

    /**
     * 按 json_result_type 选择档位 mapper。
     *
     * @return 需要切换时返回目标 mapper；返回 null 表示交给父类默认流程
     */
    private JsonMapper selectJsonResultTypeMapper(Object object) {
        if (!jsonResultTypeAware) {
            // 显式注入 mapper 的场景（RestTemplate 等）不改档
            return null;
        }
        Integer jsonResultType = resolveJsonResultType(object);
        if (jsonResultType == null) {
            return null;
        }
        if (jsonResultType == JsonResultTypeConstants.NULL_NOT_OUT) {
            return MAPPER_NON_NULL;
        }
        if (jsonResultType == JsonResultTypeConstants.STRING_EMPTY_BOOLEAN_FALSE) {
            return MAPPER_STRING_EMPTY;
        }
        return null;
    }

    private static Integer resolveJsonResultType(Object object) {
        if (object instanceof JsonResultAo) {
            return ((JsonResultAo) object).getJson_result_type();
        }
        return null;
    }
}
