package com.jactil.javafx.tickethelper.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * 全局配置类（单例）
 * 管理应用的全局配置项，如代理、通知、语言、声音开关等
 * 配置持久化到用户目录 ~/.tickethelper/config.properties
 */
public class AppConfig {

    private static final Logger logger = LoggerFactory.getLogger(AppConfig.class);

    private static volatile AppConfig instance;

    /** 当前应用版本号 */
    public static final String APP_VERSION = "1.0.0";

    /** GitHub 仓库地址 */
    public static final String GITHUB_REPO = "Jactil777/JavaFx-TicketHelper";

    /** GitHub Releases 页面 */
    public static final String GITHUB_RELEASES_URL = "https://github.com/" + GITHUB_REPO + "/releases";

    /** GitHub API - 获取最新 Release */
    public static final String GITHUB_API_LATEST_RELEASE = "https://api.github.com/repos/" + GITHUB_REPO + "/releases/latest";

    /** GitHub API - 获取所有 Release */
    public static final String GITHUB_API_RELEASES = "https://api.github.com/repos/" + GITHUB_REPO + "/releases";

    /** 配置文件路径：用户主目录/.tickethelper/config.properties */
    private static final String CONFIG_DIR = ".tickethelper";
    private static final String CONFIG_FILE = "config.properties";

    /** 当前语言：zh_CN / en_US */
    private String currentLanguage = "zh_CN";

    /** 12306 基础 URL */
    private String baseUrl = "https://kyfw.12306.cn";

    /** 是否启用代理 */
    private boolean proxyEnabled = false;

    /** 代理地址 */
    private String proxyHost = "";

    /** 代理端口 */
    private int proxyPort = 0;

    /** 声音总开关（默认开启） */
    private boolean soundEnabled = true;

    private AppConfig() {
        loadConfig();
        logger.info("AppConfig 初始化完成，当前语言：{}，声音开关：{}", currentLanguage, soundEnabled ? "开启" : "关闭");
    }

    /**
     * 获取单例实例
     */
    public static AppConfig getInstance() {
        if (instance == null) {
            synchronized (AppConfig.class) {
                if (instance == null) {
                    instance = new AppConfig();
                }
            }
        }
        return instance;
    }

    // ==================== 配置持久化 ====================

    private Path getConfigPath() {
        return Paths.get(System.getProperty("user.home"), CONFIG_DIR, CONFIG_FILE);
    }

    private void loadConfig() {
        Path path = getConfigPath();
        if (!Files.exists(path)) {
            logger.info("配置文件不存在，使用默认配置");
            return;
        }
        try (InputStream in = Files.newInputStream(path)) {
            Properties props = new Properties();
            props.load(in);
            this.currentLanguage = props.getProperty("language", "zh_CN");
            this.soundEnabled = Boolean.parseBoolean(props.getProperty("soundEnabled", "true"));
            logger.info("已加载配置文件：{}", path);
        } catch (IOException e) {
            logger.warn("加载配置文件失败，使用默认配置", e);
        }
    }

    private void saveConfig() {
        Path path = getConfigPath();
        try {
            Files.createDirectories(path.getParent());
            Properties props = new Properties();
            props.setProperty("language", this.currentLanguage);
            props.setProperty("soundEnabled", String.valueOf(this.soundEnabled));
            try (OutputStream out = Files.newOutputStream(path)) {
                props.store(out, "JavaFx-TicketHelper Configuration");
            }
            logger.debug("配置已保存到：{}", path);
        } catch (IOException e) {
            logger.error("保存配置文件失败", e);
        }
    }

    // ==================== Getters & Setters ====================

    public String getCurrentLanguage() {
        return currentLanguage;
    }

    public void setCurrentLanguage(String currentLanguage) {
        this.currentLanguage = currentLanguage;
        saveConfig();
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public boolean isProxyEnabled() {
        return proxyEnabled;
    }

    public void setProxyEnabled(boolean proxyEnabled) {
        this.proxyEnabled = proxyEnabled;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    public int getProxyPort() {
        return proxyPort;
    }

    public void setProxyPort(int proxyPort) {
        this.proxyPort = proxyPort;
    }

    public boolean isSoundEnabled() {
        return soundEnabled;
    }

    public void setSoundEnabled(boolean soundEnabled) {
        this.soundEnabled = soundEnabled;
        saveConfig();
    }
}
