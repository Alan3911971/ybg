package com.ibigou.blindbox.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibigou.blindbox.entity.WxUserOpenid;
import com.ibigou.blindbox.repository.WxUserOpenidRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 微信模板消息推送服务
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WxTemplateService {

    private final WxPayService wxPayService;
    private final WxUserOpenidRepository openidRepository;
    private final ObjectMapper json = new ObjectMapper();

    /** 绑定 openid */
    public void bindOpenid(String phone, String openid) {
        WxUserOpenid u = openidRepository.findById(phone).orElse(new WxUserOpenid());
        u.setPhone(phone);
        u.setOpenid(openid);
        u.setUpdateTime(LocalDateTime.now());
        openidRepository.save(u);
    }

    /** 发叫号提醒模板消息 */
    public void sendQueueCall(String phone, String ticketNo, String windowNo) {
        try {
            WxUserOpenid u = openidRepository.findByPhone(phone).orElse(null);
            if (u == null || u.getOpenid() == null) {
                log.info("未绑定openid，跳过推送: {}", phone);
                return;
            }
            String token = wxPayService.accessToken();
            String url = "https://api.weixin.qq.com/cgi-bin/message/template/send?access_token=" + token;
            Map<String, Object> body = new HashMap<>();
            body.put("touser", u.getOpenid());
            // 模板ID从配置取，先写死一个示例模板
            body.put("template_id", "queue_call_notice");
            Map<String, Map<String, String>> data = new HashMap<>();
            data.put("first", Map.of("value", "您的排队号已叫到"));
            data.put("keyword1", Map.of("value", ticketNo));
            data.put("keyword2", Map.of("value", windowNo != null ? windowNo + "号窗口" : "请到店"));
            data.put("remark", Map.of("value", "请尽快到店"));
            body.put("data", data);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                    .build();
            HttpResponse<String> resp = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
            log.info("模板消息推送结果: {}", resp.body());
        } catch (Exception e) {
            log.warn("模板消息推送失败: {}", e.getMessage());
        }
    }
}
