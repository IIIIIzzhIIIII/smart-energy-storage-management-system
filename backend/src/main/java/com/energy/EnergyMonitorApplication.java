package com.energy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class EnergyMonitorApplication {

    public static void main(String[] args) {
        SpringApplication.run(EnergyMonitorApplication.class, args);
        System.out.println("========== 光储智慧调度监控平台 后端启动成功 ==========");
    }
}