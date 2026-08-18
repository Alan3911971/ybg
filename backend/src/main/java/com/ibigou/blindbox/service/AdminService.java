package com.ibigou.blindbox.service;

import com.ibigou.blindbox.common.BizException;
import com.ibigou.blindbox.entity.Merchant;
import com.ibigou.blindbox.repository.MerchantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import com.ibigou.blindbox.entity.AdminUser;
import com.ibigou.blindbox.entity.BoxPrizePool;
import com.ibigou.blindbox.repository.AdminUserRepository;
import com.ibigou.blindbox.repository.BoxPrizePoolRepository;
import com.ibigou.blindbox.repository.BoxPublicPoolRepository;
import com.ibigou.blindbox.repository.BoxGroupPrizePoolRepository;
import com.ibigou.blindbox.repository.MerchantSessionRepository;
import com.ibigou.blindbox.repository.BoxPrizeLimitStatRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 平台后台商家账号管理（V1.4 5.3）。
 * 新增 / 启用禁用 / 重置密码；平台只能审计查看商家配置，不能修改商家业务配置。
 */
@Service
@RequiredArgsConstructor
public class AdminService {

    private final MerchantRepository merchantRepository;
    private final MemberService memberService;
    private final AdminUserRepository adminUserRepository;
    private final BoxPrizePoolRepository boxPrizePoolRepository;
    private final BoxPublicPoolRepository boxPublicPoolRepository;
    private final BoxGroupPrizePoolRepository boxGroupPrizePoolRepository;
    private final MerchantSessionRepository merchantSessionRepository;
    private final BoxPrizeLimitStatRepository boxPrizeLimitStatRepository;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Transactional
    public Merchant createMerchant(String merchantNo, String merchantName, String loginAccount, String loginPwd) {
        if (merchantNo == null || merchantNo.isBlank()) {
            throw new BizException("商家编号不能为空");
        }
        if (merchantName == null || merchantName.isBlank()) {
            throw new BizException("商家名称不能为空");
        }
        if (loginAccount == null || loginAccount.isBlank()) {
            throw new BizException("登录账号不能为空");
        }
        if (loginPwd == null || loginPwd.length() < 6) {
            throw new BizException("登录密码至少 6 位");
        }
        if (merchantRepository.existsById(merchantNo)) {
            throw new BizException("商家编号已存在");
        }
        if (merchantRepository.findByLoginAccount(loginAccount).isPresent()) {
            throw new BizException("登录账号已存在");
        }
        Merchant m = new Merchant();
        m.setMerchantNo(merchantNo);
        m.setMerchantName(merchantName);
        m.setLoginAccount(loginAccount);
        m.setLoginPwd(encoder.encode(loginPwd));
        m.setStatus(1);
        // 默认权重（用户拍板 2026-08-18）：私有池 100/公共 0；大类 折扣50/立减&余额50
        m.setPrivatePoolWeight(100);
        m.setPublicPoolWeight(0);
        m.setBoxDiscountTotalWeight(50);
        m.setBoxCouponTotalWeight(50);
        m.setReceiveQrStatus(0);
        m.setCreateTime(LocalDateTime.now());
        m.setUpdateTime(LocalDateTime.now());
        // V1.5：免费试用期 = 激活当天 + 平台配置试用天数（代码不写死）
        m.setMemberExpireTime(LocalDateTime.now().plusDays(memberService.freeTrialDays()));
        Merchant saved = merchantRepository.save(m);
        // 用户要求（2026-08-18）：每个商家默认配置——预置档位 + 默认权重，开箱即用
        initDefaultPools(saved.getMerchantNo());
        return saved;
    }

