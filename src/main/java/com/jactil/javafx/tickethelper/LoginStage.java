package com.jactil.javafx.tickethelper;

import com.jactil.javafx.tickethelper.model.UserInfo;
import com.jactil.javafx.tickethelper.service.LoginService;
import com.jactil.javafx.tickethelper.service.impl.LoginServiceImpl;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.animation.PauseTransition;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
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
        banner.setPrefHeight(150);
        banner.setMaxHeight(150);
        banner.getStyleClass().add("login-banner");
        banner.setAlignment(Pos.CENTER);

        Label bannerTitle = new Label("JavaFx-TicketHelper");
        bannerTitle.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 26));
        bannerTitle.getStyleClass().add("banner-title");

        Label bannerSubtitle = new Label("12306 抢票助手");
        bannerSubtitle.setFont(Font.font("Microsoft YaHei", 15));
        bannerSubtitle.getStyleClass().add("banner-subtitle");

        banner.getChildren().addAll(bannerTitle, bannerSubtitle);

        // === 表单区域 ===
        VBox formBox = new VBox(16);
        formBox.setAlignment(Pos.CENTER);
        formBox.setPadding(new Insets(25, 80, 15, 80));

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
        buttonBox.setPadding(new Insets(12, 80, 0, 80));
        buttonBox.getChildren().addAll(loginButton, rememberCheckBox, statusLabel);

        // === 底部链接（左右分布） ===
        HBox linkBox = new HBox();
        linkBox.setAlignment(Pos.CENTER);
        linkBox.setPadding(new Insets(8, 160, 20, 160));

        Label qrLoginLink = new Label("扫码登录");
        qrLoginLink.getStyleClass().add("link-qr");
        qrLoginLink.setOnMouseClicked(e -> handleQrLogin());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label moreFeaturesLink = new Label("更多功能");
        moreFeaturesLink.getStyleClass().add("link-more");
        moreFeaturesLink.setOnMouseClicked(e -> showMoreFeaturesMenu(moreFeaturesLink));

        linkBox.getChildren().addAll(qrLoginLink, spacer, moreFeaturesLink);

        // === 整体布局 ===
        VBox root = new VBox(0);
        root.getStyleClass().add("login-root");
        root.getChildren().addAll(banner, formBox, buttonBox, linkBox);

        Scene scene = new Scene(root, 800, 520);
        scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
        setScene(scene);
    }

    private HBox createFormRow(String labelText, javafx.scene.Node control) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);

        Label label = new Label(labelText);
        label.setMinWidth(80);
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
        showQrLoginDialog();
    }

    /**
     * 显示扫码登录弹窗
     */
    private void showQrLoginDialog() {
        Stage qrStage = new Stage();
        qrStage.initOwner(this);
        qrStage.initModality(Modality.APPLICATION_MODAL);
        qrStage.setTitle("扫码登录");
        qrStage.setResizable(false);

        VBox root = new VBox(15);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(20, 30, 20, 30));
        root.getStyleClass().add("login-root");

        Label tipLabel = new Label("请使用 12306 App 扫描二维码登录");
        tipLabel.setFont(Font.font("Microsoft YaHei", 14));
        tipLabel.getStyleClass().add("form-label");

        ImageView qrImageView = new ImageView();
        qrImageView.setFitWidth(220);
        qrImageView.setFitHeight(220);
        qrImageView.setPreserveRatio(true);

        Label statusLabel = new Label("正在加载二维码...");
        statusLabel.setFont(Font.font("Microsoft YaHei", 13));
        statusLabel.getStyleClass().add("status-label");

        Button refreshBtn = new Button("刷新二维码");
        refreshBtn.getStyleClass().add("btn-login");
        refreshBtn.setStyle("-fx-font-size: 13px; -fx-padding: 6 20; -fx-min-width: 120;");
        refreshBtn.setVisible(false);

        root.getChildren().addAll(tipLabel, qrImageView, statusLabel, refreshBtn);

        Scene scene = new Scene(root, 300, 360);
        scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
        qrStage.setScene(scene);

        // 加载二维码
        String[] uuidRef = new String[1];
        PauseTransition[] pollRef = new PauseTransition[1];
        Runnable[] loadQrCodeRef = new Runnable[1];

        loadQrCodeRef[0] = () -> {
            statusLabel.setText("正在加载二维码...");
            statusLabel.getStyleClass().removeAll("status-success", "status-error");
            refreshBtn.setVisible(false);

            LoginService.QrLoginResult qrResult = loginService.createQrCode();
            if (qrResult.success && qrResult.imageBase64 != null) {
                uuidRef[0] = qrResult.uuid;
                try {
                    byte[] imageBytes = Base64.getDecoder().decode(qrResult.imageBase64);
                    Image qrImage = new Image(new ByteArrayInputStream(imageBytes));
                    qrImageView.setImage(qrImage);
                    statusLabel.setText("等待扫码...");

                    // 开始轮询扫码状态
                    startQrPolling(uuidRef[0], statusLabel, qrStage, refreshBtn, loadQrCodeRef, pollRef);
                } catch (Exception e) {
                    logger.error("解析二维码图片失败", e);
                    statusLabel.setText("二维码加载失败");
                    statusLabel.getStyleClass().add("status-error");
                    refreshBtn.setVisible(true);
                }
            } else {
                statusLabel.setText(qrResult.message);
                statusLabel.getStyleClass().add("status-error");
                refreshBtn.setVisible(true);
            }
        };

        refreshBtn.setOnAction(e -> {
            if (pollRef[0] != null) {
                pollRef[0].stop();
            }
            loadQrCodeRef[0].run();
        });

        qrStage.show();
        loadQrCodeRef[0].run();
    }

    /**
     * 开始轮询扫码状态
     */
    private void startQrPolling(String uuid, Label statusLabel, Stage qrStage,
                                 Button refreshBtn, Runnable[] onExpiredRef, PauseTransition[] pollRef) {
        PauseTransition poll = new PauseTransition(Duration.seconds(2));
        pollRef[0] = poll;

        poll.setOnFinished(event -> {
            if (!qrStage.isShowing()) return;

            LoginService.QrLoginResult result = loginService.checkQrStatus(uuid);

            if (result.expired) {
                statusLabel.setText("二维码已过期，请点击刷新");
                statusLabel.getStyleClass().removeAll("status-success");
                statusLabel.getStyleClass().add("status-error");
                refreshBtn.setVisible(true);
                return;
            }

            if (result.success && result.userInfo != null) {
                statusLabel.setText("登录成功！");
                statusLabel.getStyleClass().removeAll("status-error");
                statusLabel.getStyleClass().add("status-success");
                qrStage.close();
                handleLoginSuccess(result.userInfo);
                return;
            }

            // 更新状态提示
            if (result.message != null && !result.message.equals("等待扫码...")) {
                statusLabel.setText(result.message);
            }

            // 继续轮询
            poll.playFromStart();
        });

        poll.playFromStart();
    }

    private void showMoreFeaturesMenu(Label anchor) {
        ContextMenu menu = new ContextMenu();
        menu.getStyleClass().add("more-menu");

        MenuItem registerItem = new MenuItem("注册12306帐号");
        registerItem.setOnAction(e -> openBrowser("https://kyfw.12306.cn/otn/regist/init"));

        MenuItem resetPwdItem = new MenuItem("重置密码");
        resetPwdItem.setOnAction(e -> openBrowser("https://kyfw.12306.cn/otn/forgetPassword/initforgetMyPassword"));

        MenuItem logoutItem = new MenuItem("清除已保存的账号密码");
        logoutItem.setOnAction(e -> {
            try {
                Path credPath = Paths.get(CREDENTIALS_FILE);
                if (Files.exists(credPath)) {
                    Files.delete(credPath);
                    logger.info("已清除保存的账号密码");
                    showStatus("已清除保存的账号密码", true);
                } else {
                    showStatus("没有保存的账号密码", true);
                }
            } catch (Exception ex) {
                logger.error("清除凭据失败", ex);
                showStatus("清除失败：" + ex.getMessage(), false);
            }
        });

        SeparatorMenuItem sep1 = new SeparatorMenuItem();

        MenuItem restoreItem = new MenuItem("恢复软件默认配置");
        restoreItem.setOnAction(e -> logger.info("恢复默认配置（占位）"));

        MenuItem shortcutItem = new MenuItem("创建桌面快捷方式");
        shortcutItem.setOnAction(e -> createDesktopShortcut());

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

    /**
     * 创建桌面快捷方式
     * 在用户桌面生成 .url 快捷方式文件，指向当前运行的程序
     */
    private void createDesktopShortcut() {
        try {
            // 获取桌面路径
            String desktopPath = System.getProperty("user.home") + "\\Desktop";
            Path desktopDir = Paths.get(desktopPath);
            if (!Files.exists(desktopDir)) {
                showStatus("桌面路径不存在", false);
                return;
            }

            // 获取当前程序路径（jar 或 exe）
            String appPath = getAppPath();
            if (appPath == null) {
                showStatus("无法获取程序路径", false);
                return;
            }

            // 创建 .url 快捷方式文件
            Path shortcutFile = desktopDir.resolve("JavaFx-TicketHelper.url");
            String urlContent = "[InternetShortcut]\r\nURL=file:///" + appPath.replace("\\", "/") + "\r\nIconIndex=0\r\n";
            Files.writeString(shortcutFile, urlContent);

            logger.info("桌面快捷方式已创建：{}", shortcutFile);
            showStatus("桌面快捷方式已创建", true);
        } catch (Exception ex) {
            logger.error("创建桌面快捷方式失败", ex);
            showStatus("创建失败：" + ex.getMessage(), false);
        }
    }

    /**
     * 获取当前程序的运行路径
     */
    private String getAppPath() {
        try {
            // 优先尝试获取 jar 路径
            String classResource = LoginStage.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI().getPath();
            String path = java.net.URLDecoder.decode(classResource, "UTF-8");

            // Windows 下 URI 路径会以 /E:/ 开头，需要去掉开头的 /
            if (path.matches("^/[A-Za-z]:/.*")) {
                path = path.substring(1);
            }

            // 如果是 jar 文件，直接返回
            if (path.endsWith(".jar")) {
                return path;
            }

            // 如果是 class 文件（IDE 运行），尝试找上级目录的 jar 或 exe
            Path classPath = Paths.get(path);
            // 向上找到项目根目录或 target 目录
            Path targetDir = classPath;
            while (targetDir != null) {
                if (targetDir.getFileName() != null && targetDir.getFileName().toString().equals("target")) {
                    // 在 target 目录下查找 jar
                    try (var stream = Files.list(targetDir)) {
                        Optional<Path> jarFile = stream
                                .filter(p -> p.toString().endsWith(".jar"))
                                .findFirst();
                        if (jarFile.isPresent()) {
                            return jarFile.get().toString();
                        }
                    }
                    break;
                }
                targetDir = targetDir.getParent();
            }

            // 如果找不到 jar，返回当前 class 所在目录（IDE 调试用）
            return classPath.toString();
        } catch (Exception ex) {
            logger.error("获取程序路径失败", ex);
            return null;
        }
    }
}
