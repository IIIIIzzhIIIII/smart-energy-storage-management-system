-- ============================================================
-- 桌面发行版 H2 建表脚本（由 MySQL init.sql 转换）
-- 约定：
--   * 不写 ENGINE / CHARSET / COMMENT 等 MySQL 专属语法
--   * 自增统一使用 AUTO_INCREMENT（H2 MODE=MySQL 支持）
--   * UNIQUE KEY / KEY 改为表级约束与独立索引
--   * 标识符统一小写，与 MyBatis-Plus 实体一致
-- ============================================================

CREATE TABLE IF NOT EXISTS sys_user (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  username    VARCHAR(50)  NOT NULL,
  password    VARCHAR(100) NOT NULL,
  nickname    VARCHAR(50)  DEFAULT NULL,
  role        VARCHAR(20)  NOT NULL DEFAULT 'USER',
  status      TINYINT      NOT NULL DEFAULT 1,
  create_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT uk_sys_user_username UNIQUE (username)
);

CREATE TABLE IF NOT EXISTS device (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  device_code   VARCHAR(50)  NOT NULL,
  device_name   VARCHAR(100) NOT NULL,
  device_type   VARCHAR(20)  NOT NULL,
  model         VARCHAR(50)  DEFAULT NULL,
  rated_power   DOUBLE       DEFAULT 0,
  capacity      DOUBLE       DEFAULT 0,
  comm_protocol VARCHAR(20)  DEFAULT 'MODBUS_TCP',
  comm_address  VARCHAR(50)  DEFAULT NULL,
  owner_id      BIGINT       NOT NULL,
  status        TINYINT      NOT NULL DEFAULT 1,
  create_time   DATETIME     DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT uk_device_code UNIQUE (device_code)
);
CREATE INDEX IF NOT EXISTS idx_device_owner ON device (owner_id);

