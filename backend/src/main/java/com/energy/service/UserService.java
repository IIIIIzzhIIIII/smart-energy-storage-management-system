package com.energy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.energy.common.BusinessException;
import com.energy.entity.SysUser;
import com.energy.mapper.SysUserMapper;
import com.energy.util.MD5Util;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class UserService {

    @Autowired
    private SysUserMapper sysUserMapper;

    public Page<SysUser> page(long current, long size, String keyword) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(SysUser::getUsername, keyword)
                    .or().like(SysUser::getNickname, keyword));
        }
        wrapper.orderByAsc(SysUser::getId);
        return sysUserMapper.selectPage(new Page<>(current, size), wrapper);
    }

    public void add(SysUser user) {
        Long count = sysUserMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, user.getUsername()));
        if (count != null && count > 0) {
            throw new BusinessException("用户名已存在");
        }
        if (!StringUtils.hasText(user.getPassword())) {
            user.setPassword("123456");
        }
        user.setId(null);
        user.setPassword(MD5Util.md5(user.getPassword()));
        if (user.getStatus() == null) {
            user.setStatus(1);
        }
        sysUserMapper.insert(user);
    }

    public void update(SysUser user) {
        if (user.getId() == null) {
            throw new BusinessException("缺少用户ID");
        }
        // 若密码为空则不修改密码
        if (StringUtils.hasText(user.getPassword())) {
            user.setPassword(MD5Util.md5(user.getPassword()));
        } else {
            user.setPassword(null);
        }
        sysUserMapper.updateById(user);
    }

    public void delete(Long id) {
        sysUserMapper.deleteById(id);
    }

    public void resetPassword(Long id) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setPassword(MD5Util.md5("123456"));
        sysUserMapper.updateById(user);
    }

    /**
     * 切换启用/停用状态
     */
    public void toggleStatus(Long id, Integer status) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setStatus(status);
        sysUserMapper.updateById(user);
    }
}