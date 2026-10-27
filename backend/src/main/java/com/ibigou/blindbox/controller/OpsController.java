package com.ibigou.blindbox.controller;

import com.ibigou.blindbox.common.Result;
import com.ibigou.blindbox.service.OpsService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

/**
 * 运营数据中台只读接口（V20）：全渠道大屏 / 4.4 广告机降级屏 / AI 执行日志。
 *
 * <p><b>为什么不放 /api/merchant 下</b>：WebConfig 里 {@code /api/merchant/**} 被
 * MerchantAuthInterceptor 全量拦截（只有 login 放行），而大屏和广告机是无人值守设备、没有登录态，
 * 放过去会 401 白屏。这里与电视屏 {@code /api/tv/**} 一致走免登录，用 merchantNo 定位商家。</p>
 *
 * <p><b>安全</b>：{@code app.ops.token} 配置后会校验 {@code X-Ops-Token} 头或 {@code token} 参数；
 * 没配就是内网裸奔（与 /api/tv 现状一致）。要收紧就配一个。</p>
 *
 * <p>口径文档：docs/运营数据口径-V1.md。算不出的字段返回 null + note，前端显示「待接入」，不编数。</p>
 */
@RestController
@RequestMapping("/api/ops")
@RequiredArgsConstructor
public class OpsController {

    private final OpsService opsService;

    @Value("${app.ops.token:}")
    private String token;

    /** 大屏：一次拿 12 个面板 */
    @GetMapping("/screen")
    public Result<Map<String, Object>> screen(@RequestParam(required = false) String merchantNo,
                                              @RequestParam(required = false) String date,
                                              @RequestHeader(value = "X-Ops-Token", required = false) String headerToken,
                                              @RequestParam(required = false) String token) {
        check(headerToken != null ? headerToken : token);
        return Result.ok(opsService.screen(merchantNo, date == null ? null : LocalDate.parse(date)));
    }

    /** 近 N 日趋势（柱线双轴） */
    @GetMapping("/day")
    public Result<?> day(@RequestParam(required = false) String merchantNo,
                         @RequestParam(required = false) String from,
                         @RequestParam(required = false) String to,
                         @RequestHeader(value = "X-Ops-Token", required = false) String headerToken,
                         @RequestParam(required = false) String token) {
        check(headerToken != null ? headerToken : token);
        return Result.ok(opsService.day(merchantNo,
                from == null ? null : LocalDate.parse(from),
                to == null ? null : LocalDate.parse(to)));
    }

    /** 4.4 广告机降级屏：字段精简，JSON 尽量小 */
    @GetMapping("/kiosk")
    public Result<Map<String, Object>> kiosk(@RequestParam(required = false) String merchantNo,
                                             @RequestParam(required = false) String date,
                                             @RequestHeader(value = "X-Ops-Token", required = false) String headerToken,
                                             @RequestParam(required = false) String token) {
        check(headerToken != null ? headerToken : token);
        return Result.ok(opsService.kiosk(merchantNo, date == null ? null : LocalDate.parse(date)));
    }

    /** AI 执行日志列表 */
    @GetMapping("/ai/actions")
    public Result<Map<String, Object>> actions(@RequestParam(required = false) String merchantNo,
                                               @RequestParam(required = false) String from,
                                               @RequestParam(required = false) String to,
                                               @RequestParam(required = false) Integer result,
                                               @RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "20") int size,
                                               @RequestHeader(value = "X-Ops-Token", required = false) String headerToken,
                                               @RequestParam(required = false) String token) {
        check(headerToken != null ? headerToken : token);
        return Result.ok(opsService.actions(merchantNo,
                from == null ? null : LocalDate.parse(from),
                to == null ? null : LocalDate.parse(to),
                result, page, size));
    }

    /** 单条动作 + 6 步留痕（可直接回放） */
    @GetMapping("/ai/actions/{actionNo}")
    public Result<Map<String, Object>> actionDetail(@PathVariable String actionNo,
                                                    @RequestHeader(value = "X-Ops-Token", required = false) String headerToken,
                                                    @RequestParam(required = false) String token) {
        check(headerToken != null ? headerToken : token);
        return Result.ok(opsService.actionDetail(actionNo));
    }

    /** 商家点确认：待确认 -> 进入执行队列 */
    @PostMapping("/ai/actions/{actionNo}/confirm")
    public Result<Void> confirm(@PathVariable String actionNo,
                                @RequestParam(required = false) String decision,
                                @RequestHeader(value = "X-Ops-Token", required = false) String headerToken,
                                @RequestParam(required = false) String token) {
        check(headerToken != null ? headerToken : token);
        opsService.confirmAction(actionNo, decision);
        return Result.ok();
    }

    private void check(String got) {
        if (token != null && !token.trim().isEmpty() && !token.equals(got)) {
            throw new IllegalArgumentException("ops token 无效");
        }
    }
}
