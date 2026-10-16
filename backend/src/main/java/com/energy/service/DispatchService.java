package com.energy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.energy.entity.*;
import com.energy.mapper.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 调度引擎（规则式，阶段2）：
 * 读取当前状态（光伏/负荷/SOC）-> 判断峰平谷时段 -> 匹配启用策略 -> 计算 PCS 目标功率。
 *
 * 目前实现两种策略：
 *  - PEAK_VALLEY 削峰填谷：谷时充电、峰时放电、平时待机
 *  - SELF_USE    自发自用：光伏余电充电、光伏不足放电补足
 *
 * 计算结果写入 dispatch_command 表，并通过 getCurrentTarget() 反馈给数据模拟器，
 * 使模拟的 PCS 功率跟随调度指令（体现"削峰填谷/自发自用"场景）。
 */
@Slf4j
@Service
public class DispatchService {

    @Autowired
    private DeviceMapper deviceMapper;

    @Autowired
    private DeviceDataMapper deviceDataMapper;

    @Autowired
    private StrategyMapper strategyMapper;

    @Autowired
    private DispatchCommandMapper dispatchCommandMapper;

    @Autowired
    private PeakValleyPeriodMapper peakValleyPeriodMapper;

    @Autowired
    private ChargingSessionMapper chargingSessionMapper;

    @Value("${energy.dispatch-interval-ms:10000}")
    private long dispatchIntervalMs;

    /** 当前 PCS 目标功率（正=放电, 负=充电），供数据模拟器读取 */
    private volatile double currentTarget = 0.0;
    /** 最近一次调度指令 */
    private volatile DispatchCommand latestCommand;

    /** 当前充电桩目标功率（kW），供数据模拟器读取 */
    private volatile double currentEvTarget = 0.0;
    /** 当前充电订单（进行中） */
    private volatile Long activeSessionId;
    private volatile double sessionEnergy = 0.0;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 定时执行调度决策
     */
    @Scheduled(fixedDelayString = "${energy.dispatch-interval-ms:10000}")
    public void dispatch() {
        try {
            Device pcs = findPcs();
            if (pcs == null) {
                return;
            }
            Double pv = latestPowerSum("PV");
            Double load = latestPowerSum("LOAD");
            Double soc = latestSoc(pcs.getId());
            // 无数据时跳过（系统刚启动）
            if (pv == null || load == null) {
                return;
            }

            PeakValleyPeriod period = periodOf(LocalTime.now());
            Strategy strategy = topEnabledStrategy();
            double rated = pcs.getRatedPower() == null ? 100 : pcs.getRatedPower();

            double target;
            String reason;
            if (strategy == null) {
                target = 0;
                reason = "无启用策略";
            } else if ("PEAK_VALLEY".equals(strategy.getStrategyType())) {
                target = peakValley(period, soc, rated);
                reason = peakValleyReason(period, target);
            } else if ("SELF_USE".equals(strategy.getStrategyType())) {
                target = selfUse(pv, load, soc, rated);
                reason = selfUseReason(pv, load, target);
            } else {
                target = 0;
                reason = "策略类型未实现: " + strategy.getStrategyType();
            }

            currentTarget = target;

            // 充电桩调度 + 充电订单跟踪
            Device ev = findEv();
            double evTarget = 0;
            if (ev != null) {
                double evRated = ev.getRatedPower() == null ? 60 : ev.getRatedPower();
                evTarget = computeEvTarget(strategy, period, evRated, pv, load);
            }
            currentEvTarget = evTarget;
            trackChargingSession(ev == null ? null : ev.getId(), evTarget);

            DispatchCommand cmd = new DispatchCommand();
            cmd.setDeviceId(pcs.getId());
            cmd.setStrategyId(strategy == null ? null : strategy.getId());
            cmd.setStrategyType(strategy == null ? null : strategy.getStrategyType());
            cmd.setPeriodType(period == null ? "FLAT" : period.getPeriodType());
            cmd.setTargetPower(round(target));
            cmd.setSoc(soc == null ? null : round(soc));
            cmd.setPvPower(round(pv));
            cmd.setLoadPower(round(load));
            cmd.setReason(reason);
            cmd.setCreateTime(LocalDateTime.now());
            dispatchCommandMapper.insert(cmd);
            latestCommand = cmd;
        } catch (Exception e) {
            log.error("调度决策失败: {}", e.getMessage());
        }
    }

    /** 当前 PCS 目标功率，供数据模拟器调用 */
    public double getCurrentTarget() {
        return currentTarget;
    }

    /** 当前充电桩目标功率，供数据模拟器调用 */
    public double getCurrentEvTarget() {
        return currentEvTarget;
    }

