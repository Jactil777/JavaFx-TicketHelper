package com.jactil.javafx.tickethelper.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jactil.javafx.tickethelper.model.UserInfo;
import com.jactil.javafx.tickethelper.service.LoginService;
import com.jactil.javafx.tickethelper.util.HttpClientUtil;
import com.jactil.javafx.tickethelper.util.Sm4Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * 12306 登录服务实现
 * 完整流程：访问登录页建立Session → 检查验证方式 → 发送短信 → SM4加密密码 → 提交登录
 *
 * 密码加密方式：SM4-ECB 国密算法（密钥 tiekeyuankp12306），结果 Base64 编码后加 @ 前缀
 * 来源：12306 登录页 login_new_v20260207.js 第332行
 */
public class LoginServiceImpl implements LoginService {

    private static final Logger logger = LoggerFactory.getLogger(LoginServiceImpl.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final String LOGIN_PAGE_URL = "https://kyfw.12306.cn/otn/resources/login.html";
    private static final String CHECK_VERIFY_URL = "https://kyfw.12306.cn/passport/web/checkLoginVerify";
    private static final String GET_MESSAGE_CODE_URL = "https://kyfw.12306.cn/passport/web/getMessageCode";
    private static final String LOGIN_URL = "https://kyfw.12306.cn/passport/web/login";
    private static final String LOGOUT_URL = "https://kyfw.12306.cn/passport/web/logout";
    private static final String AUTH_CHECK_URL = "https://kyfw.12306.cn/otn/login/conf";
    private static final String UAMTK_URL = "https://kyfw.12306.cn/passport/web/auth/uamtk";
    private static final String UAMAUTHCLIENT_URL = "https://kyfw.12306.cn/otn/uamauthclient";
    private static final String INIT_MY12306_API_URL = "https://kyfw.12306.cn/otn/index/initMy12306Api";
    private static final String CREATE_QR64_URL = "https://kyfw.12306.cn/passport/web/create-qr64";
    private static final String CHECK_QR_URL = "https://kyfw.12306.cn/passport/web/checkqr";

    private UserInfo currentUser;
    private boolean sessionInitialized = false;

    /**
     * 初始化 Session：访问登录页面建立 Cookie
     */
    private void initSession() {
        if (sessionInitialized) {
            return;
        }
        try {
            // 1. 访问登录页面，建立 Session Cookie
            logger.info("访问登录页面，建立 Session...");
            HttpClientUtil.get(LOGIN_PAGE_URL);

            // 2. 调用 conf 接口完善会话
            HttpClientUtil.post(AUTH_CHECK_URL, new HashMap<>());

            sessionInitialized = true;
            logger.info("Session 初始化完成");
        } catch (Exception e) {
            logger.error("Session 初始化失败", e);
            sessionInitialized = false;
        }
    }

    @Override
    public LoginResult checkVerifyMethod(String username) {
        logger.info("检查登录验证方式，账号：{}", username);
        try {
            initSession();

            Map<String, String> params = new HashMap<>();
            params.put("username", username);
            params.put("appid", "otn");

            String response = HttpClientUtil.post(CHECK_VERIFY_URL, params);
            logger.debug("checkLoginVerify 响应：{}", response);

            JsonNode root = objectMapper.readTree(response);
            int resultCode = root.path("result_code").asInt(-1);

            if (resultCode == 0) {
                logger.info("需要短信验证");
                return LoginResult.needSms();
            }

            return LoginResult.fail("未知的验证方式");
        } catch (IOException e) {
            logger.error("检查验证方式异常", e);
            return LoginResult.fail("网络异常：" + e.getMessage());
        }
    }

    @Override
    public LoginResult sendSmsCode(String username) {
        logger.info("发送短信验证码，账号：{}", username);
        try {
            Map<String, String> params = new HashMap<>();
            params.put("appid", "otn");
            params.put("username", username);
            params.put("castNum", "3452");

            String response = HttpClientUtil.post(GET_MESSAGE_CODE_URL, params);
            logger.debug("getMessageCode 响应：{}", response);

            JsonNode root = objectMapper.readTree(response);
            int resultCode = root.path("result_code").asInt(-1);

            if (resultCode == 0) {
                logger.info("短信验证码发送成功");
                LoginResult r = new LoginResult();
                r.success = true;
                return r;
            }

            String msg = root.path("result_message").asText("发送失败");
            return LoginResult.fail(msg);
        } catch (IOException e) {
            logger.error("发送短信验证码异常", e);
            return LoginResult.fail("网络异常：" + e.getMessage());
        }
    }

    @Override
    public LoginResult loginWithSmsCode(String username, String password, String smsCode) {
        logger.info("使用短信验证码登录，账号：{}", username);
        try {
            initSession();

            // SM4-ECB 加密密码（与 12306 前端 JS 一致）
            String encryptedPassword = Sm4Util.encryptPassword(password);
            logger.info("SM4 密码加密完成");

            // 提交登录
            Map<String, String> params = new HashMap<>();
            params.put("username", username);
            params.put("password", encryptedPassword);
            params.put("appid", "otn");
            params.put("randCode", smsCode);
            params.put("checkMode", "0");
            params.put("sessionId", "");
            params.put("sig", "");
            params.put("if_check_slide_passcode_token", "");
            params.put("scene", "");

            String response = HttpClientUtil.post(LOGIN_URL, params);
            logger.debug("登录响应：{}", response);

            JsonNode root = objectMapper.readTree(response);
            int resultCode = root.path("result_code").asInt(-1);
            String resultMessage = root.path("result_message").asText("");

            if (resultCode == 0) {
                UserInfo userInfo = new UserInfo(username, password);
                userInfo.setLoggedIn(true);

                // 提取登录响应中的 uamtk，作为 uamauthclient 的降级 token
                String loginUamtk = root.path("uamtk").asText("");
                logger.info("登录响应中的 uamtk：'{}'", loginUamtk);

                // 完成登录后的认证链：uamtk → uamauthclient → initMy12306Api
                completeAuthChain(userInfo, loginUamtk);

                currentUser = userInfo;
                logger.info("12306 登录成功：{}", userInfo.getRealName());
                return LoginResult.success(userInfo);
            } else {
                logger.info("登录失败：{} (code={})", resultMessage, resultCode);
                return LoginResult.fail(resultMessage);
            }
        } catch (Exception e) {
            logger.error("登录异常", e);
            return LoginResult.fail("网络异常：" + e.getMessage());
        }
    }

    /**
     * 登录后完成认证链：uamtk → uamauthclient → initMy12306Api
     * 12306 登录成功后需要依次调用这三个接口才能获取完整的用户信息
     *
     * @param userInfo   用户信息对象
     * @param loginUamtk 登录响应中返回的 uamtk 值（当 uamtk API 失败时作为降级 token）
     */
    private void completeAuthChain(UserInfo userInfo, String loginUamtk) {
        try {
            // 1. 调用 uamtk 获取 newapptk
            Map<String, String> uamtkParams = new HashMap<>();
            uamtkParams.put("appid", "otn");
            String uamtkResponse = HttpClientUtil.post(UAMTK_URL, uamtkParams);
            logger.debug("uamtk 响应：{}", uamtkResponse);

            JsonNode uamtkRoot = objectMapper.readTree(uamtkResponse);
            String newapptk = uamtkRoot.path("newapptk").asText("");
            int uamtkCode = uamtkRoot.path("result_code").asInt(-1);

            String tk = null;

            if (uamtkCode == 0 && !newapptk.isEmpty()) {
                // uamtk API 成功，使用 newapptk
                tk = newapptk;
                logger.debug("uamtk API 成功，newapptk={}", tk);
            } else if (uamtkCode == 4) {
                // 用户已在他处登录，降级使用登录响应的 uamtk
                if (loginUamtk != null && !loginUamtk.isEmpty()) {
                    tk = loginUamtk;
                    logger.debug("uamtk 返回“用户已在他处登录”，降级使用登录响应的 uamtk：{}", tk);
                } else {
                    logger.warn("uamtk 返回 code=4 且登录响应无 uamtk，跳过 uamauthclient");
                }
            } else {
                logger.warn("uamtk 未返回有效 newapptk (code={})，尝试降级", uamtkCode);
                if (loginUamtk != null && !loginUamtk.isEmpty()) {
                    tk = loginUamtk;
                    logger.info("降级使用登录响应的 uamtk：{}", tk);
                }
            }

            // 2. 调用 uamauthclient 换取 tk cookie
            if (tk != null && !tk.isEmpty()) {
                Map<String, String> authClientParams = new HashMap<>();
                authClientParams.put("tk", tk);
                logger.info("uamauthclient 请求参数 tk={}", tk);
                String authClientResponse = HttpClientUtil.post(UAMAUTHCLIENT_URL, authClientParams);
                logger.info("uamauthclient 响应：{}", authClientResponse);
            } else {
                logger.warn("无有效 tk，跳过 uamauthclient");
            }

            // 3. 调用 initMy12306Api 获取用户详细信息
            fetchUserInfo(userInfo);
        } catch (Exception e) {
            logger.warn("完成认证链失败，用户信息可能不完整", e);
        }
    }

    /**
     * 调用 initMy12306Api 获取用户详细信息
     * 使用 GET 请求，如果首次返回 HTML 会重试一次
     */
    private void fetchUserInfo(UserInfo userInfo) {
        try {
            // initMy12306Api 使用 GET 请求（与 12306 前端一致）
            String response = HttpClientUtil.get(INIT_MY12306_API_URL);
            logger.debug("initMy12306Api 响应长度：{}", response != null ? response.length() : 0);

            // 防止返回 HTML 错误页，重试一次
            if (response == null || response.trim().startsWith("<")) {
                logger.warn("initMy12306Api 返回非JSON内容，重试一次...");
                Thread.sleep(1000);
                response = HttpClientUtil.get(INIT_MY12306_API_URL);
                if (response == null || response.trim().startsWith("<")) {
                    logger.warn("initMy12306Api 重试后仍返回非JSON内容，认证链可能未完成");
                    return;
                }
            }

            JsonNode root = objectMapper.readTree(response);
            if (root.path("status").asBoolean(false)) {
                JsonNode data = root.path("data");
                if (data.has("user_name")) {
                    userInfo.setRealName(data.path("user_name").asText());
                }
                if (data.has("_email")) {
                    userInfo.setUserId(data.path("_email").asText());
                }
                logger.info("获取用户信息成功：{}", userInfo.getRealName());
            } else {
                logger.warn("initMy12306Api 返回 status=false");
            }
        } catch (Exception e) {
            logger.warn("获取用户信息失败", e);
        }
    }

    @Override
    public void logout() {
        if (currentUser == null || !currentUser.isLoggedIn()) {
            logger.info("当前未登录，无需退出");
            return;
        }
        try {
            HttpClientUtil.get(LOGOUT_URL);
            logger.info("12306 退出登录成功，账号：{}", currentUser.getUsername());
        } catch (IOException e) {
            logger.error("退出登录异常", e);
        } finally {
            currentUser = null;
            sessionInitialized = false;
        }
    }

    @Override
    public boolean isLoggedIn() {
        return currentUser != null && currentUser.isLoggedIn();
    }

    @Override
    public QrLoginResult createQrCode() {
        logger.info("创建扫码登录二维码");
        try {
            initSession();

            Map<String, String> params = new HashMap<>();
            params.put("appid", "otn");

            String response = HttpClientUtil.post(CREATE_QR64_URL, params);
            logger.debug("create-qr64 响应：{}", response);

            JsonNode root = objectMapper.readTree(response);
            int resultCode = root.path("result_code").asInt(-1);

            if (resultCode == 0) {
                String imageBase64 = root.path("image").asText("");
                String uuid = root.path("uuid").asText("");
                logger.info("二维码创建成功，uuid={}", uuid);
                return QrLoginResult.generated(imageBase64, uuid);
            }

            String msg = root.path("result_message").asText("生成二维码失败");
            return QrLoginResult.fail(msg);
        } catch (IOException e) {
            logger.error("创建二维码异常", e);
            return QrLoginResult.fail("网络异常：" + e.getMessage());
        }
    }

    @Override
    public QrLoginResult checkQrStatus(String uuid) {
        try {
            Map<String, String> params = new HashMap<>();
            params.put("uuid", uuid);
            params.put("appid", "otn");

            String response = HttpClientUtil.post(CHECK_QR_URL, params);
            logger.debug("checkqr 响应：{}", response);

            JsonNode root = objectMapper.readTree(response);
            int resultCode = root.path("result_code").asInt(-1);

            // result_code: 0=等待扫码, 1=已扫描待确认, 2=已确认登录成功, 3=已过期
            switch (resultCode) {
                case 0:
                    return QrLoginResult.waiting();
                case 1:
                    // 已扫描但未确认
                    QrLoginResult scanned = QrLoginResult.waiting();
                    scanned.message = "请在手机上确认登录";
                    return scanned;
                case 2:
                    // 扫码登录成功，获取用户信息
                    logger.info("扫码登录成功");
                    UserInfo userInfo = new UserInfo("", "");
                    userInfo.setLoggedIn(true);
                    String qrUamtk = root.path("uamtk").asText("");
                    completeAuthChain(userInfo, qrUamtk);
                    currentUser = userInfo;
                    return QrLoginResult.success(userInfo);
                case 3:
                    logger.info("二维码已过期");
                    return QrLoginResult.expired();
                default:
                    String msg = root.path("result_message").asText("未知状态");
                    return QrLoginResult.fail(msg);
            }
        } catch (IOException e) {
            logger.error("检查扫码状态异常", e);
            return QrLoginResult.fail("网络异常：" + e.getMessage());
        }
    }
}
