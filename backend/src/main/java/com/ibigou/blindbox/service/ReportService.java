package com.ibigou.blindbox.service;

import com.ibigou.blindbox.entity.*;
import com.ibigou.blindbox.enums.CouponStatus;
import com.ibigou.blindbox.repository.*;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 报表与 Excel 导出服务（V1.4 5.2/5.3 报表模块）。
 * <p>商家侧：发放/消费流水、团购登记、宜必购对账、外来券、公共池统计；
 * 平台侧：全量券/余额流水/团购登记/宜必购交易/公共池大盘。
 * 全部支持时间范围筛选与 Excel 导出。</p>
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final UserCouponRepository couponRepository;
    private final UserBalanceFlowRepository flowRepository;
    private final ThirdGroupVerifyRecordRepository groupRecordRepository;
    private final IbigouOrderRepository ibigouOrderRepository;
    private final BoxPublicPoolRepository publicPoolRepository;
    private final BoxPrizeLimitStatRepository statRepository;
    private final UserAccountRepository accountRepository;

    // ============================ 查询 ============================

    /** 商家：本店发放的余额流水（grant） */
    public List<UserBalanceFlow> grantFlows(String merchantNo, LocalDateTime from, LocalDateTime to) {
        return flowRepository.findByMerchantNoAndFlowTypeAndCreateTimeBetween(merchantNo, 1, from, to);
    }

    /** 商家：本店发生的余额消费流水（扣减+退款） */
    public List<UserBalanceFlow> consumeFlows(String merchantNo, LocalDateTime from, LocalDateTime to) {
        return flowRepository.findByMerchantNoAndFlowTypeInAndCreateTimeBetween(
                merchantNo, List.of(2, 3), from, to);
    }

    /** 商家：团购登记记录（按渠道筛选） */
    public List<ThirdGroupVerifyRecord> groupRecords(Integer channel, LocalDateTime from, LocalDateTime to) {
        return channel == null ? groupRecordRepository.findByCreateTimeBetween(from, to)
                : groupRecordRepository.findByChannelAndCreateTimeBetween(channel, from, to);
    }

    /** 商家：宜必购渠道消费对账（本商家资产在宜必购消耗） */
    public List<IbigouOrder> ibigouRecon(String merchantNo, LocalDateTime from, LocalDateTime to) {
        return ibigouOrderRepository.findByMerchantNoAndCreateTimeBetween(merchantNo, from, to);
    }

    /** 商家：外来流入优惠券（本店用户抽到的其他商家公共券） */
    public List<UserCoupon> externalCoupons(String merchantNo) {
        List<String> phones = statRepository.findDistinctUserPhonesByMerchantNo(merchantNo);
        if (phones.isEmpty()) {
            return List.of();
        }
        return couponRepository.findExternalCoupons(phones, merchantNo);
    }

    /** 商家：我投放公共档位的抽中统计 */
    public long publicPoolWonCount(String merchantNo, Long publicId) {
        List<BoxPublicPool> pools = publicId == null
                ? publicPoolRepository.findBySourceMerchantNo(merchantNo)
                : List.of(publicPoolRepository.findById(publicId).orElse(null));
        long total = 0;
        for (BoxPublicPool p : pools) {
            if (p != null) {
                total += statRepository.countByPrize(p.getPublicId(),
                        LocalDateTime.of(1970, 1, 1, 0, 0), LocalDateTime.now().plusSeconds(1));
            }
        }
        return total;
    }

    /** 平台：公共池大盘 */
    public List<BoxPublicPool> publicPoolBoard() {
        return publicPoolRepository.findAll();
    }

    /** 平台：全量券 */
    public List<UserCoupon> allCoupons() {
        return couponRepository.findAll();
    }

    /** 平台：全量余额流水 */
    public List<UserBalanceFlow> allBalanceFlows() {
        return flowRepository.findAll();
    }

    /** 平台：全量团购登记 */
    public List<ThirdGroupVerifyRecord> allGroupRecords() {
        return groupRecordRepository.findAll();
    }

    /** 平台：全量宜必购交易 */
    public List<IbigouOrder> allIbigouOrders() {
        return ibigouOrderRepository.findAll();
    }

    // ============================ Excel 导出 ============================

    /** 通用：headers + rows 生成 xlsx 字节数组（SXSSF 流式，防大数据内存溢出） */
    public byte[] toExcel(String sheetName, List<String> headers, List<List<Object>> rows) {
        try (org.apache.poi.xssf.streaming.SXSSFWorkbook wb = new org.apache.poi.xssf.streaming.SXSSFWorkbook(500);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet(sheetName);
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers.get(i));
                cell.setCellStyle(headerStyle(wb));
            }
            int r = 1;
            for (List<Object> rowData : rows) {
                Row row = sheet.createRow(r++);
                for (int i = 0; i < rowData.size(); i++) {
                    Cell cell = row.createCell(i);
                    Object v = rowData.get(i);
                    if (v == null) {
                        cell.setCellValue("");
                    } else if (v instanceof Number n) {
                        cell.setCellValue(n.doubleValue());
                    } else {
                        cell.setCellValue(String.valueOf(v));
                    }
                }
            }
            // SXSSF 流式不支持 autoSizeColumn；用固定列宽（大数据量下避免全量加载）
            for (int i = 0; i < headers.size(); i++) {
                sheet.setColumnWidth(i, 18 * 256);
            }
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Excel 导出失败", e);
        }
    }

    private CellStyle headerStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    /** 格式化时间 */
    static String fmt(LocalDateTime t) {
        return t == null ? "" : DTF.format(t);
    }

    // ============================ 各报表导出 ============================

    public byte[] exportCoupons(List<UserCoupon> coupons) {
        List<String> headers = List.of("券ID", "用户手机号", "发行商家", "奖品类型", "优惠值",
                "可用", "支持宜必购", "状态", "核销方式", "业务单号", "生效时间", "过期时间", "领取时间");
        List<List<Object>> rows = new ArrayList<>();
        for (UserCoupon c : coupons) {
            rows.add(Arrays.asList(c.getCouponId(), c.getUserPhone(), c.getSourceMerchantNo(),
                    c.getPrizeType(), c.getPrizeValue(), c.getCanUseAfterDraw(), c.getIsSupportIbigou(),
                    c.getStatus(), c.getVerifyType(), c.getBizNo(), fmt(c.getValidStart()),
                    fmt(c.getValidEnd()), fmt(c.getCreateTime())));
        }
        return toExcel("优惠券明细", headers, rows);
    }

    public byte[] exportBalanceFlows(List<UserBalanceFlow> flows) {
        List<String> headers = List.of("流水ID", "用户手机号", "门店", "流水类型", "金额",
                "可用", "来源池", "核销方式", "批次号", "业务单号", "备注", "时间");
        List<List<Object>> rows = new ArrayList<>();
        for (UserBalanceFlow f : flows) {
            rows.add(Arrays.asList(f.getFlowId(), f.getUserPhone(), f.getMerchantNo(), f.getFlowType(),
                    f.getAmount(), f.getCanUseAfterDraw(), f.getSourcePoolType(), f.getVerifyType(),
                    f.getDrawBatchNo(), f.getBizNo(), f.getRemark(), fmt(f.getCreateTime())));
        }
        return toExcel("余额流水", headers, rows);
    }

    public byte[] exportGroupRecords(List<ThirdGroupVerifyRecord> records) {
        List<String> headers = List.of("登记ID", "二维码Key", "用户手机号", "渠道", "团购金额", "奖品摘要", "登记时间");
        List<List<Object>> rows = new ArrayList<>();
        for (ThirdGroupVerifyRecord r : records) {
            rows.add(Arrays.asList(r.getRecordId(), r.getQrCodeUniqueKey(), r.getUserPhone(), r.getChannel(),
                    r.getGroupAmount(), r.getPrizeInfo(), fmt(r.getCreateTime())));
        }
        return toExcel("团购登记", headers, rows);
    }

    public byte[] exportIbigouOrders(List<IbigouOrder> orders) {
        List<String> headers = List.of("宜必购订单号", "用户手机号", "归属商家", "券ID", "扣减余额",
                "订单金额", "实付", "退款状态", "退款金额", "下单时间", "支付时间", "退款时间");
        List<List<Object>> rows = new ArrayList<>();
        for (IbigouOrder o : orders) {
            rows.add(Arrays.asList(o.getIbigouOrderNo(), o.getUserPhone(), o.getMerchantNo(), o.getCouponId(),
                    o.getDeductBalance(), o.getOrderAmount(), o.getPayAmount(), o.getRefundStatus(),
                    o.getRefundAmount(), fmt(o.getCreateTime()), fmt(o.getPayTime()), fmt(o.getRefundTime())));
        }
        return toExcel("宜必购交易", headers, rows);
    }

    /** P1：商家本店订单导出（线下结算） */
    public org.springframework.http.ResponseEntity<byte[]> exportOfflineOrders(
            java.util.List<OfflineOrder> orders, String merchantNo) {
        List<String> headers = List.of("订单号", "用户手机号", "原价", "券抵扣", "余额抵扣", "应付",
                "实付", "实付差异", "交易流水号", "状态", "创建时间");
        List<List<Object>> rows = new ArrayList<>();
        for (OfflineOrder o : orders) {
            String status = o.getOrderStatus() == 0 ? "待确认"
                    : (o.getRefundStatus() != null && o.getRefundStatus() != 0 ? "已退款" : "已完成");
            rows.add(Arrays.asList(o.getOfflineOrderNo(), o.getUserPhone(), o.getOrderAmount(),
                    o.getCouponId() == null ? "-" : o.getCouponId(), o.getDeductBalance(),
                    o.getPayAmount(), o.getPaidAmount(), o.getPaidDiff(),
                    o.getMerchantTradeNo() == null ? "" : o.getMerchantTradeNo(),
                    status, fmt(o.getCreateTime())));
        }
        byte[] data = toExcel("本店订单", headers, rows);
        try {
            String encoded = java.net.URLEncoder.encode("本店订单.xlsx", StandardCharsets.UTF_8).replace("+", "%20");
            return org.springframework.http.ResponseEntity.ok()
                    .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename*=UTF-8''" + encoded)
                    .contentType(org.springframework.http.MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(data);
        } catch (Exception e) {
            throw new RuntimeException("导出失败", e);
        }
    }

    public byte[] exportPublicPools(List<BoxPublicPool> pools) {
        List<String> headers = List.of("公共档位ID", "投放商家", "奖品类型", "优惠值", "权重",
                "上架", "支持宜必购", "限额范围/周期/上限", "备注");
        List<List<Object>> rows = new ArrayList<>();
        for (BoxPublicPool p : pools) {
            rows.add(Arrays.asList(p.getPublicId(), p.getSourceMerchantNo(), p.getPrizeType(), p.getPrizeValue(),
                    p.getWeight(), p.getEnabled(), p.getIsSupportIbigou(),
                    p.getLimitScope() + "/" + p.getLimitCycle() + "/" + p.getLimitMax(), p.getRemark()));
        }
        return toExcel("公共池大盘", headers, rows);
    }
}
