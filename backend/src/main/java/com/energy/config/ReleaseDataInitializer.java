package com.energy.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;

/**
 * 一键运行版首次启动写入演示数据：
 *   建表由 application-release.yml 的 spring.sql.init 在 Bean 初始化前完成；
 *   本类在启动完成后检查 sys_user 是否为空，为空才写入 h2-data.sql。
 * 这样既避免定时任务在建表前查询报错，也保证重启后历史数据仍在。
 * 仅 release profile 生效，开发环境 MySQL 完全不受影响。
 */
@Slf4j
@Component
@Profile("release")
@Order(0)
public class ReleaseDataInitializer implements ApplicationRunner {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        Integer userCount;
        try {
            userCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_user", Integer.class);
        } catch (Exception e) {
            userCount = 0;
        }
        if (userCount != null && userCount > 0) {
            log.info("H2 数据库已初始化，跳过种子数据");
            return;
        }

        log.info("首次启动：正在写入 H2 演示数据...");
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection,
                    new EncodedResource(new ClassPathResource("db/h2-data.sql"), StandardCharsets.UTF_8));
            log.info("H2 演示数据初始化完成");
        } catch (Exception e) {
            throw new IllegalStateException("H2 演示数据初始化失败: " + e.getMessage(), e);
        }
    }
}
