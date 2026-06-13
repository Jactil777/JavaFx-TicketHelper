package com.jactil.javafx.tickethelper;

import com.jactil.javafx.tickethelper.model.UserInfo;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 主界面窗口（参考 Bypass 分流抢票设计）
 * 包含顶部工具栏 + 标签页（抢票/订单/候补）+ 查询区 + 结果表格 + 设置区 + 状态栏
 */
public class MainStage extends Stage {

    private static final Logger logger = LoggerFactory.getLogger(MainStage.class);

    private UserInfo currentUser;
    private Runnable onLogout;

    // 查询区域控件
    private TextField fromStationField;
    private TextField toStationField;
    private DatePicker datePicker;
    private ComboBox<String> departTimeCombo;
    private TextArea logArea;

    // 设置区域折叠状态
    private boolean settingsVisible = false;
    private VBox settingsArea;

    public MainStage() {
        this(null);
    }

    public MainStage(UserInfo userInfo) {
        this(userInfo, null);
    }

    public MainStage(UserInfo userInfo, Runnable onLogout) {
        this.currentUser = userInfo;
        this.onLogout = onLogout;
        setTitle("JavaFx-TicketHelper - 抢票助手");
        setMinWidth(1200);
        setMinHeight(700);

        BorderPane root = new BorderPane();

        // 顶部工具栏
        root.setTop(createTopBar());

        // 中间内容区
        root.setCenter(createTicketPage());

        // 底部状态栏
        root.setBottom(createStatusBar());

        Scene scene = new Scene(root, 1280, 800);
        scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
        setScene(scene);

        logger.info("主界面已初始化");
    }

    // ==================== 顶部工具栏 ====================

    private VBox createTopBar() {
        VBox topBox = new VBox(0);

        // 第一行：菜单栏
        HBox menuBar = new HBox(4);
        menuBar.setAlignment(Pos.CENTER_LEFT);
        menuBar.setPadding(new Insets(4, 8, 4, 8));
        menuBar.getStyleClass().add("top-menubar");

        String[][] menuItems = {
                {"\uD83D\uDD11 注销/登录", "logout"},
                {"\uD83C\uDF10 免登录打开12306官网", "open12306"},
                {"\uD83D\uDD52 同步服务器时间", "syncTime"},
                {"\uD83D\uDD04 检查更新", "checkUpdate"},
                {"\u2699 设置代理", "proxy"},
                {"\u2764 赞助与注册VIP", "vip"},
                {"\uD83C\uDFE0 分流抢票官网", "homepage"},
        };

        for (String[] item : menuItems) {
            Label label = new Label(item[0]);
            label.getStyleClass().add("menu-item-label");
            label.setOnMouseClicked(e -> logger.info("点击菜单：{}", item[1]));
            menuBar.getChildren().add(label);
        }

        // 公告
        Label noticeLabel = new Label("\uD83D\uDCE2 公告：如果公网IP被封了，可以尝试重启光猫解决！");
        noticeLabel.getStyleClass().add("notice-label");
        HBox.setHgrow(noticeLabel, Priority.ALWAYS);
        menuBar.getChildren().add(noticeLabel);

        // 第二行：标签页
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabPane.getStyleClass().add("main-tabpane");

        Tab ticketTab = new Tab("抢票页面");
        ticketTab.setContent(createTicketPage());

        Tab orderTab = new Tab("订单管理页面");
        orderTab.setContent(createPlaceholderPage("订单管理页面", "订单查询、支付提醒、自动刷单（待实现）"));

        Tab waitlistTab = new Tab("候补订单页面");
        waitlistTab.setContent(createPlaceholderPage("候补订单页面", "候补订单查询、自动提交候补（待实现）"));

        tabPane.getTabs().addAll(ticketTab, orderTab, waitlistTab);

        topBox.getChildren().addAll(menuBar, tabPane);
        return topBox;
    }

    // ==================== 抢票页面 ====================

    private VBox createTicketPage() {
        VBox page = new VBox(0);

        // 查询区域
        VBox queryArea = createQueryArea();
        page.getChildren().add(queryArea);

        // 结果表格
        VBox tableArea = createTableArea();
        VBox.setVgrow(tableArea, Priority.ALWAYS);
        page.getChildren().add(tableArea);

        // 设置区域（可折叠）
        settingsArea = createSettingsArea();
        settingsArea.setVisible(false);
        settingsArea.setManaged(false);
        page.getChildren().add(settingsArea);

        return page;
    }

