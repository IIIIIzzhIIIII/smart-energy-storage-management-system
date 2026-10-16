package com.energy.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.energy.common.Result;
import com.energy.entity.Device;
import com.energy.service.DeviceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/device")
public class DeviceController {

    @Autowired
    private DeviceService deviceService;

    @GetMapping("/page")
    public Result<Page<Device>> page(@RequestParam(defaultValue = "1") long current,
                                     @RequestParam(defaultValue = "10") long size,
                                     @RequestParam(required = false) String keyword,
                                     @RequestParam(required = false) String deviceType) {
        return Result.success(deviceService.page(current, size, keyword, deviceType));
    }

    @GetMapping("/list")
    public Result<List<Device>> list() {
        return Result.success(deviceService.listAll());
    }

    @PostMapping
    public Result<Void> add(@RequestBody Device device) {
        deviceService.add(device);
        return Result.success();
    }

    @PutMapping
    public Result<Void> update(@RequestBody Device device) {
        deviceService.update(device);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        deviceService.delete(id);
        return Result.success();
    }
}