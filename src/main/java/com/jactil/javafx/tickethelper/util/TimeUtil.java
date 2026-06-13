package com.jactil.javafx.tickethelper.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 时间工具类
 * 提供服务器时间同步、格式化等常用时间操作
 * 当前为占位实现，后续补充 NTP 时间同步逻辑
 */
public class TimeUtil {

    private static final Logger logger = LoggerFactory.getLogger(TimeUtil.class);

    private static final DateTimeFormatter DATETIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 服务器与本地时间偏移量（毫秒），初始为 0 */
    private static long serverTimeOffset = 0L;

    /**
     * 获取当前校准后的时间
     *
     * @return 校准后的 LocalDateTime
     */
    public static LocalDateTime now() {
        return LocalDateTime.now().plusNanos(serverTimeOffset * 1_000_000);
    }

    /**
     * 获取当前时间字符串（yyyy-MM-dd HH:mm:ss）
     */
    public static String nowString() {
        return now().format(DATETIME_FORMATTER);
    }

    /**
     * 获取当前日期字符串（yyyy-MM-dd）
     */
    public static String todayString() {
        return now().format(DATE_FORMATTER);
    }

    /**
     * 同步服务器时间（占位方法）
     * 后续通过 NTP 或 12306 接口获取服务器时间并计算偏移
     */
    public static void syncServerTime() {
        logger.info("服务器时间同步（占位）：偏移量 = {} ms", serverTimeOffset);
        // TODO: 实现真实的 NTP 或 12306 服务器时间同步
    }

    /**
     * 设置服务器时间偏移量
     *
     * @param offsetMillis 偏移量（毫秒）
     */
    public static void setServerTimeOffset(long offsetMillis) {
        serverTimeOffset = offsetMillis;
        logger.info("服务器时间偏移量已更新：{} ms", offsetMillis);
    }
}
