package com.energy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 峰平谷时段配置
 */
@Data
@TableName("peak_valley_period")
public class PeakValleyPeriod {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 时段类型: PEAK/FLAT/VALLEY */
    private String periodType;
    /** 时段名称: 峰/平/谷 */
    private String name;
    /** 开始小时(0-23, 含) */
    private Integer startHour;
    /** 结束小时(0-23, 不含), 跨天时 end < start */
    private Integer endHour;
    /** 电价(元/kWh) */
    private Double price;
    /** 是否启用 */
    private Integer enabled;
}
