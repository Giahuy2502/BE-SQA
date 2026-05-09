package com.learnez.notification;

import com.doan2025.webtoeic.constants.enums.ENotiType;
import com.doan2025.webtoeic.domain.Notification;
import com.doan2025.webtoeic.domain.NotificationReceive;
import com.doan2025.webtoeic.domain.User;
import com.doan2025.webtoeic.repository.NotiRepository;
import com.doan2025.webtoeic.repository.NotificationReceiveRepository;
import com.doan2025.webtoeic.utils.NotiUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for NotiUtils.sendNoti(...).
 * Rollback: Unit test sử dụng mock repository nên không làm thay đổi database
 * thật.
 */
@ExtendWith(MockitoExtension.class)
public class NotiUtilsTest {

    @Mock
    private NotiRepository notiRepository;

    @Mock
    private NotificationReceiveRepository notificationReceiveRepository;

    @InjectMocks
    private NotiUtils notiUtils;

    // TC-NOTIUTIL-001: send notification to one recipient must create one
    // Notification
    // and one NotificationReceive with UNREAD status.
    @Test
    public void TC_NOTIUTIL_001_sendNoti_singleRecipient_shouldCreateNotificationAndReceiver() {
        // Arrange
        User receiver = new User();
        receiver.setId(10L);
        receiver.setEmail("student1@example.com");

        when(notiRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(notificationReceiveRepository.save(any(NotificationReceive.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        notiUtils.sendNoti(List.of(receiver), ENotiType.UPDATE_IN_CLASS, "Class updated", "Content A", 301L);

        // Assert
        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notiRepository, times(1)).save(notificationCaptor.capture());
        Notification savedNotification = notificationCaptor.getValue();
        assertEquals("Class updated", savedNotification.getTitle());
        assertEquals("Content A", savedNotification.getContent());
        assertEquals(301L, savedNotification.getObjectId());
        assertEquals(ENotiType.UPDATE_IN_CLASS, savedNotification.getNotiType());

        ArgumentCaptor<NotificationReceive> receiverCaptor = ArgumentCaptor.forClass(NotificationReceive.class);
        verify(notificationReceiveRepository, times(1)).save(receiverCaptor.capture());
        NotificationReceive savedReceiver = receiverCaptor.getValue();
        assertEquals(receiver.getId(), savedReceiver.getReceiver().getId());
        assertFalse(Boolean.TRUE.equals(savedReceiver.getIsRead()), "New receiver must start with UNREAD status");
        // Rollback: mock repository used; no DB changes in real DB.
    }

    // TC-NOTIUTIL-002: send notification to multiple recipients must create one
    // Notification and one NotificationReceive per recipient.
    @Test
    public void TC_NOTIUTIL_002_sendNoti_multipleRecipients_shouldCreateReceiverForEachUser() {
        // Arrange
        User receiverOne = new User();
        receiverOne.setId(11L);
        receiverOne.setEmail("student2@example.com");

        User receiverTwo = new User();
        receiverTwo.setId(12L);
        receiverTwo.setEmail("student3@example.com");

        when(notiRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(notificationReceiveRepository.save(any(NotificationReceive.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        notiUtils.sendNoti(List.of(receiverOne, receiverTwo), ENotiType.NEW_COURSE, "New course", "Course content",
                401L);

        // Assert
        verify(notiRepository, times(1)).save(any(Notification.class));
        ArgumentCaptor<NotificationReceive> receiverCaptor = ArgumentCaptor.forClass(NotificationReceive.class);
        verify(notificationReceiveRepository, times(2)).save(receiverCaptor.capture());
        List<NotificationReceive> savedReceivers = receiverCaptor.getAllValues();
        assertEquals(2, savedReceivers.size());
        assertTrue(
                savedReceivers.stream()
                        .allMatch(item -> Boolean.FALSE.equals(item.getIsRead()) || item.getIsRead() == null),
                "Every new NotificationReceive must be unread");
        assertEquals(List.of(11L, 12L), savedReceivers.stream().map(item -> item.getReceiver().getId()).toList());
        // Rollback: mock repository used; no DB changes in real DB.
    }

    // TC-NOTIUTIL-003: send notification with no recipients should still create
    // Notification but must not create NotificationReceive records.
    @Test
    public void TC_NOTIUTIL_003_sendNoti_noRecipients_shouldCreateNotificationWithoutReceivers() {
        // Arrange
        when(notiRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        notiUtils.sendNoti(List.of(), ENotiType.UPDATE_IN_CLASS, "No recipients", "Content B", 999L);

        // Assert
        verify(notiRepository, times(1)).save(any(Notification.class));
        verify(notificationReceiveRepository, never()).save(any(NotificationReceive.class));
        // Rollback: mock repository used; no DB changes in real DB.
    }
}