    // ==================== 查询区域 ====================

    private VBox createQueryArea() {
        VBox queryBox = new VBox(6);
        queryBox.setPadding(new Insets(8, 10, 8, 10));
        queryBox.getStyleClass().add("query-area");

        // 第一行：出发/目的/日期/发车时间/操作/查询按钮
        HBox row1 = new HBox(8);
        row1.setAlignment(Pos.CENTER_LEFT);

        // 出发
        Label fromLabel = new Label("出发:");
        fromLabel.getStyleClass().add("query-label");
        fromStationField = new TextField();
        fromStationField.setPromptText("出发站");
        fromStationField.setPrefWidth(120);
        fromStationField.getStyleClass().add("query-input");

        Button swapBtn = new Button("\u21C4");
        swapBtn.setPrefWidth(36);
        swapBtn.getStyleClass().add("btn-swap");
        swapBtn.setOnAction(e -> {
            String tmp = fromStationField.getText();
            fromStationField.setText(toStationField.getText());
            toStationField.setText(tmp);
        });

        // 目的
        Label toLabel = new Label("目的:");
        toLabel.getStyleClass().add("query-label");
        toStationField = new TextField();
        toStationField.setPromptText("目的站");
        toStationField.setPrefWidth(120);
        toStationField.getStyleClass().add("query-input");

        // 日期
        Label dateLabel = new Label("日期:");
        dateLabel.getStyleClass().add("query-label");
        Button datePrevBtn = new Button("<");
        datePrevBtn.getStyleClass().add("btn-date-nav");
        datePicker = new DatePicker(LocalDate.now());
        datePicker.setPrefWidth(130);
        datePicker.getStyleClass().add("query-datepicker");
        Button dateNextBtn = new Button(">");
        dateNextBtn.getStyleClass().add("btn-date-nav");

        // 发车时间
        Label timeLabel = new Label("发车时间:");
        timeLabel.getStyleClass().add("query-label");
        departTimeCombo = new ComboBox<>();
        departTimeCombo.getItems().addAll("00:00-24:00", "00:00-06:00", "06:00-12:00", "12:00-18:00", "18:00-24:00");
        departTimeCombo.setValue("00:00-24:00");
        departTimeCombo.setPrefWidth(130);

        row1.getChildren().addAll(fromLabel, fromStationField, swapBtn, toLabel, toStationField,
                dateLabel, datePrevBtn, datePicker, dateNextBtn, timeLabel, departTimeCombo);

        // 操作区（右侧）
        VBox opBox = new VBox(4);
        opBox.setAlignment(Pos.CENTER_LEFT);

        Label opTitle = new Label("操作");
        opTitle.getStyleClass().add("query-label");

        HBox opRow1 = new HBox(12);
        CheckBox adultCheck = new CheckBox("成人");
        adultCheck.setSelected(true);
        CheckBox studentCheck = new CheckBox("学生");
        Label transferLink = new Label("查询中转换乘");
        transferLink.getStyleClass().add("link-blue");

        HBox opRow2 = new HBox(12);
        CheckBox onlyAvailableCheck = new CheckBox("只看有票的车次");
        Label showAllPriceLink = new Label("显示全部票价");
        showAllPriceLink.getStyleClass().add("link-blue");

        Button queryBtn = new Button("查询\n车票");
        queryBtn.getStyleClass().add("btn-query");
        queryBtn.setPrefWidth(60);
        queryBtn.setPrefHeight(50);

        opBox.getChildren().addAll(opTitle, opRow1, opRow2);
        opRow1.getChildren().addAll(adultCheck, studentCheck, transferLink);
        opRow2.getChildren().addAll(onlyAvailableCheck, showAllPriceLink);

        HBox row1Right = new HBox(10);
        row1Right.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(row1Right, Priority.ALWAYS);
        row1Right.getChildren().addAll(opBox, queryBtn);
        row1.getChildren().add(row1Right);

        // 第二行：模式/筛选/隐藏
        HBox row2 = new HBox(8);
        row2.setAlignment(Pos.CENTER_LEFT);

        // 模式
        Label modeLabel = new Label("模式:");
        modeLabel.getStyleClass().add("query-label");
        ToggleGroup modeGroup = new ToggleGroup();
        RadioButton singleTask = new RadioButton("单任务");
        singleTask.setToggleGroup(modeGroup);
        singleTask.setSelected(true);
        RadioButton multiTask = new RadioButton("多任务");
        multiTask.setToggleGroup(modeGroup);
        RadioButton multiStation = new RadioButton("多站");
        multiStation.setToggleGroup(modeGroup);
        Button addTaskBtn = new Button("加入新任务");
        addTaskBtn.getStyleClass().add("btn-small");

        row2.getChildren().addAll(modeLabel, singleTask, multiTask, multiStation, addTaskBtn);

        // 筛选
        Label filterLabel = new Label("筛选:");
        filterLabel.getStyleClass().add("query-label");
        CheckBox filterAll = new CheckBox("全部");
        filterAll.setSelected(true);
        CheckBox filterG = new CheckBox("高铁/城际");
        filterG.setSelected(true);
        CheckBox filterD = new CheckBox("动车");
        filterD.setSelected(true);
        CheckBox filterZ = new CheckBox("Z直达");
        filterZ.setSelected(true);
        CheckBox filterT = new CheckBox("T特快");
        filterT.setSelected(true);
        CheckBox filterK = new CheckBox("K快速");
        filterK.setSelected(true);
        CheckBox filterOther = new CheckBox("其他");
        filterOther.setSelected(true);

        row2.getChildren().addAll(filterLabel, filterAll, filterG, filterD, filterZ, filterT, filterK, filterOther);

        // 隐藏
        Label hideLabel = new Label("隐藏:");
        hideLabel.getStyleClass().add("query-label");
        CheckBox hideAll = new CheckBox("全选");
        hideAll.setSelected(true);
        CheckBox hideBusiness = new CheckBox("商务/特等");
        hideBusiness.setSelected(true);
        CheckBox hideFirstPlus = new CheckBox("优选一等座");
        hideFirstPlus.setSelected(true);
        CheckBox hideFirst = new CheckBox("一等座");
        hideFirst.setSelected(true);
        CheckBox hideSecond = new CheckBox("二等座");
        hideSecond.setSelected(true);
        CheckBox hideHighSoft = new CheckBox("高软");
        hideHighSoft.setSelected(true);
        CheckBox hideSoftSleeper = new CheckBox("软卧");
        hideSoftSleeper.setSelected(true);
        CheckBox hideHardSleeper = new CheckBox("硬卧");
        hideHardSleeper.setSelected(true);
        CheckBox hideSoftSeat = new CheckBox("软座");
        hideSoftSeat.setSelected(true);
        CheckBox hideHardSeat = new CheckBox("硬座");
        hideHardSeat.setSelected(true);
        CheckBox hideNoSeat = new CheckBox("无座");
        hideNoSeat.setSelected(true);
        CheckBox hideOtherSeat = new CheckBox("其他");
        hideOtherSeat.setSelected(true);

        row2.getChildren().addAll(hideLabel, hideAll, hideBusiness, hideFirstPlus, hideFirst, hideSecond,
                hideHighSoft, hideSoftSleeper, hideHardSleeper, hideSoftSeat, hideHardSeat, hideNoSeat, hideOtherSeat);

        // 第三行：显示/隐藏设置区域切换
        HBox row3 = new HBox();
        row3.setAlignment(Pos.CENTER);
        Label toggleSettings = new Label("\u2191显示设置区域\u2191");
        toggleSettings.getStyleClass().add("toggle-settings");
        toggleSettings.setOnMouseClicked(e -> toggleSettings());
        row3.getChildren().add(toggleSettings);

        queryBox.getChildren().addAll(row1, row2, row3);
        return queryBox;
    }

