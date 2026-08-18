package com.ibigou.blindbox.config;

import com.ibigou.blindbox.service.MerchantAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 商家端统一鉴权拦截器（/api/merchant/** 除 auth/login）。
 * 防止各 controller 手动校验 token 漏配导致越权。
 */
@Component
@RequiredArgsConstructor
public class MerchantAuthInterceptor implements HandlerInterceptor {

    public static final String HEADER = "X-Merchant-Token";

    private final MerchantAuthService merchantAuthService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String token = request.getHeader(HEADER);
        merchantAuthService.requireMerchant(token);
        return true;
    }
}
