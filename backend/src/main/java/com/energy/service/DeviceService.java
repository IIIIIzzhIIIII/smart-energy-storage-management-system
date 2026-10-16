package com.energy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.energy.common.BusinessException;
import com.energy.context.PermissionUtil;
import com.energy.context.UserContext;
import com.energy.entity.Device;
import com.energy.mapper.DeviceMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class DeviceService {

    @Autowired
    private DeviceMapper deviceMapper;

    private void applyOwnerFilter(LambdaQueryWrapper<Device> wrapper) {
        // 管理员看全部，其他用户只能看自己名下的设备
        if (!PermissionUtil.isAdmin()) {
            wrapper.eq(Device::getOwnerId, UserContext.getUserId());
        }
    }

    public Page<Device> page(long current, long size, String keyword, String deviceType) {
        LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<>();
        applyOwnerFilter(wrapper);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Device::getDeviceName, keyword)
                    .or().like(Device::getDeviceCode, keyword));
        }
        if (StringUtils.hasText(deviceType)) {
            wrapper.eq(Device::getDeviceType, deviceType);
        }
        wrapper.orderByAsc(Device::getId);
        return deviceMapper.selectPage(new Page<>(current, size), wrapper);
    }

    public List<Device> listAll() {
        LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<>();
        applyOwnerFilter(wrapper);
        wrapper.orderByAsc(Device::getId);
        return deviceMapper.selectList(wrapper);
    }

    public void add(Device device) {
        PermissionUtil.requireWrite();
        // 非管理员自动归属自己
        if (!PermissionUtil.isAdmin() || device.getOwnerId() == null) {
            device.setOwnerId(UserContext.getUserId());
        }
        Long count = deviceMapper.selectCount(
                new LambdaQueryWrapper<Device>().eq(Device::getDeviceCode, device.getDeviceCode()));
        if (count != null && count > 0) {
            throw new BusinessException("设备编号已存在");
        }
        device.setId(null);
        if (device.getStatus() == null) {
            device.setStatus(1);
        }
        deviceMapper.insert(device);
    }

    public void update(Device device) {
        PermissionUtil.requireWrite();
        Device exist = deviceMapper.selectById(device.getId());
        if (exist == null) {
            throw new BusinessException("设备不存在");
        }
        // 非管理员只能改自己的设备
        if (!PermissionUtil.isAdmin() && !exist.getOwnerId().equals(UserContext.getUserId())) {
            throw new BusinessException(403, "无权操作该设备");
        }
        deviceMapper.updateById(device);
    }

    public void delete(Long id) {
        PermissionUtil.requireWrite();
        Device exist = deviceMapper.selectById(id);
        if (exist != null && !PermissionUtil.isAdmin() && !exist.getOwnerId().equals(UserContext.getUserId())) {
            throw new BusinessException(403, "无权操作该设备");
        }
        deviceMapper.deleteById(id);
    }
}