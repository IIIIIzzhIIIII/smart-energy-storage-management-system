package com.energy.service;

import com.energy.dto.PortInfo;
import com.fazecast.jSerialComm.SerialPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 串口通道管理器：
 *  1) 枚举本机串口（供前端下拉选择，并识别 USB 转 RS485 模块）；
 *  2) 长连接缓存已打开的串口，避免每 2 秒反复开关 CH340 造成读写失败与延迟；
 *  3) 同一串口一把 ReentrantLock，保证采集线程与热插拔探测线程不会同时读写同一总线；
 *  4) 检测到端口被拔出时释放缓存句柄（下次插入会重新打开）。
 */
@Slf4j
@Component
public class SerialChannelManager {

    /** 读超时：4800bps 下 9 字节应答仅需约 20ms，400ms 足够余量 */
    @Value("${energy.safety.serial-read-timeout-ms:400}")
    private int readTimeoutMs;

    private final Map<String, Channel> channels = new ConcurrentHashMap<>();

    /** 枚举本机串口 */
    public List<PortInfo> listPorts() {
        List<PortInfo> list = new ArrayList<>();
        for (SerialPort p : SerialPort.getCommPorts()) {
            PortInfo info = new PortInfo();
            String name;
            String desc;
            try {
                name = p.getSystemPortName();
                desc = p.getDescriptivePortName();
            } catch (Exception e) {
                continue;
            }
            info.setPortName(name);
            info.setDescription(StringUtils.hasText(desc) ? desc.trim() : name);
            info.setUsb485(isUsb485(info.getDescription()));
            info.setOpen(isOpen(name));
            list.add(info);
        }
        list.sort(Comparator.comparingInt(x -> portNumber(x.getPortName())));
        return list;
    }

    /** 端口是否仍在系统中（拔线后 getCommPorts 里会消失） */
    public boolean isPresent(String portName) {
        if (!StringUtils.hasText(portName)) {
            return false;
        }
        for (SerialPort p : SerialPort.getCommPorts()) {
            try {
                if (portName.equalsIgnoreCase(p.getSystemPortName())) {
                    return true;
                }
            } catch (Exception ignored) {
                // 端口在枚举过程中被拔掉，忽略
            }
        }
        return false;
    }

    public boolean isOpen(String portName) {
        Channel c = channels.get(portName);
        return c != null && c.port != null && c.port.isOpen();
    }

    /**
     * 取得（必要时打开并缓存）串口句柄。波特率变化时会重新打开。
     *
     * @return 已打开的串口；打开失败返回 null
     */
    public SerialPort channel(String portName, int baudRate) {
        if (!StringUtils.hasText(portName)) {
            return null;
        }
        Channel c = channels.computeIfAbsent(portName, Channel::new);
        synchronized (c) {
            if (c.port != null && (!c.port.isOpen() || c.baudRate != baudRate)) {
                closeQuietly(c.port);
                c.port = null;
            }
            if (c.port == null) {
                SerialPort p = SerialPort.getCommPort(portName);
                p.setBaudRate(baudRate);
                p.setNumDataBits(8);
                p.setNumStopBits(SerialPort.ONE_STOP_BIT);
                p.setParity(SerialPort.NO_PARITY);
                p.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, readTimeoutMs, 1000);
                if (!p.openPort()) {
                    log.warn("串口 {} 打开失败（@{}bps），可能被其它程序（如 UartAssist）占用或已被拔出", portName, baudRate);
                    return null;
                }
                try {
                    p.flushIOBuffers();
                } catch (Exception ignored) {
                    // 部分驱动不支持 flush，忽略
                }
                c.port = p;
                c.baudRate = baudRate;
                log.info("串口 {} 已打开 @{}bps（长连接保持）", portName, baudRate);
            }
            return c.port;
        }
    }

    /** 取某串口的互斥锁 */
    public Lock lock(String portName) {
        return channels.computeIfAbsent(portName, Channel::new).lock;
    }

    /** 端口拔出/通讯异常时释放缓存的句柄 */
    public void invalidate(String portName) {
        if (!StringUtils.hasText(portName)) {
            return;
        }
        Channel c = channels.get(portName);
        if (c == null) {
            return;
        }
        synchronized (c) {
            closeQuietly(c.port);
            c.port = null;
        }
        log.warn("串口 {} 已释放（设备拔出或通讯异常）", portName);
    }

    private void closeQuietly(SerialPort port) {
        if (port == null) {
            return;
        }
        try {
            if (port.isOpen()) {
                port.closePort();
            }
        } catch (Exception ignored) {
            // 忽略关闭异常
        }
    }

    /**
     * 丢弃总线上的残留字节，避免上一帧尾巴干扰本次解析。
     */
    public static void drain(SerialPort port) {
        try {
            int available = port.bytesAvailable();
            if (available > 0) {
                byte[] junk = new byte[Math.min(available, 256)];
                port.readBytes(junk, junk.length, 0);
            }
        } catch (Exception ignored) {
            // 忽略
        }
    }

    /**
     * 读取一帧 Modbus 应答。
     * 期望长度 = 1 地址 + 1 功能码 + 1 字节数 + 2×寄存器数 + 2 CRC = 5 + 2×registerCount。
     *
     * @return 实际读到的字节（可能为空/不完整，交由调用方做 CRC 校验判断）
     */
    public static byte[] readFrame(SerialPort port, int registerCount) {
        int want = 5 + registerCount * 2;
        byte[] buffer = new byte[want];
        int total = 0;
        long deadline = System.currentTimeMillis() + 600;
        while (total < want && System.currentTimeMillis() < deadline) {
            int n = port.readBytes(buffer, want - total, total);
            if (n > 0) {
                total += n;
            } else {
                break;
            }
        }
        if (total <= 0) {
            return null;
        }
        byte[] frame = new byte[total];
        System.arraycopy(buffer, 0, frame, 0, total);
        return frame;
    }

    /** 按描述识别 USB 转 RS485 模块（CH340/CP210x/FTDI 等为常见 485 转换芯片） */
    private static boolean isUsb485(String desc) {
        if (!StringUtils.hasText(desc)) {
            return false;
        }
        String d = desc.toUpperCase();
        return d.contains("CH340") || d.contains("CH341") || d.contains("CP210") || d.contains("FTDI")
                || d.contains("USB-SERIAL") || d.contains("USB SERIAL") || d.contains("PROLIFIC")
                || d.contains("SILICON LABS") || d.contains("RS485") || d.contains("RS-485");
    }

    private static int portNumber(String name) {
        if (!StringUtils.hasText(name)) {
            return 9999;
        }
        try {
            return Integer.parseInt(name.replaceAll("\\D+", ""));
        } catch (Exception e) {
            return 9999;
        }
    }

    /** 单串口通道：句柄 + 波特率 + 互斥锁 */
    private static class Channel {
        @SuppressWarnings("unused")
        final String name;
        final ReentrantLock lock = new ReentrantLock();
        volatile SerialPort port;
        volatile int baudRate;

        Channel(String name) {
            this.name = name;
        }
    }
}
