package com.ibigou.blindbox.controller;

import com.ibigou.blindbox.common.Result;
import com.ibigou.blindbox.entity.*;
import com.ibigou.blindbox.repository.*;
import com.ibigou.blindbox.service.AdminAuthService;
import com.ibigou.blindbox.service.AdminService;
import com.ibigou.blindbox.service.AuditLogService;
import com.ibigou.blindbox.service.GlobalConfigService;
import com.ibigou.blindbox.service.MemberService;
import com.ibigou.blindbox.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 软件公司平台后台 API（PC H5，V1.4 5.3）。
 * 重要约束：平台管理员只能审计查看商家配置，不能修改商家业务配置。
 * 鉴权：X-Admin-Token（拦截器 AdminAuthInterceptor 校验）。
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminAuthService adminAuthService;
    private final AdminService adminService;
    private final GlobalConfigService configService;
    private final ReportService reportService;
    private final SysGlobalConfigRepository configRepository;
    private final MemberService memberService;
    private final AuditLogService auditLogService;
    private final com.ibigou.blindbox.service.AlertService alertService;
    private final MerchantRepository merchantRepository;
    private final BoxPrizePoolRepository prizePoolRepository;
    private final BoxPublicPoolRepository publicPoolRepository;
    private final BoxGroupPrizePoolRepository groupPoolRepository;

    // ---------------- 平台登录 ----------------

    @PostMapping("/auth/login")
    public Result<String> login(@RequestParam String account, @RequestParam String password) {
        return Result.ok(adminAuthService.login(account, password));
    }

    /** 平台管理员修改密码（改密后全部会话下线） */
    @PostMapping("/auth/change-pwd")
    public Result<Void> changePwd(@RequestHeader("X-Admin-Token") String token,
                                  @RequestParam String oldPwd, @RequestParam String newPwd) {
        adminAuthService.changePwd(token, oldPwd, newPwd);
        return Result.ok();
    }

    // ---------------- 全局参数 ----------------

    @GetMapping("/config/list")
    public Result<List<SysGlobalConfig>> listConfig() {
        List<SysGlobalConfig> list = configRepository.findAll();
        // 密钥类脱敏（仅平台后台可见，脱敏展示）
        for (SysGlobalConfig cfg : list) {
            String k = cfg.getConfigKey();
            if (k != null && (k.startsWith("wx_pay_") && !"wx_pay_enabled".equals(k)
                    && !"wx_pay_cert_path".equals(k)) && cfg.getConfigValue() != null
                    && !cfg.getConfigValue().isBlank()) {
                String v = cfg.getConfigValue();
                cfg.setConfigValue(v.length() > 4 ? "****" + v.substring(v.length() - 4) : "****");
            }
        }
        return Result.ok(list);
    }

    @PostMapping("/config/update")
    public Result<Void> updateConfig(@RequestParam String key, @RequestParam String value,
                                     @RequestParam(required = false) String remark) {
        configService.update(key, value, remark);
        return Result.ok();
    }

    // ---------------- 商家账号管理 ----------------

    @PostMapping("/merchant/create")
    public Result<Merchant> createMerchant(@RequestParam String merchantNo, @RequestParam String merchantName,
                                           @RequestParam String loginAccount, @RequestParam String loginPwd) {
        return Result.ok(adminService.createMerchant(merchantNo, merchantName, loginAccount, loginPwd));
    }

    @PostMapping("/merchant/{merchantNo}/enable")
    public Result<Void> enableMerchant(@PathVariable String merchantNo) {
        adminService.toggleMerchant(merchantNo, true);
        return Result.ok();
    }

    @PostMapping("/merchant/{merchantNo}/disable")
    public Result<Void> disableMerchant(@PathVariable String merchantNo) {
        adminService.toggleMerchant(merchantNo, false);
        return Result.ok();
    }

    @PostMapping("/merchant/{merchantNo}/delete")
    public Result<Void> deleteMerchant(@PathVariable String merchantNo,
                                       @RequestParam String adminPassword) {
        adminService.deleteMerchant(merchantNo, adminPassword);
        return Result.ok();
    }

    @PostMapping("/merchant/{merchantNo}/reset-pwd")
    public Result<Void> resetPwd(@PathVariable String merchantNo, @RequestParam String newPwd) {
        adminService.resetPwd(merchantNo, newPwd);
        return Result.ok();
    }

    @GetMapping("/merchant/list")
    public Result<List<Merchant>> listMerchants() {
        return Result.ok(adminService.listMerchants());
    }

    // ---------------- 商家配置只读审计 ----------------

    @GetMapping("/merchant/{merchantNo}/config-audit")
    public Result<MerchantAudit> auditMerchant(@PathVariable String merchantNo) {
        Merchant merchant = merchantRepository.findById(merchantNo).orElse(null);
        List<BoxPrizePool> privatePools = prizePoolRepository.findByMerchantNoOrderByPrizeIdDesc(merchantNo);
        List<BoxPublicPool> publicPools = publicPoolRepository.findBySourceMerchantNoOrderByPublicIdDesc(merchantNo);
        List<BoxGroupPrizePool> groupPools = groupPoolRepository.findAll(); // 审计用：平台全量专属池（含本店）
        return Result.ok(new MerchantAudit(merchant, privatePools, publicPools, groupPools));
    }

    // ---------------- 公共池大盘 ----------------

    @GetMapping("/public-pool-board")
    public Result<List<BoxPublicPool>> publicPoolBoard() {
        return Result.ok(reportService.publicPoolBoard());
    }

    @GetMapping("/public-pool-board/export")
    public ResponseEntity<byte[]> exportPublicPoolBoard() {
        return excelResponse("公共池大盘.xlsx",
                reportService.exportPublicPools(reportService.publicPoolBoard()));
    }

    // ---------------- 全平台报表 ----------------

    @GetMapping("/report/coupons")
    public Result<List<UserCoupon>> reportCoupons() {
        return Result.ok(reportService.allCoupons());
    }

    @GetMapping("/report/coupons/export")
    public ResponseEntity<byte[]> exportCoupons() {
        return excelResponse("全量优惠券.xlsx", reportService.exportCoupons(reportService.allCoupons()));
    }

    @GetMapping("/report/balance-flows")
    public Result<List<UserBalanceFlow>> reportBalanceFlows() {
        return Result.ok(reportService.allBalanceFlows());
    }

    @GetMapping("/report/balance-flows/export")
    public ResponseEntity<byte[]> exportBalanceFlows() {
        return excelResponse("全量余额流水.xlsx", reportService.exportBalanceFlows(reportService.allBalanceFlows()));
    }

    @GetMapping("/report/group-records")
    public Result<List<ThirdGroupVerifyRecord>> reportGroupRecords() {
        return Result.ok(reportService.allGroupRecords());
    }

    @GetMapping("/report/group-records/export")
    public ResponseEntity<byte[]> exportGroupRecords() {
        return excelResponse("全量团购登记.xlsx", reportService.exportGroupRecords(reportService.allGroupRecords()));
    }

    @GetMapping("/report/ibigou-orders")
    public Result<List<IbigouOrder>> reportIbigouOrders() {
        return Result.ok(reportService.allIbigouOrders());
    }

    @GetMapping("/report/ibigou-orders/export")
    public ResponseEntity<byte[]> exportIbigouOrders() {
        return excelResponse("全量宜必购交易.xlsx", reportService.exportIbigouOrders(reportService.allIbigouOrders()));
    }

    // ---------------- 会员管理（V1.5） ----------------

    /** 商家会员列表（状态/免费起始/有效期/剩余天数） */
    @GetMapping("/member/list")
    public Result<java.util.List<Merchant>> memberList() {
        return Result.ok(adminService.listMerchants());
    }

    /** 全平台续费订单 */
    @GetMapping("/member/orders")
    public Result<java.util.List<MemberOrder>> memberOrders() {
        return Result.ok(memberService.allOrders());
    }

    /** 平台人工调整商家有效期（+N 个月，留审计日志） */
    @PostMapping("/member/{merchantNo}/adjust")
    public Result<Void> adjustExpire(@PathVariable String merchantNo, @RequestParam int months,
                                     @RequestHeader("X-Admin-Token") String token) {
        memberService.adminAdjustExpire(merchantNo, months, "admin");
        return Result.ok();
    }

    /** 人工操作审计日志 */
    @GetMapping("/member/audits")
    public Result<java.util.List<AuditLog>> audits() {
        return Result.ok(auditLogService.all());
    }

    /** 撤销违规人工操作（恢复余额/券状态；幂等） */
    @PostMapping("/member/audits/{logId}/revoke")
    public Result<AuditLog> revokeAudit(@PathVariable Long logId,
                                        @RequestHeader("X-Admin-Token") String token,
                                        @RequestParam String adminPassword) {
        // BUG-003：撤销审计（恢复资产）需管理员密码验证
        adminService.verifyAdminPassword(adminPassword);
        return Result.ok(auditLogService.revoke(logId, "admin"));
    }

    // ---------------- 系统告警（P2） ----------------

    @GetMapping("/alerts")
    public Result<java.util.List<com.ibigou.blindbox.entity.SysAlert>> alerts() {
        return Result.ok(alertService.list());
    }

    @PostMapping("/alerts/{id}/handle")
    public Result<Void> handleAlert(@PathVariable Long id) {
        alertService.markHandled(id);
        return Result.ok();
    }

    // ---------------- 收款码审核（P0） ----------------

    /** 审核商家收款码：action=approve|reject */
    @PostMapping("/merchant/{merchantNo}/qr-audit")
    public Result<Void> auditQr(@PathVariable String merchantNo, @RequestParam String action) {
        com.ibigou.blindbox.entity.Merchant m = merchantRepository.findById(merchantNo)
                .orElseThrow(() -> new com.ibigou.blindbox.common.BizException("商家不存在"));
        if ("approve".equals(action)) {
            m.setReceiveQrStatus(2);
        } else if ("reject".equals(action)) {
            m.setReceiveQrStatus(3);
        } else {
            throw new com.ibigou.blindbox.common.BizException("action 只能为 approve 或 reject");
        }
        merchantRepository.save(m);
        return Result.ok();
    }

    // ---------------- 工具 ----------------

    private ResponseEntity<byte[]> excelResponse(String filename, byte[] data) {
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(data);
    }

    public record MerchantAudit(Merchant merchant, List<BoxPrizePool> privatePools,
                                List<BoxPublicPool> publicPools, List<BoxGroupPrizePool> groupPools) {
    }
}
