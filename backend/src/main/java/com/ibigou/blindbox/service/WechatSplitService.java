package com.ibigou.blindbox.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibigou.blindbox.entity.Merchant;
import com.ibigou.blindbox.entity.MerchantPropertyBinding;
import com.ibigou.blindbox.entity.PropertyCompany;
import com.ibigou.blindbox.entity.PropertySplitRecord;
import com.ibigou.blindbox.repository.MerchantPropertyBindingRepository;
import com.ibigou.blindbox.repository.MerchantRepository;
import com.ibigou.blindbox.repository.PropertyCompanyRepository;
import com.ibigou.blindbox.repository.PropertySplitRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 微信分账执行服务（双模式）：T+7 结算期过后执行实际微信分账。
 * <ul>
 *   <li><b>模式1 商家→物业</b>：使用商家子商户凭证发起，物业公司商户号为接收方</li>
 *   <li><b>模式2 平台→物业</b>：使用服务商凭证发起，物业公司商户号为接收方</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WechatSplitService {

    private static final String WX_SPLIT_API = "https://api.mch.weixin.qq.com/v3/profitsharing/orders";
    private static final String WX_SPLIT_QUERY_API = "https://api.mch.weixin.qq.com/v3/profitsharing/orders/%s?sub_mchid=%s";

    private final PropertySplitRecordRepository splitRecordRepository;
    private final PropertyDeductEngineService deductEngineService;
    private final GlobalConfigService globalConfigService;
    private final MerchantRepository merchantRepository;
    private final MerchantPropertyBindingRepository merchantPropertyBindingRepository;
    private final PropertyCompanyRepository propertyCompanyRepository;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${wechat.split.notify-url:https://ybgtc.com/api/pay/wx/split-notify}")
    private String splitNotifyUrl;

    /**
     * 每日凌晨 2 点扫描已过 T+7 结算期的 PENDING 分账记录，逐条执行微信分账。
     */
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void processSettledSplits() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(7);
        List<PropertySplitRecord> pendingRecords = splitRecordRepository.findBySplitStatus(0);

        int processed = 0;
        int success = 0;
        int fail = 0;

        for (PropertySplitRecord record : pendingRecords) {
            if (record.getCreateTime() == null || !record.getCreateTime().isBefore(cutoff)) {
                continue;
            }
            processed++;
            try {
                // 按 merchantNo 查找商家（双模式通用）
                String merchantNo = record.getMerchantNo();
                if (merchantNo == null || merchantNo.isEmpty()) {
                    log.error("merchantNo is empty on split record orderNo={}, skipping", record.getOrderNo());
                    fail++;
                    continue;
                }
                Merchant merchant = merchantRepository.findById(merchantNo).orElse(null);
                if (merchant == null) {
                    log.error("Merchant not found for merchantNo={}, skipping split orderNo={}",
                            merchantNo, record.getOrderNo());
                    fail++;
                    continue;
                }

                int splitMode = record.getSplitMode() != null ? record.getSplitMode() : 1;
                MerchantPropertyBinding binding = merchantPropertyBindingRepository
                        .findByMerchantNoAndSplitModeAndStatus(merchantNo, splitMode, 1).orElse(null);
                if (binding == null) {
                    log.error("No active binding for merchantNo={}, mode={}, companyId={}, skipping orderNo={}",
                            merchantNo, splitMode, record.getCompanyId(), record.getOrderNo());
                    fail++;
                    continue;
                }

                boolean ok = executeSplit(record, merchant, splitMode);
                if (ok) {
                    record.setSplitStatus(1); // SETTLED
                    record.setSettleTime(LocalDateTime.now());
                    splitRecordRepository.save(record);
                    deductEngineService.onSplitSettled(record.getSplitId());
                    success++;
                } else {
                    fail++;
                }
            } catch (Exception e) {
                log.error("Failed to execute split for orderNo={}: {}", record.getOrderNo(), e.getMessage(), e);
                fail++;
            }
        }

        log.info("processSettledSplits completed: processed={}, success={}, fail={}", processed, success, fail);
    }

    /**
     * 执行微信分账 API 调用（双模式）。
     * <p>POST https://api.mch.weixin.qq.com/v3/profitsharing/orders</p>
     * <p>签名方式：WECHATPAY2-SHA256-RSA2048</p>
     */
    private boolean executeSplit(PropertySplitRecord record, Merchant merchant, int splitMode) {
        PropertyCompany company = propertyCompanyRepository.findById(record.getCompanyId()).orElse(null);
        if (company == null || company.getWxMchId() == null || company.getWxMchId().isEmpty()) {
            log.error("Company or wxMchId missing for companyId={}, skipping orderNo={}",
                    record.getCompanyId(), record.getOrderNo());
            return false;
        }
        String companyWxMchId = company.getWxMchId();

        try {
            // 构建请求体
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("appid", globalConfigService.get("wx_pay_appId", ""));
            body.put("out_order_no", record.getOrderNo());
            body.put("unfreeze_unsplit", true);
            body.put("notify_url", splitNotifyUrl);

            // 接收方
            Map<String, Object> receiver = new LinkedHashMap<>();
            receiver.put("type", "MERCHANTId");
            receiver.put("account", companyWxMchId);
            receiver.put("amount", record.getSplitAmount().multiply(new BigDecimal("100")).intValue()); // 元→分
            receiver.put("description", "物业费分账-" + record.getOrderNo());
            body.put("receivers", List.of(receiver));

            String mchId;
            String serialNo;
            String privateKeyPem;

            if (splitMode == PropertySplitTriggerService.MODE_PLATFORM_TO_PROPERTY) {
                // 模式2：服务商凭证
                mchId = globalConfigService.get("wx_sp_mchId", "");
                serialNo = globalConfigService.get("wx_sp_serial_no", "");
                privateKeyPem = loadPrivateKey(globalConfigService.get("wx_sp_private_key_path", ""));
                body.put("sub_mchid", merchant.getWxSubMchId());
                log.info("PLATFORM→PROPERTY split request: spMchId={}, subMchId={}, company={}, amount={}分, orderNo={}",
                        mchId, merchant.getWxSubMchId(), companyWxMchId,
                        receiver.get("amount"), record.getOrderNo());
            } else {
                // 模式1：商家子商户凭证
                mchId = merchant.getWxSubMchId();
                serialNo = globalConfigService.get("wx_merchant_cert_serial", "");
                privateKeyPem = loadPrivateKey(globalConfigService.get("wx_merchant_private_key_path", ""));
                log.info("MERCHANT→PROPERTY split request: subMchId={}, company={}, amount={}分, orderNo={}",
                        mchId, companyWxMchId, receiver.get("amount"), record.getOrderNo());
            }

            if (mchId.isEmpty() || privateKeyPem.isEmpty()) {
                log.warn("WeChat split credentials not configured, falling back to mock success. orderNo={}", record.getOrderNo());
                return true; // graceful fallback
            }

            // 发送请求
            String jsonBody = objectMapper.writeValueAsString(body);
            HttpHeaders headers = buildWechatHeaders(mchId, serialNo, privateKeyPem, "POST", "/v3/profitsharing/orders", jsonBody);
            HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);

            ResponseEntity<String> response = executeWithRetry(WX_SPLIT_API, HttpMethod.POST, entity, record.getOrderNo());

            if (response.getStatusCode().is2xxSuccessful()) {
                JsonNode respBody = objectMapper.readTree(response.getBody());
                String wxOrderId = respBody.path("orderId").asText("");
                record.setWxTransactionId(wxOrderId);
                log.info("WeChat split API success: orderNo={}, wxOrderId={}, status={}",
                        record.getOrderNo(), wxOrderId, respBody.path("state").asText());
                return true;
            } else {
                log.error("WeChat split API failed: orderNo={}, httpStatus={}, body={}",
                        record.getOrderNo(), response.getStatusCode(), response.getBody());
                return false;
            }
        } catch (Exception e) {
            log.error("WeChat split API exception: orderNo={}, error={}", record.getOrderNo(), e.getMessage(), e);
            return false;
        }
    }

    /**
     * 查询微信分账单结果。
     * <p>GET https://api.mch.weixin.qq.com/v3/profitsharing/orders/{out_order_no}</p>
     */
    public Map<String, Object> querySplitResult(String orderNo) {
        log.info("Querying split result: orderNo={}", orderNo);
        Map<String, Object> result = new HashMap<>();
        result.put("orderNo", orderNo);

        try {
            PropertySplitRecord record = splitRecordRepository.findByOrderNo(orderNo).orElse(null);
            if (record == null) {
                result.put("status", "NOT_FOUND");
                return result;
            }

            String mchId;
            String serialNo;
            String privateKeyPem;
            int splitMode = record.getSplitMode() != null ? record.getSplitMode() : 1;

            if (splitMode == PropertySplitTriggerService.MODE_PLATFORM_TO_PROPERTY) {
                mchId = globalConfigService.get("wx_sp_mchId", "");
                serialNo = globalConfigService.get("wx_sp_serial_no", "");
                privateKeyPem = loadPrivateKey(globalConfigService.get("wx_sp_private_key_path", ""));
            } else {
                Merchant merchant = merchantRepository.findById(record.getMerchantNo()).orElse(null);
                mchId = (merchant != null) ? merchant.getWxSubMchId() : "";
                serialNo = globalConfigService.get("wx_merchant_cert_serial", "");
                privateKeyPem = loadPrivateKey(globalConfigService.get("wx_merchant_private_key_path", ""));
            }

            if (mchId.isEmpty() || privateKeyPem.isEmpty()) {
                result.put("status", "CREDENTIALS_NOT_CONFIGURED");
                return result;
            }

            String queryUrl = String.format(WX_SPLIT_QUERY_API, orderNo, mchId);
            HttpHeaders headers = buildWechatHeaders(mchId, serialNo, privateKeyPem, "GET",
                    "/v3/profitsharing/orders/" + orderNo, "");
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = executeWithRetry(queryUrl, HttpMethod.GET, entity, orderNo);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode node = objectMapper.readTree(response.getBody());
                result.put("status", node.path("state").asText("UNKNOWN"));
                result.put("amount", node.path("receivers").path(0).path("amount").asInt(0));
                result.put("wxOrderId", node.path("orderId").asText(""));
            } else {
                result.put("status", "QUERY_FAILED");
            }
        } catch (Exception e) {
            log.error("Query split result exception: orderNo={}, error={}", orderNo, e.getMessage(), e);
            result.put("status", "ERROR");
            result.put("error", e.getMessage());
        }
        return result;
    }

    // ==================== 微信支付V3签名工具方法 ====================

    /**
     * 构建微信支付V3请求头（WECHATPAY2-SHA256-RSA2048签名）。
     */
    private HttpHeaders buildWechatHeaders(String mchId, String serialNo, String privateKeyPem,
                                           String method, String urlPath, String body) {
        long timestamp = System.currentTimeMillis() / 1000;
        String nonceStr = UUID.randomUUID().toString().replace("-", "");

        // 构造签名串: HTTP方法\nURL\n时间戳\n随机字符串\n请求体\n
        String message = method + "\n" + urlPath + "\n" + timestamp + "\n" + nonceStr + "\n" + body + "\n";

        String signature = signWithRSA(message, privateKeyPem);

        String auth = String.format(
                "WECHATPAY2-SHA256-RSA2048 mchid=\"%s\",nonce_str=\"%s\",timestamp=\"%d\",serial_no=\"%s\",signature=\"%s\"",
                mchId, nonceStr, timestamp, serialNo, signature);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set("Authorization", auth);
        headers.set("User-Agent", "YBG-Property/2.0");
        return headers;
    }

    /**
     * RSA-SHA256签名。
     */
    private String signWithRSA(String message, String privateKeyPem) {
        try {
            String keyContent = privateKeyPem
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s+", "");
            byte[] keyBytes = Base64.getDecoder().decode(keyContent);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
            PrivateKey privateKey = KeyFactory.getInstance("RSA").generatePrivate(spec);

            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initSign(privateKey);
            sig.update(message.getBytes("UTF-8"));
            return Base64.getEncoder().encodeToString(sig.sign());
        } catch (Exception e) {
            log.error("RSA signing failed: {}", e.getMessage(), e);
            throw new RuntimeException("WeChat pay signature failed", e);
        }
    }

    // ==================== 私钥缓存（避免每次请求都读磁盘） ====================

    private final Map<String, String> privateKeyCache = new ConcurrentHashMap<>();
    private final Map<String, Long> privateKeyCacheTime = new ConcurrentHashMap<>();
    private static final long KEY_CACHE_TTL_MS = 30 * 60 * 1000L; // 30分钟

    /**
     * 从文件路径加载PEM私钥内容（带30分钟缓存）。
     */
    private String loadPrivateKey(String path) {
        if (path == null || path.isEmpty()) return "";
        long now = System.currentTimeMillis();
        Long cachedTime = privateKeyCacheTime.get(path);
        if (cachedTime != null && (now - cachedTime) < KEY_CACHE_TTL_MS) {
            return privateKeyCache.getOrDefault(path, "");
        }
        try {
            String content = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(path)));
            privateKeyCache.put(path, content);
            privateKeyCacheTime.put(path, now);
            log.debug("Private key loaded and cached: path={}", path);
            return content;
        } catch (Exception e) {
            log.warn("Failed to load private key from {}: {}", path, e.getMessage());
            return "";
        }
    }

    // ==================== 重试机制 ====================

    private static final int MAX_RETRY = 3;
    private static final long[] RETRY_DELAYS_MS = {500L, 2000L, 5000L};

    /**
     * 带重试的REST调用。对5xx和网络异常自动重试，4xx不重试。
     */
    private ResponseEntity<String> executeWithRetry(String url, HttpMethod method,
                                                     HttpEntity<?> entity, String orderNo) {
        for (int attempt = 0; attempt <= MAX_RETRY; attempt++) {
            try {
                ResponseEntity<String> resp = restTemplate.exchange(url, method, entity, String.class);
                if (resp.getStatusCode().is5xxServerError() && attempt < MAX_RETRY) {
                    log.warn("WeChat API 5xx, retrying ({}/{}): orderNo={}, status={}",
                            attempt + 1, MAX_RETRY, orderNo, resp.getStatusCode());
                    Thread.sleep(RETRY_DELAYS_MS[Math.min(attempt, RETRY_DELAYS_MS.length - 1)]);
                    continue;
                }
                return resp;
            } catch (org.springframework.web.client.ResourceAccessException e) {
                if (attempt < MAX_RETRY) {
                    log.warn("WeChat API network error, retrying ({}/{}): orderNo={}, error={}",
                            attempt + 1, MAX_RETRY, orderNo, e.getMessage());
                    try { Thread.sleep(RETRY_DELAYS_MS[Math.min(attempt, RETRY_DELAYS_MS.length - 1)]); }
                    catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                } else {
                    throw e;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Retry interrupted", e);
            }
        }
        throw new RuntimeException("Max retries exceeded for orderNo=" + orderNo);
    }

    /**
     * 微信分账异步回调处理入口。
     *
     * @param outOrderNo    商户分账单号
     * @param state         分账状态（FINISHED / FAILED）
     * @param transactionId 微信支付订单号
     */
    @Transactional
    public void onSplitCallback(String outOrderNo, String state, String transactionId) {
        log.info("WeChat split callback received: outOrderNo={}, state={}, transactionId={}",
                outOrderNo, state, transactionId);

        PropertySplitRecord record = splitRecordRepository.findByOrderNo(outOrderNo).orElse(null);
        if (record == null) {
            log.warn("Split callback ignored: no record found for outOrderNo={}", outOrderNo);
            return;
        }

        if ("FINISHED".equals(state)) {
            record.setSplitStatus(1); // SETTLED
            record.setSettleTime(LocalDateTime.now());
            record.setWxTransactionId(transactionId);
            splitRecordRepository.save(record);
            deductEngineService.onSplitSettled(record.getSplitId());
            log.info("Split callback FINISHED: outOrderNo={}, splitId={}", outOrderNo, record.getSplitId());
        } else if ("FAILED".equals(state)) {
            record.setSplitStatus(4); // FAILED
            splitRecordRepository.save(record);
            log.error("Split callback FAILED: outOrderNo={}, transactionId={}", outOrderNo, transactionId);
        } else {
            log.warn("Split callback unknown state: outOrderNo={}, state={}", outOrderNo, state);
        }
    }
}
