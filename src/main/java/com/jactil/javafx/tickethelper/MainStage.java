package com.jactil.javafx.tickethelper;

import com.jactil.javafx.tickethelper.component.StationAutoCompleteField;
import com.jactil.javafx.tickethelper.config.AccountConfig;
import com.jactil.javafx.tickethelper.config.AppConfig;
import com.jactil.javafx.tickethelper.model.UserInfo;
import com.jactil.javafx.tickethelper.service.TicketService;
import com.jactil.javafx.tickethelper.service.impl.TicketServiceImpl;
import com.jactil.javafx.tickethelper.util.HttpClientUtil;
import com.jactil.javafx.tickethelper.util.StationUtil;
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
import javafx.scene.text.Text;
import javafx.stage.Modality;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.File;
import java.io.RandomAccessFile;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;

/**
 * 主界面窗口（参考 Bypass 分流抢票设计）
 * 包含顶部工具栏 + 标签页（抢票/订单/候补）+ 查询区 + 结果表格 + 设置区 + 状态栏
 */
public class MainStage extends Stage {

    private static final Logger logger = LoggerFactory.getLogger(MainStage.class);

    private UserInfo currentUser;
    private Runnable onLogout;

    /** 当前账户配置（分账户隔离） */
    private AccountConfig currentAccountConfig;

    // 查询区域控件
    private StationAutoCompleteField fromStationField;
    private StationAutoCompleteField toStationField;
    private DatePicker datePicker;
    private ComboBox<String> departTimeCombo;
    private VBox logArea;  // 日志输出区（VBox 容纳彩色 Text 节点）
    private ScrollPane logScrollPane;

    // 日志实时读取
    private File currentLogFile;
    private long logPointer = 0;
    private Timeline logTailTimeline;
    private static final int MAX_LOG_LINES = 500;

    // 成人/学生票
    private CheckBox adultCheck;
    private CheckBox studentCheck;

    // 车次类型筛选
    private CheckBox filterAll, filterG, filterD, filterZ, filterT, filterK, filterOther;

    // 车次席别筛选
    private CheckBox hideAll, hideBusiness, hideFirstPlus, hideFirst, hideSecond,
            hideHighSoft, hideSoftSleeper, hideHardSleeper, hideSoftSeat, hideHardSeat, hideNoSeat, hideOtherSeat;

    // 只看有票车次
    private CheckBox onlyAvailableCheck;

    // 同城车站筛选按钮和弹出窗口
    private Button fromCityBtn;
    private Button toCityBtn;
    private Popup fromCityPopup;
    private Popup toCityPopup;
    /** 出发站同城筛选状态：城市名 -> 选中的车站名集合 */
    private Map<String, java.util.Set<String>> fromCityFilters = new HashMap<>();
    /** 到达站同城筛选状态 */
    private Map<String, java.util.Set<String>> toCityFilters = new HashMap<>();

    // 原始查询结果（用于前端筛选）
    private List<Map<String, String>> allTrainResults = new ArrayList<>();

    // 查询结果摘要提示
    private Label querySummaryLabel;

    // 中转换乘/票价链接
    private Label transferLink;
    private Label showAllPriceLink;

    // 票价显示状态
    private boolean showingPrice = false;
    // 保存原始席别数据（切换票价时备份）
    private Map<String, Map<String, String>> originalSeatDataMap = new HashMap<>();

    // 车票查询
    private TicketService ticketService;
    private TableView<TableRowData> ticketTableView;
    private static final String[] TICKET_COLUMNS = {"车次", "出发地", "目的地", "历时", "商务/特等", "优选一等座",
            "一等座", "二等座", "高级软卧", "软卧", "硬卧", "软座", "硬座", "无座", "其他", "日期", "备注"};
    private static final double[] TICKET_COLUMN_WIDTHS = {80, 90, 90, 70, 85, 85, 85, 85, 80, 70, 70, 65, 65, 65, 65, 90, 100};

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
        this.ticketService = new TicketServiceImpl();

        // 加载分账户配置
        initAccountConfig();

        // 按账号初始化独立日志文件
        initAccountLogFile();

        // 启动日志实时读取
        startLogTailing();

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

        // 窗口关闭时停止日志轮询
        setOnCloseRequest(e -> {
            if (logTailTimeline != null) {
                logTailTimeline.stop();
            }
        });

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

        // 查询结果摘要提示
        querySummaryLabel = new Label();
        querySummaryLabel.getStyleClass().add("query-summary");
        querySummaryLabel.setPadding(new Insets(4, 10, 2, 10));
        page.getChildren().add(querySummaryLabel);

        // 结果表格（vgrow=ALWAYS，占满剩余空间）
        VBox tableArea = createTableArea();
        VBox.setVgrow(tableArea, Priority.ALWAYS);
        page.getChildren().add(tableArea);

        // 设置区域（固定高度，不随窗口缩放）
        settingsArea = createSettingsArea();
        settingsArea.setPrefHeight(350);
        settingsArea.setMinHeight(350);
        settingsArea.setMaxHeight(350);
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
        fromStationField = new StationAutoCompleteField("出发站", 110);

        // 出发站同城车站筛选按钮
        fromCityBtn = createCityFilterButton("筛选出发的同城车站，避免买错，会记忆保存，取消筛选需重新勾选。");
        fromCityBtn.setOnAction(e -> showCityStationPopup(fromStationField, fromCityBtn, fromCityPopup, fromCityFilters, true));

        Button swapBtn = new Button("\u21C4");
        swapBtn.setPrefWidth(32);
        swapBtn.getStyleClass().add("btn-swap");
        swapBtn.setOnAction(e -> {
            String tmp = fromStationField.getText();
            fromStationField.setTextSilent(toStationField.getText());
            toStationField.setTextSilent(tmp);
            // 交换后重新显示同城筛选状态
            updateCityFilterButtonState(fromCityBtn, fromStationField.getText(), fromCityFilters);
            updateCityFilterButtonState(toCityBtn, toStationField.getText(), toCityFilters);
        });

        Label toLabel = new Label("目的:");
        toLabel.getStyleClass().add("query-label");
        toStationField = new StationAutoCompleteField("目的站", 110);

        // 到达站同城车站筛选按钮
        toCityBtn = createCityFilterButton("筛选到达的同城车站，避免买错，会记忆保存，取消筛选需重新勾选。");
        toCityBtn.setOnAction(e -> showCityStationPopup(toStationField, toCityBtn, toCityPopup, toCityFilters, false));

        // 初始化车站搜索历史（分账户）
        initStationHistory();

        // 加载同城车站筛选状态
        loadCityFilters();

        Label dateLabel = new Label("日期:");
        dateLabel.getStyleClass().add("query-label");
        Button datePrevBtn = new Button("<");
        datePrevBtn.getStyleClass().add("btn-date-nav");
        datePicker = new DatePicker(LocalDate.now());
        datePicker.setPrefWidth(150);
        datePicker.getStyleClass().add("query-datepicker");
        datePrevBtn.setOnAction(e -> {
            LocalDate current = datePicker.getValue();
            if (current != null) {
                datePicker.setValue(current.minusDays(1));
            }
        });
        Button dateNextBtn = new Button(">");
        dateNextBtn.getStyleClass().add("btn-date-nav");
        dateNextBtn.setOnAction(e -> {
            LocalDate current = datePicker.getValue();
            if (current != null) {
                datePicker.setValue(current.plusDays(1));
            }
        });

        Label timeLabel = new Label("发车时间:");
        timeLabel.getStyleClass().add("query-label");
        departTimeCombo = new ComboBox<>();
        departTimeCombo.setEditable(true);
        departTimeCombo.getItems().addAll("00:00-24:00", "00:00-06:00", "06:00-12:00", "12:00-18:00", "18:00-24:00");
        departTimeCombo.setValue("00:00-24:00");
        departTimeCombo.setPrefWidth(120);
        departTimeCombo.setOnAction(e -> applyFilters());

