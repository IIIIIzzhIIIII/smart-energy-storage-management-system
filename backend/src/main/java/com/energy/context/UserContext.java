package com.energy.context;

/**
 * 当前登录用户上下文（基于 ThreadLocal）
 */
public class UserContext {

    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<>();

    public static void set(CurrentUser user) {
        HOLDER.set(user);
    }

    public static CurrentUser get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    public static Long getUserId() {
        CurrentUser u = HOLDER.get();
        return u == null ? null : u.getUserId();
    }

    public static String getRole() {
        CurrentUser u = HOLDER.get();
        return u == null ? null : u.getRole();
    }
}