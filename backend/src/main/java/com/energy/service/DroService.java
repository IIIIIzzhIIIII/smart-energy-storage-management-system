package com.energy.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * DRO 调度服务：调用 Python 服务的 /api/dispatch/plan 获取鲁棒调度计划，
 * 本地缓存供大屏展示（该接口需要跑两次 SARIMA + Copula + 优化，耗时约 10s，
 * 因此定时拉取缓存，而非每次请求实时计算）。
 */
@Slf4j
@Service
public class DroService {

    @Value("${energy.python.base-url:http://localhost:8000}")
    private String baseUrl;

    @Value("${energy.dro-refresh-ms:300000}")
    private long refreshMs;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = restTemplate();

    private volatile Map<String, Object> cache;
    private volatile String lastError;
    private volatile String updateTime;
    private volatile long lastSuccessAt;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 轮询：缓存为空时每 pollMs 重试一次；已有缓存时按 refreshMs 刷新。
     * 这样 Python 服务晚启动/临时不可用时，恢复后 15 秒内即可自动补上计划。
     */
    @Scheduled(fixedDelayString = "${energy.dro-poll-ms:15000}", initialDelayString = "5000")
    public void refresh() {
        long now = System.currentTimeMillis();
        if (cache != null && now - lastSuccessAt < refreshMs) {
            return;
        }
        try {
            cache = fetchPlan();
            lastError = null;
            updateTime = LocalDateTime.now().format(TIME_FMT);
            lastSuccessAt = System.currentTimeMillis();
            log.info("DRO 计划更新成功");
        } catch (Exception e) {
            lastError = e.getMessage();
            log.warn("获取 DRO 计划失败（Python 服务是否已启动？）: {}", e.getMessage());
        }
    }

    /**
     * 供前端读取当前 DRO 计划
     */
    public Map<String, Object> getPlan() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("plan", cache);
        result.put("updateTime", updateTime);
        result.put("error", lastError);
        result.put("available", cache != null);
        return result;
    }

    private Map<String, Object> fetchPlan() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = new HashMap<>();
        body.put("steps", 24);
        body.put("n_scenarios", 50);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<String> resp = restTemplate.postForEntity(
                baseUrl + "/api/dispatch/plan", entity, String.class);
        return objectMapper.readValue(resp.getBody(), Map.class);
    }

    private RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(120000); // DRO 计算较慢，放宽读取超时
        return new RestTemplate(factory);
    }
}
