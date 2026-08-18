package com.ibigou.blindbox.config;

import com.ibigou.blindbox.service.AdminAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 平台后台 API 鉴权拦截器：/api/admin/** 必须携带有效 X-Admin-Token。
 */
@Component
@RequiredArgsConstructor
public class AdminAuthInterceptor implements HandlerInterceptor {

    public static final String HEADER = "X-Admin-Token";

    private final AdminAuthService adminAuthService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        try {
            adminAuthService.requireAdmin(request.getHeader(HEADER));
            return true;
        } catch (Exception e) {
            response.setStatus(200);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":-1,\"msg\":\"" + e.getMessage() + "\",\"data\":null}");
            return false;
        }
    }
}
