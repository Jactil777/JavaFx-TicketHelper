package com.jactil.javafx.tickethelper.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jactil.javafx.tickethelper.service.TicketService;
import com.jactil.javafx.tickethelper.util.HttpClientUtil;
import com.jactil.javafx.tickethelper.util.StationUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 12306 车票查询服务实现
 */
public class TicketServiceImpl implements TicketService {

    private static final Logger logger = LoggerFactory.getLogger(TicketServiceImpl.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final String QUERY_URL = "https://kyfw.12306.cn/otn/leftTicket/queryI";

    private List<QueryCallback> callbacks = new ArrayList<>();

    /** 查询结果回调接口 */
    public interface QueryCallback {
        void onResult(List<Map<String, String>> results);
        void onError(String message);
    }

    public void addCallback(QueryCallback callback) {
        callbacks.add(callback);
    }

    @Override
    public void queryTickets(String fromStation, String toStation, String trainDate) {
        try {
            // 站名转三字码
            String fromCode = StationUtil.findStationCode(fromStation);
            String toCode = StationUtil.findStationCode(toStation);

            if (fromCode.isEmpty() || toCode.isEmpty()) {
                String msg = "无法识别车站代码：出发站=" + fromStation + "(" + fromCode + "), 目的站=" + toStation + "(" + toCode + ")";
                logger.warn(msg);
                notifyError(msg);
                return;
            }

            logger.info("查询车票：{}({}) -> {}({}), 日期={}", fromStation, fromCode, toStation, toCode, trainDate);

            String url = QUERY_URL
                    + "?leftTicketDTO.train_date=" + URLEncoder.encode(trainDate, StandardCharsets.UTF_8)
                    + "&leftTicketDTO.from_station=" + fromCode
                    + "&leftTicketDTO.to_station=" + toCode
                    + "&purpose_codes=ADULT";

            String response = HttpClientUtil.get(url);
            logger.debug("查询响应长度：{}", response != null ? response.length() : 0);

            if (response == null || response.trim().startsWith("<")) {
                notifyError("查询失败：返回非JSON内容，可能未登录或Session过期");
                return;
            }

            JsonNode root = objectMapper.readTree(response);
            boolean status = root.path("status").asBoolean(false);
            if (!status) {
                String msg = root.path("messages").asText("查询失败");
                notifyError(msg);
                return;
            }

            JsonNode data = root.path("data");
            JsonNode resultMap = data.path("map");
            JsonNode resultArray = data.path("result");

            // 解析站点映射
            Map<String, String> stationMap = new HashMap<>();
            resultMap.fields().forEachRemaining(entry -> stationMap.put(entry.getKey(), entry.getValue().asText()));

            // 解析车次列表
            List<Map<String, String>> trains = new ArrayList<>();
            if (resultArray.isArray()) {
                for (JsonNode item : resultArray) {
                    String raw = item.asText();
                    Map<String, String> train = parseTrainItem(raw, stationMap);
                    if (train != null) {
                        trains.add(train);
                    }
                }
            }

            logger.info("查询到 {} 个车次", trains.size());
            notifyResult(trains);

        } catch (Exception e) {
            logger.error("查询车票异常", e);
            notifyError("查询异常：" + e.getMessage());
        }
    }

    /**
     * 解析单条车次数据（管道符分隔）
     * 字段索引参考 12306 前端 leftTicket 数据格式
     */
    private Map<String, String> parseTrainItem(String raw, Map<String, String> stationMap) {
        try {
            String[] fields = raw.split("\\|");
            if (fields.length < 40) {
                return null;
            }

            Map<String, String> train = new HashMap<>();

            String trainNo = fields[3];                // 车次
            String fromCode = fields[6];               // 出发站代码
            String toCode = fields[7];                 // 到达站代码
            String departTime = fields[8];             // 出发时间
            String arriveTime = fields[9];             // 到达时间
            String duration = fields[10];              // 历时
            String date = fields[13];                  // 日期

            // 站点名称（优先用 map，其次用代码）
            String fromName = stationMap.getOrDefault(fromCode, fromCode);
            String toName = stationMap.getOrDefault(toCode, toCode);

            train.put("车次", trainNo);
            // 出发地 = 站名 + 出发时间
            train.put("出发地", fromName + " " + departTime);
            // 目的地 = 站名 + 到达时间
            train.put("目的地", toName + " " + arriveTime);
            train.put("历时", duration);
            train.put("日期", formatDate(date));

            // 席别余票（根据12306实际响应格式）
            // 索引: 24=无座, 25=商务/特等, 26=软座, 27=硬座, 28=硬卧, 29=软卧, 30=高级软卧
            //       31=一等座, 32=二等座, 33=优选一等座, 34=其他, 37=原始数据(跳过)
            train.put("商务/特等", getSeatTicket(fields, 25));
            train.put("优选一等座", getSeatTicket(fields, 33));
            train.put("一等座", getSeatTicket(fields, 31));
            train.put("二等座", getSeatTicket(fields, 32));
            train.put("高级软卧", getSeatTicket(fields, 30));
            train.put("软卧", getSeatTicket(fields, 29));
            train.put("硬卧", getSeatTicket(fields, 28));
            train.put("软座", getSeatTicket(fields, 26));
            train.put("硬座", getSeatTicket(fields, 27));
            train.put("无座", getSeatTicket(fields, 24));
            // "其他"列通常为空，显示--
            train.put("其他", "--");

            // 备注：显示"预订"按钮文字
            train.put("备注", "预订");

            return train;
        } catch (Exception e) {
            logger.debug("解析车次数据失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 安全获取席别余票值
     */
    private String getSeatTicket(String[] fields, int index) {
        if (index < 0 || index >= fields.length) return "--";
        String val = fields[index].trim();
        if (val.isEmpty() || "null".equals(val)) return "--";
        if ("无".equals(val)) return "无";
        if ("有".equals(val)) return "有";
        // 数字表示余票数
        try {
            int num = Integer.parseInt(val);
            if (num > 0) return String.valueOf(num);
        } catch (NumberFormatException ignored) {
        }
        return val;
    }

    /**
     * 格式化日期：20260615 -> 06-15
     */
    private String formatDate(String dateStr) {
        if (dateStr == null || dateStr.length() < 8) return dateStr;
        return dateStr.substring(4, 6) + "-" + dateStr.substring(6, 8);
    }

    private void notifyResult(List<Map<String, String>> results) {
        for (QueryCallback cb : callbacks) {
            try {
                cb.onResult(results);
            } catch (Exception e) {
                logger.error("回调异常", e);
            }
        }
    }

    private void notifyError(String message) {
        for (QueryCallback cb : callbacks) {
            try {
                cb.onError(message);
            } catch (Exception e) {
                logger.error("回调异常", e);
            }
        }
    }

    @Override
    public void submitTicketTask(String trainNo, String seatType, String passenger) {
        logger.info("提交抢票任务：车次={}, 席别={}, 乘客={}", trainNo, seatType, passenger);
    }

    @Override
    public void stopAllTasks() {
        logger.info("停止所有抢票任务");
    }
}
