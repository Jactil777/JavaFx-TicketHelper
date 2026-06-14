package com.jactil.javafx.tickethelper;

import com.jactil.javafx.tickethelper.config.AppConfig;
import com.jactil.javafx.tickethelper.model.UserInfo;
import com.jactil.javafx.tickethelper.util.TimeUtil;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;

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

    // 状态栏音量按钮
    private Label volumeLabel;

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

        // 顶部菜单栏
        root.setTop(createMenuBar());

        // 中间内容区（标签页）
        root.setCenter(createTabPane());

        // 底部状态栏
        root.setBottom(createStatusBar());

        Scene scene = new Scene(root, 1280, 800);
        scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
        setScene(scene);

        logger.info("主界面已初始化");
    }

    // ==================== 顶部菜单栏 ====================

    private HBox createMenuBar() {
        HBox menuBar = new HBox(4);
        menuBar.setAlignment(Pos.CENTER_LEFT);
        menuBar.setPadding(new Insets(4, 8, 4, 8));
        menuBar.getStyleClass().add("top-menubar");

        // 注销按钮（绑定注销逻辑）
        Label logoutLabel = new Label("\uD83D\uDD11 注销");
        logoutLabel.getStyleClass().add("menu-item-label");
        logoutLabel.setOnMouseClicked(e -> {
            logger.info("用户点击注销登录");
            if (onLogout != null) {
                onLogout.run();
            }
            close();
        });
        menuBar.getChildren().add(logoutLabel);

        String[][] menuItems = {
                {"\uD83C\uDF10 免登录打开12306官网", "open12306"},
                {"\uD83D\uDD52 同步服务器时间", "syncTime"},
                {"\uD83D\uDD04 检查更新", "checkUpdate"},
                {"\u2699 设置代理", "proxy"},
        };

        for (String[] item : menuItems) {
            Label label = new Label(item[0]);
            label.getStyleClass().add("menu-item-label");
            label.setCursor(javafx.scene.Cursor.HAND);
            if ("proxy".equals(item[1])) {
                label.setOnMouseClicked(e -> showProxyDialog());
            } else if ("checkUpdate".equals(item[1])) {
                label.setOnMouseClicked(e -> showUpdateDialog());
            } else if ("syncTime".equals(item[1])) {
                label.setOnMouseClicked(e -> doSyncServerTime());
            } else if ("open12306".equals(item[1])) {
                label.setOnMouseClicked(e -> doOpen12306());
            } else {
                label.setOnMouseClicked(e -> logger.info("点击菜单：{}", item[1]));
            }
            menuBar.getChildren().add(label);
        }

        // 赞助项目（打开弹框）
        Label donateLabel = new Label("\u2764 赞助项目");
        donateLabel.getStyleClass().add("menu-item-label");
        donateLabel.setCursor(javafx.scene.Cursor.HAND);
        donateLabel.setOnMouseClicked(e -> showDonateDialog());
        menuBar.getChildren().add(donateLabel);

        // 联系作者（打开浏览器）
        Label contactLabel = new Label("\uD83D\uDCE7 联系作者");
        contactLabel.getStyleClass().add("menu-item-label");
        contactLabel.setCursor(javafx.scene.Cursor.HAND);
        contactLabel.setOnMouseClicked(e -> {
            logger.info("联系作者QQ");
            try {
                // 优先尝试 QQ 协议直接唤起客户端
                java.awt.Desktop.getDesktop().browse(java.net.URI.create("tencent://AddContact/?fromId=45&fromSubId=1&subcmd=all&uin=3054123710"));
            } catch (Exception ex) {
                // QQ 协议失败则打开网页版
                try {
                    java.awt.Desktop.getDesktop().browse(java.net.URI.create("http://wpa.qq.com/msgrd?v=3&uin=3054123710&site=qq&menu=yes"));
                } catch (Exception ex2) {
                    logger.error("打开QQ失败", ex2);
                }
            }
        });
        menuBar.getChildren().add(contactLabel);

        // 项目地址（打开浏览器）
        Label projectLink = new Label("\uD83C\uDFE0 项目地址");
        projectLink.getStyleClass().add("menu-item-label");
        projectLink.setCursor(javafx.scene.Cursor.HAND);
        projectLink.setOnMouseClicked(e -> {
            logger.info("打开项目地址");
            try {
                java.awt.Desktop.getDesktop().browse(java.net.URI.create("https://github.com/Jactil777/JavaFx-TicketHelper"));
            } catch (Exception ex) {
                logger.error("打开浏览器失败", ex);
            }
        });
        menuBar.getChildren().add(projectLink);

        // 公告（自动滚动 + 悬浮提示）
        String[] noticeTips = {
                "\uD83D\uDCE2 1.一个账号多处登录，将互相顶下线,影响抢票!",
                "\uD83D\uDCE2 2.使用12306app同时提交订单，建议分开账号!",
                "\uD83D\uDCE2 3.软件遇到间题时，请看12306官网是否正常!",
                "\uD83D\uDCE2 4.起售直接是候补那就是没放票，不存在秒无!",
                "\uD83D\uDCE2 5.起售不放票很正常，但是要第一时间候补支付!",
                "\uD83D\uDCE2 6.如有问题请联系作者!",
                "\uD83D\uDCE2 7.长时间运行软件时，慎重启改网络，建议默认!",
                "\uD83D\uDCE2 8.如果公网IP被封了，可以尝试重启光猫解决!",
                "\uD83D\uDCE2 9.抢票靠的是坚持，放弃就是给别人机会!",
                "\uD83D\uDCE2 10.很多区间会限售，可以使用多站查询多几站!",
                "\uD83D\uDCE2 11.候补兑现率和刷票不冲突，建议两者同时进行!",
                "\uD83D\uDCE2 12.候补兑现顺序是按支付时间，建议自动支付!",
                "\uD83D\uDCE2 13.候补按支付时间排位! 支付时间! 一秒差很多人!",
                "\uD83D\uDCE2 14.车次列表中蓝色的车次，才支持选上下铺功能!",
                "\uD83D\uDCE2 15.有用户认为\u201C候补还排抢吗，有些用户先候补\u201D!",
                "\uD83D\uDCE2 16.还是很多用户抢票遇到了，不要完全依赖候补!"
        };
        Label noticeLabel = new Label(noticeTips[0]);
        noticeLabel.getStyleClass().add("notice-label");
        noticeLabel.setCursor(javafx.scene.Cursor.HAND);
        
        // 自动滚动：每3秒切换下一条
        final int[] noticeIndex = {0};
        Timeline noticeTimeline = new Timeline(new KeyFrame(Duration.seconds(3), e -> {
            noticeIndex[0] = (noticeIndex[0] + 1) % noticeTips.length;
            noticeLabel.setText(noticeTips[noticeIndex[0]]);
        }));
        noticeTimeline.setCycleCount(Timeline.INDEFINITE);
        noticeTimeline.play();
        
        // 鼠标悬停时暂停滚动，显示完整提示
        Tooltip noticeTooltip = new Tooltip(
                "1.一个账号多处登录，将互相顶下线,影响抢票!\n" +
                "2.使用12306app同时提交订单，建议分开账号!\n" +
                "3.软件遇到间题时，请看12306官网是否正常!\n" +
                "4.起售直接是候补那就是没放票，不存在秒无!\n" +
                "5.起售不放票很正常，但是要第一时间候补支付!\n" +
                "6.如有问题请联系作者!\n" +
                "7.长时间运行软件时，慎重启改网络，建议默认!\n" +
                "8.如果公网IP被封了，可以尝试重启光猫解决!\n" +
                "9.抢票靠的是坚持，放弃就是给别人机会!\n" +
                "10.很多区间会限售，可以使用多站查询多几站!\n" +
                "11.候补兑现率和刷票不冲突，建议两者同时进行!\n" +
                "12.候补兑现顺序是按支付时间，建议自动支付!\n" +
                "13.候补按支付时间排位! 支付时间! 一秒差很多人!\n" +
                "14.车次列表中蓝色的车次，才支持选上下铺功能!\n" +
                "15.有用户认为\u201C候补还排抢吗，有些用户先候补\u201D!\n" +
                "16.还是很多用户抢票遇到了，不要完全依赖候补!"
        );
        noticeTooltip.setWrapText(true);
        noticeTooltip.setMaxWidth(500);
        noticeTooltip.getStyleClass().add("status-tooltip");
        noticeTooltip.setShowDelay(javafx.util.Duration.millis(300));
        Tooltip.install(noticeLabel, noticeTooltip);
        
        noticeLabel.setOnMouseEntered(e -> noticeTimeline.pause());
        noticeLabel.setOnMouseExited(e -> noticeTimeline.play());
        
        HBox.setHgrow(noticeLabel, Priority.ALWAYS);
        menuBar.getChildren().add(noticeLabel);

        return menuBar;
    }

    // ==================== 标签页 ====================

    private TabPane createTabPane() {
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabPane.getStyleClass().add("main-tabpane");

        Tab ticketTab = new Tab("抢票页面");
        ticketTab.setContent(createTicketPage());

        Tab orderTab = new Tab("订单管理页面");
        orderTab.setContent(createOrderPage());

        Tab waitlistTab = new Tab("候补订单页面");
        waitlistTab.setContent(createWaitlistPage());

        tabPane.getTabs().addAll(ticketTab, orderTab, waitlistTab);
        return tabPane;
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
        VBox queryBox = new VBox(4);
        queryBox.setPadding(new Insets(8, 10, 8, 10));
        queryBox.getStyleClass().add("query-area");

        // ==================== 主体：左侧4行 + 右侧操作框（等高） ====================
        HBox mainRow = new HBox(0);
        mainRow.setAlignment(Pos.TOP_LEFT);

        // 左侧：4行内容
        VBox leftRows = new VBox(4);
        HBox.setHgrow(leftRows, Priority.ALWAYS);

        // -- 第1行：出发/目的/日期/发车时间 --
        HBox formFields = new HBox(6);
        formFields.setAlignment(Pos.CENTER_LEFT);

        Label fromLabel = new Label("出发:");
        fromLabel.getStyleClass().add("query-label");
        fromStationField = new TextField();
        fromStationField.setPromptText("出发站");
        fromStationField.setPrefWidth(110);
        fromStationField.getStyleClass().add("query-input");

        Button swapBtn = new Button("\u21C4");
        swapBtn.setPrefWidth(32);
        swapBtn.getStyleClass().add("btn-swap");
        swapBtn.setOnAction(e -> {
            String tmp = fromStationField.getText();
            fromStationField.setText(toStationField.getText());
            toStationField.setText(tmp);
        });

        Label toLabel = new Label("目的:");
        toLabel.getStyleClass().add("query-label");
        toStationField = new TextField();
        toStationField.setPromptText("目的站");
        toStationField.setPrefWidth(110);
        toStationField.getStyleClass().add("query-input");

        Label dateLabel = new Label("日期:");
        dateLabel.getStyleClass().add("query-label");
        Button datePrevBtn = new Button("<");
        datePrevBtn.getStyleClass().add("btn-date-nav");
        datePicker = new DatePicker(LocalDate.now());
        datePicker.setPrefWidth(150);
        datePicker.getStyleClass().add("query-datepicker");
        Button dateNextBtn = new Button(">");
        dateNextBtn.getStyleClass().add("btn-date-nav");

        Label timeLabel = new Label("发车时间:");
        timeLabel.getStyleClass().add("query-label");
        departTimeCombo = new ComboBox<>();
        departTimeCombo.getItems().addAll("00:00-24:00", "00:00-06:00", "06:00-12:00", "12:00-18:00", "18:00-24:00");
        departTimeCombo.setValue("00:00-24:00");
        departTimeCombo.setPrefWidth(120);

        formFields.getChildren().addAll(fromLabel, fromStationField, swapBtn, toLabel, toStationField,
                dateLabel, datePrevBtn, datePicker, dateNextBtn, timeLabel, departTimeCombo);

        // -- 第2行：模式 --
        HBox modeRow = new HBox(6);
        modeRow.setAlignment(Pos.CENTER_LEFT);
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
        modeRow.getChildren().addAll(modeLabel, singleTask, multiTask, multiStation, addTaskBtn);

        // -- 第3行：筛选 --
        HBox filterRow = new HBox(6);
        filterRow.setAlignment(Pos.CENTER_LEFT);
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
        filterRow.getChildren().addAll(filterLabel, filterAll, filterG, filterD, filterZ, filterT, filterK, filterOther);

        // -- 第4行：隐藏 --
        HBox hideRow = new HBox(6);
        hideRow.setAlignment(Pos.CENTER_LEFT);
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
        hideRow.getChildren().addAll(hideLabel, hideAll, hideBusiness, hideFirstPlus, hideFirst, hideSecond,
                hideHighSoft, hideSoftSleeper, hideHardSleeper, hideSoftSeat, hideHardSeat, hideNoSeat, hideOtherSeat);

        leftRows.getChildren().addAll(formFields, modeRow, filterRow, hideRow);

        // 右侧：操作框（固定宽度，高度自动匹配左侧4行）
        CheckBox adultCheck = new CheckBox("成人");
        adultCheck.setSelected(true);
        CheckBox studentCheck = new CheckBox("学生");
        Label transferLink = new Label("查询中转换乘");
        transferLink.getStyleClass().add("link-blue");
        CheckBox onlyAvailableCheck = new CheckBox("只看有票的车次");
        Label showAllPriceLink = new Label("显示全部票价");
        showAllPriceLink.getStyleClass().add("link-blue");

        Button queryBtn = new Button("查询\n车票");
        queryBtn.getStyleClass().add("btn-query");
        queryBtn.setPrefWidth(65);
        queryBtn.setPrefHeight(48);

        VBox opCheckboxes = new VBox(3);
        HBox opRow1 = new HBox(8);
        opRow1.setAlignment(Pos.CENTER_LEFT);
        opRow1.getChildren().addAll(adultCheck, studentCheck, transferLink);
        HBox opRow2 = new HBox(8);
        opRow2.setAlignment(Pos.CENTER_LEFT);
        opRow2.getChildren().addAll(onlyAvailableCheck, showAllPriceLink);
        opCheckboxes.getChildren().addAll(opRow1, opRow2);

        VBox opBox = new VBox(3);
        opBox.setPadding(new Insets(2, 8, 6, 8));
        opBox.getStyleClass().add("op-group-box");
        opBox.setPrefWidth(290);

        Label opTitle = new Label("操作");
        opTitle.getStyleClass().add("op-group-title");

        HBox opContent = new HBox(10);
        opContent.setAlignment(Pos.CENTER_LEFT);
        VBox.setVgrow(opCheckboxes, Priority.ALWAYS);
        opContent.getChildren().addAll(opCheckboxes, queryBtn);

        opBox.getChildren().addAll(opTitle, opContent);

        mainRow.getChildren().addAll(leftRows, opBox);

        // ==================== 底部：显示/隐藏设置区域切换 ====================
        HBox toggleRow = new HBox();
        toggleRow.setAlignment(Pos.CENTER);
        Label toggleSettings = new Label("\u2191显示设置区域\u2191");
        toggleSettings.getStyleClass().add("toggle-settings");
        toggleSettings.setOnMouseClicked(e -> toggleSettings());
        toggleRow.getChildren().add(toggleSettings);

        queryBox.getChildren().addAll(mainRow, toggleRow);
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
        HBox statusBar = new HBox(14);
        statusBar.setAlignment(Pos.CENTER_LEFT);
        statusBar.setPadding(new Insets(6, 12, 6, 12));
        statusBar.getStyleClass().add("status-bar");

        String userDisplay = (currentUser != null && currentUser.getRealName() != null)
                ? currentUser.getRealName() : "未登录";
        Label accountLabel = new Label("\u24D8 当前账号:[" + userDisplay + "] [免费用户]");
        accountLabel.getStyleClass().add("status-label");

        Label pushLabel = new Label("\uD83D\uDCE4 推送");
        pushLabel.getStyleClass().add("status-label");

        Label progressLabel = new Label("\u270F 进度:");
        progressLabel.getStyleClass().add("status-label");

        // 音量开关（放在进度右边）
        volumeLabel = new Label();
        volumeLabel.getStyleClass().add("status-icon-label");
        volumeLabel.setCursor(javafx.scene.Cursor.HAND);
        volumeLabel.setPadding(new Insets(4, 10, 4, 10));
        updateVolumeIcon();
        volumeLabel.setOnMouseClicked(e -> toggleSound());
        Tooltip volumeTooltip = new Tooltip("声音总开关：关闭后软件将静音，所有提示音（如抢票结果、掉线提醒）均失效，建议保持开启，以免错过重要通知！");
        volumeTooltip.setWrapText(true);
        volumeTooltip.setMaxWidth(450);
        volumeTooltip.getStyleClass().add("status-tooltip");
        volumeTooltip.setShowDelay(javafx.util.Duration.millis(300));
        Tooltip.install(volumeLabel, volumeTooltip);

        // 12306链接速度
        Label networkLabel = new Label("\uD83D\uDCF6 [优]");
        networkLabel.getStyleClass().add("status-icon-label");
        networkLabel.setCursor(javafx.scene.Cursor.HAND);
        networkLabel.setPadding(new Insets(4, 10, 4, 10));
        Tooltip networkTooltip = new Tooltip(
                "1.代表链接12306速度，分别为：优，良，差，极差\n" +
                "2.链接12306速度和网速并非直接关系，网速快不一定链接就快\n" +
                "3.链接过慢将导致提交订单时的卡顿，此处仅为测试速度\n" +
                "4.多次测试为准，点击此处将重新测速，但不要频繁测试"
        );
        networkTooltip.setWrapText(true);
        networkTooltip.setMaxWidth(450);
        networkTooltip.getStyleClass().add("status-tooltip");
        Tooltip.install(networkLabel, networkTooltip);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        statusBar.getChildren().addAll(accountLabel, pushLabel, progressLabel, volumeLabel, networkLabel, spacer);
        return statusBar;
    }

    // ==================== 订单管理页面 ====================

    private VBox createOrderPage() {
        VBox page = new VBox(0);

        // 顶部操作区：查询 + 未完成订单 + 已完成订单 + 相关问题（分组线框）
        HBox topBar = new HBox(8);
        topBar.setPadding(new Insets(8, 10, 8, 10));
        topBar.getStyleClass().add("order-top-bar");

        // 查询区域（分组）
        Button queryAllBtn = new Button("查询全部订单");
        queryAllBtn.getStyleClass().add("order-btn");
        CheckBox historyCheck = new CheckBox("历史");
        HBox queryContent = new HBox(8);
        queryContent.setAlignment(Pos.CENTER_LEFT);
        queryContent.setPadding(new Insets(4, 8, 6, 8));
        queryContent.getChildren().addAll(queryAllBtn, historyCheck);
        TitledPane queryGroup = new TitledPane("查询", queryContent);
        queryGroup.setCollapsible(false);
        queryGroup.getStyleClass().add("order-group");
        queryGroup.setPrefWidth(220);

        // 未完成订单区域（分组）
        Button payBtn = new Button("继续支付");
        payBtn.getStyleClass().add("order-btn");
        Button cancelBtn = new Button("取消订单");
        cancelBtn.getStyleClass().add("order-btn");
        HBox unfinishedContent = new HBox(8);
        unfinishedContent.setAlignment(Pos.CENTER_LEFT);
        unfinishedContent.setPadding(new Insets(4, 8, 6, 8));
        unfinishedContent.getChildren().addAll(payBtn, cancelBtn);
        TitledPane unfinishedGroup = new TitledPane("未完成订单", unfinishedContent);
        unfinishedGroup.setCollapsible(false);
        unfinishedGroup.getStyleClass().add("order-group");
        unfinishedGroup.setPrefWidth(200);

        // 已完成订单区域（分组）
        Button refundBtn = new Button("退票");
        refundBtn.getStyleClass().add("order-btn");
        Button changeBtn = new Button("改签(刷票)");
        changeBtn.getStyleClass().add("order-btn");
        Button changeStationBtn = new Button("变更到站(刷票)");
        changeStationBtn.getStyleClass().add("order-btn");
        HBox finishedContent = new HBox(8);
        finishedContent.setAlignment(Pos.CENTER_LEFT);
        finishedContent.setPadding(new Insets(4, 8, 6, 8));
        finishedContent.getChildren().addAll(refundBtn, changeBtn, changeStationBtn);
        TitledPane finishedGroup = new TitledPane("已完成订单", finishedContent);
        finishedGroup.setCollapsible(false);
        finishedGroup.getStyleClass().add("order-group");
        finishedGroup.setPrefWidth(340);

        // 相关问题区域（分组）
        Label link1 = new Label("查询本人车票");
        link1.getStyleClass().add("link-blue");
        Label link2 = new Label("改签与原票问题");
        link2.getStyleClass().add("link-blue");
        Label link3 = new Label("抢到无座?");
        link3.getStyleClass().add("link-blue");
        Label link4 = new Label("查不到车票?");
        link4.getStyleClass().add("link-blue");
        HBox faqContent = new HBox(12);
        faqContent.setAlignment(Pos.CENTER_LEFT);
        faqContent.setPadding(new Insets(4, 8, 6, 8));
        faqContent.getChildren().addAll(link1, link2, link3, link4);
        TitledPane faqGroup = new TitledPane("相关问题", faqContent);
        faqGroup.setCollapsible(false);
        faqGroup.getStyleClass().add("order-group");
        faqGroup.setPrefHeight(62);

        topBar.getChildren().addAll(queryGroup, unfinishedGroup, finishedGroup, faqGroup);

        // 订单数据表格
        VBox tableBox = new VBox(0);
        tableBox.getStyleClass().add("table-area");

        TableView<OrderRowData> orderTableView = new TableView<>();
        orderTableView.getStyleClass().add("result-table");
        orderTableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        String[] orderColumns = {"选择", "订单号", "订单时间", "发车时间", "车次", "发站", "到站",
                "乘客", "票种", "席别", "车厢", "座位", "票价", "状态"};
        double[] orderWidths = {50, 140, 140, 120, 70, 80, 80, 80, 60, 70, 50, 60, 70, 100};

        for (int i = 0; i < orderColumns.length; i++) {
            final String colName = orderColumns[i];
            TableColumn<OrderRowData, String> col = new TableColumn<>(colName);
            col.setCellValueFactory(data -> data.getValue().getProperty(colName));
            col.setPrefWidth(orderWidths[i]);
            col.setResizable(true);
            orderTableView.getColumns().add(col);
        }

        orderTableView.setPlaceholder(new Label("暂无订单数据，请点击\"查询全部订单\"获取订单信息"));
        VBox.setVgrow(orderTableView, Priority.ALWAYS);
        tableBox.getChildren().add(orderTableView);

        VBox.setVgrow(tableBox, Priority.ALWAYS);
        page.getChildren().addAll(topBar, tableBox);
        return page;
    }

    // ==================== 候补订单页面 ====================

    private VBox createWaitlistPage() {
        VBox page = new VBox(0);

        // 顶部操作区：查询 + 待支付订单 + 待兑现订单（分组线框）
        HBox topBar = new HBox(8);
        topBar.setPadding(new Insets(8, 10, 8, 10));
        topBar.getStyleClass().add("order-top-bar");

        // 查询区域（分组）
        Button queryWaitlistBtn = new Button("查询候补订单");
        queryWaitlistBtn.getStyleClass().add("order-btn");
        CheckBox processedCheck = new CheckBox("已处理");
        HBox queryContent = new HBox(8);
        queryContent.setAlignment(Pos.CENTER_LEFT);
        queryContent.setPadding(new Insets(4, 8, 6, 8));
        queryContent.getChildren().addAll(queryWaitlistBtn, processedCheck);
        TitledPane queryGroup = new TitledPane("查询", queryContent);
        queryGroup.setCollapsible(false);
        queryGroup.getStyleClass().add("order-group");
        queryGroup.setPrefWidth(220);

        // 待支付订单区域（分组）
        Button continuePayBtn = new Button("继续支付");
        continuePayBtn.getStyleClass().add("order-btn");
        Button cancelOrderBtn = new Button("取消订单");
        cancelOrderBtn.getStyleClass().add("order-btn");
        HBox pendingPayContent = new HBox(8);
        pendingPayContent.setAlignment(Pos.CENTER_LEFT);
        pendingPayContent.setPadding(new Insets(4, 8, 6, 8));
        pendingPayContent.getChildren().addAll(continuePayBtn, cancelOrderBtn);
        TitledPane pendingPayGroup = new TitledPane("待支付订单", pendingPayContent);
        pendingPayGroup.setCollapsible(false);
        pendingPayGroup.getStyleClass().add("order-group");
        pendingPayGroup.setPrefWidth(200);

        // 待兑现订单区域（分组）
        Button refundOrderBtn = new Button("退单");
        refundOrderBtn.getStyleClass().add("order-btn");
        HBox pendingContent = new HBox(8);
        pendingContent.setAlignment(Pos.CENTER_LEFT);
        pendingContent.setPadding(new Insets(4, 8, 6, 8));
        pendingContent.getChildren().add(refundOrderBtn);
        TitledPane pendingGroup = new TitledPane("待兑现订单", pendingContent);
        pendingGroup.setCollapsible(false);
        pendingGroup.getStyleClass().add("order-group");
        pendingGroup.setPrefWidth(160);

        topBar.getChildren().addAll(queryGroup, pendingPayGroup, pendingGroup);

        // 候补订单数据表格
        VBox tableBox = new VBox(0);
        tableBox.getStyleClass().add("table-area");

        TableView<WaitlistRowData> waitlistTableView = new TableView<>();
        waitlistTableView.getStyleClass().add("result-table");
        waitlistTableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        String[] waitlistColumns = {"选择", "候补单号", "下单日期", "截止时间", "车次信息",
                "席别", "旅客信息", "总金额", "成功率", "状态"};
        double[] waitlistWidths = {50, 140, 120, 120, 200, 80, 120, 80, 70, 100};

        for (int i = 0; i < waitlistColumns.length; i++) {
            final String colName = waitlistColumns[i];
            TableColumn<WaitlistRowData, String> col = new TableColumn<>(colName);
            col.setCellValueFactory(data -> data.getValue().getProperty(colName));
            col.setPrefWidth(waitlistWidths[i]);
            col.setResizable(true);
            waitlistTableView.getColumns().add(col);
        }

        waitlistTableView.setPlaceholder(new Label("暂无候补订单数据，请点击\"查询候补订单\"获取候补信息"));
        VBox.setVgrow(waitlistTableView, Priority.ALWAYS);
        tableBox.getChildren().add(waitlistTableView);

        VBox.setVgrow(tableBox, Priority.ALWAYS);
        page.getChildren().addAll(topBar, tableBox);
        return page;
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

    // ==================== 声音开关 ====================

    /** 更新音量图标（根据 AppConfig 中的声音开关状态） */
    private void updateVolumeIcon() {
        if (volumeLabel == null) return;
        AppConfig config = AppConfig.getInstance();
        if (config.isSoundEnabled()) {
            // 开启状态：喇叭图标
            volumeLabel.setText("\uD83D\uDD0A");
            volumeLabel.setStyle("-fx-text-fill: #333333;");
        } else {
            // 关闭状态：静音图标（喇叭+叉）
            volumeLabel.setText("\uD83D\uDD07");
            volumeLabel.setStyle("-fx-text-fill: #d32f2f;");
        }
    }

    /** 切换声音开关状态 */
    private void toggleSound() {
        AppConfig config = AppConfig.getInstance();
        boolean newState = !config.isSoundEnabled();
        config.setSoundEnabled(newState);
        updateVolumeIcon();
        logger.info("声音总开关：{}", newState ? "开启" : "关闭");
    }

    // ==================== 免登录打开12306官网 ====================

    private static final String HOME_URL = "https://kyfw.12306.cn/otn/view/index.html";

    private void doOpen12306() {
        // 使用 Selenium 启动真实浏览器，注入 Cookie 实现免登录
        // 优先级：Edge（Windows自带）> Chrome > 系统默认浏览器
        new Thread(() -> {
            org.openqa.selenium.WebDriver driver = null;
            java.util.List<okhttp3.Cookie> okCookies =
                    com.jactil.javafx.tickethelper.util.HttpClientUtil.getAllCookies();
            // 每次使用唯一临时目录，避免多实例冲突
            String sessionId = java.util.UUID.randomUUID().toString().substring(0, 8);
            String tmpDir = System.getProperty("java.io.tmpdir") + "/tickethelper-" + sessionId;

            // 依次尝试 Edge -> Chrome
            String[] browsers = {"edge", "chrome"};
            for (String browser : browsers) {
                try {
                    if ("edge".equals(browser)) {
                        logger.info("[浏览器] 尝试启动 Edge...");
                        org.openqa.selenium.edge.EdgeOptions options = new org.openqa.selenium.edge.EdgeOptions();
                        options.addArguments("--user-data-dir=" + tmpDir);
                        options.addArguments("--no-first-run");
                        options.addArguments("--no-default-browser-check");
                        options.addArguments("--disable-blink-features=AutomationControlled");
                        options.setExperimentalOption("excludeSwitches", java.util.Arrays.asList("enable-automation"));
                        driver = new org.openqa.selenium.edge.EdgeDriver(options);
                    } else {
                        logger.info("[浏览器] 尝试启动 Chrome...");
                        org.openqa.selenium.chrome.ChromeOptions options = new org.openqa.selenium.chrome.ChromeOptions();
                        options.addArguments("--user-data-dir=" + tmpDir);
                        options.addArguments("--no-first-run");
                        options.addArguments("--no-default-browser-check");
                        options.addArguments("--disable-blink-features=AutomationControlled");
                        options.setExperimentalOption("excludeSwitches", java.util.Arrays.asList("enable-automation"));
                        driver = new org.openqa.selenium.chrome.ChromeDriver(options);
                    }
                    logger.info("[浏览器] {} 已启动", browser);
                    break; // 启动成功，跳出循环
                } catch (Exception e) {
                    logger.warn("[浏览器] {} 启动失败: {}", browser, e.getMessage());
                    if (driver != null) {
                        try { driver.quit(); } catch (Exception ignored) {}
                        driver = null;
                    }
                }
            }

            if (driver == null) {
                // 所有浏览器都失败，回退到系统默认浏览器
                logger.info("[浏览器] 未找到可用的 Edge/Chrome，回退到系统默认浏览器");
                try {
                    java.awt.Desktop.getDesktop().browse(java.net.URI.create(HOME_URL));
                    // 提示用户需要手动登录
                    javafx.application.Platform.runLater(() -> {
                        Alert alert = new Alert(Alert.AlertType.INFORMATION);
                        alert.setTitle("提示");
                        alert.setHeaderText(null);
                        alert.setContentText("已使用系统默认浏览器打开12306。\n由于无法自动注入登录信息，请在浏览器中手动登录。\n\n建议安装 Edge 或 Chrome 浏览器以获得免登录体验。");
                        alert.showAndWait();
                    });
                } catch (Exception ex) {
                    logger.error("[浏览器] 打开系统浏览器也失败", ex);
                    javafx.application.Platform.runLater(() -> {
                        Alert alert = new Alert(Alert.AlertType.ERROR);
                        alert.setTitle("错误");
                        alert.setHeaderText(null);
                        alert.setContentText("无法打开浏览器，请手动访问 https://kyfw.12306.cn");
                        alert.showAndWait();
                    });
                }
                return;
            }

            try {
                // 先访问 12306 域名（必须先访问域名才能设置该域名的 Cookie）
                driver.get("https://kyfw.12306.cn");

                // 注入 OkHttp 的所有 Cookie
                logger.info("[浏览器] 获取到 {} 个 Cookie，开始注入", okCookies.size());
                org.openqa.selenium.WebDriver.Options manage = driver.manage();
                for (okhttp3.Cookie c : okCookies) {
                    try {
                        org.openqa.selenium.Cookie seleniumCookie = new org.openqa.selenium.Cookie(
                                c.name(),
                                c.value(),
                                c.domain() != null ? c.domain() : ".12306.cn",
                                "/",
                                new java.util.Date(c.expiresAt()),
                                c.secure(),
                                c.httpOnly()
                        );
                        manage.addCookie(seleniumCookie);
                        logger.debug("[浏览器] 注入 Cookie: {}", c.name());
                    } catch (Exception e) {
                        logger.warn("[浏览器] Cookie 注入失败 [{}]: {}", c.name(), e.getMessage());
                    }
                }
                logger.info("[浏览器] Cookie 注入完成，正在跳转到首页...");

                // 导航到 12306 首页
                driver.get(HOME_URL);
                logger.info("[浏览器] 已导航到 12306 首页");

            } catch (Exception e) {
                logger.error("[浏览器] 操作失败: {}", e.getMessage(), e);
                try { driver.quit(); } catch (Exception ignored) {}
            }
        }, "browser-12306").start();
    }

    // ==================== 同步服务器时间 ====================

    private void doSyncServerTime() {
        new Thread(() -> {
            try {
                long[] times = TimeUtil.syncServerTime();
                long serverTime = times[0];
                long localTime = times[1];

                java.time.LocalDateTime serverLdt = java.time.LocalDateTime.ofInstant(
                        java.time.Instant.ofEpochMilli(serverTime), java.time.ZoneId.systemDefault());
                java.time.LocalDateTime localLdt = java.time.LocalDateTime.ofInstant(
                        java.time.Instant.ofEpochMilli(localTime), java.time.ZoneId.systemDefault());

                java.time.format.DateTimeFormatter fmt =
                        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

                logger.info("[网络时间]：{}", serverLdt.format(fmt));
                logger.info("[本机时间]：{}", localLdt.format(fmt));
                logger.info("[同步成功]已完成自动同步本机时间。");
            } catch (Exception e) {
                logger.error("[同步失败]服务器时间同步异常：{}", e.getMessage());
            }
        }, "sync-server-time").start();
    }

    // ==================== 检查更新弹框 ====================

    private void showUpdateDialog() {
        Stage updateStage = new Stage();
        updateStage.initModality(Modality.APPLICATION_MODAL);
        updateStage.initOwner(this);
        updateStage.setTitle("\uD83D\uDD04 检查更新");
        updateStage.setResizable(false);

        String currentVersion = AppConfig.APP_VERSION;
        String latestVersion = "检测中...";
        String changelog = "正在从 GitHub 获取更新日志...";
        boolean hasUpdate = false;

        // 同步获取所有 Release 记录
        try {
            java.net.URL url = new java.net.URL(AppConfig.GITHUB_API_RELEASES);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
            conn.setRequestProperty("User-Agent", "JavaFx-TicketHelper");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            if (conn.getResponseCode() == 200) {
                String body;
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(conn.getInputStream(), "UTF-8"))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    body = sb.toString();
                }

                // 解析 Release 数组
                java.util.List<String[]> releases = parseReleases(body);
                if (!releases.isEmpty()) {
                    // 第一个是最新版本
                    String[] latest = releases.get(0);
                    latestVersion = latest[0];
                    hasUpdate = compareVersions(latestVersion, currentVersion) > 0;

                    // 格式化全部更新日志
                    StringBuilder log = new StringBuilder();
                    for (int i = 0; i < releases.size(); i++) {
                        String[] rel = releases.get(i);
                        if (i > 0) log.append("\n");
                        log.append("版本：[").append(rel[0]).append("]\n");
                        log.append("时间：").append(rel[1]).append("\n");
                        log.append("内容：\n").append(rel[2]);
                    }
                    changelog = log.toString();
                } else {
                    changelog = "暂无发布记录";
                }
            } else {
                changelog = "获取失败，HTTP " + conn.getResponseCode();
            }
            conn.disconnect();
        } catch (Exception e) {
            logger.error("检查更新失败", e);
            changelog = "网络请求失败：" + e.getMessage() + "\n\n请检查网络连接后重试。";
        }

        final String finalLatestVersion = latestVersion;
        final String finalChangelog = changelog;
        final boolean finalHasUpdate = hasUpdate;

        VBox root = new VBox(0);
        root.getStyleClass().add("update-root");

        // 顶部版本信息
        HBox versionBox = new HBox(20);
        versionBox.setPadding(new Insets(12, 16, 8, 16));
        versionBox.setAlignment(Pos.CENTER_LEFT);

        Label currentVerLabel = new Label("当前版本：" + currentVersion);
        currentVerLabel.getStyleClass().add("update-version-label");

        Label latestVerLabel = new Label("最新版本：" + finalLatestVersion);
        latestVerLabel.getStyleClass().add("update-version-label");

        versionBox.getChildren().addAll(currentVerLabel, latestVerLabel);
        root.getChildren().add(versionBox);

        // 日志标题
        Label logTitle = new Label("日志记录：");
        logTitle.getStyleClass().add("update-log-title");
        logTitle.setPadding(new Insets(4, 16, 4, 16));
        root.getChildren().add(logTitle);

        // 更新日志内容
        TextArea logArea = new TextArea(finalChangelog);
        logArea.getStyleClass().add("update-log-area");
        logArea.setEditable(false);
        logArea.setWrapText(true);
        logArea.setPrefHeight(260);

        VBox logBox = new VBox(logArea);
        logBox.setPadding(new Insets(0, 16, 12, 16));
        root.getChildren().add(logBox);

        // 底部按钮
        HBox btnBox = new HBox(12);
        btnBox.setAlignment(Pos.CENTER);
        btnBox.setPadding(new Insets(0, 16, 14, 16));

        Button downloadBtn = new Button("安装包下载");
        downloadBtn.getStyleClass().add("update-btn-download");
        downloadBtn.setPrefWidth(120);
        downloadBtn.setOnAction(e -> {
            try {
                java.awt.Desktop.getDesktop().browse(java.net.URI.create(AppConfig.GITHUB_RELEASES_URL));
            } catch (Exception ex) {
                logger.error("打开浏览器失败", ex);
            }
        });

        Button autoUpdateBtn = new Button("自动更新");
        autoUpdateBtn.getStyleClass().add("update-btn-auto");
        autoUpdateBtn.setPrefWidth(120);
        autoUpdateBtn.setDisable(true);
        autoUpdateBtn.setOnAction(e -> {});

        Button closeBtn = new Button(finalHasUpdate ? "暂不更新" : "关闭");
        closeBtn.getStyleClass().add("update-btn-close");
        closeBtn.setPrefWidth(100);
        closeBtn.setOnAction(e -> updateStage.close());

        btnBox.getChildren().addAll(downloadBtn, autoUpdateBtn, closeBtn);
        root.getChildren().add(btnBox);

        // 底部提示
        Label bottomTip = new Label(finalHasUpdate ? "\u2B50 发现新版本，请下载最新版本安装包进行更新！" : "\u2705 当前已是最新版本，无需更新。");
        bottomTip.getStyleClass().add(finalHasUpdate ? "update-tip-new" : "update-tip-latest");
        bottomTip.setWrapText(true);
        bottomTip.setMaxWidth(Double.MAX_VALUE);
        bottomTip.setPadding(new Insets(8, 16, 10, 16));
        root.getChildren().add(bottomTip);

        Scene scene = new Scene(root, 520, 420);
        scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
        updateStage.setScene(scene);
        updateStage.show();

        logger.info("打开检查更新弹框，当前版本：{}，最新版本：{}", currentVersion, finalLatestVersion);
    }

    /** 解析 GitHub Releases JSON 数组，返回 List<[版本号, 发布时间, 更新内容]> */
    private java.util.List<String[]> parseReleases(String json) {
        java.util.List<String[]> list = new java.util.ArrayList<>();
        // 找到 [ 开始
        int arrStart = json.indexOf('[');
        if (arrStart < 0) return list;
        // 逐个解析 { ... } 对象
        int pos = arrStart + 1;
        while (pos < json.length()) {
            int objStart = json.indexOf('{', pos);
            if (objStart < 0) break;
            int objEnd = findMatchingBrace(json, objStart);
            if (objEnd < 0) break;
            String obj = json.substring(objStart, objEnd + 1);

            String tagName = extractJsonField(obj, "tag_name");
            String publishedAt = extractJsonField(obj, "published_at");
            String body = extractJsonField(obj, "body");

            if (tagName != null) {
                String version = tagName.startsWith("v") ? tagName.substring(1) : tagName;
                String date = "";
                if (publishedAt != null && publishedAt.length() >= 10) {
                    date = publishedAt.substring(0, 10); // yyyy-MM-dd
                }
                String content = "";
                if (body != null && !body.isEmpty()) {
                    content = body.replace("\\n", "\n").replace("\\r", "").replace("\"", "");
                } else {
                    content = "暂无更新说明";
                }
                list.add(new String[]{version, date, content});
            }
            pos = objEnd + 1;
        }
        return list;
    }

    /** 找到与 start 处 { 匹配的 } 位置 */
    private int findMatchingBrace(String s, int start) {
        int depth = 0;
        boolean inString = false;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' && (i == 0 || s.charAt(i - 1) != '\\')) {
                inString = !inString;
            } else if (!inString) {
                if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) return i;
                }
            }
        }
        return -1;
    }

    /** 从简单 JSON 字符串中提取字段值 */
    private String extractJsonField(String json, String field) {
        String key = "\"" + field + "\"";
        int idx = json.indexOf(key);
        if (idx < 0) return null;
        int start = json.indexOf(':', idx) + 1;
        // 跳过空白
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) start++;
        if (start >= json.length()) return null;
        if (json.charAt(start) == '"') {
            // 字符串值
            int end = json.indexOf('"', start + 1);
            // 处理转义
            while (end > 0 && json.charAt(end - 1) == '\\') {
                end = json.indexOf('"', end + 1);
            }
            if (end < 0) return null;
            return json.substring(start + 1, end);
        } else if (json.charAt(start) == 'n' && json.startsWith("null", start)) {
            return null;
        }
        return null;
    }

    /** 比较版本号，返回 >0 表示 v1 更新，<0 表示 v2 更新，0 表示相同 */
    private int compareVersions(String v1, String v2) {
        String[] parts1 = v1.split("[.\\-]");
        String[] parts2 = v2.split("[.\\-]");
        int len = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < len; i++) {
            int n1 = i < parts1.length ? parseVersionPart(parts1[i]) : 0;
            int n2 = i < parts2.length ? parseVersionPart(parts2[i]) : 0;
            if (n1 != n2) return n1 - n2;
        }
        return 0;
    }

    private int parseVersionPart(String s) {
        try {
            return Integer.parseInt(s.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ==================== 代理设置弹框 ====================

    private void showProxyDialog() {
        Stage proxyStage = new Stage();
        proxyStage.initModality(Modality.APPLICATION_MODAL);
        proxyStage.initOwner(this);
        proxyStage.setTitle("\u2699 网络设置");
        proxyStage.setResizable(false);

        VBox root = new VBox(0);
        root.getStyleClass().add("proxy-root");

        // 标题栏区域（代理设置标签）
        VBox tabArea = new VBox(0);
        tabArea.setPadding(new Insets(8, 12, 0, 12));
        Label tabLabel = new Label("代理设置");
        tabLabel.getStyleClass().add("proxy-tab-label");
        tabArea.getChildren().add(tabLabel);
        root.getChildren().add(tabArea);

        // 表单区域
        VBox formBox = new VBox(10);
        formBox.setPadding(new Insets(16, 20, 12, 20));

        // 第一行：协议值 + 超时 + 使用系统代理
        HBox row1 = new HBox(16);
        row1.setAlignment(Pos.CENTER_LEFT);

        Label protocolLabel = new Label("协议值：");
        protocolLabel.setPrefWidth(60);
        ComboBox<String> protocolCombo = new ComboBox<>();
        protocolCombo.getItems().addAll("Https代理", "Socks5代理");
        protocolCombo.setValue("Https代理");
        protocolCombo.setPrefWidth(130);

        Label timeoutLabel = new Label("超时：");
        Spinner<Integer> timeoutSpinner = new Spinner<>(100, 10000, 1500, 100);
        timeoutSpinner.setPrefWidth(80);
        timeoutSpinner.setEditable(true);

        CheckBox useSystemProxy = new CheckBox("使用系统代理");
        useSystemProxy.setSelected(false);

        row1.getChildren().addAll(protocolLabel, protocolCombo, timeoutLabel, timeoutSpinner, useSystemProxy);

        // 第二行：IP地址 + 端口 + 测试
        HBox row2 = new HBox(16);
        row2.setAlignment(Pos.CENTER_LEFT);

        Label ipLabel = new Label("IP地址：");
        ipLabel.setPrefWidth(60);
        TextField ipField = new TextField();
        ipField.setPrefWidth(130);

        Label portLabel = new Label("端口：");
        TextField portField = new TextField();
        portField.setPrefWidth(80);

        Button testBtn = new Button("测试");
        testBtn.setPrefWidth(70);
        testBtn.setOnAction(e -> logger.info("测试代理连接"));

        row2.getChildren().addAll(ipLabel, ipField, portLabel, portField, testBtn);

        // 第三行：用户名 + 密码
        HBox row3 = new HBox(16);
        row3.setAlignment(Pos.CENTER_LEFT);

        Label userLabel = new Label("用户名：");
        userLabel.setPrefWidth(60);
        TextField userField = new TextField();
        userField.setPrefWidth(130);

        Label passLabel = new Label("密码：");
        PasswordField passField = new PasswordField();
        passField.setPrefWidth(80);

        Label optionalLabel = new Label("(此行可选)");
        optionalLabel.getStyleClass().add("proxy-optional-label");

        row3.getChildren().addAll(userLabel, userField, passLabel, passField, optionalLabel);

        // 第四行：IP网址 + 正则提取
        HBox row4 = new HBox(16);
        row4.setAlignment(Pos.CENTER_LEFT);

        Label urlLabel = new Label("IP网址：");
        urlLabel.setPrefWidth(60);
        ComboBox<String> urlCombo = new ComboBox<>();
        urlCombo.getItems().addAll(
                "http://www.xicidaili.com/nt/1",
                "http://www.kuaidaili.com/proxylist/1",
                "http://www.cz88.net/proxy"
        );
        urlCombo.setValue("http://www.xicidaili.com/nt/1");
        urlCombo.setPrefWidth(220);
        urlCombo.setEditable(true);

        Button regexBtn = new Button("正则提取");
        regexBtn.setPrefWidth(70);
        regexBtn.setOnAction(e -> logger.info("正则提取代理IP"));

        row4.getChildren().addAll(urlLabel, urlCombo, regexBtn);

        formBox.getChildren().addAll(row1, row2, row3, row4);
        root.getChildren().add(formBox);

        // 表格区域
        TableView<javafx.collections.ObservableMap<String, String>> proxyTable = new TableView<>();
        proxyTable.getStyleClass().add("proxy-table");

        TableColumn<javafx.collections.ObservableMap<String, String>, String> colIp = new TableColumn<>("代理IP地址");
        colIp.setPrefWidth(180);
        colIp.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
                data.getValue().getOrDefault("ip", "")));

        TableColumn<javafx.collections.ObservableMap<String, String>, String> colPort = new TableColumn<>("端口");
        colPort.setPrefWidth(80);
        colPort.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
                data.getValue().getOrDefault("port", "")));

        TableColumn<javafx.collections.ObservableMap<String, String>, String> colDetail = new TableColumn<>("详细信息");
        colDetail.setPrefWidth(200);
        colDetail.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
                data.getValue().getOrDefault("detail", "")));

        proxyTable.getColumns().addAll(colIp, colPort, colDetail);
        proxyTable.setPrefHeight(200);
        proxyTable.setPlaceholder(new Label("暂无代理IP"));

        VBox tableBox = new VBox(proxyTable);
        tableBox.setPadding(new Insets(0, 20, 12, 20));
        root.getChildren().add(tableBox);

        // 底部按钮
        HBox btnBox = new HBox(16);
        btnBox.setAlignment(Pos.CENTER);
        btnBox.setPadding(new Insets(0, 20, 12, 20));

        Button batchTestBtn = new Button("批量测试");
        batchTestBtn.setPrefWidth(90);
        batchTestBtn.setOnAction(e -> logger.info("批量测试代理"));

        Button enableBtn = new Button("开启");
        enableBtn.setPrefWidth(90);
        enableBtn.setOnAction(e -> logger.info("开启代理"));

        Button closeBtn = new Button("关闭");
        closeBtn.setPrefWidth(90);
        closeBtn.setOnAction(e -> proxyStage.close());

        btnBox.getChildren().addAll(batchTestBtn, enableBtn, closeBtn);
        root.getChildren().add(btnBox);

        // 底部提示
        Label bottomTip = new Label("网络正常不需要设置，不建议使用公共代理，不稳定影响抢票！");
        bottomTip.getStyleClass().add("proxy-bottom-tip");
        bottomTip.setWrapText(true);
        bottomTip.setMaxWidth(Double.MAX_VALUE);
        root.getChildren().add(bottomTip);

        Scene scene = new Scene(root, 560, 490);
        scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
        proxyStage.setScene(scene);
        proxyStage.show();

        logger.info("打开代理设置弹框");
    }

    // ==================== 赞助项目弹框 ====================

    private void showDonateDialog() {
        Stage donateStage = new Stage();
        donateStage.initModality(Modality.APPLICATION_MODAL);
        donateStage.initOwner(this);
        donateStage.setTitle("\u2764 赞助项目");
        donateStage.setResizable(false);

        VBox root = new VBox(0);
        root.getStyleClass().add("donate-root");

        // 顶部提示条（铺满宽度）
        Label topNotice = new Label("抢票助手完全免费，仅依靠用户赞助维持，赞助不等于有票，请勿盲目赞助，认为赞助了必须抢到票的请勿赞助。");
        topNotice.getStyleClass().add("donate-top-notice");
        topNotice.setWrapText(true);
        topNotice.setMaxWidth(Double.MAX_VALUE);
        root.getChildren().add(topNotice);

        // 主内容区（铺满宽度）
        VBox mainContent = new VBox(0);
        mainContent.setPadding(new Insets(32, 40, 32, 40));

        // 标题
        Label mainTitle = new Label("\u2764 感谢您的支持");
        mainTitle.getStyleClass().add("donate-main-title");
        mainContent.getChildren().add(mainTitle);

        // 说明文字
        Label descTitle = new Label("如果您在使用后感觉抢票助手帮助到了您，并愿意支持项目的开发及维护工作，可以使用微信或支付宝扫码赞助任意金额。");
        descTitle.setWrapText(true);
        descTitle.setMaxWidth(740);
        descTitle.getStyleClass().add("donate-desc");
        mainContent.getChildren().add(descTitle);

        // 间距
        Region spacer1 = new Region();
        spacer1.setPrefHeight(20);
        mainContent.getChildren().add(spacer1);

        // 操作步骤
        Label stepsTitle = new Label("操作步骤");
        stepsTitle.getStyleClass().add("donate-steps-title");
        mainContent.getChildren().add(stepsTitle);

        Label descSteps = new Label("1、微信或支付宝扫描下方二维码\n2、输入任意金额赞助即可");
        descSteps.setWrapText(true);
        descSteps.setMaxWidth(740);
        descSteps.getStyleClass().add("donate-desc");
        mainContent.getChildren().add(descSteps);

        // 间距
        Region spacer2 = new Region();
        spacer2.setPrefHeight(20);
        mainContent.getChildren().add(spacer2);

        // 注意
        Label descNote = new Label("\u26A0 注意：赞助不代表一定能抢到票，抢票成功率受多种因素影响，请理性赞助。");
        descNote.setWrapText(true);
        descNote.setMaxWidth(740);
        descNote.getStyleClass().add("donate-desc-note");
        mainContent.getChildren().add(descNote);

        // 间距
        Region spacer3 = new Region();
        spacer3.setPrefHeight(24);
        mainContent.getChildren().add(spacer3);

        // 二维码区域标题
        Label qrSectionTitle = new Label("扫码赞助");
        qrSectionTitle.getStyleClass().add("donate-qr-section-title");
        mainContent.getChildren().add(qrSectionTitle);

        // 两个二维码并排（左右对称居中）
        HBox qrRow = new HBox(0);
        qrRow.setAlignment(Pos.CENTER);
        qrRow.setPadding(new Insets(16, 0, 0, 0));

        // 支付宝（Logo样式：蓝色圆角方块+白字"支"+"支付宝"）
        VBox alipayBox = new VBox(12);
        alipayBox.setAlignment(Pos.CENTER);
        alipayBox.setPrefWidth(370);
        HBox alipayLogo = new HBox(6);
        alipayLogo.setAlignment(Pos.CENTER);
        Label alipayIcon = new Label("\u652F");
        alipayIcon.getStyleClass().add("donate-alipay-icon");
        Label alipayText = new Label("支付宝");
        alipayText.getStyleClass().add("donate-qr-label-alipay");
        alipayLogo.getChildren().addAll(alipayIcon, alipayText);
        ImageView alipayImage = new ImageView(getClass().getResource("/images/zhifubao_shoukuanma.jpg").toExternalForm());
        alipayImage.setFitWidth(220);
        alipayImage.setFitHeight(220);
        alipayImage.setPreserveRatio(true);
        alipayImage.getStyleClass().add("donate-qr-image");
        alipayBox.getChildren().addAll(alipayLogo, alipayImage);

        // 微信
        VBox wechatBox = new VBox(12);
        wechatBox.setAlignment(Pos.CENTER);
        wechatBox.setPrefWidth(370);
        Label wechatLabel = new Label("\uD83D\uDCAC 微信");
        wechatLabel.getStyleClass().add("donate-qr-label-wechat");
        ImageView wechatImage = new ImageView(getClass().getResource("/images/weixin_shoukuanma.jpg").toExternalForm());
        wechatImage.setFitWidth(220);
        wechatImage.setFitHeight(220);
        wechatImage.setPreserveRatio(true);
        wechatImage.getStyleClass().add("donate-qr-image");
        wechatBox.getChildren().addAll(wechatLabel, wechatImage);

        qrRow.getChildren().addAll(alipayBox, wechatBox);
        mainContent.getChildren().add(qrRow);

        root.getChildren().add(mainContent);

        Scene scene = new Scene(root, 900, 560);
        scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
        donateStage.setScene(scene);
        donateStage.show();

        logger.info("打开赞助项目弹框");
    }

    // ==================== 候补订单表格数据模型 ====================

    public static class WaitlistRowData {
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

    // ==================== 订单表格数据模型 ====================

    public static class OrderRowData {
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
