package com.ibigou.blindbox.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * 运营汇总 ETL 调度（V20）：把 offline_order / user_coupon 汇总进 channel_daily 等表。
 *
 * <p><b>默认关闭</b>（{@code app.ops.rebuild-enabled=false}）。原因：存储过程
 * {@code sp_ops_rebuild} 不在 Flyway 里，必须先手工在库上创建，且多实例部署同时开会把汇总跑重。
 * 想自动跑：确认只有一个实例在跑，然后配 OPS_REBUILD=true 重启；否则用外部 crontab 或手动 CALL。</p>
 *
 * <p>调度节奏见 docs/运营数据口径-V1.md 第 6 节：每日 02:00 跑昨日、每小时 :05 跑当日。</p>
 */
@Service
public class OpsRebuildService {

    private static final Logger log = LoggerFactory.getLogger(OpsRebuildService.class);

    private final JdbcTemplate jdbc;

    @Value("${app.ops.rebuild-enabled:false}")
    private boolean enabled;

    public OpsRebuildService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 每日 02:00：重跑昨天（幂等，UNIQUE 商家+日期+渠道） */
    @Scheduled(cron = "0 0 2 * * ?")
    public void rebuildYesterday() {
        rebuild(LocalDate.now().minusDays(1), "daily");
    }

    /** 每小时 :05：刷新当天，保证大屏当日数字是新的 */
    @Scheduled(cron = "0 5 * * * ?")
    public void rebuildToday() {
        rebuild(LocalDate.now(), "hourly");
    }

    /** 首次上线回填：近 N 天，手动触发一次（不自动跑） */
    public int backfill(int days) {
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(Math.max(days - 1, 0));
        return rebuildRange(from, to);
    }

    private void rebuild(LocalDate day, String tag) {
        if (!enabled) {
            return;
        }
        try {
            int n = rebuildRange(day, day);
            log.info("[ops-etl] {} rebuild {} -> {} merchants", tag, day, n);
        } catch (Exception e) {
            // 汇总失败绝不能影响主流程：大屏会走实时聚合兜底
            log.warn("[ops-etl] {} rebuild {} failed: {}", tag, day, e.getMessage());
        }
    }

    private int rebuildRange(LocalDate from, LocalDate to) {
        if (!spExists()) {
            log.warn("[ops-etl] sp_ops_rebuild 不存在，跳过（需手工在库上创建存储过程）");
            return 0;
        }
        List<String> nos = jdbc.queryForList(
                "SELECT merchant_no FROM merchant WHERE status=1", String.class);
        int n = 0;
        for (String no : nos) {
            try {
                jdbc.update("CALL sp_ops_rebuild(?,?,?)", no, from.toString(), to.toString());
                n++;
            } catch (Exception e) {
                log.warn("[ops-etl] merchant {} rebuild failed: {}", no, e.getMessage());
            }
        }
        return n;
    }

    private boolean spExists() {
        try {
            Integer c = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.ROUTINES " +
                    "WHERE ROUTINE_SCHEMA=DATABASE() AND ROUTINE_NAME='sp_ops_rebuild'", Integer.class);
            return c != null && c > 0;
        } catch (Exception e) {
            return false;
        }
    }
}
