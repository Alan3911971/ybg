package com.ibigou.blindbox.controller;

import com.ibigou.blindbox.common.BizException;
import com.ibigou.blindbox.common.Result;
import com.ibigou.blindbox.service.WxPayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 微信公众号 OAuth2 静默授权 + JSSDK 签名（JSAPI 支付前置）。
 * 流程：微信内打开 pay.html → 前端调 /oauth-url 拿授权链接 → 微信授权回调 /oauth-callback
 *       → 302 带回原页面并附 openid → 前端 createPay(scene=jsapi&openid) → 前端 wx.chooseWXPay
 */
@RestController
@RequestMapping("/api/pay/wx")
@RequiredArgsConstructor
@Slf4j
public class WxOAuthController {

    private final WxPayService wxPayService;

    /** 生成公众号 OAuth2 静默授权链接；redirect 为回调后要回的原页面完整URL（编码） */
    @GetMapping("/oauth-url")
    public Result oauthUrl(@RequestParam String redirect) {
        try {
            String callback = "https://ybgtc.com/api/pay/wx/oauth-callback";
            String fullCallback = callback + "?redirect=" + URLEncoder.encode(redirect, StandardCharsets.UTF_8.name());
            String url = wxPayService.oauthUrl(fullCallback);
            Map<String, String> data = new HashMap<>();
            data.put("url", url);
            return Result.ok(data);
        } catch (BizException e) {
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            log.error("生成授权链接失败", e);
            return Result.fail("生成授权链接失败: " + e.getMessage());
        }
    }

    /** 微信授权回调：code 换 openid 后 302 回原页面并附带 openid */
    @GetMapping("/oauth-callback")
    public ResponseEntity<Void> oauthCallback(@RequestParam String code,
                                @RequestParam(required = false) String redirect,
                                @RequestParam(required = false) String state) {
        try {
            String openid = wxPayService.oauthOpenId(code);
            String target = redirect == null || redirect.isBlank() ? "https://ybgtc.com/h5/customer/pay.html" : redirect;
            String sep = target.contains("?") ? "&" : "?";
            String location = target + sep + "openid=" + URLEncoder.encode(openid, StandardCharsets.UTF_8.name());
            HttpHeaders headers = new HttpHeaders();
            headers.add("Location", location);
            return ResponseEntity.status(302).headers(headers).build();
        } catch (Exception e) {
            log.error("OAuth回调失败", e);
            HttpHeaders headers = new HttpHeaders();
            headers.add("Location", "https://ybgtc.com/h5/customer/pay.html?oauth_error=1");
            return ResponseEntity.status(302).headers(headers).build();
        }
    }

    /** JSSDK wx.config 签名参数（url 为当前页完整地址，不含#hash） */
    @GetMapping("/jssdk-config")
    public Result jssdkConfig(@RequestParam String url) {
        try {
            return Result.ok(wxPayService.jssdkConfig(url));
        } catch (BizException e) {
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            log.error("JSSDK签名失败", e);
            return Result.fail("JSSDK签名失败: " + e.getMessage());
        }
    }
}
