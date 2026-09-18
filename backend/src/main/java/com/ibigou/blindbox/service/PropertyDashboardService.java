package com.ibigou.blindbox.service;

import com.ibigou.blindbox.entity.*;
import com.ibigou.blindbox.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 物业数据看板聚合服务。
 * 从现有 Repository 读取数据，在 Java 层做聚合统计，返回结构化 Map 供前端 ECharts 渲染。
 */
@Service
@RequiredArgsConstructor
public class PropertyDashboardService {

    private final PropertyBillRepository billRepository;
    private final PropertyBindRelationRepository bindRelationRepository;
    private final PropertyWorkorderRepository workorderRepository;
    private final PropertySplitRecordRepository splitRecordRepository;
    private final PropertyOwnerRepository ownerRepository;

    // ==================== 1. 收缴率统计 ====================

    public Map<String, Object> getCollectionStats(Long companyId) {
        List<PropertyBill> allBills = billRepository.findAll().stream()
                .filter(b -> companyId.equals(b.getCompanyId()))
                .collect(Collectors.toList());

        long totalBills = allBills.size();
        // status: 0=待缴, 1=已缴, 2=逾期, 3=部分抵扣
        long paidBills = allBills.stream()
                .filter(b -> b.getStatus() != null && (b.getStatus() == 1 || b.getStatus() == 3))
                .count();
        long unpaidBills = totalBills - paidBills;

        BigDecimal collectionRate = totalBills > 0
                ? BigDecimal.valueOf(paidBills).multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(totalBills), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // 按月分组趋势（最近6个月）
        DateTimeFormatter monthFmt = DateTimeFormatter.ofPattern("yyyy-MM");
        YearMonth now = YearMonth.now();
        List<String> months = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            months.add(now.minusMonths(i).format(monthFmt));
        }

