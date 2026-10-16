package com.energy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("protocol")
public class Protocol {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String protocolName;
    private String protocolType;
    private String sensorType;
    private Integer functionCode;
    private Integer registerAddress;
    private Integer registerCount;
    private String dataType;
    private Double scale;
    private Integer baudRate;
    private Integer dataBits;
    private Integer stopBits;
    private String parity;
    private String description;
}