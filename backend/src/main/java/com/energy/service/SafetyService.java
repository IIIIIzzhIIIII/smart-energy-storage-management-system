package com.energy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.energy.common.BusinessException;
import com.energy.context.PermissionUtil;
import com.energy.context.UserContext;
import com.energy.dto.FrameRecord;
import com.energy.dto.MatchResult;
import com.energy.entity.Device;
import com.energy.entity.Protocol;
import com.energy.entity.SensorData;
import com.energy.entity.SensorDevice;
import com.energy.mapper.DeviceMapper;
import com.energy.mapper.ProtocolMapper;
import com.energy.mapper.SensorDataMapper;
import com.energy.mapper.SensorDeviceMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class SafetyService {

    @Autowired
    private SensorDeviceMapper sensorDeviceMapper;
    @Autowired
    private ProtocolMapper protocolMapper;
    @Autowired
    private SensorDataMapper sensorDataMapper;
    @Autowired
    private DeviceMapper deviceMapper;
    @Autowired
    private SerialPortService serialPortService;
    @Autowired
    private SerialChannelManager serialChannelManager;
    @Autowired
    private SerialFrameLog serialFrameLog;

    /** 检测模块默认使用的串口（USB-RS485 枚举出的端口，可在 application.yml 中调整） */
    @Value("${energy.safety.serial-port:COM5}")
    private String defaultSerialPort;

    /** 获取（或自动创建）储能安全检测模块设备 */
    public Device getModule() {
        LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<Device>()
                .eq(Device::getDeviceType, "SAFETY");
        if (!PermissionUtil.isAdmin()) {
            wrapper.eq(Device::getOwnerId, UserContext.getUserId());
        }
        wrapper.orderByAsc(Device::getId).last("LIMIT 1");
        Device module = deviceMapper.selectOne(wrapper);
        if (module == null) {
            module = new Device();
            module.setDeviceCode("SAFETY-" + UserContext.getUserId());
            module.setDeviceName("储能电池安全检测模块");
            module.setDeviceType("SAFETY");
            module.setModel("BMS-SAFETY-24P");
            module.setRatedPower(0.0);
            module.setCapacity(0.0);
            module.setCommProtocol("MODBUS_RTU");
            module.setCommAddress(defaultSerialPort);
            module.setOwnerId(UserContext.getUserId());
            module.setStatus(1);
            deviceMapper.insert(module);
        }
        return module;
    }

    /** 传感器列表（含最新读数） */
    public List<SensorDevice> listSensors(Long moduleId) {
        LambdaQueryWrapper<SensorDevice> wrapper = new LambdaQueryWrapper<SensorDevice>()
                .eq(SensorDevice::getModuleId, moduleId)
                .orderByAsc(SensorDevice::getPortNo);
        List<SensorDevice> list = sensorDeviceMapper.selectList(wrapper);
        for (SensorDevice s : list) {
            s.setLatestValue(latestValue(s.getId()));
        }
        return list;
    }

    private Double latestValue(Long sensorId) {
        SensorData data = sensorDataMapper.selectOne(new LambdaQueryWrapper<SensorData>()
                .eq(SensorData::getSensorId, sensorId)
                .orderByDesc(SensorData::getId)
                .last("LIMIT 1"));
        return data == null ? null : data.getValue();
    }

    /** 协议模板库 */
    public List<Protocol> listProtocols() {
        return protocolMapper.selectList(new LambdaQueryWrapper<Protocol>().orderByAsc(Protocol::getId));
    }

    /**
     * 新增采集设备：先进行协议匹配（探测设备是否已插上并响应），
     * 匹配成功才保存；匹配失败不保存并返回失败原因。
     */
    public MatchResult addSensorWithMatch(SensorDevice sensor) {
        PermissionUtil.requireWrite();
        Device module = getModule();
        sensor.setId(null);
        sensor.setModuleId(module.getId());
        if (!PermissionUtil.isAdmin() || sensor.getOwnerId() == null) {
            sensor.setOwnerId(UserContext.getUserId());
        }
        // 端口位置按编号自动推导：1-12 上排，13-24 下排
        int portNo = sensor.getPortNo() == null ? 1 : sensor.getPortNo();
        sensor.setPortNo(portNo);
        sensor.setPortPosition(portNo <= 12 ? "TOP" : "BOTTOM");
        // 校验端口占用
        Long count = sensorDeviceMapper.selectCount(new LambdaQueryWrapper<SensorDevice>()
                .eq(SensorDevice::getModuleId, module.getId())
                .eq(SensorDevice::getPortNo, portNo));
        if (count != null && count > 0) {
            throw new BusinessException("接口 " + portNo + " 已被占用");
        }
        // 校验设备编号唯一
        Long codeCount = sensorDeviceMapper.selectCount(new LambdaQueryWrapper<SensorDevice>()
                .eq(SensorDevice::getDeviceCode, sensor.getDeviceCode()));
        if (codeCount != null && codeCount > 0) {
            throw new BusinessException("设备编号已存在");
        }
        if (!StringUtils.hasText(sensor.getCommProtocol())) sensor.setCommProtocol("MODBUS_RTU");
        if (sensor.getSlaveAddress() == null) sensor.setSlaveAddress(1);
        if (!StringUtils.hasText(sensor.getPortName())) sensor.setPortName(module.getCommAddress());
        if (sensor.getRegisterOffset() == null) {
            // 温湿度一体变送器：一次块读 2 个寄存器，湿度在第 1 个(偏移 0)、温度在第 2 个(偏移 1)
            sensor.setRegisterOffset("TEMP".equals(sensor.getSensorType()) ? 1 : 0);
        }
        applyDefaults(sensor);

        // 关键：先与该采集设备做协议匹配（真实模式下会向 RS485 从站发探测帧，确认设备已插上并响应）
        Protocol protocol = protocolMapper.selectOne(new LambdaQueryWrapper<Protocol>()
                .eq(Protocol::getSensorType, sensor.getSensorType())
                .last("LIMIT 1"));
        MatchResult result = serialPortService.match(protocol, sensor);
        if (sensor.getBaudRate() == null) {
            sensor.setBaudRate(protocol != null && protocol.getBaudRate() != null ? protocol.getBaudRate() : 4800);
        }
        if (!result.isSuccess()) {
            // 匹配失败：不保存，返回失败原因
            return result;
        }
        sensor.setMatchStatus(2);
        sensor.setStatus(1);
        sensorDeviceMapper.insert(sensor);
        return result;
    }

    public void updateSensor(SensorDevice sensor) {
        PermissionUtil.requireWrite();
        SensorDevice exist = sensorDeviceMapper.selectById(sensor.getId());
        if (exist == null) throw new BusinessException("采集设备不存在");
        if (!PermissionUtil.isAdmin() && !exist.getOwnerId().equals(UserContext.getUserId())) {
            throw new BusinessException(403, "无权操作该设备");
        }
        sensorDeviceMapper.updateById(sensor);
    }

    public void deleteSensor(Long id) {
        PermissionUtil.requireWrite();
        SensorDevice exist = sensorDeviceMapper.selectById(id);
        if (exist != null && !PermissionUtil.isAdmin() && !exist.getOwnerId().equals(UserContext.getUserId())) {
            throw new BusinessException(403, "无权操作该设备");
        }
        sensorDeviceMapper.deleteById(id);
    }

    /** 协议匹配：发送探测帧，成功后置为已匹配+在线 */
    public MatchResult match(Long sensorId) {
        PermissionUtil.requireWrite();
        SensorDevice sensor = sensorDeviceMapper.selectById(sensorId);
        if (sensor == null) throw new BusinessException("采集设备不存在");
        Protocol protocol = protocolMapper.selectOne(new LambdaQueryWrapper<Protocol>()
                .eq(Protocol::getSensorType, sensor.getSensorType())
                .last("LIMIT 1"));
        MatchResult result = serialPortService.match(protocol, sensor);
        sensor.setMatchStatus(result.isSuccess() ? 2 : 3);
        sensor.setStatus(result.isSuccess() ? 1 : 0);
        sensorDeviceMapper.updateById(sensor);
        return result;
    }

    /**
     * 切换检测模块使用的串口（前端"串口连接"卡片选择 USB-RS485 实际枚举到的端口）。
     * 同步把该模块下仍指向旧端口的采集设备切到新端口，并重置为待匹配，等待重新识别。
     */
    public void updateModulePort(String portName) {
        PermissionUtil.requireWrite();
        if (!StringUtils.hasText(portName)) {
            throw new BusinessException("串口号不能为空");
        }
        Device module = getModule();
        String oldPort = module.getCommAddress();

        Device moduleUpdate = new Device();
        moduleUpdate.setId(module.getId());
        moduleUpdate.setCommAddress(portName);
        deviceMapper.updateById(moduleUpdate);

        List<SensorDevice> sensors = sensorDeviceMapper.selectList(new LambdaQueryWrapper<SensorDevice>()
                .eq(SensorDevice::getModuleId, module.getId()));
        for (SensorDevice s : sensors) {
            if (StringUtils.hasText(s.getPortName()) && !s.getPortName().equals(oldPort)) {
                continue; // 该设备已单独指定了其它串口，不动它
            }
            if (portName.equalsIgnoreCase(s.getPortName())) {
                continue; // 串口没有变化，保持当前匹配/在线状态，避免打断正在采集的设备
            }
            SensorDevice update = new SensorDevice();
            update.setId(s.getId());
            update.setPortName(portName);
            update.setMatchStatus(0);
            update.setStatus(0);
            sensorDeviceMapper.updateById(update);
        }
        serialChannelManager.invalidate(oldPort);
        serialChannelManager.invalidate(portName);
    }

    /** 最近的串口原始报文（协议匹配 / 端口识别），用于前端排查接线与参数 */
    public List<FrameRecord> recentFrames(int limit) {
        return serialFrameLog.recent(limit);
    }

    /** 默认阈值 + 单位（按检测报告与行业惯例） */
    private void applyDefaults(SensorDevice s) {
        if (s.getUnit() == null) s.setUnit(unitOf(s.getSensorType()));
        if (s.getWarnMin() == null && s.getWarnMax() == null && s.getCritMin() == null && s.getCritMax() == null
                && s.getEmergMin() == null && s.getEmergMax() == null) {
            switch (s.getSensorType() == null ? "" : s.getSensorType()) {
                case "TEMP": s.setWarnMax(55.0); s.setCritMax(65.0); s.setEmergMax(75.0); break;
                case "GAS": s.setWarnMax(10.0); s.setCritMax(25.0); s.setEmergMax(50.0); break;
                case "HUMIDITY": s.setWarnMax(80.0); s.setCritMax(90.0); s.setEmergMax(95.0); break;
                case "VOLTAGE":
                    s.setWarnMin(2.80); s.setWarnMax(3.55);
                    s.setCritMin(2.60); s.setCritMax(3.65);
                    s.setEmergMin(2.30); s.setEmergMax(3.80); break;
                case "CURRENT": s.setWarnMax(165.0); s.setCritMax(195.0); s.setEmergMax(225.0); break;
                case "INSULATION": s.setWarnMin(1.0); s.setCritMin(0.5); s.setEmergMin(0.2); break;
                case "SMOKE": case "DI": case "BMS": s.setWarnMax(0.5); break;
                default: break;
            }
        }
    }

    private String unitOf(String type) {
        switch (type == null ? "" : type) {
            case "TEMP": return "℃";
            case "GAS": return "%LEL";
            case "HUMIDITY": return "%RH";
            case "VOLTAGE": return "V";
            case "CURRENT": return "A";
            case "INSULATION": return "MΩ";
            default: return "";
        }
    }
}