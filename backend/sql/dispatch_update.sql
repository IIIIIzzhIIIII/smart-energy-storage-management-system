-- =====================================================
-- 调度功能新增表迁移脚本
-- 在已有数据库上执行本文件即可，不会清空现有数据
-- 与 init.sql 中的表定义保持一致
-- =====================================================
USE energy_monitor;

-- 峰平谷时段表
CREATE TABLE IF NOT EXISTS peak_valley_period (
  id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  period_type VARCHAR(10) NOT NULL COMMENT '时段类型: PEAK/FLAT/VALLEY',
  name        VARCHAR(20) DEFAULT NULL COMMENT '时段名称: 峰/平/谷',
  start_hour  INT         NOT NULL COMMENT '开始小时(0-23, 含)',
  end_hour    INT         NOT NULL COMMENT '结束小时(0-23, 不含), 跨天时 end < start',
  price       DOUBLE      DEFAULT 0 COMMENT '电价(元/kWh)',
  enabled     TINYINT     NOT NULL DEFAULT 1 COMMENT '是否启用',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='峰平谷时段表';

-- 调度指令表
CREATE TABLE IF NOT EXISTS dispatch_command (
  id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  device_id     BIGINT       DEFAULT NULL COMMENT 'PCS设备ID',
  strategy_id   BIGINT       DEFAULT NULL COMMENT '触发的策略ID',
  strategy_type VARCHAR(20)  DEFAULT NULL COMMENT '策略类型',
  period_type   VARCHAR(10)  DEFAULT NULL COMMENT '当前时段: PEAK/FLAT/VALLEY',
  target_power  DOUBLE       DEFAULT 0 COMMENT '目标功率(kW), 负=充电, 正=放电',
  soc           DOUBLE       DEFAULT NULL COMMENT '当前SOC',
  pv_power      DOUBLE       DEFAULT NULL COMMENT '当前光伏功率',
  load_power    DOUBLE       DEFAULT NULL COMMENT '当前负荷功率',
  reason        VARCHAR(255) DEFAULT NULL COMMENT '决策原因',
  create_time   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '产生时间',
  PRIMARY KEY (id),
  KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='调度指令表';

-- 峰平谷时段种子数据（幂等：先删再插，避免重复）
DELETE FROM peak_valley_period;
INSERT INTO peak_valley_period (period_type, name, start_hour, end_hour, price, enabled) VALUES
('VALLEY', '谷时', 23, 7, 0.35, 1),
('PEAK',   '峰时', 10, 12, 0.95, 1),
('PEAK',   '峰时', 17, 21, 0.95, 1);
