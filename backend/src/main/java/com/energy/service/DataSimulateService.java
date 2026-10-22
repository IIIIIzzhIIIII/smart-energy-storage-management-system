package com.energy.service;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.energy.entity.Device;
import com.energy.entity.DeviceData;
import com.energy.mapper.DeviceDataMapper;
import com.energy.mapper.DeviceMapper;
import com.energy.websocket.WebSocketServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class DataSimulateService {

    @Autowired
    private DeviceMapper deviceMapper;

    @Autowired
    private DeviceDataMapper deviceDataMapper;

    @Autowired
    private DispatchService dispatchService;

    @Value("${energy.simulate-interval-ms}")
    private long intervalMs;

    /** 记录每个 PCS 设备的当前 SOC，保证连续变化 */
    private final Map<Long, Double> socMap = new ConcurrentHashMap<>();
    /** 记录每个设备累计电量 */
    private final Map<Long, Double> energyInMap = new ConcurrentHashMap<>();
    private final Map<Long, Double> energyOutMap = new ConcurrentHashMap<>();

    private final java.util.Random random = new java.util.Random();

    /** 每日工况档案缓存：以日期为种子，同日稳定可复现、不同日有差异，使日/月统计产生真实波动 */
    private final Map<LocalDate, DayProfile> profileCache = new ConcurrentHashMap<>();

    /**
     * 定时生成模拟数据（定时任务，论文中预留 Modbus TCP 扩展采集真实数据）
     */
    @Scheduled(fixedDelayString = "${energy.simulate-interval-ms}")
    public void generate() {
        List<Device> devices = deviceMapper.selectList(null);
        if (devices.isEmpty()) {
            return;
        }
        for (Device device : devices) {
            if ("SAFETY".equals(device.getDeviceType())) {
                continue;
            }
            try {
                DeviceData data = buildData(device, LocalDateTime.now());
                deviceDataMapper.insert(data);
                // 通过 WebSocket 广播实时数据
                String json = JSON.toJSONString(data);
                WebSocketServer.broadcast(json);
            } catch (Exception e) {
                log.error("生成设备数据失败: {}", device.getDeviceCode(), e);
            }
        }
    }

    /**
     * 生成历史模拟数据（往前 days 天，按历史粒度间隔生成，便于大屏查看历史曲线）
     *
     * @param days  往前推的天数
     * @param force 为 true 时强制重新生成（不校验是否已存在历史数据）
     * @return 生成的记录条数
     */
    @Transactional
    public int generateHistory(int days, boolean force) {
        if (days <= 0) {
            return 0;
        }
        List<Device> devices = deviceMapper.selectList(null);
        if (devices.isEmpty()) {
            return 0;
        }
        LocalDateTime start = LocalDate.now().minusDays(days).atStartOfDay();
        LocalDateTime end = LocalDate.now().atStartOfDay(); // 到今日 00:00，避免与实时数据重叠

        // 幂等：已存在早于今天的历史数据则跳过，避免重复生成（force 时忽略）
        if (!force) {
            Long existing = deviceDataMapper.selectCount(
                    new LambdaQueryWrapper<DeviceData>().lt(DeviceData::getTs, end));
            if (existing != null && existing > 0) {
                log.info("已存在历史数据，跳过历史数据生成");
                return 0;
            }
        }

        int inserted = 0;
        int stepSeconds = 60; // 历史数据按 1 分钟粒度生成
        for (LocalDateTime t = start; t.isBefore(end); t = t.plusSeconds(stepSeconds)) {
            for (Device device : devices) {
                if ("SAFETY".equals(device.getDeviceType())) {
                    continue;
                }
                try {
                    deviceDataMapper.insert(buildData(device, t));
                    inserted++;
                } catch (Exception e) {
                    log.error("生成历史数据失败: {}", e.getMessage());
                }
            }
        }
        log.info("历史数据生成完成，共 {} 条", inserted);
        return inserted;
    }

    /**
     * 每日工况档案：天气（影响光伏出力）、负荷水平、储能调度强度。
     * 以日期为随机种子，保证同一天可复现、不同天有差异，避免各日统计高度雷同。
     */
    private DayProfile profileOf(LocalDate date) {
        return profileCache.computeIfAbsent(date, d -> {
            java.util.Random r = new java.util.Random(d.toEpochDay() * 1000003L + 20260919L);
            double w = r.nextDouble();
            double clearness;
            if (w < 0.25) {
                clearness = 0.15 + r.nextDouble() * 0.20;   // 阴雨天
            } else if (w < 0.60) {
                clearness = 0.55 + r.nextDouble() * 0.20;   // 多云
            } else {
                clearness = 0.80 + r.nextDouble() * 0.20;   // 晴天
            }
            double load = 0.85 + r.nextDouble() * 0.35;
            int dow = d.getDayOfWeek().getValue();
            if (dow == 6 || dow == 7) {
                load *= 0.72;                                // 周末负荷偏低
            }
            double pcs = 0.85 + r.nextDouble() * 0.30;
            return new DayProfile(clearness, load, pcs);
        });
    }

    private DeviceData buildData(Device device, LocalDateTime ts) {
        return buildData(device, ts, profileOf(ts.toLocalDate()));
    }

    private DeviceData buildData(Device device, LocalDateTime ts, DayProfile p) {
        DeviceData data = new DeviceData();
        data.setDeviceId(device.getId());
        data.setTs(ts);

        double power = 0;
        double voltage = 220;
        double current = 0;
        Double soc = null;
        Double irradiance = null;
        double energyIn = 0;
        double energyOut = 0;

        switch (device.getDeviceType()) {
            case "PV":
                // 光伏：白天辐照度高、发电多，夜间为0
                irradiance = calcIrradiance(ts);
                power = irradiance / 1000.0 * (device.getRatedPower() == null ? 50 : device.getRatedPower());
                power = Math.max(0, power * p.clearness * (0.80 + random.nextDouble() * 0.25));
                voltage = 380 + random.nextDouble() * 30;
                current = power * 1000 / voltage;
                break;
            case "PCS":
                // 储能：跟随调度引擎的目标功率（负=充电, 正=放电）
                double curSoc = socMap.getOrDefault(device.getId(), 60.0);
                double rated = device.getRatedPower() == null ? 100 : device.getRatedPower();
                double target = dispatchService.getCurrentTarget() * p.pcsFactor;
                power = Math.max(-rated, Math.min(rated, target));
                // SOC 随充放电变化（充电↑ 放电↓）
                double dSoc = power / rated * 0.15;
                double newSoc = Math.max(5, Math.min(95, curSoc + dSoc + (random.nextDouble() - 0.5) * 0.05));
                socMap.put(device.getId(), newSoc);
                soc = newSoc;
                voltage = 400 + random.nextDouble() * 20;
                current = Math.abs(power) * 1000 / voltage;
                if (power < 0) {
                    energyIn = Math.abs(power) * intervalMs / 3600000.0;
                } else {
                    energyOut = power * intervalMs / 3600000.0;
                }
                break;
            case "LOAD":
                // 负载：消耗功率，随时间波动
                double base = device.getRatedPower() == null ? 500 : device.getRatedPower();
                power = base * (0.3 + random.nextDouble() * 0.5) * p.loadFactor;
                if (ts.getHour() >= 19 || ts.getHour() < 6) {
                    power = base * (0.1 + random.nextDouble() * 0.2) * p.loadFactor;
                }
                voltage = 220 + random.nextDouble() * 10;
                current = power * 1000 / voltage;
                break;
            case "GRID":
                // 电网：正=购电，负=上网
                power = (random.nextDouble() - 0.3) * 200 * p.loadFactor;
                voltage = 10000;
                current = Math.abs(power) * 1000 / voltage;
                if (power > 0) {
                    energyIn = power * intervalMs / 3600000.0;
                } else {
                    energyOut = Math.abs(power) * intervalMs / 3600000.0;
                }
                break;
            case "METER":
                // 电表：计量关口
                power = (100 + random.nextDouble() * 300) * p.loadFactor;
                voltage = 220 + random.nextDouble() * 10;
                current = power * 1000 / voltage;
                energyIn = power * intervalMs / 3600000.0;
                break;
            case "EV":
                // 充电桩：充电负载（正功率=消耗），跟随调度目标（谷时/光伏余电充电）
                double evTarget = dispatchService.getCurrentEvTarget();
                power = Math.max(0, evTarget);
                voltage = 380;
                current = power * 1000 / voltage;
                energyIn = power * intervalMs / 3600000.0;
                break;
            default:
                power = random.nextDouble() * 100;
                voltage = 220;
                current = power * 1000 / voltage;
        }

        data.setPower(round(power));
        data.setVoltage(round(voltage));
        data.setCurrent(round(current));
        data.setSoc(soc == null ? null : round(soc));
        data.setIrradiance(irradiance == null ? null : round(irradiance));
        // 累积电量
        double cumIn = energyInMap.getOrDefault(device.getId(), 0.0) + energyIn;
        double cumOut = energyOutMap.getOrDefault(device.getId(), 0.0) + energyOut;
        energyInMap.put(device.getId(), cumIn);
        energyOutMap.put(device.getId(), cumOut);
        data.setEnergyIn(round(cumIn));
        data.setEnergyOut(round(cumOut));

        return data;
    }

    /**
     * 根据指定时间计算辐照度（近似正弦曲线，夜间为0）
     */
    private double calcIrradiance(LocalDateTime ts) {
        int hour = ts.getHour();
        int minute = ts.getMinute();
        double hourDouble = hour + minute / 60.0;
        if (hourDouble < 6 || hourDouble > 19) {
            return 0;
        }
        // 正午最强
        double t = (hourDouble - 6) / 13.0 * Math.PI;
        double v = Math.sin(t) * 1000;
        return Math.max(0, v * (0.9 + random.nextDouble() * 0.1));
    }

    private double round(double v) {
        return Math.round(v * 100) / 100.0;
    }

    /** 单日工况系数 */
    private static class DayProfile {
        final double clearness;
        final double loadFactor;
        final double pcsFactor;

        DayProfile(double clearness, double loadFactor, double pcsFactor) {
            this.clearness = clearness;
            this.loadFactor = loadFactor;
            this.pcsFactor = pcsFactor;
        }
    }
}