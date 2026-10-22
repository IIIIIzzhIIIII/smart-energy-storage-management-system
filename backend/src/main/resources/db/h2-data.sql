-- ============================================================
-- 一键运行版 H2 初始数据（MySQL init.sql + safety_update.sql 的最终状态）
-- 所有账号密码均为 123456
-- ============================================================

INSERT INTO sys_user (username, password, nickname, role, status) VALUES
('admin',      'e10adc3949ba59abbe56e057f20f883e', '系统管理员', 'ADMIN',      1),
('dispatcher', 'e10adc3949ba59abbe56e057f20f883e', '调度员',     'DISPATCHER', 1),
('user',       'e10adc3949ba59abbe56e057f20f883e', '场站用户',   'USER',       1),
('viewer',     'e10adc3949ba59abbe56e057f20f883e', '只读访客',   'VIEWER',     1);

-- 演示设备（归属 user 用户, id=3；SAFETY 模块 id=7）
INSERT INTO device (device_code, device_name, device_type, model, rated_power, capacity, comm_protocol, comm_address, owner_id, status) VALUES
('PV-001',     '光伏逆变器1号',       'PV',     'SG-50KTL',       50,   0,   'MODBUS_TCP', '192.168.1.101', 3, 1),
('PCS-001',    '储能变流器1号',       'PCS',    'ES-100kW',       100,  200, 'MODBUS_TCP', '192.168.1.102', 3, 1),
('METER-001',  '关口电表1号',         'METER',  'DTS-353',        0,    0,   'MODBUS_TCP', '192.168.1.103', 3, 1),
('LOAD-001',   '园区负载1号',         'LOAD',   'LOAD-500',       500,  0,   'MODBUS_TCP', '192.168.1.104', 3, 1),
('GRID-001',   '电网进线',            'GRID',   'GRID-10kV',      1000, 0,   'MODBUS_TCP', '192.168.1.105', 3, 1),
('EV-001',     '充电桩1号',           'EV',     'DC-60kW',        60,   0,   'MODBUS_TCP', '192.168.1.106', 3, 1),
('SAFETY-001', '储能电池安全检测模块', 'SAFETY', 'BMS-SAFETY-24P', 0,    0,   'MODBUS_RTU', 'COM5',          3, 1);

-- 演示告警规则
INSERT INTO alarm_rule (device_id, metric, min_value, max_value, level, enabled, owner_id) VALUES
(2, 'SOC',     20,  90,   'WARN',     1, 3),
(2, 'POWER',  -90,  90,   'WARN',     1, 3),
(4, 'POWER',   0,   450,  'WARN',     1, 3),
(1, 'VOLTAGE', 380, 460,  'CRITICAL', 1, 3);

-- 演示调度策略
INSERT INTO strategy (name, strategy_type, device_id, params, priority, enabled, owner_id) VALUES
('峰谷套利-默认', 'PEAK_VALLEY', 2, '{"peakCharge":0.95,"valleyCharge":0.35,"peakDischarge":true,"timeWindow":"19:00-22:00"}', 10, 1, 3),
('自发自用-默认', 'SELF_USE',    2, '{"reserveSoc":30}', 8, 1, 3),
('削峰填谷-默认', 'PEAK_VALLEY', 2, '{"peakPeriod":"08:00-12:00","valleyPeriod":"00:00-06:00"}', 5, 0, 3);

-- 峰平谷时段（与报表电价一致）
INSERT INTO peak_valley_period (period_type, name, start_hour, end_hour, price, enabled) VALUES
('VALLEY', '谷时', 23, 7,  0.35, 1),
('PEAK',   '峰时', 10, 12, 0.95, 1),
('PEAK',   '峰时', 17, 21, 0.95, 1);

-- 协议模板（TEMP / HUMIDITY 为 safety_update.sql 执行后的温湿度一体配置）
INSERT INTO protocol (protocol_name, protocol_type, sensor_type, function_code, register_address, register_count, data_type, scale, baud_rate, data_bits, stop_bits, parity, description) VALUES
('电池温度探头协议',   'MODBUS_RTU', 'TEMP',     3, 0, 2, 'INT16', 0.1,  4800, 8, 1, 'NONE', '温湿度一体(说明书): 读0x0000起2寄存器, 16bit大端, 第2个=温度(补码)×0.1 ℃'),
('可燃气体传感器协议', 'MODBUS_RTU', 'GAS',      3, 1, 1, 'INT16', 1.0,  9600, 8, 1, 'NONE', '可燃气体浓度百分比LEL'),
('舱内湿度传感器协议', 'MODBUS_RTU', 'HUMIDITY', 3, 0, 2, 'INT16', 0.1,  4800, 8, 1, 'NONE', '温湿度一体(说明书): 读0x0000起2寄存器, 16bit大端, 第1个=湿度×0.1 %RH'),
('单体电压采集协议',   'MODBUS_RTU', 'VOLTAGE',  3, 3, 1, 'INT16', 0.001, 9600, 8, 1, 'NONE', '单体电芯电压伏特'),
('充放电电流协议',     'MODBUS_RTU', 'CURRENT',  3, 4, 1, 'INT16', 0.1,  9600, 8, 1, 'NONE', '充放电电流安培'),
('绝缘电阻采集协议',   'MODBUS_RTU', 'INSULATION', 3, 5, 1, 'INT16', 0.01, 9600, 8, 1, 'NONE', '直流母线对地绝缘电阻兆欧'),
('烟感硬接点协议',     'MODBUS_RTU', 'SMOKE',    3, 6, 1, 'INT16', 1.0,  9600, 8, 1, 'NONE', '烟感状态0正常1触发'),
('硬接点DI协议',       'MODBUS_RTU', 'DI',       3, 7, 1, 'INT16', 1.0,  9600, 8, 1, 'NONE', '断路器熔断器门磁风机SPD状态');

-- 真实 485 总线上的温湿度变送器（模拟模式下也会生成数据）
INSERT INTO sensor_device (module_id, port_no, port_position, device_code, device_name, sensor_type, comm_protocol,
  slave_address, baud_rate, register_address, register_offset, unit, warn_min, warn_max, crit_min, crit_max,
  emerg_min, emerg_max, match_status, status, owner_id, port_name) VALUES
(7, 1, 'TOP', 'TEMP-001', '电芯温度探头1',   'TEMP',     'MODBUS_RTU', 1, 4800, 0, 1, '℃',   NULL, 55, NULL, 65, NULL, 75, 0, 0, 3, 'COM5'),
(7, 4, 'TOP', 'HUM-001',  '舱内湿度传感器1', 'HUMIDITY', 'MODBUS_RTU', 1, 4800, 0, 0, '%RH', NULL, 80, NULL, 90, NULL, 95, 0, 0, 3, 'COM5');