    /**
     * 商家默认配置（用户拍板 2026-08-18）：
     * 折扣券 12 档（98/95/90/88/85/80/75/7/65/60/55/50 折）+ 立减券 4 档（5/10/15/20 元）
     * + 本次免单 + 本次免单免一（立减 9999 元实现免单，免一档限额=1）。
     * 默认权重：私有池 100 / 公共 0；大类 折扣50 / 立减&余额50；档位均等 weight=1。
     */
    private void initDefaultPools(String merchantNo) {
        LocalDateTime now = LocalDateTime.now();
        // 折扣券 12 档（prizeType=1，值为折扣率）
        double[] discounts = {9.8, 9.5, 9.0, 8.8, 8.5, 8.0, 7.5, 7.0, 6.5, 6.0, 5.5, 5.0};
        for (double v : discounts) {
            saveDefaultPool(merchantNo, 1, java.math.BigDecimal.valueOf(v), 1, "默认折扣券" + v + "折", null, null, 0, now);
        }
        // 立减券 4 档
        int[] cuts = {5, 10, 15, 20};
        for (int v : cuts) {
            saveDefaultPool(merchantNo, 2, java.math.BigDecimal.valueOf(v), 1, "默认立减券" + v + "元", null, null, 0, now);
        }
        // 本次免单（立减 9999 = 任何订单免单）
        saveDefaultPool(merchantNo, 2, java.math.BigDecimal.valueOf(9999), 1, "本次免单", null, null, 0, now);
        // 本次免单免一（同免单但限额 1：单商家每日最多 1 次）
        saveDefaultPool(merchantNo, 2, java.math.BigDecimal.valueOf(9999), 1, "本次免单免一", 2, 1, 1, now);
        // 安慰奖：10% 现金存入余额（固定 10 元余额，开奖即入账；按消费比例版见团购登记折算）
        saveDefaultPool(merchantNo, 3, java.math.BigDecimal.valueOf(10), 1, "安慰奖10%现金存入余额", null, null, 0, now);
        // 安慰奖：5% 现金存入余额
        saveDefaultPool(merchantNo, 3, java.math.BigDecimal.valueOf(5), 1, "安慰奖5%现金存入余额", null, null, 0, now);
        // 谢谢参与（无资产）
        saveDefaultPool(merchantNo, 5, java.math.BigDecimal.ZERO, 1, "谢谢参与", null, null, 0, now);
    }

    private void saveDefaultPool(String merchantNo, int prizeType, java.math.BigDecimal value, int weight,
                                 String remark, Integer limitScope, Integer limitCycle, Integer limitMax,
                                 LocalDateTime now) {
        BoxPrizePool pool = new BoxPrizePool();
        pool.setMerchantNo(merchantNo);
        pool.setPrizeType(prizeType);
        pool.setPrizeValue(value);
        pool.setWeight(weight);
        pool.setEnabled(1);
        pool.setRemark(remark);
        pool.setIsPutPublic(0);
        pool.setIsSupportIbigou(0);
        pool.setLimitScope(limitScope == null ? 1 : limitScope);   // 默认 1 平台全局
        pool.setLimitCycle(limitCycle == null ? 1 : limitCycle);   // 默认 1 每日
        pool.setLimitMax(limitMax == null ? 0 : limitMax);         // 默认 0 不限
        pool.setCreateTime(now);
        pool.setUpdateTime(now);
        boxPrizePoolRepository.save(pool);
    }

    @Transactional
    public void toggleMerchant(String merchantNo, boolean enabled) {
        Merchant m = merchantRepository.findById(merchantNo)
                .orElseThrow(() -> new BizException("商家不存在"));
        m.setStatus(enabled ? 1 : 0);
        m.setUpdateTime(LocalDateTime.now());
        merchantRepository.save(m);
    }

    @Transactional
    public void resetPwd(String merchantNo, String newPwd) {
        if (newPwd == null || newPwd.length() < 6) {
            throw new BizException("新密码长度至少 6 位");
        }
        Merchant m = merchantRepository.findById(merchantNo)
                .orElseThrow(() -> new BizException("商家不存在"));
        m.setLoginPwd(encoder.encode(newPwd));
        m.setUpdateTime(LocalDateTime.now());
        merchantRepository.save(m);
    }

    public List<Merchant> listMerchants() {
        return merchantRepository.findAll();
    }

    /** 校验管理员密码（敏感操作二次验证） */
    public void verifyAdminPassword(String adminPassword) {
        if (adminPassword == null || adminPassword.isBlank()) {
            throw new BizException("请输入管理员密码确认操作");
        }
        AdminUser admin = adminUserRepository.findByAccount("admin")
                .orElseThrow(() -> new BizException("管理员账号异常"));
        if (!encoder.matches(adminPassword, admin.getLoginPwd())) {
            throw new BizException("管理员密码错误，操作失败");
        }
    }

    /** 删除商家：需管理员密码验证；删除商家及私有配置，业务流水保留（审计完整性） */
    @Transactional
    public void deleteMerchant(String merchantNo, String adminPassword) {
        if (!merchantRepository.existsById(merchantNo)) {
            throw new BizException("商家不存在");
        }
        verifyAdminPassword(adminPassword);
        // 删除商家私有配置（业务流水保留：订单/券/余额流水/审计/会员订单等）
        boxPrizePoolRepository.deleteAll(boxPrizePoolRepository.findByMerchantNoOrderByPrizeIdDesc(merchantNo));
        boxGroupPrizePoolRepository.deleteAll(boxGroupPrizePoolRepository.findByMerchantNo(merchantNo));
        boxPublicPoolRepository.deleteAll(boxPublicPoolRepository.findBySourceMerchantNo(merchantNo));
        merchantSessionRepository.deleteAll(merchantSessionRepository.findByMerchantNo(merchantNo));
        boxPrizeLimitStatRepository.deleteByMerchantNo(merchantNo);
        merchantRepository.deleteById(merchantNo);
    }
}
