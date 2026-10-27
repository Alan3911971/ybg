package com.ibigou.blindbox.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibigou.blindbox.entity.AiTaskLog;
import com.ibigou.blindbox.entity.MemberAppointment;
import com.ibigou.blindbox.entity.MemberProfile;
import com.ibigou.blindbox.repository.AiTaskLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * V19 AI Agent 服务（可插拔）。
 *
 * <p><b>设计原则：没有大模型也能用。</b>未配置 <code>app.ai.enabled=true</code> 或缺少 api-key 时，
 * 自动降级为本地规则引擎（确定性话术模板），接口照常返回；配置后走 OpenAI 兼容接口。
 * 每次生成都会落 ai_task_log，标明本次走的是 llm 还是 rule，便于审计与排障。</p>
 *
 * <p>当前四项能力：</p>
 * <ul>
 *   <li>visit_script —— 智能回访话术（输入客户喜好 / 消费 / 回访历史）</li>
 *   <li>birthday_copy —— 生日关怀文案（输入档案 + 券面额）</li>
 *   <li>appointment_reply —— 预约自动回复（确认 / 改期 / 取消）</li>
 *   <li>churn_advice —— 流失预警挽留建议（风险分由规则计算，文案由 AI 生成）</li>
 * </ul>
 */
@Service
@Slf4j
public class AiAgentService {

    public static final String SRC_LLM = "llm";
    public static final String SRC_RULE = "rule";
    public static final String MODEL_RULE = "local-rule";

