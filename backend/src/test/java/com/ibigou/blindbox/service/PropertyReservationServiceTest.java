package com.ibigou.blindbox.service;

import com.ibigou.blindbox.common.BizException;
import com.ibigou.blindbox.entity.PropertyFacility;
import com.ibigou.blindbox.entity.PropertyReservation;
import com.ibigou.blindbox.repository.PropertyFacilityRepository;
import com.ibigou.blindbox.repository.PropertyReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PropertyReservationServiceTest {

    @Mock private PropertyFacilityRepository facilityRepository;
    @Mock private PropertyReservationRepository reservationRepository;

    @InjectMocks private PropertyReservationService reservationService;

    private PropertyFacility facility;
    private static final Long FACILITY_ID = 1L;
    private static final Long OWNER_ID = 10L;
    private static final Long COMPANY_ID = 100L;

    @BeforeEach
    void setUp() {
        facility = new PropertyFacility();
        facility.setId(FACILITY_ID);
        facility.setCompanyId(COMPANY_ID);
        facility.setName("浼氳瀹");
        facility.setPrice(new BigDecimal("50.00"));
        facility.setOpenTime(LocalTime.of(8, 0));
        facility.setCloseTime(LocalTime.of(22, 0));
        facility.setStatus(1);
    }

    @Test
    @DisplayName("createReservation - 姝ｅ父鍒涘缓棰勭害")
    void createReservation_success() {
        when(facilityRepository.findById(FACILITY_ID)).thenReturn(Optional.of(facility));
        when(reservationRepository.findByFacilityIdAndReserveDateAndStatusNotIn(eq(FACILITY_ID), any(), anyList()))
                .thenReturn(Collections.emptyList());
        when(reservationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LocalDate date = LocalDate.of(2025, 10, 1);
        LocalTime start = LocalTime.of(10, 0);
        LocalTime end = LocalTime.of(12, 0);

        PropertyReservation result = reservationService.createReservation(
                OWNER_ID, COMPANY_ID, FACILITY_ID, date, start, end, "鍥㈤槦浼氳");

        assertNotNull(result);
        assertEquals(OWNER_ID, result.getOwnerId());
        assertEquals(FACILITY_ID, result.getFacilityId());
        assertEquals(0, result.getStatus()); // pending
        assertEquals(new BigDecimal("100.00"), result.getAmount()); // 2h 脳 50

        ArgumentCaptor<PropertyReservation> captor = ArgumentCaptor.forClass(PropertyReservation.class);
        verify(reservationRepository).save(captor.capture());
        assertTrue(captor.getValue().getReservationNo().startsWith("RV"));
    }

    @Test
    @DisplayName("createReservation - 鍦哄湴涓嶅瓨鍦ㄦ姏寮傚父")
    void createReservation_facilityNotFound_throws() {
        when(facilityRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(BizException.class, () ->
                reservationService.createReservation(OWNER_ID, COMPANY_ID, 999L,
                        LocalDate.now(), LocalTime.of(10, 0), LocalTime.of(12, 0), null));
    }

    @Test
    @DisplayName("createReservation - 鍦哄湴鍋滅敤鎶涘紓甯?")
    void createReservation_facilityDisabled_throws() {
        facility.setStatus(0);
        when(facilityRepository.findById(FACILITY_ID)).thenReturn(Optional.of(facility));

        assertThrows(BizException.class, () ->
                reservationService.createReservation(OWNER_ID, COMPANY_ID, FACILITY_ID,
                        LocalDate.now(), LocalTime.of(10, 0), LocalTime.of(12, 0), null));
    }

    @Test
    @DisplayName("createReservation - 鏃舵鍐茬獊鎶涘紓甯?")
    void createReservation_timeConflict_throws() {
        when(facilityRepository.findById(FACILITY_ID)).thenReturn(Optional.of(facility));

        PropertyReservation existing = new PropertyReservation();
        existing.setStartTime(LocalTime.of(9, 0));
        existing.setEndTime(LocalTime.of(11, 0));
        existing.setStatus(1); // confirmed
        when(reservationRepository.findByFacilityIdAndReserveDateAndStatusNotIn(eq(FACILITY_ID), any(), anyList()))
                .thenReturn(List.of(existing));

        assertThrows(BizException.class, () ->
                reservationService.createReservation(OWNER_ID, COMPANY_ID, FACILITY_ID,
                        LocalDate.now(), LocalTime.of(10, 0), LocalTime.of(12, 0), null));
    }

    @Test
    @DisplayName("cancelReservation - 姝ｇ‘璁剧疆鍙栨秷鐘舵€佸拰鍘熷洜")
    void cancelReservation_setsStatusAndReason() {
        PropertyReservation r = new PropertyReservation();
        r.setId(1L);
        r.setStatus(0);
        when(reservationRepository.findById(1L)).thenReturn(Optional.of(r));
        when(reservationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        reservationService.cancelReservation(1L, "涓存椂鏈変簨");

        assertEquals(3, r.getStatus()); // cancelled
        assertEquals("涓存椂鏈変簨", r.getCancelReason());
        verify(reservationRepository).save(r);
    }

    @Test
    @DisplayName("confirmReservation - 姝ｇ‘璁剧疆纭鐘舵€?")
    void confirmReservation_setsConfirmedStatus() {
        PropertyReservation r = new PropertyReservation();
        r.setId(1L);
        r.setStatus(0);
        when(reservationRepository.findById(1L)).thenReturn(Optional.of(r));
        when(reservationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        reservationService.confirmReservation(1L);

        assertEquals(1, r.getStatus()); // confirmed
    }
}
