package com.limo.config;

/**
 * 当前登录用户上下文：JwtInterceptor 把 userId 放进来，业务层可随时取。
 * 用 ThreadLocal（每请求一个线程，隔离用户）。
 */
public class UserContext {
    private static final ThreadLocal<Long> HOLDER = new ThreadLocal<>();
    public static void set(Long userId) { HOLDER.set(userId); }
    public static Long get() { return HOLDER.get(); }
    public static void clear() { HOLDER.remove(); }
}
