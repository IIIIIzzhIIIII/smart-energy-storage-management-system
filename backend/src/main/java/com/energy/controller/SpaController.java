package com.energy.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 前端使用 createWebHistory()，刷新 /dashboard 等地址时浏览器会直接请求后端。
 * 这里把已知的 Vue 路由 forward 到 index.html，避免 404。
 * 只列明确路由，不拦截 /api、/ws 和静态资源。
 */
@Controller
public class SpaController {

    @GetMapping({
            "/",
            "/login",
            "/dashboard",
            "/device",
            "/safety",
            "/history",
            "/strategy",
            "/alarm",
            "/report",
            "/user"
    })
    public String index() {
        return "forward:/index.html";
    }
}
