package com.energy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("device_data")
public class DeviceData {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long deviceId;
    private LocalDateTime ts;
    private Double power;
    private Double voltage;
    private Double current;
    private Double soc;
    private Double irradiance;
    private Double energyIn;
    private Double energyOut;
}