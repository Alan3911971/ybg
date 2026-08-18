package com.ibigou.blindbox.service;

import com.ibigou.blindbox.common.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * P1 长期个人身份二维码（V1.5 第四章）：仅识别身份，不能付款；长期有效可截图。
 * <p>内容格式 YBGT-ID:{phone}:{hmac}，HMAC-SHA256 签名（密钥平台配置 identity_qr_secret，
 * 留空自动生成）；商家扫码验签后定位用户历史订单。</p>
 */
@Service
@RequiredArgsConstructor
public class IdentityQrService {

    public static final String PREFIX = "YBGT-ID:";

    private final GlobalConfigService configService;

    private final SecureRandom random = new SecureRandom();

    private String secret() {
        String s = configService.get("identity_qr_secret", "");
        if (s == null || s.isBlank()) {
            s = "ibigou-id-" + String.format("%032x", random.nextLong() & Long.MAX_VALUE)
                    + String.format("%032x", random.nextLong() & Long.MAX_VALUE);
            configService.update("identity_qr_secret", s, "长期身份二维码HMAC密钥(自动生成)");
        }
        return s;
    }

    /** 生成长期身份码内容 */
    public String issue(String userPhone) {
        if (userPhone == null || !userPhone.matches("^1[0-9]{10}$")) {
            throw new BizException("手机号不合法");
        }
        return PREFIX + userPhone + ":" + hmac(userPhone);
    }

    /** 验签解析：返回手机号 */
    public String verify(String content) {
        if (content == null || !content.startsWith(PREFIX)) {
            throw new BizException("身份二维码无效");
        }
        String[] parts = content.substring(PREFIX.length()).split(":");
        if (parts.length != 2) {
            throw new BizException("身份二维码无效");
        }
        String phone = parts[0];
        String sig = parts[1];
        if (!hmac(phone).equals(sig)) {
            throw new BizException("身份二维码签名校验失败");
        }
        return phone;
    }

    private String hmac(String phone) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(phone.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new BizException("身份码生成失败");
        }
    }
}
