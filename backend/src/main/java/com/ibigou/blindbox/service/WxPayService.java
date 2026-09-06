package com.ibigou.blindbox.service;

import com.ibigou.blindbox.common.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;

/**
 * 平台微信支付 V2 版本（Native 扫码支付）。
 * <p>配置：wx_pay_enabled（0=测试mock，1=真实）、wx_pay_mch_id、wx_pay_app_id、wx_pay_api_key（V2密钥）。</p>
 * <p>V2 版本不需要证书，Native 支付只需商户号+AppID+API密钥。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WxPayService {

    private final GlobalConfigService configService;
    private final MemberService memberService;
    private final OfflineOrderService offlineOrderService;

    private static final String UNIFIED_ORDER_URL = "https://api.mch.weixin.qq.com/pay/unifiedorder";
    private static final String ORDER_QUERY_URL = "https://api.mch.weixin.qq.com/pay/orderquery";
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public boolean enabled() {
        return "1".equals(configService.get("wx_pay_enabled", "0"));
    }

    /** Native 下单：返回 code_url（扫码支付链接）；未开启返回 null */
    public String nativePay(String outTradeNo, BigDecimal amountYuan, String description, String notifyUrl) {
        if (!enabled()) {
            log.info("微信支付未开启(测试mock)，订单 {}", outTradeNo);
            return null;
        }
        String appId = configService.get("wx_pay_app_id", "");
        String mchId = configService.get("wx_pay_mch_id", "");
        String apiKey = configService.get("wx_pay_api_key", "");
        if (appId.isBlank() || mchId.isBlank() || apiKey.isBlank()) {
            throw new BizException("微信支付未配置完整（AppID/商户号/API密钥）");
        }

        SortedMap<String, String> params = new TreeMap<>();
        params.put("appid", appId);
        params.put("mch_id", mchId);
        params.put("nonce_str", UUID.randomUUID().toString().replace("-", ""));
        params.put("body", description.length() > 128 ? description.substring(0, 128) : description);
        params.put("out_trade_no", outTradeNo);
        params.put("total_fee", amountYuan.multiply(BigDecimal.valueOf(100)).intValue() + "");
        params.put("spbill_create_ip", "127.0.0.1");
        params.put("notify_url", notifyUrl);
        params.put("trade_type", "NATIVE");
        params.put("sign", sign(params, apiKey));

        String xml = toXml(params);
        log.info("微信V2下单请求: {}", xml);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(UNIFIED_ORDER_URL))
                    .header("Content-Type", "application/xml; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(xml, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String respXml = response.body();
            log.info("微信V2下单响应: {}", respXml);
            Map<String, String> resp = parseXml(respXml);
            if (!"SUCCESS".equals(resp.get("return_code"))) {
                throw new BizException("微信支付下单失败: " + resp.get("return_msg"));
            }
            if (!"SUCCESS".equals(resp.get("result_code"))) {
                throw new BizException("微信支付下单失败: " + resp.get("err_code") + " " + resp.get("err_code_des"));
            }
            return resp.get("code_url");
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("微信支付下单失败: " + e.getMessage());
        }
    }

    /** 查单：返回 SUCCESS/NOTPAY/CLOSED/UNKNOWN */
    public String queryOrderState(String outTradeNo) {
        if (!enabled()) {
            return "NOTPAY";
        }
        String appId = configService.get("wx_pay_app_id", "");
        String mchId = configService.get("wx_pay_mch_id", "");
        String apiKey = configService.get("wx_pay_api_key", "");

        SortedMap<String, String> params = new TreeMap<>();
        params.put("appid", appId);
        params.put("mch_id", mchId);
        params.put("out_trade_no", outTradeNo);
        params.put("nonce_str", UUID.randomUUID().toString().replace("-", ""));
        params.put("sign", sign(params, apiKey));

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ORDER_QUERY_URL))
                    .header("Content-Type", "application/xml; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(toXml(params), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            Map<String, String> resp = parseXml(response.body());
            if (!"SUCCESS".equals(resp.get("return_code")) || !"SUCCESS".equals(resp.get("result_code"))) {
                return "UNKNOWN";
            }
            String tradeState = resp.get("trade_state");
            if ("SUCCESS".equals(tradeState)) return "SUCCESS";
            if ("NOTPAY".equals(tradeState) || "USERPAYING".equals(tradeState)) return "NOTPAY";
            if ("CLOSED".equals(tradeState) || "REVOKED".equals(tradeState) || "PAYERROR".equals(tradeState)) return "CLOSED";
            return "UNKNOWN";
        } catch (Exception e) {
            log.error("微信查单异常", e);
            return "UNKNOWN";
        }
    }

    /** 微信V2回调：验签 → 确认订单支付。返回微信要求的XML响应 */
    public String handleNotify(String body) {
        if (!enabled()) {
            throw new BizException("微信支付未开启");
        }
        String apiKey = configService.get("wx_pay_api_key", "");
        try {
            Map<String, String> data = parseXml(body);
            if (!"SUCCESS".equals(data.get("return_code"))) {
                return wxResp("FAIL", "return_code not SUCCESS");
            }
            // 验签
            String sign = data.remove("sign");
            String calcSign = sign(data, apiKey);
            if (!calcSign.equalsIgnoreCase(sign)) {
                log.warn("微信回调验签失败，期望 {} 实际 {}", calcSign, sign);
                return wxResp("FAIL", "签名验证失败");
            }
            if (!"SUCCESS".equals(data.get("result_code"))) {
                return wxResp("FAIL", "result_code not SUCCESS");
            }
            String outTradeNo = data.get("out_trade_no");
            String transactionId = data.get("transaction_id");
            log.info("微信V2回调成功，订单={}, 微信订单号={}", outTradeNo, transactionId);
            // 根据订单号前缀判断订单类型
            if (outTradeNo != null && outTradeNo.startsWith("OFF-")) {
                offlineOrderService.confirmPaid(outTradeNo, "wechat");
            } else {
                memberService.confirmRenewPaid(outTradeNo, "wxpay-notify");
            }
            return wxResp("SUCCESS", "OK");
        } catch (Exception e) {
            log.error("微信回调处理异常", e);
            return wxResp("FAIL", e.getMessage());
        }
    }

    // ========== 工具方法 ==========

    private String sign(Map<String, String> params, String apiKey) {
        SortedMap<String, String> sorted = new TreeMap<>(params);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : sorted.entrySet()) {
            if (entry.getValue() != null && !entry.getValue().isEmpty() && !"sign".equals(entry.getKey())) {
                sb.append(entry.getKey()).append("=").append(entry.getValue()).append("&");
            }
        }
        sb.append("key=").append(apiKey);
        return md5(sb.toString()).toUpperCase();
    }

    private String md5(String str) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] bytes = md.digest(str.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : bytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("MD5失败", e);
        }
    }

    private String toXml(Map<String, String> params) {
        StringBuilder sb = new StringBuilder("<xml>");
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (entry.getValue() != null) {
                sb.append("<").append(entry.getKey()).append(">")
                  .append("<![CDATA[").append(entry.getValue()).append("]]>")
                  .append("</").append(entry.getKey()).append(">");
            }
        }
        sb.append("</xml>");
        return sb.toString();
    }

    private Map<String, String> parseXml(String xml) throws Exception {
        Map<String, String> map = new HashMap<>();
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        NodeList nodes = doc.getDocumentElement().getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                map.put(node.getNodeName(), node.getTextContent());
            }
        }
        return map;
    }

    private String wxResp(String code, String msg) {
        return "<xml><return_code><![CDATA[" + code + "]]></return_code><return_msg><![CDATA[" + msg + "]]></return_msg></xml>";
    }
}
