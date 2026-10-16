package com.energy.dto;

import lombok.Data;

/**
 * 一次 Modbus 串口事务的原始报文记录（用于前端展示"原始帧"，便于排查接线/波特率/从站地址问题）
 */
@Data
public class FrameRecord {
    /** 时间，yyyy-MM-dd HH:mm:ss */
    private String time;
    /** 串口号 */
    private String portName;
    /** 波特率 */
    private Integer baudRate;
    /** 从站地址 */
    private Integer slaveAddress;
    /** 寄存器起始地址 */
    private Integer registerAddress;
    /** 寄存器数量 */
    private Integer registerCount;
    /** 请求帧（十六进制） */
    private String requestFrame;
    /** 应答帧（十六进制），无响应时为空 */
    private String responseFrame;
    /** 通讯是否成功（CRC 校验通过） */
    private Boolean success;
    /** 结果说明 */
    private String message;
}