        formFields.getChildren().addAll(fromLabel, fromStationField, fromCityBtn, swapBtn, toLabel, toStationField, toCityBtn,
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

        // -- 第3行：车次类型 --
        HBox filterRow = new HBox(6);
        filterRow.setAlignment(Pos.CENTER_LEFT);
        Label filterLabel = new Label("车次类型:");
        filterLabel.getStyleClass().add("query-label");
        filterAll = new CheckBox("全部");
        filterAll.setSelected(true);
        filterG = new CheckBox("高铁/城际");
        filterG.setSelected(true);
        filterD = new CheckBox("动车");
        filterD.setSelected(true);
        filterZ = new CheckBox("Z直达");
        filterZ.setSelected(true);
        filterT = new CheckBox("T特快");
        filterT.setSelected(true);
        filterK = new CheckBox("K快速");
        filterK.setSelected(true);
        filterOther = new CheckBox("其他");
        filterOther.setSelected(true);
        filterAll.setOnAction(e -> {
            boolean selected = filterAll.isSelected();
            filterG.setSelected(selected);
            filterD.setSelected(selected);
            filterZ.setSelected(selected);
            filterT.setSelected(selected);
            filterK.setSelected(selected);
            filterOther.setSelected(selected);
            applyFilters();
        });
        // 车次类型筛选变化时触发过滤
        filterG.setOnAction(e -> { syncSelectAll(filterAll, filterG, filterD, filterZ, filterT, filterK, filterOther); applyFilters(); });
        filterD.setOnAction(e -> { syncSelectAll(filterAll, filterG, filterD, filterZ, filterT, filterK, filterOther); applyFilters(); });
        filterZ.setOnAction(e -> { syncSelectAll(filterAll, filterG, filterD, filterZ, filterT, filterK, filterOther); applyFilters(); });
        filterT.setOnAction(e -> { syncSelectAll(filterAll, filterG, filterD, filterZ, filterT, filterK, filterOther); applyFilters(); });
        filterK.setOnAction(e -> { syncSelectAll(filterAll, filterG, filterD, filterZ, filterT, filterK, filterOther); applyFilters(); });
        filterOther.setOnAction(e -> { syncSelectAll(filterAll, filterG, filterD, filterZ, filterT, filterK, filterOther); applyFilters(); });
        filterRow.getChildren().addAll(filterLabel, filterAll, filterG, filterD, filterZ, filterT, filterK, filterOther);

        // -- 第4行：车次席别 --
        HBox hideRow = new HBox(6);
        hideRow.setAlignment(Pos.CENTER_LEFT);
        Label hideLabel = new Label("车次席别:");
        hideLabel.getStyleClass().add("query-label");
        hideAll = new CheckBox("全选");
        hideAll.setSelected(true);
        hideBusiness = new CheckBox("商务/特等");
        hideBusiness.setSelected(true);
        hideFirstPlus = new CheckBox("优选一等座");
        hideFirstPlus.setSelected(true);
        hideFirst = new CheckBox("一等座");
        hideFirst.setSelected(true);
        hideSecond = new CheckBox("二等座");
        hideSecond.setSelected(true);
        hideHighSoft = new CheckBox("高软");
        hideHighSoft.setSelected(true);
        hideSoftSleeper = new CheckBox("软卧");
        hideSoftSleeper.setSelected(true);
        hideHardSleeper = new CheckBox("硬卧");
        hideHardSleeper.setSelected(true);
        hideSoftSeat = new CheckBox("软座");
        hideSoftSeat.setSelected(true);
        hideHardSeat = new CheckBox("硬座");
        hideHardSeat.setSelected(true);
        hideNoSeat = new CheckBox("无座");
        hideNoSeat.setSelected(true);
        hideOtherSeat = new CheckBox("其他");
        hideOtherSeat.setSelected(true);
        hideAll.setOnAction(e -> {
            boolean selected = hideAll.isSelected();
            hideBusiness.setSelected(selected);
            hideFirstPlus.setSelected(selected);
            hideFirst.setSelected(selected);
            hideSecond.setSelected(selected);
            hideHighSoft.setSelected(selected);
            hideSoftSleeper.setSelected(selected);
            hideHardSleeper.setSelected(selected);
            hideSoftSeat.setSelected(selected);
            hideHardSeat.setSelected(selected);
            hideNoSeat.setSelected(selected);
            hideOtherSeat.setSelected(selected);
            applyFilters();
        });
        // 席别筛选变化时触发过滤
        Runnable seatFilterAction = () -> { syncSelectAll(hideAll, hideBusiness, hideFirstPlus, hideFirst, hideSecond, hideHighSoft, hideSoftSleeper, hideHardSleeper, hideSoftSeat, hideHardSeat, hideNoSeat, hideOtherSeat); applyFilters(); };
        hideBusiness.setOnAction(e -> seatFilterAction.run());
        hideFirstPlus.setOnAction(e -> seatFilterAction.run());
        hideFirst.setOnAction(e -> seatFilterAction.run());
        hideSecond.setOnAction(e -> seatFilterAction.run());
        hideHighSoft.setOnAction(e -> seatFilterAction.run());
        hideSoftSleeper.setOnAction(e -> seatFilterAction.run());
        hideHardSleeper.setOnAction(e -> seatFilterAction.run());
        hideSoftSeat.setOnAction(e -> seatFilterAction.run());
        hideHardSeat.setOnAction(e -> seatFilterAction.run());
        hideNoSeat.setOnAction(e -> seatFilterAction.run());
        hideOtherSeat.setOnAction(e -> seatFilterAction.run());
        hideRow.getChildren().addAll(hideLabel, hideAll, hideBusiness, hideFirstPlus, hideFirst, hideSecond,
                hideHighSoft, hideSoftSleeper, hideHardSleeper, hideSoftSeat, hideHardSeat, hideNoSeat, hideOtherSeat);

        leftRows.getChildren().addAll(formFields, modeRow, filterRow, hideRow);

        // 右侧：操作框（固定宽度，高度自动匹配左侧4行）
        adultCheck = new CheckBox("成人");
        adultCheck.setSelected(true);
        studentCheck = new CheckBox("学生");
        // 成人/学生互斥
        adultCheck.setOnAction(e -> {
            if (adultCheck.isSelected()) studentCheck.setSelected(false);
            else if (!studentCheck.isSelected()) adultCheck.setSelected(true); // 至少选一个
        });
        studentCheck.setOnAction(e -> {
            if (studentCheck.isSelected()) adultCheck.setSelected(false);
            else if (!adultCheck.isSelected()) studentCheck.setSelected(true); // 至少选一个
        });
        transferLink = new Label("查询中转换乘");
        transferLink.getStyleClass().add("link-blue");
        transferLink.setCursor(javafx.scene.Cursor.HAND);
        transferLink.setOnMouseClicked(e -> doOpenTransferPage());
        onlyAvailableCheck = new CheckBox("只看有票的车次");
        onlyAvailableCheck.setOnAction(e -> applyFilters());
        showAllPriceLink = new Label("显示全部票价");
        showAllPriceLink.getStyleClass().add("link-blue");
        showAllPriceLink.setCursor(javafx.scene.Cursor.HAND);
        // 鼠标悬浮提示
        Tooltip priceTip = new Tooltip("查询的是实际票价，显示的卧铺票价均为上铺票价。\n鼠标放到硬卧或者软卧等价格上时会显示其他价格。\n候补卧铺时，先按最高价下铺票价收取，多了会退。\n具体票价以您确认支付时实际购买的铺别票价为准。");
        priceTip.setWrapText(true);
        priceTip.setPrefWidth(320);
        Tooltip.install(showAllPriceLink, priceTip);
        showAllPriceLink.setOnMouseClicked(e -> doTogglePriceDisplay());

        Button queryBtn = new Button("查询车票");
        queryBtn.getStyleClass().add("btn-query");
        queryBtn.setPrefWidth(80);
        queryBtn.setPrefHeight(52);
        queryBtn.setOnAction(e -> doQueryTickets());

        Button clearBtn = new Button("清空查询");
        clearBtn.getStyleClass().add("btn-query");
        clearBtn.setPrefWidth(80);
        clearBtn.setPrefHeight(36);
        clearBtn.setOnAction(e -> doClearTickets());

        VBox btnBox = new VBox(8);
        btnBox.getChildren().addAll(queryBtn, clearBtn);

        VBox opCheckboxes = new VBox(12);
        HBox opRow1 = new HBox(8);
        opRow1.setAlignment(Pos.CENTER_LEFT);
        opRow1.getChildren().addAll(adultCheck, studentCheck, transferLink);
        HBox opRow2 = new HBox(8);
        opRow2.setAlignment(Pos.CENTER_LEFT);
        opRow2.getChildren().addAll(onlyAvailableCheck, showAllPriceLink);
        opCheckboxes.getChildren().addAll(opRow1, opRow2);

        VBox opBox = new VBox(3);
        opBox.setPadding(new Insets(12, 16, 14, 16));
        opBox.getStyleClass().add("op-group-box");
        opBox.setPrefWidth(340);
        opBox.setMaxWidth(340);

        Label opTitle = new Label("操作");
        opTitle.getStyleClass().add("op-group-title");

        HBox opContent = new HBox(16);
        opContent.setAlignment(Pos.CENTER_LEFT);
        VBox.setVgrow(opCheckboxes, Priority.ALWAYS);
        opContent.getChildren().addAll(opCheckboxes, btnBox);

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
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        // 隐藏原生表头，列名作为第一行数据渲染，保证表头和数据使用同一套单元格布局
        // 通过 CSS 隐藏表头（.column-header-background 高度设为 0）
        ticketTableView = tableView;

        for (int i = 0; i < TICKET_COLUMNS.length; i++) {
            final String colName = TICKET_COLUMNS[i];
            TableColumn<TableRowData, String> col = new TableColumn<>(colName);
            col.setCellValueFactory(data -> data.getValue().getProperty(colName));
            col.setPrefWidth(TICKET_COLUMN_WIDTHS[i]);
            // 备注列使用按钮样式
            if ("备注".equals(colName)) {
                col.setCellFactory(column -> new TableCell<TableRowData, String>() {
                    @Override
                    protected void updateItem(String item, boolean empty) {
                        super.updateItem(item, empty);
                        if (empty || item == null) {
                            setText(null);
                            getStyleClass().remove("btn-book");
                        } else {
                            setText(item);
                            if (!getStyleClass().contains("btn-book")) {
                                getStyleClass().add("btn-book");
                            }
                        }
                    }
                });
            }
            tableView.getColumns().add(col);
        }

        // 添加表头行作为第一行数据
        TableRowData headerRow = new TableRowData();
        for (String colName : TICKET_COLUMNS) {
            headerRow.setProperty(colName, colName);
        }
        headerRow.setHeaderRow(true);
        tableView.getItems().add(headerRow);

        tableView.setPlaceholder(new Label("暂无查询结果，请输入出发站和目的站后点击\"查询车票\""));
        VBox.setVgrow(tableView, Priority.ALWAYS);
        tableBox.getChildren().add(tableView);

        return tableBox;
    }

    // ==================== 车票查询 ====================

    private void doQueryTickets() {
        String fromStation = fromStationField.getText();
        String toStation = toStationField.getText();
        LocalDate date = datePicker.getValue();

        if (fromStation == null || fromStation.trim().isEmpty()) {
            logger.info("请输入出发站");
            return;
        }
        if (toStation == null || toStation.trim().isEmpty()) {
            logger.info("请输入目的站");
            return;
        }
        if (date == null) {
            logger.info("请选择出发日期");
            return;
        }

        String trainDate = date.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE);

        // 保存车站到搜索历史（分账户）
        saveStationHistory(fromStation.trim(), toStation.trim());

        // 根据成人/学生票选择 purpose_codes
        String purposeCodes = studentCheck.isSelected() ? "0X00" : "ADULT";
        logger.info("开始查询车票：{} -> {}, 日期={}, 类型={}", fromStation, toStation, trainDate, purposeCodes);

        // 清空表格（保留表头行）
        if (ticketTableView != null) {
            ticketTableView.getItems().removeIf(row -> !row.isHeaderRow());
        }

        // 注册回调
        ((TicketServiceImpl) ticketService).addCallback(new TicketServiceImpl.QueryCallback() {
            @Override
            public void onResult(List<Map<String, String>> results) {
                javafx.application.Platform.runLater(() -> {
                    if (ticketTableView == null) return;
                    // 保存原始结果用于后续筛选
                    allTrainResults = new ArrayList<>(results);
                    // 应用当前筛选条件
                    applyFilters();
                    logger.info("查询到 {} 条记录，筛选后显示 {} 条", results.size(),
                            ticketTableView.getItems().size() - 1);
                });
            }

            @Override
            public void onError(String message) {
                javafx.application.Platform.runLater(() ->
                        logger.error("查询失败：{}", message)
                );
            }
        });

