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
}
