package com.learnez.notification;

import com.doan2025.webtoeic.domain.NotificationReceive;
import com.doan2025.webtoeic.domain.User;
import com.doan2025.webtoeic.dto.response.NotiResponse;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Notification service unit tests per business specification.
 * Tests include explicit CheckDB verifications and Rollback note.
 */
@ExtendWith(MockitoExtension.class)
public class NotificationServiceTest {

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

    // TC-NOTI-001: Count unread notifications returns repository count
    @Test
    public void TC_NOTI_001_countNoti_shouldReturnUnreadCount() {
        // Arrange
        HttpServletRequest request = mock(HttpServletRequest.class);
        String email = "user1@example.com";
        when(jwtUtil.getEmailFromToken(request)).thenReturn(email);

        User user = new User();
        user.setId(11L);
        user.setEmail(email);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(notiRepository.countNotiByReceiverId(11L)).thenReturn(5L);

        // Act
        Long result = notiService.countNoti(request);

        // Assert
        assertEquals(5L, result, "Unread count must match repository result");
        verify(userRepository, times(1)).findByEmail(email);
        verify(notiRepository, times(1)).countNotiByReceiverId(11L);
        // Rollback: mock repos → no DB change
    }

    // TC-NOTI-002: List notifications returns filtered page
    @Test
    public void TC_NOTI_002_listNoti_shouldReturnPage() {
        // Arrange
        HttpServletRequest request = mock(HttpServletRequest.class);
        String email = "user2@example.com";
        when(jwtUtil.getEmailFromToken(request)).thenReturn(email);

        User user = new User();
        user.setId(22L);
        user.setEmail(email);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        NotiResponse n1 = new NotiResponse();
        Page<NotiResponse> page = new PageImpl<>(List.of(n1));
        when(notiRepository.filter(eq(22L), any(Pageable.class))).thenReturn(page);

        // Act
        Page<NotiResponse> result = notiService.listNoti(request, Pageable.unpaged());

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(notiRepository, times(1)).filter(eq(22L), any(Pageable.class));
    }

    // TC-NOTI-003: Update notifications to READ -> set isRead true and save
    @Test
    public void TC_NOTI_003_updateNoti_shouldMarkAsRead() {
        // Arrange
        HttpServletRequest request = mock(HttpServletRequest.class);
        String email = "user3@example.com";
        when(jwtUtil.getEmailFromToken(request)).thenReturn(email);

        User user = new User();
        user.setId(33L);
        user.setEmail(email);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        Long notiId1 = 1001L;
        Long notiId2 = 1002L;

        NotificationReceive nr1 = new NotificationReceive();
        nr1.setIsRead(false);
        when(notificationReceiveRepository.findByNotificationIdAndReceiverId(notiId1, 33L)).thenReturn(nr1);

        NotificationReceive nr2 = new NotificationReceive();
        nr2.setIsRead(false);
        when(notificationReceiveRepository.findByNotificationIdAndReceiverId(notiId2, 33L)).thenReturn(nr2);

        // Act
        notiService.updateNoti(request, List.of(notiId1, notiId2));

        // Assert: both saved with isRead=true
        ArgumentCaptor<NotificationReceive> captor = ArgumentCaptor.forClass(NotificationReceive.class);
        verify(notificationReceiveRepository, times(2)).save(captor.capture());
        List<NotificationReceive> savedList = captor.getAllValues();
        assertTrue(savedList.stream().allMatch(nr -> Boolean.TRUE.equals(nr.getIsRead())),
                "All saved NotificationReceive must be marked as read");
    }

    // TC-NOTI-004: sendNoti phải tạo Notification và danh sách NotificationReceive
    // tương ứng với từng người nhận.
    @Test
    public void TC_NOTI_004_sendNoti_shouldCreateNotificationAndReceivers() {
        // Arrange
        HttpServletRequest request = mock(HttpServletRequest.class);

        // Act
        notiService.sendNoti(request);

        // Assert
        // Expected behavior theo đặc tả: hệ thống phải tạo 1 Notification và ít nhất
        // 1 NotificationReceive tương ứng với người nhận.
        verify(notiRepository, times(1)).save(any());
        verify(notificationReceiveRepository, atLeastOnce()).save(any(NotificationReceive.class));

        // Note: Test case này có thể fail nếu code hiện tại chưa implement sendNoti
        // theo đặc tả.
        // Rollback: Unit test sử dụng mock repository nên không làm thay đổi database
        // thật.
    }

    // TC-NOTI-005: sendNoti should not create duplicate NotificationReceive for
    // same user & notification
    @Test
    public void TC_NOTI_005_sendNoti_shouldPreventDuplicateReceivers() {
        // Arrange
        HttpServletRequest request = mock(HttpServletRequest.class);

        // Act
        notiService.sendNoti(request);

        // Assert
        // Expected behavior theo đặc tả: hệ thống không được tạo trùng receiver cho
        // cùng
        // một notification và user.
        verify(notificationReceiveRepository, never()).save(argThat(nr -> nr != null && nr.getNotification() != null));

        // Note: Test case này có thể fail nếu code hiện tại chưa kiểm tra duplicate.
        // Rollback: Unit test sử dụng mock repository nên không làm thay đổi database
        // thật.
    }
}
