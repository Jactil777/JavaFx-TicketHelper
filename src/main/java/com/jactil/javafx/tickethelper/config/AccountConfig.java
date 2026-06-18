package com.jactil.javafx.tickethelper.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 分账户配置管理类
 * 每个12306账号拥有独立的配置目录：~/.tickethelper/accounts/{username}/
 * 存储：凭据、音量开关、车站搜索历史等
 */
public class AccountConfig {

    private static final Logger logger = LoggerFactory.getLogger(AccountConfig.class);

    /** 账户配置根目录 */
    private static final String ACCOUNTS_DIR = ".tickethelper" + File.separator + "accounts";

    /** 每个账户的配置缓存（username -> AccountConfig） */
    private static final ConcurrentHashMap<String, AccountConfig> cache = new ConcurrentHashMap<>();

    /** 当前账号用户名 */
    private final String username;

    /** 账户配置目录路径 */
    private final Path accountDir;

    // ---- 凭据 ----
    private String savedPassword;
    private boolean rememberPassword;

    // ---- 声音开关（默认开启，继承全局默认） ----
    private boolean soundEnabled = true;

    // ---- 车站搜索历史 ----
    private java.util.List<String> departureHistory = new java.util.ArrayList<>();
    private java.util.List<String> arrivalHistory = new java.util.ArrayList<>();
    private static final int MAX_HISTORY = 20;

    // ---- 同城车站筛选状态（JSON格式） ----
    private String cityFilter = "";

    private AccountConfig(String username) {
        this.username = username;
        this.accountDir = Paths.get(System.getProperty("user.home"), ACCOUNTS_DIR, sanitizeUsername(username));
        loadAll();
    }

    /**
     * 获取指定账号的配置实例（带缓存）
     */
    public static AccountConfig get(String username) {
        if (username == null || username.trim().isEmpty()) {
            return null;
        }
        return cache.computeIfAbsent(username.trim(), AccountConfig::new);
    }

    /**
     * 清除指定账号的配置缓存（退出登录时调用）
     */
    public static void evict(String username) {
        if (username != null) {
            cache.remove(username.trim());
        }
    }

    /** 清理用户名中的非法字符，用作目录名 */
    private static String sanitizeUsername(String username) {
        return username.replaceAll("[^a-zA-Z0-9_@.\\\\-]", "_");
    }

    // ==================== 加载/保存 ====================

    private void loadAll() {
        if (!Files.exists(accountDir)) {
            logger.info("账户配置目录不存在，使用默认配置：{}", accountDir);
            return;
        }
        loadCredentials();
        loadAccountConfig();
        loadStationHistory();
    }

    private void loadCredentials() {
        Path path = accountDir.resolve("credentials.properties");
        if (!Files.exists(path)) return;
        try (InputStream in = Files.newInputStream(path)) {
            Properties props = new Properties();
            props.load(in);
            this.savedPassword = props.getProperty("password", "");
            this.rememberPassword = Boolean.parseBoolean(props.getProperty("remember", "false"));
            logger.info("已加载账户凭据：{}", username);
        } catch (IOException e) {
            logger.warn("加载账户凭据失败：{}", username, e);
        }
    }

    private void saveCredentials() {
        try {
            Files.createDirectories(accountDir);
            Path path = accountDir.resolve("credentials.properties");
            Properties props = new Properties();
            if (rememberPassword && savedPassword != null) {
                props.setProperty("username", username);
                props.setProperty("password", savedPassword);
                props.setProperty("remember", "true");
            } else {
                props.setProperty("remember", "false");
            }
            try (OutputStream os = Files.newOutputStream(path)) {
                props.store(os, "Account Credentials: " + username);
            }
            logger.info("已保存账户凭据：{}", username);
        } catch (IOException e) {
            logger.error("保存账户凭据失败：{}", username, e);
        }
    }

    private void loadAccountConfig() {
        Path path = accountDir.resolve("config.properties");
        if (!Files.exists(path)) return;
        try (InputStream in = Files.newInputStream(path)) {
            Properties props = new Properties();
            props.load(in);
            this.soundEnabled = Boolean.parseBoolean(props.getProperty("soundEnabled", "true"));
            logger.info("已加载账户配置：{}", username);
        } catch (IOException e) {
            logger.warn("加载账户配置失败：{}", username, e);
        }
    }