        Map<String, List<PropertyBill>> billsByMonth = allBills.stream()
                .filter(b -> b.getCreateTime() != null)
                .collect(Collectors.groupingBy(
                        b -> b.getCreateTime().format(monthFmt),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<Map<String, Object>> trend = new ArrayList<>();
        for (String m : months) {
            List<PropertyBill> monthBills = billsByMonth.getOrDefault(m, Collections.emptyList());
            long mTotal = monthBills.size();
            long mPaid = monthBills.stream()
                    .filter(b -> b.getStatus() != null && (b.getStatus() == 1 || b.getStatus() == 3))
                    .count();
            BigDecimal mRate = mTotal > 0
                    ? BigDecimal.valueOf(mPaid).multiply(BigDecimal.valueOf(100))
                            .divide(BigDecimal.valueOf(mTotal), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            Map<String, Object> point = new LinkedHashMap<>();
            point.put("month", m);
            point.put("total", mTotal);
            point.put("paid", mPaid);
            point.put("rate", mRate);
            trend.add(point);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalBills", totalBills);
        result.put("paidBills", paidBills);
        result.put("unpaidBills", unpaidBills);
        result.put("collectionRate", collectionRate);
        result.put("trend", trend);
        return result;
    }

    // ==================== 2. 分账统计 ====================

    public Map<String, Object> getSplitStats(Long companyId) {
        List<PropertySplitRecord> allSplits = splitRecordRepository.findAll().stream()
                .filter(s -> companyId.equals(s.getCompanyId()))
                .collect(Collectors.toList());

        BigDecimal totalAmount = allSplits.stream()
                .map(PropertySplitRecord::getSplitAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long totalCount = allSplits.size();

        // 按模式分组
        long mode1Count = allSplits.stream().filter(s -> Integer.valueOf(1).equals(s.getSplitMode())).count();
        long mode2Count = allSplits.stream().filter(s -> Integer.valueOf(2).equals(s.getSplitMode())).count();
        BigDecimal mode1Amount = allSplits.stream()
                .filter(s -> Integer.valueOf(1).equals(s.getSplitMode()))
                .map(PropertySplitRecord::getSplitAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal mode2Amount = allSplits.stream()
                .filter(s -> Integer.valueOf(2).equals(s.getSplitMode()))
                .map(PropertySplitRecord::getSplitAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 按月趋势（最近6个月）
        DateTimeFormatter monthFmt = DateTimeFormatter.ofPattern("yyyy-MM");
        YearMonth now = YearMonth.now();
        List<String> months = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            months.add(now.minusMonths(i).format(monthFmt));
        }

        Map<String, List<PropertySplitRecord>> splitsByMonth = allSplits.stream()
                .filter(s -> s.getCreateTime() != null)
                .collect(Collectors.groupingBy(
                        s -> s.getCreateTime().format(monthFmt),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<Map<String, Object>> trend = new ArrayList<>();
        for (String m : months) {
            List<PropertySplitRecord> monthSplits = splitsByMonth.getOrDefault(m, Collections.emptyList());
            BigDecimal mAmount = monthSplits.stream()
                    .map(PropertySplitRecord::getSplitAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            Map<String, Object> point = new LinkedHashMap<>();
            point.put("month", m);
            point.put("amount", mAmount);
            point.put("count", monthSplits.size());
            trend.add(point);
        }

        // 模式占比
        List<Map<String, Object>> modeDistribution = new ArrayList<>();
        Map<String, Object> m1 = new LinkedHashMap<>();
        m1.put("name", "商家→物业");
        m1.put("value", mode1Amount);
        m1.put("count", mode1Count);
        modeDistribution.add(m1);

        Map<String, Object> m2 = new LinkedHashMap<>();
        m2.put("name", "平台→物业");
        m2.put("value", mode2Amount);
        m2.put("count", mode2Count);
        modeDistribution.add(m2);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalAmount", totalAmount);
        result.put("totalCount", totalCount);
        result.put("mode1Amount", mode1Amount);
        result.put("mode1Count", mode1Count);
        result.put("mode2Amount", mode2Amount);
        result.put("mode2Count", mode2Count);
        result.put("trend", trend);
        result.put("modeDistribution", modeDistribution);
        return result;
    }

    // ==================== 3. 工单分析 ====================

    public Map<String, Object> getWorkorderStats(Long companyId) {
        List<PropertyWorkorder> allWo = workorderRepository.findAll().stream()
                .filter(w -> companyId.equals(w.getCompanyId()))
                .collect(Collectors.toList());

        long total = allWo.size();
        // status: 0=待处理, 1=处理中, 2=已完成, 3=已关闭, 4=已取消
        long completed = allWo.stream()
                .filter(w -> w.getStatus() != null && (w.getStatus() == 2 || w.getStatus() == 3))
                .count();
        long pending = allWo.stream()
                .filter(w -> w.getStatus() != null && (w.getStatus() == 0 || w.getStatus() == 1))
                .count();

        // 平均响应时间（小时）：createTime → completedTime
        double avgResponseHours = allWo.stream()
                .filter(w -> w.getCreateTime() != null && w.getCompletedTime() != null)
                .mapToDouble(w -> java.time.Duration.between(w.getCreateTime(), w.getCompletedTime()).toMinutes() / 60.0)
                .average()
                .orElse(0.0);
        BigDecimal avgResponse = BigDecimal.valueOf(avgResponseHours).setScale(1, RoundingMode.HALF_UP);

        // 按 category 分布
        Map<String, Long> categoryMap = allWo.stream()
                .filter(w -> w.getCategory() != null && !w.getCategory().isBlank())
                .collect(Collectors.groupingBy(PropertyWorkorder::getCategory, Collectors.counting()));

        List<Map<String, Object>> categoryDistribution = categoryMap.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("name", e.getKey());
                    item.put("value", e.getValue());
                    return item;
                })
                .collect(Collectors.toList());

        // 如果没有分类数据，按 woType 兜底
        if (categoryDistribution.isEmpty()) {
            Map<Integer, Long> typeMap = allWo.stream()
                    .collect(Collectors.groupingBy(PropertyWorkorder::getWoType, Collectors.counting()));
            Map<Integer, String> typeNameMap = Map.of(
                    1, "报修", 2, "投诉", 3, "建议", 4, "咨询", 5, "其他"
            );
            categoryDistribution = typeMap.entrySet().stream()
                    .sorted(Map.Entry.<Integer, Long>comparingByValue().reversed())
                    .map(e -> {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("name", typeNameMap.getOrDefault(e.getKey(), "类型" + e.getKey()));
                        item.put("value", e.getValue());
                        return item;
                    })
                    .collect(Collectors.toList());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("completed", completed);
        result.put("pending", pending);
        result.put("avgResponseHours", avgResponse);
        result.put("categoryDistribution", categoryDistribution);
        return result;
    }

    // ==================== 4. 综合概览 ====================

    public Map<String, Object> getOverview(Long companyId) {
        // 绑定业主数
        long bindingCount = bindRelationRepository.findByCompanyIdAndStatus(companyId, 1).size();

        // 业主总数（去重绑定中的业主）
        Set<Long> boundOwnerIds = bindRelationRepository.findByCompanyIdAndStatus(companyId, 1).stream()
                .map(PropertyBindRelation::getOwnerId)
                .collect(Collectors.toSet());
        long ownerCount = boundOwnerIds.size();

        // 本月收缴金额
        YearMonth currentMonth = YearMonth.now();
        LocalDateTime monthStart = currentMonth.atDay(1).atStartOfDay();
        LocalDateTime monthEnd = currentMonth.atEndOfMonth().atTime(23, 59, 59);

        BigDecimal monthlyRevenue = billRepository.findAll().stream()
                .filter(b -> companyId.equals(b.getCompanyId()))
                .filter(b -> b.getStatus() != null && (b.getStatus() == 1 || b.getStatus() == 3))
                .filter(b -> b.getCreateTime() != null
                        && !b.getCreateTime().isBefore(monthStart)
                        && !b.getCreateTime().isAfter(monthEnd))
                .map(b -> b.getPaid() != null ? b.getPaid() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 本月分账总额
        BigDecimal monthlySplit = splitRecordRepository.findAll().stream()
                .filter(s -> companyId.equals(s.getCompanyId()))
                .filter(s -> s.getCreateTime() != null
                        && !s.getCreateTime().isBefore(monthStart)
                        && !s.getCreateTime().isAfter(monthEnd))
                .map(PropertySplitRecord::getSplitAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 待处理工单
        long pendingWorkorders = workorderRepository.findAll().stream()
                .filter(w -> companyId.equals(w.getCompanyId()))
                .filter(w -> w.getStatus() != null && (w.getStatus() == 0 || w.getStatus() == 1))
                .count();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ownerCount", ownerCount);
        result.put("bindingCount", bindingCount);
        result.put("monthlyRevenue", monthlyRevenue);
        result.put("monthlySplit", monthlySplit);
        result.put("pendingWorkorders", pendingWorkorders);
        return result;
    }
}