    // ==================== 结果表格 ====================

    private VBox createTableArea() {
        VBox tableBox = new VBox(0);
        tableBox.getStyleClass().add("table-area");

        TableView<TableRowData> tableView = new TableView<>();
        tableView.getStyleClass().add("result-table");

        String[] columns = {"车次", "出发地", "目的地", "历时", "商务/特等", "优选一等座",
                "一等座", "二等座", "高级软卧", "软卧", "硬卧", "软座", "硬座", "无座", "其他", "日期", "备注"};
        double[] widths = {70, 80, 80, 70, 80, 80, 70, 70, 70, 60, 60, 60, 60, 60, 60, 90, 100};

        for (int i = 0; i < columns.length; i++) {
            final String colName = columns[i];
            TableColumn<TableRowData, String> col = new TableColumn<>(colName);
            col.setCellValueFactory(data -> data.getValue().getProperty(colName));
            col.setPrefWidth(widths[i]);
            col.setResizable(true);
            tableView.getColumns().add(col);
        }

        tableView.setPlaceholder(new Label("暂无查询结果，请输入出发站和目的站后点击\"查询车票\""));
        VBox.setVgrow(tableView, Priority.ALWAYS);
        tableBox.getChildren().add(tableView);

        return tableBox;
    }

    // ==================== 设置区域 ====================