    /**
     * 当前调度状态（供前端大屏展示）
     */
    public Map<String, Object> current() {
        Map<String, Object> result = new LinkedHashMap<>();
        PeakValleyPeriod period = periodOf(LocalTime.now());
        Strategy strategy = topEnabledStrategy();

        Map<String, Object> periodMap = new LinkedHashMap<>();
        if (period != null) {
            periodMap.put("type", period.getPeriodType());
            periodMap.put("name", period.getName());
            periodMap.put("price", period.getPrice());
        } else {
            periodMap.put("type", "FLAT");
            periodMap.put("name", "平时");
            periodMap.put("price", 0.60);
        }
        result.put("period", periodMap);

        Map<String, Object> strategyMap = new LinkedHashMap<>();
        if (strategy != null) {
            strategyMap.put("id", strategy.getId());
            strategyMap.put("name", strategy.getName());
            strategyMap.put("type", strategy.getStrategyType());
        }
        result.put("strategy", strategyMap);

        if (latestCommand != null) {
            result.put("targetPower", latestCommand.getTargetPower());
            result.put("soc", latestCommand.getSoc());
            result.put("pvPower", latestCommand.getPvPower());
            result.put("loadPower", latestCommand.getLoadPower());
            result.put("reason", latestCommand.getReason());
        } else {
            result.put("targetPower", currentTarget);
            result.put("soc", null);
            result.put("pvPower", null);
            result.put("loadPower", null);
            result.put("reason", "等待首次调度");
        }
        Map<String, Object> charging = new LinkedHashMap<>();
        charging.put("active", activeSessionId != null);
        charging.put("targetPower", round(currentEvTarget));
        charging.put("sessionEnergy", round(sessionEnergy));
        result.put("charging", charging);

        result.put("updateTime", LocalDateTime.now().format(TIME_FMT));
        result.put("recent", recent(10));
        return result;
    }

