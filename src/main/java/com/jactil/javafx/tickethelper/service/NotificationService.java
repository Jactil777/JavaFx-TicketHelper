package com.jactil.javafx.tickethelper.service;

/**
 * 通知服务接口
 * 负责发送各类通知：系统弹窗、邮件、HTTP 推送、微信/QQ 通知
 * 当前为占位接口，待后续实现
 */
public interface NotificationService {

    /**
     * 发送系统弹窗通知
     *
     * @param title   通知标题
     * @param message 通知内容
     */
    void showSystemNotification(String title, String message);

    /**
     * 发送邮件通知
     *
     * @param to      收件人邮箱
     * @param subject 邮件主题
     * @param body    邮件正文
     */
    void sendEmail(String to, String subject, String body);

    /**
     * 发送 HTTP 推送通知
     *
     * @param webhookUrl 推送地址
     * @param message    推送内容
     */
    void sendHttpPush(String webhookUrl, String message);
}