    private VBox createSettingsArea() {
        VBox settingsBox = new VBox(0);
        settingsBox.getStyleClass().add("settings-area");

        // 设置区域标题
        HBox titleBar = new HBox();
        titleBar.setAlignment(Pos.CENTER);
        titleBar.setPadding(new Insets(4, 0, 4, 0));
        Label toggleLabel = new Label("\u2193隐藏设置区域\u2193");
        toggleLabel.getStyleClass().add("toggle-settings");
        toggleLabel.setOnMouseClicked(e -> toggleSettings());
        titleBar.getChildren().add(toggleLabel);
        settingsBox.getChildren().add(titleBar);

        // 设置内容区：左侧设置 + 右侧输出
        HBox contentBox = new HBox(0);
        contentBox.setPadding(new Insets(4, 8, 8, 8));

        // 左侧：设置面板
        VBox leftPanel = new VBox(0);
        leftPanel.setPrefWidth(420);
        leftPanel.setMinWidth(380);

        // 设置 TabPane
        TabPane settingsTabPane = new TabPane();
        settingsTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        settingsTabPane.setPrefHeight(200);

        // 抢票设置
        Tab grabTab = new Tab("抢票设置");
        grabTab.setContent(createGrabSettings());
        // 其他设置tab（占位）
        Tab queryTab = new Tab("查询起售");
        queryTab.setContent(createPlaceholderContent("查询起售功能（待实现）"));
        Tab tencentTab = new Tab("腾讯通知");
        tencentTab.setContent(createPlaceholderContent("腾讯通知功能（待实现）"));
        Tab emailTab = new Tab("邮件通知");
        emailTab.setContent(createPlaceholderContent("邮件通知功能（待实现）"));
        Tab wechatTab = new Tab("微信通知");
        wechatTab.setContent(createPlaceholderContent("微信通知功能（待实现）"));
        Tab autoPayTab = new Tab("自动支付");
        autoPayTab.setContent(createPlaceholderContent("自动支付功能（待实现）"));
        Tab multiTaskTab = new Tab("多任务设置");
        multiTaskTab.setContent(createPlaceholderContent("多任务设置功能（待实现）"));

        settingsTabPane.getTabs().addAll(grabTab, queryTab, tencentTab, emailTab, wechatTab, autoPayTab, multiTaskTab);
        leftPanel.getChildren().add(settingsTabPane);

        // 右侧：输出区 + 通用设置
        VBox rightPanel = new VBox(0);
        HBox.setHgrow(rightPanel, Priority.ALWAYS);

        // 输出区标题
        HBox logTitle = new HBox();
        logTitle.setAlignment(Pos.CENTER_LEFT);
        logTitle.setPadding(new Insets(4, 8, 2, 8));
        Label logTitleLabel = new Label("输出区");
        logTitleLabel.getStyleClass().add("section-title");
        HBox.setHgrow(logTitleLabel, Priority.ALWAYS);
        Label findLogLink = new Label("查找日志");
        findLogLink.getStyleClass().add("link-blue");
        logTitle.getChildren().addAll(logTitleLabel, findLogLink);

        // 日志输出区
        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefHeight(160);
        logArea.getStyleClass().add("log-area");
        logArea.appendText("等待查询...\n");

        // 通用设置 / 其他设置
        TabPane rightTabPane = new TabPane();
        rightTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        rightTabPane.setPrefHeight(120);

        Tab commonTab = new Tab("通用设置");
        commonTab.setContent(createCommonSettings());
        Tab otherTab = new Tab("其他设置");
        otherTab.setContent(createPlaceholderContent("其他设置（待实现）"));

        rightTabPane.getTabs().addAll(commonTab, otherTab);

        rightPanel.getChildren().addAll(logTitle, logArea, rightTabPane);

        contentBox.getChildren().addAll(leftPanel, rightPanel);
        settingsBox.getChildren().add(contentBox);

        return settingsBox;
    }

