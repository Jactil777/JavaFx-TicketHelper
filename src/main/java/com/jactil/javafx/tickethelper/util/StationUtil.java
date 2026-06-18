package com.jactil.javafx.tickethelper.util;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 12306 车站数据工具类
 * 从 12306 公开接口获取全国车站数据，支持按站名/拼音模糊搜索
 */
public class StationUtil {

    private static final Logger logger = LoggerFactory.getLogger(StationUtil.class);

    private static final String STATION_DATA_URL = "https://kyfw.12306.cn/otn/resources/js/framework/station_name.js";

    /** 车站数据（懒加载） */
    private static volatile List<Station> stations;

    /** 车站实体 */
    public static class Station {
        private final String code;       // 三字码，如 VAP
        private final String name;       // 中文名，如 北京北
        private final String pinyin;     // 拼音，如 beijingbei
        private final String shortCode;  // 简码，如 bjb
        private final String cityCode;   // 城市代码（如 0357=北京，1717=重庆）
        private final String cityName;   // 城市名（如 北京、重庆）

        public Station(String code, String name, String pinyin, String shortCode, String cityCode, String cityName) {
            this.code = code;
            this.name = name;
            this.pinyin = pinyin;
            this.shortCode = shortCode;
            this.cityCode = cityCode;
            this.cityName = cityName;
        }

        public String getCode() { return code; }
        public String getName() { return name; }
        public String getPinyin() { return pinyin; }
        public String getShortCode() { return shortCode; }
        public String getCityCode() { return cityCode; }
        public String getCityName() { return cityName; }

        @Override
        public String toString() { return name; }
    }

    /**
     * 获取全部车站列表（懒加载，线程安全）
     */
    public static List<Station> getAllStations() {
        if (stations == null) {
            synchronized (StationUtil.class) {
                if (stations == null) {
                    stations = loadStations();
                }
            }
        }
        return stations;
    }

    /**
     * 获取与指定车站同城的所有车站（基于 cityCode 精确匹配）
     */
    public static List<Station> getStationsInSameCity(String stationName) {
        if (stationName == null || stationName.trim().isEmpty()) {
            return Collections.emptyList();
        }
        // 先找到目标车站的 cityCode
        String targetCityCode = findStationCityCode(stationName.trim());
        if (targetCityCode == null || targetCityCode.isEmpty()) {
            return Collections.emptyList();
        }
        return getAllStations().stream()
                .filter(s -> targetCityCode.equals(s.getCityCode()))
                .collect(Collectors.toList());
    }

    /**
     * 根据站名查找其 cityCode
     */
    public static String findStationCityCode(String stationName) {
        return getAllStations().stream()
                .filter(s -> s.getName().equals(stationName))
                .findFirst()
                .map(Station::getCityCode)
                .orElse("");
    }

    /**
     * 按关键字模糊搜索车站（匹配中文名、拼音、简码）
     * 最多返回 20 条结果
     */
    public static List<Station> search(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String kw = keyword.trim().toLowerCase();
        return getAllStations().stream()
                .filter(s -> s.getName().contains(keyword.trim())
                        || s.getPinyin().toLowerCase().contains(kw)
                        || s.getShortCode().toLowerCase().contains(kw))
                .limit(20)
                .collect(Collectors.toList());
    }

    /**
     * 按站名精确查找车站代码（三字码）
     */
    public static String findStationCode(String stationName) {
        if (stationName == null || stationName.trim().isEmpty()) {
            return "";
        }
        return getAllStations().stream()
                .filter(s -> s.getName().equals(stationName.trim()))
                .findFirst()
                .map(Station::getCode)
                .orElse("");
    }

    /**
     * 加载车站数据：优先从网络获取，失败则使用内置数据
     */
    private static List<Station> loadStations() {
        // 1. 尝试从 12306 接口获取
        try {
            String data = fetchFromNetwork();
            if (data != null && !data.isEmpty()) {
                List<Station> list = parse(data);
                logger.info("从网络加载车站数据成功，共 {} 个车站", list.size());
                return list;
            }
        } catch (Exception e) {
            logger.warn("从网络加载车站数据失败: {}", e.getMessage());
        }

        // 2. 降级：使用内置资源文件
        try {
            String data = fetchFromResource();
            if (data != null && !data.isEmpty()) {
                List<Station> list = parse(data);
                logger.info("从内置资源加载车站数据成功，共 {} 个车站", list.size());
                return list;
            }
        } catch (Exception e) {
            logger.error("从内置资源加载车站数据失败: {}", e.getMessage(), e);
        }

        logger.error("车站数据加载失败，所有来源均不可用");
        return Collections.emptyList();
    }

    /**
     * 从 12306 网络接口获取车站数据
     */
    private static String fetchFromNetwork() {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();

        Request request = new Request.Builder()
                .url(STATION_DATA_URL)
                .header("User-Agent", "Mozilla/5.0")
                .header("Referer", "https://kyfw.12306.cn/")
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                return response.body().string();
            }
        } catch (Exception e) {
            logger.debug("网络获取车站数据异常: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 从内置资源文件获取车站数据
     */
    private static String fetchFromResource() {
        try (InputStream is = StationUtil.class.getResourceAsStream("/data/station_name.js")) {
            if (is == null) {
                logger.info("内置车站数据文件不存在: /data/station_name.js");
                return null;
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        } catch (Exception e) {
            logger.debug("读取内置车站数据异常: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 解析车站数据字符串
     * 格式: @bjb|北京北|VAP|beijingbei|bjb|0|0357|北京|||@bjd|北京东|BOP|beijingdong|bjd|1|0357|北京|||
     * 字段: [简码|站名|代码|拼音|简码2|序号|cityCode|cityName]
     */
    static List<Station> parse(String raw) {
        List<Station> list = new ArrayList<>();
        if (raw == null || raw.isEmpty()) {
            return list;
        }

        // 提取单引号内的数据
        int start = raw.indexOf('\'');
        int end = raw.lastIndexOf('\'');
        if (start < 0 || end <= start) {
            // 尝试双引号
            start = raw.indexOf('"');
            end = raw.lastIndexOf('"');
        }
        if (start < 0 || end <= start) {
            logger.warn("无法解析车站数据格式");
            return list;
        }

        String data = raw.substring(start + 1, end);
        // 按 @ 分割每个车站
        String[] items = data.split("@");
        for (String item : items) {
            if (item.trim().isEmpty()) continue;
            String[] parts = item.split("\\|");
            if (parts.length >= 8) {
                // parts: [shortCode, name, code, pinyin, shortCode2, index, cityCode, cityName]
                list.add(new Station(parts[2], parts[1], parts[3], parts[0], parts[6], parts[7]));
            } else if (parts.length >= 5) {
                // 兼容旧格式：没有 cityCode/cityName 字段
                list.add(new Station(parts[2], parts[1], parts[3], parts[0], parts[0], ""));
            }
        }
        return list;
    }
}
