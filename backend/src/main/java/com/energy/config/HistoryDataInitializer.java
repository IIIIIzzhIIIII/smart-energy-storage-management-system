package com.energy.config;

import com.energy.service.DataSimulateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 启动时根据配置自动生成历史模拟数据，方便大屏/报表演示历史曲线。
 * 通过 application.yml 中的 energy.history-days 控制（0 表示不生成）。
 */
@Slf4j
@Component
@Order(1)
public class HistoryDataInitializer implements ApplicationRunner {

    @Autowired
    private DataSimulateService dataSimulateService;

    @Value("${energy.history-days:0}")
    private int historyDays;

    @Override
    public void run(ApplicationArguments args) {
        if (historyDays <= 0) {
            return;
        }
        long begin = System.currentTimeMillis();
        int inserted = dataSimulateService.generateHistory(historyDays, false);
        log.info("历史模拟数据初始化结束，生成 {} 条记录，耗时 {} ms", inserted, System.currentTimeMillis() - begin);
    }
}
