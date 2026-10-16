package com.energy.service;

import com.energy.dto.FrameRecord;
import com.energy.dto.MatchResult;
import com.energy.entity.Protocol;
import com.energy.entity.SensorDevice;
import com.fazecast.jSerialComm.SerialPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;

/**
 * 真实串口实现（USB 转 RS485 + Modbus-RTU 温湿度变送器）。
 *
 * 依据说明书：8 数据位 / 无校验 / 1 停止位 / CRC16，功能码 0x03，16bit 大端；
 * 寄存器 0x0000 = 湿度、0x0001 = 温度（均只读，值 ×0.1）。
 * 因此"温度"与"湿度"是同一台变送器同一次块读的两个寄存器：
 *   湿度设备 register_offset=0、温度设备 register_offset=1，两者共用一次串口事务
 *   （由 frame-cache-ms 短缓存实现，避免 2 秒采集周期内重复占用 485 总线）。
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "energy.safety.mode", havingValue = "real")
public class RealSerialPortService implements SerialPortService {

    @Autowired
    private SerialChannelManager channelManager;
    @Autowired
    private SerialFrameLog frameLog;

    @Value("${energy.safety.serial-port:COM5}")
    private String configPort;

    @Value("${energy.safety.serial-baud-rate:4800}")
    private int configBaudRate;

    /** 同一次块读结果的短缓存时长：让同从站的温度/湿度两条设备共用一帧 */
    @Value("${energy.safety.frame-cache-ms:500}")
    private long frameCacheMs;

    private final Map<String, CachedFrame> frameCache = new ConcurrentHashMap<>();

    @Override
    public MatchResult match(Protocol protocol, SensorDevice sensor) {
        MatchResult result = new MatchResult();
        String portName = resolvePort(sensor);
        int baudRate = resolveBaudRate(protocol, sensor);
        int slave = resolveSlave(sensor);
        int register = resolveRegister(protocol, sensor);
        int count = resolveCount(protocol, sensor);

        if (!channelManager.isPresent(portName)) {
            channelManager.invalidate(portName);
            result.setSuccess(false);
            result.setMessage("串口 " + portName + " 未检测到，请确认 USB-RS485 已插入且驱动正常（当前可用串口："
                    + currentPorts() + "）");
            return result;
        }

        FrameRead read = transact(portName, baudRate, slave, register, count);
        frameLog.add(read.record);
        if (!Boolean.TRUE.equals(read.record.getSuccess())) {
            result.setSuccess(false);
            result.setMessage("协议匹配失败：端口 " + portName + " @" + baudRate + "bps，从站 " + slave
                    + "，" + read.record.getMessage() + "（请求帧 " + read.record.getRequestFrame() + "）");
            return result;
        }
        result.setSuccess(true);
        result.setMessage("协议匹配成功：端口 " + portName + " @" + baudRate + "bps，从站 " + slave
                + " 响应正常，CRC 校验通过，请求帧 " + read.record.getRequestFrame()
                + "，应答帧 " + read.record.getResponseFrame());
        result.setSampleValue(extract(read.raw, protocol, sensor));
        return result;
    }

    @Override
    public Double readValue(Protocol protocol, SensorDevice sensor) {
        String portName = resolvePort(sensor);
        if (!channelManager.isPresent(portName)) {
            channelManager.invalidate(portName);
            return null;
        }
        int baudRate = resolveBaudRate(protocol, sensor);
        int slave = resolveSlave(sensor);
        int register = resolveRegister(protocol, sensor);
        int count = resolveCount(protocol, sensor);

        FrameRead read = transact(portName, baudRate, slave, register, count);
        if (!Boolean.TRUE.equals(read.record.getSuccess())) {
            log.debug("采集失败 端口={} 从站={} 原因={}", portName, slave, read.record.getMessage());
            return null;
        }
        return extract(read.raw, protocol, sensor);
    }

    /**
     * 把应答帧中该设备对应的寄存器按数据类型换算为工程量。
     * 温度低于 0℃ 时说明书以补码上传，故温度固定按有符号 INT16 解析；湿度固定按无符号 16 位。
     */
    private Double extract(byte[] response, Protocol protocol, SensorDevice sensor) {
        if (response == null) {
            return null;
        }
        int offset = sensor != null && sensor.getRegisterOffset() != null ? Math.max(0, sensor.getRegisterOffset()) : 0;
        Integer raw = ModbusRtu.rawRegister(response, offset);
        if (raw == null) {
            return null;
        }
        String dataType = protocol == null || !StringUtils.hasText(protocol.getDataType())
                ? "INT16" : protocol.getDataType();
        if (sensor != null && "HUMIDITY".equals(sensor.getSensorType())) {
            dataType = "UINT16";
        } else if (sensor != null && "TEMP".equals(sensor.getSensorType())) {
            dataType = "INT16";
        }
        Double scale = protocol == null || protocol.getScale() == null ? 1.0 : protocol.getScale();
        return ModbusRtu.toValue(raw, dataType, scale, 2);
    }

    @Override
    public boolean isSimulate() {
        return false;
    }

    /** 带短缓存的串口事务：同端口同从站的温度/湿度两条设备共用一次块读 */
    private FrameRead transact(String portName, int baudRate, int slave, int register, int count) {
        String key = portName + "|" + baudRate + "|" + slave + "|" + register + "|" + count;
        long now = System.currentTimeMillis();
        CachedFrame cached = frameCache.get(key);
        if (cached != null && now - cached.at < frameCacheMs) {
            FrameRead hit = new FrameRead();
            hit.raw = cached.raw;
            hit.record = cached.record;
            return hit;
        }
        FrameRead read = doTransact(portName, baudRate, slave, register, count);
        if (Boolean.TRUE.equals(read.record.getSuccess())) {
            frameCache.put(key, new CachedFrame(read.raw, read.record, now));
        }
        return read;
    }

    /** 一次完整的 Modbus 请求/应答 */
    private FrameRead doTransact(String portName, int baudRate, int slave, int register, int count) {
        FrameRecord record = new FrameRecord();
        record.setPortName(portName);
        record.setBaudRate(baudRate);
        record.setSlaveAddress(slave);
        record.setRegisterAddress(register);
        record.setRegisterCount(count);

        FrameRead read = new FrameRead();
        read.record = record;

        byte[] request = ModbusRtu.buildReadRequest(slave, register, count);
        record.setRequestFrame(ModbusRtu.hex(request));

        Lock lock = channelManager.lock(portName);
        lock.lock();
        try {
            SerialPort port = channelManager.channel(portName, baudRate);
            if (port == null) {
                record.setSuccess(false);
                record.setMessage("串口打开失败（可能被 UartAssist 等程序占用，或设备已拔出）");
                return read;
            }
            SerialChannelManager.drain(port);
            if (port.writeBytes(request, request.length) != request.length) {
                record.setSuccess(false);
                record.setMessage("请求帧写入失败");
                channelManager.invalidate(portName);
                return read;
            }
            byte[] response = SerialChannelManager.readFrame(port, count);
            read.raw = response;
            record.setResponseFrame(ModbusRtu.hex(response));
            if (ModbusRtu.isValidReadResponse(response, slave, count)) {
                record.setSuccess(true);
                record.setMessage("CRC 校验通过");
                return read;
            }
            Integer exception = ModbusRtu.exceptionCode(response, slave);
            if (exception != null) {
                record.setSuccess(false);
                record.setMessage("从站返回异常码 0x" + String.format("%02X", exception));
            } else if (response == null || response.length == 0) {
                record.setSuccess(false);
                record.setMessage("从站无响应（超时），请检查波特率与从站地址");
            } else {
                record.setSuccess(false);
                record.setMessage("应答帧不合法或 CRC 校验失败：" + record.getResponseFrame());
            }
            return read;
        } catch (Exception e) {
            record.setSuccess(false);
            record.setMessage("串口通讯异常: " + e.getMessage());
            return read;
        } finally {
            lock.unlock();
        }
    }

    private String resolvePort(SensorDevice sensor) {
        if (sensor != null && StringUtils.hasText(sensor.getPortName())) {
            return sensor.getPortName().trim();
        }
        return configPort;
    }

    private int resolveBaudRate(Protocol protocol, SensorDevice sensor) {
        if (sensor != null && sensor.getBaudRate() != null && sensor.getBaudRate() > 0) {
            return sensor.getBaudRate();
        }
        if (protocol != null && protocol.getBaudRate() != null && protocol.getBaudRate() > 0) {
            return protocol.getBaudRate();
        }
        return configBaudRate;
    }

    private int resolveSlave(SensorDevice sensor) {
        return sensor == null || sensor.getSlaveAddress() == null ? 1 : sensor.getSlaveAddress();
    }

    private int resolveRegister(Protocol protocol, SensorDevice sensor) {
        if (sensor != null && sensor.getRegisterAddress() != null) {
            return sensor.getRegisterAddress();
        }
        if (protocol != null && protocol.getRegisterAddress() != null) {
            return protocol.getRegisterAddress();
        }
        return 0;
    }

    /** 块读寄存器个数：至少覆盖该设备所用的偏移位置 */
    private int resolveCount(Protocol protocol, SensorDevice sensor) {
        int count = protocol == null || protocol.getRegisterCount() == null ? 2 : protocol.getRegisterCount();
        count = Math.max(1, count);
        int offset = sensor == null || sensor.getRegisterOffset() == null ? 0 : sensor.getRegisterOffset();
        return Math.max(count, offset + 1);
    }

    private String currentPorts() {
        StringBuilder sb = new StringBuilder();
        channelManager.listPorts().forEach(p -> {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(p.getPortName());
        });
        return sb.length() == 0 ? "无" : sb.toString();
    }

    /** 一次事务的返回：报文记录 + 原始应答字节 */
    private static class FrameRead {
        FrameRecord record;
        byte[] raw;
    }

    /** 短缓存的块读结果 */
    private static class CachedFrame {
        final byte[] raw;
        final FrameRecord record;
        final long at;

        CachedFrame(byte[] raw, FrameRecord record, long at) {
            this.raw = raw;
            this.record = record;
            this.at = at;
        }
    }
}