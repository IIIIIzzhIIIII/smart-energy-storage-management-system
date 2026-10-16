-- =====================================================
-- 储能安全检测模块：真实 USB-RS485 温湿度变送器接入改造脚本
-- 在已有数据库上执行本文件即可（本文件只需执行一次）
-- 说明：传感器通讯协议依据设备说明书
--    Modbus-RTU / 8 数据位 / 无校验 / 1 停止位 / CRC16
--    只读功能码 0x03，寄存器 16bit 大端（高字节在前）
--    0x0000(40001) = 湿度  只读  值×0.1 得 %RH
--    0x0001(40002) = 温度  只读  值×0.1 得 ℃（低于 0℃ 时为补码）
--    从站地址出厂默认 0x01，波特率 4800(默认)/9600/19200...
--    一次事务读 0x0000 起 2 个寄存器即可同时拿到湿度和温度：
--      请求: 01 03 00 00 00 02 C4 0B
--      应答: 01 03 04 [湿度H 湿度L] [温度H 温度L] CRC_L CRC_H
-- =====================================================
USE energy_monitor;

-- -----------------------------------------------------
-- 1. sensor_device 增加真实串口相关列
--    port_name       实际串口号（如 COM5），一个物理 485 总线上可挂多个从站
--    register_offset 一次块读中该设备取第几个寄存器（湿度=0，温度=1）
--    last_comm_time  最后一次成功通讯时间（前端离线提示用）
-- -----------------------------------------------------
ALTER TABLE sensor_device
  ADD COLUMN port_name        VARCHAR(32) DEFAULT NULL COMMENT '串口号, 如 COM5',
  ADD COLUMN register_offset  INT         DEFAULT 0    COMMENT '块内寄存器偏移: 0=第1个 1=第2个',
  ADD COLUMN last_comm_time   DATETIME    DEFAULT NULL COMMENT '最后成功通讯时间';

-- -----------------------------------------------------
-- 2. 温湿度一体协议模板：两条都读 0x0000 起 2 个寄存器，靠偏移区分取哪一半
-- -----------------------------------------------------
UPDATE protocol
   SET register_address = 0,
       register_count   = 2,
       data_type        = 'INT16',
       scale            = 0.1,
       baud_rate        = 4800,
       data_bits        = 8,
       stop_bits        = 1,
       parity           = 'NONE',
       description      = '温湿度一体(说明书): 读0x0000起2寄存器, 16bit大端, 第1个=湿度×0.1 %RH'
 WHERE sensor_type = 'HUMIDITY';

UPDATE protocol
   SET register_address = 0,
       register_count   = 2,
       data_type        = 'INT16',
       scale            = 0.1,
       baud_rate        = 4800,
       data_bits        = 8,
       stop_bits        = 1,
       parity           = 'NONE',
       description      = '温湿度一体(说明书): 读0x0000起2寄存器, 16bit大端, 第2个=温度(补码)×0.1 ℃'
 WHERE sensor_type = 'TEMP';

-- -----------------------------------------------------
-- 3. 检测模块通讯地址改为实际使用的串口（本机为 USB-SERIAL CH340 => COM5）
--    如实际枚举出的串口号不同，请改成实际值（前端\"串口连接\"卡片可改）
-- -----------------------------------------------------
UPDATE device SET comm_address = 'COM5' WHERE device_type = 'SAFETY';

-- -----------------------------------------------------
-- 4. 现场 485 总线上只有 1 台从站（出厂地址 0x01），
--    先把温湿度两条设备的通讯参数统一改为"COM5 + 从站1 + 0x0000 起 2 个寄存器"
-- -----------------------------------------------------
UPDATE sensor_device sd
  JOIN device d ON d.id = sd.module_id
   SET sd.port_name        = 'COM5',
       sd.comm_protocol    = 'MODBUS_RTU',
       sd.baud_rate        = 4800,
       sd.slave_address    = 1,
       sd.register_address = 0,
       sd.register_offset  = IF(sd.sensor_type = 'TEMP', 1, 0),
       sd.unit             = IF(sd.sensor_type = 'TEMP', '℃', '%RH'),
       sd.match_status     = 0,
       sd.status           = 0,
       sd.last_comm_time   = NULL
 WHERE d.device_type = 'SAFETY'
   AND sd.sensor_type IN ('TEMP', 'HUMIDITY');

