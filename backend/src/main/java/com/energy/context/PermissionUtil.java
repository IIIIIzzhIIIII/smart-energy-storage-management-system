package com.energy.context;

import com.energy.common.BusinessException;

public class PermissionUtil {

    public static void requireAdmin() {
        String role = UserContext.getRole();
        if (!"ADMIN".equals(role)) {
            throw new BusinessException(403, "无权限执行此操作，仅管理员可用");
        }
    }

    /**
     * 校验普通用户是否可写（只读用户 VIEWER 禁止写操作）
     */
    public static void requireWrite() {
        String role = UserContext.getRole();
        if ("VIEWER".equals(role)) {
            throw new BusinessException(403, "只读用户无操作权限");
        }
    }

    public static boolean isAdmin() {
        return "ADMIN".equals(UserContext.getRole());
    }
}