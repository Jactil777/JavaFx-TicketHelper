package com.jactil.javafx.tickethelper.util;

import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.*;
import java.io.IOException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * HTTP 请求工具类
 * 基于 OkHttp 封装，统一处理 12306 接口请求
 * 当前为占位实现，后续补充 Cookie 管理、重试机制等
 */
public class HttpClientUtil {

    private static final Logger logger = LoggerFactory.getLogger(HttpClientUtil.class);

    private static final OkHttpClient client;

    static {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true);

        // 配置信任所有证书，解决部分 JDK 环境下 12306 SSL 握手失败的问题
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(X509Certificate[] chain, String authType) {
                        }

                        @Override
                        public void checkServerTrusted(X509Certificate[] chain, String authType) {
                        }

                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }
                    }
            };
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new SecureRandom());
            builder.sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) trustAllCerts[0]);
            builder.hostnameVerifier((hostname, session) -> true);
        } catch (Exception e) {
            logger.error("SSL 配置失败，使用默认配置", e);
        }

        builder.cookieJar(new CookieJar() {
                    private final java.util.List<Cookie> cookieStore = new java.util.ArrayList<>();

                    @Override
                    public void saveFromResponse(HttpUrl url, java.util.List<Cookie> cookies) {
                        cookieStore.addAll(cookies);
                    }

                    @Override
                    public java.util.List<Cookie> loadForRequest(HttpUrl url) {
                        java.util.List<Cookie> validCookies = new java.util.ArrayList<>();
                        java.util.Iterator<Cookie> it = cookieStore.iterator();
                        while (it.hasNext()) {
                            Cookie cookie = it.next();
                            if (cookie.expiresAt() < System.currentTimeMillis()) {
                                it.remove();
                            } else if (cookie.matches(url)) {
                                validCookies.add(cookie);
                            }
                        }
                        return validCookies;
                    }
                });

        client = builder.build();
    }

    /**
     * 发送 GET 请求
     *
     * @param url 请求地址
     * @return 响应体字符串
     */
    public static String get(String url) throws IOException {
        logger.debug("GET 请求：{}", url);
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "application/json, text/javascript, */*; q=0.01")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", "https://kyfw.12306.cn/otn/resources/login.html")
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (response.body() != null) {
                return response.body().string();
            }
            return "";
        }
    }

    /**
     * 发送 POST 请求（表单形式）
     *
     * @param url    请求地址
     * @param params 表单参数
     * @return 响应体字符串
     */
    public static String post(String url, Map<String, String> params) throws IOException {
        logger.debug("POST 请求：{}", url);
        FormBody.Builder formBuilder = new FormBody.Builder();
        if (params != null) {
            params.forEach(formBuilder::add);
        }
        Request request = new Request.Builder()
                .url(url)
                .post(formBuilder.build())
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Origin", "https://kyfw.12306.cn")
                .header("Referer", "https://kyfw.12306.cn/otn/resources/login.html")
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (response.body() != null) {
                return response.body().string();
            }
            return "";
        }
    }

    /**
     * 获取 OkHttpClient 实例（用于自定义请求）
     */
    public static OkHttpClient getClient() {
        return client;
    }

    /**
     * 获取当前所有 Cookie（用于浏览器同步登录）
     */
    public static java.util.List<Cookie> getAllCookies() {
        // 通过反射访问内部 cookieJar
        try {
            java.lang.reflect.Field field = client.getClass().getDeclaredField("cookieJar");
            field.setAccessible(true);
            Object jar = field.get(client);
            if (jar instanceof CookieJar) {
                java.lang.reflect.Field storeField = jar.getClass().getDeclaredField("cookieStore");
                storeField.setAccessible(true);
                @SuppressWarnings("unchecked")
                java.util.List<Cookie> cookies = (java.util.List<Cookie>) storeField.get(jar);
                return new java.util.ArrayList<>(cookies);
            }
        } catch (Exception e) {
            logger.warn("获取 Cookies 失败", e);
        }
        return new java.util.ArrayList<>();
    }
}
