-- =====================================================
-- 光储智慧调度监控平台 数据库初始化脚本
-- MySQL 8.0 / 5.7
-- 使用方法：在 Navicat 中新建查询执行，或命令行 source 本文件
-- =====================================================

CREATE DATABASE IF NOT EXISTS energy_monitor DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE energy_monitor;

-- -----------------------------------------------------
-- 1. 用户表
-- -----------------------------------------------------
DROP TABLE IF EXISTS sys_user;
CREATE TABLE sys_user (
  id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  username    VARCHAR(50)  NOT NULL COMMENT '登录账号',
  password    VARCHAR(100) NOT NULL COMMENT '密码(MD5)',
  nickname    VARCHAR(50)  DEFAULT NULL COMMENT '昵称/姓名',
  role        VARCHAR(20)  NOT NULL DEFAULT 'USER' COMMENT '角色: ADMIN/DISPATCHER/USER/VIEWER',
  status      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态: 1启用 0停用',
  create_time DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';

-- -----------------------------------------------------
-- 2. 设备台账表
-- -----------------------------------------------------
DROP TABLE IF EXISTS device;
CREATE TABLE device (
  id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  device_code   VARCHAR(50)  NOT NULL COMMENT '设备编号',
  device_name   VARCHAR(100) NOT NULL COMMENT '设备名称',
  device_type   VARCHAR(20)  NOT NULL COMMENT '设备类型: PV/PCS/METER/LOAD/GRID',
  model         VARCHAR(50)  DEFAULT NULL COMMENT '设备型号',
  rated_power   DOUBLE       DEFAULT 0 COMMENT '额定功率(kW)',
  capacity      DOUBLE       DEFAULT 0 COMMENT '容量(kWh)',
  comm_protocol VARCHAR(20)  DEFAULT 'MODBUS_TCP' COMMENT '通讯协议(预留Modbus TCP扩展)',
  comm_address  VARCHAR(50)  DEFAULT NULL COMMENT '通讯地址',
  owner_id      BIGINT       NOT NULL COMMENT '归属用户ID',
  status        TINYINT      NOT NULL DEFAULT 1 COMMENT '状态: 1在线 0离线',
  create_time   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_device_code (device_code),
  KEY idx_owner (owner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备台账表';

-- -----------------------------------------------------
-- 3. 设备实时/历史数据表（时序数据暂存 MySQL，预留 InfluxDB 扩展）
-- -----------------------------------------------------
DROP TABLE IF EXISTS device_data;
CREATE TABLE device_data (
  id         BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
  device_id  BIGINT   NOT NULL COMMENT '设备ID',
  ts         DATETIME NOT NULL COMMENT '数据时间',
  power      DOUBLE   DEFAULT 0 COMMENT '功率(kW), 正=放电/输出, 负=充电/吸收',
  voltage    DOUBLE   DEFAULT 0 COMMENT '电压(V)',
  current    DOUBLE   DEFAULT 0 COMMENT '电流(A)',
  soc        DOUBLE   DEFAULT NULL COMMENT 'SOC(%), 仅储能',
  irradiance DOUBLE   DEFAULT NULL COMMENT '辐照度(W/m2), 仅光伏',
  energy_in  DOUBLE   DEFAULT NULL COMMENT '充电量/购入电量(kWh)',
  energy_out DOUBLE   DEFAULT NULL COMMENT '放电量/输出电量(kWh)',
  PRIMARY KEY (id),
  KEY idx_device_ts (device_id, ts)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备时序数据表';

-- -----------------------------------------------------
-- 4. 调度策略表
-- -----------------------------------------------------
DROP TABLE IF EXISTS strategy;
CREATE TABLE strategy (
  id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  name        VARCHAR(100) NOT NULL COMMENT '策略名称',
  strategy_type VARCHAR(20) NOT NULL COMMENT '策略类型: PLAN/PEAK_VALLEY/SELF_USE/DEMAND',
  device_id   BIGINT       DEFAULT NULL COMMENT '关联设备ID(储能PCS)',
  params      VARCHAR(500) DEFAULT NULL COMMENT '策略参数(JSON)',
  priority    INT          DEFAULT 0 COMMENT '优先级, 数值越大越高',
  enabled     TINYINT      NOT NULL DEFAULT 1 COMMENT '是否启用: 1启用 0停用',
  owner_id    BIGINT       NOT NULL COMMENT '归属用户ID',
  create_time DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_owner (owner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='调度策略表';

-- -----------------------------------------------------
-- 5. 告警规则表
-- -----------------------------------------------------
DROP TABLE IF EXISTS alarm_rule;
CREATE TABLE alarm_rule (
  id        BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  device_id BIGINT      NOT NULL COMMENT '设备ID',
  metric    VARCHAR(20) NOT NULL COMMENT '监测指标: POWER/VOLTAGE/CURRENT/SOC',
  min_value DOUBLE      DEFAULT NULL COMMENT '下限',
  max_value DOUBLE      DEFAULT NULL COMMENT '上限',
  level     VARCHAR(20) NOT NULL DEFAULT 'WARN' COMMENT '告警级别: INFO/WARN/CRITICAL',
  enabled   TINYINT     NOT NULL DEFAULT 1 COMMENT '是否启用',
  owner_id  BIGINT      NOT NULL COMMENT '归属用户ID',
  PRIMARY KEY (id),
  KEY idx_device (device_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警规则表';

-- -----------------------------------------------------
-- 6. 告警记录表
-- -----------------------------------------------------
DROP TABLE IF EXISTS alarm_record;
CREATE TABLE alarm_record (
  id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  device_id   BIGINT       NOT NULL COMMENT '设备ID',
  device_name VARCHAR(100) DEFAULT NULL COMMENT '设备名称',
  metric      VARCHAR(20)  DEFAULT NULL COMMENT '指标',
  value       DOUBLE       DEFAULT NULL COMMENT '触发值',
  threshold   VARCHAR(50)  DEFAULT NULL COMMENT '阈值描述',
  level       VARCHAR(20)  DEFAULT 'WARN' COMMENT '级别',
  message     VARCHAR(255) DEFAULT NULL COMMENT '告警内容',
  status      TINYINT      NOT NULL DEFAULT 0 COMMENT '处理状态: 0未处理 1已处理',
  owner_id    BIGINT       NOT NULL COMMENT '归属用户ID',
  create_time DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '产生时间',
  handle_time DATETIME     DEFAULT NULL COMMENT '处理时间',
  PRIMARY KEY (id),
  KEY idx_owner (owner_id),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警记录表';

-- -----------------------------------------------------
-- 7. 统计报表表
-- -----------------------------------------------------
DROP TABLE IF EXISTS energy_report;
CREATE TABLE energy_report (
  id           BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
  owner_id     BIGINT   NOT NULL COMMENT '归属用户ID',
  period       VARCHAR(10) NOT NULL COMMENT '周期: DAY/MONTH',
  period_value VARCHAR(20) NOT NULL COMMENT '周期值: 2026-09-03 或 2026-09',
  generation   DOUBLE   DEFAULT 0 COMMENT '发电量(kWh)',
  consumption  DOUBLE   DEFAULT 0 COMMENT '用电量(kWh)',
  charge       DOUBLE   DEFAULT 0 COMMENT '充电量(kWh)',
  discharge    DOUBLE   DEFAULT 0 COMMENT '放电量(kWh)',
  grid_import  DOUBLE   DEFAULT 0 COMMENT '购电量(kWh)',
  grid_export  DOUBLE   DEFAULT 0 COMMENT '上网电量(kWh)',
  revenue      DOUBLE   DEFAULT 0 COMMENT '收益(元)',
  PRIMARY KEY (id),
  UNIQUE KEY uk_period (owner_id, period, period_value)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='统计报表表';

-- -----------------------------------------------------
-- 8. 操作日志表
-- -----------------------------------------------------
DROP TABLE IF EXISTS sys_log;
CREATE TABLE sys_log (
  id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  username    VARCHAR(50)  DEFAULT NULL COMMENT '操作用户',
  operation   VARCHAR(255) DEFAULT NULL COMMENT '操作内容',
  method      VARCHAR(255) DEFAULT NULL COMMENT '请求方法',
  ip          VARCHAR(50)  DEFAULT NULL COMMENT 'IP',
  create_time DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '时间',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志表';

-- -----------------------------------------------------
-- 9. 峰平谷时段表
-- -----------------------------------------------------
DROP TABLE IF EXISTS peak_valley_period;
CREATE TABLE peak_valley_period (
  id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  period_type VARCHAR(10) NOT NULL COMMENT '时段类型: PEAK/FLAT/VALLEY',
  name        VARCHAR(20) DEFAULT NULL COMMENT '时段名称: 峰/平/谷',
  start_hour  INT         NOT NULL COMMENT '开始小时(0-23, 含)',
  end_hour    INT         NOT NULL COMMENT '结束小时(0-23, 不含), 跨天时 end < start',
  price       DOUBLE      DEFAULT 0 COMMENT '电价(元/kWh)',
  enabled     TINYINT     NOT NULL DEFAULT 1 COMMENT '是否启用',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='峰平谷时段表';

-- -----------------------------------------------------
-- 10. 调度指令表
-- -----------------------------------------------------
DROP TABLE IF EXISTS dispatch_command;
CREATE TABLE dispatch_command (
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

-- -----------------------------------------------------
-- 11. 充电订单表
-- -----------------------------------------------------
DROP TABLE IF EXISTS charging_session;
CREATE TABLE charging_session (
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

-- =====================================================
-- 初始数据
-- =====================================================

-- 内置账号（密码均为 123456，MD5: e10adc3949ba59abbe56e057f20f883e）
INSERT INTO sys_user (username, password, nickname, role, status) VALUES
('admin',      'e10adc3949ba59abbe56e057f20f883e', '系统管理员', 'ADMIN',      1),
('dispatcher', 'e10adc3949ba59abbe56e057f20f883e', '调度员',     'DISPATCHER', 1),
('user',       'e10adc3949ba59abbe56e057f20f883e', '场站用户',   'USER',       1),
('viewer',     'e10adc3949ba59abbe56e057f20f883e', '只读访客',   'VIEWER',     1);

-- 演示设备（归属 user 用户, id=3）
INSERT INTO device (device_code, device_name, device_type, model, rated_power, capacity, comm_protocol, comm_address, owner_id, status) VALUES
('PV-001',  '光伏逆变器1号', 'PV',    'SG-50KTL',   50,   0,    'MODBUS_TCP', '192.168.1.101', 3, 1),
('PCS-001', '储能变流器1号', 'PCS',   'ES-100kW',   100,  200,  'MODBUS_TCP', '192.168.1.102', 3, 1),
('METER-001','关口电表1号',  'METER', 'DTS-353',    0,    0,    'MODBUS_TCP', '192.168.1.103', 3, 1),
('LOAD-001','园区负载1号',  'LOAD',  'LOAD-500',    500,  0,    'MODBUS_TCP', '192.168.1.104', 3, 1),
('GRID-001','电网进线',     'GRID',  'GRID-10kV',   1000, 0,    'MODBUS_TCP', '192.168.1.105', 3, 1),
('EV-001',  '充电桩1号',    'EV',    'DC-60kW',     60,   0,    'MODBUS_TCP', '192.168.1.106', 3, 1);

-- 演示告警规则
INSERT INTO alarm_rule (device_id, metric, min_value, max_value, level, enabled, owner_id) VALUES
(2, 'SOC',    20,  90,   'WARN',     1, 3),
(2, 'POWER', -90, 90,   'WARN',     1, 3),
(4, 'POWER', 0,   450,  'WARN',     1, 3),
(1, 'VOLTAGE', 380, 460, 'CRITICAL', 1, 3);

-- 演示调度策略
INSERT INTO strategy (name, strategy_type, device_id, params, priority, enabled, owner_id) VALUES
('峰谷套利-默认', 'PEAK_VALLEY', 2, '{"peakCharge":0.95,"valleyCharge":0.35,"peakDischarge":true,"timeWindow":"19:00-22:00"}', 10, 1, 3),
('自发自用-默认', 'SELF_USE', 2, '{"reserveSoc":30}', 8, 1, 3),
('削峰填谷-默认', 'PEAK_VALLEY', 2, '{"peakPeriod":"08:00-12:00","valleyPeriod":"00:00-06:00"}', 5, 0, 3);

-- 峰平谷时段（与报表电价一致）
INSERT INTO peak_valley_period (period_type, name, start_hour, end_hour, price, enabled) VALUES
('VALLEY', '谷时', 23, 7, 0.35, 1),
('PEAK',   '峰时', 10, 12, 0.95, 1),
('PEAK',   '峰时', 17, 21, 0.95, 1);
-- =====================================================
-- 12. 储能电池安全检测模块相关表
-- =====================================================

-- 采集子设备表（挂接在检测模块上下接口上）
DROP TABLE IF EXISTS sensor_device;
CREATE TABLE sensor_device (
  id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  module_id     BIGINT       NOT NULL COMMENT '检测模块设备ID(device表)',
  port_no       INT          NOT NULL COMMENT '接口编号 1-24(1-12上排,13-24下排)',
  port_position VARCHAR(10)  DEFAULT NULL COMMENT '接口位置: TOP/BOTTOM',
  device_code   VARCHAR(50)  NOT NULL COMMENT '采集设备编号',
  device_name   VARCHAR(100) NOT NULL COMMENT '采集设备名称',
  sensor_type   VARCHAR(20)  NOT NULL COMMENT '类型: TEMP/GAS/HUMIDITY/VOLTAGE/CURRENT/INSULATION/SMOKE/DI/BMS',
  comm_protocol VARCHAR(20)  DEFAULT 'MODBUS_RTU' COMMENT '通讯协议',
  slave_address INT          DEFAULT 1 COMMENT 'Modbus从站地址 1-247',
  baud_rate     INT          DEFAULT 9600 COMMENT '波特率',
  register_address INT       DEFAULT 0 COMMENT '寄存器地址',
  unit          VARCHAR(20)  DEFAULT NULL COMMENT '单位',
  warn_min      DOUBLE       DEFAULT NULL COMMENT '预警下限',
  warn_max      DOUBLE       DEFAULT NULL COMMENT '预警上限',
  crit_min      DOUBLE       DEFAULT NULL COMMENT '严重下限',
  crit_max      DOUBLE       DEFAULT NULL COMMENT '严重上限',
  emerg_min     DOUBLE       DEFAULT NULL COMMENT '紧急下限',
  emerg_max     DOUBLE       DEFAULT NULL COMMENT '紧急上限',
  match_status  TINYINT      NOT NULL DEFAULT 0 COMMENT '匹配状态: 0待匹配 1匹配中 2已匹配 3失败',
  status        TINYINT      NOT NULL DEFAULT 0 COMMENT '在线状态: 1在线 0离线',
  owner_id      BIGINT       NOT NULL COMMENT '归属用户ID',
  create_time   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_device_code (device_code),
  KEY idx_module (module_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采集子设备表';

-- 协议模板库
DROP TABLE IF EXISTS protocol;
CREATE TABLE protocol (
  id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  protocol_name VARCHAR(100) NOT NULL COMMENT '协议名称',
  protocol_type VARCHAR(20)  DEFAULT 'MODBUS_RTU' COMMENT '协议类型',
  sensor_type   VARCHAR(20)  DEFAULT NULL COMMENT '适用传感器类型',
  function_code INT          DEFAULT 3 COMMENT '功能码(3读保持寄存器)',
  register_address INT       DEFAULT 0 COMMENT '寄存器起始地址',
  register_count INT         DEFAULT 1 COMMENT '寄存器数量',
  data_type     VARCHAR(20)  DEFAULT 'INT16' COMMENT '数据类型',
  scale         DOUBLE       DEFAULT 1 COMMENT '量程换算系数',
  baud_rate     INT          DEFAULT 9600 COMMENT '波特率',
  data_bits     INT          DEFAULT 8 COMMENT '数据位',
  stop_bits     INT          DEFAULT 1 COMMENT '停止位',
  parity        VARCHAR(10)  DEFAULT 'NONE' COMMENT '校验位',
  description   VARCHAR(255) DEFAULT NULL COMMENT '描述',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='协议模板库';

-- 传感器时序数据
DROP TABLE IF EXISTS sensor_data;
CREATE TABLE sensor_data (
  id        BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
  sensor_id BIGINT   NOT NULL COMMENT '采集设备ID',
  ts        DATETIME NOT NULL COMMENT '数据时间',
  value     DOUBLE   DEFAULT NULL COMMENT '采集值',
  unit      VARCHAR(20) DEFAULT NULL COMMENT '单位',
  PRIMARY KEY (id),
  KEY idx_sensor_ts (sensor_id, ts)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='传感器时序数据表';

-- 检测模块演示设备（归属 user 用户 id=3，设备ID=7）
INSERT INTO device (device_code, device_name, device_type, model, rated_power, capacity, comm_protocol, comm_address, owner_id, status) VALUES
('SAFETY-001', '储能电池安全检测模块', 'SAFETY', 'BMS-SAFETY-24P', 0, 0, 'MODBUS_RTU', 'COM3', 3, 1);

-- 协议模板库初始数据
INSERT INTO protocol (protocol_name, protocol_type, sensor_type, function_code, register_address, register_count, data_type, scale, baud_rate, data_bits, stop_bits, parity, description) VALUES
('电池温度探头协议', 'MODBUS_RTU', 'TEMP', 3, 0, 1, 'INT16', 0.1, 9600, 8, 1, 'NONE', 'NTC温度探头，寄存器值乘0.1得摄氏度'),
('可燃气体传感器协议', 'MODBUS_RTU', 'GAS', 3, 1, 1, 'INT16', 1.0, 9600, 8, 1, 'NONE', '可燃气体浓度百分比LEL'),
('舱内湿度传感器协议', 'MODBUS_RTU', 'HUMIDITY', 3, 2, 1, 'INT16', 0.1, 9600, 8, 1, 'NONE', '电池舱湿度百分比RH'),
('单体电压采集协议', 'MODBUS_RTU', 'VOLTAGE', 3, 3, 1, 'INT16', 0.001, 9600, 8, 1, 'NONE', '单体电芯电压伏特'),
('充放电电流协议', 'MODBUS_RTU', 'CURRENT', 3, 4, 1, 'INT16', 0.1, 9600, 8, 1, 'NONE', '充放电电流安培'),
('绝缘电阻采集协议', 'MODBUS_RTU', 'INSULATION', 3, 5, 1, 'INT16', 0.01, 9600, 8, 1, 'NONE', '直流母线对地绝缘电阻兆欧'),
('烟感硬接点协议', 'MODBUS_RTU', 'SMOKE', 3, 6, 1, 'INT16', 1.0, 9600, 8, 1, 'NONE', '烟感状态0正常1触发'),
('硬接点DI协议', 'MODBUS_RTU', 'DI', 3, 7, 1, 'INT16', 1.0, 9600, 8, 1, 'NONE', '断路器熔断器门磁风机SPD状态'),
('BMS告警寄存器协议', 'MODBUS_RTU', 'BMS', 3, 8, 1, 'INT16', 1.0, 9600, 8, 1, 'NONE', 'BMS过温过压欠压过流寄存器');

-- 演示采集设备（挂接在检测模块 id=7 上）
INSERT INTO sensor_device (module_id, port_no, port_position, device_code, device_name, sensor_type, comm_protocol, slave_address, baud_rate, register_address, unit, warn_min, warn_max, crit_min, crit_max, emerg_min, emerg_max, match_status, status, owner_id) VALUES
(7, 1,  'TOP',    'TEMP-001', '电芯温度探头1', 'TEMP', 'MODBUS_RTU', 1, 9600, 0, '℃', NULL, 55, NULL, 65, NULL, 75, 2, 1, 3),
(7, 2,  'TOP',    'TEMP-002', '铜排温度探头1', 'TEMP', 'MODBUS_RTU', 2, 9600, 0, '℃', NULL, 55, NULL, 65, NULL, 75, 2, 1, 3),
(7, 3,  'TOP',    'GAS-001',  '可燃气体传感器1', 'GAS', 'MODBUS_RTU', 3, 9600, 1, '%LEL', NULL, 10, NULL, 25, NULL, 50, 2, 1, 3),
(7, 4,  'TOP',    'HUM-001',  '舱内湿度传感器1', 'HUMIDITY', 'MODBUS_RTU', 4, 9600, 2, '%RH', NULL, 80, NULL, 90, NULL, 95, 2, 1, 3),
(7, 13, 'BOTTOM', 'VOL-001',  '单体电压采集1', 'VOLTAGE', 'MODBUS_RTU', 5, 9600, 3, 'V', 2.80, 3.55, 2.60, 3.65, 2.30, 3.80, 2, 1, 3),
(7, 14, 'BOTTOM', 'SMOKE-001','烟感传感器1', 'SMOKE', 'MODBUS_RTU', 6, 9600, 6, '', NULL, 0.5, NULL, NULL, NULL, NULL, 2, 1, 3);