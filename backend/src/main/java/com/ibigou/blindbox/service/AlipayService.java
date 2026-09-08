package com.ibigou.blindbox.service;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.request.AlipayTradePrecreateRequest;
import com.alipay.api.request.AlipayTradeWapPayRequest;
import com.alipay.api.response.AlipayTradePrecreateResponse;
import com.alipay.api.response.AlipayTradeWapPayResponse;
import com.ibigou.blindbox.common.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 支付宝支付服务（扫码支付模式）。
 * <p>配置（平台后台，脱敏）：alipay_enabled（0=测试mock，1=真实）、alipay_app_id、
 * alipay_merchant_private_key、alipay_public_key、alipay_gateway_url、alipay_notify_url。</p>
 * <p>真实支付宝需商户号+应用私钥+公网回调；测试环境用 mock（alipay_enabled=0）。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AlipayService {

    private final GlobalConfigService configService;
    private final OfflineOrderService offlineOrderService;

    public boolean enabled() {
        return "1".equals(configService.get("alipay_enabled", "0"));
    }

    /** WAP 支付下单：返回自动提交的 form HTML（拉起支付宝收银台）；未配置/关闭返回 null */
    public String wapPay(String outTradeNo, BigDecimal amountYuan, String subject, String notifyUrl, String returnUrl) {
        if (!enabled()) {
            log.info("支付宝支付未开启(测试mock)，订单 {}", outTradeNo);
            return null;
        }
        AlipayClient client = buildClient();
        AlipayTradeWapPayRequest request = new AlipayTradeWapPayRequest();
        request.setNotifyUrl(notifyUrl);
        if (returnUrl != null && !returnUrl.isBlank()) {
            request.setReturnUrl(returnUrl);
        }
        request.setBizContent("{" +
                "\"out_trade_no\":\"" + outTradeNo + "\"," +
                "\"total_amount\":\"" + amountYuan.toPlainString() + "\"," +
                "\"subject\":\"" + subject + "\"," +
                "\"product_code\":\"QUICK_WAP_WAY\"," +
                "\"timeout_express\":\"30m\"" +
                "}");
        try {
            AlipayTradeWapPayResponse response = client.pageExecute(request);
            if (response.isSuccess()) {
                return response.getBody();
            } else {
                throw new BizException("支付宝WAP支付下单失败: " + response.getMsg() + " " + response.getSubMsg());
            }
        } catch (AlipayApiException e) {
            throw new BizException("支付宝WAP支付下单失败: " + e.getMessage());
        }
    }

    /** 扫码下单：返回 qr_code（用户扫码付款）；未配置/关闭返回 null（测试走确认接口） */
    public String precreate(String outTradeNo, BigDecimal amountYuan, String subject, String notifyUrl) {
        if (!enabled()) {
            log.info("支付宝支付未开启(测试mock)，订单 {}", outTradeNo);
            return null;
        }
        AlipayClient client = buildClient();
        AlipayTradePrecreateRequest request = new AlipayTradePrecreateRequest();
        request.setNotifyUrl(notifyUrl);
        request.setBizContent("{" +
                "\"out_trade_no\":\"" + outTradeNo + "\"," +
                "\"total_amount\":\"" + amountYuan.toPlainString() + "\"," +
                "\"subject\":\"" + subject + "\"," +
                "\"timeout_express\":\"30m\"" +
                "}");
        try {
            AlipayTradePrecreateResponse response = client.execute(request);
            if (response.isSuccess()) {
                return response.getQrCode();
            } else {
                throw new BizException("支付宝支付下单失败: " + response.getMsg() + " " + response.getSubMsg());
            }
        } catch (AlipayApiException e) {
            throw new BizException("支付宝支付下单失败: " + e.getMessage());
        }
    }

    /** 支付宝回调验签：验签通过后确认订单支付 */
    public String handleNotify(Map<String, String> params) {
        if (!enabled()) {
            throw new BizException("支付宝支付未开启");
        }
        try {
            boolean signVerified = com.alipay.api.internal.util.AlipaySignature.rsaCheckV1(
                    params,
                    configService.get("alipay_public_key", ""),
                    "UTF-8",
                    "RSA2");
            if (!signVerified) {
                log.warn("支付宝回调验签失败");
                return "fail";
            }
            String outTradeNo = params.get("out_trade_no");
            String tradeStatus = params.get("trade_status");
            if ("TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus)) {
                log.info("支付宝回调支付成功，订单 {}", outTradeNo);
                // 确认线下订单支付（如果是线下订单）
                try {
                    offlineOrderService.confirmPaid(outTradeNo, "alipay-notify");
                } catch (Exception e) {
                    log.warn("确认订单支付失败: {}", e.getMessage());
                }
            }
            return "success";
        } catch (AlipayApiException e) {
            log.error("支付宝回调验签异常", e);
            return "fail";
        }
    }

    private AlipayClient buildClient() {
        String appId = configService.get("alipay_app_id", "");
        String privateKey = configService.get("alipay_merchant_private_key", "");
        String publicKey = configService.get("alipay_public_key", "");
        String gatewayUrl = configService.get("alipay_gateway_url", "https://openapi.alipay.com/gateway.do");
        if (appId.isBlank() || privateKey.isBlank() || publicKey.isBlank()) {
            throw new BizException("平台支付宝支付未配置完整，请先在平台后台配置");
        }
        return new DefaultAlipayClient(gatewayUrl, appId, privateKey, "json", "UTF-8", publicKey, "RSA2");
    }
}