package com.ibigou.blindbox.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;

/**
 * P2 敏感配置加密（AES-GCM）。
 * 密钥来源：环境变量 IBIGOU_CONFIG_KEY（生产必配）；缺省开发密钥仅限测试环境。
 * 仅对敏感 key 加密存储；解密兼容旧明文（迁移期）。
 */
@Service
@Slf4j
public class ConfigCryptoService {

    /** 需要加密存储的敏感 key */
    public static final Set<String> SENSITIVE_KEYS = Set.of(
            "wx_pay_api_key", "wx_pay_api_v3_key", "identity_qr_secret");

    private static final String PREFIX = "enc:";

    public boolean isSensitive(String key) {
        return SENSITIVE_KEYS.contains(key);
    }

    /** 加密：返回 enc:base64(iv+ciphertext) */
    public String encrypt(String plain) {
        if (plain == null || plain.isBlank() || plain.startsWith(PREFIX)) {
            return plain;
        }
        try {
            byte[] keyBytes = keyBytes();
            byte[] iv = new byte[12];
            new java.security.SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keyBytes, "AES"), new GCMParameterSpec(128, iv));
            byte[] ct = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return PREFIX + Base64.getEncoder().encodeToString(concat(iv, ct));
        } catch (Exception e) {
            log.error("配置加密失败", e);
            return plain;
        }
    }

    /** 解密：enc: 前缀才解；失败按明文返回（兼容旧数据） */
    public String decrypt(String stored) {
        if (stored == null || !stored.startsWith(PREFIX)) {
            return stored;
        }
        try {
            byte[] all = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            byte[] iv = java.util.Arrays.copyOfRange(all, 0, 12);
            byte[] ct = java.util.Arrays.copyOfRange(all, 12, all.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keyBytes(), "AES"), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("配置解密失败(按明文返回, 可能为旧数据): {}", e.getMessage());
            return stored;
        }
    }

    private byte[] keyBytes() throws Exception {
        String env = System.getenv("IBIGOU_CONFIG_KEY");
        String key = env == null || env.isBlank() ? "ibigou-dev-key-0001" : env;
        byte[] raw = key.getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[16];
        System.arraycopy(raw, 0, out, 0, Math.min(raw.length, 16));
        return out;
    }

    private byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
}
