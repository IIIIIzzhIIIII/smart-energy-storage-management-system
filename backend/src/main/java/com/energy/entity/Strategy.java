package com.energy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("strategy")
public class Strategy {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String strategyType;
    private Long deviceId;
    private String params;
    private Integer priority;
    private Integer enabled;
    private Long ownerId;
    private LocalDateTime createTime;
}