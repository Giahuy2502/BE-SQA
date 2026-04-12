package com.doan2025.webtoeic.utils;

import com.doan2025.webtoeic.constants.enums.ENotiType;
import com.doan2025.webtoeic.domain.Notification;
import com.doan2025.webtoeic.domain.NotificationReceive;
import com.doan2025.webtoeic.domain.User;
import com.doan2025.webtoeic.repository.NotiRepository;
import com.doan2025.webtoeic.repository.NotificationReceiveRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotiUtilsTest {

    @Mock
    private NotiRepository notiRepository;

    @Mock
    private NotificationReceiveRepository notificationReceiveRepository;

    @InjectMocks
    private NotiUtils notiUtils;

    // UTC-NU-001: Gửi thông báo cho danh sách user -> lưu Notification và lưu NotificationReceive theo từng user
    @Test
    void sendNoti_shouldSaveNotificationAndReceivers_whenUsersProvided() {
        // Given
        User u1 = new User();
        u1.setId(1L);
        u1.setEmail("u1@test.com");
        User u2 = new User();
        u2.setId(2L);
        u2.setEmail("u2@test.com");
        List<User> users = List.of(u1, u2);

        ENotiType notiType = ENotiType.NEW_COURSE;
        String title = "Tiêu đề";
        String content = "Nội dung";
        Long objectId = 99L;

        // Mock: repository save trả về entity đã lưu (ở đây chỉ cần không null)
        when(notiRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        notiUtils.sendNoti(users, notiType, title, content, objectId);

        // Then: phải lưu notification 1 lần
        ArgumentCaptor<Notification> notiCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notiRepository, times(1)).save(notiCaptor.capture());
        Notification savedNoti = notiCaptor.getValue();
        assertNotNull(savedNoti);
        assertEquals(title, savedNoti.getTitle());
        assertEquals(content, savedNoti.getContent());
        assertEquals(objectId, savedNoti.getObjectId());
        assertEquals(notiType, savedNoti.getNotiType());

        // Then: phải tạo NotificationReceive tương ứng với từng user
        ArgumentCaptor<NotificationReceive> receiveCaptor = ArgumentCaptor.forClass(NotificationReceive.class);
        verify(notificationReceiveRepository, times(2)).save(receiveCaptor.capture());

        List<NotificationReceive> receives = receiveCaptor.getAllValues();
        assertEquals(2, receives.size());
        // Theo code, isRead được set false rõ ràng
        assertFalse(receives.get(0).getIsRead());
        assertFalse(receives.get(1).getIsRead());
    }

    // UTC-NU-002: Danh sách users rỗng -> vẫn lưu Notification, nhưng không tạo NotificationReceive
    @Test
    void sendNoti_shouldOnlySaveNotification_whenUsersEmpty() {
        // Given
        List<User> users = List.of();
        when(notiRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        notiUtils.sendNoti(users, ENotiType.NEW_COURSE, "T", "C", 1L);

        // Then
        verify(notiRepository, times(1)).save(any(Notification.class));
        verify(notificationReceiveRepository, never()).save(any(NotificationReceive.class));
    }
}

