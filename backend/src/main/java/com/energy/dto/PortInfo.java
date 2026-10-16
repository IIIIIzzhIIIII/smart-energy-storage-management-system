package com.energy.dto;

import lombok.Data;

/**
 * 串口列表项（USB-RS485 模块识别用）
 */
@Data
public class PortInfo {
    /** 串口号，如 COM5 */
    private String portName;
    /** 系统描述，如 USB-SERIAL CH340 (COM5) */
    private String description;
    /** 是否疑似 USB 转 RS485 模块（按描述关键字判断） */
    private boolean usb485;
    /** 是否已被本程序打开（长连接保持中） */
    private boolean open;
}
