package com.doan2025.webtoeic.service.impl;

import com.doan2025.webtoeic.constants.enums.ERole;
import com.doan2025.webtoeic.constants.enums.EScheduleStatus;
import com.doan2025.webtoeic.constants.enums.ResponseCode;
import com.doan2025.webtoeic.constants.enums.ResponseObject;
import com.doan2025.webtoeic.domain.Class;
import com.doan2025.webtoeic.domain.ClassSchedule;
import com.doan2025.webtoeic.domain.Room;
import com.doan2025.webtoeic.domain.User;
import com.doan2025.webtoeic.dto.SearchScheduleSto;
import com.doan2025.webtoeic.dto.request.ClassScheduleRequest;
import com.doan2025.webtoeic.dto.response.ClassScheduleResponse;
import com.doan2025.webtoeic.exception.WebToeicException;
import com.doan2025.webtoeic.repository.*;
import com.doan2025.webtoeic.utils.ConvertUtil;
import com.doan2025.webtoeic.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClassScheduleServiceImplTest {

    @Mock private ClassScheduleRepository classScheduleRepository;
    @Mock private ClassRepository classRepository;
    @Mock private ClassMemberRepository classMemberRepository;
    @Mock private AttendanceRepository attendanceRepository;
    @Mock private UserRepository userRepository;
    @Mock private RoomRepository roomRepository;
    @Mock private JwtUtil jwtUtil;
    @Mock private ConvertUtil convertUtil;
    @Mock private HttpServletRequest httpServletRequest;

    @InjectMocks
    private ClassScheduleServiceImpl classScheduleService;

    private static final String TEST_EMAIL = "test@example.com";
    private static final Long SCHEDULE_ID = 1L;
    private static final Long CLASS_ID = 10L;
    private static final Long ROOM_ID = 20L;

    private User teacherUser;
    private User consultantUser;
    private User managerUser;
    private User studentUser;
    // adminUser: role=null → rơi vào else branch → WebToeicException (không phải MANAGER/CONSULTANT/TEACHER/STUDENT)
    private User adminUser;

    private ClassSchedule classSchedule;
    private Class clazz;
    private Room room;
    private Pageable pageable;
    private Page<?> mockPage;

    @BeforeEach
    void setUp() {
        pageable = PageRequest.of(0, 10);
        mockPage = new PageImpl<>(Collections.emptyList());

        room = new Room();
        room.setId(ROOM_ID);

        User teacher = new User();
        teacher.setEmail("teacher@example.com");
        teacher.setCode("TCH001");
        teacher.setRole(ERole.TEACHER);

        clazz = new Class();
        clazz.setId(CLASS_ID);
        clazz.setTeacher(teacher);

        classSchedule = new ClassSchedule();
        classSchedule.setId(SCHEDULE_ID);
        classSchedule.setClazz(clazz);
        classSchedule.setRoom(room);
        classSchedule.setStatus(EScheduleStatus.ACTIVE);
        classSchedule.setIsActive(true);

        teacherUser = new User();
        teacherUser.setEmail(TEST_EMAIL);
        teacherUser.setCode("TCH_TEST");
        teacherUser.setRole(ERole.TEACHER);

        consultantUser = new User();
        consultantUser.setEmail(TEST_EMAIL);
        consultantUser.setCode("CON001");
        consultantUser.setRole(ERole.CONSULTANT);

        managerUser = new User();
        managerUser.setEmail(TEST_EMAIL);
        managerUser.setCode("MGR001");
        managerUser.setRole(ERole.MANAGER);

        studentUser = new User();
        studentUser.setEmail(TEST_EMAIL);
        studentUser.setCode("STD001");
        studentUser.setRole(ERole.STUDENT);

        // adminUser có role=null để kích hoạt else branch trong getClassSchedule
        adminUser = new User();
        adminUser.setEmail(TEST_EMAIL);
        adminUser.setCode("ADM001");
        adminUser.setRole(null);
    }

    // ============================================================
    // 1. detailStatisticAttendance()
    // ============================================================

    @Test
    void detailStatisticAttendance_UserNotExisted_ThrowsWebToeicException() {
        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn(TEST_EMAIL);
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                classScheduleService.detailStatisticAttendance(httpServletRequest, SCHEDULE_ID, pageable))
                .isInstanceOf(WebToeicException.class);
    }

    @Test
    void detailStatisticAttendance_ScheduleNotExisted_ThrowsWebToeicException() {
        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn(TEST_EMAIL);
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(teacherUser));
        when(classScheduleRepository.findById(SCHEDULE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                classScheduleService.detailStatisticAttendance(httpServletRequest, SCHEDULE_ID, pageable))
                .isInstanceOf(WebToeicException.class);
    }

    @Test
    void detailStatisticAttendance_TeacherIsMemberOfClass_ReturnsPage() {
        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn(TEST_EMAIL);
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(teacherUser));
        when(classScheduleRepository.findById(SCHEDULE_ID)).thenReturn(Optional.of(classSchedule));
        when(classMemberRepository.existsMemberInClass(CLASS_ID, teacherUser.getId())).thenReturn(true);
        when(attendanceRepository.detailStatisticAttendance(eq(SCHEDULE_ID), any(Pageable.class)))
                .thenReturn((Page) mockPage);

        Page<?> result = classScheduleService.detailStatisticAttendance(httpServletRequest, SCHEDULE_ID, pageable);

        assertThat(result).isNotNull();
        verify(attendanceRepository).detailStatisticAttendance(eq(SCHEDULE_ID), any(Pageable.class));
    }

    @Test
    void detailStatisticAttendance_UserIsConsultant_ReturnsPage() {
        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn(TEST_EMAIL);
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(consultantUser));
        when(classScheduleRepository.findById(SCHEDULE_ID)).thenReturn(Optional.of(classSchedule));
        when(attendanceRepository.detailStatisticAttendance(eq(SCHEDULE_ID), any(Pageable.class)))
                .thenReturn((Page) mockPage);

        Page<?> result = classScheduleService.detailStatisticAttendance(httpServletRequest, SCHEDULE_ID, pageable);

        assertThat(result).isNotNull();
    }

    @Test
    void detailStatisticAttendance_TeacherNotMemberOfClass_ThrowsWebToeicException() {
        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn(TEST_EMAIL);
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(teacherUser));
        when(classScheduleRepository.findById(SCHEDULE_ID)).thenReturn(Optional.of(classSchedule));
        when(classMemberRepository.existsMemberInClass(CLASS_ID, teacherUser.getId())).thenReturn(false);

        assertThatThrownBy(() ->
                classScheduleService.detailStatisticAttendance(httpServletRequest, SCHEDULE_ID, pageable))
                .isInstanceOf(WebToeicException.class);
    }

    // ============================================================
    // 2. overviewStatisticAttendance()
    // ============================================================

    @Test
    void overviewStatisticAttendance_UserNotExisted_ThrowsWebToeicException() {
        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn(TEST_EMAIL);
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                classScheduleService.overviewStatisticAttendance(httpServletRequest, CLASS_ID, pageable))
                .isInstanceOf(WebToeicException.class);
    }

    @Test
    void overviewStatisticAttendance_TeacherIsMemberOfClass_ReturnsPage() {
        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn(TEST_EMAIL);
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(teacherUser));
        when(classMemberRepository.existsMemberInClass(CLASS_ID, teacherUser.getId())).thenReturn(true);
        when(attendanceRepository.overviewStatisticAttendance(eq(CLASS_ID), any(Pageable.class)))
                .thenReturn((Page) mockPage);

        Page<?> result = classScheduleService.overviewStatisticAttendance(httpServletRequest, CLASS_ID, pageable);

        assertThat(result).isNotNull();
    }

    @Test
    void overviewStatisticAttendance_UserIsConsultant_ReturnsPage() {
        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn(TEST_EMAIL);
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(consultantUser));
        when(attendanceRepository.overviewStatisticAttendance(eq(CLASS_ID), any(Pageable.class)))
                .thenReturn((Page) mockPage);

        Page<?> result = classScheduleService.overviewStatisticAttendance(httpServletRequest, CLASS_ID, pageable);

        assertThat(result).isNotNull();
    }

    @Test
    void overviewStatisticAttendance_TeacherNotMemberOfClass_ThrowsWebToeicException() {
        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn(TEST_EMAIL);
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(teacherUser));
        when(classMemberRepository.existsMemberInClass(CLASS_ID, teacherUser.getId())).thenReturn(false);

        assertThatThrownBy(() ->
                classScheduleService.overviewStatisticAttendance(httpServletRequest, CLASS_ID, pageable))
                .isInstanceOf(WebToeicException.class);
    }

    // ============================================================
    // 3. overviewStudentAttendance()
    // ============================================================

    @Test
    void overviewStudentAttendance_UserNotExisted_ThrowsWebToeicException() {
        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn(TEST_EMAIL);
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                classScheduleService.overviewStudentAttendance(httpServletRequest, CLASS_ID, pageable))
                .isInstanceOf(WebToeicException.class);
    }

    @Test
    void overviewStudentAttendance_TeacherIsMemberOfClass_ReturnsPage() {
        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn(TEST_EMAIL);
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(teacherUser));
        when(classMemberRepository.existsMemberInClass(CLASS_ID, teacherUser.getId())).thenReturn(true);
        when(attendanceRepository.overviewStudentAttendance(eq(CLASS_ID), any(Pageable.class)))
                .thenReturn((Page) mockPage);

        Page<?> result = classScheduleService.overviewStudentAttendance(httpServletRequest, CLASS_ID, pageable);

        assertThat(result).isNotNull();
    }

    @Test
    void overviewStudentAttendance_UserIsManager_ReturnsPage() {
        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn(TEST_EMAIL);
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(managerUser));
        when(attendanceRepository.overviewStudentAttendance(eq(CLASS_ID), any(Pageable.class)))
                .thenReturn((Page) mockPage);

        Page<?> result = classScheduleService.overviewStudentAttendance(httpServletRequest, CLASS_ID, pageable);

        assertThat(result).isNotNull();
    }

    @Test
    void overviewStudentAttendance_TeacherNotMemberOfClass_ThrowsWebToeicException() {
        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn(TEST_EMAIL);
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(teacherUser));
        when(classMemberRepository.existsMemberInClass(CLASS_ID, teacherUser.getId())).thenReturn(false);

        assertThatThrownBy(() ->
                classScheduleService.overviewStudentAttendance(httpServletRequest, CLASS_ID, pageable))
                .isInstanceOf(WebToeicException.class);
    }

    // ============================================================
    // 4. getScheduleDetail()
    // ============================================================

    @Test
    void getScheduleDetail_UserNotExisted_ThrowsWebToeicException() {
        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn(TEST_EMAIL);
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                classScheduleService.getScheduleDetail(httpServletRequest, SCHEDULE_ID))
                .isInstanceOf(WebToeicException.class);
    }

    @Test
    void getScheduleDetail_ScheduleNotExisted_ThrowsWebToeicException() {
        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn(TEST_EMAIL);
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(teacherUser));
        when(classScheduleRepository.findById(SCHEDULE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                classScheduleService.getScheduleDetail(httpServletRequest, SCHEDULE_ID))
                .isInstanceOf(WebToeicException.class);
    }
    @Test
