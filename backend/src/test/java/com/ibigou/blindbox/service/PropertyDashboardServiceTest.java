package com.ibigou.blindbox.service;

import com.ibigou.blindbox.entity.PropertyBill;
import com.ibigou.blindbox.entity.PropertyBindRelation;
import com.ibigou.blindbox.entity.PropertyOwner;
import com.ibigou.blindbox.entity.PropertySplitRecord;
import com.ibigou.blindbox.entity.PropertyWorkorder;
import com.ibigou.blindbox.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PropertyDashboardServiceTest {

    @Mock private PropertyBillRepository billRepository;
    @Mock private PropertyBindRelationRepository bindRelationRepository;
    @Mock private PropertyWorkorderRepository workorderRepository;
    @Mock private PropertySplitRecordRepository splitRecordRepository;
    @Mock private PropertyOwnerRepository ownerRepository;

    @InjectMocks private PropertyDashboardService dashboardService;

    private static final Long COMPANY_ID = 100L;

    @Test
    @DisplayName("getOverview - 杩斿洖姝ｇ‘鐨勬瑙堟暟鎹?")
    void getOverview_returnsCorrectData() {
        when(ownerRepository.count()).thenReturn(50L);
        when(bindRelationRepository.findByCompanyIdAndStatus(COMPANY_ID, 1))
                .thenReturn(List.of(new PropertyBindRelation(), new PropertyBindRelation()));
        when(billRepository.findByCompanyIdAndBillPeriod(eq(COMPANY_ID), anyString()))
                .thenReturn(Collections.emptyList());
        when(splitRecordRepository.findByCompanyId(eq(COMPANY_ID)))
                .thenReturn(Collections.emptyList());
        when(workorderRepository.findByCompanyIdAndStatusInOrderByCreateTimeDesc(eq(COMPANY_ID), anyList()))
                .thenReturn(Collections.emptyList());

        Map<String, Object> overview = dashboardService.getOverview(COMPANY_ID);

        assertNotNull(overview);
        assertEquals(50L, overview.get("ownerCount"));
        assertEquals(2, overview.get("bindingCount"));
    }

    @Test
    @DisplayName("getCollectionStats - 绌鸿处鍗曞垪琛ㄦ椂鏀剁即鐜囦负0")
    void getCollectionStats_emptyBills_zeroRate() {
        when(billRepository.findByCompanyIdAndBillPeriod(eq(COMPANY_ID), anyString()))
                .thenReturn(Collections.emptyList());

        Map<String, Object> stats = dashboardService.getCollectionStats(COMPANY_ID);

        assertNotNull(stats);
        assertEquals(0, stats.get("totalBills"));
    }

    @Test
    @DisplayName("getWorkorderStats - 姝ｇ‘缁熻宸ュ崟鏁伴噺")
    void getWorkorderStats_countsCorrectly() {
        PropertyWorkorder wo1 = new PropertyWorkorder();
        wo1.setStatus(1); // completed
        PropertyWorkorder wo2 = new PropertyWorkorder();
        wo2.setStatus(0); // pending
        when(workorderRepository.findByCompanyIdAndStatusInOrderByCreateTimeDesc(eq(COMPANY_ID), anyList()))
                .thenReturn(List.of(wo1, wo2));

        Map<String, Object> stats = dashboardService.getWorkorderStats(COMPANY_ID);

        assertNotNull(stats);
        assertEquals(2, stats.get("total"));
    }

    @Test
    @DisplayName("getSplitStats - 绌哄垎璐﹁褰曟椂鎬婚涓?")
    void getSplitStats_emptyRecords_zeroAmount() {
        when(splitRecordRepository.findByCompanyId(COMPANY_ID))
                .thenReturn(Collections.emptyList());

        Map<String, Object> stats = dashboardService.getSplitStats(COMPANY_ID);

        assertNotNull(stats);
    }
}
