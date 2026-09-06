package com.ibigou.blindbox.service;

import com.ibigou.blindbox.common.BizException;
import com.ibigou.blindbox.entity.CustomerSession;
import com.ibigou.blindbox.repository.CustomerSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * P0 顾客短信验证码登录（V1.5"用户唯一标识：手机号"归属验证）。
 * 登录后签发 token（24h），敏感操作接口经拦截器校验。
 * V2.3：customer_sms_login_required=0 时关闭验证码，11位手机号直登。
 */
@Service
@RequiredArgsConstructor
public class CustomerAuthService {

    public static final long TOKEN_TTL_HOURS = 24;

    private final SmsService smsService;
    private final CustomerSessionRepository sessionRepository;
    private final GlobalConfigService globalConfigService;

    /** 发送验证码 */
    public String sendCode(String userPhone) {
        if (userPhone == null || !userPhone.matches("^1[0-9]{10}$")) {
            throw new BizException("请输入正确的手机号");
        }
        return smsService.sendCode(userPhone);
    }

    /** 验证码登录：签发 token（sms_login_required=0 时跳过验证码直登） */
    @Transactional
    public String login(String userPhone, String code) {
        if (userPhone == null || !userPhone.matches("^1[0-9]{10}$")) {
            throw new BizException("请输入正确的手机号");
        }
        if (globalConfigService.customerSmsLoginRequired()) {
            smsService.verifyCode(userPhone, code);
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        CustomerSession session = new CustomerSession();
        session.setToken(token);
        session.setUserPhone(userPhone);
        session.setExpireTime(LocalDateTime.now().plusHours(TOKEN_TTL_HOURS));
        session.setCreateTime(LocalDateTime.now());
        sessionRepository.save(session);
        // 惰性清理过期会话
        sessionRepository.findByExpireTimeBefore(LocalDateTime.now()).forEach(sessionRepository::delete);
        return token;
    }

    /** 校验 token 返回手机号（拦截器用） */
    public String requireUser(String token) {
        if (token == null || token.isBlank()) {
            throw new BizException("请先登录");
        }
        CustomerSession session = sessionRepository.findById(token).orElse(null);
        if (session == null) {
            throw new BizException("请先登录");
        }
        if (session.getExpireTime().isBefore(LocalDateTime.now())) {
            sessionRepository.delete(session);
            throw new BizException("登录已过期，请重新登录");
        }
        return session.getUserPhone();
    }
}
