package com.energy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("alarm_rule")
public class AlarmRule {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long deviceId;
    private String metric;
    private Double minValue;
    private Double maxValue;
    private String level;
    private Integer enabled;
    private Long ownerId;
}