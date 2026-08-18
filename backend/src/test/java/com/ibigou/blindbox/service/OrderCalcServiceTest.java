package com.ibigou.blindbox.service;

import com.ibigou.blindbox.entity.Merchant;
import com.ibigou.blindbox.entity.UserCoupon;
import com.ibigou.blindbox.repository.DailyDeductQuotaRepository;
import com.ibigou.blindbox.repository.MerchantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 核心计算单测：四重 min 余额抵扣（V1.5 定稿公式）。
 */
class OrderCalcServiceTest {

    private OrderCalcService service;
    private BalanceService balanceService;
    private DailyDeductQuotaRepository quotaRepo;
    private CouponService couponService;

    private Merchant merchant(int percent, String dailyLimit) {
        Merchant m = new Merchant();
        m.setMerchantNo("M001");
        m.setBalanceDeductPercent(percent);
        m.setDailyDeductLimit(new BigDecimal(dailyLimit));
        return m;
    }

    @BeforeEach
    void setUp() {
        balanceService = Mockito.mock(BalanceService.class);
        quotaRepo = Mockito.mock(DailyDeductQuotaRepository.class);
        couponService = Mockito.mock(CouponService.class);
        MerchantRepository merchantRepo = Mockito.mock(MerchantRepository.class);
        GlobalConfigService configService = Mockito.mock(GlobalConfigService.class);

        when(merchantRepo.findById("M001")).thenReturn(Optional.of(merchant(50, "30")));
        when(quotaRepo.findForUpdate(anyString(), anyString(), any())).thenReturn(Optional.empty());
        when(configService.balanceDeductRate()).thenReturn(80);

        service = new OrderCalcService(merchantRepo, quotaRepo, balanceService, configService, couponService);
    }

    @Test
    void 理论抵扣限制_余额充足_单日不限() {
        // 余额 100，理论 50，单日上限 30 -> min(100,50,30,100)=30
        when(balanceService.availableBalance("P1")).thenReturn(new BigDecimal("100.00"));
        OrderCalcService.OrderCalc calc = service.calc("P1", "M001", null, new BigDecimal("100.00"));
        assertTrue(calc.actualDeduct().compareTo(new BigDecimal("30.00")) == 0);
        assertTrue(calc.payAmount().compareTo(new BigDecimal("70.00")) == 0);
    }

    @Test
    void 余额不足限制() {
        // 余额 10，理论 50，上限 30 -> min(10,50,30,100)=10
        when(balanceService.availableBalance("P1")).thenReturn(new BigDecimal("10.00"));
        OrderCalcService.OrderCalc calc = service.calc("P1", "M001", null, new BigDecimal("100.00"));
        assertTrue(calc.actualDeduct().compareTo(new BigDecimal("10.00")) == 0);
        assertTrue(calc.payAmount().compareTo(new BigDecimal("90.00")) == 0);
    }

    @Test
    void 本店禁止抵扣_percent0() {
        // 门店 percent=0：抵扣 0，实付全款
        when(balanceService.availableBalance("P1")).thenReturn(new BigDecimal("100.00"));
        Merchant m = merchant(0, "30");
        service = service; // 保留原 service，用反射改 merchant？直接重建
        MerchantRepository mr = Mockito.mock(MerchantRepository.class);
        when(mr.findById("M001")).thenReturn(Optional.of(m));
        service = new OrderCalcService(mr, quotaRepo, balanceService,
                Mockito.mock(GlobalConfigService.class), couponService);
        OrderCalcService.OrderCalc calc = service.calc("P1", "M001", null, new BigDecimal("100.00"));
        assertTrue(calc.actualDeduct().compareTo(new BigDecimal("0.00")) == 0);
        assertTrue(calc.payAmount().compareTo(new BigDecimal("100.00")) == 0);
    }

    @Test
    void 折扣券参与计算() {
        // 8 折券：盲盒后 80，理论 40，上限 30 -> min(100,40,30,80)=30，实付 50
        UserCoupon coupon = new UserCoupon();
        coupon.setCouponId(1L);
        coupon.setPrizeType(1);
        coupon.setPrizeValue(new BigDecimal("8.00"));
        when(couponService.get(1L)).thenReturn(coupon);
        when(balanceService.availableBalance("P1")).thenReturn(new BigDecimal("100.00"));
        OrderCalcService.OrderCalc calc = service.calc("P1", "M001", 1L, new BigDecimal("100.00"));
        assertTrue(calc.afterCoupon().compareTo(new BigDecimal("80.00")) == 0);
        assertTrue(calc.actualDeduct().compareTo(new BigDecimal("30.00")) == 0);
        assertTrue(calc.payAmount().compareTo(new BigDecimal("50.00")) == 0);
    }
}
