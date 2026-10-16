package com.energy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("energy_report")
public class EnergyReport {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ownerId;
    private String period;
    private String periodValue;
    private Double generation;
    private Double consumption;
    private Double charge;
    private Double discharge;
    private Double gridImport;
    private Double gridExport;
    private Double revenue;
}