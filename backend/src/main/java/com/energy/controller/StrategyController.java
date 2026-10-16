package com.energy.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.energy.common.Result;
import com.energy.entity.Strategy;
import com.energy.service.StrategyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/strategy")
public class StrategyController {

    @Autowired
    private StrategyService strategyService;

    @GetMapping("/page")
    public Result<Page<Strategy>> page(@RequestParam(defaultValue = "1") long current,
                                       @RequestParam(defaultValue = "10") long size,
                                       @RequestParam(required = false) String strategyType) {
        return Result.success(strategyService.page(current, size, strategyType));
    }

    @PostMapping
    public Result<Void> add(@RequestBody Strategy strategy) {
        strategyService.add(strategy);
        return Result.success();
    }

    @PutMapping
    public Result<Void> update(@RequestBody Strategy strategy) {
        strategyService.update(strategy);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        strategyService.delete(id);
        return Result.success();
    }

    @PutMapping("/toggle/{id}")
    public Result<Void> toggle(@PathVariable Long id, @RequestParam Integer enabled) {
        strategyService.toggleEnabled(id, enabled);
        return Result.success();
    }
}