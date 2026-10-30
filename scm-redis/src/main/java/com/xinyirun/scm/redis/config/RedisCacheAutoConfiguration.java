package com.xinyirun.scm.redis.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.xinyirun.scm.common.properies.SystemConfigProperies;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurerSupport;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import tools.jackson.databind.DefaultTyping;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@Configuration
@AutoConfigureAfter(DataRedisAutoConfiguration.class)
@Slf4j
public class RedisCacheAutoConfiguration extends CachingConfigurerSupport {

    @Autowired
    private SystemConfigProperies systemConfigProperies;

    /**
     * 自定义缓存管理器
     * 实现有效期、自定义key前缀、序列化方法
     *
     * @param lettuceConnectionFactory
     * @param jackson2JsonRedisSerializer
     * @return
     */
    @Bean
    public CacheManager cacheManager(
        @Qualifier("scm_lettuce_connection_factory") LettuceConnectionFactory lettuceConnectionFactory,
        JacksonJsonRedisSerializer<Object> jackson2JsonRedisSerializer) {

        RedisSerializationContext.SerializationPair keyPair =
            RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer());
        RedisSerializationContext.SerializationPair valuePair =
            RedisSerializationContext.SerializationPair.fromSerializer(jackson2JsonRedisSerializer);

        //以锁写入的方式创建RedisCacheWriter对象
        RedisCacheWriter writer = RedisCacheWriter.lockingRedisCacheWriter(lettuceConnectionFactory);

        int redisCacheExpiredMin = systemConfigProperies.getRedisCacheExpiredMin();
        RedisCacheConfiguration cacheConfiguration =
            RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofMinutes(redisCacheExpiredMin)).serializeKeysWith(keyPair)
                .serializeValuesWith(valuePair);

        RedisCacheManager redisCacheManager = new RedisCacheManager(writer, cacheConfiguration);

        return redisCacheManager;

    }

    /**
     * 配置一个序列器, 将对象序列化为字符串存储, 和将对象反序列化为对象
     * 并注入到cacheManager和redisTemplate
     */
    @Bean
    public JacksonJsonRedisSerializer<Object> jackson2JsonRedisSerializer() {
        // Jackson 3 内置 java.time / jdk8 支持，无需再注册 JavaTimeModule；
        // 日期默认即为 ISO-8601（不再是时间戳），等价于旧配置 disable(WRITE_DATES_AS_TIMESTAMPS)。
        //
        // 类型信息（多态）策略：
        // 1) 白名单收敛到本项目与 JDK 常用包，避免 allowIfSubType(Object.class) 把整棵类型树放开（安全面）；
        // 2) 写回格式显式指定 WRAPPER_ARRAY（形如 ["com.xinyirun...Xxx",{...}]），与升级前
        //    Jackson 2 的 enableDefaultTyping(NON_FINAL) 默认格式一致 —— 这样存量缓存可读，
        //    且升级若回滚，旧版本也能读新写入的值（双向兼容）。
        BasicPolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("com.xinyirun.")
                .allowIfSubType("java.util.")
                .allowIfSubType("java.lang.")
                .allowIfSubType("java.math.")
                .allowIfSubType("java.time.")
                .allowIfSubTypeIsArray()
                .build();
        JsonMapper om = JsonMapper.builder()
                .changeDefaultVisibility(vc -> vc.withVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY))
                .activateDefaultTyping(ptv, DefaultTyping.NON_FINAL, JsonTypeInfo.As.WRAPPER_ARRAY)
                .build();
        return new JacksonJsonRedisSerializer<>(om, Object.class);
    }

    /**
     * redisTemplate 操作模板，value 实现Jackson2JsonRedisSerializer
     *
     * @param lettuceConnectionFactory
     * @param jackson2JsonRedisSerializer
     * @return
     */
    @Bean
    public RedisTemplate redisTemplate(
        @Qualifier("scm_lettuce_connection_factory") LettuceConnectionFactory lettuceConnectionFactory,
        JacksonJsonRedisSerializer<Object> jackson2JsonRedisSerializer) {
        RedisTemplate redisTemplate = new RedisTemplate();
        redisTemplate.setConnectionFactory(lettuceConnectionFactory);

        //        FastJsonRedisSerializer<Object> fastJsonRedisSerializer = new FastJsonRedisSerializer<>(Object.class);
        // 全局开启AutoType，不建议使用
        // ParserConfig.getGlobalInstance().setAutoTypeSupport(true);
        // 建议使用这种方式，小范围指定白名单
//        ParserConfig.getGlobalInstance().addAccept("com.xinyirun.scm.");
        // fastjson2 的 AutoType 白名单随 FastJsonRedisSerializer 一起停用，Redis 序列化已改为 Jackson 3

        // 设置键（key）的序列化采用StringRedisSerializer。
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        // 设置值（value）的序列化采用FastJsonRedisSerializer。
        redisTemplate.setValueSerializer(jackson2JsonRedisSerializer);

        // Hash key序列化
        redisTemplate.setHashKeySerializer(new StringRedisSerializer());
        // Hash value序列化
        redisTemplate.setHashValueSerializer(jackson2JsonRedisSerializer);

        redisTemplate.setDefaultSerializer(jackson2JsonRedisSerializer);

        redisTemplate.afterPropertiesSet();
        return redisTemplate;
    }

    @Bean
    public ChannelTopic expiredTopic() {
        ChannelTopic ot = new ChannelTopic("__keyevent@0__:expired");
        return ot;
    }

    /**
     * redis增加监听
     * @param
     * @return
     */
    @Bean("scm_redis_listener_container")
    RedisMessageListenerContainer keyExpirationListenerContainer(
        @Qualifier("scm_lettuce_connection_factory") LettuceConnectionFactory lettuceConnectionFactory) {

        RedisMessageListenerContainer listenerContainer = new RedisMessageListenerContainer();
        listenerContainer.setConnectionFactory(lettuceConnectionFactory);
        return listenerContainer;
    }

    /**
     * 处理程序连接不上redis时或者超时连接，程序自动查询数据库，不抛异常
     */
    @Bean
    @Override
    public CacheErrorHandler errorHandler() {

        CacheErrorHandler cacheErrorHandler = new CacheErrorHandler() {

            @Override
            public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
                log.error("handleCacheGetError error", e);
                log.error("缓存key:【{}】出现异常:{}", key, e);

            }

            @Override
            public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) {

                log.error("handleCachePutError error", e);
                log.error("缓存key:【{}】加入出现异常:{}", key, e);

            }

            @Override
            public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {

                log.error("handleCacheEvictError error", e);
                log.error("缓存key:【{}】置失效出现异常:{}", key, e);

            }

            @Override
            public void handleCacheClearError(RuntimeException e, Cache cache) {

                log.error("handleCacheClearError error", e);
                log.error("清除缓存出现异常:{}", e);

            }
        };
        return cacheErrorHandler;
    }

}
