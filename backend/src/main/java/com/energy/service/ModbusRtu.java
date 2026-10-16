package com.energy.service;

/**
 * Modbus-RTU 报文工具（纯静态函数，便于单元测试）。
 *
 * 依据接入的 USB-RS485 温湿度变送器说明书：
 *   编码 8 位二进制 / 数据位 8 / 无校验 / 停止位 1 / 错误校验 CRC16
 *   只用到功能码 0x03（读保持寄存器）
 *   数据区 16bits 数据高字节在前
 *   寄存器 0x0000(40001)=湿度 只读，0x0001(40002)=温度 只读
 *   例：请求 01 03 00 00 00 02 C4 0B
 *       应答 01 03 04 01 E6 FF 9F 1B A0  => 湿度 48.6%RH、温度 -9.7℃
 */
public final class ModbusRtu {

    /** 读保持寄存器 */
    public static final int FUNC_READ_HOLDING = 0x03;

    private ModbusRtu() {
    }

    /**
     * 主站问询帧：地址码 + 功能码 + 寄存器起始地址 + 寄存器长度 + 校验码(低位在前)
     */
    public static byte[] buildReadRequest(int slave, int register, int count) {
        byte[] frame = new byte[8];
        frame[0] = (byte) (slave & 0xFF);
        frame[1] = (byte) FUNC_READ_HOLDING;
        frame[2] = (byte) ((register >> 8) & 0xFF);
        frame[3] = (byte) (register & 0xFF);
        frame[4] = (byte) ((count >> 8) & 0xFF);
        frame[5] = (byte) (count & 0xFF);
        int crc = crc16(frame, 6);
        frame[6] = (byte) (crc & 0xFF);        // 校验码低位
        frame[7] = (byte) ((crc >> 8) & 0xFF); // 校验码高位
        return frame;
    }

    /** CRC16（Modbus 多项式 0xA001），返回低 16 位结果 */
    public static int crc16(byte[] data, int len) {
        int crc = 0xFFFF;
        for (int i = 0; i < len; i++) {
            crc ^= (data[i] & 0xFF);
            for (int j = 0; j < 8; j++) {
                if ((crc & 1) != 0) {
                    crc = (crc >> 1) ^ 0xA001;
                } else {
                    crc = crc >> 1;
                }
            }
        }
        return crc & 0xFFFF;
    }

    /** 校验整帧（含末尾两字节 CRC）：CRC 通过才认定设备真实存在 */
    public static boolean verifyCrc(byte[] frame) {
        if (frame == null || frame.length < 4) {
            return false;
        }
        int expected = crc16(frame, frame.length - 2);
        int actual = (frame[frame.length - 2] & 0xFF) | ((frame[frame.length - 1] & 0xFF) << 8);
        return expected == actual;
    }

    /** 是否为合法的 0x03 读保持寄存器应答帧（地址、功能码、字节数、长度、CRC 全部校验） */
    public static boolean isValidReadResponse(byte[] resp, int slave, int registerCount) {
        if (resp == null || resp.length < 5) {
            return false;
        }
        if ((resp[0] & 0xFF) != (slave & 0xFF) || (resp[1] & 0xFF) != FUNC_READ_HOLDING) {
            return false;
        }
        int byteCount = resp[2] & 0xFF;
        if (byteCount != registerCount * 2) {
            return false;
        }
        if (resp.length < 3 + byteCount + 2) {
            return false;
        }
        byte[] frame = new byte[3 + byteCount + 2];
        System.arraycopy(resp, 0, frame, 0, frame.length);
        return verifyCrc(frame);
    }

    /** 若是从站异常应答（功能码 | 0x80），返回异常码；否则返回 null */
    public static Integer exceptionCode(byte[] resp, int slave) {
        if (resp == null || resp.length < 5) {
            return null;
        }
        if ((resp[0] & 0xFF) != (slave & 0xFF) || (resp[1] & 0xFF) != (FUNC_READ_HOLDING | 0x80)) {
            return null;
        }
        return resp[2] & 0xFF;
    }

    /**
     * 取应答帧数据区第 index 个寄存器（0 基）的原始无符号值；越界或帧不合法返回 null。
     */
    public static Integer rawRegister(byte[] resp, int index) {
        if (resp == null || resp.length < 5 || index < 0) {
            return null;
        }
        if ((resp[1] & 0xFF) != FUNC_READ_HOLDING) {
            return null;
        }
        int byteCount = resp[2] & 0xFF;
        if ((index + 1) * 2 > byteCount) {
            return null;
        }
        int p = 3 + index * 2;
        if (resp.length < p + 2) {
            return null;
        }
        return ((resp[p] & 0xFF) << 8) | (resp[p + 1] & 0xFF); // 高字节在前
    }

    /**
     * 原始寄存器值按数据类型换算为工程量。
     *
     * INT16：按补码解析（说明书规定温度低于 0℃ 时以补码上传，如 FF9FH = -97 => -9.7℃）
     * UINT16 / 其它：按无符号解析
     *
     * @param scale    换算系数（本变送器为 0.1）
     * @param decimals 结果保留小数位
     */
    public static Double toValue(Integer raw, String dataType, Double scale, int decimals) {
        if (raw == null) {
            return null;
        }
        double v = raw;
        if (dataType == null || dataType.isEmpty() || "INT16".equalsIgnoreCase(dataType)) {
            if (raw >= 0x8000) {
                v = raw - 0x10000;
            }
        }
        double s = scale == null ? 1 : scale;
        double factor = Math.pow(10, decimals);
        return Math.round(v * s * factor) / factor;
    }

    /** 十六进制字符串（用于日志与前端展示原始帧） */
    public static String hex(byte[] data) {
        if (data == null || data.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(data.length * 3);
        for (byte b : data) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(String.format("%02X", b & 0xFF));
        }
        return sb.toString();
    }
}
