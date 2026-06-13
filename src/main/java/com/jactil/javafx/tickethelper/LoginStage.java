package com.jactil.javafx.tickethelper;

import com.jactil.javafx.tickethelper.model.UserInfo;
import com.jactil.javafx.tickethelper.service.LoginService;
import com.jactil.javafx.tickethelper.service.impl.LoginServiceImpl;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Consumer;

/**
 * 登录窗口（参考 Bypass 设计）
 * 多步验证流程：检查验证方式 → 发送短信 → 输入验证码 → RSA加密登录
 */
public class LoginStage extends Stage {

    private static final Logger logger = LoggerFactory.getLogger(LoginStage.class);

    private Consumer<UserInfo> onLoginSuccess;
    private final LoginService loginService;

    private TextField usernameField;
    private PasswordField passwordField;
    private TextField passwordVisibleField;
    private Button togglePasswordBtn;
    private ComboBox<String> serverCombo;
    private Button loginButton;
    private Label statusLabel;
    private boolean passwordVisible = false;
    private CheckBox rememberCheckBox;
    private static final String CREDENTIALS_FILE = System.getProperty("user.home") + "/.javafx-tickethelper/credentials.properties";

    public LoginStage() {
        setTitle("JavaFx-TicketHelper - 登录");
        setResizable(false);

        loginService = new LoginServiceImpl();

        // === 顶部 Banner 区域 ===
        VBox banner = new VBox();
        banner.setPrefHeight(120);
        banner.setMaxHeight(120);
        banner.getStyleClass().add("login-banner");
        banner.setAlignment(Pos.CENTER);

        Label bannerTitle = new Label("JavaFx-TicketHelper");
        bannerTitle.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 20));
        bannerTitle.getStyleClass().add("banner-title");

        Label bannerSubtitle = new Label("12306 抢票助手");
        bannerSubtitle.setFont(Font.font("Microsoft YaHei", 12));
        bannerSubtitle.getStyleClass().add("banner-subtitle");

        banner.getChildren().addAll(bannerTitle, bannerSubtitle);

        // === 表单区域 ===
        VBox formBox = new VBox(12);
        formBox.setAlignment(Pos.CENTER);
        formBox.setPadding(new Insets(20, 60, 10, 60));

        HBox usernameRow = createFormRow("用户名：", buildUsernameField());
        HBox passwordRow = createFormRow("密  码：", buildPasswordWithToggle());
        HBox serverRow = createFormRow("服务器：", buildServerCombo());

        formBox.getChildren().addAll(usernameRow, passwordRow, serverRow);

        // === 状态提示 ===
        statusLabel = new Label("");
        statusLabel.getStyleClass().add("status-label");
        statusLabel.setVisible(false);

        // === 记住账号密码 ===
        rememberCheckBox = new CheckBox("记住账号密码");
        rememberCheckBox.getStyleClass().add("remember-checkbox");

        // 加载已保存的凭据
        loadSavedCredentials();

        // === 登录按钮 ===
        loginButton = new Button("登 录");
        loginButton.getStyleClass().add("btn-login");
        loginButton.setOnAction(event -> handleLogin());

        VBox buttonBox = new VBox(8);
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.setPadding(new Insets(10, 60, 0, 60));
        buttonBox.getChildren().addAll(rememberCheckBox, loginButton, statusLabel);

        // === 底部链接 ===
        HBox linkBox = new HBox(20);
        linkBox.setAlignment(Pos.CENTER);
        linkBox.setPadding(new Insets(10, 60, 20, 60));

        Label qrLoginLink = new Label("扫码登录");
        qrLoginLink.getStyleClass().add("link-red");
        qrLoginLink.setOnMouseClicked(e -> handleQrLogin());

        Label moreFeaturesLink = new Label("更多功能");
        moreFeaturesLink.getStyleClass().add("link-green");
        moreFeaturesLink.setOnMouseClicked(e -> showMoreFeaturesMenu(moreFeaturesLink));

        linkBox.getChildren().addAll(qrLoginLink, moreFeaturesLink);

        // === 整体布局 ===
        VBox root = new VBox(0);
        root.getStyleClass().add("login-root");
        root.getChildren().addAll(banner, formBox, buttonBox, linkBox);

        Scene scene = new Scene(root, 620, 400);
        scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
        setScene(scene);
    }

    private HBox createFormRow(String labelText, javafx.scene.Node control) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);

        Label label = new Label(labelText);
        label.setMinWidth(70);
        label.getStyleClass().add("form-label");

        HBox.setHgrow(control, Priority.ALWAYS);
        if (control instanceof javafx.scene.layout.Region) {
            ((javafx.scene.layout.Region) control).setMaxWidth(Double.MAX_VALUE);
        }

        row.getChildren().addAll(label, control);
        return row;
    }

    private TextField buildUsernameField() {
        usernameField = new TextField();
        usernameField.setPromptText("请输入 12306 账号（手机号/邮箱）");
        usernameField.getStyleClass().add("login-input");
        return usernameField;
    }

    private HBox buildPasswordWithToggle() {
        HBox pwdBox = new HBox(0);
        pwdBox.getStyleClass().add("password-wrapper");

        passwordField = new PasswordField();
        passwordField.setPromptText("请输入密码");
        passwordField.getStyleClass().add("login-input");
        passwordField.getStyleClass().add("login-input-pwd");
        HBox.setHgrow(passwordField, Priority.ALWAYS);

        passwordVisibleField = new TextField();
        passwordVisibleField.setPromptText("请输入密码");
        passwordVisibleField.getStyleClass().add("login-input");
        passwordVisibleField.getStyleClass().add("login-input-pwd");
        passwordVisibleField.setVisible(false);
        passwordVisibleField.setManaged(false);
        HBox.setHgrow(passwordVisibleField, Priority.ALWAYS);

        togglePasswordBtn = new Button("\uD83D\uDC41");
        togglePasswordBtn.getStyleClass().add("btn-toggle-pwd");
        togglePasswordBtn.setTooltip(new Tooltip("显示密码"));
        togglePasswordBtn.setOnAction(e -> togglePasswordVisibility());

        pwdBox.getChildren().addAll(passwordField, passwordVisibleField, togglePasswordBtn);
        return pwdBox;
    }

    private void togglePasswordVisibility() {
        passwordVisible = !passwordVisible;
        if (passwordVisible) {
            passwordVisibleField.setText(passwordField.getText());
            passwordField.setVisible(false);
            passwordField.setManaged(false);
            passwordVisibleField.setVisible(true);
            passwordVisibleField.setManaged(true);
            togglePasswordBtn.setTooltip(new Tooltip("隐藏密码"));
            passwordVisibleField.requestFocus();
        } else {
            passwordField.setText(passwordVisibleField.getText());
            passwordVisibleField.setVisible(false);
            passwordVisibleField.setManaged(false);
            passwordField.setVisible(true);
            passwordField.setManaged(true);
            togglePasswordBtn.setTooltip(new Tooltip("显示密码"));
            passwordField.requestFocus();
        }
    }

    private ComboBox<String> buildServerCombo() {
        serverCombo = new ComboBox<>();
        serverCombo.getItems().addAll(
                "默认分配的服务器-ipv4",
                "默认分配的服务器-ipv6",
                "自定义服务器"
        );
        serverCombo.setValue("默认分配的服务器-ipv4");
        serverCombo.getStyleClass().add("login-input");
        return serverCombo;
    }

    public void setOnLoginSuccess(Consumer<UserInfo> onLoginSuccess) {
        this.onLoginSuccess = onLoginSuccess;
    }

    /**
     * 处理登录（多步验证流程）
     */
    private void handleLogin() {
        String username = usernameField.getText().trim();
        String password = passwordVisible ? passwordVisibleField.getText().trim() : passwordField.getText().trim();

        if (username.isEmpty()) {
            showStatus("请输入账号", false);
            return;
        }
        if (password.isEmpty()) {
            showStatus("请输入密码", false);
            return;
        }

        loginButton.setDisable(true);
        loginButton.setText("验证中...");
        showStatus("正在检查验证方式...", true);

        // 第一步：检查验证方式
        new Thread(() -> {
            LoginService.LoginResult checkResult = loginService.checkVerifyMethod(username);

            javafx.application.Platform.runLater(() -> {
                if (checkResult.needSmsVerify) {
                    // 需要短信验证 → 发送验证码
                    showSmsVerifyDialog(username, password);
                } else if (checkResult.success) {
                    // 无需验证，直接登录成功（理论上不会到这里）
                    handleLoginSuccess(checkResult.userInfo);
                } else {
                    loginButton.setDisable(false);
                    loginButton.setText("登 录");
                    showStatus(checkResult.errorMessage, false);
                }
            });
        }, "CheckVerifyThread").start();
    }

    /**
     * 显示短信验证码输入弹窗
     */
    private void showSmsVerifyDialog(String username, String password) {
        showStatus("需要短信验证，正在发送验证码...", true);
        loginButton.setText("发送验证码...");

        // 发送短信验证码
        new Thread(() -> {
            LoginService.LoginResult sendResult = loginService.sendSmsCode(username);

            javafx.application.Platform.runLater(() -> {
                loginButton.setDisable(false);
                loginButton.setText("登 录");

                if (!sendResult.success) {
                    showStatus("发送验证码失败：" + sendResult.errorMessage, false);
                    return;
                }

                showStatus("验证码已发送，请输入短信验证码", true);

                // 弹出验证码输入对话框
                Dialog<String> dialog = new Dialog<>();
                dialog.setTitle("短信验证");
                dialog.setHeaderText("验证码已发送至您的手机\n请输入收到的短信验证码");
                dialog.initOwner(this);
                dialog.initModality(Modality.APPLICATION_MODAL);

                TextField smsCodeField = new TextField();
                smsCodeField.setPromptText("请输入短信验证码");
                smsCodeField.setPrefWidth(220);
                smsCodeField.setStyle("-fx-font-size: 18px; -fx-padding: 10 14;");

                VBox contentBox = new VBox(12);
                contentBox.setAlignment(Pos.CENTER);
                contentBox.setPadding(new Insets(10, 20, 10, 20));
                contentBox.getChildren().add(smsCodeField);

                dialog.getDialogPane().setContent(contentBox);
                dialog.getDialogPane().setPrefWidth(320);
                dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

                Button okButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
                okButton.setDisable(true);
                smsCodeField.textProperty().addListener((obs, oldVal, newVal) -> {
                    okButton.setDisable(newVal.trim().length() < 4);
                });

                dialog.setResultConverter(buttonType -> {
                    if (buttonType == ButtonType.OK) {
                        return smsCodeField.getText().trim();
                    }
                    return null;
                });

                Optional<String> result = dialog.showAndWait();
                result.ifPresent(smsCode -> {
                    if (smsCode.isEmpty()) return;
                    submitLoginWithSmsCode(username, password, smsCode);
                });
            });
        }, "SendSmsThread").start();
    }

    /**
     * 提交短信验证码登录
     */
    private void submitLoginWithSmsCode(String username, String password, String smsCode) {
        loginButton.setDisable(true);
        loginButton.setText("登录中...");
        showStatus("正在登录 12306...", true);

        new Thread(() -> {
            LoginService.LoginResult loginResult = loginService.loginWithSmsCode(username, password, smsCode);

            javafx.application.Platform.runLater(() -> {
                loginButton.setDisable(false);
                loginButton.setText("登 录");

                if (loginResult.success) {
                    handleLoginSuccess(loginResult.userInfo);
                } else {
                    showStatus("登录失败：" + loginResult.errorMessage, false);
                }
            });
        }, "LoginThread").start();
    }

    /**
     * 加载已保存的凭据
     */
    private void loadSavedCredentials() {
        try {
            Path path = Paths.get(CREDENTIALS_FILE);
            if (Files.exists(path)) {
                Properties props = new Properties();
                try (InputStream is = Files.newInputStream(path)) {
                    props.load(is);
                }
                String savedUser = props.getProperty("username", "");
                String savedPass = props.getProperty("password", "");
                boolean remember = Boolean.parseBoolean(props.getProperty("remember", "false"));

                logger.info("加载已保存凭据：user={}, remember={}", savedUser, remember);

                if (!savedUser.isEmpty()) {
                    usernameField.setText(savedUser);
                }
                if (!savedPass.isEmpty()) {
                    passwordField.setText(savedPass);
                }
                rememberCheckBox.setSelected(remember);
            }
        } catch (Exception e) {
            logger.warn("加载凭据失败", e);
        }
    }

    /**
     * 保存凭据
     */
    private void saveCredentials(String username, String password) {
        try {
            // 确保父目录存在
            Path path = Paths.get(CREDENTIALS_FILE);
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }

            Properties props = new Properties();
            if (rememberCheckBox.isSelected()) {
                props.setProperty("username", username);
                props.setProperty("password", password);
                props.setProperty("remember", "true");
                logger.info("保存凭据：user={}", username);
            } else {
                props.setProperty("remember", "false");
                logger.info("清除凭据（未勾选记住密码）");
            }
            try (OutputStream os = Files.newOutputStream(path)) {
                props.store(os, "JavaFx-TicketHelper Credentials");
            }
        } catch (Exception e) {
            logger.warn("保存凭据失败", e);
        }
    }

    private void handleLoginSuccess(UserInfo userInfo) {
        // 保存凭据
        saveCredentials(usernameField.getText().trim(), passwordVisible ? passwordVisibleField.getText().trim() : passwordField.getText().trim());

        showStatus("登录成功！", true);
        logger.info("登录成功，用户：{}", userInfo.getUsername());
        if (onLoginSuccess != null) {
            onLoginSuccess.accept(userInfo);
        }
    }

    private void showStatus(String message, boolean success) {
        statusLabel.setText(message);
        statusLabel.setVisible(true);
        statusLabel.getStyleClass().removeAll("status-success", "status-error");
        statusLabel.getStyleClass().add(success ? "status-success" : "status-error");
    }

    private void handleQrLogin() {
        logger.info("点击扫码登录");
        openBrowser("https://kyfw.12306.cn/otn/resources/login.html");
    }

    private void showMoreFeaturesMenu(Label anchor) {
        ContextMenu menu = new ContextMenu();
        menu.getStyleClass().add("more-menu");

        MenuItem registerItem = new MenuItem("注册12306帐号");
        registerItem.setOnAction(e -> openBrowser("https://kyfw.12306.cn/otn/regist/init"));

        MenuItem resetPwdItem = new MenuItem("重置密码");
        resetPwdItem.setOnAction(e -> openBrowser("https://kyfw.12306.cn/otn/forgetPassword/init"));

        MenuItem logoutItem = new MenuItem("注销登录");
        logoutItem.setOnAction(e -> {
            loginService.logout();
            showStatus("已注销登录", true);
        });

        SeparatorMenuItem sep1 = new SeparatorMenuItem();

        MenuItem restoreItem = new MenuItem("恢复软件默认配置");
        restoreItem.setOnAction(e -> logger.info("恢复默认配置（占位）"));

        MenuItem shortcutItem = new MenuItem("创建桌面快捷方式");
        shortcutItem.setOnAction(e -> logger.info("创建桌面快捷方式（占位）"));

        menu.getItems().addAll(registerItem, resetPwdItem, logoutItem, sep1, restoreItem, shortcutItem);
        menu.show(anchor, javafx.geometry.Side.BOTTOM, 0, 5);
    }

    private void openBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
            }
        } catch (Exception ex) {
            logger.error("打开浏览器失败：{}", url, ex);
        }
    }
}
