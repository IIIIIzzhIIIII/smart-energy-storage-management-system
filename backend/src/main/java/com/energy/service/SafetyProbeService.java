package com.energy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.energy.dto.FrameRecord;
import com.energy.dto.IdentifyResult;
import com.energy.dto.PortInfo;
import com.energy.entity.SensorDevice;
import com.energy.mapper.SensorDeviceMapper;
import com.fazecast.jSerialComm.SerialPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.stream.Collectors;

/**
 * 串口探测 / 传感器识别 / 热插拔管理。
 *
 * 识别策略：依次尝试候选波特率 × 候选从站地址，发送 Modbus 读寄存器帧
 * （0x0000 起 2 个寄存器，即湿度 + 温度），只有"地址 + 功能码 + 字节数 + CRC"全部校验
 * 通过才认定匹配成功，避免误把噪声当成设备应答。
 *
 * 热插拔语义：
 *  - 端口从系统串口列表中消失（拔掉 USB-RS485）→ 该端口下所有采集设备置为离线并停止采集；
 *  - 端口重新出现 → 自动重新识别，识别成功后自动恢复采集（无需人工点击"匹配"）；
 *    为了不空转，识别失败时同端口的重试间隔受 offline-recheck-ms 限制。
 */
@Slf4j
@Service
public class SafetyProbeService {

    @Autowired
    private SerialChannelManager channelManager;
    @Autowired
    private SerialFrameLog frameLog;
    @Autowired
    private SensorDeviceMapper sensorDeviceMapper;

    @Value("${energy.safety.mode:simulate}")
    private String mode;

    @Value("${energy.safety.serial-port:COM5}")
    private String defaultPort;

    /** 候选波特率：说明书默认 4800，同时兼容 9600/19200/38400 */
    @Value("${energy.safety.identify-baud-rates:4800,9600,19200,38400}")
    private String baudRateCandidates;

    /** 候选从站地址：出厂默认 1，同时兼容多台级联 */
    @Value("${energy.safety.identify-slave-addresses:1,2,3,4}")
    private String slaveCandidates;

    /** 同一端口识别失败后的重试间隔（毫秒），避免热插拔巡检空转占用总线 */
    @Value("${energy.safety.offline-recheck-ms:10000}")
    private long offlineRecheckMs;

    /** 传感器寄存器块：0x0000(湿度) 起 2 个寄存器 */
    @Value("${energy.safety.register-start:0}")
    private int registerStart;

    @Value("${energy.safety.register-count:2}")
    private int registerCount;

    private final Map<String, Long> lastProbeAt = new ConcurrentHashMap<>();

    /** 枚举本机串口 */
    public List<PortInfo> scanPorts() {
        return channelManager.listPorts();
    }

    /** 列出当前可用串口号，用于失败提示 */
    public String availablePortText() {
        List<PortInfo> ports = channelManager.listPorts();
        if (ports.isEmpty()) {
            return "无";
        }
        return ports.stream().map(PortInfo::getPortName).collect(Collectors.joining(", "));
    }

