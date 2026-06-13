package com.jactil.javafx.tickethelper;

import com.jactil.javafx.tickethelper.config.AppConfig;
import com.jactil.javafx.tickethelper.model.UserInfo;
import javafx.application.Application;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 程序入口类
 * 负责启动 JavaFX 应用，初始化登录窗口
 */
public class App extends Application {

    private static final Logger logger = LoggerFactory.getLogger(App.class);

    private Image appIcon;

    @Override
    public void start(Stage primaryStage) {
        logger.info("JavaFx-TicketHelper 启动中...");

        // 加载应用图标
        appIcon = new Image(getClass().getResourceAsStream("/images/JavaFx-TicketHelper-iocn.png"));

        // 初始化全局配置
        AppConfig.getInstance();

        // 显示登录窗口
        showLoginWindow();

        logger.info("登录窗口已显示");
    }

    /**
     * 显示登录窗口，登录成功后自动切换到主界面
     */
    private void showLoginWindow() {
        LoginStage loginStage = new LoginStage();
        loginStage.getIcons().add(appIcon);
        loginStage.show();

        // 监听登录成功事件，关闭登录窗口并打开主界面
        loginStage.setOnLoginSuccess(userInfo -> {
            loginStage.close();
            showMainWindow(userInfo);
            logger.info("登录成功，已进入主界面，用户：{}", userInfo.getRealName());
        });
    }

    /**
     * 显示主界面，注销后自动返回登录页
     */
    private void showMainWindow(UserInfo userInfo) {
        MainStage[] mainStageRef = new MainStage[1];
        mainStageRef[0] = new MainStage(userInfo, () -> {
            // 注销回调：关闭主界面，重新显示登录窗口
            mainStageRef[0].close();
            showLoginWindow();
            logger.info("已注销，返回登录页");
        });
        mainStageRef[0].getIcons().add(appIcon);
        mainStageRef[0].show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
