package com.energy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("device")
public class Device {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String deviceCode;
    private String deviceName;
    private String deviceType;
    private String model;
    private Double ratedPower;
    private Double capacity;
    private String commProtocol;
    private String commAddress;
    private Long ownerId;
    private Integer status;
    private LocalDateTime createTime;
}