package com.jactil.javafx.tickethelper.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 全局配置类（单例）
 * 管理应用的全局配置项，如代理、通知、语言等
 * 当前为占位实现，后续可扩展为从配置文件读取
 */
public class AppConfig {

    private static final Logger logger = LoggerFactory.getLogger(AppConfig.class);

    private static volatile AppConfig instance;

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

    private AppConfig() {
        logger.info("AppConfig 初始化完成，当前语言：{}", currentLanguage);
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

    // ==================== Getters & Setters ====================

    public String getCurrentLanguage() {
        return currentLanguage;
    }

    public void setCurrentLanguage(String currentLanguage) {
        this.currentLanguage = currentLanguage;
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
}
