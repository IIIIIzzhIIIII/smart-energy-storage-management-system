package com.energy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sensor_device")
public class SensorDevice {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long moduleId;
    private Integer portNo;
    private String portPosition;
    private String deviceCode;
    private String deviceName;
    private String sensorType;
    private String commProtocol;
    private Integer slaveAddress;
    private Integer baudRate;
    private Integer registerAddress;
    /** 串口号，如 COM5（一个 RS485 总线上可挂多个从站，共用同一串口） */
    private String portName;
    /** 块读时的寄存器偏移：0=取第 1 个寄存器(湿度) 1=取第 2 个(温度) */
    private Integer registerOffset;
    /** 最后一次成功通讯时间，前端用于离线提示 */
    private LocalDateTime lastCommTime;
    private String unit;
    private Double warnMin;
    private Double warnMax;
    private Double critMin;
    private Double critMax;
    private Double emergMin;
    private Double emergMax;
    private Integer matchStatus;
    private Integer status;
    private Long ownerId;
    private LocalDateTime createTime;

    @TableField(exist = false)
    private Double latestValue;
}