    private final AiTaskLogRepository aiTaskLogRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.ai.enabled:false}")
    private boolean enabled;

    @Value("${app.ai.base-url:}")
    private String baseUrl;

    @Value("${app.ai.api-key:}")
    private String apiKey;

    @Value("${app.ai.model:qwen-plus}")
    private String model;

    @Value("${app.ai.timeout-ms:15000}")
    private int timeoutMs;

    public AiAgentService(AiTaskLogRepository aiTaskLogRepository) {
        this.aiTaskLogRepository = aiTaskLogRepository;
    }

    /** AI 当前可用状态（给前端展示「已接入 / 规则模式」） */
    public Map<String, Object> status() {
        Map<String, Object> m = new HashMap<>();
        m.put("enabled", effectiveEnabled());
        m.put("configured", enabled && !apiKey.isBlank() && !baseUrl.isBlank());
        m.put("model", effectiveEnabled() ? model : MODEL_RULE);
        m.put("mode", effectiveEnabled() ? SRC_LLM : SRC_RULE);
        return m;
    }

    private boolean effectiveEnabled() {
        return enabled && apiKey != null && !apiKey.isBlank() && baseUrl != null && !baseUrl.isBlank();
    }

    // ==================== 能力 1：智能回访话术 ====================

    public String visitScript(String merchantNo, MemberProfile p, Map<String, Object> stats) {
        String name = safe(p == null ? null : p.getName());
        String pref = safe(p == null ? null : p.getCustomerPref());
        String familyPref = safe(p == null ? null : p.getFamilyPref());
        Object amount = stats == null ? null : stats.get("totalAmount");
        Object times = stats == null ? null : stats.get("visitTimes");
        Object lastDays = stats == null ? null : stats.get("lastVisitDays");

        String userPrompt = "会员称呼：" + (name.isEmpty() ? "会员" : name) + "\n"
                + "客户喜好：" + (pref.isEmpty() ? "（未填写）" : pref) + "\n"
                + "家人喜好：" + (familyPref.isEmpty() ? "（未填写）" : familyPref) + "\n"
                + "累计消费：" + amount + " 元，回访/到店次数：" + times + "，距上次：" + lastDays + " 天\n"
                + "请生成一段 60 字以内、自然口语化的回访开场话术，可提及其喜好，不要出现「亲爱的用户」这类套话。";

        String rule = ruleVisitScript(name, pref, lastDays);
        return generate(merchantNo, "visit_script", p == null ? null : p.getUserPhone(),
                "你是社区商圈商家的会员运营助理，说话亲切、简短、像真人店员。", userPrompt, rule);
    }

    private String ruleVisitScript(String name, String pref, Object lastDays) {
        StringBuilder sb = new StringBuilder();
        sb.append(name.isEmpty() ? "您好" : name).append("，好久没见您了");
        if (lastDays instanceof Number n && n.intValue() > 0) {
            sb.append("，算下来有 ").append(n.intValue()).append(" 天没来店里了");
        }
        sb.append("。");
        if (!pref.isEmpty()) {
            sb.append("上次您说喜欢").append(pref).append("，我们特意留了新鲜的，").append("随时过来看看？");
        } else {
            sb.append("最近上了不少新品，有空过来逛逛？");
        }
        return sb.toString();
    }

    // ==================== 能力 2：生日关怀文案 ====================

    public String birthdayCopy(String merchantNo, MemberProfile p, String couponDesc) {
        String name = safe(p == null ? null : p.getName());
        String pref = safe(p == null ? null : p.getCustomerPref());
        String userPrompt = "会员称呼：" + (name.isEmpty() ? "会员" : name) + "\n"
                + "客户喜好：" + (pref.isEmpty() ? "（未填写）" : pref) + "\n"
                + "生日礼遇：" + (couponDesc == null || couponDesc.isBlank() ? "（暂无）" : couponDesc) + "\n"
                + "请生成一段 50 字以内的生日祝福，要提到礼遇，语气温暖不营销腔。";

        String rule = ruleBirthdayCopy(name, couponDesc);
        return generate(merchantNo, "birthday_copy", p == null ? null : p.getUserPhone(),
                "你是社区商圈商家的会员运营助理，写生日祝福，温暖真诚。", userPrompt, rule);
    }

    private String ruleBirthdayCopy(String name, String couponDesc) {
        String who = name.isEmpty() ? "您" : name;
        String gift = (couponDesc == null || couponDesc.isBlank()) ? "" : "，已为您准备好 " + couponDesc;
        return who + "，生日快乐！感谢您一直以来的支持" + gift + "，到店出示即可使用，祝您新的一岁顺顺利利。";
    }

    // ==================== 能力 3：预约自动回复 ====================

    /**
     * @param action confirm 确认 / reschedule 改期 / cancel 取消
     */
    public String appointmentReply(String merchantNo, MemberAppointment a, String action) {
        String item = safe(a.getServiceItem());
        String time = a.getApptTime() == null ? "" : a.getApptTime().toString().replace("T", " ").substring(0, 16);
        String userPrompt = "预约项目：" + (item.isEmpty() ? "（未填写）" : item) + "\n"
                + "预约时间：" + time + "\n"
                + "顾客留言：" + safe(a.getCustomerMsg()) + "\n"
                + "商家处理动作：" + actionLabel(action) + "\n"
                + "请生成一段 50 字以内回复给顾客的话术，明确告知处理结果。";

        String rule = ruleAppointmentReply(item, time, action);
        return generate(merchantNo, "appointment_reply", a.getUserPhone(),
                "你是社区商圈商家的预约助理，回复顾客要清楚、礼貌、简短。", userPrompt, rule);
    }

    private String ruleAppointmentReply(String item, String time, String action) {
        String what = item.isEmpty() ? "您的预约" : ("「" + item + "」的预约");
        return switch (action == null ? "confirm" : action) {
            case "reschedule" -> "您好，" + what + "我们需要调整时间，麻烦您回复一个方便的时间段，我们优先为您安排。";
            case "cancel" -> "您好，非常抱歉，" + what + "因故需要取消，我们会尽快与您联系重新安排，给您添麻烦了。";
            default -> "您好，" + what + "已确认，时间 " + time + "，请准时到店，我们已为您准备好。";
        };
    }

    private String actionLabel(String action) {
        return switch (action == null ? "confirm" : action) {
            case "reschedule" -> "需要改期";
            case "cancel" -> "需要取消";
            default -> "确认预约";
        };
    }

    // ==================== 能力 4：流失预警挽留建议 ====================

    /**
     * 风险分由规则计算（确定性），文案由 AI 生成；AI 不可用时返回规则文案。
     */
    public Map<String, Object> churnAdvice(String merchantNo, MemberProfile p, long lastVisitDays,
                                           int visitTimes, java.math.BigDecimal totalAmount) {
        int score = 0;
        if (lastVisitDays >= 90) score += 50;
        else if (lastVisitDays >= 60) score += 35;
        else if (lastVisitDays >= 30) score += 20;
        if (visitTimes <= 1) score += 20;
        if (totalAmount != null && totalAmount.compareTo(java.math.BigDecimal.valueOf(100)) < 0) score += 10;
        score = Math.min(100, score);
        String level = score >= 60 ? "high" : (score >= 30 ? "medium" : "low");

        String name = safe(p == null ? null : p.getName());
        String pref = safe(p == null ? null : p.getCustomerPref());
        String userPrompt = "会员称呼：" + (name.isEmpty() ? "会员" : name) + "\n"
                + "客户喜好：" + (pref.isEmpty() ? "（未填写）" : pref) + "\n"
                + "距上次到店/回访：" + lastVisitDays + " 天，累计 " + visitTimes + " 次，累计消费 " + totalAmount + " 元\n"
                + "流失风险等级：" + level + "\n"
                + "请给出一条 40 字以内的挽留建议（商家该做什么），不要空话。";

        String rule = ruleChurnAdvice(level, lastVisitDays);
        String advice = generate(merchantNo, "churn_advice", p == null ? null : p.getUserPhone(),
                "你是会员运营顾问，给商家具体可执行的挽留动作。", userPrompt, rule);

        Map<String, Object> r = new HashMap<>();
        r.put("score", score);
        r.put("level", level);
        r.put("lastVisitDays", lastVisitDays);
        r.put("advice", advice);
        return r;
    }

    private String ruleChurnAdvice(String level, long lastVisitDays) {
        return switch (level) {
            case "high" -> "已 " + lastVisitDays + " 天未到店，建议主动电话问候并赠送一张限时回归券。";
            case "medium" -> "到店频次下降，建议推送其常购品类的新品或专属折扣。";
            default -> "状态正常，保持定期回访即可。";
        };
    }

    // ==================== 核心：生成 + 降级 + 落库 ====================

    /**
     * 统一生成入口：优先大模型，失败或未配置则降级 ruleFallback，并落 ai_task_log。
     */
    private String generate(String merchantNo, String bizType, String phone,
                            String systemPrompt, String userPrompt, String ruleFallback) {
        long t0 = System.currentTimeMillis();
        String result = null;
        String source = SRC_RULE;
        String usedModel = MODEL_RULE;

        if (effectiveEnabled()) {
            try {
                result = callLlm(systemPrompt, userPrompt);
                if (result != null && !result.isBlank()) {
                    source = SRC_LLM;
                    usedModel = model;
                }
            } catch (Exception e) {
                log.warn("[AI] 大模型调用失败，降级本地规则 bizType={} err={}", bizType, e.getMessage());
            }
        }
        if (result == null || result.isBlank()) {
            result = ruleFallback;
            source = SRC_RULE;
            usedModel = MODEL_RULE;
        }

        try {
            AiTaskLog t = new AiTaskLog();
            t.setMerchantNo(merchantNo == null ? "" : merchantNo);
            t.setBizType(bizType);
            t.setTargetPhone(phone);
            t.setSource(source);
            t.setModel(usedModel);
            t.setPrompt(truncate(userPrompt, 2000));
            t.setResult(truncate(result, 2000));
            t.setCostMs((int) Math.min(Integer.MAX_VALUE, System.currentTimeMillis() - t0));
            t.setCreateTime(LocalDateTime.now());
            aiTaskLogRepository.save(t);
        } catch (Exception e) {
            log.warn("[AI] 记录 ai_task_log 失败: {}", e.getMessage());
        }
        return result;
    }

    /** 调用 OpenAI 兼容 chat/completions 接口 */
    private String callLlm(String systemPrompt, String userPrompt) {
        RestTemplate rt = new RestTemplate();
        // 超时在 ClientHttpRequestFactory 上设置，避免无限等待拖垮定时任务
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(java.time.Duration.ofMillis(timeoutMs));
        factory.setReadTimeout(java.time.Duration.ofMillis(timeoutMs));
        rt.setRequestFactory(factory);

        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth(apiKey);

        Map<String, Object> sys = new HashMap<>();
        sys.put("role", "system");
        sys.put("content", systemPrompt);
        Map<String, Object> usr = new HashMap<>();
        usr.put("role", "user");
        usr.put("content", userPrompt);
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", List.of(sys, usr));
        body.put("temperature", 0.7);

        // base-url 兼容三种写法，避免拼出 /v1/v1/ 这种错路径：
        //   http://host:20128/v1  →  /v1/chat/completions
        //   http://host:20128/v1/ →  /v1/chat/completions
        //   http://host:20128     →  /v1/chat/completions
        String base = baseUrl.trim();
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        String url = base.endsWith("/v1") ? base + "/chat/completions" : base + "/v1/chat/completions";
        String resp = rt.postForObject(url, new HttpEntity<>(body, h), String.class);
        if (resp == null || resp.isBlank()) return null;

        Map<String, Object> json;
        try {
            json = objectMapper.readValue(resp, Map.class);
        } catch (Exception e) {
            log.warn("[AI] 解析大模型响应失败: {}", e.getMessage());
            return null;
        }
        Object choices = json.get("choices");
        if (choices instanceof List<?> list && !list.isEmpty()) {
            Object first = list.get(0);
            if (first instanceof Map<?, ?> fm) {
                Object msg = fm.get("message");
                if (msg instanceof Map<?, ?> mm && mm.get("content") != null) {
                    return String.valueOf(mm.get("content")).trim();
                }
            }
        }
        return null;
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
