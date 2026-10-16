package com.energy.service;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.energy.context.PermissionUtil;
import com.energy.context.UserContext;
import com.energy.entity.Device;
import com.energy.entity.DeviceData;
import com.energy.mapper.DeviceDataMapper;
import com.energy.mapper.DeviceMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class MonitorService {

    @Autowired
    private DeviceMapper deviceMapper;

    @Autowired
    private DeviceDataMapper deviceDataMapper;

    /**
     * 获取当前用户所有设备的最新数据快照
     */
    public List<Map<String, Object>> latestSnapshot() {
        List<Device> devices = getMyDevices();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Device device : devices) {
            DeviceData data = deviceDataMapper.selectOne(
                    new LambdaQueryWrapper<DeviceData>()
                            .eq(DeviceData::getDeviceId, device.getId())
                            .orderByDesc(DeviceData::getId)
                            .last("LIMIT 1"));
            Map<String, Object> map = new HashMap<>();
            map.put("device", device);
            map.put("data", data);
            result.add(map);
        }
        return result;
    }

    /**
     * 能量流桑基图数据
     * 计算当前时刻：光伏出力、储能充放电、负载用电、电网交互
     */
    public Map<String, Object> energyFlow() {
        List<Device> devices = getMyDevices();
        double pvPower = 0;
        double loadPower = 0;
        double pcsPower = 0;   // 正=放电, 负=充电
        double gridPower = 0;  // 正=购电, 负=上网
        double evPower = 0;    // 充电桩充电功率

        Map<Long, Device> deviceMap = new HashMap<>();
        for (Device d : devices) {
            deviceMap.put(d.getId(), d);
        }
        for (Device device : devices) {
            DeviceData data = deviceDataMapper.selectOne(
                    new LambdaQueryWrapper<DeviceData>()
                            .eq(DeviceData::getDeviceId, device.getId())
                            .orderByDesc(DeviceData::getId)
                            .last("LIMIT 1"));
            if (data == null || data.getPower() == null) {
                continue;
            }
            switch (device.getDeviceType()) {
                case "PV":
                    pvPower = data.getPower();
                    break;
                case "PCS":
                    pcsPower = data.getPower();
                    break;
                case "LOAD":
                    loadPower = data.getPower();
                    break;
                case "GRID":
                    gridPower = data.getPower();
                    break;
                case "EV":
                    evPower = data.getPower();
                    break;
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("pvPower", pvPower);
        result.put("loadPower", loadPower);
        result.put("pcsPower", pcsPower);
        result.put("gridPower", gridPower);
        result.put("evPower", evPower);
        result.put("chargePower", pcsPower < 0 ? -pcsPower : 0);
        result.put("dischargePower", pcsPower > 0 ? pcsPower : 0);
        result.put("gridImport", gridPower > 0 ? gridPower : 0);
        result.put("gridExport", gridPower < 0 ? -gridPower : 0);
        result.put("time", LocalDateTime.now());
        return result;
    }

    /**
     * 历史数据查询
     */
    public List<DeviceData> history(Long deviceId, LocalDateTime start, LocalDateTime end) {
        checkDevicePermission(deviceId);
        return deviceDataMapper.history(deviceId, start, end);
    }

    /**
     * 按天聚合的功率曲线数据（0:00 - 24:00，按分钟聚合）
     * 返回指定日期当天光伏/负载/储能三类设备的平均功率序列
     */
    public Map<String, Object> dayCurve(LocalDate date) {
        List<Device> devices = getMyDevices();
        Map<Long, String> typeMap = new HashMap<>();
        for (Device d : devices) {
            typeMap.put(d.getId(), d.getDeviceType());
        }

        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.plusDays(1).atStartOfDay();
        List<DeviceData> list = deviceDataMapper.selectByTimeRange(start, end);

        int buckets = 24 * 60; // 一天 1440 分钟
        double[] pvSum = new double[buckets];
        double[] pvCnt = new double[buckets];
        double[] loadSum = new double[buckets];
        double[] loadCnt = new double[buckets];
        double[] pcsSum = new double[buckets];
        double[] pcsCnt = new double[buckets];

        for (DeviceData d : list) {
            String type = typeMap.get(d.getDeviceId());
            if (type == null || d.getPower() == null) {
                continue;
            }
            int minute = d.getTs().getHour() * 60 + d.getTs().getMinute();
            if (minute < 0 || minute >= buckets) {
                continue;
            }
            double p = d.getPower();
            switch (type) {
                case "PV":
                    pvSum[minute] += p;
                    pvCnt[minute]++;
                    break;
                case "LOAD":
                    loadSum[minute] += p;
                    loadCnt[minute]++;
                    break;
                case "PCS":
                    pcsSum[minute] += p;
                    pcsCnt[minute]++;
                    break;
                default:
                    break;
            }
        }

        List<String> times = new ArrayList<>(buckets);
        List<Double> pv = new ArrayList<>(buckets);
        List<Double> load = new ArrayList<>(buckets);
        List<Double> pcs = new ArrayList<>(buckets);
        for (int i = 0; i < buckets; i++) {
            times.add(String.format("%02d:%02d", i / 60, i % 60));
            pv.add(pvCnt[i] > 0 ? round(pvSum[i] / pvCnt[i]) : null);
            load.add(loadCnt[i] > 0 ? round(loadSum[i] / loadCnt[i]) : null);
            pcs.add(pcsCnt[i] > 0 ? round(pcsSum[i] / pcsCnt[i]) : null);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("date", date.toString());
        result.put("times", times);
        result.put("pv", pv);
        result.put("load", load);
        result.put("pcs", pcs);
        return result;
    }

    private double round(double v) {
        return Math.round(v * 100) / 100.0;
    }

    private List<Device> getMyDevices() {
        LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<>();
        if (!PermissionUtil.isAdmin()) {
            wrapper.eq(Device::getOwnerId, UserContext.getUserId());
        }
        wrapper.orderByAsc(Device::getId);
        return deviceMapper.selectList(wrapper);
    }

    private void checkDevicePermission(Long deviceId) {
        Device device = deviceMapper.selectById(deviceId);
        if (device == null) {
            return;
        }
        if (!PermissionUtil.isAdmin() && !device.getOwnerId().equals(UserContext.getUserId())) {
            throw new RuntimeException("无权查看该设备数据");
        }
    }
}