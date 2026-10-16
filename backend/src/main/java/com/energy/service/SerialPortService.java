package com.energy.service;

import com.energy.dto.MatchResult;
import com.energy.entity.Protocol;
import com.energy.entity.SensorDevice;

/**
 * 串口通信抽象：USB 转 RS485 采集通道
 * 实现类通过 energy.safety.mode 配置切换（simulate 模拟 / real 真串口）
 */
public interface SerialPortService {

    MatchResult match(Protocol protocol, SensorDevice sensor);

    Double readValue(Protocol protocol, SensorDevice sensor);

    boolean isSimulate();
}