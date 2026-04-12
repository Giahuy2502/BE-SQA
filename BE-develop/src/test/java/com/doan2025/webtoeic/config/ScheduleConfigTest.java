package com.doan2025.webtoeic.config;

import com.doan2025.webtoeic.domain.ClassSchedule;
import com.doan2025.webtoeic.domain.Room;
import com.doan2025.webtoeic.domain.User;
import com.doan2025.webtoeic.repository.ClassMemberRepository;
import com.doan2025.webtoeic.repository.ClassScheduleRepository;
import com.doan2025.webtoeic.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduleConfigTest {

    @Mock
    private EmailService emailService;
    @Mock
    private ClassMemberRepository classMemberRepository;
    @Mock
    private ClassScheduleRepository classScheduleRepository;

    private ScheduleConfig scheduleConfig;

    @BeforeEach
    void setUp() {
        scheduleConfig = new ScheduleConfig(emailService, classMemberRepository, classScheduleRepository);
    }

    // UTC-SC-001: Không có lịch ngày mai -> không gửi email
    @Test
    void notifyUpcomingClasses_shouldNotSendEmail_whenNoSchedules() {
        // Given
        when(classScheduleRepository.findSchedulesForNextDay(any(Date.class), any(Date.class)))
                .thenReturn(List.of());

        // When
        scheduleConfig.notifyUpcomingClasses();

        // Then
        verify(emailService, never()).sendClassNotification(any(), any(), any());
    }

    // UTC-SC-002: Có lịch ngày mai -> gửi email cho teacher + members, và loại trùng email
    @Test
    void notifyUpcomingClasses_shouldSendEmailToUniqueRecipients_whenSchedulesExist() {
        // Given: tạo lớp + teacher
        User teacher = new User();
        teacher.setEmail("teacher@test.com");
        teacher.setFirstName("T");
        teacher.setLastName("E");

        com.doan2025.webtoeic.domain.Class clazz = new com.doan2025.webtoeic.domain.Class();
        clazz.setId(10L);
        clazz.setName("Lớp A");
        clazz.setTeacher(teacher);

        // Member list: có 1 email trùng với teacher để kiểm tra loại trùng
        User m1 = new User();
        m1.setEmail("student1@test.com");
        User m2 = new User();
        m2.setEmail("teacher@test.com"); // trùng
        when(classMemberRepository.findMembersInClass(10L)).thenReturn(List.of(m1, m2));

        Room room = new Room();
        room.setName("P101");

        ClassSchedule schedule = new ClassSchedule();
        schedule.setClazz(clazz);
        schedule.setTitle("Buổi 1");
        schedule.setStartAt(new Date());
        schedule.setEndAt(new Date());
        schedule.setRoom(room);

        when(classScheduleRepository.findSchedulesForNextDay(any(Date.class), any(Date.class)))
                .thenReturn(List.of(schedule));

        // When
        scheduleConfig.notifyUpcomingClasses();

        // Then: emailService được gọi 1 lần
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<String>> recipientsCaptor =
                ArgumentCaptor.forClass((Class<Set<String>>) (Class<?>) Set.class);
        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> contentCaptor = ArgumentCaptor.forClass(String.class);

        verify(emailService, times(1)).sendClassNotification(recipientsCaptor.capture(), subjectCaptor.capture(), contentCaptor.capture());

        Set<String> recipients = recipientsCaptor.getValue();
        assertEquals(2, recipients.size()); // teacher + student1 (teacher bị trùng email nên không tăng)
        assertTrue(recipients.contains("teacher@test.com"));
        assertTrue(recipients.contains("student1@test.com"));

        assertTrue(subjectCaptor.getValue().contains("Lớp A"));
        assertTrue(subjectCaptor.getValue().contains("Buổi 1"));
        assertTrue(contentCaptor.getValue().contains("Lớp học: Lớp A"));
    }

    // UTC-SC-003: Schedule có clazz = null -> bỏ qua và không gửi email
    @Test
    void notifyUpcomingClasses_shouldSkip_whenClassIsNull() {
        // Given
        ClassSchedule schedule = new ClassSchedule();
        schedule.setClazz(null);
        when(classScheduleRepository.findSchedulesForNextDay(any(Date.class), any(Date.class)))
                .thenReturn(List.of(schedule));

        // When
        scheduleConfig.notifyUpcomingClasses();

        // Then
        verify(emailService, never()).sendClassNotification(any(), any(), any());
        verify(classMemberRepository, never()).findMembersInClass(any(Long.class));
    }

    // UTC-SC-004: Không có teacher email và members không có email -> recipients rỗng -> không gửi email
    @Test
    void notifyUpcomingClasses_shouldNotSend_whenRecipientsEmpty() {
        // Given
        com.doan2025.webtoeic.domain.Class clazz = new com.doan2025.webtoeic.domain.Class();
        clazz.setId(20L);
        clazz.setName("Lớp B");
        // teacher null email
        User teacher = new User();
        teacher.setEmail(null);
        clazz.setTeacher(teacher);

        User memberWithoutEmail = new User();
        memberWithoutEmail.setEmail(null);
        when(classMemberRepository.findMembersInClass(20L)).thenReturn(List.of(memberWithoutEmail));

        ClassSchedule schedule = new ClassSchedule();
        schedule.setClazz(clazz);
        schedule.setTitle("Buổi rỗng recipients");
        schedule.setStartAt(new Date());
        schedule.setEndAt(new Date());

        when(classScheduleRepository.findSchedulesForNextDay(any(Date.class), any(Date.class)))
                .thenReturn(List.of(schedule));

        // When
        scheduleConfig.notifyUpcomingClasses();

        // Then
        verify(emailService, never()).sendClassNotification(any(), any(), any());
    }

    // UTC-SC-005: Nếu một schedule lỗi thì vẫn tiếp tục xử lý schedule sau
    @Test
    void notifyUpcomingClasses_shouldContinueProcessing_whenOneScheduleThrowsException() {
        // Given schedule 1: class id 30 -> repository members ném lỗi
        com.doan2025.webtoeic.domain.Class clazz1 = new com.doan2025.webtoeic.domain.Class();
        clazz1.setId(30L);
        clazz1.setName("Lớp lỗi");
        User teacher1 = new User();
        teacher1.setEmail("teacher1@test.com");
        teacher1.setFirstName("T1");
        teacher1.setLastName("L1");
        clazz1.setTeacher(teacher1);
        ClassSchedule schedule1 = new ClassSchedule();
        schedule1.setClazz(clazz1);
        schedule1.setTitle("Schedule lỗi");
        schedule1.setStartAt(new Date());
        schedule1.setEndAt(new Date());

        // Given schedule 2: class id 31 -> xử lý thành công
        com.doan2025.webtoeic.domain.Class clazz2 = new com.doan2025.webtoeic.domain.Class();
        clazz2.setId(31L);
        clazz2.setName("Lớp thành công");
        User teacher2 = new User();
        teacher2.setEmail("teacher2@test.com");
        teacher2.setFirstName("T2");
        teacher2.setLastName("L2");
        clazz2.setTeacher(teacher2);
        ClassSchedule schedule2 = new ClassSchedule();
        schedule2.setClazz(clazz2);
        schedule2.setTitle("Schedule thành công");
        schedule2.setStartAt(new Date());
        schedule2.setEndAt(new Date());

        when(classScheduleRepository.findSchedulesForNextDay(any(Date.class), any(Date.class)))
                .thenReturn(List.of(schedule1, schedule2));
        when(classMemberRepository.findMembersInClass(30L)).thenThrow(new RuntimeException("db error"));
        User member = new User();
        member.setEmail("student@test.com");
        when(classMemberRepository.findMembersInClass(31L)).thenReturn(List.of(member));

        // When
        scheduleConfig.notifyUpcomingClasses();

        // Then: dù schedule1 lỗi, schedule2 vẫn gửi mail
        verify(emailService, times(1)).sendClassNotification(any(), any(), any());
    }
}

