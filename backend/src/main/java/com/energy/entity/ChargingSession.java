package com.energy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 充电订单（光储充一体化中的"充"）
 */
@Data
@TableName("charging_session")
public class ChargingSession {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 充电桩设备ID */
    private Long deviceId;
    /** 开始时间 */
    private LocalDateTime startTime;
    /** 结束时间（充电中为 null） */
    private LocalDateTime endTime;
    /** 累计充电电量(kWh) */
    private Double energy;
    /** 充电功率(kW) */
    private Double power;
    /** 状态: 0=充电中 1=已完成 */
    private Integer status;
    /** 归属用户ID */
    private Long ownerId;
    private LocalDateTime createTime;
}
