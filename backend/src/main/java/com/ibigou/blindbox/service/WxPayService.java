package com.ibigou.blindbox.service;

import com.alibaba.fastjson.JSON;
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
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;

/**
 * 平台微信支付 V2 版本（Native 扫码支付）。
 * <p>配置：wx_pay_enabled（0=测试mock，1=真实）、wx_pay_mchId、wx_pay_appId、wx_pay_api_key（V2密钥）。</p>
 * <p>V2 版本不需要证书，Native 支付只需商户号+AppID+API密钥。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WxPayService {

    private final GlobalConfigService configService;
    private final MemberService memberService;
    private final OfflineOrderService offlineOrderService;
    private final PropertyBillPaymentService propertyBillPaymentService;
    private final PropertyTempParkingService tempParkingService;
    @org.springframework.context.annotation.Lazy
    private final PropertyReservationService reservationService;

    private static final String UNIFIED_ORDER_URL = "https://api.mch.weixin.qq.com/pay/unifiedorder";
    private static final String ORDER_QUERY_URL = "https://api.mch.weixin.qq.com/pay/orderquery";
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    // 公众号 AccessToken / jsapi_ticket 缓存（有效期2小时，提前5分钟刷新）
    private volatile String accessTokenCache;
    private volatile long accessTokenExpireAt;
    private volatile String jsapiTicketCache;
    private volatile long jsapiTicketExpireAt;

    public boolean enabled() {
        return "1".equals(configService.get("wx_pay_enabled", "0"));
    }

    /** Native 下单：返回 code_url（扫码支付链接）；未开启抛出异常 */
    public String nativePay(String outTradeNo, BigDecimal amountYuan, String description, String notifyUrl) {
        if (!enabled()) {
            log.warn("微信支付未开启，无法发起Native支付，订单 {}", outTradeNo);
            throw new BizException("微信支付未配置");
        }
        return unifiedOrder(outTradeNo, amountYuan, description, notifyUrl, "NATIVE", null);
    }

    /** H5 支付下单：返回 mweb_url（跳转拉起微信收银台）；未开启抛出异常 */
    public String h5Pay(String outTradeNo, BigDecimal amountYuan, String description, String notifyUrl) {
        if (!enabled()) {
            log.warn("微信支付未开启，无法发起H5支付，订单 {}", outTradeNo);
            throw new BizException("微信支付未配置");
        }
        // H5 支付场景信息（必填：场景类型+客户端IP+用户代理）
        String sceneInfo = "{\"h5_info\":{\"type\":\"Wap\",\"wap_url\":\"" + configService.get("IBIGOU_DOMAIN", "https://ybgtc.com") + "\",\"wap_name\":\"宜必购\"}}";
        return unifiedOrder(outTradeNo, amountYuan, description, notifyUrl, "MWEB", sceneInfo);
    }

    /** JSAPI 下单（微信公众号内支付）：返回 wx.chooseWXPay 所需全部参数（prepayId+paySign已算好） */
    public Map<String, String> jsapiPay(String outTradeNo, BigDecimal amountYuan, String description, String notifyUrl, String openid) {
        if (!enabled()) {
            log.warn("微信支付未开启，无法发起JSAPI支付，订单 {}", outTradeNo);
            throw new BizException("微信支付未配置");
        }
        String appId = configService.get("wx_pay_appId", "");
        String mchId = configService.get("wx_pay_mchId", "");
        String apiKey = configService.get("wx_pay_api_key", "");
        if (appId.isBlank() || mchId.isBlank() || apiKey.isBlank()) {
            throw new BizException("微信支付未配置完整（AppID/商户号/API密钥）");
        }
        if (openid == null || openid.isBlank()) {
            throw new BizException("JSAPI支付缺少openid");
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
        params.put("trade_type", "JSAPI");
        params.put("openid", openid);
        params.put("sign", sign(params, apiKey));

        String xml = toXml(params);
        log.info("微信V2下单(JSAPI)请求: {}", xml);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(UNIFIED_ORDER_URL))
                    .header("Content-Type", "application/xml; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(xml, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String respXml = response.body();
            log.info("微信V2下单(JSAPI)响应: {}", respXml);
            Map<String, String> resp = parseXml(respXml);
            if (!"SUCCESS".equals(resp.get("return_code"))) {
                throw new BizException("微信支付下单失败: " + resp.get("return_msg"));
            }
            if (!"SUCCESS".equals(resp.get("result_code"))) {
                throw new BizException("微信支付下单失败: " + resp.get("err_code") + " " + resp.get("err_code_des"));
            }
            String prepayId = resp.get("prepayId");
            if (prepayId == null || prepayId.isBlank()) {
                throw new BizException("微信支付下单失败: 未返回prepayId");
            }
            // 构造 wx.chooseWXPay 参数（V2 MD5签名）
            String timeStamp = System.currentTimeMillis() / 1000 + "";
            String nonceStr = UUID.randomUUID().toString().replace("-", "").substring(0, 32);
            String pkg = "prepayId=" + prepayId;
            SortedMap<String, String> signParams = new TreeMap<>();
            signParams.put("appId", appId);
            signParams.put("nonceStr", nonceStr);
            signParams.put("package", pkg);
            signParams.put("signType", "MD5");
            signParams.put("timeStamp", timeStamp);
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> e : signParams.entrySet()) {
                sb.append(e.getKey()).append("=").append(e.getValue()).append("&");
            }
            sb.append("key=").append(apiKey);
            String paySign = md5(sb.toString()).toUpperCase();

            Map<String, String> result = new HashMap<>();
            result.put("appId", appId);
            result.put("timeStamp", timeStamp);
            result.put("nonceStr", nonceStr);
            result.put("package", pkg);
            result.put("signType", "MD5");
            result.put("paySign", paySign);
            return result;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("微信支付下单失败: " + e.getMessage());
        }
    }

    /** 生成公众号 OAuth2 静默授权链接（scope=snsapi_base，拿 code 后换 openid） */
    public String oauthUrl(String redirectUri) {
        String appId = configService.get("wx_pay_appId", "");
        String secret = configService.get("wx_pay_app_secret", "");
        if (appId.isBlank() || secret.isBlank()) {
            throw new BizException("公众号配置不完整（AppID/AppSecret）");
        }
        try {
            String enc = URLEncoder.encode(redirectUri, StandardCharsets.UTF_8.name());
            return "https://open.weixin.qq.com/connect/oauth2/authorize?appid=" + appId
                    + "&redirect_uri=" + enc
                    + "&response_type=code&scope=snsapi_base&state=ibigou#wechat_redirect";
        } catch (Exception e) {
            throw new BizException("生成授权链接失败: " + e.getMessage());
        }
    }

    /** 用 code 换 openid（公众号 OAuth2 snsapi_base） */
    public String oauthOpenId(String code) {
        String appId = configService.get("wx_pay_appId", "");
        String secret = configService.get("wx_pay_app_secret", "");
        if (appId.isBlank() || secret.isBlank()) {
            throw new BizException("公众号配置不完整（AppID/AppSecret）");
        }
        try {
            String url = "https://api.weixin.qq.com/sns/oauth2/access_token?appid=" + appId
                    + "&secret=" + secret + "&code=" + code + "&grant_type=authorization_code";
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
            HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            log.info("微信OAuth2换openid响应: {}", resp.body());
            Map<String, Object> json = JSON.parseObject(resp.body());
            if (json.containsKey("openid")) {
                return String.valueOf(json.get("openid"));
            }
            throw new BizException("换取openid失败: " + resp.body());
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("换取openid失败: " + e.getMessage());
        }
    }

    /** 公众号 access_token（缓存） */
    public String accessToken() {
        if (accessTokenCache != null && System.currentTimeMillis() < accessTokenExpireAt) {
            return accessTokenCache;
        }
        String appId = configService.get("wx_pay_appId", "");
        String secret = configService.get("wx_pay_app_secret", "");
        if (appId.isBlank() || secret.isBlank()) {
            throw new BizException("公众号配置不完整（AppID/AppSecret）");
        }
        try {
            String url = "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid=" + appId + "&secret=" + secret;
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
            HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            Map<String, Object> json = JSON.parseObject(resp.body());
            if (json.containsKey("access_token")) {
                accessTokenCache = String.valueOf(json.get("access_token"));
                int expires = json.get("expires_in") == null ? 7200 : Integer.parseInt(String.valueOf(json.get("expires_in")));
                accessTokenExpireAt = System.currentTimeMillis() + (expires - 300) * 1000L;
                return accessTokenCache;
            }
            throw new BizException("获取access_token失败: " + resp.body());
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("获取access_token失败: " + e.getMessage());
        }
    }

    /** 公众号 jsapi_ticket（缓存） */
    public String jsapiTicket() {
        if (jsapiTicketCache != null && System.currentTimeMillis() < jsapiTicketExpireAt) {
            return jsapiTicketCache;
        }
        try {
            String url = "https://api.weixin.qq.com/cgi-bin/ticket/getticket?access_token=" + accessToken() + "&type=jsapi";
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
            HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            Map<String, Object> json = JSON.parseObject(resp.body());
            if ("0".equals(String.valueOf(json.get("errcode")))) {
                jsapiTicketCache = String.valueOf(json.get("ticket"));
                jsapiTicketExpireAt = System.currentTimeMillis() + 6900 * 1000L;
                return jsapiTicketCache;
            }
            throw new BizException("获取jsapi_ticket失败: " + resp.body());
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("获取jsapi_ticket失败: " + e.getMessage());
        }
    }

    /** JSSDK wx.config 签名（url 为当前页完整地址，不含#hash） */
    public Map<String, String> jssdkConfig(String url) {
        String appId = configService.get("wx_pay_appId", "");
        String ticket = jsapiTicket();
        String nonceStr = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String timestamp = System.currentTimeMillis() / 1000 + "";
        String raw = "jsapi_ticket=" + ticket + "&noncestr=" + nonceStr + "&timestamp=" + timestamp + "&url=" + url;
        String signature = sha1Hex(raw);
        Map<String, String> m = new HashMap<>();
        m.put("appId", appId);
        m.put("timestamp", timestamp);
        m.put("nonceStr", nonceStr);
        m.put("signature", signature);
        return m;
    }

    private String sha1Hex(String str) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] b = md.digest(str.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte x : b) hex.append(String.format("%02x", x));
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA1失败", e);
        }
    }

    /** 统一下单（V2）：tradeType=NATIVE/MWEB，返回 code_url 或 mweb_url */
    private String unifiedOrder(String outTradeNo, BigDecimal amountYuan, String description, String notifyUrl,
                                String tradeType, String sceneInfo) {
        String appId = configService.get("wx_pay_appId", "");
        String mchId = configService.get("wx_pay_mchId", "");
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
        params.put("trade_type", tradeType);
        if (sceneInfo != null && !sceneInfo.isBlank()) {
            params.put("scene_info", sceneInfo);
        }
        params.put("sign", sign(params, apiKey));

        String xml = toXml(params);
        log.info("微信V2下单({})请求: {}", tradeType, xml);
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
            if ("MWEB".equals(tradeType)) {
                return resp.get("mweb_url");
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
        String appId = configService.get("wx_pay_appId", "");
        String mchId = configService.get("wx_pay_mchId", "");
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
            String transactionId = data.get("transactionId");
            if (transactionId == null || transactionId.isBlank()) {
                transactionId = data.get("transaction_id");
            }
            log.info("微信V2回调成功，订单={}, 微信订单号={}", outTradeNo, transactionId);
            // 根据订单号前缀判断订单类型
            if (outTradeNo != null && outTradeNo.startsWith("PB")) {
                propertyBillPaymentService.confirmPaid(outTradeNo, "wechat");
            } else if (outTradeNo != null && outTradeNo.startsWith("TP")) {
                tempParkingService.onPaymentSuccess(outTradeNo, transactionId);
            } else if (outTradeNo != null && outTradeNo.startsWith("RS")) {
                reservationService.onPaymentSuccess(outTradeNo, transactionId);
            } else if (outTradeNo != null && outTradeNo.startsWith("OFF-")) {
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
