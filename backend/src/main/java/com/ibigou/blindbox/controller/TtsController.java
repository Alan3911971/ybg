package com.ibigou.blindbox.controller;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 语音播报 TTS 代理 API。
 * 复刻浏览器网页端温柔女声音色：后端代理百度翻译 TTS 合成标准 MP3，
 * 供 APP 端 MediaPlayer 播放（规避 WebView Web Speech API 在 Android
 * 上的卡顿/变调/机械感/破音问题）。
 *
 * 路由：/api/tts?text=xxx&spd=3
 * 返回：audio/mpeg（标准 MP3，兼容所有 Android MediaPlayer 解码）
 */
@RestController
@RequestMapping("/api/tts")
public class TtsController {

    private final RestTemplate restTemplate;

    public TtsController(RestTemplateBuilder builder) {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * 合成中文女声 MP3。
     * GET /api/tts?text=宜必购盲盒&spd=3
     *
     * @param text 待播报文本（UTF-8，≤200 字超长截断）
     * @param spd  语速 1-5，默认 3（接近浏览器网页端正常语速）
     */
    @GetMapping(produces = "audio/mpeg")
    public ResponseEntity<byte[]> speak(
            @RequestParam String text,
            @RequestParam(defaultValue = "3") int spd) {
        if (text == null || text.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        String safe = text.length() > 200 ? text.substring(0, 200) : text;
        int speed = (spd < 1 || spd > 5) ? 3 : spd;
        try {
            String encoded = URLEncoder.encode(safe, StandardCharsets.UTF_8);
            String url = "https://fanyi.baidu.com/gettts?lan=zh&text="
                    + encoded + "&spd=" + speed + "&source=web";
            HttpHeaders headers = new HttpHeaders();
            headers.set("Referer", "https://fanyi.baidu.com/");
            headers.set("User-Agent",
                    "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 IbigouApp/1.0");
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<byte[]> resp = restTemplate.exchange(
                    url, HttpMethod.GET, entity, byte[].class);
            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null
                    && resp.getBody().length > 0) {
                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType("audio/mpeg"))
                        .cacheControl(org.springframework.http.CacheControl.maxAge(
                                java.time.Duration.ofDays(7)))
                        .body(resp.getBody());
            }
        } catch (Exception e) {
            // 降级：返回空音频，前端 fallback 到 Web Speech / 原生 TTS
        }
        return ResponseEntity.noContent().build();
    }
}