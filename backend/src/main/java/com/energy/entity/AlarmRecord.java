package com.energy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("alarm_record")
public class AlarmRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long deviceId;
    private String deviceName;
    private String metric;
    private Double value;
    private String threshold;
    private String level;
    private String message;
    private Integer status;
    private Long ownerId;
    private LocalDateTime createTime;
    private LocalDateTime handleTime;
}