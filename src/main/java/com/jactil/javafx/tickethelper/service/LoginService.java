package com.jactil.javafx.tickethelper.service;

import com.jactil.javafx.tickethelper.model.UserInfo;

/**
 * 12306 登录服务接口
 * 支持多步验证流程：检查验证方式 → 发送短信 → 输入验证码 → 登录
 */
public interface LoginService {

    /**
     * 登录结果
     */
    class LoginResult {
        /** 是否需要短信验证 */
        public boolean needSmsVerify;
        /** 登录成功后的用户信息（仅登录成功时有值） */
        public UserInfo userInfo;
        /** 错误信息 */
        public String errorMessage;
        /** 是否成功 */
        public boolean success;

        public static LoginResult needSms() {
            LoginResult r = new LoginResult();
            r.needSmsVerify = true;
            r.success = false;
            return r;
        }

        public static LoginResult success(UserInfo userInfo) {
            LoginResult r = new LoginResult();
            r.success = true;
            r.userInfo = userInfo;
            return r;
        }

        public static LoginResult fail(String message) {
            LoginResult r = new LoginResult();
            r.success = false;
            r.errorMessage = message;
            return r;
        }
    }

    /**
     * 第一步：检查登录验证方式
     *
     * @param username 用户名
     * @return 如果需要短信验证返回 needSmsVerify=true，否则返回 null
     */
    LoginResult checkVerifyMethod(String username);

    /**
     * 第二步：发送短信验证码
     *
     * @param username 用户名
     * @return 发送是否成功
     */
    LoginResult sendSmsCode(String username);

    /**
     * 第三步：使用短信验证码登录
     *
     * @param username 用户名
     * @param password 密码
     * @param smsCode  短信验证码
     * @return 登录结果
     */
    LoginResult loginWithSmsCode(String username, String password, String smsCode);

    /**
     * 退出登录
     */
    void logout();

    /**
     * 检查当前是否已登录
     */
    boolean isLoggedIn();

    /**
     * 扫码登录结果
     */
    class QrLoginResult {
        /** 二维码图片 Base64 数据（create-qr64 返回） */
        public String imageBase64;
        /** 二维码 uuid */
        public String uuid;
        /** 扫码登录成功后的用户信息 */
        public UserInfo userInfo;
        /** 错误/提示信息 */
        public String message;
        /** 是否成功 */
        public boolean success;
        /** 二维码是否过期 */
        public boolean expired;

        public static QrLoginResult generated(String imageBase64, String uuid) {
            QrLoginResult r = new QrLoginResult();
            r.imageBase64 = imageBase64;
            r.uuid = uuid;
            r.success = true;
            return r;
        }

        public static QrLoginResult waiting() {
            QrLoginResult r = new QrLoginResult();
            r.success = false;
            r.expired = false;
            r.message = "等待扫码...";
            return r;
        }

        public static QrLoginResult expired() {
            QrLoginResult r = new QrLoginResult();
            r.success = false;
            r.expired = true;
            r.message = "二维码已过期";
            return r;
        }

        public static QrLoginResult success(UserInfo userInfo) {
            QrLoginResult r = new QrLoginResult();
            r.success = true;
            r.userInfo = userInfo;
            return r;
        }

        public static QrLoginResult fail(String message) {
            QrLoginResult r = new QrLoginResult();
            r.success = false;
            r.expired = false;
            r.message = message;
            return r;
        }
    }

    /**
     * 创建扫码登录二维码
     *
     * @return 包含 Base64 图片数据和 uuid 的结果
     */
    QrLoginResult createQrCode();

    /**
     * 检查扫码状态
     *
     * @param uuid 二维码 uuid
     * @return 扫码状态结果
     */
    QrLoginResult checkQrStatus(String uuid);
}
