package com.energy.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.energy.common.Result;
import com.energy.entity.AlarmRecord;
import com.energy.entity.AlarmRule;
import com.energy.service.AlarmService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/alarm")
public class AlarmController {

    @Autowired
    private AlarmService alarmService;

    // ========== 规则 ==========
    @GetMapping("/rule/page")
    public Result<Page<AlarmRule>> rulePage(@RequestParam(defaultValue = "1") long current,
                                            @RequestParam(defaultValue = "10") long size) {
        return Result.success(alarmService.rulePage(current, size));
    }

    @PostMapping("/rule")
    public Result<Void> addRule(@RequestBody AlarmRule rule) {
        alarmService.addRule(rule);
        return Result.success();
    }

    @PutMapping("/rule")
    public Result<Void> updateRule(@RequestBody AlarmRule rule) {
        alarmService.updateRule(rule);
        return Result.success();
    }

    @DeleteMapping("/rule/{id}")
    public Result<Void> deleteRule(@PathVariable Long id) {
        alarmService.deleteRule(id);
        return Result.success();
    }

    // ========== 记录 ==========
    @GetMapping("/record/page")
    public Result<Page<AlarmRecord>> recordPage(@RequestParam(defaultValue = "1") long current,
                                                @RequestParam(defaultValue = "10") long size,
                                                @RequestParam(required = false) Integer status) {
        return Result.success(alarmService.recordPage(current, size, status));
    }

    @PutMapping("/record/handle/{id}")
    public Result<Void> handle(@PathVariable Long id) {
        alarmService.handleRecord(id);
        return Result.success();
    }
}