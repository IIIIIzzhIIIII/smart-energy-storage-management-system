package com.energy.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.energy.common.Result;
import com.energy.context.PermissionUtil;
import com.energy.entity.SysUser;
import com.energy.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
public class UserController {

    @Autowired
    private UserService userService;

    @GetMapping("/page")
    public Result<Page<SysUser>> page(@RequestParam(defaultValue = "1") long current,
                                      @RequestParam(defaultValue = "10") long size,
                                      @RequestParam(required = false) String keyword) {
        PermissionUtil.requireAdmin();
        return Result.success(userService.page(current, size, keyword));
    }

    @PostMapping
    public Result<Void> add(@RequestBody SysUser user) {
        PermissionUtil.requireAdmin();
        userService.add(user);
        return Result.success();
    }

    @PutMapping
    public Result<Void> update(@RequestBody SysUser user) {
        PermissionUtil.requireAdmin();
        userService.update(user);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        PermissionUtil.requireAdmin();
        userService.delete(id);
        return Result.success();
    }

    @PutMapping("/reset/{id}")
    public Result<Void> resetPassword(@PathVariable Long id) {
        PermissionUtil.requireAdmin();
        userService.resetPassword(id);
        return Result.success();
    }

    @PutMapping("/status/{id}")
    public Result<Void> toggleStatus(@PathVariable Long id, @RequestParam Integer status) {
        PermissionUtil.requireAdmin();
        userService.toggleStatus(id, status);
        return Result.success();
    }
}