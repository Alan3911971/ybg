package com.ibigou.blindbox.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 运营数据中台读数服务（V20）。
 *
 * <p><b>双模取数</b>：{@code app.ops.source}</p>
 * <ul>
 *   <li>{@code realtime}：直查业务表（offline_order / user_coupon）实时聚合。
 *       V20 汇总表还没上生产时也能出真数，渠道归因全落 walkin、佣金按 0 计。</li>
 *   <li>{@code mart}：读 V20 汇总表 v_ops_day / v_ops_month / channel_daily / funnel_daily，
 *       需要先建表并跑过 sp_ops_rebuild。表不存在时<b>自动降级到 realtime</b>，不会报错白屏。</li>
 * </ul>
 *
 * <p>口径以 {@code docs/运营数据口径-V1.md} 为准。两条铁律：</p>
 * <ol>
 *   <li>商品毛利率只用来反算成本，页面显示的毛利率必须再扣券成本和佣金 —— 混用会得出 40% 这种反结论。</li>
 *   <li>算不出来的字段返回 null + note「待接入」，<b>绝不编数</b>（曝光量就属此类）。</li>
 * </ol>
 *
 * <p>为什么用 JdbcTemplate 而不是 JPA 实体：汇总数据是视图 + 聚合查询，映射成实体既别扭又容易
 * 和 Flyway 建的表对不上；而表/列存在性要动态判断（V20 未上线时），JPA 实体做不到。
 * 所有 SQL 都走 {@code tableExists / colExists} 兜底，缺表缺列就走降级分支。</p>
 */
@Service
public class OpsService {

    private final JdbcTemplate jdbc;

    @Value("${app.ops.source:realtime}")
    private String source;

    @Value("${app.ops.default-merchant:}")
    private String defaultMerchant;

    /** 兜底参数：merchant_ops_config 没配或表还没建时使用，与 V20 默认值一致 */
    private static final BigDecimal DEF_GOODS_MARGIN = new BigDecimal("0.4000");
    private static final BigDecimal DEF_COUPON_COST_RATIO = new BigDecimal("0.4200");
    private static final BigDecimal DEF_COMMISSION = new BigDecimal("0.0250");
    private static final BigDecimal DEF_GROSS_TARGET = new BigDecimal("0.3550");

    public OpsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ==================================================================
    // 对外：大屏 / 降级屏 / 趋势 / AI 动作
    // ==================================================================

    /** 全渠道指挥屏：一次返回 12 个面板，避免大屏发 12 个并发请求 */
    public Map<String, Object> screen(String merchantNo, LocalDate date) {
        String mn = fix(merchantNo);
        LocalDate d = date == null ? LocalDate.now() : date;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("merchantNo", mn);
        out.put("statDate", d.toString());
        boolean mart = useMart();
        out.put("source", mart ? "mart" : "realtime");
        out.put("kpi", kpi(mn, d, mart));
        out.put("month", month(mn, d, mart));
        out.put("channels", channels(mn, d, mart));
        out.put("funnel", funnel(mn, d, mart));
        out.put("flow", flow(mn));
        out.put("health", health(mn));
        out.put("ai", aiSummary(mn, d));
        // 现场履约 + 会员结构：大屏右下角面板要用，单独给避免前端再发两次请求
        out.put("queue", queue(mn));
        out.put("member", member(mn, d));
        return out;
    }

