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
    public void queryTickets(String fromStation, String toStation, String trainDate, String purposeCodes) {
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

            // 默认成人票
            if (purposeCodes == null || purposeCodes.isEmpty()) {
                purposeCodes = "ADULT";
            }

            logger.info("查询车票：{}({}) -> {}({}), 日期={}, 类型={}", fromStation, fromCode, toStation, toCode, trainDate, purposeCodes);

            String url = QUERY_URL
                    + "?leftTicketDTO.train_date=" + URLEncoder.encode(trainDate, StandardCharsets.UTF_8)
                    + "&leftTicketDTO.from_station=" + fromCode
                    + "&leftTicketDTO.to_station=" + toCode
                    + "&purpose_codes=" + purposeCodes;

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
            boolean firstTrain = true;
            if (resultArray.isArray()) {
                for (JsonNode item : resultArray) {
                    String raw = item.asText();
                    if (firstTrain) {
                        String[] debugFields = raw.split("\\|");
                        logger.debug("=== First train raw fields (total={}) ===", debugFields.length);
                        for (int i = 0; i < debugFields.length; i++) {
                            logger.debug("  [{}] = '{}'", i, debugFields[i]);
                        }
                        firstTrain = false;
                    }
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
            // 保存原始数据（用于票价查询）
            train.put("_raw", raw);
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

            // 解析票价（字段38：席别代码+价格编码）
            parseTicketPrices(fields, trainNo, train);

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

    /** 席别单字符代码 -> 表格列名 映射（不含冲突项"9"） */
    private static final java.util.Map<String, String> PRICE_SEAT_NAME_MAP = new java.util.HashMap<>();
    static {
        // 12306票价字段中使用的单字符席别代码（大写+小写）
        PRICE_SEAT_NAME_MAP.put("M", "商务/特等");   PRICE_SEAT_NAME_MAP.put("m", "商务/特等");
        PRICE_SEAT_NAME_MAP.put("P", "优选一等座");   PRICE_SEAT_NAME_MAP.put("p", "优选一等座");
        PRICE_SEAT_NAME_MAP.put("O", "一等座");       PRICE_SEAT_NAME_MAP.put("o", "一等座");
        PRICE_SEAT_NAME_MAP.put("W", "二等座");       PRICE_SEAT_NAME_MAP.put("w", "二等座");
        PRICE_SEAT_NAME_MAP.put("1", "高级软卧");     PRICE_SEAT_NAME_MAP.put("4", "软卧");
        PRICE_SEAT_NAME_MAP.put("3", "硬卧");         PRICE_SEAT_NAME_MAP.put("2", "软座");
        PRICE_SEAT_NAME_MAP.put("6", "无座");
        // 注意："9" 在高铁(G/D/C)中表示商务座，在普速(K/T/Z)中表示硬座，需特殊处理
    }

    /**
     * 从原始响应中动态查找票价字段并解析
     * 票价字段格式：[1位前缀][5位价格(分)][席别代码] 重复组合
     * 例如：9023900004M008300021O → 前缀9, 02390分=M座, 08300分=O座
     * 字段位置不固定，通过模式特征动态定位
     */
    private void parseTicketPrices(String[] fields, String trainNo, Map<String, String> train) {
        // 判断车次类型：G/D/C开头为高铁，"9"表示商务座；其他为普速，"9"表示硬座
        boolean isHighSpeed = trainNo != null && (trainNo.startsWith("G") || trainNo.startsWith("D") || trainNo.startsWith("C"));

        // 动态查找票价字段：在 fields[30]~fields[45] 范围内搜索
        // 票价字段特征：长度>=18且包含多个 [5位数字][A-Za-z] 模式
        String priceField = null;
        java.util.regex.Pattern priceUnitPattern = java.util.regex.Pattern.compile("\\d{5}[A-Za-z]");
        for (int i = 30; i < Math.min(fields.length, 46); i++) {
            String f = fields[i].trim();
            if (f.length() >= 18) {
                java.util.regex.Matcher m = priceUnitPattern.matcher(f);
                int count = 0;
                while (m.find()) count++;
                if (count >= 2) {
                    priceField = f;
                    logger.debug("[{}] price field found at index [{}]: '{}'", trainNo, i, priceField);
                    break;
                }
            }
        }
        if (priceField == null || priceField.isEmpty() || "null".equals(priceField)) return;

        Map<String, String> priceMap = new HashMap<>();

        // 票价字段格式分析：
        // C7049: M009000021O007200021O007203155
        // G2907: 9019950003M009950000O007450000O007453000
        // 格式：[可选数字前缀][1位席别字母][5位价格(分)][不定长尾部数字] 重复
        // 策略：找到第一个字母，然后提取其后5位数字作为价格，跳到下一个字母继续
        
        // 找到第一个字母的位置
        int firstLetterIdx = -1;
        for (int i = 0; i < priceField.length(); i++) {
            if (Character.isLetter(priceField.charAt(i))) {
                firstLetterIdx = i;
                break;
            }
        }
        if (firstLetterIdx < 0) return; // 没有字母，无法解析
        
        logger.debug("[{}] first letter at idx={}, starting parse from there", trainNo, firstLetterIdx);
        
        // 从第一个字母开始，每10字符一组：[1位席别][5位价格][4位尾部]
        // 价格单位是“角”（0.1元），需要除以10得到元
        int pos = firstLetterIdx;
        while (pos + 10 <= priceField.length()) {
            char seatCode = priceField.charAt(pos);
            String priceStr = priceField.substring(pos + 1, pos + 6);
            
            try {
                int priceInJiao = Integer.parseInt(priceStr); // 价格单位：角
                if (priceInJiao > 0) {
                    double priceInYuan = priceInJiao / 10.0; // 角转元
                    String formattedPrice = formatPrice(priceInYuan);
                    String colName;
                    if ("9".equalsIgnoreCase(String.valueOf(seatCode))) {
                        colName = isHighSpeed ? "商务/特等" : "硬座";
                    } else {
                        colName = PRICE_SEAT_NAME_MAP.get(String.valueOf(seatCode).toLowerCase());
                        if (colName == null) colName = PRICE_SEAT_NAME_MAP.get(String.valueOf(seatCode).toUpperCase());
                    }
                    if (colName != null && !priceMap.containsKey(colName)) {
                        priceMap.put(colName, "¥" + formattedPrice);
                        logger.debug("[{}] parsed: {}={} -> {}", trainNo, seatCode, priceStr, colName + "=" + formattedPrice);
                    }
                }
            } catch (NumberFormatException ignored) {}
            pos += 10; // 每组10字符
        }

        for (Map.Entry<String, String> entry : priceMap.entrySet()) {
            train.put("_price_" + entry.getKey(), entry.getValue());
        }
        if (!priceMap.isEmpty()) {
            logger.debug("[{}] prices found: {}", trainNo, priceMap);
        }

        // 无座票价规则：高铁/动车(G/D/C)无座票价 = 二等座票价，普速无座票价 = 硬座票价
        if (!priceMap.containsKey("无座")) {
            String noSeatPrice;
            if (isHighSpeed) {
                noSeatPrice = priceMap.get("二等座");
            } else {
                noSeatPrice = priceMap.get("硬座");
            }
            if (noSeatPrice != null) {
                priceMap.put("无座", noSeatPrice);
                train.put("_price_无座", noSeatPrice);
                logger.debug("[{}] 无座票价={} (={})", trainNo, noSeatPrice, isHighSpeed ? "二等座" : "硬座");
            }
        }
    }

    /**
     * 格式化价格：去掉小数点后多余的0
     */
    private String formatPrice(double price) {
        if (price == (long) price) {
            return String.valueOf((long) price);
        }
        // 保留1位小数（12306票价通常为x.5格式）
        String s = String.valueOf(price);
        if (s.endsWith("0")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
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