void getScheduleDetail_UserIsMemberOfClass_ReturnsResponse() { 
// Thành viên → được xem chi tiết (đúng nghiệp vụ)
ClassScheduleResponse expectedResponse = new ClassScheduleResponse();
when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn(TEST_EMAIL);
when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(teacherUser));
when(classScheduleRepository.findById(SCHEDULE_ID)).thenReturn(Optional.of(classSchedule));
when(classMemberRepository.existsMemberInClass(SCHEDULE_ID, teacherUser.getId()))
        .thenReturn(true); // LÀ thành viên → được phép
when(convertUtil.convertScheduleToDto(httpServletRequest, classSchedule))
        .thenReturn(expectedResponse);

ClassScheduleResponse result =
        classScheduleService.getScheduleDetail(httpServletRequest, SCHEDULE_ID);

assertThat(result).isEqualTo(expectedResponse);
}

@Test
void getScheduleDetail_UserIsNotMemberOfClass_ThrowsWebToeicException() {
// Không là thành viên → bị từ chối (đúng nghiệp vụ)
when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn(TEST_EMAIL);
when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(teacherUser));
when(classScheduleRepository.findById(SCHEDULE_ID)).thenReturn(Optional.of(classSchedule));
when(classMemberRepository.existsMemberInClass(SCHEDULE_ID, teacherUser.getId()))
        .thenReturn(false); // KHÔNG là thành viên → bị từ chối

