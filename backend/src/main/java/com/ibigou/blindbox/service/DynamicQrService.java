package com.ibigou.blindbox.service;

import com.ibigou.blindbox.common.BizException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * V1.5 P2 用户动态核销二维码（27行：用户出示动态核销码，商家扫码核销）。
 * <p>短期有效（默认 2 分钟）+ 每次签发随机 token，前端定时刷新防截图盗用；
 * 商家扫出 token 后校验并定位用户，仅用于核销/查单，不能付款。</p>
 */
@Service
public class DynamicQrService {

    /** 动态码有效期（秒） */
    public static final long TTL_SECONDS = 120;

    private final Map<String, TokenEntry> tokens = new ConcurrentHashMap<>();

    /** 签发动态码：返回 token + 过期秒数 */
    public DynamicCode issue(String userPhone) {
        String token = UUID.randomUUID().toString().replace("-", "");
        tokens.put(token, new TokenEntry(userPhone, LocalDateTime.now().plusSeconds(TTL_SECONDS)));
        cleanup();
        return new DynamicCode(token, TTL_SECONDS, "YBGT-VERIFY:" + token);
    }

    /** 校验动态码：返回用户手机号；无效/过期抛业务异常 */
    public String verify(String token) {
        if (token == null) {
            throw new BizException("动态核销码无效");
        }
        TokenEntry entry = tokens.get(token.trim());
        if (entry == null || entry.expireAt.isBefore(LocalDateTime.now())) {
            tokens.remove(token);
            throw new BizException("动态核销码已失效，请让用户刷新后重试");
        }
        return entry.userPhone;
    }

    private void cleanup() {
        LocalDateTime now = LocalDateTime.now();
        tokens.entrySet().removeIf(e -> e.getValue().expireAt.isBefore(now));
    }

    private record TokenEntry(String userPhone, LocalDateTime expireAt) {
    }

    public record DynamicCode(String token, long expireSeconds, String content) {
    }
}