        // 异步查询
        new Thread(() -> ticketService.queryTickets(fromStation, toStation, trainDate, purposeCodes), "ticket-query").start();
    }

    // 清空查询数据（保留表头行）
    private void doClearTickets() {
        if (ticketTableView != null) {
            ticketTableView.getItems().removeIf(row -> !row.isHeaderRow());
            allTrainResults.clear();
            originalSeatDataMap.clear();
            showingPrice = false;
            if (showAllPriceLink != null) showAllPriceLink.setText("显示全部票价");
            logger.info("已清空查询数据");
        }
    }

    /**
     * 查询中转换乘：使用 Selenium 打开浏览器并注入 Cookie 保持登录态
     */
    private void doOpenTransferPage() {
        String fromStation = fromStationField.getText();
        String toStation = toStationField.getText();
        LocalDate date = datePicker.getValue();

        if (fromStation == null || fromStation.trim().isEmpty() || toStation == null || toStation.trim().isEmpty()) {
            logger.info("请先输入出发站和目的站");
            return;
        }
        if (date == null) {
            logger.info("请选择出发日期");
            return;
        }

        String fromCode = com.jactil.javafx.tickethelper.util.StationUtil.findStationCode(fromStation.trim());
        String toCode = com.jactil.javafx.tickethelper.util.StationUtil.findStationCode(toStation.trim());
        String dateStr = date.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE);

        String url = "https://kyfw.12306.cn/otn/lcQuery/init?linktypeid=lx"
                + "&fs=" + java.net.URLEncoder.encode(fromStation.trim() + "," + fromCode, java.nio.charset.StandardCharsets.UTF_8)
                + "&ts=" + java.net.URLEncoder.encode(toStation.trim() + "," + toCode, java.nio.charset.StandardCharsets.UTF_8)
                + "&date=" + dateStr
                + "&flag=N,N,Y";

        logger.info("打开中转换乘页面：{}", url);

        // 使用 Selenium 启动浏览器并注入 Cookie（与 doOpen12306 相同模式）
        new Thread(() -> {
            org.openqa.selenium.WebDriver driver = null;
            java.util.List<okhttp3.Cookie> okCookies =
                    com.jactil.javafx.tickethelper.util.HttpClientUtil.getAllCookies();
            String sessionId = java.util.UUID.randomUUID().toString().substring(0, 8);
            String tmpDir = System.getProperty("java.io.tmpdir") + "/tickethelper-transfer-" + sessionId;

            String[] browsers = {"edge", "chrome"};
            for (String browser : browsers) {
                try {
                    if ("edge".equals(browser)) {
                        org.openqa.selenium.edge.EdgeOptions options = new org.openqa.selenium.edge.EdgeOptions();
                        options.addArguments("--user-data-dir=" + tmpDir);
                        options.addArguments("--no-first-run", "--no-default-browser-check");
                        options.addArguments("--disable-blink-features=AutomationControlled");
                        options.setExperimentalOption("excludeSwitches", java.util.Arrays.asList("enable-automation"));
                        driver = new org.openqa.selenium.edge.EdgeDriver(options);
                    } else {
                        org.openqa.selenium.chrome.ChromeOptions options = new org.openqa.selenium.chrome.ChromeOptions();
                        options.addArguments("--user-data-dir=" + tmpDir);
                        options.addArguments("--no-first-run", "--no-default-browser-check");
                        options.addArguments("--disable-blink-features=AutomationControlled");
                        options.setExperimentalOption("excludeSwitches", java.util.Arrays.asList("enable-automation"));
                        driver = new org.openqa.selenium.chrome.ChromeDriver(options);
                    }
                    break;
                } catch (Exception e) {
                    logger.warn("[中转换乘] {} 启动失败: {}", browser, e.getMessage());
                    if (driver != null) { try { driver.quit(); } catch (Exception ignored) {}
                        driver = null; }
                }
            }

            if (driver == null) {
                // Selenium 全部失败，尝试直接启动 Edge 并通过 CDP 注入 Cookie
                boolean cdpSuccess = tryLaunchEdgeWithCdp(url, okCookies);
                if (!cdpSuccess) {
                    // 最终回退：系统默认浏览器（无登录态）
                    try {
                        java.awt.Desktop.getDesktop().browse(java.net.URI.create(url));
                    } catch (Exception ex) {
                        logger.error("[中转换乘] 打开浏览器失败", ex);
                    }
                    javafx.application.Platform.runLater(() -> {
                        Alert alert = new Alert(Alert.AlertType.INFORMATION);
                        alert.setTitle("提示");
                        alert.setHeaderText(null);
                        alert.setContentText("已使用系统默认浏览器打开中转换乘页面。\n\n⚠ 由于浏览器驱动无法启动，无法自动注入登录信息。\n请在打开的浏览器中手动登录12306账号后，页面将自动加载。\n\n💡 建议：确保 Edge 或 Chrome 浏览器已安装，\n   并保持网络连接以自动下载浏览器驱动。");
                        alert.showAndWait();
                    });
                }
                return;
            }

            try {
                // 先访问 12306 域名以设置 Cookie
                driver.get("https://kyfw.12306.cn");
                org.openqa.selenium.WebDriver.Options manage = driver.manage();
                for (okhttp3.Cookie c : okCookies) {
                    try {
                        manage.addCookie(new org.openqa.selenium.Cookie(
                                c.name(), c.value(),
                                c.domain() != null ? c.domain() : ".12306.cn",
                                "/", new java.util.Date(c.expiresAt()), c.secure(), c.httpOnly()));
                    } catch (Exception e) {
                        logger.debug("[中转换乘] Cookie 注入失败 [{}]: {}", c.name(), e.getMessage());
                    }
                }
                // 导航到中转换乘页面
                driver.get(url);
                logger.info("[中转换乘] 页面已打开");
            } catch (Exception e) {
                logger.error("[中转换乘] 操作失败", e);
                try { driver.quit(); } catch (Exception ignored) {}
            }
        }, "transfer-browser").start();
    }

    /**
     * 尝试直接启动 Edge 浏览器并通过 CDP 注入 Cookie
     * 绕过 Selenium Manager 的驱动下载，直接连接已安装的 Edge
     */
    private boolean tryLaunchEdgeWithCdp(String url, java.util.List<okhttp3.Cookie> okCookies) {
        try {
            // 查找 Edge 浏览器可执行文件
            String edgePath = findEdgeExecutable();
            if (edgePath == null) {
                logger.info("[中转换乘] 未找到 Edge 浏览器");
                return false;
            }

            int debugPort = 9222;
            logger.info("[中转换乘] 尝试通过 CDP 启动 Edge (端口={})", debugPort);

            // 启动 Edge 并开启远程调试端口
            ProcessBuilder pb = new ProcessBuilder(
                    edgePath,
                    "--remote-debugging-port=" + debugPort,
                    "--user-data-dir=" + System.getProperty("java.io.tmpdir") + "/tickethelper-cdp-" + java.util.UUID.randomUUID().toString().substring(0, 8),
                    "--no-first-run",
                    "--no-default-browser-check",
                    url
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // 等待浏览器启动
            Thread.sleep(3000);

            // 通过 CDP 注入 Cookie
            injectCookiesViaCdp(debugPort, okCookies, url);

            logger.info("[中转换乘] Edge CDP 模式启动成功");
            return true;
        } catch (Exception e) {
            logger.warn("[中转换乘] Edge CDP 启动失败: {}", e.getMessage());
            return false;
        }
    }

    /** 查找 Edge 浏览器可执行文件路径 */
    private String findEdgeExecutable() {
        String[] candidates = {
                System.getenv("ProgramFiles(x86)") + "\\Microsoft\\Edge\\Application\\msedge.exe",
                System.getenv("ProgramFiles") + "\\Microsoft\\Edge\\Application\\msedge.exe",
                System.getenv("LOCALAPPDATA") + "\\Microsoft\\Edge\\Application\\msedge.exe"
        };
        for (String path : candidates) {
            if (path != null && java.nio.file.Files.exists(java.nio.file.Paths.get(path))) {
                return path;
            }
        }
        return null;
    }

    /** 通过 CDP 协议向浏览器注入 Cookie */
    private void injectCookiesViaCdp(int port, java.util.List<okhttp3.Cookie> okCookies, String targetUrl) {
        try {
            String cookieJson = buildCdpCookieJson(okCookies);
            String cdpUrl = "http://localhost:" + port + "/json/new?about:blank";
            // 先创建一个空白页获取 wsUrl
            String newPageResponse = HttpClientUtil.get(cdpUrl);
            if (newPageResponse != null) {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(newPageResponse);
                String wsUrl = node.path("webSocketDebuggerUrl").asText();
                if (!wsUrl.isEmpty()) {
                    // 通过 WebSocket 发送 CDP 命令注入 Cookie
                    final java.util.concurrent.CountDownLatch wsLatch = new java.util.concurrent.CountDownLatch(1);
                    java.net.http.WebSocket ws = java.net.http.HttpClient.newHttpClient().newWebSocketBuilder()
                            .buildAsync(java.net.URI.create(wsUrl), new java.net.http.WebSocket.Listener() {
                                @Override
                                public void onOpen(java.net.http.WebSocket ws) {
                                    String setCookieCmd = "{\"id\":1,\"method\":\"Network.setCookies\",\"params\":{\"cookies\":" + cookieJson + "}}";
                                    ws.sendText(setCookieCmd, true);
                                    ws.request(1);
                                }
                                @Override
                                public java.util.concurrent.CompletionStage<?> onText(java.net.http.WebSocket ws, CharSequence data, boolean last) {
                                    // 收到响应后导航到目标页面
                                    String navigateCmd = "{\"id\":2,\"method\":\"Page.navigate\",\"params\":{\"url\":\"" + targetUrl.replace("\"", "\\\"") + "\"}}";
                                    ws.sendText(navigateCmd, true);
                                    ws.request(1);
                                    wsLatch.countDown();
                                    return java.util.concurrent.CompletableFuture.completedFuture(null);
                                }
                                @Override
                                public void onError(java.net.http.WebSocket ws, Throwable error) {
                                    logger.debug("[中转换乘] CDP WebSocket 错误: {}", error.getMessage());
                                    wsLatch.countDown();
                                }
                            }).get(5, java.util.concurrent.TimeUnit.SECONDS);
                    ws.request(1);
                    // 等待 Cookie 注入和页面导航完成
                    wsLatch.await(5, java.util.concurrent.TimeUnit.SECONDS);
                    ws.sendClose(java.net.http.WebSocket.NORMAL_CLOSURE, "done");
                }
            }
        } catch (Exception e) {
            logger.debug("[中转换乘] CDP Cookie 注入失败: {}", e.getMessage());
        }
    }

    /** 构建 CDP setCookies 命令所需的 Cookie JSON */
    private String buildCdpCookieJson(java.util.List<okhttp3.Cookie> okCookies) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (okhttp3.Cookie c : okCookies) {
            if (!first) sb.append(",");
            first = false;
            sb.append("{\"name\":\"").append(escapeJson(c.name())).append("\"");
            sb.append(",\"value\":\"").append(escapeJson(c.value())).append("\"");
            sb.append(",\"domain\":\"").append(escapeJson(c.domain() != null ? c.domain() : ".12306.cn")).append("\"");
            sb.append(",\"path\":\"/\"");
            sb.append(",\"secure\":").append(c.secure());
            sb.append(",\"httpOnly\":").append(c.httpOnly());
            if (c.expiresAt() > 0) {
                sb.append(",\"expires\":").append(c.expiresAt() / 1000);
            }
            sb.append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    /**
     * 切换余票/票价显示
     */
    private void doTogglePriceDisplay() {
        if (allTrainResults.isEmpty()) {
            logger.warn("请先查询车票");
            return;
        }

        if (!showingPrice) {
            // 切换到票价显示：先备份原始数据，再查询票价
            showingPrice = true;
            showAllPriceLink.setText("恢复显示余票");

            // 备份当前表格中的原始席别数据
            originalSeatDataMap.clear();
            for (TableRowData row : ticketTableView.getItems()) {
                if (row.isHeaderRow()) continue;
                String trainNo = row.getProperty("车次").get();
                Map<String, String> seatBackup = new HashMap<>();
                for (String seatCol : SEAT_COLUMNS) {
                    seatBackup.put(seatCol, row.getProperty(seatCol).get());
                }
                originalSeatDataMap.put(trainNo, seatBackup);
            }

            // 异步查询票价
            new Thread(() -> doQueryPrices(), "price-query").start();
        } else {
            // 切换回余票显示
            showingPrice = false;
            showAllPriceLink.setText("显示全部票价");

            // 恢复原始席别数据
            javafx.application.Platform.runLater(() -> {
                for (TableRowData row : ticketTableView.getItems()) {
                    if (row.isHeaderRow()) continue;
                    String trainNo = row.getProperty("车次").get();
                    Map<String, String> backup = originalSeatDataMap.get(trainNo);
                    if (backup != null) {
                        for (Map.Entry<String, String> entry : backup.entrySet()) {
                            row.setProperty(entry.getKey(), entry.getValue());
                        }
                    }
                }
                logger.info("已恢复余票显示");
            });
        }
    }

    /** 席别列名列表 */
    private static final String[] SEAT_COLUMNS = {"商务/特等", "优选一等座", "一等座", "二等座", "高级软卧", "软卧", "硬卧", "软座", "硬座", "无座"};

    /**
     * 显示票价（从已解析的原始数据中直接获取，无需额外API调用）
     */
    private void doQueryPrices() {
        try {
            final boolean[] hasAnyPrice = {false};
            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
            javafx.application.Platform.runLater(() -> {
                try {
                    for (TableRowData row : ticketTableView.getItems()) {
                        if (row.isHeaderRow()) continue;
                        String trainNo = row.getProperty("车次").get();
                        // 从 allTrainResults 中找到对应的原始数据（含票价）
                        for (Map<String, String> train : allTrainResults) {
                            if (trainNo.equals(train.get("车次"))) {
                                // 遍历所有席别列，检查是否有票价数据
                                for (String seatCol : SEAT_COLUMNS) {
                                    String priceVal = train.get("_price_" + seatCol);
                                    if (priceVal != null) {
                                        row.setProperty(seatCol, priceVal);
                                        hasAnyPrice[0] = true;
                                    } else {
                                        row.setProperty(seatCol, "--");
                                    }
                                }
                                break;
                            }
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
            latch.await();
            if (hasAnyPrice[0]) {
                javafx.application.Platform.runLater(() -> logger.info("票价显示完成（从查询结果中直接解析）"));
            } else {
                javafx.application.Platform.runLater(() -> {
                    logger.info("票价显示完成，但未找到任何票价数据");
                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle("提示");
                    alert.setHeaderText(null);
                    alert.setContentText("当前查询结果中没有票价数据。\n\n可能原因：\n1. 该车次尚未开售，12306 暂未返回票价信息\n2. 该车次已停运或无票价数据\n\n请在开售后重新查询。");
                    alert.showAndWait();
                });
            }
        } catch (Exception e) {
            logger.error("显示票价异常", e);
        }
    }

    /**
     * 应用前端筛选条件（车次类型 + 席别 + 发车时间）
     * 从 allTrainResults 中过滤后刷新表格
     */
    private void applyFilters() {
        if (ticketTableView == null) return;

        // 清除旧数据行（保留表头行）
        ticketTableView.getItems().removeIf(row -> !row.isHeaderRow());

        if (allTrainResults.isEmpty()) return;

        // 1. 发车时间筛选
        String timeRange = departTimeCombo.getValue();
        int filterStartMin = 0, filterEndMin = 24 * 60;
        if (timeRange != null && !"00:00-24:00".equals(timeRange)) {
            String[] parts = timeRange.split("-");
            if (parts.length == 2) {
                filterStartMin = parseTimeToMinutes(parts[0]);
                filterEndMin = parseTimeToMinutes(parts[1]);
            }
        }

        // 2. 车次类型筛选
        boolean showG = filterG.isSelected();
        boolean showD = filterD.isSelected();
        boolean showZ = filterZ.isSelected();
        boolean showT = filterT.isSelected();
        boolean showK = filterK.isSelected();
        boolean showOther = filterOther.isSelected();

        // 3. 席别筛选：选中的席别表示要显示该席别有票的车次
        boolean seatBusiness = hideBusiness.isSelected();
        boolean seatFirstPlus = hideFirstPlus.isSelected();
        boolean seatFirst = hideFirst.isSelected();
        boolean seatSecond = hideSecond.isSelected();
        boolean seatHighSoft = hideHighSoft.isSelected();
        boolean seatSoftSleeper = hideSoftSleeper.isSelected();
        boolean seatHardSleeper = hideHardSleeper.isSelected();
        boolean seatSoftSeat = hideSoftSeat.isSelected();
        boolean seatHardSeat = hideHardSeat.isSelected();
        boolean seatNoSeat = hideNoSeat.isSelected();
        // 如果所有席别都选中，则不过滤（显示所有）
        boolean filterBySeat = !(seatBusiness && seatFirstPlus && seatFirst && seatSecond
                && seatHighSoft && seatSoftSleeper && seatHardSleeper
                && seatSoftSeat && seatHardSeat && seatNoSeat);

        int count = 0;
        for (Map<String, String> train : allTrainResults) {
            // 车次类型过滤
            String trainNo = train.getOrDefault("车次", "");
            if (!matchTrainType(trainNo, showG, showD, showZ, showT, showK, showOther)) {
                continue;
            }

            // 发车时间过滤（从"出发地"字段提取时间，格式"站名 HH:mm"）
            String departStr = train.getOrDefault("出发地", "");
            int departMin = extractDepartMinutes(departStr);
            if (departMin >= 0) {
                if (filterEndMin <= filterStartMin) {
                    // 跨午夜时段，如 18:00-24:00 或 18:00-06:00
                    if (departMin < filterStartMin && departMin >= filterEndMin) continue;
                } else {
                    if (departMin < filterStartMin || departMin >= filterEndMin) continue;
                }
            }

            // 同城车站筛选：检查出发站和到达站是否在筛选列表中
            if (!fromCityFilters.isEmpty() || !toCityFilters.isEmpty()) {
                // 从"出发地"提取站名（格式"站名 HH:mm"）
                String fromStationInTrain = departStr.contains(" ") ? departStr.substring(0, departStr.indexOf(" ")).trim() : departStr.trim();
                // 从"目的地"提取站名
                String arriveStr = train.getOrDefault("目的地", "");
                String toStationInTrain = arriveStr.contains(" ") ? arriveStr.substring(0, arriveStr.indexOf(" ")).trim() : arriveStr.trim();

                // 检查出发站
                if (!fromCityFilters.isEmpty()) {
                    String fromCityCode = StationUtil.findStationCityCode(fromStationInTrain);
                    java.util.Set<String> allowedFrom = fromCityFilters.get(fromCityCode);
                    if (allowedFrom != null && !allowedFrom.isEmpty() && !allowedFrom.contains(fromStationInTrain)) {
                        continue; // 出发站不在筛选列表中
                    }
                }
                // 检查到达站
                if (!toCityFilters.isEmpty()) {
                    String toCityCode = StationUtil.findStationCityCode(toStationInTrain);
                    java.util.Set<String> allowedTo = toCityFilters.get(toCityCode);
                    if (allowedTo != null && !allowedTo.isEmpty() && !allowedTo.contains(toStationInTrain)) {
                        continue; // 到达站不在筛选列表中
                    }
                }
            }

            // 只看有票车次过滤：至少有一个席别显示"有"或数字的车次才保留
            if (onlyAvailableCheck != null && onlyAvailableCheck.isSelected()) {
                boolean hasAnyTicket = false;
                for (String seatCol : SEAT_COLUMNS) {
                    if (hasTicket(train.get(seatCol))) {
                        hasAnyTicket = true;
                        break;
                    }
                }
                if (!hasAnyTicket) continue;
            }

            // 席别过滤：至少有一个选中的席别显示"有票"
            if (filterBySeat) {
                boolean hasAvailableSeat = false;
                if (seatBusiness && hasTicket(train.get("商务/特等"))) hasAvailableSeat = true;
                if (seatFirstPlus && hasTicket(train.get("优选一等座"))) hasAvailableSeat = true;
                if (seatFirst && hasTicket(train.get("一等座"))) hasAvailableSeat = true;
                if (seatSecond && hasTicket(train.get("二等座"))) hasAvailableSeat = true;
                if (seatHighSoft && hasTicket(train.get("高级软卧"))) hasAvailableSeat = true;
                if (seatSoftSleeper && hasTicket(train.get("软卧"))) hasAvailableSeat = true;
                if (seatHardSleeper && hasTicket(train.get("硬卧"))) hasAvailableSeat = true;
                if (seatSoftSeat && hasTicket(train.get("软座"))) hasAvailableSeat = true;
                if (seatHardSeat && hasTicket(train.get("硬座"))) hasAvailableSeat = true;
                if (seatNoSeat && hasTicket(train.get("无座"))) hasAvailableSeat = true;
                if (!hasAvailableSeat) continue;
            }

            TableRowData row = new TableRowData();
            train.forEach(row::setProperty);
            ticketTableView.getItems().add(row);
            count++;
        }

        logger.debug("筛选后显示 {}/{} 条记录", count, allTrainResults.size());

        // 更新摘要提示：深圳北 --> 广州南（6月16日 周二）共计279个车次
        if (querySummaryLabel != null) {
            String from = fromStationField.getText();
            String to = toStationField.getText();
            LocalDate date = datePicker.getValue();
            String dayOfWeek = "";
            if (date != null) {
                java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("M月d日 EEE", java.util.Locale.CHINESE);
                dayOfWeek = date.format(fmt);
            }
            String summary = String.format("%s --> %s（%s）共计%d个车次", from, to, dayOfWeek, count);
            querySummaryLabel.setText(summary);
        }

        // 如果处于票价显示模式，筛选后需要重新查询票价
        if (showingPrice && count > 0) {
            new Thread(() -> doQueryPrices(), "price-query-filter").start();
        }
    }

    /**
     * 判断车次是否匹配选中的车次类型
     */
    private boolean matchTrainType(String trainNo, boolean showG, boolean showD, boolean showZ,
                                   boolean showT, boolean showK, boolean showOther) {
        if (trainNo == null || trainNo.isEmpty()) return showOther;
        char first = trainNo.charAt(0);
        switch (first) {
            case 'G': case 'C': return showG; // 高铁/城际
            case 'D': return showD;             // 动车
            case 'Z': return showZ;             // Z直达
            case 'T': return showT;             // T特快
            case 'K': return showK;             // K快速
            default: return showOther;          // 其他（普快等）
        }
    }

    /**
     * 从"出发地"字段提取出发时间（分钟数）
     * 出发地格式："站名 HH:mm"
     */
    private int extractDepartMinutes(String departStr) {
        if (departStr == null || departStr.isEmpty()) return -1;
        int spaceIdx = departStr.lastIndexOf(' ');
        if (spaceIdx < 0) return -1;
        String timeStr = departStr.substring(spaceIdx + 1).trim();
        return parseTimeToMinutes(timeStr);
    }

    /**
     * 将 "HH:mm" 格式时间转为分钟数
     */
    private int parseTimeToMinutes(String timeStr) {
        try {
            String[] parts = timeStr.split(":");
            return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * 判断席别是否有票（"有" 或 数字 > 0）
     */
    private boolean hasTicket(String seatVal) {
        if (seatVal == null || seatVal.isEmpty() || "无".equals(seatVal) || "--".equals(seatVal)) return false;
        if ("有".equals(seatVal)) return true;
        try {
            return Integer.parseInt(seatVal) > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * 同步"全选/全部"复选框状态：当所有子项都选中时自动勾选全选，否则取消全选
     */
    private void syncSelectAll(CheckBox selectAllBox, CheckBox... items) {
        boolean allSelected = true;
        for (CheckBox cb : items) {
            if (!cb.isSelected()) { allSelected = false; break; }
        }
        selectAllBox.setSelected(allSelected);
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

        // 设置内容区：三列布局（抢票设置 | 输出区 | 通用设置+其他设置）
        HBox contentBox = new HBox(0);
        contentBox.setPadding(new Insets(4, 8, 8, 8));

        // ===== 第1列：抢票设置 TabPane（固定宽度，不伸缩） =====
        VBox leftPanel = new VBox(0);
        leftPanel.getStyleClass().add("settings-panel-border");
        leftPanel.setPrefWidth(520);
        leftPanel.setMinWidth(480);

        TabPane settingsTabPane = new TabPane();
        settingsTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Tab grabTab = new Tab("抢票设置");
        grabTab.setContent(createGrabSettings());
        Tab queryTab = new Tab("查询起售");
        queryTab.setContent(createPlaceholderContent("查询起售功能（待实现）"));
        Tab tencentTab = new Tab("腾讯通知");
        tencentTab.setContent(createPlaceholderContent("腾讯通知功能（待实现）"));
        Tab emailTab = new Tab("邮件通知");
        emailTab.setContent(createEmailSettings());
        Tab wechatTab = new Tab("微信通知");
        wechatTab.setContent(createWechatSettings());
        Tab autoPayTab = new Tab("自动支付");
        autoPayTab.setContent(createAutoPaySettings());
        Tab multiTaskTab = new Tab("多任务设置");
        multiTaskTab.setContent(createPlaceholderContent("多任务设置功能（待实现）"));

        settingsTabPane.getTabs().addAll(grabTab, queryTab, tencentTab, emailTab, wechatTab, autoPayTab, multiTaskTab);
        leftPanel.getChildren().add(settingsTabPane);

        // ===== 第2列：输出区（唯一伸缩列） =====
        VBox middlePanel = new VBox(0);
        middlePanel.getStyleClass().add("settings-panel-border");
        middlePanel.setMinWidth(200);
        HBox.setHgrow(middlePanel, Priority.ALWAYS);

        // 输出区标题栏（输出区在左，查找日志在最右）
        HBox logTitleBar = new HBox();
        logTitleBar.setAlignment(Pos.CENTER_LEFT);
        logTitleBar.setPadding(new Insets(6, 8, 4, 8));
        logTitleBar.getStyleClass().add("log-title-bar");
        Label logTitleLabel = new Label("输出区");
        logTitleLabel.getStyleClass().add("section-title");
        Region logSpacer = new Region();
        HBox.setHgrow(logSpacer, Priority.ALWAYS);
        Label findLogLink = new Label("查找日志");
        findLogLink.getStyleClass().add("link-blue");
        findLogLink.setOnMouseClicked(e -> openLogDirectory());
        logTitleBar.getChildren().addAll(logTitleLabel, logSpacer, findLogLink);

        logArea = new VBox(0);
        logArea.getStyleClass().add("log-area");
        logArea.setPadding(new Insets(6));

        // 初始提示文字
        Text waitText = new Text("等待查询...\n");
        waitText.setFill(javafx.scene.paint.Color.BLUE);
        logArea.getChildren().add(waitText);

        // 用 ScrollPane 包裹实现滚动
        logScrollPane = new ScrollPane(logArea);
        logScrollPane.setFitToWidth(true);
        logScrollPane.getStyleClass().add("log-scroll-pane");
        VBox.setVgrow(logScrollPane, Priority.ALWAYS);

        middlePanel.getChildren().addAll(logTitleBar, logScrollPane);

        // ===== 第3列：通用设置 + 其他设置 + 开始抢票按钮（固定宽度，不伸缩） =====
        VBox rightPanel = new VBox(0);
        rightPanel.getStyleClass().add("settings-panel-border");
        rightPanel.setPrefWidth(370);
        rightPanel.setMinWidth(370);

        TabPane rightTabPane = new TabPane();
        rightTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Tab commonTab = new Tab("通用设置");
        commonTab.setContent(createCommonSettings());
        Tab otherTab = new Tab("其他设置");
        otherTab.setContent(createOtherSettings());

        rightTabPane.getTabs().addAll(commonTab, otherTab);

        // 开始抢票按钮
        Button startGrabBtn = new Button("开始抢票");
        startGrabBtn.getStyleClass().add("btn-start-grab");
        startGrabBtn.setMaxWidth(Double.MAX_VALUE);
        startGrabBtn.setPrefHeight(40);
        startGrabBtn.setOnAction(e -> onStartGrab());

        rightPanel.getChildren().addAll(rightTabPane, startGrabBtn);

        contentBox.getChildren().addAll(leftPanel, middlePanel, rightPanel);
        settingsBox.getChildren().add(contentBox);

        return settingsBox;
    }

    // ==================== 抢票设置内容 ====================

    /** 乘客列表控件（后续通过接口填充） */
    private ListView<String> passengerListView;
    /** 席别列表控件（后续通过接口填充） */
    private ListView<String> seatListView;
    /** 已选车次列表控件（后续通过接口填充） */
    private ListView<String> selectedTrainListView;

    /**
     * 创建带 CheckBox 的 ListView 单元格工厂
     */
    private javafx.util.Callback<javafx.scene.control.ListView<String>, javafx.scene.control.ListCell<String>> checkBoxCellFactory() {
        return lv -> new javafx.scene.control.ListCell<>() {
            private final CheckBox checkBox = new CheckBox();
            {
                checkBox.setOnAction(e -> {
                    if (getItem() != null) {
                        // toggle selection visual
                        setStyle(checkBox.isSelected() ? "-fx-background-color: #1976D2; -fx-text-fill: white;" : "");
                    }
                });
            }
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    checkBox.setText(item);
                    setGraphic(checkBox);
                    setText(null);
                }
            }
        };
    }

    private VBox createGrabSettings() {
        VBox box = new VBox(6);
        box.setPadding(new Insets(8));

        // 4列横排布局：乘客 | 席别 | 已选车次 | 可选设置
        HBox topRow = new HBox(8);
        HBox.setHgrow(topRow, Priority.ALWAYS);

        // 第1列：乘客列表（下拉勾选框，数据后续通过接口填充）
        VBox passengerBox = new VBox(4);
        Label passengerTitle = new Label("*乘客: 加儿童 (0/14)");
        passengerTitle.getStyleClass().add("setting-label");
        passengerListView = new ListView<>();
        passengerListView.setPrefHeight(130);
        passengerListView.setPlaceholder(new Label("暂无乘客数据"));
        passengerListView.getStyleClass().add("setting-list");
        passengerListView.setCellFactory(checkBoxCellFactory());
        passengerBox.getChildren().addAll(passengerTitle, passengerListView);
        HBox.setHgrow(passengerBox, Priority.ALWAYS);

        // 第2列：席别列表（下拉勾选框，数据后续通过接口填充）
        VBox seatBox = new VBox(4);
        Label seatTitle = new Label("*席别: 选辅 (0/22)");
        seatTitle.getStyleClass().add("setting-label");
        seatListView = new ListView<>();
        seatListView.setPrefHeight(130);
        seatListView.setPlaceholder(new Label("暂无席别数据"));
        seatListView.getStyleClass().add("setting-list");
        seatListView.setCellFactory(checkBoxCellFactory());
        seatBox.getChildren().addAll(seatTitle, seatListView);
        HBox.setHgrow(seatBox, Priority.ALWAYS);

        // 第3列：已选车次（数据后续通过接口填充）
        VBox trainBox = new VBox(4);
        Label trainTitle = new Label("*已选车次:");
        trainTitle.getStyleClass().add("setting-label");
        selectedTrainListView = new ListView<>();
        selectedTrainListView.setPrefHeight(130);
        selectedTrainListView.setPlaceholder(new Label("暂无已选车次"));
        selectedTrainListView.getStyleClass().add("setting-list");
        trainBox.getChildren().addAll(trainTitle, selectedTrainListView);
        HBox.setHgrow(trainBox, Priority.ALWAYS);

        // 第4列：可选设置（复刻 Bypass UI）
        VBox optionsBox = new VBox(4);
        Label optionsTitle = new Label("可选设置:");
        optionsTitle.getStyleClass().add("setting-label");

        CheckBox autoWaitlist = new CheckBox("自动抢候补");
        autoWaitlist.getStyleClass().add("setting-checkbox");
        CheckBox priorityWaitlist = new CheckBox("优先候补不抢票");
        priorityWaitlist.getStyleClass().add("setting-checkbox");
        CheckBox byTrainOrder = new CheckBox("按车次顺序提交");
        byTrainOrder.getStyleClass().add("setting-checkbox");
        CheckBox selectBerth = new CheckBox("选上下铺和选座");
        selectBerth.getStyleClass().add("setting-checkbox");
        CheckBox autoPay = new CheckBox("抢到自动付");
        autoPay.getStyleClass().add("setting-checkbox");
        CheckBox grabExtra = new CheckBox("抢增开列车");
        grabExtra.getStyleClass().add("setting-checkbox");

        // 时间范围选择
        ComboBox<String> timeRangeCombo = new ComboBox<>();
        timeRangeCombo.getItems().addAll("00:00-24:00", "06:00-12:00", "12:00-18:00", "18:00-24:00");
        timeRangeCombo.setValue("00:00-24:00");
        timeRangeCombo.setPrefWidth(110);

        optionsBox.getChildren().addAll(optionsTitle, autoWaitlist, priorityWaitlist,
                byTrainOrder, selectBerth, autoPay, grabExtra, timeRangeCombo);
        HBox.setHgrow(optionsBox, Priority.ALWAYS);

        topRow.getChildren().addAll(passengerBox, seatBox, trainBox, optionsBox);
        box.getChildren().add(topRow);
        return box;
    }

    // ==================== 通用设置内容 ====================

    private VBox createCommonSettings() {
        VBox box = new VBox(6);
        box.setPadding(new Insets(8));

        // 定时抢票
        HBox timedRow = new HBox(6);
        timedRow.setAlignment(Pos.CENTER_LEFT);
        CheckBox timedGrab = new CheckBox("定时抢票");
        timedGrab.getStyleClass().add("setting-checkbox");
        timedGrab.setTooltip(new Tooltip("设定某个时间，到点开始刷票\n【设置完毕，需要点击开始抢票】"));
        Spinner<Integer> hourSpinner = new Spinner<>(0, 23, 8);
        hourSpinner.setPrefWidth(75);
        Spinner<Integer> minSpinner = new Spinner<>(0, 59, 51);
        minSpinner.setPrefWidth(75);
        Spinner<Integer> secSpinner = new Spinner<>(0, 59, 5);
        secSpinner.setPrefWidth(75);
        timedRow.getChildren().addAll(timedGrab, hourSpinner, new Label(":"), minSpinner, new Label(":"), secSpinner);

        // 修改间隔
        HBox intervalRow = new HBox(6);
        intervalRow.setAlignment(Pos.CENTER_LEFT);
        CheckBox modifyInterval = new CheckBox("修改间隔");
        modifyInterval.getStyleClass().add("setting-checkbox");
        modifyInterval.setTooltip(new Tooltip("依据设备的承受能力和封ip的可能性\n长时间[捡漏抢票]建议用默认，不勾选\n短时间[整点预售]整点十几秒内，最低间隔\n普通用户最低一秒，赞助用户最低100毫秒\n请勿长时间低间隔，否则可能很容易被封IP"));
        Spinner<Integer> intervalSpinner = new Spinner<>(100, 99999, 1000);
        intervalSpinner.setPrefWidth(110);
        intervalRow.getChildren().addAll(modifyInterval, intervalSpinner);

        CheckBox delayClose = new CheckBox("延迟关闭 [修改间隔] 选项");
        delayClose.getStyleClass().add("setting-checkbox");
        delayClose.setTooltip(new Tooltip("这个选项启用时，自动在抢票十秒后关闭间隔\n一般用在定时抢预售票时，搭配最低间隔使用\n防止长时间低间隔被封IP，这个选项默认打开\n如果非要长时间低间隔，可以慎重关闭此选项"));
        CheckBox nationalCDN = new CheckBox("全国CDN 可用: 308");
        nationalCDN.getStyleClass().add("setting-checkbox");
        nationalCDN.setSelected(true);
        nationalCDN.setTooltip(new Tooltip("拉取所有的12306服务器IP，智能测速之后\n每次查询使用随机的IP，减少缓存也增加抢票成功率\n如某些杀毒软件提示，请添加信任，否则无法使用本功能"));
        CheckBox noSubmitWhenNoTicket = new CheckBox("实时余票无座时,不提交");
        noSubmitWhenNoTicket.getStyleClass().add("setting-checkbox");
        noSubmitWhenNoTicket.setTooltip(new Tooltip("勾选上是12306返回是无座的时候不要，返回二等座/硬座最后系统强制分配的无座，这就没办法了。\n无座是12306分配的，提交的时候都是二等座/硬座，但最后12306的最终强制分配是无法决定的。\n勾选上，能避免80%的强制分配，【仍不能完全保证】，这个可以咨询下12306，关于强制分配无座。"));
        CheckBox partialSubmit = new CheckBox("余票不足乘客时,部分提交");
        partialSubmit.getStyleClass().add("setting-checkbox");
        partialSubmit.setTooltip(new Tooltip("比如有3个乘客，刷出两张票的时候，\n勾选此功能则提交前两位乘客\n乘客列表中右键，可以调整联系人排序"));

        box.getChildren().addAll(timedRow, intervalRow, delayClose, nationalCDN,
                noSubmitWhenNoTicket, partialSubmit);
        return box;
    }

    // ==================== 其他设置内容 ====================

    private VBox createOtherSettings() {
        VBox box = new VBox(6);
        box.setPadding(new Insets(8));

        // 统一 CheckBox 宽度，确保 Spinner 对齐
        double checkBoxWidth = 90;

        // 小黑屋
        HBox blackRoomRow = new HBox(6);
        blackRoomRow.setAlignment(Pos.CENTER_LEFT);
        CheckBox blackRoom = new CheckBox("小黑屋");
        blackRoom.getStyleClass().add("setting-checkbox");
        blackRoom.setMinWidth(checkBoxWidth);
        blackRoom.setMaxWidth(checkBoxWidth);
        Spinner<Integer> blackRoomSpinner = new Spinner<>(10, 999, 120);
        blackRoomSpinner.setPrefWidth(95);
        blackRoomSpinner.setEditable(true);
        Label blackRoomUnit = new Label("秒/次");
        blackRoomRow.getChildren().addAll(blackRoom, blackRoomSpinner, blackRoomUnit);
        Tooltip blackRoomTip = new Tooltip("温馨提示：\n12306的余票并不是实时的，显示有票实则已经无票了\n这就是缓存机制，避免过多的请求真实的数据而造成拥堵\n所以小黑屋的功能就是当遇到缓存的时候，停留一段时间提交\n以免耽误其他车次的提交，或者被封账号。\n当然，有些时候缓存也能抢到票，所以请慎重抉择。\n候补和抢票是两个黑屋，是有区分的，互不影响。");
        blackRoomTip.setWrapText(true);
        blackRoomTip.setMaxWidth(400);
        Tooltip.install(blackRoom, blackRoomTip);

        // 屏蔽临近
        CheckBox shieldNear = new CheckBox("屏蔽临近");
        shieldNear.getStyleClass().add("setting-checkbox");
        shieldNear.setMinWidth(checkBoxWidth);
        shieldNear.setMaxWidth(checkBoxWidth);
        Spinner<Integer> shieldNearSpinner = new Spinner<>(10, 720, 30);
        shieldNearSpinner.setPrefWidth(95);
        shieldNearSpinner.setEditable(true);
        Label shieldNearUnit = new Label("分钟的车");
        HBox shieldNearRow = new HBox(6);
        shieldNearRow.setAlignment(Pos.CENTER_LEFT);
        shieldNearRow.getChildren().addAll(shieldNear, shieldNearSpinner, shieldNearUnit);
        Tooltip shieldNearTip = new Tooltip("温馨提示：\n12306有时会放出距离发车时间只有10分钟的车，考虑到需要赶赴火车站，可能来不及赶上。\n所以此功能可以设置一个您足够去到达火车站的时间，不足您设置时间的列车会自动屏蔽。\n只有当天的列车才生效，最长可设置720分钟(12小时)，最短10分钟，请根据自己的情况设置。");
        shieldNearTip.setWrapText(true);
        shieldNearTip.setMaxWidth(400);
        Tooltip.install(shieldNear, shieldNearTip);

        // 动车不要卧铺
        CheckBox noSleeper = new CheckBox("动车不要卧铺 (一/二等卧)");
        noSleeper.getStyleClass().add("setting-checkbox");
        Tooltip noSleeperTip = new Tooltip("温馨提示：\n动车二等卧与硬卧在12306查询时归为一类，软件默认勾选硬卧时包含二等卧。这在一般车次没问题，\n但某些混合编组列车即有二等座也有二等卧，用户只想要普速硬卧和动车二等座，却误抢动车二等卧。\n此选项勾选后，即使勾选了软卧/硬卧，也不再提交动车一等卧/二等卧，确保只抢普速硬卧或二等座。");
        noSleeperTip.setWrapText(true);
        noSleeperTip.setMaxWidth(400);
        Tooltip.install(noSleeper, noSleeperTip);

        box.getChildren().addAll(blackRoomRow, shieldNearRow, noSleeper);
        return box;
    }

    /**
     * 开始抢票按钮点击事件
     */
    private void onStartGrab() {
        logger.info("点击开始抢票");
        // TODO: 实现抢票逻辑
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
        faqGroup.setMinHeight(68);

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

    // ==================== 邮件通知设置 ====================

    private VBox createEmailSettings() {
        VBox root = new VBox(6);
        root.setPadding(new Insets(8));

        // 线框标题：邮箱设置
        TitledPane emailBorder = new TitledPane();
        emailBorder.setText("邮箱设置");
        emailBorder.setCollapsible(false);
        emailBorder.getStyleClass().add("settings-titled-pane");

        VBox emailContent = new VBox(8);
        emailContent.setPadding(new Insets(8));

        // 统一控件高度（用 inline style 覆盖全局 CSS padding: 10 14，inline style 优先级最高）
        String compactStyle = "-fx-padding: 3 6; -fx-pref-height: 26; -fx-max-height: 26;";
        // 统一右侧列宽度（服务器下拉、收件人输入框、测试发送按钮一致）
        double rightColWidth = 180;
        // 统一左侧输入框宽度
        double leftInputWidth = 180;

        // 发件人 + 服务器
        HBox row1 = new HBox(6);
        row1.setAlignment(Pos.CENTER_LEFT);
        Label senderLabel = new Label("发件人:");
        senderLabel.setMinWidth(55);
        TextField senderField = new TextField();
        senderField.setPrefWidth(leftInputWidth);
        senderField.setStyle(compactStyle);
        senderField.setPromptText("发件人邮箱");
        Region row1Spacer = new Region();
        HBox.setHgrow(row1Spacer, Priority.ALWAYS);
        Label serverLabel = new Label("服务器:");
        serverLabel.setMinWidth(55);
        ComboBox<String> serverCombo = new ComboBox<>();
        serverCombo.getItems().addAll("smtp.126.com", "smtp.163.com", "smtp.qq.com", "smtp.gmail.com", "smtp.sina.com");
        serverCombo.setValue("smtp.126.com");
        serverCombo.setPrefWidth(rightColWidth);
        serverCombo.setPrefHeight(26);
        serverCombo.setMaxHeight(26);
        row1.getChildren().addAll(senderLabel, senderField, row1Spacer, serverLabel, serverCombo);

        // 密码 + 收件人
        HBox row2 = new HBox(6);
        row2.setAlignment(Pos.CENTER_LEFT);
        Label pwdLabel = new Label("密  码:");
        pwdLabel.setMinWidth(55);
        PasswordField pwdField = new PasswordField();
        pwdField.setPrefWidth(leftInputWidth);
        pwdField.setStyle(compactStyle);
        pwdField.setPromptText("邮箱密码/授权码");
        Region row2Spacer = new Region();
        HBox.setHgrow(row2Spacer, Priority.ALWAYS);
        Label receiverLabel = new Label("收件人:");
        receiverLabel.setMinWidth(55);
        TextField receiverField = new TextField();
        receiverField.setPrefWidth(rightColWidth);
        receiverField.setStyle(compactStyle);
        receiverField.setPromptText("收件人邮箱");
        row2.getChildren().addAll(pwdLabel, pwdField, row2Spacer, receiverLabel, receiverField);

        // 选项 + 测试发送
        HBox row3 = new HBox(6);
        row3.setAlignment(Pos.CENTER_LEFT);
        Label optionLabel = new Label("选  项:");
        optionLabel.setMinWidth(55);
        CheckBox sslCheck = new CheckBox("使用SSL加密");
        TextField portField = new TextField("465");
        portField.setPrefWidth(80);
        portField.setMinWidth(80);
        portField.setStyle(compactStyle);
        portField.setPromptText("端口");
        Region row3Spacer = new Region();
        HBox.setHgrow(row3Spacer, Priority.ALWAYS);
        Button testSendBtn = new Button("测试发送");
        testSendBtn.setPrefWidth(rightColWidth);
        testSendBtn.setMinWidth(rightColWidth);
        testSendBtn.setStyle(compactStyle);
        testSendBtn.getStyleClass().add("setting-btn");
        row3.getChildren().addAll(optionLabel, sslCheck, portField, row3Spacer, testSendBtn);

        // 底部说明文字
        VBox descBox = new VBox(2);
        String[] descLines = {
            "1.发件人和收件人均为带@的全名称，发件人和收件人最好为同一个账号",
            "2.多个收件人请用英文符号；分隔。例如：xx@139.com;ss@163.com",
            "3.移动139、联通186、电信189等运营商邮箱可设置免费短信提醒",
            "4.发送失败请检查用户名密码、25端口是否被禁用、邮箱已开启POP3功能"
        };
        for (String line : descLines) {
            Label descLabel = new Label(line);
            descLabel.getStyleClass().add("settings-desc-label");
            descBox.getChildren().add(descLabel);
        }

        emailContent.getChildren().addAll(row1, row2, row3, descBox);
        emailBorder.setContent(emailContent);
        root.getChildren().add(emailBorder);

        return root;
    }

    // ==================== 微信通知设置 ====================

    private VBox createWechatSettings() {
        VBox root = new VBox(6);
        root.setPadding(new Insets(8));

        // 线框标题：微信设置
        TitledPane wechatBorder = new TitledPane();
        wechatBorder.setText("微信设置");
        wechatBorder.setCollapsible(false);
        wechatBorder.getStyleClass().add("settings-titled-pane");

        VBox wechatContent = new VBox(8);
        wechatContent.setPadding(new Insets(8));

        // 主体：左侧二维码 + 右侧操作区
        HBox mainRow = new HBox(16);
        mainRow.setAlignment(Pos.TOP_LEFT);

        // -- 左侧：二维码占位区域 --
        VBox qrBox = new VBox(4);
        qrBox.setAlignment(Pos.CENTER);
        qrBox.setMinWidth(160);
        qrBox.setMinHeight(160);
        qrBox.setMaxWidth(160);
        qrBox.setMaxHeight(160);
        qrBox.getStyleClass().add("qr-placeholder");
        Label qrPlaceholder = new Label("公众号二维码\n（待审核）");
        qrPlaceholder.setAlignment(Pos.CENTER);
        qrPlaceholder.getStyleClass().add("qr-placeholder-text");
        qrBox.getChildren().add(qrPlaceholder);

        // -- 右侧：绑定状态 + 按钮 + 说明 --
        VBox rightCol = new VBox(8);

        // 绑定状态
        HBox statusRow = new HBox(6);
        statusRow.setAlignment(Pos.CENTER_LEFT);
        Label statusLabel = new Label("绑定状态:");
        statusLabel.setMinWidth(70);
        Label statusValue = new Label("未绑定");
        statusValue.getStyleClass().add("wechat-status");
        statusRow.getChildren().addAll(statusLabel, statusValue);

        // 4个按钮 2x2 排列
        HBox btnRow1 = new HBox(8);
        btnRow1.setAlignment(Pos.CENTER_LEFT);
        Button loadQrBtn = new Button("加载二维码");
        loadQrBtn.setPrefWidth(120);
        loadQrBtn.getStyleClass().add("setting-btn");
        Button checkBindBtn = new Button("检查绑定");
        checkBindBtn.setPrefWidth(120);
        checkBindBtn.getStyleClass().add("setting-btn");
        btnRow1.getChildren().addAll(loadQrBtn, checkBindBtn);

        HBox btnRow2 = new HBox(8);
        btnRow2.setAlignment(Pos.CENTER_LEFT);
        Button changeBindBtn = new Button("更换绑定");
        changeBindBtn.setPrefWidth(120);
        changeBindBtn.getStyleClass().add("setting-btn");
        Button testBtn = new Button("测试");
        testBtn.setPrefWidth(120);
        testBtn.getStyleClass().add("setting-btn");
        btnRow2.getChildren().addAll(changeBindBtn, testBtn);

        // 初次使用说明
        VBox descBox = new VBox(2);
        Label descTitle = new Label("初次使用");
        descTitle.getStyleClass().add("settings-desc-title");
        String[] descLines = {
            "1.加载二维码，手机扫描二维码并关注",
            "2.点击检查绑定，检查是否已成功绑定",
            "完成绑定后，自动启用，请勿取消关注"
        };
        descBox.getChildren().add(descTitle);
        for (String line : descLines) {
            Label descLabel = new Label(line);
            descLabel.getStyleClass().add("settings-desc-label");
            descBox.getChildren().add(descLabel);
        }

        rightCol.getChildren().addAll(statusRow, btnRow1, btnRow2, descBox);
        mainRow.getChildren().addAll(qrBox, rightCol);
        wechatContent.getChildren().add(mainRow);
        wechatBorder.setContent(wechatContent);
        root.getChildren().add(wechatBorder);

        return root;
    }

    // ==================== 自动支付设置 ====================

    private VBox createAutoPaySettings() {
        VBox root = new VBox(6);
        root.setPadding(new Insets(8));

        // 线框标题：支付设置（用 TitledPane 实现带标题的线框）
        TitledPane payBorder = new TitledPane();
        payBorder.setText("支付设置");
        payBorder.setCollapsible(false);
        payBorder.getStyleClass().add("settings-titled-pane");

        VBox payContent = new VBox(8);
        payContent.setPadding(new Insets(8));

        // 主体：左侧输入 + 右侧勾选（两列）
        HBox mainRow = new HBox(16);
        mainRow.setAlignment(Pos.TOP_LEFT);

        // -- 左侧：支付宝账号 + 支付密码 --
        VBox leftCol = new VBox(8);
        leftCol.setMinWidth(180);

        HBox accountRow = new HBox(6);
        accountRow.setAlignment(Pos.CENTER_LEFT);
        Label accountLabel = new Label("支付宝账号:");
        accountLabel.setMinWidth(75);
        TextField accountField = new TextField();
        accountField.setPrefWidth(130);
        accountField.setPromptText("支付宝账号");
        accountRow.getChildren().addAll(accountLabel, accountField);

        HBox passwordRow = new HBox(6);
        passwordRow.setAlignment(Pos.CENTER_LEFT);
        Label passwordLabel = new Label("支付密码:");
        passwordLabel.setMinWidth(75);
        PasswordField passwordField = new PasswordField();
        passwordField.setPrefWidth(130);
        passwordField.setPromptText("支付密码");
        passwordRow.getChildren().addAll(passwordLabel, passwordField);

        leftCol.getChildren().addAll(accountRow, passwordRow);

        // -- 右侧：勾选框（2列排列） --
        VBox rightCol = new VBox(4);

        CheckBox autoPayCheck = new CheckBox("抢到票自动支付");
        autoPayCheck.setTooltip(new Tooltip("如果勾选了此功能，抢到票或者抢到候补会尝试自动支付。\n如果支付失败，订单依然是未支付状态，就需要手动支付。\n如果支付成功，订单就是已支付状态，可以去核对一下。\n建议开启这个功能后，也要设置一些通知，以免支付失败。\n请在抢票之前勾选，已经抢到票了，再次勾选不会起作用。"));

        CheckBox payUnpaidCheck = new CheckBox("支付未付款订单");
        CheckBox noSeatNoPayCheck = new CheckBox("无座不支付");
        CheckBox showPayWindowCheck = new CheckBox("显示支付的窗口");
        CheckBox rememberCheck = new CheckBox("记住勾选");
        rememberCheck.setTooltip(new Tooltip("是否记住自动支付的相关勾选，就是保存配置的意思，重启软件不影响。\n请注意，一旦保存了配置，请留意自动支付是否勾选，以免测试时支付。"));

        // 勾选框排列（参照 Bypass 布局）
        HBox checkRow1 = new HBox(12);
        checkRow1.getChildren().addAll(autoPayCheck);
        HBox checkRow2 = new HBox(12);
        checkRow2.getChildren().addAll(payUnpaidCheck, noSeatNoPayCheck);
        HBox checkRow3 = new HBox(12);
        checkRow3.getChildren().addAll(showPayWindowCheck, rememberCheck);

        rightCol.getChildren().addAll(checkRow1, checkRow2, checkRow3);

        mainRow.getChildren().addAll(leftCol, rightCol);

        // 底部说明文字
        VBox descBox = new VBox(2);
        String[] descLines = {
            "1.主要解决，抢到票时通知不到而错过付款，候补是按付款时间计算的。",
            "2.支持余额、余额宝、花呗、银行卡，按支付宝支付顺序依次尝试支付。",
            "3.不是登录密码，是六位数字的支付密码，为了安全只在本机加密使用。",
            "4.支付全程都在本机执行，受支付宝风控保护，建议提前进行支付测试。",
            "5.收款方为铁路，不会经过分流，如有订单的问题，请咨询12306客服。"
        };
        for (String line : descLines) {
            Label descLabel = new Label(line);
            descLabel.getStyleClass().add("settings-desc-label");
            descBox.getChildren().add(descLabel);
        }

        payContent.getChildren().addAll(mainRow, descBox);
        payBorder.setContent(payContent);
        root.getChildren().add(payBorder);

        return root;
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

    /**
     * 打开日志文件所在的 Windows 文件夹
     */
    private void openLogDirectory() {
        File logDir = new File("logs");
        if (!logDir.exists()) {
            logDir.mkdirs();
        }
        try {
            Desktop.getDesktop().open(logDir);
        } catch (Exception ex) {
            logger.error("打开日志目录失败：{}", ex.getMessage());
        }
    }

    /**
     * 按账号初始化独立日志文件
     * 移除 logback.xml 中的默认 FILE appender，替换为账号专属的滚动日志文件
     * 未登录时使用 tickethelper.log，已登录时使用 tickethelper-{username}.log
     */
    private void initAccountLogFile() {
        String accountName = (currentUser != null && currentUser.getUsername() != null)
                ? currentUser.getUsername() : "default";
        String logFileName = "logs/tickethelper-" + accountName + ".log";
        String logPattern = "logs/tickethelper-" + accountName + ".%d{yyyy-MM-dd}.log";

        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        ch.qos.logback.classic.Logger rootLogger = loggerContext.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);

        // 移除旧的 FILE appender（来自 logback.xml）
        rootLogger.detachAppender("FILE");

        // 创建账号专属的滚动文件 appender
        RollingFileAppender<ch.qos.logback.classic.spi.ILoggingEvent> fileAppender = new RollingFileAppender<>();
        fileAppender.setName("FILE-" + accountName);
        fileAppender.setFile(logFileName);

        TimeBasedRollingPolicy<ch.qos.logback.classic.spi.ILoggingEvent> rollingPolicy = new TimeBasedRollingPolicy<>();
        rollingPolicy.setFileNamePattern(logPattern);
        rollingPolicy.setMaxHistory(7);
        rollingPolicy.setParent(fileAppender);
        rollingPolicy.setContext(loggerContext);
        rollingPolicy.start();
        fileAppender.setRollingPolicy(rollingPolicy);

        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setPattern("%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{50} - %msg%n");
        encoder.setCharset(java.nio.charset.StandardCharsets.UTF_8);
        encoder.setContext(loggerContext);
        encoder.start();
        fileAppender.setEncoder(encoder);

        fileAppender.setContext(loggerContext);
        fileAppender.start();

        rootLogger.addAppender(fileAppender);

        // 记录当前日志文件引用，供实时读取使用
        currentLogFile = new File(logFileName);
        logPointer = 0;

        logger.info("已初始化账号日志文件：{}", logFileName);
    }

    /**
     * 启动日志文件实时读取（类似 tail -f）
     * 每 500ms 轮询一次日志文件的新内容，显示在输出区
     * 格式：HH:mm:ss.SSS 日志内容（蓝色）
     * 最多保留 MAX_LOG_LINES 行，超出后移除最老的
     */
    private void startLogTailing() {
        if (currentLogFile == null) return;

        // 如果文件已存在，从末尾开始读（只显示新日志）
        if (currentLogFile.exists()) {
            logPointer = currentLogFile.length();
        }

        logTailTimeline = new Timeline(new javafx.animation.KeyFrame(javafx.util.Duration.millis(500), e -> pollLogFile()));
        logTailTimeline.setCycleCount(Timeline.INDEFINITE);
        logTailTimeline.play();
    }

    /**
     * 轮询日志文件，读取新增内容并显示到输出区
     */
    private void pollLogFile() {
        if (currentLogFile == null || !currentLogFile.exists()) return;

        long fileLength = currentLogFile.length();
        // 文件被滚动重建后（长度变小），重置指针
        if (fileLength < logPointer) {
            logPointer = 0;
        }
        if (fileLength == logPointer) return;

        try (RandomAccessFile raf = new RandomAccessFile(currentLogFile, "r")) {
            raf.seek(logPointer);
            byte[] buffer = new byte[(int) (fileLength - logPointer)];
            raf.readFully(buffer);
            logPointer = fileLength;

            String newContent = new String(buffer, java.nio.charset.StandardCharsets.UTF_8);
            String[] lines = newContent.split("\n", -1);

            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;

                // 解析 logback 格式：2026-06-17 14:39:53.287 [main] INFO  ... - 消息内容
                String displayLine = parseLogLine(trimmed);
                if (displayLine != null) {
                    Text text = new Text(displayLine + "\n");
                    text.setFill(javafx.scene.paint.Color.BLUE);
                    logArea.getChildren().add(text);
                }
            }

            // 超过最大行数，移除最老的
            while (logArea.getChildren().size() > MAX_LOG_LINES) {
                logArea.getChildren().remove(0);
            }

            // 自动滚动到底部（仅当用户已在底部附近时）
            if (logScrollPane.getVvalue() > 0.95) {
                logScrollPane.setVvalue(1.0);
            }
        } catch (Exception ex) {
            // 文件被占用或读取异常，静默跳过
        }
    }

    /**
     * 解析 logback 日志行，提取时间+消息
     * 输入：2026-06-17 14:39:53.287 [main] INFO  c.j.j.t.MainStage - 已初始化账号日志文件
     * 输出：14:39:53.287 已初始化账号日志文件
     */
    private String parseLogLine(String line) {
        // 匹配：yyyy-MM-dd HH:mm:ss.SSS [thread] LEVEL  logger - msg
        if (line.length() < 23) return null;
        // 时间部分："14:39:53.287"
        String timePart = line.substring(11, 23);
        // 提取日志级别：在 "] " 之后，%-5level 占5字符
        int bracketEnd = line.indexOf("] ");
        if (bracketEnd < 0) return null;
        String level = line.substring(bracketEnd + 2, Math.min(bracketEnd + 7, line.length())).trim();
        // 只显示 INFO 级别
        if (!"INFO".equals(level)) return null;
        // 查找 " - " 分隔符后的消息内容
        int msgStart = line.indexOf(" - ");
        if (msgStart < 0) return null;
        String message = line.substring(msgStart + 3).trim();
        if (message.isEmpty()) return null;
        return timePart + "  " + message;
    }

    /**
     * 保存车站搜索历史（分账户）
     * 查询车票时自动调用，将出发站和到达站保存到历史
     */
    private void saveStationHistory(String fromStation, String toStation) {
        if (currentAccountConfig == null) return;
        currentAccountConfig.addDepartureHistory(fromStation);
        currentAccountConfig.addArrivalHistory(toStation);
        // 更新输入框的历史列表
        fromStationField.setHistoryItems(currentAccountConfig.getDepartureHistory());
        toStationField.setHistoryItems(currentAccountConfig.getArrivalHistory());
    }

    // ==================== 分账户配置 ====================

    /**
     * 初始化分账户配置
     * 根据当前登录用户加载独立的配置（音量、车站历史等）
     */
    private void initAccountConfig() {
        if (currentUser != null && currentUser.getUsername() != null) {
            currentAccountConfig = AccountConfig.get(currentUser.getUsername());
            logger.info("已加载账户配置：user={}", currentUser.getUsername());
        } else {
            // 未登录时使用全局默认配置
            currentAccountConfig = null;
            logger.info("未登录，使用全局默认配置");
        }
    }

    /**
     * 初始化车站搜索历史（分账户）
     * 将历史数据注入到输入框，并设置选中回调以保存新选择
     */
    private void initStationHistory() {
        if (currentAccountConfig == null) {
            // 未登录时，使用空历史
            fromStationField.setHistoryItems(java.util.Collections.emptyList());
            toStationField.setHistoryItems(java.util.Collections.emptyList());
            return;
        }

        // 注入出发站历史
        fromStationField.setHistoryItems(currentAccountConfig.getDepartureHistory());
        fromStationField.setOnHistorySelected(station -> {
            currentAccountConfig.addDepartureHistory(station);
            // 更新列表（去重置顶后）
            fromStationField.setHistoryItems(currentAccountConfig.getDepartureHistory());
            return null;
        });

        // 注入到达站历史
        toStationField.setHistoryItems(currentAccountConfig.getArrivalHistory());
        toStationField.setOnHistorySelected(station -> {
            currentAccountConfig.addArrivalHistory(station);
            toStationField.setHistoryItems(currentAccountConfig.getArrivalHistory());
            return null;
        });

        logger.info("已加载车站历史：出发{}条，到达{}条",
                currentAccountConfig.getDepartureHistory().size(),
                currentAccountConfig.getArrivalHistory().size());
    }

    // ==================== 同城车站筛选 ====================

    /**
     * 创建同城车站筛选按钮（蓝色向下箭头）
     */
    private Button createCityFilterButton(String tooltipText) {
        Button btn = new Button("\u2193"); // 向下箭头
        btn.setPrefWidth(24);
        btn.setPrefHeight(28);
        btn.getStyleClass().add("btn-city-filter");
        btn.setCursor(javafx.scene.Cursor.HAND);
        Tooltip tooltip = new Tooltip("温馨提示：\n" + tooltipText);
        tooltip.setWrapText(true);
        tooltip.setPrefWidth(220);
        Tooltip.install(btn, tooltip);
        return btn;
    }

    /**
     * 更新同城筛选按钮的视觉状态（有筛选时高亮）
     */
    private void updateCityFilterButtonState(Button btn, String stationName, Map<String, java.util.Set<String>> filters) {
        if (stationName == null || stationName.trim().isEmpty()) {
            btn.getStyleClass().remove("btn-city-filter-active");
            return;
        }
        String cityCode = StationUtil.findStationCityCode(stationName.trim());
        if (cityCode != null && !cityCode.isEmpty() && filters.containsKey(cityCode) && !filters.get(cityCode).isEmpty()) {
            if (!btn.getStyleClass().contains("btn-city-filter-active")) {
                btn.getStyleClass().add("btn-city-filter-active");
            }
        } else {
            btn.getStyleClass().remove("btn-city-filter-active");
        }
    }

    /**
     * 显示同城车站筛选弹出窗口
     */
    private void showCityStationPopup(StationAutoCompleteField stationField, Button anchorBtn,
                                       Popup existingPopup, Map<String, java.util.Set<String>> filters, boolean isFrom) {
        String stationName = stationField.getText();
        if (stationName == null || stationName.trim().isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("提示");
            alert.setHeaderText(null);
            alert.setContentText("请先输入车站名称，再筛选同城车站。");
            alert.showAndWait();
            return;
        }

        String cityCode = StationUtil.findStationCityCode(stationName.trim());
        List<StationUtil.Station> cityStations = StationUtil.getStationsInSameCity(stationName.trim());
        if (cityStations.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("提示");
            alert.setHeaderText(null);
            alert.setContentText("未找到与「" + stationName.trim() + "」同城的其他车站。");
            alert.showAndWait();
            return;
        }

        // 如果只有一个车站（就是当前站），也提示
        if (cityStations.size() <= 1) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("提示");
            alert.setHeaderText(null);
            alert.setContentText("「" + stationName.trim() + "」没有同城其他车站。");
            alert.showAndWait();
            return;
        }

        // 隐藏已有弹出窗口
        if (existingPopup != null && existingPopup.isShowing()) {
            existingPopup.hide();
        }

        // 创建弹出窗口内容
        VBox popupContent = new VBox(4);
        popupContent.setPadding(new Insets(6, 8, 6, 8));
        popupContent.setStyle("-fx-background-color: white; -fx-border-color: #b0b0b0; -fx-border-radius: 4; -fx-background-radius: 4;");
        popupContent.setPrefWidth(140);

        // 获取当前城市的已选车站
        java.util.Set<String> selectedStations = filters.computeIfAbsent(cityCode, k -> new java.util.HashSet<>());
        // 默认全选
        if (selectedStations.isEmpty()) {
            for (StationUtil.Station s : cityStations) {
                selectedStations.add(s.getName());
            }
        }

        List<CheckBox> checkBoxes = new ArrayList<>();
        for (StationUtil.Station s : cityStations) {
            CheckBox cb = new CheckBox(s.getName());
            cb.setSelected(selectedStations.contains(s.getName()));
            cb.setStyle("-fx-font-size: 13px;");
            checkBoxes.add(cb);
            popupContent.getChildren().add(cb);
        }

        Popup popup = new Popup();
        popup.setAutoHide(true);
        popup.getContent().add(popupContent);

        // 定位到按钮正下方
        double screenX = anchorBtn.localToScreen(0, 0).getX();
        double screenY = anchorBtn.localToScreen(0, 0).getY() + anchorBtn.getHeight();
        popup.show(anchorBtn, screenX, screenY);

        // 保存引用以便后续关闭
        if (isFrom) {
            fromCityPopup = popup;
        } else {
            toCityPopup = popup;
        }

        // 弹出窗口关闭时保存选择
        popup.setOnHidden(e -> {
            java.util.Set<String> newSelected = new java.util.HashSet<>();
            for (CheckBox cb : checkBoxes) {
                if (cb.isSelected()) {
                    newSelected.add(cb.getText());
                }
            }
            if (newSelected.isEmpty()) {
                filters.remove(cityCode);
            } else {
                filters.put(cityCode, newSelected);
            }
            // 保存持久化
            saveCityFilters();
            // 更新按钮状态
            updateCityFilterButtonState(anchorBtn, stationField.getText(), filters);
            // 如果有查询结果，重新应用筛选
            if (!allTrainResults.isEmpty()) {
                applyFilters();
            }
            logger.debug("同城筛选已保存：cityCode={} -> {}", cityCode, newSelected);
        });
    }

    /**
     * 保存同城车站筛选状态到配置文件
     */
    private void saveCityFilters() {
        if (currentAccountConfig == null) return;
        try {
            // 保存为 JSON 格式：{"from":{"深圳":["深圳","深圳东","深圳北","福田"]},"to":{...}}
            StringBuilder sb = new StringBuilder();
            sb.append("{\"from\":{");
            boolean firstFrom = true;
            for (Map.Entry<String, java.util.Set<String>> entry : fromCityFilters.entrySet()) {
                if (!firstFrom) sb.append(",");
                sb.append("\"").append(entry.getKey()).append("\":");
                sb.append("[");
                boolean first = true;
                for (String s : entry.getValue()) {
                    if (!first) sb.append(",");
                    sb.append("\"").append(s).append("\"");
                    first = false;
                }
                sb.append("]");
                firstFrom = false;
            }
            sb.append("},\"to\":{");
            boolean firstTo = true;
            for (Map.Entry<String, java.util.Set<String>> entry : toCityFilters.entrySet()) {
                if (!firstTo) sb.append(",");
                sb.append("\"").append(entry.getKey()).append("\":");
                sb.append("[");
                boolean first = true;
                for (String s : entry.getValue()) {
                    if (!first) sb.append(",");
                    sb.append("\"").append(s).append("\"");
                    first = false;
                }
                sb.append("]");
                firstTo = false;
            }
            sb.append("}}");
            currentAccountConfig.setCityFilter(sb.toString());
        } catch (Exception e) {
            logger.warn("保存同城筛选状态失败", e);
        }
    }

    /**
     * 加载同城车站筛选状态
     */
    private void loadCityFilters() {
        if (currentAccountConfig == null) return;
        String json = currentAccountConfig.getCityFilter();
        if (json == null || json.isEmpty()) return;
        try {
            // 简单解析 JSON（不引入额外依赖）
            fromCityFilters.clear();
            toCityFilters.clear();
            parseCityFilterJson(json, "from", fromCityFilters);
            parseCityFilterJson(json, "to", toCityFilters);
            // 更新按钮状态
            updateCityFilterButtonState(fromCityBtn, fromStationField.getText(), fromCityFilters);
            updateCityFilterButtonState(toCityBtn, toStationField.getText(), toCityFilters);
            logger.info("已加载同城筛选状态");
        } catch (Exception e) {
            logger.warn("加载同城筛选状态失败", e);
        }
    }

    /**
     * 简单解析同城筛选 JSON
     */
    private void parseCityFilterJson(String json, String key, Map<String, java.util.Set<String>> target) {
        String fromKey = "\"" + key + "\":{";
        int start = json.indexOf(fromKey);
        if (start < 0) return;
        start += fromKey.length();
        int end = json.indexOf("}", start);
        if (end < 0) return;
        String content = json.substring(start, end);
        // 解析每个城市
        String[] cityEntries = content.split("},\"");
        for (String entry : cityEntries) {
            entry = entry.replace("}", "").trim();
            if (entry.isEmpty()) continue;
            int colonIdx = entry.indexOf(":[");
            if (colonIdx < 0) continue;
            String city = entry.substring(0, colonIdx).replace("\"", "").trim();
            String stationsStr = entry.substring(colonIdx + 2);
            if (stationsStr.endsWith("]")) stationsStr = stationsStr.substring(0, stationsStr.length() - 1);
            java.util.Set<String> stations = new java.util.HashSet<>();
            for (String s : stationsStr.split(",")) {
                s = s.replace("\"", "").trim();
                if (!s.isEmpty()) stations.add(s);
            }
            if (!stations.isEmpty()) {
                target.put(city, stations);
            }
        }
    }

    /**
     * 获取当前账户的声音开关状态（优先用账户配置，回退到全局配置）
     */
    private boolean isSoundEnabled() {
        if (currentAccountConfig != null) {
            return currentAccountConfig.isSoundEnabled();
        }
        return AppConfig.getInstance().isSoundEnabled();
    }

    /**
     * 设置声音开关（保存到分账户配置）
     */
    private void setSoundEnabled(boolean enabled) {
        if (currentAccountConfig != null) {
            currentAccountConfig.setSoundEnabled(enabled);
        } else {
            AppConfig.getInstance().setSoundEnabled(enabled);
        }
    }

    // ==================== 声音开关 ====================

    /** 更新音量图标（根据账户配置中的声音开关状态） */
    private void updateVolumeIcon() {
        if (volumeLabel == null) return;
        if (isSoundEnabled()) {
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
        boolean newState = !isSoundEnabled();
        setSoundEnabled(newState);
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
        private boolean headerRow = false;

        public void setProperty(String key, String value) {
            properties.put(key, value);
        }

        public javafx.beans.property.MapProperty<String, String> getProperties() {
            return properties;
        }

        public boolean isHeaderRow() {
            return headerRow;
        }

        public void setHeaderRow(boolean headerRow) {
            this.headerRow = headerRow;
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