    /** 近 N 日趋势（柱线双轴） */
    public List<Map<String, Object>> day(String merchantNo, LocalDate from, LocalDate to) {
        String mn = fix(merchantNo);
        LocalDate f = from == null ? LocalDate.now().minusDays(13) : from;
        LocalDate t = to == null ? LocalDate.now() : to;
        if (useMart()) {
            try {
                List<Map<String, Object>> rows = jdbc.queryForList(
                        "SELECT stat_date, net_amount, pay_cnt, gross_margin_rate, avg_ticket " +
                        "FROM v_ops_day WHERE merchant_no=? AND stat_date BETWEEN ? AND ? ORDER BY stat_date",
                        mn, f.toString(), t.toString());
                List<Map<String, Object>> res = new ArrayList<>();
                for (Map<String, Object> r : rows) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("statDate", str(r.get("stat_date")));
                    m.put("netAmount", money(r.get("net_amount")));
                    m.put("payCnt", num(r.get("pay_cnt")));
                    m.put("grossMarginRate", pct(r.get("gross_margin_rate")));
                    m.put("avgTicket", money(r.get("avg_ticket")));
                    res.add(m);
                }
                return res;
            } catch (Exception ignored) {
                // 视图不可用，落回实时聚合
            }
        }
        return dayRealtime(mn, f, t);
    }

    /** 4.4 广告机降级屏：只要 6 块，字段尽量短，JSON 越小越好 */
    public Map<String, Object> kiosk(String merchantNo, LocalDate date) {
        String mn = fix(merchantNo);
        LocalDate d = date == null ? LocalDate.now() : date;
        boolean mart = useMart();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("merchantNo", mn);
        out.put("statDate", d.toString());
        out.put("source", mart ? "mart" : "realtime");
        out.put("today", kpi(mn, d, mart));
        out.put("week", day(mn, d.minusDays(6), d));
        out.put("channels", channels(mn, d, mart));
        out.put("month", month(mn, d, mart));
        out.put("ai", aiSummary(mn, d));
        out.put("todo", todo(mn, d));
        out.put("queue", queue(mn));
        out.put("flow", flow(mn));
        return out;
    }

    /** AI 执行日志：列表 + 四个统计数 */
    public Map<String, Object> actions(String merchantNo, LocalDate from, LocalDate to,
                                       Integer result, int page, int size) {
        String mn = fix(merchantNo);
        Map<String, Object> out = new LinkedHashMap<>();
        if (!tableExists("ai_action")) {
            out.put("total", 0);
            out.put("summary", Map.of("done", 0, "success", 0, "fail", 0, "pending", 0));
            out.put("items", List.of());
            out.put("note", "待接入：ai_action 表尚未创建（V20 迁移未执行）");
            return out;
        }
        LocalDate f = from == null ? LocalDate.now().minusDays(6) : from;
        LocalDate t = to == null ? LocalDate.now() : to;
        StringBuilder w = new StringBuilder("WHERE merchant_no=? AND create_time >= ? AND create_time < ? ");
        List<Object> args = new ArrayList<>();
        args.add(mn);
        args.add(f.atStartOfDay());
        args.add(t.plusDays(1).atStartOfDay());
        if (result != null && result > 0) {
            w.append("AND result=? ");
            args.add(result);
        }
        Map<String, Object> sum = jdbc.queryForMap(
                "SELECT COUNT(*) total, " +
                " IFNULL(SUM(result=1),0) success, " +
                " IFNULL(SUM(result IN (2,3)),0) fail, " +
                " IFNULL(SUM(run_status=1),0) pending " +
                "FROM ai_action " + w, args.toArray());
        out.put("total", num(sum.get("total")));
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("done", num(sum.get("total")));
        s.put("success", num(sum.get("success")));
        s.put("fail", num(sum.get("fail")));
        s.put("pending", num(sum.get("pending")));
        out.put("summary", s);

        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add(Math.max(0, page - 1) * size);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT action_no, title, action_type, biz_goal, channel_code, run_status, result, " +
                " risk_level, need_confirm, actual_cash, actual_coupon_cost, gross_profit, roi_cash, roi_full, " +
                " order_cnt, verify_cnt, new_member_cnt, publish_time, settle_time, summary_text, " +
                " fail_reason, owner, eta_date, create_time " +
                "FROM ai_action " + w + "ORDER BY create_time DESC LIMIT ? OFFSET ?", pageArgs.toArray());
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("actionNo", str(r.get("action_no")));
            m.put("title", str(r.get("title")));
            m.put("actionType", str(r.get("action_type")));
            m.put("bizGoal", str(r.get("biz_goal")));
            m.put("channelCode", str(r.get("channel_code")));
            m.put("runStatus", num(r.get("run_status")));
            m.put("result", num(r.get("result")));
            m.put("needConfirm", num(r.get("need_confirm")));
            m.put("actualCash", money(r.get("actual_cash")));
            m.put("actualCouponCost", money(r.get("actual_coupon_cost")));
            m.put("grossProfit", money(r.get("gross_profit")));
            m.put("roiCash", money(r.get("roi_cash")));
            m.put("roiFull", money(r.get("roi_full")));
            m.put("orderCnt", num(r.get("order_cnt")));
            m.put("verifyCnt", num(r.get("verify_cnt")));
            m.put("summaryText", str(r.get("summary_text")));
            m.put("failReason", str(r.get("fail_reason")));
            m.put("owner", str(r.get("owner")));
            m.put("etaDate", str(r.get("eta_date")));
            m.put("publishTime", str(r.get("publish_time")));
            m.put("createTime", str(r.get("create_time")));
            items.add(m);
        }
        out.put("items", items);
        return out;
    }

    /** 单条动作 + 6 步留痕（已按时间升序，可直接回放） */
    public Map<String, Object> actionDetail(String actionNo) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (!tableExists("ai_action")) {
            out.put("note", "待接入：ai_action 表尚未创建（V20 迁移未执行）");
            return out;
        }
        List<Map<String, Object>> a = jdbc.queryForList("SELECT * FROM ai_action WHERE action_no=?", actionNo);
        if (a.isEmpty()) {
            out.put("note", "动作不存在");
            return out;
        }
        Map<String, Object> r = a.get(0);
        Map<String, Object> action = new LinkedHashMap<>();
        action.put("actionNo", str(r.get("action_no")));
        action.put("title", str(r.get("title")));
        action.put("flowCode", str(r.get("flow_code")));
        action.put("actionType", str(r.get("action_type")));
        action.put("bizGoal", str(r.get("biz_goal")));
        action.put("channelCode", str(r.get("channel_code")));
        action.put("runStatus", num(r.get("run_status")));
        action.put("result", num(r.get("result")));
        action.put("needConfirm", num(r.get("need_confirm")));
        action.put("budgetCash", money(r.get("budget_cash")));
        action.put("actualCash", money(r.get("actual_cash")));
        action.put("actualCouponCost", money(r.get("actual_coupon_cost")));
        action.put("grossProfit", money(r.get("gross_profit")));
        action.put("upliftAmount", money(r.get("uplift_amount")));
        action.put("roiCash", money(r.get("roi_cash")));
        action.put("roiFull", money(r.get("roi_full")));
        action.put("orderCnt", num(r.get("order_cnt")));
        action.put("verifyCnt", num(r.get("verify_cnt")));
        action.put("publishTime", str(r.get("publish_time")));
        action.put("settleTime", str(r.get("settle_time")));
        action.put("summaryText", str(r.get("summary_text")));
        action.put("failReason", str(r.get("fail_reason")));
        action.put("owner", str(r.get("owner")));
        action.put("etaDate", str(r.get("eta_date")));
        out.put("action", action);

        List<Map<String, Object>> steps = new ArrayList<>();
        if (tableExists("ai_action_step")) {
            Object id = r.get("id");
            List<Map<String, Object>> rs = jdbc.queryForList(
                    "SELECT step_no, step_code, step_name, status, title, detail, reject_reason, " +
                    " operator, duration_ms, occur_time, payload_json " +
                    "FROM ai_action_step WHERE action_id=? ORDER BY step_no", id);
            for (Map<String, Object> s : rs) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("stepNo", num(s.get("step_no")));
                m.put("stepCode", str(s.get("step_code")));
                m.put("stepName", str(s.get("step_name")));
                m.put("status", num(s.get("status")));
                m.put("title", str(s.get("title")));
                m.put("detail", str(s.get("detail")));
                m.put("rejectReason", str(s.get("reject_reason")));
                m.put("operator", str(s.get("operator")));
                m.put("durationMs", num(s.get("duration_ms")));
                m.put("occurTime", str(s.get("occur_time")));
                m.put("mediaUrl", mediaUrl(s.get("payload_json")));
                steps.add(m);
            }
        }
        out.put("steps", steps);
        return out;
    }

    /** 商家点「确认」：待确认 -> 进入执行队列；need_confirm 清零 */
    public void confirmAction(String actionNo, String decision) {
        if (!tableExists("ai_action")) {
            throw new IllegalStateException("ai_action 表尚未创建（V20 迁移未执行）");
        }
        int n = jdbc.update("UPDATE ai_action SET run_status=2, need_confirm=0, update_time=NOW() " +
                            "WHERE action_no=? AND run_status=1", actionNo);
        if (n == 0) {
            throw new IllegalStateException("动作不存在或不是待确认状态");
        }
        if (decision != null && !decision.isEmpty() && tableExists("ai_action_step")) {
            jdbc.update("INSERT INTO ai_action_step(action_id, step_no, step_code, step_name, status, " +
                        " title, detail, operator, occur_time) " +
                        "SELECT id, 99, 'merchant_confirm', '商家确认', 2, '商家点了确认', ?, '商家', NOW() " +
                        "FROM ai_action WHERE action_no=?", decision, actionNo);
        }
    }

    // ==================================================================
    // 面板取数
    // ==================================================================

    private Map<String, Object> kpi(String mn, LocalDate d, boolean mart) {
        Map<String, BigDecimal> cfg = cfg(mn);
        if (mart) {
            try {
                Map<String, Object> r = jdbc.queryForMap(
                        "SELECT net_amount, pay_cnt, avg_ticket, discount_amount, coupon_issue_cnt, " +
                        " coupon_verify_cnt, coupon_verify_rate, coupon_cost_amount, commission_amount, " +
                        " cost_amount, gross_profit, gross_margin_rate, own_amount, platform_amount, " +
                        " own_ratio, new_member_cnt, cost_is_estimated " +
                        "FROM v_ops_day WHERE merchant_no=? AND stat_date=?", mn, d.toString());
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("netAmount", money(r.get("net_amount")));
                m.put("payCnt", num(r.get("pay_cnt")));
                m.put("avgTicket", money(r.get("avg_ticket")));
                m.put("discountAmount", money(r.get("discount_amount")));
                m.put("couponIssueCnt", num(r.get("coupon_issue_cnt")));
                m.put("couponVerifyCnt", num(r.get("coupon_verify_cnt")));
                m.put("couponVerifyRate", pct(r.get("coupon_verify_rate")));
                m.put("couponCostAmount", money(r.get("coupon_cost_amount")));
                m.put("commissionAmount", money(r.get("commission_amount")));
                m.put("costAmount", money(r.get("cost_amount")));
                m.put("grossProfit", money(r.get("gross_profit")));
                m.put("grossMarginRate", pct(r.get("gross_margin_rate")));
                m.put("grossMarginTarget", pct(cfg.get("grossMarginTarget")));
                m.put("ownAmount", money(r.get("own_amount")));
                m.put("platformAmount", money(r.get("platform_amount")));
                m.put("ownRatio", pct(r.get("own_ratio")));
                m.put("newMemberCnt", num(r.get("new_member_cnt")));
                m.put("costIsEstimated", num(r.get("cost_is_estimated")));
                return m;
            } catch (Exception ignored) {
                // 视图不可用 -> 实时聚合
            }
        }
        return kpiRealtime(mn, d, cfg);
    }

    private Map<String, Object> kpiRealtime(String mn, LocalDate d, Map<String, BigDecimal> cfg) {
        Map<String, Object> base = jdbc.queryForMap(
                "SELECT IFNULL(SUM(IFNULL(pay_amount,0)),0) pay_amount, " +
                " IFNULL(SUM(IFNULL(refund_amount,0)),0) refund_amount, " +
                " COUNT(*) pay_cnt " +
                "FROM offline_order WHERE merchant_no=? AND order_status IN (1,3) AND DATE(pay_time)=?",
                mn, d.toString());
        BigDecimal pay = bd(base.get("pay_amount"));
        BigDecimal refund = bd(base.get("refund_amount"));
        BigDecimal net = pay.subtract(refund);
        long cnt = ((Number) base.get("pay_cnt")).longValue();

        String face = faceExpr();
        Map<String, Object> c = jdbc.queryForMap(
                "SELECT IFNULL(SUM(CASE WHEN status=1 THEN " + face + " ELSE 0 END),0) face, " +
                " IFNULL(SUM(CASE WHEN status=1 THEN 1 ELSE 0 END),0) verify " +
                "FROM user_coupon WHERE source_merchant_no=? AND DATE(update_time)=?", mn, d.toString());
        BigDecimal couponFace = bd(c.get("face"));
        long verify = ((Number) c.get("verify")).longValue();
        Long issue = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_coupon WHERE source_merchant_no=? AND DATE(create_time)=?",
                Long.class, mn, d.toString());

        BigDecimal goodsMargin = cfg.get("goodsMarginRate");
        BigDecimal couponRatio = cfg.get("couponCostRatio");
        BigDecimal commissionRate = cfg.get("commissionRate");

        BigDecimal cost = net.multiply(BigDecimal.ONE.subtract(goodsMargin));
        BigDecimal couponCost = couponFace.multiply(couponRatio);
        BigDecimal platform = platformNet(mn, d);          // 无渠道打点时恒为 0
        BigDecimal commission = platform.multiply(commissionRate);
        BigDecimal gross = net.subtract(cost).subtract(couponCost).subtract(commission);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("netAmount", money(net));
        m.put("payCnt", cnt);
        m.put("avgTicket", money(div(net, cnt)));
        m.put("discountAmount", money(couponFace));
        m.put("couponIssueCnt", issue == null ? 0 : issue);
        m.put("couponVerifyCnt", verify);
        m.put("couponVerifyRate", pct(div(new BigDecimal(verify), new BigDecimal(issue == null ? 0 : issue))));
        m.put("couponCostAmount", money(couponCost));
        m.put("commissionAmount", money(commission));
        m.put("costAmount", money(cost));
        m.put("grossProfit", money(gross));
        m.put("grossMarginRate", rate(gross, net));
        m.put("grossMarginTarget", pct(cfg.get("grossMarginTarget")));
        m.put("ownAmount", money(net.subtract(platform)));
        m.put("platformAmount", money(platform));
        m.put("ownRatio", rate(net.subtract(platform), net));
        m.put("newMemberCnt", null);
        m.put("costIsEstimated", 1);                        // 未录进货价，成本是反算的
        m.put("note", "实时聚合：渠道归因未打点时全部计入自然到店，佣金按 0 计");
        return m;
    }

    private Map<String, Object> month(String mn, LocalDate d, boolean mart) {
        if (mart) {
            try {
                Map<String, Object> r = jdbc.queryForMap(
                        "SELECT month_amount, month_pay_cnt, avg_day_amount, days_done, month_target_amount, " +
                        " achieve_rate, forecast_amount, forecast_rate, need_day_amount " +
                        "FROM v_ops_month WHERE merchant_no=? AND ym=?", mn, d.toString().substring(0, 7));
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("monthAmount", money(r.get("month_amount")));
                m.put("monthPayCnt", num(r.get("month_pay_cnt")));
                m.put("avgDayAmount", money(r.get("avg_day_amount")));
                m.put("daysDone", num(r.get("days_done")));
                m.put("monthTargetAmount", money(r.get("month_target_amount")));
                m.put("achieveRate", pct(r.get("achieve_rate")));
                m.put("forecastAmount", money(r.get("forecast_amount")));
                m.put("forecastRate", pct(r.get("forecast_rate")));
                m.put("needDayAmount", money(r.get("need_day_amount")));
                return m;
            } catch (Exception ignored) {
                // 落回实时
            }
        }
        LocalDate first = d.withDayOfMonth(1);
        Map<String, Object> r = jdbc.queryForMap(
                "SELECT IFNULL(SUM(IFNULL(pay_amount,0)-IFNULL(refund_amount,0)),0) amt, " +
                " COUNT(*) cnt, COUNT(DISTINCT DATE(pay_time)) days " +
                "FROM offline_order WHERE merchant_no=? AND order_status IN (1,3) " +
                " AND pay_time>=? AND pay_time<?",
                mn, first.atStartOfDay(), d.plusDays(1).atStartOfDay());
        BigDecimal amt = bd(r.get("amt"));
        long days = ((Number) r.get("days")).longValue();
        int daysInMonth = d.lengthOfMonth();
        BigDecimal avgDay = days > 0 ? amt.divide(new BigDecimal(days), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;

        BigDecimal target = cfg(mn).get("monthTarget");
        if (target == null || target.signum() <= 0) {
            target = avgDay.multiply(new BigDecimal(daysInMonth));   // 未设目标：按当前日均推全月
        }
        BigDecimal forecast = avgDay.multiply(new BigDecimal(daysInMonth));
        long left = Math.max(daysInMonth - d.getDayOfMonth(), 1);
        BigDecimal gap = target.subtract(amt);
        BigDecimal needDay = gap.signum() > 0
                ? gap.divide(new BigDecimal(left), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("monthAmount", money(amt));
        m.put("monthPayCnt", num(r.get("cnt")));
        m.put("avgDayAmount", money(avgDay));
        m.put("daysDone", days);
        m.put("monthTargetAmount", money(target));
        m.put("achieveRate", rate(amt, target));
        m.put("forecastAmount", money(forecast));
        m.put("forecastRate", rate(forecast, target));
        m.put("needDayAmount", money(needDay));
        m.put("targetIsAuto", cfg(mn).get("monthTarget").signum() <= 0 ? 1 : 0);
        return m;
    }

    private List<Map<String, Object>> channels(String mn, LocalDate d, boolean mart) {
        List<Map<String, Object>> res = new ArrayList<>();
        if (mart) {
            try {
                List<Map<String, Object>> rows = jdbc.queryForList(
                        "SELECT c.channel_code, d.channel_name, IFNULL(d.channel_type,'offline') channel_type, " +
                        " c.net_amount, c.pay_cnt, c.coupon_verify_cnt, d.color_hex " +
                        "FROM channel_daily c LEFT JOIN ops_channel_dict d ON d.channel_code=c.channel_code " +
                        "WHERE c.merchant_no=? AND c.stat_date=? ORDER BY c.net_amount DESC", mn, d.toString());
                for (Map<String, Object> r : rows) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("channelCode", str(r.get("channel_code")));
                    m.put("channelName", str(r.get("channel_name")));
                    m.put("channelType", str(r.get("channel_type")));
                    m.put("netAmount", money(r.get("net_amount")));
                    m.put("payCnt", num(r.get("pay_cnt")));
                    m.put("couponVerifyCnt", num(r.get("coupon_verify_cnt")));
                    m.put("colorHex", str(r.get("color_hex")));
                    res.add(m);
                }
                return res;
            } catch (Exception ignored) {
                // 落回实时
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("channelCode", "walkin");
        m.put("channelName", "自然到店");
        m.put("channelType", "offline");
        m.put("netAmount", money(jdbc.queryForObject(
                "SELECT IFNULL(SUM(IFNULL(pay_amount,0)-IFNULL(refund_amount,0)),0) FROM offline_order " +
                "WHERE merchant_no=? AND order_status IN (1,3) AND DATE(pay_time)=?", BigDecimal.class,
                mn, d.toString())));
        m.put("payCnt", num(jdbc.queryForObject(
                "SELECT COUNT(*) FROM offline_order WHERE merchant_no=? AND order_status IN (1,3) AND DATE(pay_time)=?",
                Long.class, mn, d.toString())));
        m.put("colorHex", "#8ba3b8");
        m.put("note", "渠道归因待打点：未打点前全部计入自然到店");
        res.add(m);
        return res;
    }

    /** 漏斗 5 步；算不出的一步返回 null + note，前端显示「待接入」 */
    private List<Map<String, Object>> funnel(String mn, LocalDate d, boolean mart) {
        if (mart) {
            try {
                List<Map<String, Object>> rows = jdbc.queryForList(
                        "SELECT step_no, step_code, step_name, unit, SUM(user_cnt) user_cnt, " +
                        " SUM(prev_cnt) prev_cnt, MAX(conv_rate) conv_rate " +
                        "FROM funnel_daily WHERE merchant_no=? AND stat_date=? AND channel_code='all' " +
                        "GROUP BY step_no, step_code, step_name, unit ORDER BY step_no", mn, d.toString());
                List<Map<String, Object>> res = new ArrayList<>();
                for (Map<String, Object> r : rows) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("stepNo", num(r.get("step_no")));
                    m.put("stepCode", str(r.get("step_code")));
                    m.put("stepName", str(r.get("step_name")));
                    m.put("unit", str(r.get("unit")));
                    m.put("userCnt", num(r.get("user_cnt")));
                    m.put("prevCnt", num(r.get("prev_cnt")));
                    m.put("convRate", pct(r.get("conv_rate")));
                    res.add(m);
                }
                if (!res.isEmpty()) {
                    return res;
                }
            } catch (Exception ignored) {
                // 落回实时
            }
        }
        Long lead = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_coupon WHERE source_merchant_no=? AND DATE(create_time)=?",
                Long.class, mn, d.toString());
        Long verify = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_coupon WHERE source_merchant_no=? AND status=1 AND DATE(update_time)=?",
                Long.class, mn, d.toString());
        // 二次到店 = 当日成交客户里「此前 30 天也买过」的回头客，不是 30 天累计复购人数。
        // 口径修正前这里返回的是「近 30 天下单≥2次的人」，分母却是当日核销数，窗口不一致
        // 会算出 200% 这种转化率（2026-09-08 实测：2 人 / 1 张核销 = 200%），上屏就是误导。
        LocalDateTime dayStart = d.atStartOfDay();
        LocalDateTime dayEnd = d.plusDays(1).atStartOfDay();
        LocalDateTime prevStart = d.minusDays(30).atStartOfDay();
        Long repurchase = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT o.user_phone) FROM offline_order o " +
                " WHERE o.merchant_no=? AND o.order_status=1 AND o.pay_time>=? AND o.pay_time<? " +
                "   AND EXISTS (SELECT 1 FROM offline_order p WHERE p.merchant_no=o.merchant_no " +
                "     AND p.user_phone=o.user_phone AND p.order_status=1 " +
                "     AND p.pay_time>=? AND p.pay_time<?)",
                Long.class, mn, dayStart, dayEnd, prevStart, dayStart);
        // 转化率分母：当日成交客户数（不是核销张数，两者量纲不同不能相除）
        Long custCnt = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT user_phone) FROM offline_order " +
                " WHERE merchant_no=? AND order_status=1 AND pay_time>=? AND pay_time<?",
                Long.class, mn, dayStart, dayEnd);

        List<Map<String, Object>> res = new ArrayList<>();
        res.add(step(1, "exposure", "内容曝光", "次", null, "待接入：无埋点，需接平台内容后台 API 或短链计数"));
        res.add(step(2, "visit", "进店点开", "人", null, "待接入：到店客流无统计"));
        Object prev = null;
        for (int i = 3; i <= 5; i++) {
            long cur = i == 3 ? lead : (i == 4 ? verify : repurchase);
            String code = i == 3 ? "lead" : (i == 4 ? "verify" : "repurchase");
            String name = i == 3 ? "领券加购" : (i == 4 ? "到店核销" : "二次到店");
            String unit = i == 3 ? "张" : (i == 4 ? "张" : "人");
            Map<String, Object> m = step(i, code, name, unit, cur, null);
            if (i == 5) {
                // 回头客占比 = 回头客 / 当日成交客户，分母必须是同一天的客户数
                m.put("convRate", custCnt == null || custCnt == 0 ? null
                        : pct(div(new BigDecimal(repurchase), new BigDecimal(custCnt))));
                m.put("note", custCnt == null ? null
                        : "占当日成交客户 " + custCnt + " 人的比例（30 日内有复购）");
            } else {
                m.put("convRate", prev == null ? null
                        : pct(div(new BigDecimal(cur), new BigDecimal(((Number) prev).longValue()))));
            }
            res.add(m);
            prev = cur;
        }
        return res;
    }

    private Map<String, Object> step(int no, String code, String name, String unit, Long cnt, String note) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("stepNo", no);
        m.put("stepCode", code);
        m.put("stepName", name);
        m.put("unit", unit);
        m.put("userCnt", cnt);
        m.put("prevCnt", null);
        m.put("convRate", null);
        if (note != null) {
            m.put("note", note);
        }
        return m;
    }

    /** 实时订单流水：不走汇总层，最近 6 笔 */
    private List<Map<String, Object>> flow(String mn) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT offline_order_no, user_phone, pay_amount, refund_amount, order_status, pay_time " +
                "FROM offline_order WHERE merchant_no=? AND pay_time IS NOT NULL " +
                "ORDER BY pay_time DESC LIMIT 6", mn);
        List<Map<String, Object>> res = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("orderNo", str(r.get("offline_order_no")));
            m.put("userPhone", mask(str(r.get("user_phone"))));
            m.put("payAmount", money(r.get("pay_amount")));
            m.put("orderStatus", num(r.get("order_status")));
            m.put("payTime", str(r.get("pay_time")));
            res.add(m);
        }
        return res;
    }

    /** 渠道接入健康度：已接通 / 待授权 / 仅素材 */
    private Map<String, Object> health(String mn) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (!tableExists("merchant_channel_bind")) {
            m.put("connected", null);
            m.put("pendingAuth", null);
            m.put("materialOnly", null);
            m.put("note", "待接入：merchant_channel_bind 未建（V20 迁移未执行）");
            return m;
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT access_status, COUNT(*) c FROM merchant_channel_bind WHERE merchant_no=? GROUP BY access_status", mn);
        long conn = 0, pend = 0, mat = 0;
        for (Map<String, Object> r : rows) {
            long c = ((Number) r.get("c")).longValue();
            int st = ((Number) r.get("access_status")).intValue();
            if (st == 1) {
                conn += c;
            } else if (st == 2 || st == 4) {
                pend += c;
            } else if (st == 3) {
                mat += c;
            }
        }
        m.put("connected", conn);
        m.put("pendingAuth", pend);
        m.put("materialOnly", mat);
        return m;
    }

    private Map<String, Object> aiSummary(String mn, LocalDate d) {
        if (!tableExists("ai_action")) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("done", null);
            m.put("success", null);
            m.put("fail", null);
            m.put("pending", null);
            m.put("roiCash", null);
            m.put("note", "待接入：ai_action 未建（V20 迁移未执行）");
            return m;
        }
        LocalDate from = d.minusDays(6);
        Map<String, Object> r = jdbc.queryForMap(
                "SELECT COUNT(*) done, IFNULL(SUM(result=1),0) success, IFNULL(SUM(result IN (2,3)),0) fail, " +
                " IFNULL(SUM(run_status=1),0) pending, IFNULL(MAX(roi_cash),0) roi_cash " +
                "FROM ai_action WHERE merchant_no=? AND create_time>=? AND create_time<?",
                mn, from.atStartOfDay(), d.plusDays(1).atStartOfDay());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("done", num(r.get("done")));
        m.put("success", num(r.get("success")));
        m.put("fail", num(r.get("fail")));
        m.put("pending", num(r.get("pending")));
        m.put("roiCash", money(r.get("roi_cash")));
        return m;
    }

    /** 降级屏「该处理了」：把要商家动手的事聚成几行 */
    private List<Map<String, Object>> todo(String mn, LocalDate d) {
        List<Map<String, Object>> res = new ArrayList<>();
        Long wait = null;
        if (tableExists("merchant_queue_ticket")) {
            wait = jdbc.queryForObject("SELECT COUNT(*) FROM merchant_queue_ticket WHERE merchant_no=? AND status=0",
                    Long.class, mn);
        }
        if (wait != null && wait > 0) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("level", "info");
            m.put("text", "排队等待 " + wait + " 桌，注意叫号");
            res.add(m);
        }
        if (tableExists("ai_action")) {
            Long pend = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM ai_action WHERE merchant_no=? AND run_status=1", Long.class, mn);
            if (pend != null && pend > 0) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("level", "warn");
                m.put("text", "AI 有 " + pend + " 件事等你确认");
                res.add(m);
            }
        }
        if (res.isEmpty()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("level", "ok");
            m.put("text", "今天没有要处理的事");
            res.add(m);
        }
        return res;
    }

    /** 现场叫号：等待组数 + 当前叫号（叫号系统已有表，这里只读不写） */
    private Map<String, Object> queue(String mn) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (!tableExists("merchant_queue_ticket")) {
            m.put("waiting", null);
            m.put("calling", null);
            m.put("note", "待接入：叫号数据暂无表");
            return m;
        }
        Long wait = jdbc.queryForObject("SELECT COUNT(*) FROM merchant_queue_ticket WHERE merchant_no=? AND status=0",
                Long.class, mn);
        List<Map<String, Object>> cur = jdbc.queryForList(
                "SELECT ticket_no, seats, window_no FROM merchant_queue_ticket " +
                "WHERE merchant_no=? AND status=1 ORDER BY call_time DESC LIMIT 1", mn);
        m.put("waiting", wait);
        m.put("calling", cur.isEmpty() ? null : str(cur.get(0).get("ticket_no")));
        if (!cur.isEmpty()) {
            m.put("windowNo", str(cur.get(0).get("window_no")));
            m.put("seats", num(cur.get(0).get("seats")));
        }
        return m;
    }

    /**
     * 会员结构：总数 / 等级分层 / 本月新增（首单口径）/ 今日生日 / 流失风险。
     *
     * <p><b>为什么新会员按首单而不是注册时间</b>：member_profile 没有注册时间列，
     * 只能拿「该手机号在本店的第一笔成交日」当新增日 —— 语义是「首次成交」，不是「注册」，
     * 讲数时要说清楚，别当成注册会员数对外讲。</p>
     */
    private Map<String, Object> member(String mn, LocalDate d) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (!tableExists("member_profile")) {
            m.put("total", null);
            m.put("tiers", List.of());
            m.put("note", "待接入：member_profile 表不存在");
            return m;
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM member_profile WHERE merchant_no=?", Long.class, mn);
        m.put("total", num(total));

        List<Map<String, Object>> tiers = new ArrayList<>();
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT IFNULL(NULLIF(member_type,''),'普通') t, COUNT(*) c FROM member_profile " +
                "WHERE merchant_no=? GROUP BY member_type ORDER BY c DESC", mn);
        for (Map<String, Object> r : rows) {
            Map<String, Object> t = new LinkedHashMap<>();
            t.put("name", str(r.get("t")));
            t.put("cnt", num(r.get("c")));
            t.put("ratio", pct(div(new BigDecimal(((Number) r.get("c")).longValue()),
                    new BigDecimal(total == null ? 0 : total))));
            tiers.add(t);
        }
        m.put("tiers", tiers);

        // 本月新增：首单落在本月
        Long newCnt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM (SELECT user_phone FROM offline_order WHERE merchant_no=? AND order_status=1 " +
                " GROUP BY user_phone HAVING MIN(DATE(pay_time)) >= ?) t",
                Long.class, mn, d.withDayOfMonth(1).toString());
        m.put("newCnt", num(newCnt));

        // 流失风险：有会员记录但 30 天没成交（含从未成交的）
        Long churn = jdbc.queryForObject(
                "SELECT COUNT(*) FROM member_profile p WHERE p.merchant_no=? AND NOT EXISTS (" +
                " SELECT 1 FROM offline_order o WHERE o.merchant_no=p.merchant_no AND o.user_phone=p.user_phone " +
                " AND o.order_status=1 AND o.pay_time>=?)",
                Long.class, mn, d.minusDays(30).atStartOfDay());
        m.put("churnRiskCnt", num(churn));

        // 今天生日（birthday 可能是 yyyy-MM-dd 或 MM-dd，用 suffix 匹配，两种都能命中）
        String md = d.toString().substring(5);
        List<Map<String, Object>> bs = jdbc.queryForList(
                "SELECT IFNULL(NULLIF(name,''),'会员') nm FROM member_profile " +
                "WHERE merchant_no=? AND birthday LIKE ? LIMIT 3", mn, "%" + md);
        List<String> names = new ArrayList<>();
        for (Map<String, Object> r : bs) {
            names.add(str(r.get("nm")));
        }
        m.put("birthdayToday", names);
        return m;
    }

    private List<Map<String, Object>> dayRealtime(String mn, LocalDate f, LocalDate t) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT DATE(pay_time) d, IFNULL(SUM(IFNULL(pay_amount,0)-IFNULL(refund_amount,0)),0) net, COUNT(*) cnt " +
                "FROM offline_order WHERE merchant_no=? AND order_status IN (1,3) " +
                " AND DATE(pay_time) BETWEEN ? AND ? GROUP BY DATE(pay_time) ORDER BY d",
                mn, f.toString(), t.toString());
        // 券成本按天单独查，避免 group by 里写相关子查询（ONLY_FULL_GROUP_BY 会挑刺）
        Map<String, BigDecimal> coupon = new HashMap<>();
        if (colExists("user_coupon", "status")) {
            List<Map<String, Object>> cr = jdbc.queryForList(
                    "SELECT DATE(update_time) d, IFNULL(SUM(" + faceExpr() + "),0) face FROM user_coupon " +
                    "WHERE source_merchant_no=? AND status=1 AND DATE(update_time) BETWEEN ? AND ? " +
                    "GROUP BY DATE(update_time)", mn, f.toString(), t.toString());
            for (Map<String, Object> r : cr) {
                coupon.put(str(r.get("d")), bd(r.get("face")));
            }
        }
        Map<String, BigDecimal> cfg = cfg(mn);
        BigDecimal goodsMargin = cfg.get("goodsMarginRate");
        BigDecimal couponRatio = cfg.get("couponCostRatio");
        List<Map<String, Object>> res = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            String day = str(r.get("d"));
            BigDecimal net = bd(r.get("net"));
            long cnt = ((Number) r.get("cnt")).longValue();
            BigDecimal cost = net.multiply(BigDecimal.ONE.subtract(goodsMargin));
            BigDecimal cc = coupon.getOrDefault(day, BigDecimal.ZERO).multiply(couponRatio);
            BigDecimal gross = net.subtract(cost).subtract(cc);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("statDate", day);
            m.put("netAmount", money(net));
            m.put("payCnt", cnt);
            m.put("grossMarginRate", rate(gross, net));
            m.put("avgTicket", money(div(net, cnt)));
            res.add(m);
        }
        return res;
    }

    // ==================================================================
    // 工具
    // ==================================================================

    private boolean useMart() {
        return "mart".equalsIgnoreCase(source);
    }

    private String fix(String merchantNo) {
        if (merchantNo != null && !merchantNo.trim().isEmpty()) {
            return merchantNo.trim();
        }
        return defaultMerchant == null ? "" : defaultMerchant.trim();
    }

    /** 平台渠道净额（有 channel_code 列才有意义，否则恒 0） */
    private BigDecimal platformNet(String mn, LocalDate d) {
        if (!colExists("offline_order", "channel_code")) {
            return BigDecimal.ZERO;
        }
        try {
            BigDecimal v = jdbc.queryForObject(
                    "SELECT IFNULL(SUM(IFNULL(pay_amount,0)-IFNULL(refund_amount,0)),0) FROM offline_order " +
                    "WHERE merchant_no=? AND order_status IN (1,3) AND DATE(pay_time)=? " +
                    " AND channel_code IN ('douyin','meituan','channels','xiaohongshu')",
                    BigDecimal.class, mn, d.toString());
            return v == null ? BigDecimal.ZERO : v;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private String faceExpr() {
        return colExists("user_coupon", "face_amount") ? "IFNULL(face_amount, prize_value)" : "IFNULL(prize_value,0)";
    }

    private Map<String, BigDecimal> cfg(String mn) {
        Map<String, BigDecimal> m = new HashMap<>();
        m.put("goodsMarginRate", DEF_GOODS_MARGIN);
        m.put("couponCostRatio", DEF_COUPON_COST_RATIO);
        m.put("commissionRate", DEF_COMMISSION);
        m.put("grossMarginTarget", DEF_GROSS_TARGET);
        m.put("monthTarget", BigDecimal.ZERO);
        if (tableExists("merchant_ops_config")) {
            try {
                Map<String, Object> r = jdbc.queryForMap(
                        "SELECT goods_margin_rate, coupon_cost_ratio, commission_rate_default, " +
                        " gross_margin_target, month_target_amount FROM merchant_ops_config WHERE merchant_no=?", mn);
                if (r.get("goods_margin_rate") != null) {
                    m.put("goodsMarginRate", bd(r.get("goods_margin_rate")));
                }
                if (r.get("coupon_cost_ratio") != null) {
                    m.put("couponCostRatio", bd(r.get("coupon_cost_ratio")));
                }
                if (r.get("commission_rate_default") != null) {
                    m.put("commissionRate", bd(r.get("commission_rate_default")));
                }
                if (r.get("gross_margin_target") != null) {
                    m.put("grossMarginTarget", bd(r.get("gross_margin_target")));
                }
                if (r.get("month_target_amount") != null) {
                    m.put("monthTarget", bd(r.get("month_target_amount")));
                }
            } catch (Exception ignored) {
                // 没配就用默认值
            }
        }
        return m;
    }

    private final Map<String, Boolean> metaCache = new ConcurrentHashMap<>();

    private boolean tableExists(String table) {
        return metaCache.computeIfAbsent("t:" + table, k -> {
            try {
                Integer c = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.TABLES " +
                        "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=?", Integer.class, table);
                return c != null && c > 0;
            } catch (Exception e) {
                return false;
            }
        });
    }

    private boolean colExists(String table, String col) {
        return metaCache.computeIfAbsent("c:" + table + "." + col, k -> {
            try {
                Integer c = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                        "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND COLUMN_NAME=?",
                        Integer.class, table, col);
                return c != null && c > 0;
            } catch (Exception e) {
                return false;
            }
        });
    }

    private static BigDecimal bd(Object v) {
        if (v == null) {
            return BigDecimal.ZERO;
        }
        if (v instanceof BigDecimal b) {
            return b;
        }
        return new BigDecimal(v.toString());
    }

    private static BigDecimal div(BigDecimal a, BigDecimal b) {
        if (b == null || b.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return a.divide(b, 6, RoundingMode.HALF_UP);
    }

    private static BigDecimal div(BigDecimal a, long b) {
        return b <= 0 ? BigDecimal.ZERO : a.divide(new BigDecimal(b), 6, RoundingMode.HALF_UP);
    }

    /** 金额：两位小数字符串，别用浮点（对账会卡在 0.01 上） */
    private static String money(Object v) {
        return bd(v).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** 比率：小数 -> 百分数字符串，一位小数（32.06% -> "32.1"） */
    private static String pct(Object v) {
        if (v == null) {
            return null;
        }
        return bd(v).multiply(new BigDecimal("100")).setScale(1, RoundingMode.HALF_UP).toPlainString();
    }

    /**
     * 比率（带基数保护）：基数绝对值小于 1（元）时百分比会失真成天文数字
     * —— 实收 0.18 元时毛利率会算出 -4682400% 这种没人看得懂的数。
     * 这种场景一律返回 null，由页面显示「待接入」，不编数。
     */
    private static String rate(BigDecimal value, BigDecimal base) {
        if (value == null || base == null || base.abs().compareTo(BigDecimal.ONE) < 0) {
            return null;
        }
        return pct(div(value, base));
    }

    private static Number num(Object v) {
        return v == null ? null : (Number) v;
    }

    private static String str(Object v) {
        return v == null ? null : v.toString();
    }

    private static String mask(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    /** 从 payload_json 里抠出素材地址（MySQL JSON，失败就返回 null，不抛异常） */
    private String mediaUrl(Object json) {
        if (json == null) {
            return null;
        }
        String s = json.toString();
        int i = s.indexOf("media_url");
        if (i < 0) {
            return null;
        }
        int a = s.indexOf(':', i);
        int b = s.indexOf('"', a + 1);
        if (a < 0 || b < 0) {
            return null;
        }
        int e = s.indexOf('"', b + 1);
        return e > b ? s.substring(b + 1, e) : null;
    }
}