assertThatThrownBy(() ->
        classScheduleService.getScheduleDetail(httpServletRequest, SCHEDULE_ID))
        .isInstanceOf(WebToeicException.class);
}

    // ============================================================
    // 5. getClassSchedule()
    // ============================================================

    @Test
    void getClassSchedule_UserNotExisted_ThrowsWebToeicException() {
        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn(TEST_EMAIL);
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                classScheduleService.getClassSchedule(httpServletRequest, new SearchScheduleSto(), pageable))
                .isInstanceOf(WebToeicException.class);
    }

    @Test
    void getClassSchedule_AllFiltersNullAndUserIsManager_ReturnsPage() {
        SearchScheduleSto dto = new SearchScheduleSto();
        dto.setClassId(null);
        dto.setTeacherId(null);
        dto.setStatus(null);

        Page<ClassSchedule> schedulePage = new PageImpl<>(Collections.emptyList());
        ClassScheduleResponse response = new ClassScheduleResponse();

        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn(TEST_EMAIL);
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(managerUser));
        when(classScheduleRepository.filterSchedule(any(SearchScheduleSto.class), isNull(), any(Pageable.class)))
                .thenReturn(schedulePage);

        Page<?> result = classScheduleService.getClassSchedule(httpServletRequest, dto, pageable);

        assertThat(result).isNotNull();
    }

    @Test
    void getClassSchedule_AllFiltersHaveValuesAndUserIsTeacher_ReturnsPage() {
        SearchScheduleSto dto = new SearchScheduleSto();
        dto.setClassId(List.of(CLASS_ID));
        dto.setTeacherId(List.of(1L));
        dto.setStatus(List.of("ACTIVE"));

        Page<ClassSchedule> schedulePage = new PageImpl<>(Collections.emptyList());
        List<Long> memberClasses = List.of(CLASS_ID);

        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn(TEST_EMAIL);
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(teacherUser));
        when(classMemberRepository.findClassOfMember(TEST_EMAIL)).thenReturn(memberClasses);
        when(classScheduleRepository.filterSchedule(any(SearchScheduleSto.class), eq(memberClasses), any(Pageable.class)))
                .thenReturn(schedulePage);

        Page<?> result = classScheduleService.getClassSchedule(httpServletRequest, dto, pageable);

        assertThat(result).isNotNull();
    }

    @Test
    void getClassSchedule_UserIsStudent_ReturnsPage() {
        SearchScheduleSto dto = new SearchScheduleSto();
        List<Long> memberClasses = List.of(CLASS_ID);
        Page<ClassSchedule> schedulePage = new PageImpl<>(Collections.emptyList());

        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn(TEST_EMAIL);
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(studentUser));
        when(classMemberRepository.findClassOfMember(TEST_EMAIL)).thenReturn(memberClasses);
        when(classScheduleRepository.filterSchedule(any(SearchScheduleSto.class), eq(memberClasses), any(Pageable.class)))
                .thenReturn(schedulePage);

        Page<?> result = classScheduleService.getClassSchedule(httpServletRequest, dto, pageable);

        assertThat(result).isNotNull();
    }

    @Test
    void getClassSchedule_UserIsAdmin_ThrowsWebToeicException() {
        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn(TEST_EMAIL);
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(adminUser));

        assertThatThrownBy(() ->
                classScheduleService.getClassSchedule(httpServletRequest, new SearchScheduleSto(), pageable))
                .isInstanceOf(WebToeicException.class);
    }

    @Test
    void getClassSchedule_ManagerWithNonEmptyPage_MapsEachItemViaConvertUtil() {
        // Cover line: result.map(item -> convertUtil.convertScheduleToDto(...))
        SearchScheduleSto dto = new SearchScheduleSto();
        ClassScheduleResponse response = new ClassScheduleResponse();
        Page<ClassSchedule> schedulePage = new PageImpl<>(List.of(classSchedule));

        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn(TEST_EMAIL);
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(managerUser));
        when(classScheduleRepository.filterSchedule(any(SearchScheduleSto.class), isNull(), any(Pageable.class)))
                .thenReturn(schedulePage);
        when(convertUtil.convertScheduleToDto(eq(httpServletRequest), eq(classSchedule))).thenReturn(response);

        Page<?> result = classScheduleService.getClassSchedule(httpServletRequest, dto, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0)).isEqualTo(response);
        verify(convertUtil).convertScheduleToDto(httpServletRequest, classSchedule);
    }

    @Test
    void getClassSchedule_ClassIdNotNullTeacherIdEmptyStatusEmpty_UserIsManager_ReturnsPage() {
        SearchScheduleSto dto = new SearchScheduleSto();
        dto.setClassId(List.of(CLASS_ID));
        dto.setTeacherId(Collections.emptyList());
        dto.setStatus(Collections.emptyList());

        Page<ClassSchedule> schedulePage = new PageImpl<>(Collections.emptyList());

        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn(TEST_EMAIL);
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(managerUser));
        when(classScheduleRepository.filterSchedule(any(SearchScheduleSto.class), isNull(), any(Pageable.class)))
                .thenReturn(schedulePage);

        Page<?> result = classScheduleService.getClassSchedule(httpServletRequest, dto, pageable);

        assertThat(result).isNotNull();
        assertThat(dto.getTeacherId()).isNull();
        assertThat(dto.getStatus()).isNull();
    }
