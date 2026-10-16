package com.energy.controller;

import com.energy.common.Result;
import com.energy.context.PermissionUtil;
import com.energy.entity.DeviceData;
import com.energy.service.DataSimulateService;
import com.energy.service.MonitorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/monitor")
public class MonitorController {

    @Autowired
    private MonitorService monitorService;

    @Autowired
    private DataSimulateService dataSimulateService;

    @GetMapping("/snapshot")
    public Result<List<Map<String, Object>>> snapshot() {
        return Result.success(monitorService.latestSnapshot());
    }

    @GetMapping("/energy-flow")
    public Result<Map<String, Object>> energyFlow() {
        return Result.success(monitorService.energyFlow());
    }

    @GetMapping("/history")
    public Result<List<DeviceData>> history(
            @RequestParam Long deviceId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime start,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime end) {
        return Result.success(monitorService.history(deviceId, start, end));
    }

    /**
     * 指定日期（年月日）的全天功率曲线（0:00 - 24:00）
     */
    @GetMapping("/day-curve")
    public Result<Map<String, Object>> dayCurve(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date) {
        return Result.success(monitorService.dayCurve(date));
    }

    /**
     * 生成历史模拟数据（仅管理员），days 为往前推的天数，force=true 时强制重新生成
     */
    @PostMapping("/generate-history")
    public Result<Integer> generateHistory(@RequestParam(defaultValue = "7") int days,
                                           @RequestParam(defaultValue = "false") boolean force) {
        PermissionUtil.requireAdmin();
        return Result.success(dataSimulateService.generateHistory(days, force));
    }
}