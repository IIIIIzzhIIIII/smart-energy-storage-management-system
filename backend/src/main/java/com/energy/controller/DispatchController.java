package com.energy.controller;

import com.energy.common.Result;
import com.energy.entity.DispatchCommand;
import com.energy.entity.PeakValleyPeriod;
import com.energy.service.DispatchService;
import com.energy.service.DroService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dispatch")
public class DispatchController {

    @Autowired
    private DispatchService dispatchService;

    @Autowired
    private DroService droService;

    /** 当前调度状态（时段/策略/目标功率/SOC/最近指令） */
    @GetMapping("/current")
    public Result<Map<String, Object>> current() {
        return Result.success(dispatchService.current());
    }

    /** 最近调度指令 */
    @GetMapping("/list")
    public Result<List<DispatchCommand>> list(@RequestParam(defaultValue = "20") int limit) {
        return Result.success(dispatchService.recent(limit));
    }

    /** 峰平谷时段配置 */
    @GetMapping("/periods")
    public Result<List<PeakValleyPeriod>> periods() {
        return Result.success(dispatchService.periods());
    }

    /** DRO 鲁棒调度计划（来自 Python 服务，缓存） */
    @GetMapping("/dro-plan")
    public Result<Map<String, Object>> droPlan() {
        return Result.success(droService.getPlan());
    }

    /** 充电状态 + 充电订单（光储充一体化） */
    @GetMapping("/charging")
    public Result<Map<String, Object>> charging() {
        return Result.success(dispatchService.chargingStatus());
    }
}
