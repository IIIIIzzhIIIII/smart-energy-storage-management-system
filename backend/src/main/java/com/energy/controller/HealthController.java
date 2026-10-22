package com.energy.controller;

import com.energy.common.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 服务健康检查：桌面启动器用它判断后端是否已经就绪。
 * 该接口不需要登录，已在 WebConfig 中排除 JWT 拦截。
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "ok");
        data.put("service", "energy-monitor");
        try {
            Integer one = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            data.put("database", one != null && one == 1 ? "ok" : "unknown");
        } catch (Exception e) {
            data.put("database", "error: " + e.getMessage());
        }
        return Result.success(data);
    }
}
