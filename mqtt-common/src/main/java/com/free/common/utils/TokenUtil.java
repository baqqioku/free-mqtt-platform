package com.free.common.utils;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class TokenUtil {

    // 固定密钥（生产建议放配置中心/环境变量）
    private static final String SECRET = "MySuperSecretKey"; // 必须16字节(AES-128)

    // 生成 token
    public static String generateToken(Long userId) {
        try {
            long timestamp = System.currentTimeMillis();
            String raw = userId + ":" + timestamp;

            // AES 加密
            Cipher cipher = Cipher.getInstance("AES");
            SecretKeySpec keySpec = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "AES");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            byte[] encrypted = cipher.doFinal(raw.getBytes(StandardCharsets.UTF_8));

            // Base64 输出
            return Base64.getUrlEncoder().withoutPadding().encodeToString(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("生成token失败", e);
        }
    }

    // 解析 token 获取 userId
    public static Long parseUserId(String token) {
        try {
            // Base64 解码
            byte[] decoded = Base64.getUrlDecoder().decode(token);

            // AES 解密
            Cipher cipher = Cipher.getInstance("AES");
            SecretKeySpec keySpec = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "AES");
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            byte[] decrypted = cipher.doFinal(decoded);

            String raw = new String(decrypted, StandardCharsets.UTF_8);

            // raw 格式为 "userId:timestamp"
            String[] parts = raw.split(":");
            return Long.parseLong(parts[0]);
        } catch (Exception e) {
            throw new RuntimeException("解析token失败", e);
        }
    }

    public static void main(String[] args) {
        String token = generateToken(12345L);
        System.out.println("token = " + token);

        Long userId = parseUserId(token);
        System.out.println("userId = " + userId);
    }
}
