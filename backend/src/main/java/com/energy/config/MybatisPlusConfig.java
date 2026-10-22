package com.energy.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MybatisPlusConfig {

    @Value("${spring.datasource.driver-class-name:com.mysql.cj.jdbc.Driver}")
    private String driverClassName;

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 开发环境 MySQL，一键运行版 H2；分页方言必须与驱动一致
        DbType dbType = driverClassName != null && driverClassName.toLowerCase().contains("h2")
                ? DbType.H2 : DbType.MYSQL;
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(dbType));
        return interceptor;
    }
}
