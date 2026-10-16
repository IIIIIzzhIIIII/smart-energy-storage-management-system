package com.energy.service;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.energy.entity.Protocol;
import com.energy.entity.SensorData;
import com.energy.entity.SensorDevice;
import com.energy.mapper.ProtocolMapper;
import com.energy.mapper.SensorDataMapper;
import com.energy.mapper.SensorDeviceMapper;
import com.energy.websocket.WebSocketServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class SafetyDataService {

    @Autowired
    private SensorDeviceMapper sensorDeviceMapper;
    @Autowired
    private SensorDataMapper sensorDataMapper;
    @Autowired
    private ProtocolMapper protocolMapper;
    @Autowired
    private SerialPortService serialPortService;

    /** 连续采集失败多少次判定为离线并停止采集（2s 一次，默认 3 次≈6s） */
    @Value("${energy.safety.offline-threshold:3}")
    private int offlineThreshold;

    /** 各设备连续失败计数 */
    private final Map<Long, Integer> failCounts = new ConcurrentHashMap<>();

    /** 定时采集已匹配且在线传感器的数据（模拟/真实串口统一走 SerialPortService） */
    @Scheduled(fixedDelayString = "${energy.safety.collect-interval-ms:2000}")
    public void collect() {
        List<SensorDevice> sensors = sensorDeviceMapper.selectList(new LambdaQueryWrapper<SensorDevice>()
                .eq(SensorDevice::getMatchStatus, 2)
                .eq(SensorDevice::getStatus, 1));
        for (SensorDevice sensor : sensors) {
            try {
                Protocol protocol = protocolMapper.selectOne(new LambdaQueryWrapper<Protocol>()
                        .eq(Protocol::getSensorType, sensor.getSensorType())
                        .last("LIMIT 1"));
                Double value = serialPortService.readValue(protocol, sensor);
                if (value == null) {
                    onFail(sensor);
                    continue;
                }
                onSuccess(sensor, value);
            } catch (Exception e) {
                log.error("采集传感器数据失败: {}", sensor.getDeviceName(), e);
                onFail(sensor);
            }
        }
    }

    /** 采集成功：落库 + 记录最后通讯时间 + WebSocket 推送前端 */
    private void onSuccess(SensorDevice sensor, Double value) {
        failCounts.remove(sensor.getId());
        SensorData data = new SensorData();
        data.setSensorId(sensor.getId());
        data.setTs(LocalDateTime.now());
        data.setValue(value);
        data.setUnit(sensor.getUnit());
        sensorDataMapper.insert(data);

        SensorDevice update = new SensorDevice();
        update.setId(sensor.getId());
        update.setLastCommTime(data.getTs());
        update.setStatus(1);
        sensorDeviceMapper.updateById(update);

        Map<String, Object> msg = new HashMap<>();
        msg.put("type", "sensorData");
        msg.put("data", data);
        WebSocketServer.broadcast(JSON.toJSONString(msg));
    }

    /**
     * 采集失败：连续失败达到阈值即置为离线并停止采集（拔掉 USB 后不再每轮空等超时）。
     * 恢复由 SafetyProbeService 的热插拔巡检在设备重新接入后自动完成。
     */
    private void onFail(SensorDevice sensor) {
        int fails = failCounts.merge(sensor.getId(), 1, Integer::sum);
        if (fails < offlineThreshold) {
            return;
        }
        failCounts.remove(sensor.getId());
        log.warn("采集设备「{}」连续 {} 次通讯失败，置为离线并停止采集", sensor.getDeviceName(), offlineThreshold);
        SensorDevice update = new SensorDevice();
        update.setId(sensor.getId());
        update.setStatus(0);
        sensorDeviceMapper.updateById(update);
        sensor.setStatus(0);
    }
}