@Test
void getClassSchedule_AllFiltersHaveValuesAndUserIsConsultant_ReturnsPage() {
    // QUAN TRỌNG: phải dùng CONSULTANT (không phải MANAGER)
    // Để JaCoCo cover nhánh: MANAGER=false → phải evaluate CONSULTANT=true
    SearchScheduleSto dto = new SearchScheduleSto();
    dto.setClassId(List.of(CLASS_ID));    // non-null, non-empty
    dto.setTeacherId(List.of(1L));         // non-null, non-empty  
    dto.setStatus(List.of("ACTIVE"));      // non-null, non-empty

    Page<ClassSchedule> schedulePage = new PageImpl<>(Collections.emptyList());

    when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn(TEST_EMAIL);
    when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(consultantUser));
    when(classScheduleRepository.filterSchedule(
            any(SearchScheduleSto.class), isNull(), any(Pageable.class)))
            .thenReturn(schedulePage);

    Page<?> result = classScheduleService.getClassSchedule(httpServletRequest, dto, pageable);

    assertThat(result).isNotNull();
    // Verify 3 filter fields KHÔNG bị set null (vì đều non-empty)
    assertThat(dto.getClassId()).isNotNull();
    assertThat(dto.getTeacherId()).isNotNull();
    assertThat(dto.getStatus()).isNotNull();
}