    /**
     * 识别端口上的温湿度变送器：自动探测波特率与从站地址。
     *
     * @param portName  串口号，为空时用配置的默认端口
     * @param baudHint  已知波特率（优先尝试，命中立即返回）
     * @param slaveHint 已知从站地址（优先尝试）
     */
    public IdentifyResult identify(String portName, Integer baudHint, Integer slaveHint) {
        IdentifyResult result = new IdentifyResult();
        String port = StringUtils.hasText(portName) ? portName.trim() : defaultPort;
        result.setPortName(port);

        if (!channelManager.isPresent(port)) {
            channelManager.invalidate(port);
            result.setSuccess(false);
            result.setMessage("串口 " + port + " 未检测到，请确认 USB-RS485 已插好、驱动已安装（当前可用串口："
                    + availablePortText() + "）");
            return result;
        }

        List<Integer> bauds = candidateList(baudHint, baudRateCandidates);
        List<Integer> slaves = candidateList(slaveHint, slaveCandidates);

        String lastMessage = "从站无响应";
        for (Integer baud : bauds) {
            for (Integer slave : slaves) {
                Probe probe = probe(port, baud, slave, registerStart, registerCount);
                result.getFrames().add(probe.record);
                if (Boolean.TRUE.equals(probe.record.getSuccess())) {
                    result.setSuccess(true);
                    result.setBaudRate(baud);
                    result.setSlaveAddress(slave);
                    result.setRequestFrame(probe.record.getRequestFrame());
                    result.setResponseFrame(probe.record.getResponseFrame());
                    result.setHumidity(ModbusRtu.toValue(ModbusRtu.rawRegister(probe.raw, 0), "UINT16", 0.1, 2));
                    result.setTemperature(ModbusRtu.toValue(ModbusRtu.rawRegister(probe.raw, 1), "INT16", 0.1, 2));
                    result.setMessage("识别成功：端口 " + port + "，波特率 " + baud + "，从站地址 " + slave
                            + "，湿度 " + fmt(result.getHumidity()) + " %RH，温度 " + fmt(result.getTemperature()) + " ℃，CRC 校验通过");
                    lastProbeAt.remove(port);
                    return result;
                }
                lastMessage = probe.record.getMessage();
                // 串口本身打不开（被其它程序占用 / 已被拔出）时，换波特率或从站也必然失败，直接结束
                if (lastMessage != null && lastMessage.contains("串口打开失败")) {
                    result.setSuccess(false);
                    result.setMessage("串口 " + port + " 无法打开：" + lastMessage
                            + "。请先关闭占用该串口的程序（如 UartAssist 串口助手）再重试");
                    return result;
                }
            }
        }

        result.setSuccess(false);
        result.setMessage("端口 " + port + " 上未识别到温湿度变送器（已尝试波特率 " + bauds + "、从站 " + slaves + "）："
                + lastMessage + "。请检查 485 的 A/B 接线是否接反、供电是否正常、从站地址与波特率是否与说明书一致");
        return result;
    }

    /** 候选值列表：提示值优先，再补配置候选，去重 */
    private List<Integer> candidateList(Integer hint, String candidates) {
        List<Integer> list = new ArrayList<>();
        if (hint != null && hint > 0) {
            list.add(hint);
        }
        for (String token : candidates.split(",")) {
            String t = token.trim();
            if (!StringUtils.hasText(t)) {
                continue;
            }
            try {
                int v = Integer.parseInt(t);
                if (!list.contains(v)) {
                    list.add(v);
                }
            } catch (NumberFormatException ignored) {
                // 忽略非法配置项
            }
        }
        return list;
    }

    private String fmt(Double v) {
        return v == null ? "-" : String.valueOf(v);
    }

