package com.energy.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Modbus-RTU 报文解析测试：全部使用说明书中给出的示例帧与真实抓包帧，不依赖硬件。
 *
 * 说明书示例：读从站 0x01 的温湿度值
 *   问询帧 01 03 00 00 00 02 C4 0B
 *   应答帧 01 03 04 01 E6 FF 9F 1B A0  => 湿度 48.6%RH、温度 -9.7℃
 */
class ModbusRtuTest {

    /** 说明书问询帧示例 */
    @Test
    @DisplayName("构建读寄存器问询帧：01 03 00 00 00 02 C4 0B")
    void buildReadRequestShouldMatchDatasheet() {
        byte[] frame = ModbusRtu.buildReadRequest(1, 0, 2);
        assertEquals("01 03 00 00 00 02 C4 0B", ModbusRtu.hex(frame));
    }

    /** 说明书应答帧示例：CRC 校验 + 两个寄存器拆分 + 换算 */
    @Test
    @DisplayName("解析说明书应答帧：湿度 48.6%RH、温度 -9.7℃（负温补码）")
    void parseDatasheetResponse() {
        byte[] response = hex("01 03 04 01 E6 FF 9F 1B A0");

        assertTrue(ModbusRtu.verifyCrc(response), "示例帧 CRC 应校验通过");
        assertTrue(ModbusRtu.isValidReadResponse(response, 1, 2));

        // 第 1 个寄存器 = 湿度（无符号）
        Integer humidityRaw = ModbusRtu.rawRegister(response, 0);
        assertEquals(0x01E6, humidityRaw);
        assertEquals(48.6, ModbusRtu.toValue(humidityRaw, "UINT16", 0.1, 2), 0.0001);

        // 第 2 个寄存器 = 温度（补码有符号）
        Integer temperatureRaw = ModbusRtu.rawRegister(response, 1);
        assertEquals(0xFF9F, temperatureRaw);
        assertEquals(-9.7, ModbusRtu.toValue(temperatureRaw, "INT16", 0.1, 2), 0.0001);
    }

    /** 现场抓包帧：01 03 00 00 00 02 -> 湿度 63.2%RH、温度 30.1℃ */
    @Test
    @DisplayName("解析现场抓包：湿度 63.2%RH、温度 30.1℃")
    void parseRealCapturedFrame() {
        byte[] request = ModbusRtu.buildReadRequest(1, 0, 2);
        assertEquals("01 03 00 00 00 02 C4 0B", ModbusRtu.hex(request));

        byte[] response = withCrc("01 03 04 02 78 01 2D");
        assertTrue(ModbusRtu.isValidReadResponse(response, 1, 2));

        assertEquals(63.2, ModbusRtu.toValue(ModbusRtu.rawRegister(response, 0), "UINT16", 0.1, 2), 0.0001);
        assertEquals(30.1, ModbusRtu.toValue(ModbusRtu.rawRegister(response, 1), "INT16", 0.1, 2), 0.0001);
    }

    /** CRC 校验必须能挡住噪声/错误帧，避免把噪声当成设备应答 */
    @Test
    @DisplayName("CRC 错误或长度不足的应答帧一律判为无效")
    void invalidFramesShouldBeRejected() {
        byte[] corrupted = hex("01 03 04 01 E6 FF 9F 1B A1");
        assertFalse(ModbusRtu.verifyCrc(corrupted));
        assertFalse(ModbusRtu.isValidReadResponse(corrupted, 1, 2));

        // 从站地址不匹配
        assertFalse(ModbusRtu.isValidReadResponse(hex("01 03 04 01 E6 FF 9F 1B A0"), 2, 2));

        // 字节数与请求寄存器个数不符
        assertFalse(ModbusRtu.isValidReadResponse(hex("01 03 04 01 E6 FF 9F 1B A0"), 1, 1));

        // 无响应
        assertFalse(ModbusRtu.isValidReadResponse(null, 1, 2));
        assertNull(ModbusRtu.rawRegister(null, 0));
    }

    /** 越界读取应返回 null，而不是抛异常或给出错误的值 */
    @Test
    @DisplayName("寄存器偏移越界返回 null（防止配置错误导致读到错误数据）")
    void outOfRangeOffsetShouldReturnNull() {
        byte[] response = hex("01 03 04 01 E6 FF 9F 1B A0");
        assertNull(ModbusRtu.rawRegister(response, 2));
        assertNull(ModbusRtu.rawRegister(response, -1));
    }

    /** 从站异常应答（功能码 0x83）应能取出异常码 */
    @Test
    @DisplayName("识别从站异常应答 0x83 并取出异常码")
    void parseExceptionResponse() {
        byte[] response = withCrc("01 83 02");
        assertEquals(2, ModbusRtu.exceptionCode(response, 1));
        assertFalse(ModbusRtu.isValidReadResponse(response, 1, 2));
    }

    /** 无符号与有符号解析的差异：0xFFFF 在 UINT16 下是 65535，在 INT16 下是 -1 */
    @Test
    @DisplayName("INT16 补码解析：0xFF9F=-97，0x8000=-32768")
    void signedConversion() {
        assertEquals(-97.0, ModbusRtu.toValue(0xFF9F, "INT16", 1.0, 2), 0.0001);
        assertEquals(-3276.8, ModbusRtu.toValue(0x8000, "INT16", 0.1, 2), 0.0001);
        assertEquals(6553.5, ModbusRtu.toValue(0xFFFF, "UINT16", 0.1, 2), 0.0001);
    }

    /** 十六进制字符串转字节数组（测试辅助） */
    private static byte[] hex(String text) {
        String[] parts = text.trim().split("\\s+");
        byte[] data = new byte[parts.length];
        for (int i = 0; i < parts.length; i++) {
            data[i] = (byte) Integer.parseInt(parts[i], 16);
        }
        return data;
    }

    /** 给不含 CRC 的帧补上正确 CRC（校验码低位在前） */
    private static byte[] withCrc(String textWithoutCrc) {
        byte[] body = hex(textWithoutCrc);
        int crc = ModbusRtu.crc16(body, body.length);
        byte[] frame = new byte[body.length + 2];
        System.arraycopy(body, 0, frame, 0, body.length);
        frame[body.length] = (byte) (crc & 0xFF);
        frame[body.length + 1] = (byte) ((crc >> 8) & 0xFF);
        return frame;
    }
}
