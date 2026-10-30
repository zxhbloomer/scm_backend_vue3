package com.xinyirun.scm.ai.config.memory;

import org.springframework.ai.chat.messages.MessageType;

import java.util.Arrays;

/**
 * MessageType 字符串反查工具。
 *
 * <p>背景：Spring AI 1.1.x 的 {@code MessageType} 提供 {@code static fromValue(String)}；
 * 升级到 2.0.1 后该方法已被移除（javap 实测只剩 {@code values()}/{@code valueOf(String)}/{@code getValue()}）。
 * 数据库表 {@code ai_conversation_content.type} 与 {@code ai_workflow_conversation_content.type}
 * 存的是 {@code MessageType#getValue()} 的小写值（user/assistant/system/tool），
 * 因此这里按 {@code getValue()} 做忽略大小写反查，语义与旧 {@code fromValue} 保持一致。
 *
 * @author SCM-AI开发团队
 */
final class MessageTypeSupport {

    private MessageTypeSupport() {
    }

    /**
     * 按 {@link MessageType#getValue()} 反查枚举。
     *
     * @param value 数据库存储的消息类型字符串
     * @return 对应枚举
     * @throws IllegalArgumentException 值不存在时抛出，与旧 {@code fromValue} 行为一致
     */
    static MessageType fromValue(String value) {
        return Arrays.stream(MessageType.values())
                .filter(type -> type.getValue().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知消息类型: " + value));
    }
}
