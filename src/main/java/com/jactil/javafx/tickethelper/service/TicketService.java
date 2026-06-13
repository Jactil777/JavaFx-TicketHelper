package com.jactil.javafx.tickethelper.service;

/**
 * 抢票服务接口
 * 负责余票查询、车次筛选、提交订单等核心抢票逻辑
 * 当前为占位接口，待后续实现
 */
public interface TicketService {

    /**
     * 查询余票
     *
     * @param fromStation 出发站
     * @param toStation   到达站
     * @param trainDate   乘车日期（yyyy-MM-dd）
     */
    void queryTickets(String fromStation, String toStation, String trainDate);

    /**
     * 提交抢票任务
     *
     * @param trainNo    车次号
     * @param seatType   席别代码
     * @param passenger  乘客信息
     */
    void submitTicketTask(String trainNo, String seatType, String passenger);

    /**
     * 停止所有抢票任务
     */
    void stopAllTasks();
}