    // ==================== 抢票设置内容 ====================

    private VBox createGrabSettings() {
        VBox box = new VBox(6);
        box.setPadding(new Insets(8));

        // 乘客/席别/已选车次 三列
        HBox topRow = new HBox(8);

        // 乘客列表
        VBox passengerBox = new VBox(4);
        Label passengerTitle = new Label("*乘客: 加儿童 (0/14)");
        passengerTitle.getStyleClass().add("setting-label");
        ListView<String> passengerList = new ListView<>();
        passengerList.getItems().addAll("姜来[成人]", "何秀玲[成人]", "卓超[成人]", "周良翼[成人]",
                "姜善周[成人]", "曹祥文[未填]", "曾文翔[成人]", "李奈仁[成人]", "李淑华[成人]", "柳婉清[成人]");
        passengerList.setPrefHeight(120);
        passengerList.getStyleClass().add("setting-list");
        passengerBox.getChildren().addAll(passengerTitle, passengerList);

        // 席别列表
        VBox seatBox = new VBox(4);
        Label seatTitle = new Label("*席别: 选辅 (0/22)");
        seatTitle.getStyleClass().add("setting-label");
        ListView<String> seatList = new ListView<>();
        seatList.getItems().addAll("硬卧", "硬座", "二等座", "一等座", "无座", "软卧", "软座", "商务座", "高级软卧", "优选一等座");
        seatList.setPrefHeight(120);
        seatList.getSelectionModel().select(0);
        seatList.getStyleClass().add("setting-list");
        seatBox.getChildren().addAll(seatTitle, seatList);

        // 已选车次
        VBox trainBox = new VBox(4);
        Label trainTitle = new Label("*已选车次:");
        trainTitle.getStyleClass().add("setting-label");
        ListView<String> trainList = new ListView<>();
        trainList.setPrefHeight(120);
        trainList.setPlaceholder(new Label("可选设置:"));
        trainList.getStyleClass().add("setting-list");
        trainBox.getChildren().addAll(trainTitle, trainList);

        topRow.getChildren().addAll(passengerBox, seatBox, trainBox);

        // 选项区
        VBox optionsBox = new VBox(4);
        CheckBox autoWaitlist = new CheckBox("自动抢候补 设置");
        CheckBox priorityWaitlist = new CheckBox("优先候补不抢票");
        CheckBox byTrainOrder = new CheckBox("按车次顺序提交");
        CheckBox selectBerth = new CheckBox("选上下铺和选座");
        CheckBox autoPay = new CheckBox("抢到自动付 设置");
        CheckBox grabExtra = new CheckBox("抢增开列车 设置");

        HBox timeRow = new HBox(4);
        timeRow.setAlignment(Pos.CENTER_LEFT);
        Label timeLabel = new Label("00:00-24:00");
        timeRow.getChildren().add(timeLabel);

        optionsBox.getChildren().addAll(autoWaitlist, priorityWaitlist, byTrainOrder, selectBerth, autoPay, grabExtra, timeRow);

        box.getChildren().addAll(topRow, optionsBox);
        return box;
    }

    // ==================== 通用设置内容 ====================

