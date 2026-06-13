package com.jactil.javafx.tickethelper.util;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.util.Base64;

/**
 * SM4 国密加密工具类
 * 12306 登录密码加密：SM4-ECB 模式，密钥 tiekeyuankp12306，结果 Base64 编码后加 @ 前缀
 */
public class Sm4Util {

    /** 12306 SM4 加密密钥（16字节 = 128位） */
    private static final String SM4_KEY = "tiekeyuankp12306";

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    /**
     * SM4-ECB 加密密码（与 12306 前端 JS 逻辑一致）
     *
     * @param password 明文密码
     * @return 加密后的字符串，格式：@Base64(ciphertext)
     */
    public static String encryptPassword(String password) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(
                    SM4_KEY.getBytes(StandardCharsets.UTF_8), "SM4");

            Cipher cipher = Cipher.getInstance("SM4/ECB/PKCS5Padding", "BC");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);

            byte[] encrypted = cipher.doFinal(password.getBytes(StandardCharsets.UTF_8));
            return "@" + Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("SM4 加密失败", e);
        }
    }
}
