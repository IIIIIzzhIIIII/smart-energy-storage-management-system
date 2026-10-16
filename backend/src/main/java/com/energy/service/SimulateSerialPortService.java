package com.energy.service;

import com.energy.dto.MatchResult;
import com.energy.entity.Protocol;
import com.energy.entity.SensorDevice;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模拟串口实现：用于毕业设计答辩演示，流程与真实串口一致（探测帧 + CRC 校验），数据由程序模拟生成。
 *
 * 数值模型贴近真实工况：
 *  1) 常态为"额定值 + 极小噪声"，变化平缓，不会剧烈跳动；
 *  2) 偶发越限采用"双级平滑"：目标值分级缓慢逼近，形成 缓慢爬升 - 保持 - 缓慢回落 的渐变过程
 *     （模拟温升、产气、绝缘劣化等真实演变），而不是瞬间跳变；
 *  3) 全程钳制在物理合理区间内。
 */
@Service
@ConditionalOnProperty(name = "energy.safety.mode", havingValue = "simulate", matchIfMissing = true)
public class SimulateSerialPortService implements SerialPortService {

    /** 平滑系数：越小曲线越平缓（双重平滑后单步变化约为总差的 4%） */
    private static final double RATE = 0.15;
    /** 每次采样触发一次越限过程的概率 */
    private static final double SPIKE_PROB = 0.0025;
    /** 越限过程的保持采样次数（采集间隔 2s，25 次约 50s） */
    private static final int HOLD_STEPS = 25;

    private final Random random = new Random();

    /** 每个传感器的运行状态（额定值/目标值/当前值/保持计时） */
    private final Map<String, State> stateMap = new ConcurrentHashMap<>();

    @Override
    public MatchResult match(Protocol protocol, SensorDevice sensor) {
        MatchResult result = new MatchResult();
        try { Thread.sleep(1200); } catch (InterruptedException ignored) {}
        if (protocol == null) {
            result.setSuccess(false);
            result.setMessage("协议模板不存在，请先在协议库中配置该类型的通讯协议");
            return result;
        }
        int slave = sensor.getSlaveAddress() == null ? 1 : sensor.getSlaveAddress();
        int reg = protocol.getRegisterAddress() == null ? 0 : protocol.getRegisterAddress();
        String frame = String.format("请求帧 [%02X 03 %04X 0001 CRC16]", slave, reg);
        result.setSuccess(true);
        result.setMessage("协议匹配成功：" + protocol.getProtocolName() + "，" + frame + "，从站 " + slave + " 响应正常，CRC 校验通过");
        result.setSampleValue(readValue(protocol, sensor));
        return result;
    }

    @Override
    public Double readValue(Protocol protocol, SensorDevice sensor) {
        return genValue(sensor);
    }

    @Override
    public boolean isSimulate() {
        return true;
    }

    /**
     * 生成模拟采集值：常态下仅叠加极小噪声，越限时按双级平滑缓慢逼近目标值，全程限制在物理区间内。
     */
    private double genValue(SensorDevice sensor) {
        String type = sensor.getSensorType();
        String key = keyOf(sensor);
        // 状态量（烟感 / 硬接点 / BMS告警）：0 正常，1 触发
        if ("SMOKE".equals(type) || "DI".equals(type) || "BMS".equals(type)) {
            return random.nextDouble() < 0.01 ? 1 : 0;
        }
        Range r = rangeOf(type);
        State st = stateMap.computeIfAbsent(key, k -> new State(r.nominal + (random.nextDouble() - 0.5) * r.jitter));
        // 越限保持计时结束，目标回到额定值，开始缓慢回落
        if (st.hold > 0) {
            st.hold--;
            if (st.hold == 0) {
                st.goal = st.nominal;
            }
        } else if (random.nextDouble() < SPIKE_PROB) {
            st.goal = spikeTarget(sensor, st.nominal, r);
            st.hold = HOLD_STEPS;
        }
        // 第一级平滑：目标值缓慢逼近最终目标
        st.target = st.target + (st.goal - st.target) * RATE;
        // 第二级平滑：当前值缓慢逼近目标值，并叠加极小测量噪声
        double next = st.current + (st.target - st.current) * RATE + (random.nextDouble() - 0.5) * r.noise;
        next = clamp(next, r.min, r.max);
        st.current = next;
        return Math.round(next * 100.0) / 100.0;
    }

    /** 越限目标值：优先按设备自身配置的阈值生成，落在预警与紧急之间 */
    private double spikeTarget(SensorDevice sensor, double nominal, Range r) {
        if (sensor.getWarnMin() != null && sensor.getEmergMin() != null) {
            double lo = sensor.getEmergMin();
            double hi = sensor.getWarnMin();
            return lo + random.nextDouble() * Math.max(0.01, hi - lo);
        }
        if (sensor.getWarnMax() != null) {
            double lo = sensor.getWarnMax();
            double hi = sensor.getEmergMax() != null ? sensor.getEmergMax() : lo * 1.2;
            return lo + random.nextDouble() * Math.max(0.01, hi - lo);
        }
        return nominal + r.spike;
    }

    private double clamp(double v, double min, double max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }

    /**
     * 状态缓存键：正常按设备ID；新增设备在落库前 id 为 null，
     * 此时用"类型 + 从站地址"生成临时键，避免 ConcurrentHashMap 空键异常。
     */
    private String keyOf(SensorDevice sensor) {
        if (sensor != null && sensor.getId() != null) {
            return "ID:" + sensor.getId();
        }
        String type = sensor == null ? "" : String.valueOf(sensor.getSensorType());
        String addr = sensor == null ? "" : String.valueOf(sensor.getSlaveAddress());
        return "TMP:" + type + ":" + addr;
    }

    /** 各类型物理区间与波动参数：额定值、额定抖动、单步噪声、最小值、最大值、越限偏移 */
    private Range rangeOf(String type) {
        switch (type == null ? "" : type) {
            case "TEMP": return new Range(42, 8, 0.15, 15, 90, 30);
            case "GAS": return new Range(4, 3, 0.08, 0, 60, 30);
            case "HUMIDITY": return new Range(60, 15, 0.25, 20, 98, 35);
            case "VOLTAGE": return new Range(3.20, 0.06, 0.004, 2.0, 4.0, 0.5);
            case "CURRENT": return new Range(60, 40, 1.5, -250, 250, 130);
            case "INSULATION": return new Range(6, 2, 0.05, 0.1, 20, -5.5);
            default: return new Range(30, 5, 0.3, 0, 100, 20);
        }
    }

    private static class Range {
        final double nominal;
        final double jitter;
        final double noise;
        final double min;
        final double max;
        final double spike;

        Range(double nominal, double jitter, double noise, double min, double max, double spike) {
            this.nominal = nominal;
            this.jitter = jitter;
            this.noise = noise;
            this.min = min;
            this.max = max;
            this.spike = spike;
        }
    }

    /** 单个传感器的运行状态 */
    private static class State {
        final double nominal;
        double goal;
        double target;
        double current;
        int hold;

        State(double nominal) {
            this.nominal = nominal;
            this.goal = nominal;
            this.target = nominal;
            this.current = nominal;
            this.hold = 0;
        }
    }
}