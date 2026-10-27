package com.ibigou.blindbox.controller;

import com.ibigou.blindbox.common.BizException;
import com.ibigou.blindbox.common.Result;
import com.ibigou.blindbox.entity.PropertyOwner;
import com.ibigou.blindbox.repository.PropertyOwnerRepository;
import com.ibigou.blindbox.service.WxPayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 业主端微信一键登录（/api/property/owner/wx）。
 *
 * 复用现有公众号 OAuth2 静默授权（snsapi_base，不弹授权页，业主无感）：
 *   1) 页面调 /oauth-url?redirect=当前页 → 拿到授权链接
 *   2) 微信 302 回到 /callback?code= → 换 openid
 *   3) openid 已绑定业主 → 直接签发 token，302 回原页并带 owner_token
 *      openid 未绑定     → 302 回登录页并带 wx_openid，业主用手机号登录一次即自动绑定
 *
 * 配置缺失（wx_pay_appId / wx_pay_app_secret 未填）时明确报错，不静默降级。
 */
@Slf4j
@RestController
@RequestMapping("/api/property/owner/wx")
@RequiredArgsConstructor
public class PropertyOwnerWxController {

    private final WxPayService wxPayService;
    private final PropertyOwnerRepository ownerRepository;

    private static final String DEFAULT_REDIRECT = "https://ybgtc.com/h5/property/customer/index.html";
    private static final String LOGIN_PAGE = "https://ybgtc.com/h5/property/customer/login.html";

    /** 拿授权链接；redirect 为授权完要回的原页面 */
    @GetMapping("/oauth-url")
    public Result<Map<String, Object>> oauthUrl(@RequestParam(required = false) String redirect) {
        String target = redirect == null || redirect.isBlank() ? DEFAULT_REDIRECT : redirect;
        try {
            String callback = "https://ybgtc.com/api/property/owner/wx/callback?redirect="
                    + URLEncoder.encode(target, StandardCharsets.UTF_8.name());
            String url = wxPayService.oauthUrl(callback);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("url", url);
            data.put("redirect", target);
            return Result.ok(data);
        } catch (BizException e) {
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            log.error("生成业主微信授权链接失败", e);
            return Result.fail("生成授权链接失败: " + e.getMessage());
        }
    }

    /** 微信授权回调：换 openid → 已绑定直接发 token，未绑定回登录页带上 openid */
    @GetMapping("/callback")
    public ResponseEntity<Void> callback(@RequestParam String code,
                                         @RequestParam(required = false) String redirect,
                                         @RequestParam(required = false) String state) {
        String target = redirect == null || redirect.isBlank() ? DEFAULT_REDIRECT : redirect;
        try {
            String openid = wxPayService.oauthOpenId(code);
            PropertyOwner owner = ownerRepository.findByWxOpenid(openid).orElse(null);
            if (owner != null) {
                String token = UUID.randomUUID().toString();
                PropertyOwnerController.TOKEN_STORE.put(token, owner.getOwnerId());
                log.info("业主微信一键登录成功 ownerId={}", owner.getOwnerId());
                return redirectTo(target, "owner_token", token, "wx", "1");
            }
            // 没绑过：回登录页，登录一次自动绑
            String sep = LOGIN_PAGE.contains("?") ? "&" : "?";
            HttpHeaders h = new HttpHeaders();
            h.add("Location", LOGIN_PAGE + sep + "wx_openid=" + URLEncoder.encode(openid, StandardCharsets.UTF_8.name())
                    + "&wx_unbound=1&redirect=" + URLEncoder.encode(target, StandardCharsets.UTF_8.name()));
            return ResponseEntity.status(302).headers(h).build();
        } catch (Exception e) {
            log.error("业主微信授权回调失败", e);
            HttpHeaders h = new HttpHeaders();
            h.add("Location", LOGIN_PAGE + "?wx_error=" + URLEncoder.encode(
                    e.getMessage() == null ? "授权失败" : e.getMessage(), StandardCharsets.UTF_8));
            return ResponseEntity.status(302).headers(h).build();
        }
    }

    /** 页面侧确认某个 openid 是否已绑定（登录页展示「已绑定，可直接登录」用） */
    @GetMapping("/status")
    public Result<Map<String, Object>> status(@RequestParam String openid) {
        PropertyOwner owner = ownerRepository.findByWxOpenid(openid).orElse(null);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("bound", owner != null);
        if (owner != null) {
            data.put("ownerName", owner.getOwnerName());
            data.put("ownerPhoneMask", maskPhone(owner.getOwnerPhone()));
        }
        return Result.ok(data);
    }

    /** 解绑：业主在「我的」里主动解除微信绑定（L3 类操作，落到本人账号） */
    @PostMapping("/unbind")
    public Result<Map<String, Object>> unbind(@RequestHeader("X-Owner-Token") String token) {
        Long ownerId = PropertyOwnerController.TOKEN_STORE.get(token);
        if (ownerId == null) throw new BizException("登录已过期");
        PropertyOwner owner = ownerRepository.findById(ownerId).orElseThrow(() -> new BizException("业主不存在"));
        owner.setWxOpenid(null);
        ownerRepository.save(owner);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("wxBound", false);
        return Result.ok(data);
    }

    private ResponseEntity<Void> redirectTo(String target, String k1, String v1, String k2, String v2) {
        String sep = target.contains("?") ? "&" : "?";
        String loc = target + sep + k1 + "=" + URLEncoder.encode(v1, StandardCharsets.UTF_8)
                + "&" + k2 + "=" + URLEncoder.encode(v2, StandardCharsets.UTF_8);
        HttpHeaders h = new HttpHeaders();
        h.add("Location", loc);
        return ResponseEntity.status(302).headers(h).build();
    }

    private String maskPhone(String p) {
        if (p == null || p.length() < 7) return p;
        return p.substring(0, 3) + "****" + p.substring(p.length() - 4);
    }
}
