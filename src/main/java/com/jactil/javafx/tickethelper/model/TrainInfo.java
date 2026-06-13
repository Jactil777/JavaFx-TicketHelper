package com.jactil.javafx.tickethelper.model;

/**
 * 车次信息模型
 * 对应 12306 余票查询接口返回的单条车次数据
 */
public class TrainInfo {

    /** 车次号，如 G1、D302 */
    private String trainNo;

    /** 出发站 */
    private String fromStation;

    /** 到达站 */
    private String toStation;

    /** 出发时间（HH:mm） */
    private String departTime;

    /** 到达时间（HH:mm） */
    private String arriveTime;

    /** 历时（HH:mm） */
    private String duration;

    /** 商务座余票 */
    private String businessSeat;

    /** 一等座余票 */
    private String firstClassSeat;

    /** 二等座余票 */
    private String secondClassSeat;

    /** 硬卧余票 */
    private String hardSleeper;

    /** 软卧余票 */
    private String softSleeper;

    /** 无座余票 */
    private String noSeat;

    /** 是否可购买 */
    private boolean canBuy;

    public TrainInfo() {}

    // ==================== Getters & Setters ====================

    public String getTrainNo() {
        return trainNo;
    }

    public void setTrainNo(String trainNo) {
        this.trainNo = trainNo;
    }

    public String getFromStation() {
        return fromStation;
    }

    public void setFromStation(String fromStation) {
        this.fromStation = fromStation;
    }

    public String getToStation() {
        return toStation;
    }

    public void setToStation(String toStation) {
        this.toStation = toStation;
    }

    public String getDepartTime() {
        return departTime;
    }

    public void setDepartTime(String departTime) {
        this.departTime = departTime;
    }

    public String getArriveTime() {
        return arriveTime;
    }

    public void setArriveTime(String arriveTime) {
        this.arriveTime = arriveTime;
    }

    public String getDuration() {
        return duration;
    }

    public void setDuration(String duration) {
        this.duration = duration;
    }

    public String getBusinessSeat() {
        return businessSeat;
    }

    public void setBusinessSeat(String businessSeat) {
        this.businessSeat = businessSeat;
    }

    public String getFirstClassSeat() {
        return firstClassSeat;
    }

    public void setFirstClassSeat(String firstClassSeat) {
        this.firstClassSeat = firstClassSeat;
    }

    public String getSecondClassSeat() {
        return secondClassSeat;
    }

    public void setSecondClassSeat(String secondClassSeat) {
        this.secondClassSeat = secondClassSeat;
    }

    public String getHardSleeper() {
        return hardSleeper;
    }

    public void setHardSleeper(String hardSleeper) {
        this.hardSleeper = hardSleeper;
    }

    public String getSoftSleeper() {
        return softSleeper;
    }

    public void setSoftSleeper(String softSleeper) {
        this.softSleeper = softSleeper;
    }

    public String getNoSeat() {
        return noSeat;
    }

    public void setNoSeat(String noSeat) {
        this.noSeat = noSeat;
    }

    public boolean isCanBuy() {
        return canBuy;
    }

    public void setCanBuy(boolean canBuy) {
        this.canBuy = canBuy;
    }

    @Override
    public String toString() {
        return "TrainInfo{trainNo='" + trainNo + "', " + fromStation + "->" + toStation +
                ", " + departTime + "-" + arriveTime + "}";
    }
}
