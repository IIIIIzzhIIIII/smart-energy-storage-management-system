package com.energy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 调度指令记录（调度引擎每次决策的输出）
 */
@Data
@TableName("dispatch_command")
public class DispatchCommand {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** PCS 设备ID */
    private Long deviceId;
    /** 触发的策略ID */
    private Long strategyId;
    /** 策略类型: PEAK_VALLEY/SELF_USE/... */
    private String strategyType;
    /** 当前时段: PEAK/FLAT/VALLEY */
    private String periodType;
    /** 目标功率(kW), 负=充电, 正=放电 */
    private Double targetPower;
    /** 当前 SOC */
    private Double soc;
    /** 当前光伏功率 */
    private Double pvPower;
    /** 当前负荷功率 */
    private Double loadPower;
    /** 决策原因 */
    private String reason;
    private LocalDateTime createTime;
}
