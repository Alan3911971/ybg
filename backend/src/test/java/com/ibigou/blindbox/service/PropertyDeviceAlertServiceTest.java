package com.ibigou.blindbox.service;

import com.ibigou.blindbox.entity.PropertyAlertRule;
import com.ibigou.blindbox.entity.PropertyDevice;
import com.ibigou.blindbox.entity.PropertyDeviceAlert;
import com.ibigou.blindbox.repository.PropertyAlertRuleRepository;
import com.ibigou.blindbox.repository.PropertyDeviceAlertRepository;
import com.ibigou.blindbox.repository.PropertyDeviceRepository;
import com.ibigou.blindbox.repository.PropertyWorkorderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PropertyDeviceAlertServiceTest {

    @Mock private PropertyDeviceRepository deviceRepository;
    @Mock private PropertyAlertRuleRepository alertRuleRepository;
    @Mock private PropertyDeviceAlertRepository deviceAlertRepository;
    @Mock private PropertyWorkorderRepository workorderRepository;

    @InjectMocks private PropertyDeviceAlertService alertService;

    private PropertyDevice device;

    @BeforeEach
    void setUp() {
        device = new PropertyDevice();
        device.setId(1L);
        device.setCompanyId(100L);
        device.setCommunityId(1L);
        device.setDeviceNo("DEV-001");
        device.setDeviceName("1鍙锋ゼ闂ㄧ");
        device.setDeviceType("door");
        device.setStatus(1);
    }

    @Test
    @DisplayName("processAlert - 姝ｅ父鍒涘缓鍛婅璁板綍")
    void processAlert_createsAlertRecord() {
        when(deviceRepository.findByDeviceNo("DEV-001")).thenReturn(Optional.of(device));
        when(alertRuleRepository.findByDeviceTypeAndAlertTypeAndStatus("door", "offline", 1))
                .thenReturn(Collections.emptyList()); // no rules 鈫?still create alert
        when(deviceAlertRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PropertyDeviceAlert alert = alertService.processAlert("DEV-001", "offline", "璁惧绂荤嚎", "{}");

        assertNotNull(alert);
        assertEquals(1L, alert.getDeviceId());
        assertEquals("offline", alert.getAlertType());
        assertEquals("璁惧绂荤嚎", alert.getMessage());
        assertEquals(0, alert.getAckStatus()); // unacknowledged
        verify(deviceAlertRepository).save(any());
    }

    @Test
    @DisplayName("processAlert - 璁惧涓嶅瓨鍦ㄨ繑鍥瀗ull")
    void processAlert_deviceNotFound_returnsNull() {
        when(deviceRepository.findByDeviceNo("UNKNOWN")).thenReturn(Optional.empty());

        PropertyDeviceAlert result = alertService.processAlert("UNKNOWN", "offline", "test", null);

        assertNull(result);
        verify(deviceAlertRepository, never()).save(any());
    }

    @Test
    @DisplayName("acknowledgeAlert - 姝ｇ‘璁剧疆纭鐘舵€?")
    void acknowledgeAlert_setsAckStatus() {
        PropertyDeviceAlert alert = new PropertyDeviceAlert();
        alert.setId(1L);
        alert.setAckStatus(0);
        when(deviceAlertRepository.findById(1L)).thenReturn(Optional.of(alert));
        when(deviceAlertRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        alertService.acknowledgeAlert(1L, 99L);

        assertEquals(1, alert.getAckStatus()); // acknowledged
        assertEquals(99L, alert.getAckBy());
        assertNotNull(alert.getAckTime());
    }

    @Test
    @DisplayName("resolveAlert - 姝ｇ‘璁剧疆宸插鐞嗙姸鎬?")
    void resolveAlert_setsResolvedStatus() {
        PropertyDeviceAlert alert = new PropertyDeviceAlert();
        alert.setId(1L);
        alert.setAckStatus(1);
        when(deviceAlertRepository.findById(1L)).thenReturn(Optional.of(alert));
        when(deviceAlertRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        alertService.resolveAlert(1L, 99L);

        assertEquals(2, alert.getAckStatus()); // resolved
    }

    @Test
    @DisplayName("getAlertStats - 姝ｇ‘缁熻鍛婅鏁伴噺")
    void getAlertStats_countsCorrectly() {
        when(deviceAlertRepository.countByCompanyIdAndAckStatus(100L, 0)).thenReturn(5L);
        when(deviceAlertRepository.findByCompanyIdAndCreateTimeAfter(eq(100L), any(LocalDateTime.class)))
                .thenReturn(List.of(new PropertyDeviceAlert(), new PropertyDeviceAlert(), new PropertyDeviceAlert()));

        Map<String, Object> stats = alertService.getAlertStats(100L);

        assertNotNull(stats);
        assertEquals(5L, stats.get("unackedCount"));
        assertEquals(3, stats.get("todayCount"));
    }

    @Test
    @DisplayName("updateDeviceHeartbeat - 鏇存柊蹇冭烦鏃堕棿鍜屽湪绾跨姸鎬?")
    void updateDeviceHeartbeat_updatesStatusAndTime() {
        device.setStatus(0); // was offline
        device.setLastHeartbeat(null);
        when(deviceRepository.findByDeviceNo("DEV-001")).thenReturn(Optional.of(device));
        when(deviceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        alertService.updateDeviceHeartbeat("DEV-001");

        assertEquals(1, device.getStatus()); // back online
        assertNotNull(device.getLastHeartbeat());
        verify(deviceRepository).save(device);
    }
}