-- 5. 删除多余的演示设备：只保留每个类型（温度/湿度）id 最小的一条，
--    其余（重复的温湿度、可燃气体、电压、烟感等演示从站 2~6）全部删除，
--    否则真实模式下每 2 秒都会对不存在的从站空等超时。
--    ★ 若你希望保留其它类型的演示设备，可跳过本段 DELETE。
DELETE sd FROM sensor_device sd
  JOIN device d ON d.id = sd.module_id
 WHERE d.device_type = 'SAFETY'
   AND sd.id NOT IN (
       SELECT keep_id FROM (
           SELECT MIN(s2.id) AS keep_id
             FROM sensor_device s2
            WHERE s2.module_id IN (SELECT id FROM device WHERE device_type = 'SAFETY')
              AND s2.sensor_type IN ('TEMP', 'HUMIDITY')
            GROUP BY s2.sensor_type
       ) t
   );

-- 6. 若缺少温度 / 湿度设备则补齐（端口顺延到当前最大接口号之后，避免占用已被使用的接口）
INSERT INTO sensor_device
  (module_id, port_no, port_position, device_code, device_name, sensor_type, comm_protocol,
   slave_address, baud_rate, register_address, register_offset, unit,
   warn_min, warn_max, crit_min, crit_max, emerg_min, emerg_max, match_status, status, owner_id, port_name)
SELECT d.id,
       LEAST(IFNULL((SELECT MAX(port_no) FROM sensor_device WHERE module_id = d.id), 0) + 1, 24),
       IF(IFNULL((SELECT MAX(port_no) FROM sensor_device WHERE module_id = d.id), 0) + 1 <= 12, 'TOP', 'BOTTOM'),
       'HUM-001', '舱内湿度传感器1', 'HUMIDITY', 'MODBUS_RTU',
       1, 4800, 0, 0, '%RH', NULL, 80, NULL, 90, NULL, 95, 0, 0, d.owner_id, 'COM5'
  FROM device d
 WHERE d.device_type = 'SAFETY'
   AND NOT EXISTS (SELECT 1 FROM sensor_device s WHERE s.module_id = d.id AND s.sensor_type = 'HUMIDITY')
   AND NOT EXISTS (SELECT 1 FROM sensor_device s WHERE s.device_code = 'HUM-001');

INSERT INTO sensor_device
  (module_id, port_no, port_position, device_code, device_name, sensor_type, comm_protocol,
   slave_address, baud_rate, register_address, register_offset, unit,
   warn_min, warn_max, crit_min, crit_max, emerg_min, emerg_max, match_status, status, owner_id, port_name)
SELECT d.id,
       LEAST(IFNULL((SELECT MAX(port_no) FROM sensor_device WHERE module_id = d.id), 0) + 1, 24),
       IF(IFNULL((SELECT MAX(port_no) FROM sensor_device WHERE module_id = d.id), 0) + 1 <= 12, 'TOP', 'BOTTOM'),
       'TEMP-001', '舱内温度传感器1', 'TEMP', 'MODBUS_RTU',
       1, 4800, 0, 1, '℃', NULL, 55, NULL, 65, NULL, 75, 0, 0, d.owner_id, 'COM5'
  FROM device d
 WHERE d.device_type = 'SAFETY'
   AND NOT EXISTS (SELECT 1 FROM sensor_device s WHERE s.module_id = d.id AND s.sensor_type = 'TEMP')
   AND NOT EXISTS (SELECT 1 FROM sensor_device s WHERE s.device_code = 'TEMP-001');

-- -----------------------------------------------------
-- 校验（可选，手工执行查看结果）
-- SELECT sensor_type, slave_address, register_address, register_offset, unit, match_status, status, port_name
--   FROM sensor_device WHERE module_id = (SELECT MIN(id) FROM device WHERE device_type='SAFETY');
-- SELECT sensor_type, register_address, register_count, data_type, scale, baud_rate FROM protocol WHERE sensor_type IN ('TEMP','HUMIDITY');
-- -----------------------------------------------------
