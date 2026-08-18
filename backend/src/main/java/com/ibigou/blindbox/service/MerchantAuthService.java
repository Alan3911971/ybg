package com.ibigou.blindbox.service;

import com.ibigou.blindbox.common.BizException;
import com.ibigou.blindbox.entity.Merchant;
import com.ibigou.blindbox.entity.MerchantSession;
import com.ibigou.blindbox.repository.MerchantRepository;
import com.ibigou.blindbox.repository.MerchantSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 商家 H5 登录鉴权（V1.4 补充完善：DB 会话持久化，重启不失效，token 24h 过期）。
 */
@Service
@RequiredArgsConstructor
public class MerchantAuthService {

    public static final long TOKEN_TTL_HOURS = 24;

    private final MerchantRepository merchantRepository;
    private final MerchantSessionRepository sessionRepository;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    /** 登录：写入 merchant_session，返回 token */
    public String login(String account, String password) {
        Merchant merchant = merchantRepository.findByLoginAccount(account)
                .orElseThrow(() -> new BizException("账号或密码错误"));
        if (merchant.getStatus() == null || merchant.getStatus() != 1) {
            throw new BizException("商家账号已被禁用");
        }
        if (!encoder.matches(password, merchant.getLoginPwd())) {
            throw new BizException("账号或密码错误");
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        MerchantSession session = new MerchantSession();
        session.setToken(token);
        session.setMerchantNo(merchant.getMerchantNo());
        session.setExpireTime(LocalDateTime.now().plusHours(TOKEN_TTL_HOURS));
        session.setCreateTime(LocalDateTime.now());
        sessionRepository.save(session);
        // 惰性清理过期会话
        sessionRepository.findByExpireTimeBefore(LocalDateTime.now()).forEach(sessionRepository::delete);
        return token;
    }

    /** 校验 token 并返回商家编号 */
    public String merchantNoByToken(String token) {
        MerchantSession session = sessionRepository.findById(token).orElse(null);
        if (session == null) {
            throw new BizException("登录已失效，请重新登录");
        }
        if (session.getExpireTime().isBefore(LocalDateTime.now())) {
            sessionRepository.delete(session);
            throw new BizException("登录已过期，请重新登录");
        }
        return session.getMerchantNo();
    }
}
