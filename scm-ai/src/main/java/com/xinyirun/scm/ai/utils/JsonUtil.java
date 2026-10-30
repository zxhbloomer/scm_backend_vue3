package com.xinyirun.scm.ai.utils;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON 工具类（Jackson 3 / tools.jackson 实现）
 *
 * <p>迁移说明：Jackson 3 的 {@link JsonMapper} 不可变且线程安全，
 * 因此统一在静态初始化处用 Builder 装配一次；{@code JavaTimeModule}、{@code Jdk8Module}
 * 在 Jackson 3 已内建，无需注册。</p>
 */
@Slf4j
public class JsonUtil {

    private static final ObjectMapper objectMapper = JsonMapper.builder()
            .changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.NON_NULL))
            .configure(SerializationFeature.INDENT_OUTPUT, Boolean.FALSE)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .build();

    public static final ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    public static String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JacksonException e) {
            log.error("JsonUtil error", e);
        }
        return null;
    }

    /**
     * JSON对象反序列化
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        if (StringUtils.isBlank(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, clazz);
        } catch (JacksonException e) {
            log.error("反序列化失败", e);
        }
        return null;
    }

    public static <T> T fromJson(JsonNode jsonNode, Class<T> clazz) {
        try {
            return objectMapper.treeToValue(jsonNode, clazz);
        } catch (JacksonException e) {
            log.error("反序列化失败", e);
        }
        return null;
    }


    public static <T> List<T> fromArrayNode(ArrayNode arrayNode, Class<T> clazz) {
        List<T> result = new ArrayList<>();
        try {
            for (JsonNode jsonNode : arrayNode) {
                result.add(objectMapper.treeToValue(jsonNode, clazz));
            }
        } catch (JacksonException e) {
            log.error("反序列化失败", e);
        }
        return result;
    }

    public static JsonNode toJsonNode(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JacksonException e) {
            log.error("反序列化失败", e);
        }
        return null;
    }

    public static Map<String, Object> toMap(String json) {
        Map<String, Object> result;
        try {
            result = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (JacksonException e) {
            throw new RuntimeException(e);
        }
        return result;
    }

    public static <T> List<T> toList(String json, Class<T> clazz) {
        try {
            return objectMapper.readValue(json, objectMapper.getTypeFactory().constructCollectionType(List.class, clazz));
        } catch (JacksonException e) {
            log.error("反序列化失败", e);
        }
        return null;
    }

    public static Map<String, Object> toMap(Object obj) {
        try {
            return objectMapper.convertValue(obj, new TypeReference<HashMap<String, Object>>() {
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 从Map转换为指定类型对象
     */
    public static <T> T fromMap(Map<String, Object> map, Class<T> clazz) {
        try {
            return objectMapper.convertValue(map, clazz);
        } catch (Exception e) {
            log.error("从Map转换失败", e);
            throw new RuntimeException(e);
        }
    }

    public static JsonNode classToJsonNode(Object obj) {
        return objectMapper.valueToTree(obj);
    }

    public static ArrayNode createArrayNode() {
        return objectMapper.createArrayNode();
    }

}
