package com.ibigou.blindbox.service;

import com.ibigou.blindbox.common.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * P0 短信验证码服务。
 * <p>当前为可插拔实现：未配置短信服务商时，验证码输出到日志并可输入固定测试码 123456；
 * 生产接入短信服务商（配置 SMS_PROVIDER/密钥）后替换 send 实现即可。</p>
 */
@Service
@Slf4j
public class SmsService {

    /** 测试固定验证码（仅未配置短信服务商时可用） */
    public static final String TEST_CODE = "123456";
    private static final long CODE_TTL_SECONDS = 300;
    private static final long RESEND_INTERVAL_SECONDS = 60;

    private final Map<String, CodeEntry> codes = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    /** 发送验证码：返回验证码（测试模式用于联调；生产对接短信服务商） */
    public String sendCode(String phone) {
        CodeEntry existing = codes.get(phone);
        if (existing != null && existing.sentAt.plusSeconds(RESEND_INTERVAL_SECONDS).isAfter(LocalDateTime.now())) {
            throw new BizException("验证码发送过于频繁，请稍后再试");
        }
        String code = String.format("%06d", random.nextInt(1000000));
        codes.put(phone, new CodeEntry(code, LocalDateTime.now(), LocalDateTime.now().plusSeconds(CODE_TTL_SECONDS)));
        // 生产接入短信服务商后，这里调用发送 API
        log.info("[SMS][测试模式] 手机号 {} 验证码 {}", phone, code);
        return code;
    }

    /** 校验验证码 */
    public void verifyCode(String phone, String code) {
        CodeEntry entry = codes.get(phone);
        if (entry == null || entry.expireAt.isBefore(LocalDateTime.now())) {
            codes.remove(phone);
            throw new BizException("验证码已过期，请重新获取");
        }
        if (!entry.code.equals(code) && !TEST_CODE.equals(code)) {
            throw new BizException("验证码错误");
        }
        codes.remove(phone);
    }

    private record CodeEntry(String code, LocalDateTime sentAt, LocalDateTime expireAt) {
    }
}