    /**
     * 充电状态 + 最近充电订单（供大屏展示）
     */
    public Map<String, Object> chargingStatus() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("active", activeSessionId != null);
        result.put("targetPower", round(currentEvTarget));
        result.put("sessionEnergy", round(sessionEnergy));
        result.put("sessions", chargingSessionMapper.selectList(
                new LambdaQueryWrapper<ChargingSession>()
                        .orderByDesc(ChargingSession::getId)
                        .last("LIMIT 20")));
        return result;
    }

    /** 最近 N 条调度指令 */
    public List<DispatchCommand> recent(int limit) {
        return dispatchCommandMapper.selectList(
                new LambdaQueryWrapper<DispatchCommand>()
                        .orderByDesc(DispatchCommand::getId)
                        .last("LIMIT " + Math.max(1, Math.min(limit, 100))));
    }

    /** 峰平谷时段配置 */
    public List<PeakValleyPeriod> periods() {
        return peakValleyPeriodMapper.selectList(
                new LambdaQueryWrapper<PeakValleyPeriod>()
                        .eq(PeakValleyPeriod::getEnabled, 1)
                        .orderByAsc(PeakValleyPeriod::getId));
    }

    // ==================== 策略逻辑 ====================

    private double peakValley(PeakValleyPeriod period, Double soc, double rated) {
        if (period == null) {
            return 0;
        }
        switch (period.getPeriodType()) {
            case "VALLEY": // 谷时充电
                if (soc != null && soc >= 90) return 0;
                return -rated;
            case "PEAK":   // 峰时放电
                if (soc != null && soc <= 20) return 0;
                return rated;
            default:       // 平段待机
                return 0;
        }
    }

    private String peakValleyReason(PeakValleyPeriod period, double target) {
        if (period == null) {
            return "平段待机";
        }
        if (target < 0) return period.getName() + "充电";
        if (target > 0) return period.getName() + "放电";
        return "平段待机";
    }

    private double selfUse(double pv, double load, Double soc, double rated) {
        double diff = pv - load; // 光伏余量（正=有余，负=缺口）
        if (diff > 0) {
            if (soc != null && soc >= 90) return 0;
            return -Math.min(diff, rated); // 余电充电
        } else {
            if (soc != null && soc <= 20) return 0;
            return Math.min(-diff, rated); // 放电补足
        }
    }

    private String selfUseReason(double pv, double load, double target) {
        if (target < 0) return "光伏余电充电";
        if (target > 0) return "光伏不足，储能放电补足";
        return "光储平衡";
    }

    /**
     * 充电桩目标功率（光储充一体化）：
     *  - 削峰填谷：谷时满功率充电、峰时停止（避峰）、平时半功率
     *  - 自发自用：光伏余电充电、无余电不充电
     */
    private double computeEvTarget(Strategy strategy, PeakValleyPeriod period, double evRated, double pv, double load) {
        if (strategy == null) {
            return 0;
        }
        if ("PEAK_VALLEY".equals(strategy.getStrategyType())) {
            if (period == null) return 0;
            switch (period.getPeriodType()) {
                case "VALLEY":
                    return evRated;            // 谷时满功率充电
                case "PEAK":
                    return 0;                  // 峰时停止充电（避峰）
                default:
                    return evRated * 0.5;      // 平时半功率
            }
        } else if ("SELF_USE".equals(strategy.getStrategyType())) {
            double surplus = pv - load;        // 光伏余量
            if (surplus > 0) {
                return Math.min(evRated, surplus); // 光伏余电充电
            }
            return 0;                          // 无余电不充电
        }
        return 0;
    }

    /**
     * 充电订单跟踪：开始充电建订单、累积电量、停止充电时结单
     */
    private void trackChargingSession(Long evId, double evTarget) {
        try {
            if (evTarget > 0.1) {
                if (activeSessionId == null) {
                    ChargingSession s = new ChargingSession();
                    s.setDeviceId(evId);
                    s.setStartTime(LocalDateTime.now());
                    s.setEnergy(0.0);
                    s.setPower(round(evTarget));
                    s.setStatus(0);
                    chargingSessionMapper.insert(s);
                    activeSessionId = s.getId();
                    sessionEnergy = 0.0;
                }
                sessionEnergy += evTarget * dispatchIntervalMs / 3600000.0;
                ChargingSession upd = new ChargingSession();
                upd.setId(activeSessionId);
                upd.setEnergy(round(sessionEnergy));
                upd.setPower(round(evTarget));
                chargingSessionMapper.updateById(upd);
            } else if (activeSessionId != null) {
                ChargingSession upd = new ChargingSession();
                upd.setId(activeSessionId);
                upd.setEndTime(LocalDateTime.now());
                upd.setEnergy(round(sessionEnergy));
                upd.setStatus(1);
                chargingSessionMapper.updateById(upd);
                activeSessionId = null;
            }
        } catch (Exception e) {
            log.warn("充电订单跟踪失败: {}", e.getMessage());
        }
    }

    // ==================== 辅助方法 ====================

    private Strategy topEnabledStrategy() {
        return strategyMapper.selectOne(
                new LambdaQueryWrapper<Strategy>()
                        .eq(Strategy::getEnabled, 1)
                        .orderByDesc(Strategy::getPriority)
                        .orderByAsc(Strategy::getId)
                        .last("LIMIT 1"));
    }

    private PeakValleyPeriod periodOf(LocalTime time) {
        List<PeakValleyPeriod> periods = peakValleyPeriodMapper.selectList(
                new LambdaQueryWrapper<PeakValleyPeriod>().eq(PeakValleyPeriod::getEnabled, 1));
        int hour = time.getHour();
        for (PeakValleyPeriod p : periods) {
            if (p.getStartHour() == null || p.getEndHour() == null) continue;
            int start = p.getStartHour();
            int end = p.getEndHour();
            if (start < end) {
                if (hour >= start && hour < end) return p;
            } else { // 跨天
                if (hour >= start || hour < end) return p;
            }
        }
        return null;
    }

    private Device findPcs() {
        return deviceMapper.selectOne(
                new LambdaQueryWrapper<Device>()
                        .eq(Device::getDeviceType, "PCS")
                        .orderByAsc(Device::getId)
                        .last("LIMIT 1"));
    }

    private Device findEv() {
        return deviceMapper.selectOne(
                new LambdaQueryWrapper<Device>()
                        .eq(Device::getDeviceType, "EV")
                        .orderByAsc(Device::getId)
                        .last("LIMIT 1"));
    }

    private Double latestPowerSum(String deviceType) {
        List<Device> devices = deviceMapper.selectList(
                new LambdaQueryWrapper<Device>().eq(Device::getDeviceType, deviceType));
        double sum = 0;
        boolean has = false;
        for (Device d : devices) {
            DeviceData data = deviceDataMapper.selectOne(
                    new LambdaQueryWrapper<DeviceData>()
                            .eq(DeviceData::getDeviceId, d.getId())
                            .orderByDesc(DeviceData::getId)
                            .last("LIMIT 1"));
            if (data != null && data.getPower() != null) {
                sum += data.getPower();
                has = true;
            }
        }
        return has ? sum : null;
    }

    private Double latestSoc(Long pcsId) {
        DeviceData data = deviceDataMapper.selectOne(
                new LambdaQueryWrapper<DeviceData>()
                        .eq(DeviceData::getDeviceId, pcsId)
                        .orderByDesc(DeviceData::getId)
                        .last("LIMIT 1"));
        return data == null ? null : data.getSoc();
    }

    private double round(double v) {
        return Math.round(v * 100) / 100.0;
    }
}