    /**
     * 热插拔巡检：拔掉 → 置离线停采；插回 → 自动重新识别并恢复采集。
     * 仅在真实串口模式（mode=real）下生效。
     */
    @Scheduled(fixedDelayString = "${energy.safety.watch-interval-ms:3000}")
    public void watchPlugState() {
        if (!"real".equalsIgnoreCase(mode)) {
            return;
        }
        List<SensorDevice> sensors = sensorDeviceMapper.selectList(new LambdaQueryWrapper<SensorDevice>()
                .isNotNull(SensorDevice::getPortName));
        if (sensors == null || sensors.isEmpty()) {
            return;
        }

        // 按串口分组（保持顺序，便于日志阅读）
        Map<String, List<SensorDevice>> byPort = sensors.stream()
                .filter(s -> StringUtils.hasText(s.getPortName()))
                .collect(Collectors.groupingBy(SensorDevice::getPortName, LinkedHashMap::new, Collectors.toList()));

        for (Map.Entry<String, List<SensorDevice>> entry : byPort.entrySet()) {
            String port = entry.getKey();
            List<SensorDevice> list = entry.getValue();

            // 1) 端口消失：USB-RS485 被拔掉 → 停止采集 + 置离线
            if (!channelManager.isPresent(port)) {
                channelManager.invalidate(port);
                for (SensorDevice s : list) {
                    if (s.getStatus() == null || s.getStatus() != 0) {
                        s.setStatus(0);
                        sensorDeviceMapper.updateById(s);
                        log.warn("串口 {} 已拔出，采集设备「{}」置为离线并停止采集", port, s.getDeviceName());
                    }
                }
                continue;
            }

            // 2) 端口在线且存在离线设备：自动重新匹配（拔插后无需人工干预即可恢复）
            boolean hasOffline = list.stream().anyMatch(s -> s.getStatus() == null || s.getStatus() != 1);
            if (!hasOffline) {
                continue;
            }
            long now = System.currentTimeMillis();
            Long last = lastProbeAt.get(port);
            if (last != null && now - last < offlineRecheckMs) {
                continue;
            }
            lastProbeAt.put(port, now);

            SensorDevice first = list.get(0);
            IdentifyResult r = identify(port, first.getBaudRate(), first.getSlaveAddress());
            if (!r.isSuccess()) {
                log.warn("串口 {} 自动重新匹配失败：{}", port, r.getMessage());
                continue;
            }
            for (SensorDevice s : list) {
                if (r.getSlaveAddress() == null || !r.getSlaveAddress().equals(s.getSlaveAddress())) {
                    continue;
                }
                s.setBaudRate(r.getBaudRate());
                s.setMatchStatus(2);
                s.setStatus(1);
                s.setLastCommTime(LocalDateTime.now());
                sensorDeviceMapper.updateById(s);
                log.info("串口 {} 重新接入，采集设备「{}」已自动恢复（@{}bps 从站{}）",
                        port, s.getDeviceName(), r.getBaudRate(), r.getSlaveAddress());
            }
        }
    }

    /** 发送一帧读寄存器请求并接收应答，返回原始报文记录与应答字节 */
    private Probe probe(String portName, int baudRate, int slave, int register, int count) {
        FrameRecord record = new FrameRecord();
        record.setPortName(portName);
        record.setBaudRate(baudRate);
        record.setSlaveAddress(slave);
        record.setRegisterAddress(register);
        record.setRegisterCount(count);

        Probe probe = new Probe();
        probe.record = record;

        byte[] request = ModbusRtu.buildReadRequest(slave, register, count);
        record.setRequestFrame(ModbusRtu.hex(request));

        Lock lock = channelManager.lock(portName);
        lock.lock();
        try {
            SerialPort port = channelManager.channel(portName, baudRate);
            if (port == null) {
                record.setSuccess(false);
                record.setMessage("串口打开失败（可能被 UartAssist 等程序占用）");
                return probe;
            }
            SerialChannelManager.drain(port);
            if (port.writeBytes(request, request.length) != request.length) {
                record.setSuccess(false);
                record.setMessage("请求帧写入失败");
                return probe;
            }
            byte[] response = SerialChannelManager.readFrame(port, count);
            probe.raw = response;
            record.setResponseFrame(ModbusRtu.hex(response));
            if (ModbusRtu.isValidReadResponse(response, slave, count)) {
                record.setSuccess(true);
                record.setMessage("CRC 校验通过");
                return probe;
            }
            Integer exception = ModbusRtu.exceptionCode(response, slave);
            if (exception != null) {
                record.setSuccess(false);
                record.setMessage("从站返回异常码 0x" + String.format("%02X", exception));
            } else if (response == null || response.length == 0) {
                record.setSuccess(false);
                record.setMessage("从站无响应（超时）");
            } else {
                record.setSuccess(false);
                record.setMessage("应答帧不合法或 CRC 校验失败：" + record.getResponseFrame());
            }
            return probe;
        } catch (Exception e) {
            record.setSuccess(false);
            record.setMessage("串口通讯异常: " + e.getMessage());
            return probe;
        } finally {
            lock.unlock();
            frameLog.add(record);
        }
    }

    /** 单次探测的内部结果 */
    private static class Probe {
        FrameRecord record;
        byte[] raw;
    }
}
