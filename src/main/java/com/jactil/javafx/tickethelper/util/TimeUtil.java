package com.jactil.javafx.tickethelper.util;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 时间工具类
 * 提供服务器时间同步、格式化等常用时间操作
 */
public class TimeUtil {

    private static final Logger logger = LoggerFactory.getLogger(TimeUtil.class);

    private static final DateTimeFormatter DATETIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private static final DateTimeFormatter DATETIME_FORMATTER_SHORT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 服务器与本地时间偏移量（毫秒），初始为 0 */
    private static long serverTimeOffset = 0L;

    /** 12306 服务器地址，用于获取服务器时间 */
    private static final String TIME_SYNC_URL = "https://kyfw.12306.cn/otn/";

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
     * 同步服务器时间
     * 通过 HTTP 请求 12306 服务器，从响应头 Date 字段获取服务器时间
     *
     * @return long[2]：[0]=服务器时间戳(ms), [1]=本地时间戳(ms)
     * @throws IOException 网络请求失败
     */
    public static long[] syncServerTime() throws IOException {
        long localTime = System.currentTimeMillis();

        OkHttpClient client = HttpClientUtil.getClient();
        Request request = new Request.Builder()
                .url(TIME_SYNC_URL)
                .head()
                .build();

        long serverTime;
        try (Response response = client.newCall(request).execute()) {
            String dateHeader = response.header("Date");
            if (dateHeader != null) {
                // 解析 HTTP Date 头，格式如：Sun, 14 Jun 2026 07:18:50 GMT
                java.util.Date httpDate = new java.util.Date(dateHeader);
                serverTime = httpDate.getTime();
            } else {
                serverTime = localTime;
                logger.warn("服务器未返回 Date 头，使用本地时间");
            }
        }

        // 计算偏移量
        serverTimeOffset = serverTime - localTime;
        logger.info("服务器时间偏移量：{} ms", serverTimeOffset);

        return new long[]{serverTime, localTime};
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
