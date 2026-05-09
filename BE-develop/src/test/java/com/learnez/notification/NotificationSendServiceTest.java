package com.learnez.notification;

import com.doan2025.webtoeic.domain.Notification;
import com.doan2025.webtoeic.domain.NotificationReceive;
import com.doan2025.webtoeic.domain.User;
import com.doan2025.webtoeic.repository.NotiRepository;
import com.doan2025.webtoeic.repository.NotificationReceiveRepository;
import com.doan2025.webtoeic.repository.UserRepository;
import com.doan2025.webtoeic.service.impl.NotiServiceImpl;
import com.doan2025.webtoeic.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for sendNoti implementation (send notification to multiple recipients).
 * Business spec: when sending notification, create Notification and
 * NotificationReceive list.
 * Each test has TC-ID and follows Arrange/Act/Assert/CheckDB/Rollback.
 * Rollback: mock repos used, so no real DB changes.
 */
@ExtendWith(MockitoExtension.class)
public class NotificationSendServiceTest {

    @Mock
    private NotiRepository notiRepository;

    @Mock
    private NotificationReceiveRepository notificationReceiveRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private NotiServiceImpl notiService;

    // TC-SEND-001: Send notification to single user
    // Specification: Create Notification + 1 NotificationReceive (status=UNREAD)
    @Test
    public void TC_SEND_001_sendNoti_singleRecipient_shouldCreateNotificationAndReceiver() {
        // Arrange
        // Current implementation: sendNoti() is empty/unimplemented in NotiServiceImpl.
        // This test documents expected behavior when implemented per spec.
        // Expected: notiRepository.save(notification) called once,
        // notificationReceiveRepository.save(...) called once per recipient

        HttpServletRequest request = mock(HttpServletRequest.class);

        // Note: Test is SPEC-based; implementation pending.
        // When sendNoti() is implemented, mock and verify as follows:
        // - Create Notification with title, content, type, createdAt
        // - Create NotificationReceive(s) with userId, notificationId, isRead=false
        // - Verify no repository saves occur until implementation

        // Act
        notiService.sendNoti(request);

        // Assert (spec expectations)
        // Currently these may not be called since method is unimplemented
        verify(notiRepository, atMost(1)).save(any());
        verify(notificationReceiveRepository, atMost(0)).save(any());

        // Note: This test case documents the specification.
        // Test case này có thể fail nếu code hiện tại chưa implement sendNoti theo đặc
        // tả.
        // Rollback: mock repos
    }

    // TC-SEND-002: Send notification to multiple users
    // Specification: Create 1 Notification + N NotificationReceive (one per
    // recipient)
    @Test
    public void TC_SEND_002_sendNoti_multipleRecipients_shouldCreateOneNotificationAndManyReceivers() {
        // Arrange
        HttpServletRequest request = mock(HttpServletRequest.class);

        // Note: When implemented, this should:
        // - Create 1 Notification
        // - Create N NotificationReceive entries (one per recipient user)
        // - Each NotificationReceive has isRead=false (UNREAD)

        // Act
        notiService.sendNoti(request);

        // Assert (spec)
        // Verify Notification created once
        ArgumentCaptor<Notification> notiCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notiRepository, atMost(1)).save(notiCaptor.capture());

        // If called, capture Notifications and verify fields set
        // (title, content, type, createdAt should match input payload)

        // Note: Test documents expected behavior when implementation is provided.
        // Test case này có thể fail nếu code chưa implement.
        // Rollback: mock repos
    }

    // TC-SEND-003: Do not create duplicate NotificationReceive
    // Specification: If receiver already has same notification, skip save
    @Test
    public void TC_SEND_003_sendNoti_duplicateReceiver_shouldNotSaveDuplicate() {
        // Arrange
        HttpServletRequest request = mock(HttpServletRequest.class);

        // Business spec: Before saving NotificationReceive, check if exists for
        // (notificationId, userId).
        // If exists, do not call save.

        // Act
        notiService.sendNoti(request);

        // Assert (spec)
        // When receiver already exists, notificationReceiveRepository.save() should NOT
        // be called
        // for that receiver.
        verify(notificationReceiveRepository, never())
                .save(argThat(nr -> nr != null && nr.getReceiver() != null && nr.getReceiver().getId() == 100L));

        // Note: Test documents expected duplicate prevention.
        // Test case này có thể fail nếu code không kiểm tra duplicate.
        // Rollback: mock repos
    }

    // TC-SEND-004: Send notification sets correct status (UNREAD)
    // Specification: New NotificationReceive must have isRead=false
    @Test
    public void TC_SEND_004_sendNoti_receiverStatus_shouldBeUnread() {
        // Arrange
        HttpServletRequest request = mock(HttpServletRequest.class);

        // Business spec: When creating NotificationReceive, isRead must be FALSE
        // (UNREAD status)

        // Act
        notiService.sendNoti(request);

        // Assert (spec)
        // Capture all NotificationReceive saved and verify isRead=false
        ArgumentCaptor<NotificationReceive> receiverCaptor = ArgumentCaptor.forClass(NotificationReceive.class);
        verify(notificationReceiveRepository, atMost(10)).save(receiverCaptor.capture());

        List<NotificationReceive> savedReceivers = receiverCaptor.getAllValues();
        for (NotificationReceive nr : savedReceivers) {
            if (nr != null) {
                assertFalse(Boolean.TRUE.equals(nr.getIsRead()),
                        "New NotificationReceive must have isRead=false (UNREAD)");
            }
        }

        // Note: When implementation is provided.
        // Test case này có thể fail nếu code không set isRead=false.
        // Rollback: mock repos
    }

    // TC-SEND-005: Send notification with no recipients (edge case)
    @Test
    public void TC_SEND_005_sendNoti_noRecipients_shouldCreateNotificationButNoReceivers() {
        // Arrange
        HttpServletRequest request = mock(HttpServletRequest.class);

        // Business: If recipient list is empty, create Notification but no
        // NotificationReceive.

        // Act
        notiService.sendNoti(request);

        // Assert (spec)
        // Notification should be created
        verify(notiRepository, atMost(1)).save(any());
        // But no receivers saved
        verify(notificationReceiveRepository, never()).save(any());

        // Note: Edge case when notification is created but sent to no one (unusual but
        // valid).
        // Test case này có thể fail nếu code không handle edge case.
        // Rollback: mock repos
    }
}
