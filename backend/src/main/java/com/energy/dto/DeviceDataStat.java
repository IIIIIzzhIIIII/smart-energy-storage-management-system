package com.energy.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设备时序采样统计（用于按实际采样间隔折算电量）。
 * 采用 MIN/MAX/COUNT 三个通用聚合，避免 TIMESTAMPDIFF 等 MySQL 专有函数，
 * 使同一套代码可同时运行在 MySQL 与 H2 上。
 */
@Data
public class DeviceDataStat {
    private LocalDateTime minTs;
    private LocalDateTime maxTs;
    private Long cnt;
}
