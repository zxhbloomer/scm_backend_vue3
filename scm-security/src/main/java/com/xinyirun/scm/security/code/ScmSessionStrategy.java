package com.xinyirun.scm.security.code;

import jakarta.servlet.http.HttpSession;
import org.springframework.web.context.request.ServletWebRequest;

/**
 * 会话属性读写工具。
 *
 * <p>替代已停止维护的 Spring Social {@code HttpSessionSessionStrategy}
 * （spring-social 1.1.6 基于 javax.servlet 与 Spring 4，无法在 Spring Framework 7 / Jakarta EE 11 下使用）。</p>
 *
 * @author zxh
 */
public class ScmSessionStrategy {

    /**
     * 写入会话属性（会话不存在时创建）
     */
    public void setAttribute(ServletWebRequest request, String name, Object value) {
        request.getRequest().getSession(true).setAttribute(name, value);
    }

    /**
     * 读取会话属性（会话不存在时返回 null）
     */
    public Object getAttribute(ServletWebRequest request, String name) {
        HttpSession session = request.getRequest().getSession(false);
        return session == null ? null : session.getAttribute(name);
    }

    /**
     * 移除会话属性（会话不存在时静默忽略）
     */
    public void removeAttribute(ServletWebRequest request, String name) {
        HttpSession session = request.getRequest().getSession(false);
        if (session != null) {
            session.removeAttribute(name);
        }
    }
}