// ── Branch 2: classId = [] → isNull=false, isEmpty=true → vào if ─────
@Test
void getClassSchedule_ClassIdIsEmpty_ShouldSetClassIdNull() {
    SearchScheduleSto dto = new SearchScheduleSto();
    dto.setClassId(Collections.emptyList()); // isNull=false, isEmpty=true → vào if

    Page<ClassSchedule> page = new PageImpl<>(Collections.emptyList());
    when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn(TEST_EMAIL);
    when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(managerUser));
    when(classScheduleRepository.filterSchedule(any(), isNull(), any())).thenReturn(page);

    Page<?> result = classScheduleService.getClassSchedule(httpServletRequest, dto, pageable);

    assertThat(result).isNotNull();
    assertThat(dto.getClassId()).isNull(); // phải được set null
}

// ── Branch 3: classId có giá trị → isNull=false, isEmpty=false → không vào if ──
@Test
void getClassSchedule_ClassIdHasValue_ShouldNotSetClassIdNull() {
    SearchScheduleSto dto = new SearchScheduleSto();
    dto.setClassId(List.of(CLASS_ID)); // isNull=false, isEmpty=false → KHÔNG vào if

    Page<ClassSchedule> page = new PageImpl<>(Collections.emptyList());
    when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn(TEST_EMAIL);
    when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(managerUser));
    when(classScheduleRepository.filterSchedule(any(), isNull(), any())).thenReturn(page);

    Page<?> result = classScheduleService.getClassSchedule(httpServletRequest, dto, pageable);

    assertThat(result).isNotNull();
    assertThat(dto.getClassId())
            .as("classId có giá trị không được bị set null")
            .isNotNull()
            .isNotEmpty();
}

    // ============================================================ 
    // 6. createScheduleInClass()
    // ============================================================

    @Test
    void createScheduleInClass_UserNotExisted_ThrowsWebToeicException() {
        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn(TEST_EMAIL);
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                classScheduleService.createScheduleInClass(httpServletRequest, List.of(new ClassScheduleRequest())))
                .isInstanceOf(WebToeicException.class);
    }

    @Test
    void createScheduleInClass_EmptyList_ReturnsEmptyList() {
        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn(TEST_EMAIL);
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(managerUser));

        List<?> result = classScheduleService.createScheduleInClass(httpServletRequest, Collections.emptyList());

        assertThat(result).isEmpty();
    }

    @Test
    void createScheduleInClass_ClassNotExisted_ThrowsWebToeicException() {
        ClassScheduleRequest request = buildValidScheduleRequest();
        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn(TEST_EMAIL);
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(managerUser));
        when(classRepository.findById(CLASS_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                classScheduleService.createScheduleInClass(httpServletRequest, List.of(request)))
                .isInstanceOf(WebToeicException.class);
    }

    @Test
    void createScheduleInClass_RoomAlreadyBooked_ThrowsWebToeicException() {
        ClassScheduleRequest request = buildValidScheduleRequest();
        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn(TEST_EMAIL);
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(managerUser));
        when(classRepository.findById(CLASS_ID)).thenReturn(Optional.of(clazz));
        when(classScheduleRepository.existsScheduleByRoomIdAndStartAtAndEndAt(
                any(), any(), eq(ROOM_ID))).thenReturn(List.of(99L));

        assertThatThrownBy(() ->
                classScheduleService.createScheduleInClass(httpServletRequest, List.of(request)))
                .isInstanceOf(WebToeicException.class);
    }

    @Test
    void createScheduleInClass_ClassAlreadyHasOverlappingSchedule_ThrowsWebToeicException() {
        ClassScheduleRequest request = buildValidScheduleRequest();
        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn(TEST_EMAIL);
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(managerUser));
        when(classRepository.findById(CLASS_ID)).thenReturn(Optional.of(clazz));
        when(classScheduleRepository.existsScheduleByRoomIdAndStartAtAndEndAt(
                any(), any(), eq(ROOM_ID))).thenReturn(Collections.emptyList());
        when(classScheduleRepository.existsScheduleByClassIdAndStartAtAndEndAt(
                any(), any(), eq(CLASS_ID))).thenReturn(List.of(99L));

        assertThatThrownBy(() ->
                classScheduleService.createScheduleInClass(httpServletRequest, List.of(request)))
                .isInstanceOf(WebToeicException.class);
    }

    @Test
    void createScheduleInClass_ValidRequest_ReturnsCreatedScheduleList() {
        ClassScheduleRequest request = buildValidScheduleRequest();
        ClassSchedule savedSchedule = classSchedule;
        ClassScheduleResponse response = new ClassScheduleResponse();

        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn(TEST_EMAIL);
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(managerUser));
        when(classRepository.findById(CLASS_ID)).thenReturn(Optional.of(clazz));
        when(classScheduleRepository.existsScheduleByRoomIdAndStartAtAndEndAt(
                any(), any(), eq(ROOM_ID))).thenReturn(Collections.emptyList());
        when(classScheduleRepository.existsScheduleByClassIdAndStartAtAndEndAt(
                any(), any(), eq(CLASS_ID))).thenReturn(Collections.emptyList());
        when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
        when(classScheduleRepository.save(any(ClassSchedule.class))).thenReturn(savedSchedule);
        when(convertUtil.convertScheduleToDto(httpServletRequest, savedSchedule)).thenReturn(response);

        List<?> result = classScheduleService.createScheduleInClass(httpServletRequest, List.of(request));

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isEqualTo(response);
    }

    @Test
    void createScheduleInClass_RoomNotFoundInRepository_ThrowsWebToeicException() {
        ClassScheduleRequest request = buildValidScheduleRequest();
        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn(TEST_EMAIL);
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(managerUser));
        when(classRepository.findById(CLASS_ID)).thenReturn(Optional.of(clazz));
        when(classScheduleRepository.existsScheduleByRoomIdAndStartAtAndEndAt(
                any(), any(), eq(ROOM_ID))).thenReturn(Collections.emptyList());
        when(classScheduleRepository.existsScheduleByClassIdAndStartAtAndEndAt(
                any(), any(), eq(CLASS_ID))).thenReturn(Collections.emptyList());
        when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                classScheduleService.createScheduleInClass(httpServletRequest, List.of(request)))
                .isInstanceOf(WebToeicException.class);
    }

    // ============================================================
    // 7. updateScheduleInClass()
    // ============================================================

    @Test
    void updateScheduleInClass_UserNotExisted_ThrowsWebToeicException() {
        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn(TEST_EMAIL);
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                classScheduleService.updateScheduleInClass(httpServletRequest, new ClassScheduleRequest()))
                .isInstanceOf(WebToeicException.class);
    }

    @Test
    void updateScheduleInClass_ScheduleNotExisted_ThrowsWebToeicException() {
        ClassScheduleRequest request = buildValidScheduleRequest();
        request.setClassScheduleId(SCHEDULE_ID);

        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn(TEST_EMAIL);
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(teacherUser));
        when(classScheduleRepository.findById(SCHEDULE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                classScheduleService.updateScheduleInClass(httpServletRequest, request))
                .isInstanceOf(WebToeicException.class);
    }

    @Test
    void updateScheduleInClass_UserIsNeitherConsultantNorClassTeacher_ThrowsWebToeicException() {
        ClassScheduleRequest request = buildValidScheduleRequest();
        request.setClassScheduleId(SCHEDULE_ID);

        // teacherUser is not the teacher of this class (class teacher has different email)
        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn(TEST_EMAIL);
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(teacherUser));
        when(classScheduleRepository.findById(SCHEDULE_ID)).thenReturn(Optional.of(classSchedule));

        assertThatThrownBy(() ->
                classScheduleService.updateScheduleInClass(httpServletRequest, request))
                .isInstanceOf(WebToeicException.class);
    }

    @Test
    void updateScheduleInClass_UserIsConsultantAndRoomNotChanged_ReturnsUpdatedResponse() {
        ClassScheduleRequest request = buildValidScheduleRequest();
        request.setClassScheduleId(SCHEDULE_ID);
        // Same roomId and same time → no conflict check
        request.setStartAt(classSchedule.getStartAt());
        request.setEndAt(classSchedule.getEndAt());

        ClassScheduleResponse response = new ClassScheduleResponse();

        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn(TEST_EMAIL);
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(consultantUser));
        when(classScheduleRepository.findById(SCHEDULE_ID)).thenReturn(Optional.of(classSchedule));
        when(classScheduleRepository.save(any(ClassSchedule.class))).thenReturn(classSchedule);
        when(convertUtil.convertScheduleToDto(httpServletRequest, classSchedule)).thenReturn(response);

        ClassScheduleResponse result = classScheduleService.updateScheduleInClass(httpServletRequest, request);

        assertThat(result).isEqualTo(response);
        verify(classScheduleRepository, never())
                .existsScheduleByRoomIdAndStartAtAndEndAt(any(), any(), any());
    }

    @Test
    void updateScheduleInClass_ClassTeacherChangesRoomAndRoomIsBooked_ThrowsWebToeicException() {
        // Make teacherUser the teacher of the class
        clazz.getTeacher().setEmail(TEST_EMAIL);

        ClassScheduleRequest request = buildValidScheduleRequest();
        request.setClassScheduleId(SCHEDULE_ID);
        request.setRoomId(99L); // different room

        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn(TEST_EMAIL);
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(teacherUser));
        when(classScheduleRepository.findById(SCHEDULE_ID)).thenReturn(Optional.of(classSchedule));
        when(classScheduleRepository.existsScheduleByRoomIdAndStartAtAndEndAt(
                any(), any(), eq(99L))).thenReturn(List.of(99L));

        assertThatThrownBy(() ->
                classScheduleService.updateScheduleInClass(httpServletRequest, request))
                .isInstanceOf(WebToeicException.class);
    }

    @Test
    void updateScheduleInClass_ClassTeacherChangesRoomAndRoomIsAvailable_ReturnsUpdatedResponse() {
        clazz.getTeacher().setEmail(TEST_EMAIL);

        ClassScheduleRequest request = buildValidScheduleRequest();
        request.setClassScheduleId(SCHEDULE_ID);
        request.setRoomId(99L); // different room, but available

        ClassScheduleResponse response = new ClassScheduleResponse();

        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn(TEST_EMAIL);
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(teacherUser));
        when(classScheduleRepository.findById(SCHEDULE_ID)).thenReturn(Optional.of(classSchedule));
        when(classScheduleRepository.existsScheduleByRoomIdAndStartAtAndEndAt(
                any(), any(), eq(99L))).thenReturn(Collections.emptyList());
        when(classScheduleRepository.save(any(ClassSchedule.class))).thenReturn(classSchedule);
        when(convertUtil.convertScheduleToDto(httpServletRequest, classSchedule)).thenReturn(response);

        ClassScheduleResponse result = classScheduleService.updateScheduleInClass(httpServletRequest, request);

        assertThat(result).isEqualTo(response);
    }

    @Test
    void updateScheduleInClass_SameRoomDifferentStartAt_EntersConflictCheck_Branch2() {
        // Cover branch 2: roomId same → false, startAt different → true → enter if block
        // Dùng spy để stub getStartAt() trả về giá trị cũ dù FieldUpdateUtil đã setStartAt
        Date oldStartAt = new Date(0); // epoch — khác với request.startAt
        Date newEndAt = Date.from(LocalDateTime.now().plusDays(1).plusHours(2)
                .atZone(ZoneId.systemDefault()).toInstant());

        ClassSchedule spySchedule = spy(classSchedule);
        doReturn(oldStartAt).when(spySchedule).getStartAt(); // getStartAt vẫn trả old sau khi FieldUpdateUtil set
        doReturn(newEndAt).when(spySchedule).getEndAt();     // same as request → endAt branch = false

        ClassScheduleRequest request = buildValidScheduleRequest();
        request.setClassScheduleId(SCHEDULE_ID);
        request.setRoomId(ROOM_ID); // same room → first condition false
        request.setEndAt(newEndAt); // same as spy.getEndAt() → third condition false
        // request.startAt (non-null) != spy.getStartAt() (epoch) → second condition TRUE → enter if

        ClassScheduleResponse response = new ClassScheduleResponse();

        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn(TEST_EMAIL);
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(consultantUser));
        when(classScheduleRepository.findById(SCHEDULE_ID)).thenReturn(Optional.of(spySchedule));
        when(classScheduleRepository.existsScheduleByRoomIdAndStartAtAndEndAt(
                any(), any(), eq(ROOM_ID))).thenReturn(Collections.emptyList());
        when(classScheduleRepository.save(any(ClassSchedule.class))).thenReturn(spySchedule);
        when(convertUtil.convertScheduleToDto(eq(httpServletRequest), any(ClassSchedule.class))).thenReturn(response);

        ClassScheduleResponse result = classScheduleService.updateScheduleInClass(httpServletRequest, request);

        assertThat(result).isEqualTo(response);
        verify(classScheduleRepository).existsScheduleByRoomIdAndStartAtAndEndAt(any(), any(), eq(ROOM_ID));
    }

    @Test
    void updateScheduleInClass_SameRoomSameStartAtDifferentEndAt_EntersConflictCheck_Branch3() {
        // Cover branch 3: roomId same → false, startAt same → false, endAt different → true → enter if block
        Date sharedStartAt = Date.from(LocalDateTime.now().plusDays(1)
                .atZone(ZoneId.systemDefault()).toInstant());
        Date oldEndAt = new Date(0); // epoch — khác với request.endAt

        ClassSchedule spySchedule = spy(classSchedule);
        doReturn(sharedStartAt).when(spySchedule).getStartAt(); // same as request → second condition false
        doReturn(oldEndAt).when(spySchedule).getEndAt();        // khác request → third condition TRUE → enter if

        ClassScheduleRequest request = buildValidScheduleRequest();
        request.setClassScheduleId(SCHEDULE_ID);
        request.setRoomId(ROOM_ID);      // same → first condition false
        request.setStartAt(sharedStartAt); // same as spy.getStartAt() → second condition false
        // request.endAt (newEndAt) != spy.getEndAt() (epoch) → third condition TRUE

        ClassScheduleResponse response = new ClassScheduleResponse();

        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn(TEST_EMAIL);
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(consultantUser));
        when(classScheduleRepository.findById(SCHEDULE_ID)).thenReturn(Optional.of(spySchedule));
        when(classScheduleRepository.existsScheduleByRoomIdAndStartAtAndEndAt(
                any(), any(), eq(ROOM_ID))).thenReturn(Collections.emptyList());
        when(classScheduleRepository.save(any(ClassSchedule.class))).thenReturn(spySchedule);
        when(convertUtil.convertScheduleToDto(eq(httpServletRequest), any(ClassSchedule.class))).thenReturn(response);

        ClassScheduleResponse result = classScheduleService.updateScheduleInClass(httpServletRequest, request);

        assertThat(result).isEqualTo(response);
        verify(classScheduleRepository).existsScheduleByRoomIdAndStartAtAndEndAt(any(), any(), eq(ROOM_ID));
    }

    // ============================================================
    // 8. cancelledScheduleInClass()
    // ============================================================

    @Test
    void cancelledScheduleInClass_UserNotExisted_ThrowsWebToeicException() {
        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn(TEST_EMAIL);
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                classScheduleService.cancelledScheduleInClass(httpServletRequest, List.of(SCHEDULE_ID)))
                .isInstanceOf(WebToeicException.class);
    }

    @Test
    void cancelledScheduleInClass_EmptyList_ReturnsWithoutProcessing() {
        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn(TEST_EMAIL);
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(teacherUser));

        assertThatCode(() ->
                classScheduleService.cancelledScheduleInClass(httpServletRequest, Collections.emptyList()))
                .doesNotThrowAnyException();

        verify(classScheduleRepository, never()).findById(any());
    }

    @Test
    void cancelledScheduleInClass_ScheduleNotExisted_ThrowsWebToeicException() {
        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn(TEST_EMAIL);
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(teacherUser));
        when(classScheduleRepository.findById(SCHEDULE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                classScheduleService.cancelledScheduleInClass(httpServletRequest, List.of(SCHEDULE_ID)))
                .isInstanceOf(WebToeicException.class);
    }

    @Test
    void cancelledScheduleInClass_ClassTeacherCancels_SetsStatusCancelledAndContinues() {
        // teacherUser.getCode() == schedule's class teacher code
        clazz.getTeacher().setCode(teacherUser.getCode());

        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn(TEST_EMAIL);
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(teacherUser));
        when(classScheduleRepository.findById(SCHEDULE_ID)).thenReturn(Optional.of(classSchedule));
        when(classScheduleRepository.save(any(ClassSchedule.class))).thenReturn(classSchedule);

        assertThatCode(() ->
                classScheduleService.cancelledScheduleInClass(httpServletRequest, List.of(SCHEDULE_ID)))
                .doesNotThrowAnyException();

        assertThat(classSchedule.getStatus()).isEqualTo(EScheduleStatus.CANCELLED);
        assertThat(classSchedule.getIsActive()).isFalse();
        verify(classScheduleRepository).save(classSchedule);
    }

    @Test
    void cancelledScheduleInClass_UserIsConsultant_SetsStatusCancelledAndContinues() {
        // consultantUser.getRole().getCode() == CONSULTANT → allowed
        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn(TEST_EMAIL);
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(consultantUser));
        when(classScheduleRepository.findById(SCHEDULE_ID)).thenReturn(Optional.of(classSchedule));
        when(classScheduleRepository.save(any(ClassSchedule.class))).thenReturn(classSchedule);

        assertThatCode(() ->
                classScheduleService.cancelledScheduleInClass(httpServletRequest, List.of(SCHEDULE_ID)))
                .doesNotThrowAnyException();

        assertThat(classSchedule.getStatus()).isEqualTo(EScheduleStatus.CANCELLED);
        assertThat(classSchedule.getIsActive()).isFalse();
        verify(classScheduleRepository).save(classSchedule);
    }

    @Test
    void cancelledScheduleInClass_StudentRole_ThrowsWebToeicException() {
    when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn(TEST_EMAIL);
    when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(studentUser));
    when(classScheduleRepository.findById(SCHEDULE_ID)).thenReturn(Optional.of(classSchedule));

    assertThatThrownBy(() ->
        classScheduleService.cancelledScheduleInClass(httpServletRequest, List.of(SCHEDULE_ID)))
        .isInstanceOf(WebToeicException.class);
    }

    @Test
    void cancelledScheduleInClass_TeacherNotOwnerOfClass_ThrowsWebToeicException() {
    when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn(TEST_EMAIL);
    when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(teacherUser));
    when(classScheduleRepository.findById(SCHEDULE_ID)).thenReturn(Optional.of(classSchedule));

    assertThatThrownBy(() ->
        classScheduleService.cancelledScheduleInClass(httpServletRequest, List.of(SCHEDULE_ID)))
        .isInstanceOf(WebToeicException.class);
    }

    // ============================================================
    // Helper
    // ============================================================

    private ClassScheduleRequest buildValidScheduleRequest() {
        ClassScheduleRequest request = new ClassScheduleRequest();
        request.setClassId(CLASS_ID);
        request.setRoomId(ROOM_ID);
        request.setTitle("Test Schedule");
        // status=1 (ACTIVE) để EScheduleStatus.fromValue(1) không ném exception
        request.setStatus(1);
        request.setStartAt(Date.from(LocalDateTime.now().plusDays(1)
                .atZone(ZoneId.systemDefault()).toInstant()));
        request.setEndAt(Date.from(LocalDateTime.now().plusDays(1).plusHours(2)
                .atZone(ZoneId.systemDefault()).toInstant()));
        return request;
    }
}