    private void saveAccountConfig() {
        try {
            Files.createDirectories(accountDir);
            Path path = accountDir.resolve("config.properties");
            Properties props = new Properties();
            props.setProperty("soundEnabled", String.valueOf(this.soundEnabled));
            try (OutputStream os = Files.newOutputStream(path)) {
                props.store(os, "Account Config: " + username);
            }
        } catch (IOException e) {
            logger.error("保存账户配置失败：{}", username, e);
        }
    }

    private void loadStationHistory() {
        Path path = accountDir.resolve("station_history.properties");
        if (!Files.exists(path)) return;
        try (InputStream in = Files.newInputStream(path)) {
            Properties props = new Properties();
            props.load(in);
            departureHistory = parseHistoryList(props.getProperty("departure", ""));
            arrivalHistory = parseHistoryList(props.getProperty("arrival", ""));
            this.cityFilter = props.getProperty("cityFilter", "");
            logger.info("已加载车站历史：出发{}条，到达{}条", departureHistory.size(), arrivalHistory.size());
        } catch (IOException e) {
            logger.warn("加载车站历史失败：{}", username, e);
        }
    }

    private void saveStationHistory() {
        try {
            Files.createDirectories(accountDir);
            Path path = accountDir.resolve("station_history.properties");
            Properties props = new Properties();
            props.setProperty("departure", String.join(",", departureHistory));
            props.setProperty("arrival", String.join(",", arrivalHistory));
            if (this.cityFilter != null && !this.cityFilter.isEmpty()) {
                props.setProperty("cityFilter", this.cityFilter);
            }
            try (OutputStream os = Files.newOutputStream(path)) {
                props.store(os, "Station History: " + username);
            }
        } catch (IOException e) {
            logger.error("保存车站历史失败：{}", username, e);
        }
    }

    private java.util.List<String> parseHistoryList(String value) {
        java.util.List<String> list = new java.util.ArrayList<>();
        if (value == null || value.trim().isEmpty()) return list;
        for (String item : value.split(",")) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty() && !list.contains(trimmed)) {
                list.add(trimmed);
            }
        }
        return list;
    }

    // ==================== 凭据 ====================

    public String getSavedPassword() {
        return savedPassword;
    }

    public boolean isRememberPassword() {
        return rememberPassword;
    }

    public void saveCredentials(String password, boolean remember) {
        this.savedPassword = remember ? password : null;
        this.rememberPassword = remember;
        saveCredentials();
    }

    // ==================== 声音开关 ====================

    public boolean isSoundEnabled() {
        return soundEnabled;
    }

    public void setSoundEnabled(boolean enabled) {
        this.soundEnabled = enabled;
        saveAccountConfig();
    }

    // ==================== 车站搜索历史 ====================

    public java.util.List<String> getDepartureHistory() {
        return new java.util.ArrayList<>(departureHistory);
    }

    public java.util.List<String> getArrivalHistory() {
        return new java.util.ArrayList<>(arrivalHistory);
    }

    /**
     * 添加出发站历史记录（去重、置顶、限制数量）
     */
    public void addDepartureHistory(String stationName) {
        if (stationName == null || stationName.trim().isEmpty()) return;
        stationName = stationName.trim();
        departureHistory.remove(stationName);
        departureHistory.add(0, stationName);
        while (departureHistory.size() > MAX_HISTORY) {
            departureHistory.remove(departureHistory.size() - 1);
        }
        saveStationHistory();
    }

    /**
     * 添加到达站历史记录（去重、置顶、限制数量）
     */
    public void addArrivalHistory(String stationName) {
        if (stationName == null || stationName.trim().isEmpty()) return;
        stationName = stationName.trim();
        arrivalHistory.remove(stationName);
        arrivalHistory.add(0, stationName);
        while (arrivalHistory.size() > MAX_HISTORY) {
            arrivalHistory.remove(arrivalHistory.size() - 1);
        }
        saveStationHistory();
    }

    public String getUsername() {
        return username;
    }

    // ==================== 同城车站筛选 ====================

    public String getCityFilter() {
        return cityFilter;
    }

    public void setCityFilter(String cityFilter) {
        this.cityFilter = cityFilter != null ? cityFilter : "";
        saveStationHistory();
    }
}
