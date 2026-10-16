package com.energy.controller;

import com.energy.common.Result;
import com.energy.service.ReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/report")
public class ReportController {

    @Autowired
    private ReportService reportService;

    @GetMapping("/day")
    public Result<Map<String, Object>> day(@RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date) {
        return Result.success(reportService.dayReport(date));
    }

    @GetMapping("/month")
    public Result<Map<String, Object>> month(@RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return Result.success(reportService.monthReport(month.atDay(1)));
    }

    @GetMapping("/last7days")
    public Result<List<Map<String, Object>>> last7Days() {
        return Result.success(reportService.last7Days());
    }
}