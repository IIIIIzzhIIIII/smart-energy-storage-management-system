package com.energy.controller;

import com.energy.common.Result;
import com.energy.service.WeatherService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/weather")
public class WeatherController {

    @Autowired
    private WeatherService weatherService;

    /**
     * 实时天气 + 定位信息（大屏展示）
     * 可选传 lat/lon：前端浏览器 GPS 定位时传入，后端据此查询；不传则用 IP 定位
     */
    @GetMapping("/current")
    public Result<Map<String, Object>> current(
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lon) {
        return Result.success(weatherService.current(lat, lon));
    }
}
