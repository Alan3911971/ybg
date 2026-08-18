package com.ibigou.blindbox.config;

import com.ibigou.blindbox.service.CustomerAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * P0：顾客端 API 登录拦截（/api/customer/**，登录/发码接口除外）。
 * 敏感操作（抽奖/下单/退款/钱包/宜必购）必须携带有效 X-User-Token。
 */
@Component
@RequiredArgsConstructor
public class CustomerAuthInterceptor implements HandlerInterceptor {

    public static final String HEADER = "X-User-Token";

    private final CustomerAuthService customerAuthService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        try {
            customerAuthService.requireUser(request.getHeader(HEADER));
            return true;
        } catch (Exception e) {
            response.setStatus(200);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":-1,\"msg\":\"" + e.getMessage() + "\",\"data\":null}");
            return false;
        }
    }
}
