package com.jactil.javafx.tickethelper;

import com.jactil.javafx.tickethelper.model.UserInfo;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 主界面窗口
 * 包含顶部导航栏 + 标签页结构（抢票/订单/候补/设置）
 * 无实际业务逻辑，仅保留界面框架
 */
public class MainStage extends Stage {

    private static final Logger logger = LoggerFactory.getLogger(MainStage.class);

    private UserInfo currentUser;
    private Runnable onLogout;

    public MainStage() {
        this(null);
    }

    public MainStage(UserInfo userInfo) {
        this(userInfo, null);
    }

    public MainStage(UserInfo userInfo, Runnable onLogout) {
        this.currentUser = userInfo;
        this.onLogout = onLogout;
        setTitle("JavaFx-TicketHelper - 主界面");
        setMinWidth(1000);
        setMinHeight(680);

        BorderPane root = new BorderPane();

        // 顶部导航栏
        HBox navBar = createNavBar();
        root.setTop(navBar);

        // 标签页区域
        TabPane tabPane = createTabPane();
        root.setCenter(tabPane);

        Scene scene = new Scene(root, 1000, 680);
        scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
        setScene(scene);

        logger.info("主界面已初始化");
    }

    /**
     * 创建顶部导航栏
     */
    private HBox createNavBar() {
        HBox navBar = new HBox(20);
        navBar.setAlignment(Pos.CENTER_LEFT);
        navBar.setPadding(new Insets(10, 20, 10, 20));
        navBar.getStyleClass().add("nav-bar");

        Label titleLabel = new Label("🚄 JavaFx-TicketHelper");
        titleLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 18));
        titleLabel.getStyleClass().add("nav-title");

        // 右侧占位：语言切换 / 用户信息
        HBox rightBox = new HBox(15);
        rightBox.setAlignment(Pos.CENTER_RIGHT);
        HBox.setHgrow(rightBox, javafx.scene.layout.Priority.ALWAYS);

        String userDisplay = (currentUser != null && currentUser.getRealName() != null)
                ? currentUser.getRealName()
                : "未登录";
        Label userLabel = new Label("当前用户：" + userDisplay);
        userLabel.getStyleClass().add("nav-user");

        // 注销按钮
        Button logoutBtn = new Button("注销登录");
        logoutBtn.getStyleClass().add("btn-logout");
        logoutBtn.setOnAction(e -> {
            logger.info("用户点击注销登录");
            if (onLogout != null) {
                onLogout.run();
            }
            close();
        });

        Label versionLabel = new Label("v1.0.0");
        versionLabel.getStyleClass().add("nav-version");

        navBar.getChildren().addAll(titleLabel, rightBox, userLabel, logoutBtn, versionLabel);
        return navBar;
    }

    /**
     * 创建标签页（抢票/订单/候补/设置）
     */
    private TabPane createTabPane() {
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabPane.getStyleClass().add("main-tabpane");

        // 抢票标签页
        Tab ticketTab = new Tab("🎫 抢票");
        ticketTab.setContent(createPlaceholderContent("抢票模块", "余票查询、车次筛选、席别选择、多任务抢票（待实现）"));

        // 订单标签页
        Tab orderTab = new Tab("📋 订单");
        orderTab.setContent(createPlaceholderContent("订单模块", "订单管理、支付提醒（待实现）"));

        // 候补标签页
        Tab waitlistTab = new Tab("⏳ 候补");
        waitlistTab.setContent(createPlaceholderContent("候补模块", "候补订单查询、自动提交候补（待实现）"));

        // 设置标签页
        Tab settingsTab = new Tab("⚙️ 设置");
        settingsTab.setContent(createPlaceholderContent("设置模块", "代理设置、通知配置、服务器时间同步（待实现）"));

        tabPane.getTabs().addAll(ticketTab, orderTab, waitlistTab, settingsTab);
        return tabPane;
    }

    /**
     * 创建占位内容（各标签页的空白占位）
     */
    private VBox createPlaceholderContent(String title, String description) {
        VBox content = new VBox(15);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(40));

        Label titleLabel = new Label(title);
        titleLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 24));
        titleLabel.getStyleClass().add("placeholder-title");

        Label descLabel = new Label(description);
        descLabel.setFont(Font.font("Microsoft YaHei", 14));
        descLabel.getStyleClass().add("placeholder-desc");

        content.getChildren().addAll(titleLabel, descLabel);
        return content;
    }
}