CREATE TABLE IF NOT EXISTS device_data (
  id         BIGINT   NOT NULL AUTO_INCREMENT,
  device_id  BIGINT   NOT NULL,
  ts         DATETIME NOT NULL,
  power      DOUBLE   DEFAULT 0,
  voltage    DOUBLE   DEFAULT 0,
  current    DOUBLE   DEFAULT 0,
  soc        DOUBLE   DEFAULT NULL,
  irradiance DOUBLE   DEFAULT NULL,
  energy_in  DOUBLE   DEFAULT NULL,
  energy_out DOUBLE   DEFAULT NULL,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_device_data_device_ts ON device_data (device_id, ts);

CREATE TABLE IF NOT EXISTS strategy (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  name          VARCHAR(100) NOT NULL,
  strategy_type VARCHAR(20)  NOT NULL,
  device_id     BIGINT       DEFAULT NULL,
  params        VARCHAR(500) DEFAULT NULL,
  priority      INT          DEFAULT 0,
  enabled       TINYINT      NOT NULL DEFAULT 1,
  owner_id      BIGINT       NOT NULL,
  create_time   DATETIME     DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_strategy_owner ON strategy (owner_id);

CREATE TABLE IF NOT EXISTS alarm_rule (
  id        BIGINT      NOT NULL AUTO_INCREMENT,
  device_id BIGINT      NOT NULL,
  metric    VARCHAR(20) NOT NULL,
  min_value DOUBLE      DEFAULT NULL,
  max_value DOUBLE      DEFAULT NULL,
  level     VARCHAR(20) NOT NULL DEFAULT 'WARN',
  enabled   TINYINT     NOT NULL DEFAULT 1,
  owner_id  BIGINT      NOT NULL,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_alarm_rule_device ON alarm_rule (device_id);

CREATE TABLE IF NOT EXISTS alarm_record (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  device_id   BIGINT       NOT NULL,
  device_name VARCHAR(100) DEFAULT NULL,
  metric      VARCHAR(20)  DEFAULT NULL,
  value       DOUBLE       DEFAULT NULL,
  threshold   VARCHAR(50)  DEFAULT NULL,
  level       VARCHAR(20)  DEFAULT 'WARN',
  message     VARCHAR(255) DEFAULT NULL,
  status      TINYINT      NOT NULL DEFAULT 0,
  owner_id    BIGINT       NOT NULL,
  create_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
  handle_time DATETIME     DEFAULT NULL,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_alarm_record_owner ON alarm_record (owner_id);
CREATE INDEX IF NOT EXISTS idx_alarm_record_status ON alarm_record (status);

CREATE TABLE IF NOT EXISTS energy_report (
  id           BIGINT      NOT NULL AUTO_INCREMENT,
  owner_id     BIGINT      NOT NULL,
  period       VARCHAR(10) NOT NULL,
  period_value VARCHAR(20) NOT NULL,
  generation   DOUBLE      DEFAULT 0,
  consumption  DOUBLE      DEFAULT 0,
  charge       DOUBLE      DEFAULT 0,
  discharge    DOUBLE      DEFAULT 0,
  grid_import  DOUBLE      DEFAULT 0,
  grid_export  DOUBLE      DEFAULT 0,
  revenue      DOUBLE      DEFAULT 0,
  PRIMARY KEY (id),
  CONSTRAINT uk_energy_report_period UNIQUE (owner_id, period, period_value)
);


CREATE TABLE IF NOT EXISTS sys_log (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  username    VARCHAR(50)  DEFAULT NULL,
  operation   VARCHAR(255) DEFAULT NULL,
  method      VARCHAR(255) DEFAULT NULL,
  ip          VARCHAR(50)  DEFAULT NULL,
  create_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS peak_valley_period (
  id          BIGINT      NOT NULL AUTO_INCREMENT,
  period_type VARCHAR(10) NOT NULL,
  name        VARCHAR(20) DEFAULT NULL,
  start_hour  INT         NOT NULL,
  end_hour    INT         NOT NULL,
  price       DOUBLE      DEFAULT 0,
  enabled     TINYINT     NOT NULL DEFAULT 1,
  PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS dispatch_command (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  device_id     BIGINT       DEFAULT NULL,
  strategy_id   BIGINT       DEFAULT NULL,
  strategy_type VARCHAR(20)  DEFAULT NULL,
  period_type   VARCHAR(10)  DEFAULT NULL,
  target_power  DOUBLE       DEFAULT 0,
  soc           DOUBLE       DEFAULT NULL,
  pv_power      DOUBLE       DEFAULT NULL,
  load_power    DOUBLE       DEFAULT NULL,
  reason        VARCHAR(255) DEFAULT NULL,
  create_time   DATETIME     DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_dispatch_command_time ON dispatch_command (create_time);

CREATE TABLE IF NOT EXISTS charging_session (
  id          BIGINT   NOT NULL AUTO_INCREMENT,
  device_id   BIGINT   DEFAULT NULL,
  start_time  DATETIME DEFAULT NULL,
  end_time    DATETIME DEFAULT NULL,
  energy      DOUBLE   DEFAULT 0,
  power       DOUBLE   DEFAULT 0,
  status      TINYINT  NOT NULL DEFAULT 0,
  owner_id    BIGINT   DEFAULT NULL,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_charging_session_device ON charging_session (device_id);

-- ------------------------------------------------------------
-- 储能安全检测模块（包含 safety_update.sql 执行后的最终列）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sensor_device (
  id               BIGINT       NOT NULL AUTO_INCREMENT,
  module_id        BIGINT       NOT NULL,
  port_no          INT          NOT NULL,
  port_position    VARCHAR(10)  DEFAULT NULL,
  device_code      VARCHAR(50)  NOT NULL,
  device_name      VARCHAR(100) NOT NULL,
  sensor_type      VARCHAR(20)  NOT NULL,
  comm_protocol    VARCHAR(20)  DEFAULT 'MODBUS_RTU',
  slave_address    INT          DEFAULT 1,
  baud_rate        INT          DEFAULT 9600,
  register_address INT          DEFAULT 0,
  register_offset  INT          DEFAULT 0,
  port_name        VARCHAR(32)  DEFAULT NULL,
  last_comm_time   DATETIME     DEFAULT NULL,
  unit             VARCHAR(20)  DEFAULT NULL,
  warn_min         DOUBLE       DEFAULT NULL,
  warn_max         DOUBLE       DEFAULT NULL,
  crit_min         DOUBLE       DEFAULT NULL,
  crit_max         DOUBLE       DEFAULT NULL,
  emerg_min        DOUBLE       DEFAULT NULL,
  emerg_max        DOUBLE       DEFAULT NULL,
  match_status     TINYINT      NOT NULL DEFAULT 0,
  status           TINYINT      NOT NULL DEFAULT 0,
  owner_id         BIGINT       NOT NULL,
  create_time      DATETIME     DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT uk_sensor_device_code UNIQUE (device_code)
);
CREATE INDEX IF NOT EXISTS idx_sensor_device_module ON sensor_device (module_id);

CREATE TABLE IF NOT EXISTS protocol (
  id               BIGINT       NOT NULL AUTO_INCREMENT,
  protocol_name    VARCHAR(100) NOT NULL,
  protocol_type    VARCHAR(20)  DEFAULT 'MODBUS_RTU',
  sensor_type      VARCHAR(20)  DEFAULT NULL,
  function_code    INT          DEFAULT 3,
  register_address INT          DEFAULT 0,
  register_count   INT          DEFAULT 1,
  data_type        VARCHAR(20)  DEFAULT 'INT16',
  scale            DOUBLE       DEFAULT 1,
  baud_rate        INT          DEFAULT 9600,
  data_bits        INT          DEFAULT 8,
  stop_bits        INT          DEFAULT 1,
  parity           VARCHAR(10)  DEFAULT 'NONE',
  description      VARCHAR(255) DEFAULT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS sensor_data (
  id        BIGINT   NOT NULL AUTO_INCREMENT,
  sensor_id BIGINT   NOT NULL,
  ts        DATETIME NOT NULL,
  value     DOUBLE   DEFAULT NULL,
  unit      VARCHAR(20) DEFAULT NULL,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_sensor_data_sensor_ts ON sensor_data (sensor_id, ts);
