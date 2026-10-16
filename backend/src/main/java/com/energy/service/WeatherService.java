package com.energy.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * 天气服务：调用和风天气实时天气 + 逆地理定位接口，并做本地缓存。
 * 供大屏展示「站点定位 + 实时气象」，后续也可作为光伏预测的天气输入。
 */
@Slf4j
@Service
public class WeatherService {

    @Value("${energy.weather.api-host}")
    private String apiHost;

    @Value("${energy.weather.api-key}")
    private String apiKey;

    @Value("${energy.weather.latitude}")
    private double latitude;

    @Value("${energy.weather.longitude}")
    private double longitude;

    @Value("${energy.weather.cache-seconds:600}")
    private long cacheSeconds;

    @Value("${energy.weather.use-ip-location:true}")
    private boolean useIpLocation;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = restTemplate();

    private volatile Map<String, Object> cache;
    private volatile long lastFetchTime = 0L;
    private volatile boolean locationResolved = false;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 首次请求时按运行机器公网 IP 自动定位（失败则回退到配置坐标）。
     * 懒加载，不阻塞启动。
     */
    private void ensureLocationResolved() {
        if (locationResolved || !useIpLocation) {
            return;
        }
        synchronized (this) {
            if (locationResolved || !useIpLocation) {
                return;
            }
            resolveLocationByIp();
            locationResolved = true;
        }
    }

    private void resolveLocationByIp() {
        try {
            ResponseEntity<String> resp = restTemplate.getForEntity("https://api.ip.sb/geoip", String.class);
            JsonNode node = objectMapper.readTree(resp.getBody());
            if (node.hasNonNull("latitude") && node.hasNonNull("longitude")) {
                latitude = node.path("latitude").asDouble(latitude);
                longitude = node.path("longitude").asDouble(longitude);
                log.info("IP 自动定位成功: lat={}, lon={}", latitude, longitude);
            }
        } catch (Exception e) {
            log.warn("IP 自动定位失败，使用配置坐标: {}", e.getMessage());
        }
    }

    private RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(20000);
        return new RestTemplate(factory);
    }

    /**
     * 获取实时天气 + 定位信息（带缓存）
     */
    public Map<String, Object> current() {
        return current(null, null);
    }

    public Map<String, Object> current(Double lat, Double lon) {
        if (lat != null && lon != null) {
            // 浏览器 GPS 定位：坐标变化时更新并失效缓存
            if (Math.abs(lat - latitude) > 1e-6 || Math.abs(lon - longitude) > 1e-6) {
                latitude = lat;
                longitude = lon;
                locationResolved = true;
                cache = null;
            }
        } else {
            ensureLocationResolved();
        }
        long now = System.currentTimeMillis();
        if (cache != null && (now - lastFetchTime) < cacheSeconds * 1000L) {
            return cache;
        }
        synchronized (this) {
            if (cache != null && (now - lastFetchTime) < cacheSeconds * 1000L) {
                return cache;
            }
            Map<String, Object> result = fetch();
            cache = result;
            lastFetchTime = System.currentTimeMillis();
            return result;
        }
    }

    private Map<String, Object> fetch() {
        Map<String, Object> result = new LinkedHashMap<>();

        // 1) 定位（逆地理）：失败不影响天气展示
        Map<String, Object> location = new LinkedHashMap<>();
        location.put("lat", latitude);
        location.put("lon", longitude);
        try {
            String geoUrl = apiHost + "/geo/v2/city/lookup?location=" + longitude + "," + latitude;
            JsonNode geo = getJson(geoUrl);
            if (geo != null && geo.path("location").isArray() && geo.path("location").size() > 0) {
                JsonNode loc = geo.path("location").get(0);
                location.put("province", loc.path("adm1").asText(""));
                location.put("city", loc.path("adm2").asText(""));
                location.put("district", loc.path("name").asText(""));
            }
        } catch (Exception e) {
            log.warn("逆地理定位失败: {}", e.getMessage());
        }
        result.put("location", location);

        // 2) 实时天气：失败则降级为 available=false
        try {
            String weatherUrl = apiHost + "/weather/v1/current/" + latitude + "/" + longitude;
            JsonNode weather = getJson(weatherUrl);
            result.put("condition", weather.path("condition").path("text").asText("--"));
            result.put("conditionCode", weather.path("condition").path("code").asText(""));
            result.put("temperature", weather.path("temperature").path("value").asDouble(0));
            result.put("feelsLike", weather.path("feelsLike").path("value").asDouble(0));
            result.put("humidity", Math.round(weather.path("humidity").asDouble(0) * 100)); // 0~1 -> 百分比
            result.put("windSpeed", weather.path("wind").path("speed").path("value").asDouble(0));
            result.put("windDirection", windDirectionText(weather.path("wind").path("direction").path("compass").asText("")));
            result.put("windScale", weather.path("wind").path("scale").asInt(0));
            result.put("pressure", weather.path("pressure").path("value").asDouble(0));
            result.put("cloudCover", Math.round(weather.path("cloudCover").asDouble(0) * 100)); // 0~1 -> 百分比
            result.put("uvIndex", weather.path("uvIndex").asInt(0));
            result.put("available", true);
        } catch (Exception e) {
            log.error("获取天气数据失败: {}", e.getMessage());
            result.put("available", false);
            result.put("error", e.getMessage());
        }

        result.put("updateTime", LocalDateTime.now().format(TIME_FMT));
        return result;
    }

    private JsonNode getJson(String url) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-QW-Api-Key", apiKey);
        headers.set("Accept-Encoding", "gzip");
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<byte[]> resp = restTemplate.exchange(url, HttpMethod.GET, entity, byte[].class);
        byte[] body = resp.getBody();

        // 和风天气返回 gzip 压缩，RestTemplate 不会自动解压，这里手动解压
        if (body != null) {
            String contentEncoding = resp.getHeaders().getFirst("Content-Encoding");
            if ("gzip".equalsIgnoreCase(contentEncoding) || isGzip(body)) {
                body = gunzip(body);
            }
        }
        return objectMapper.readTree(body);
    }

    private boolean isGzip(byte[] data) {
        return data != null && data.length >= 2
                && (data[0] & 0xff) == 0x1f && (data[1] & 0xff) == 0x8b;
    }

    private byte[] gunzip(byte[] data) throws Exception {
        try (GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(data));
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = gis.read(buf)) != -1) {
                bos.write(buf, 0, n);
            }
            return bos.toByteArray();
        }
    }

    private String windDirectionText(String compass) {
        Map<String, String> map = new HashMap<>();
        map.put("n", "北风");
        map.put("nne", "北东北风");
        map.put("ne", "东北风");
        map.put("ene", "东东北风");
        map.put("e", "东风");
        map.put("ese", "东东南风");
        map.put("se", "东南风");
        map.put("sse", "南东南风");
        map.put("s", "南风");
        map.put("ssw", "南西南风");
        map.put("sw", "西南风");
        map.put("wsw", "西西南风");
        map.put("w", "西风");
        map.put("wnw", "西西北风");
        map.put("nw", "西北风");
        map.put("nnw", "北西北风");
        map.put("vrb", "风向不定");
        map.put("none", "无风");
        return map.getOrDefault(compass, compass);
    }
}
