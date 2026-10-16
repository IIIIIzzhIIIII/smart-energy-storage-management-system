package com.energy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.energy.common.BusinessException;
import com.energy.context.PermissionUtil;
import com.energy.context.UserContext;
import com.energy.entity.AlarmRecord;
import com.energy.entity.AlarmRule;
import com.energy.entity.Device;
import com.energy.entity.DeviceData;
import com.energy.mapper.AlarmRecordMapper;
import com.energy.mapper.AlarmRuleMapper;
import com.energy.mapper.DeviceDataMapper;
import com.energy.mapper.DeviceMapper;
import com.energy.websocket.WebSocketServer;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AlarmService {

    @Autowired
    private AlarmRuleMapper alarmRuleMapper;

    @Autowired
    private AlarmRecordMapper alarmRecordMapper;

    @Autowired
    private DeviceMapper deviceMapper;

    @Autowired
    private DeviceDataMapper deviceDataMapper;

    // ===================== 规则 CRUD =====================

    private void applyOwnerFilter(LambdaQueryWrapper<AlarmRule> wrapper) {
        if (!PermissionUtil.isAdmin()) {
            wrapper.eq(AlarmRule::getOwnerId, UserContext.getUserId());
        }
    }

    public Page<AlarmRule> rulePage(long current, long size) {
        LambdaQueryWrapper<AlarmRule> wrapper = new LambdaQueryWrapper<>();
        applyOwnerFilter(wrapper);
        wrapper.orderByAsc(AlarmRule::getId);
        return alarmRuleMapper.selectPage(new Page<>(current, size), wrapper);
    }

    public void addRule(AlarmRule rule) {
        PermissionUtil.requireWrite();
        if (!PermissionUtil.isAdmin() || rule.getOwnerId() == null) {
            rule.setOwnerId(UserContext.getUserId());
        }
        rule.setId(null);
        if (rule.getEnabled() == null) {
            rule.setEnabled(1);
        }
        alarmRuleMapper.insert(rule);
    }

    public void updateRule(AlarmRule rule) {
        PermissionUtil.requireWrite();
        AlarmRule exist = alarmRuleMapper.selectById(rule.getId());
        if (exist == null) {
            throw new BusinessException("告警规则不存在");
        }
        if (!PermissionUtil.isAdmin() && !exist.getOwnerId().equals(UserContext.getUserId())) {
            throw new BusinessException(403, "无权操作该规则");
        }
        alarmRuleMapper.updateById(rule);
    }

    public void deleteRule(Long id) {
        PermissionUtil.requireWrite();
        AlarmRule exist = alarmRuleMapper.selectById(id);
        if (exist != null && !PermissionUtil.isAdmin() && !exist.getOwnerId().equals(UserContext.getUserId())) {
            throw new BusinessException(403, "无权操作该规则");
        }
        alarmRuleMapper.deleteById(id);
    }

    // ===================== 告警记录 =====================

    public Page<AlarmRecord> recordPage(long current, long size, Integer status) {
        LambdaQueryWrapper<AlarmRecord> wrapper = new LambdaQueryWrapper<>();
        applyOwnerFilterForRecord(wrapper);
        if (status != null) {
            wrapper.eq(AlarmRecord::getStatus, status);
        }
        wrapper.orderByDesc(AlarmRecord::getId);
        return alarmRecordMapper.selectPage(new Page<>(current, size), wrapper);
    }

    private void applyOwnerFilterForRecord(LambdaQueryWrapper<AlarmRecord> wrapper) {
        if (!PermissionUtil.isAdmin()) {
            wrapper.eq(AlarmRecord::getOwnerId, UserContext.getUserId());
        }
    }

    public void handleRecord(Long id) {
        PermissionUtil.requireWrite();
        AlarmRecord record = new AlarmRecord();
        record.setId(id);
        record.setStatus(1);
        record.setHandleTime(LocalDateTime.now());
        alarmRecordMapper.updateById(record);
    }

    /**
     * 定时检测越限，生成告警记录（每 5 秒）
     */
    @Scheduled(fixedDelay = 5000)
    public void check() {
        List<AlarmRule> rules = alarmRuleMapper.selectList(
                new LambdaQueryWrapper<AlarmRule>().eq(AlarmRule::getEnabled, 1));
        for (AlarmRule rule : rules) {
            try {
                checkRule(rule);
            } catch (Exception e) {
                log.error("告警检测失败, ruleId={}", rule.getId(), e);
            }
        }
    }

    private void checkRule(AlarmRule rule) {
        DeviceData data = deviceDataMapper.selectOne(
                new LambdaQueryWrapper<DeviceData>()
                        .eq(DeviceData::getDeviceId, rule.getDeviceId())
                        .orderByDesc(DeviceData::getId)
                        .last("LIMIT 1"));
        if (data == null) {
            return;
        }
        Double value = getMetricValue(data, rule.getMetric());
        if (value == null) {
            return;
        }
        boolean triggered = false;
        String thresholdDesc = "";
        if (rule.getMinValue() != null && value < rule.getMinValue()) {
            triggered = true;
            thresholdDesc = "低于下限" + rule.getMinValue();
        }
        if (rule.getMaxValue() != null && value > rule.getMaxValue()) {
            triggered = true;
            thresholdDesc = "超过上限" + rule.getMaxValue();
        }
        if (!triggered) {
            return;
        }

        // 避免重复告警：同一规则最近 1 分钟内已有未处理告警则跳过
        Long count = alarmRecordMapper.selectCount(
                new LambdaQueryWrapper<AlarmRecord>()
                        .eq(AlarmRecord::getDeviceId, rule.getDeviceId())
                        .eq(AlarmRecord::getMetric, rule.getMetric())
                        .eq(AlarmRecord::getStatus, 0)
                        .ge(AlarmRecord::getCreateTime, LocalDateTime.now().minusMinutes(1)));
        if (count != null && count > 0) {
            return;
        }

        Device device = deviceMapper.selectById(rule.getDeviceId());
        AlarmRecord record = new AlarmRecord();
        record.setDeviceId(rule.getDeviceId());
        record.setDeviceName(device == null ? null : device.getDeviceName());
        record.setMetric(rule.getMetric());
        record.setValue(value);
        record.setThreshold(thresholdDesc);
        record.setLevel(rule.getLevel());
        record.setMessage((device == null ? "设备" : device.getDeviceName())
                + " " + rule.getMetric() + "=" + value + " " + thresholdDesc);
        record.setStatus(0);
        record.setOwnerId(rule.getOwnerId());
        record.setCreateTime(LocalDateTime.now());
        alarmRecordMapper.insert(record);

        // 通过 WebSocket 推送告警
        Map<String, Object> alarmMsg = new HashMap<>();
        alarmMsg.put("type", "alarm");
        alarmMsg.put("data", record);
        WebSocketServer.broadcast(JSON.toJSONString(alarmMsg));
    }

    private Double getMetricValue(DeviceData data, String metric) {
        switch (metric) {
            case "POWER":
                return data.getPower();
            case "VOLTAGE":
                return data.getVoltage();
            case "CURRENT":
                return data.getCurrent();
            case "SOC":
                return data.getSoc();
            default:
                return null;
        }
    }
}