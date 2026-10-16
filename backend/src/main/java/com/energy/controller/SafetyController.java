package com.energy.controller;

import com.energy.common.Result;
import com.energy.dto.FrameRecord;
import com.energy.dto.IdentifyResult;
import com.energy.dto.MatchResult;
import com.energy.dto.PortInfo;
import com.energy.entity.Device;
import com.energy.entity.Protocol;
import com.energy.entity.SensorDevice;
import com.energy.service.SafetyProbeService;
import com.energy.service.SafetyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/safety")
public class SafetyController {

    @Autowired
    private SafetyService safetyService;

    @Autowired
    private SafetyProbeService safetyProbeService;

    @GetMapping("/module")
    public Result<Device> module() {
        return Result.success(safetyService.getModule());
    }

    @GetMapping("/sensors")
    public Result<List<SensorDevice>> sensors(@RequestParam Long moduleId) {
        return Result.success(safetyService.listSensors(moduleId));
    }

    @GetMapping("/protocols")
    public Result<List<Protocol>> protocols() {
        return Result.success(safetyService.listProtocols());
    }

    /**
     * 新增采集设备：服务端先做协议匹配探测，匹配成功才保存
     */
    @PostMapping("/sensor")
    public Result<MatchResult> add(@RequestBody SensorDevice sensor) {
        return Result.success(safetyService.addSensorWithMatch(sensor));
    }

    @PutMapping("/sensor")
    public Result<Void> update(@RequestBody SensorDevice sensor) {
        safetyService.updateSensor(sensor);
        return Result.success();
    }

    @DeleteMapping("/sensor/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        safetyService.deleteSensor(id);
        return Result.success();
    }

    @PostMapping("/sensor/{id}/match")
    public Result<MatchResult> match(@PathVariable Long id) {
        return Result.success(safetyService.match(id));
    }

    /** 枚举本机串口（用于识别接入的 USB-RS485 模块） */
    @GetMapping("/ports")
    public Result<List<PortInfo>> ports() {
        return Result.success(safetyProbeService.scanPorts());
    }

    /** 切换检测模块使用的串口，body: { "portName": "COM5" } */
    @PutMapping("/module/port")
    public Result<Void> updateModulePort(@RequestBody Map<String, String> body) {
        safetyService.updateModulePort(body == null ? null : body.get("portName"));
        return Result.success();
    }

    /**
     * 识别串口上的温湿度变送器：自动探测波特率与从站地址，返回实测湿度/温度与原始帧。
     * body: { "portName": "COM5", "baudRate": 4800, "slaveAddress": 1 }（后两项可省略）
     */
    @PostMapping("/identify")
    public Result<IdentifyResult> identify(@RequestBody(required = false) Map<String, Object> body) {
        String portName = body == null ? null : String.valueOf(body.getOrDefault("portName", "")).trim();
        return Result.success(safetyProbeService.identify(portName, toInteger(body, "baudRate"), toInteger(body, "slaveAddress")));
    }

    /** 最近的串口原始报文（协议匹配 / 端口识别），便于排查接线与参数 */
    @GetMapping("/frames")
    public Result<List<FrameRecord>> frames(@RequestParam(defaultValue = "50") Integer limit) {
        return Result.success(safetyService.recentFrames(limit));
    }

    private Integer toInteger(Map<String, Object> body, String key) {
        if (body == null || body.get(key) == null) {
            return null;
        }
        Object value = body.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}