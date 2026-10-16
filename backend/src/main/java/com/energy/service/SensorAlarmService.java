package com.energy.service;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.energy.entity.AlarmRecord;
import com.energy.entity.SensorData;
import com.energy.entity.SensorDevice;
import com.energy.mapper.AlarmRecordMapper;
import com.energy.mapper.SensorDataMapper;
import com.energy.mapper.SensorDeviceMapper;
import com.energy.websocket.WebSocketServer;
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
public class SensorAlarmService {

    @Autowired
    private SensorDeviceMapper sensorDeviceMapper;
    @Autowired
    private SensorDataMapper sensorDataMapper;
    @Autowired
    private AlarmRecordMapper alarmRecordMapper;

    /** 定时检测传感器越限，按 4 级分级生成告警（每 5 秒） */
    @Scheduled(fixedDelay = 5000)
    public void check() {
        List<SensorDevice> sensors = sensorDeviceMapper.selectList(new LambdaQueryWrapper<SensorDevice>()
                .eq(SensorDevice::getMatchStatus, 2)
                .eq(SensorDevice::getStatus, 1));
        for (SensorDevice sensor : sensors) {
            try {
                checkSensor(sensor);
            } catch (Exception e) {
                log.error("传感器告警检测失败: {}", sensor.getDeviceName(), e);
            }
        }
    }

    private void checkSensor(SensorDevice sensor) {
        SensorData data = sensorDataMapper.selectOne(new LambdaQueryWrapper<SensorData>()
                .eq(SensorData::getSensorId, sensor.getId())
                .orderByDesc(SensorData::getId)
                .last("LIMIT 1"));
        if (data == null || data.getValue() == null) return;
        double value = data.getValue();
        String level = null;
        String thresholdDesc = null;
        if (sensor.getEmergMin() != null && value < sensor.getEmergMin()) { level = "EMERGENCY"; thresholdDesc = "低于紧急下限" + sensor.getEmergMin(); }
        else if (sensor.getEmergMax() != null && value > sensor.getEmergMax()) { level = "EMERGENCY"; thresholdDesc = "超过紧急上限" + sensor.getEmergMax(); }
        else if (sensor.getCritMin() != null && value < sensor.getCritMin()) { level = "CRITICAL"; thresholdDesc = "低于严重下限" + sensor.getCritMin(); }
        else if (sensor.getCritMax() != null && value > sensor.getCritMax()) { level = "CRITICAL"; thresholdDesc = "超过严重上限" + sensor.getCritMax(); }
        else if (sensor.getWarnMin() != null && value < sensor.getWarnMin()) { level = "WARN"; thresholdDesc = "低于预警下限" + sensor.getWarnMin(); }
        else if (sensor.getWarnMax() != null && value > sensor.getWarnMax()) { level = "WARN"; thresholdDesc = "超过预警上限" + sensor.getWarnMax(); }
        if (level == null) return;

        // 去重：同一传感器最近 1 分钟已有未处理告警则跳过
        Long count = alarmRecordMapper.selectCount(new LambdaQueryWrapper<AlarmRecord>()
                .eq(AlarmRecord::getDeviceId, sensor.getId())
                .eq(AlarmRecord::getMetric, sensor.getSensorType())
                .eq(AlarmRecord::getStatus, 0)
                .ge(AlarmRecord::getCreateTime, LocalDateTime.now().minusMinutes(1)));
        if (count != null && count > 0) return;

        AlarmRecord record = new AlarmRecord();
        record.setDeviceId(sensor.getId());
        record.setDeviceName(sensor.getDeviceName());
        record.setMetric(sensor.getSensorType());
        record.setValue(value);
        record.setThreshold(thresholdDesc);
        record.setLevel(level);
        record.setMessage(sensor.getDeviceName() + " " + sensor.getSensorType() + "=" + value
                + (sensor.getUnit() == null ? "" : sensor.getUnit()) + " " + thresholdDesc);
        record.setStatus(0);
        record.setOwnerId(sensor.getOwnerId());
        record.setCreateTime(LocalDateTime.now());
        alarmRecordMapper.insert(record);

        Map<String, Object> msg = new HashMap<>();
        msg.put("type", "alarm");
        msg.put("data", record);
        WebSocketServer.broadcast(JSON.toJSONString(msg));
    }
}