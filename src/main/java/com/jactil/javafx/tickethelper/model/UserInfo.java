package com.jactil.javafx.tickethelper.model;

/**
 * 12306 用户账号信息模型
 */
public class UserInfo {

    /** 用户名（手机号/邮箱） */
    private String username;

    /** 密码（仅内存中使用，不持久化） */
    private transient String password;

    /** 真实姓名 */
    private String realName;

    /** 12306 返回的用户 ID */
    private String userId;

    /** 是否已登录 */
    private boolean loggedIn;

    public UserInfo() {}

    public UserInfo(String username, String password) {
        this.username = username;
        this.password = password;
    }

    // ==================== Getters & Setters ====================

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRealName() {
        return realName;
    }

    public void setRealName(String realName) {
        this.realName = realName;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public boolean isLoggedIn() {
        return loggedIn;
    }

    public void setLoggedIn(boolean loggedIn) {
        this.loggedIn = loggedIn;
    }

    @Override
    public String toString() {
        return "UserInfo{username='" + username + "', realName='" + realName + "', loggedIn=" + loggedIn + "}";
    }
}
