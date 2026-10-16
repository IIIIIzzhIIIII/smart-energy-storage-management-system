-- =====================================================
-- 光储充一体化新增表迁移脚本
-- 在已有数据库上执行本文件即可，不会清空现有数据
-- =====================================================
USE energy_monitor;

-- 充电订单表
CREATE TABLE IF NOT EXISTS charging_session (
  id          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
  device_id   BIGINT   DEFAULT NULL COMMENT '充电桩设备ID',
  start_time  DATETIME DEFAULT NULL COMMENT '开始时间',
  end_time    DATETIME DEFAULT NULL COMMENT '结束时间(充电中为NULL)',
  energy      DOUBLE   DEFAULT 0 COMMENT '累计充电电量(kWh)',
  power       DOUBLE   DEFAULT 0 COMMENT '充电功率(kW)',
  status      TINYINT  NOT NULL DEFAULT 0 COMMENT '状态: 0充电中 1已完成',
  owner_id    BIGINT   DEFAULT NULL COMMENT '归属用户ID',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_device (device_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='充电订单表';

-- 充电桩演示设备（归属 user 用户 id=3；如已存在则跳过）
INSERT INTO device (device_code, device_name, device_type, model, rated_power, capacity, comm_protocol, comm_address, owner_id, status)
SELECT 'EV-001', '充电桩1号', 'EV', 'DC-60kW', 60, 0, 'MODBUS_TCP', '192.168.1.106', 3, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM device WHERE device_code = 'EV-001');
