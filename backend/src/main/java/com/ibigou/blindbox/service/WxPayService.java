package com.ibigou.blindbox.service;

import com.ibigou.blindbox.common.BizException;
import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.payments.model.Transaction;
import com.wechat.pay.java.service.payments.nativepay.NativePayService;
import com.wechat.pay.java.service.payments.nativepay.model.Amount;
import com.wechat.pay.java.service.payments.nativepay.model.PrepayRequest;
import com.wechat.pay.java.service.payments.nativepay.model.PrepayResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * V1.5 平台微信支付（Native 模式，收商家年费）。
 * <p>配置（平台后台，脱敏）：wx_pay_enabled（0=测试mock，1=真实）、wx_pay_mch_id、wx_pay_app_id、
 * wx_pay_api_key、wx_pay_api_v3_key、wx_pay_cert_path（商户证书 pem）、wx_pay_cert_serial。</p>
 * <p>真实微信需商户号+证书+公网回调；测试环境用 mock（wx_pay_enabled=0，走测试确认接口）。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WxPayService {

    private final GlobalConfigService configService;
    private final MemberService memberService;

    public boolean enabled() {
        return "1".equals(configService.get("wx_pay_enabled", "0"));
    }

    /** Native 下单：返回 code_url（用户扫码/跳转微信付款）；未配置/关闭返回 null（测试走确认接口） */
    public String nativePay(String outTradeNo, BigDecimal amountYuan, String description, String notifyUrl) {
        if (!enabled()) {
            log.info("微信支付未开启(测试mock)，订单 {}", outTradeNo);
            return null;
        }
        RSAAutoCertificateConfig config = buildConfig();
        NativePayService service = new NativePayService.Builder().config(config).build();
        PrepayRequest request = new PrepayRequest();
        request.setAppid(configService.get("wx_pay_app_id"));
        request.setMchid(configService.get("wx_pay_mch_id"));
        request.setDescription(description);
        request.setOutTradeNo(outTradeNo);
        request.setNotifyUrl(notifyUrl);
        Amount amount = new Amount();
        amount.setTotal(amountYuan.multiply(BigDecimal.valueOf(100)).intValue());
        amount.setCurrency("CNY");
        request.setAmount(amount);
        PrepayResponse resp = service.prepay(request);
        return resp.getCodeUrl();
    }

    /** 查单：返回交易状态（SUCCESS/CLOSED/NOTPAY 等）；未配置返回 NOTPAY */
    public String queryOrderState(String outTradeNo) {
        if (!enabled()) {
            return "NOTPAY";
        }
        try {
            RSAAutoCertificateConfig config = buildConfig();
            NativePayService service = new NativePayService.Builder().config(config).build();
            com.wechat.pay.java.service.payments.nativepay.model.QueryOrderByOutTradeNoRequest q = new com.wechat.pay.java.service.payments.nativepay.model.QueryOrderByOutTradeNoRequest();
            q.setMchid(configService.get("wx_pay_mch_id"));
            q.setOutTradeNo(outTradeNo);
            Transaction tx = service.queryOrderByOutTradeNo(q);
            return tx.getTradeState() == null ? "UNKNOWN" : tx.getTradeState().name();
        } catch (Exception e) {
            throw new BizException("微信查单失败: " + e.getMessage());
        }
    }

    /** 微信支付回调：验签解密 → 确认续费订单支付 */
    public String handleNotify(String body, String wechatSignature, String wechatTimestamp,
                               String wechatNonce, String wechatSerial) {
        if (!enabled()) {
            throw new BizException("微信支付未开启");
        }
        // RSAAutoCertificateConfig 直接实现 NotificationConfig，可作回调验签/解密配置
        NotificationParser parser = new NotificationParser(buildConfig());
        RequestParam param = new RequestParam.Builder()
                .serialNumber(wechatSerial)
                .nonce(wechatNonce)
                .signature(wechatSignature)
                .timestamp(wechatTimestamp)
                .body(body)
                .build();
        Transaction transaction = parser.parse(param, Transaction.class);
        String outTradeNo = transaction.getOutTradeNo();
        if (transaction.getTradeState() != Transaction.TradeStateEnum.SUCCESS) {
            log.warn("微信回调非成功状态 {} {}", outTradeNo, transaction.getTradeState());
            return "{\"code\":\"FAIL\",\"message\":\"trade not success\"}";
        }
        memberService.confirmRenewPaid(outTradeNo, "wxpay-notify");
        return "{\"code\":\"SUCCESS\"}";
    }

    private RSAAutoCertificateConfig buildConfig() {
        String mchId = configService.get("wx_pay_mch_id", "");
        String appId = configService.get("wx_pay_app_id", "");
        String apiV3Key = configService.get("wx_pay_api_v3_key", "");
        String certSerial = configService.get("wx_pay_cert_serial", "");
        String certPath = configService.get("wx_pay_cert_path", "");
        if (mchId.isBlank() || appId.isBlank() || apiV3Key.isBlank() || certSerial.isBlank() || certPath.isBlank()) {
            throw new BizException("平台微信支付未配置完整，请先在平台后台配置");
        }
        String privateKey;
        try {
            privateKey = Files.readString(Paths.get(certPath));
        } catch (Exception e) {
            throw new BizException("商户证书读取失败: " + e.getMessage());
        }
        return new RSAAutoCertificateConfig.Builder()
                .merchantId(mchId)
                .privateKey(privateKey)
                .merchantSerialNumber(certSerial)
                .apiV3Key(apiV3Key)
                .build();
    }
}