    private VBox createCommonSettings() {
        VBox box = new VBox(6);
        box.setPadding(new Insets(8));

        CheckBox timedGrab = new CheckBox("定时抢票");
        Spinner<Integer> timedSpinner = new Spinner<>(0, 23, 5);
        timedSpinner.setPrefWidth(70);
        HBox timedRow = new HBox(4);
        timedRow.getChildren().addAll(timedGrab, new Label("05:00:00"));

        CheckBox modifyInterval = new CheckBox("修改间隔");
        Spinner<Integer> intervalSpinner = new Spinner<>(100, 99999, 1000);
        intervalSpinner.setPrefWidth(70);
        HBox intervalRow = new HBox(4);
        intervalRow.getChildren().addAll(modifyInterval, new Label("1000"));

        CheckBox delayClose = new CheckBox("延迟关闭 [修改间隔] 选项");
        CheckBox nationalCDN = new CheckBox("全国CDN 可用: 348");
        nationalCDN.setSelected(true);
        CheckBox noSubmitWhenNoTicket = new CheckBox("实时余票无座时,不提交");
        CheckBox partialSubmit = new CheckBox("余票不足乘客时,部分提交");

        Button startGrabBtn = new Button("开始抢票");
        startGrabBtn.getStyleClass().add("btn-start-grab");
        startGrabBtn.setPrefWidth(120);
        startGrabBtn.setPrefHeight(36);

        box.getChildren().addAll(timedRow, intervalRow, delayClose, nationalCDN, noSubmitWhenNoTicket, partialSubmit, startGrabBtn);
        return box;
    }

    // ==================== 底部状态栏 ====================

    private HBox createStatusBar() {
        HBox statusBar = new HBox(16);
        statusBar.setAlignment(Pos.CENTER_LEFT);
        statusBar.setPadding(new Insets(4, 10, 4, 10));
        statusBar.getStyleClass().add("status-bar");

        String userDisplay = (currentUser != null && currentUser.getRealName() != null)
                ? currentUser.getRealName() : "未登录";
        Label accountLabel = new Label("\u24D8 当前账号:[" + userDisplay + "] [免费用户]");
        accountLabel.getStyleClass().add("status-label");

        Label pushLabel = new Label("\uD83D\uDCE4 推送");
        pushLabel.getStyleClass().add("status-label");

        Label progressLabel = new Label("\u270F 进度:");
        progressLabel.getStyleClass().add("status-label");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label volumeLabel = new Label("\uD83D\uDD0A");
        volumeLabel.getStyleClass().add("status-label");

        Label networkLabel = new Label("\uD83D\uDCF6 [优]");
        networkLabel.getStyleClass().add("status-label");

        // 注销按钮
        Button logoutBtn = new Button("注销登录");
        logoutBtn.getStyleClass().add("btn-logout-small");
        logoutBtn.setOnAction(e -> {
            logger.info("用户点击注销登录");
            if (onLogout != null) {
                onLogout.run();
            }
            close();
        });

        statusBar.getChildren().addAll(accountLabel, pushLabel, progressLabel, spacer, volumeLabel, networkLabel, logoutBtn);
        return statusBar;
    }

    // ==================== 占位页面 ====================

    private VBox createPlaceholderPage(String title, String description) {
        VBox content = new VBox(15);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(40));

        Label titleLabel = new Label(title);
        titleLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 28));
        titleLabel.getStyleClass().add("placeholder-title");

        Label descLabel = new Label(description);
        descLabel.setFont(Font.font("Microsoft YaHei", 16));
        descLabel.getStyleClass().add("placeholder-desc");

        content.getChildren().addAll(titleLabel, descLabel);
        return content;
    }

    private Label createPlaceholderContent(String text) {
        Label label = new Label(text);
        label.setPadding(new Insets(20));
        label.getStyleClass().add("placeholder-desc");
        return label;
    }

    // ==================== 设置区域折叠切换 ====================

    private void toggleSettings() {
        settingsVisible = !settingsVisible;
        settingsArea.setVisible(settingsVisible);
        settingsArea.setManaged(settingsVisible);
        logger.info("设置区域：{}", settingsVisible ? "展开" : "折叠");
    }

    // ==================== 表格数据模型 ====================

    public static class TableRowData {
        private final javafx.beans.property.SimpleMapProperty<String, String> properties =
                new javafx.beans.property.SimpleMapProperty<>(javafx.collections.FXCollections.observableHashMap());

        public void setProperty(String key, String value) {
            properties.put(key, value);
        }

        public javafx.beans.property.MapProperty<String, String> getProperties() {
            return properties;
        }

        public javafx.beans.binding.StringBinding getProperty(String key) {
            return new javafx.beans.binding.StringBinding() {
                {
                    bind(properties);
                }

                @Override
                protected String computeValue() {
                    return properties.getOrDefault(key, "");
                }
            };
        }
    }
}
