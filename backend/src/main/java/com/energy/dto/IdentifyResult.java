package com.energy.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 串口识别结果：自动探测波特率与从站地址后，返回实测的湿度/温度与原始帧
 */
@Data
public class IdentifyResult {
    private boolean success;
    private String message;
    /** 实际使用的串口号 */
    private String portName;
    /** 探测到的波特率 */
    private Integer baudRate;
    /** 探测到的从站地址 */
    private Integer slaveAddress;
    /** 湿度（%RH，寄存器 0x0000） */
    private Double humidity;
    /** 温度（℃，寄存器 0x0001，支持负值） */
    private Double temperature;
    /** 成功的请求帧 */
    private String requestFrame;
    /** 成功的应答帧 */
    private String responseFrame;
    /** 探测过程中所有尝试的原始帧 */
    private List<FrameRecord> frames = new ArrayList<>();
}
