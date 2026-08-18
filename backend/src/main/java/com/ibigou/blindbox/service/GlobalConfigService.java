package com.ibigou.blindbox.service;

import com.ibigou.blindbox.common.BizException;
import com.ibigou.blindbox.entity.SysGlobalConfig;
import com.ibigou.blindbox.repository.SysGlobalConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 全局参数服务：balance_deduct_rate / ibigou_channel_switch。
 * 平台管理员唯一可编辑；商家只读。
 */
@Service
@RequiredArgsConstructor
public class GlobalConfigService {

    public static final int CHANNEL_CLOSED = 0;
    public static final int CHANNEL_OPEN = 1;

    private final SysGlobalConfigRepository configRepository;
    private final ConfigCryptoService cryptoService;

    /** 余额最大抵扣百分比（0-100）；缺省 80 */
    public int balanceDeductRate() {
        return configRepository.findById(SysGlobalConfig.KEY_BALANCE_DEDUCT_RATE)
                .map(c -> Integer.parseInt(c.getConfigValue()))
                .orElse(80);
    }

    /** 通用读取（无则 null；敏感 key 自动解密） */
    public String get(String key) {
        String v = configRepository.findById(key).map(SysGlobalConfig::getConfigValue).orElse(null);
        return cryptoService.isSensitive(key) ? cryptoService.decrypt(v) : v;
    }

    /** 通用读取（带默认值） */
    public String get(String key, String def) {
        String v = get(key);
        return v == null || v.isBlank() ? def : v;
    }

    /** 宜必购渠道总开关 */
    public boolean ibigouChannelOpen() {
        return configRepository.findById(SysGlobalConfig.KEY_IBIGOU_CHANNEL_SWITCH)
                .map(c -> Integer.parseInt(c.getConfigValue()) == CHANNEL_OPEN)
                .orElse(true);
    }

    /** 修改全局参数（平台后台专用） */
    public void update(String key, String value, String remark) {
        if (SysGlobalConfig.KEY_BALANCE_DEDUCT_RATE.equals(key)) {
            int v = Integer.parseInt(value);
            if (v < 0 || v > 100) {
                throw new BizException("balance_deduct_rate 范围必须为 0-100");
            }
        } else if (SysGlobalConfig.KEY_IBIGOU_CHANNEL_SWITCH.equals(key)) {
            int v = Integer.parseInt(value);
            if (v != CHANNEL_CLOSED && v != CHANNEL_OPEN) {
                throw new BizException("ibigou_channel_switch 只能为 0 或 1");
            }
        } else if (java.util.Set.of(
                "free_trial_days", "monthly_price", "renew_gift_switch", "renew_gift_months",
                "wx_pay_mch_id", "wx_pay_app_id", "wx_pay_api_key",
                "wx_pay_enabled", "wx_pay_api_v3_key", "wx_pay_cert_path", "wx_pay_cert_serial",
                "cross_store_return_percent", "test_pay_confirm_enabled",
                "identity_qr_secret", "coupon_valid_days").contains(key)) {
            if ("wx_pay_enabled".equals(key) && !"0".equals(value) && !"1".equals(value)) {
                throw new BizException("微信支付开关只能为 0 或 1");
            }
            if ("cross_store_return_percent".equals(key)
                    && (Integer.parseInt(value) < 0 || Integer.parseInt(value) > 100)) {
                throw new BizException("跨店返还比例范围 0-100");
            }
            if ("free_trial_days".equals(key) && (Integer.parseInt(value) < 0 || Integer.parseInt(value) > 3650)) {
                throw new BizException("免费试用天数范围 0-3650");
            }
            if ("monthly_price".equals(key)) {
                try {
                    new java.math.BigDecimal(value);
                } catch (Exception e) {
                    throw new BizException("月单价必须为数字");
                }
            }
            if ("renew_gift_switch".equals(key) && !"0".equals(value) && !"1".equals(value)) {
                throw new BizException("活动开关只能为 0 或 1");
            }
            if ("renew_gift_months".equals(key) && (Integer.parseInt(value) < 0 || Integer.parseInt(value) > 60)) {
                throw new BizException("赠送月数范围 0-60");
            }
        } else {
            throw new BizException("不支持的全局配置 key: " + key);
        }
        SysGlobalConfig cfg = configRepository.findById(key)
                .orElseGet(() -> {
                    SysGlobalConfig c = new SysGlobalConfig();
                    c.setConfigKey(key);
                    return c;
                });
        cfg.setConfigValue(cryptoService.isSensitive(key) ? cryptoService.encrypt(value) : value);
        cfg.setRemark(remark);
        configRepository.save(cfg);
    }
}
