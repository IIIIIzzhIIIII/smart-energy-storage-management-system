package com.energy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.energy.context.PermissionUtil;
import com.energy.context.UserContext;
import com.energy.entity.Device;
import com.energy.mapper.DeviceDataMapper;
import com.energy.mapper.DeviceMapper;
import com.energy.mapper.EnergyReportMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class ReportService {

    @Autowired
    private DeviceMapper deviceMapper;

    @Autowired
    private DeviceDataMapper deviceDataMapper;

    @Autowired
    private EnergyReportMapper energyReportMapper;

    @Value("${energy.simulate-interval-ms}")
    private long intervalMs;

    // 峰平谷电价（元/kWh）
    private static final double PRICE_PEAK = 0.95;
    private static final double PRICE_FLAT = 0.60;
    private static final double PRICE_VALLEY = 0.35;

    /**
     * 某一自然日统计
     */
    public Map<String, Object> dayReport(LocalDate date) {
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.plusDays(1).atStartOfDay();
        return computeReport(start, end, "DAY", date.format(DateTimeFormatter.ISO_DATE));
    }

    /**
     * 某一月统计
     */
    public Map<String, Object> monthReport(LocalDate month) {
        LocalDate first = month.withDayOfMonth(1);
        LocalDateTime start = first.atStartOfDay();
        LocalDateTime end = first.plusMonths(1).atStartOfDay();
        return computeReport(start, end, "MONTH", month.format(DateTimeFormatter.ofPattern("yyyy-MM")));
    }

    /**
     * 近7天趋势
     */
    public List<Map<String, Object>> last7Days() {
        List<Map<String, Object>> result = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = 6; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            Map<String, Object> d = dayReport(day);
            d.put("date", day.toString());
            result.add(d);
        }
        return result;
    }

    private Map<String, Object> computeReport(LocalDateTime start, LocalDateTime end, String period, String periodValue) {
        List<Device> devices = getMyDevices();


        double generation = 0;    // 光伏发电
        double consumption = 0;   // 负载用电
        double charge = 0;        // 储能充电
        double discharge = 0;     // 储能放电
        double gridImport = 0;    // 购电
        double gridExport = 0;    // 上网
        double evCharging = 0;    // 充电桩充电

        for (Device device : devices) {
            Double pos = deviceDataMapper.sumPositivePower(device.getId(), start, end);
            Double neg = deviceDataMapper.sumNegativePower(device.getId(), start, end);
            double p = pos == null ? 0 : pos;
            double n = neg == null ? 0 : neg;
            // 按该设备在本时段的实际采样间隔折算电量(kWh)，兼容历史(1分钟/条)与实时(秒级)两种粒度
            Double sampleSec = deviceDataMapper.avgIntervalSeconds(device.getId(), start, end);
            double intervalHours = (sampleSec == null || sampleSec <= 0)
                    ? intervalMs / 3600000.0 : sampleSec / 3600.0;
            double pEnergy = p * intervalHours;
            double nEnergy = n * intervalHours;
            switch (device.getDeviceType()) {
                case "PV":
                    generation = pEnergy;
                    break;
                case "PCS":
                    discharge = pEnergy;   // 放电
                    charge = nEnergy;      // 充电
                    break;
                case "LOAD":
                    consumption = pEnergy;
                    break;
                case "GRID":
                    gridImport = pEnergy;
                    gridExport = nEnergy;
                    break;
                case "EV":
                    evCharging = pEnergy;  // 充电桩只消耗
                    break;
            }
        }

        // 收益模型：自发自用节省 + 峰谷套利(放电按峰价卖出、充电按谷价买入) - 购电成本
        double selfUseSaving = generation * PRICE_FLAT;
        double arbitrage = discharge * PRICE_PEAK - charge * PRICE_VALLEY;
        double gridCost = gridImport * PRICE_FLAT;
        double revenue = selfUseSaving + arbitrage - gridCost;
        // 若有上网电量，按平价卖电
        revenue += gridExport * PRICE_FLAT;

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("period", period);
        map.put("periodValue", periodValue);
        map.put("generation", round(generation));
        map.put("consumption", round(consumption));
        map.put("charge", round(charge));
        map.put("discharge", round(discharge));
        map.put("gridImport", round(gridImport));
        map.put("gridExport", round(gridExport));
        map.put("evCharging", round(evCharging));
        map.put("revenue", round(revenue));
        return map;
    }

    private List<Device> getMyDevices() {
        LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<>();
        if (!PermissionUtil.isAdmin()) {
            wrapper.eq(Device::getOwnerId, UserContext.getUserId());
        }
        wrapper.orderByAsc(Device::getId);
        return deviceMapper.selectList(wrapper);
    }

    private double round(double v) {
        return Math.round(v * 100) / 100.0;
    }
}