// AuthContext.java
// 当前登录用户上下文：由 AuthInterceptor 在每个请求线程内填充/清理
package com.palmlawyer.util;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class AuthContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();

    private AuthContext() {}

    public static void set(Long userId) {
        USER_ID.set(userId);
    }

    /** 当前登录用户 ID；未登录时抛 401 */
    public static Long userId() {
        Long id = USER_ID.get();
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录");
        }
        return id;
    }

    public static void clear() {
        USER_ID.remove();
    }
}
