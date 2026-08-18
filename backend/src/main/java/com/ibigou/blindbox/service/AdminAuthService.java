package com.ibigou.blindbox.service;

import com.ibigou.blindbox.common.BizException;
import com.ibigou.blindbox.entity.AdminUser;
import com.ibigou.blindbox.repository.AdminUserRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 平台管理员鉴权服务（V1.4 5.3）。
 * 首次启动自动创建默认管理员 admin / Admin@2026（BCrypt 真实加密）。
 * 会话：内存 token（24h 过期），重启需重新登录。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminAuthService {

    public static final long TOKEN_TTL_HOURS = 24;

    private final AdminUserRepository adminUserRepository;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final Map<String, Long> tokenToAdminId = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> tokenExpire = new ConcurrentHashMap<>();

    /** 默认管理员初始密码：环境变量 IBIGOU_ADMIN_INIT_PWD 优先，缺省 Admin@2026（测试用；生产必须 env 注入后首登改密） */
    @org.springframework.beans.factory.annotation.Value("${admin.init.password:Admin@2026}")
    private String initAdminPassword;

    @PostConstruct
    public void initDefaultAdmin() {
        try {
            if (adminUserRepository.findByAccount("admin").isEmpty()) {
                AdminUser u = new AdminUser();
                u.setAccount("admin");
                u.setLoginPwd(encoder.encode(initAdminPassword));
                u.setStatus(1);
                u.setCreateTime(LocalDateTime.now());
                u.setUpdateTime(LocalDateTime.now());
                adminUserRepository.save(u);
                log.info("平台默认管理员已创建 admin（初始密码来自配置，请尽快修改）");
            }
        } catch (Exception e) {
            log.warn("平台管理员初始化跳过（可能表未建）: {}", e.getMessage());
        }
    }

    public String login(String account, String password) {
        AdminUser u = adminUserRepository.findByAccount(account)
                .orElseThrow(() -> new BizException("账号或密码错误"));
        if (u.getStatus() == null || u.getStatus() != 1) {
            throw new BizException("账号已被禁用");
        }
        if (!encoder.matches(password, u.getLoginPwd())) {
            throw new BizException("账号或密码错误");
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        tokenToAdminId.put(token, u.getAdminId());
        tokenExpire.put(token, LocalDateTime.now().plusHours(TOKEN_TTL_HOURS));
        return token;
    }

    /** 修改密码：需旧密码正确；改密后强制下线所有会话（重新登录） */
    public void changePwd(String token, String oldPwd, String newPwd) {
        requireAdmin(token);
        Long adminId = tokenToAdminId.get(token);
        AdminUser u = adminUserRepository.findById(adminId)
                .orElseThrow(() -> new BizException("管理员不存在"));
        if (!encoder.matches(oldPwd, u.getLoginPwd())) {
            throw new BizException("原密码错误");
        }
        if (newPwd == null || newPwd.length() < 8) {
            throw new BizException("新密码长度至少 8 位");
        }
        u.setLoginPwd(encoder.encode(newPwd));
        u.setUpdateTime(LocalDateTime.now());
        adminUserRepository.save(u);
        // 强制全部会话下线
        tokenToAdminId.clear();
        tokenExpire.clear();
    }

    /** 校验 token，无效/过期抛业务异常 */
    public void requireAdmin(String token) {
        if (token == null || !tokenToAdminId.containsKey(token)) {
            throw new BizException("登录已失效，请重新登录");
        }
        LocalDateTime expire = tokenExpire.get(token);
        if (expire == null || expire.isBefore(LocalDateTime.now())) {
            tokenToAdminId.remove(token);
            tokenExpire.remove(token);
            throw new BizException("登录已过期，请重新登录");
        }
    }
}
