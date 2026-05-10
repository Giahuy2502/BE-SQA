package com.doan2025.webtoeic.service.impl;

import com.doan2025.webtoeic.constants.enums.*;
import com.doan2025.webtoeic.domain.*;
import com.doan2025.webtoeic.domain.Class;
import com.doan2025.webtoeic.dto.request.AttendanceRequest;
import com.doan2025.webtoeic.exception.WebToeicException;
import com.doan2025.webtoeic.repository.AttendanceRepository;
import com.doan2025.webtoeic.repository.ClassScheduleRepository;
import com.doan2025.webtoeic.repository.UserRepository;
import com.doan2025.webtoeic.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttendanceServiceImplTest {
    @Mock private ClassScheduleRepository classScheduleRepository;
    @Mock private AttendanceRepository attendanceRepository;
    @Mock private UserRepository userRepository;
    @Mock private JwtUtil jwtUtil;
    @Mock private HttpServletRequest request;

    // @InjectMocks: Mockito tạo instance thật của AttendanceServiceImpl
    // và tự động inject tất cả các @Mock vào đây thay cho constructor/field injection.
    @InjectMocks
    private AttendanceServiceImpl attendanceService;

    // Email hằng số dùng chung cho giáo viên trong toàn bộ test
    private static final String TEACHER_EMAIL = "teacher@test.com";

    // Các đối tượng dùng chung, được khởi tạo lại trước MỖI test (trong @BeforeEach)
    private User teacher;
    private ClassSchedule scheduleActive;      // Lịch đang diễn ra: startAt=now-30min, endAt=now+90min
    private ClassSchedule scheduleEarlyFuture; // Lịch chưa bắt đầu: startAt=now+2h
    private ClassSchedule schedulePast;        // Lịch đã qua: endAt=now-2h
    private ClassSchedule scheduleEnded;       // Lịch đã kết thúc (dùng cho nhánh orElse(last))

    // ============================================================
    // HELPER METHOD — buildRequests()
    // ============================================================

    /**
     * Tạo List<AttendanceRequest> có 1 phần tử để truyền vào service.
     *
     * Lý do dùng new ArrayList<>(List.of(...)) thay vì List.of(...) trực tiếp:
     * - List.of() trả về danh sách BẤT BIẾN (immutable) — không thể thêm/sửa/xóa.
     * - Một số test cần set null vào phần tử request để test nhánh null-check.
     * - ArrayList là danh sách CÓ THỂ THAY ĐỔI (mutable) — tránh UnsupportedOperationException.
     */
    private List<AttendanceRequest> buildRequests(Long scheduleId, Long attendanceId,
                                                   int status, Long studentId) {
        AttendanceRequest req = new AttendanceRequest();
        req.setScheduleId(scheduleId);       // null → trigger IS_NULL exception
        req.setAttendanceId(attendanceId);   // ID bản ghi điểm danh cần update
        req.setAttendanceStatus(status);     // 1=PRESENT, 2=ABSENT, 3=LATE
        req.setStudentId(studentId);         // ID học viên cần điểm danh
        return new ArrayList<>(List.of(req));
    }

    // ============================================================
    // SETUP — Chạy trước MỖI test method
    // ============================================================

    /**
     * @BeforeEach đảm bảo mỗi test bắt đầu với trạng thái sạch, độc lập nhau.
     * Tất cả các đối tượng được tạo lại từ đầu — test này không ảnh hưởng test kia.
     */
    @BeforeEach
    void setUp() {
        // --- Tạo giáo viên mặc định ---
        teacher = new User();
        teacher.setEmail(TEACHER_EMAIL);
        teacher.setRole(ERole.TEACHER);

        // Lớp học có giáo viên là teacher ở trên
        Class clazz = new Class();
        clazz.setTeacher(teacher);

        Instant now = Instant.now(); // Thời điểm hiện tại làm mốc tính toán

        // --- scheduleActive: lịch đang trong cửa sổ hợp lệ [start-15min, end+15min] ---
        // now - 30min < now < now + 90min → hợp lệ để điểm danh
        scheduleActive = new ClassSchedule();
        scheduleActive.setId(1L);
        scheduleActive.setClazz(clazz);
        scheduleActive.setStartAt(Timestamp.from(now.minusSeconds(1800)));  // now - 30 phút
        scheduleActive.setEndAt(Timestamp.from(now.plusSeconds(5400)));     // now + 90 phút
        scheduleActive.setIsAttendance(false); // Chưa được điểm danh lần nào

        // --- scheduleEarlyFuture: lịch chưa đến giờ ---
        // now < startAt - 15min → quá sớm, chưa được phép điểm danh
        scheduleEarlyFuture = new ClassSchedule();
        scheduleEarlyFuture.setId(2L);
        scheduleEarlyFuture.setClazz(clazz);
        scheduleEarlyFuture.setStartAt(Timestamp.from(now.plusSeconds(7200)));  // now + 2 tiếng
        scheduleEarlyFuture.setEndAt(Timestamp.from(now.plusSeconds(14400)));   // now + 4 tiếng
        scheduleEarlyFuture.setIsAttendance(false);

        // --- schedulePast: lịch đã qua giờ ---
        // now > endAt + 15min → quá hạn, không được phép điểm danh
        schedulePast = new ClassSchedule();
        schedulePast.setId(3L);
        schedulePast.setClazz(clazz);
        schedulePast.setStartAt(Timestamp.from(now.minusSeconds(14400))); // now - 4 tiếng
        schedulePast.setEndAt(Timestamp.from(now.minusSeconds(7200)));    // now - 2 tiếng
        schedulePast.setIsAttendance(false);

        // --- scheduleEnded: lịch đã kết thúc, dùng để test nhánh orElse(last) ---
        // Khi filter không tìm được lịch nào còn hiệu lực, dùng lịch cuối cùng
        scheduleEnded = new ClassSchedule();
        scheduleEnded.setId(4L);
        scheduleEnded.setClazz(clazz);
        scheduleEnded.setStartAt(Timestamp.from(now.minusSeconds(14400)));
        scheduleEnded.setEndAt(Timestamp.from(now.minusSeconds(7200)));
        scheduleEnded.setIsAttendance(false);
    }


    // ============================================================
    // NHÓM TEST: updateAttendance() — Cập nhật điểm danh
    // ============================================================

    // ----------------------------------------------------------
    // [OFF-138] | ✅ Pass
    // Mục tiêu: Báo lỗi khi token không map được user trong DB.
    @Test
    void updateAttendance_UserNotFound_ThrowsException() {
        when(jwtUtil.getEmailFromToken(request)).thenReturn(TEACHER_EMAIL);
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        // Kỳ vọng: service phải ném WebToeicException
        assertThrows(WebToeicException.class,
                () -> attendanceService.updateAttendance(request, buildRequests(1L, 10L, 1, 5L)));
    }

    // ----------------------------------------------------------
    // [OFF-139] | ✅ Pass
    // Mục tiêu: Báo lỗi IS_NULL khi request không truyền scheduleId.
    // ----------------------------------------------------------
    @Test
    void updateAttendance_ScheduleIdIsNull_ThrowsException() {
        // scheduleId = null → trường bắt buộc bị thiếu
        List<AttendanceRequest> reqs = buildRequests(null, 10L, 1, 5L);

        when(jwtUtil.getEmailFromToken(request)).thenReturn(TEACHER_EMAIL);
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(teacher));

        // Service phải phát hiện scheduleId=null và trả về mã lỗi IS_NULL
        WebToeicException ex = assertThrows(WebToeicException.class,
                () -> attendanceService.updateAttendance(request, reqs));
        assertEquals(ResponseCode.IS_NULL, ex.getResponseCode());
    }

    // ----------------------------------------------------------
    // [OFF-140] | ✅ Pass
    // Mục tiêu: Báo lỗi khi scheduleId không tồn tại trong DB.
    // ----------------------------------------------------------
    @Test
    void updateAttendance_ScheduleNotFound_ThrowsException() {
        when(jwtUtil.getEmailFromToken(request)).thenReturn(TEACHER_EMAIL);
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(teacher));

        // scheduleId=1 không có trong DB → Optional.empty()
        when(classScheduleRepository.findById(1L)).thenReturn(Optional.empty());

        // Kỳ vọng: service ném exception vì không tìm thấy lịch học
        assertThrows(WebToeicException.class,
                () -> attendanceService.updateAttendance(request, buildRequests(1L, 10L, 1, 5L)));
    }

    // ----------------------------------------------------------
    // [OFF-141] | ✅ Pass
    // Mục tiêu: Từ chối khi role=STUDENT VÀ email không khớp teacher lớp.
    //
    // Logic phân quyền trong updateAttendance() dùng điều kiện AND:
    //   if (!isTeacher AND !emailMatch) → throw NOT_PERMISSION
    //
    // Test này: !isTeacher=true (STUDENT) AND !emailMatch=true → AND=true → throw
    // ----------------------------------------------------------
    @Test
    void updateAttendance_NotTeacherRoleAndEmailNotMatch_ThrowsPermissionException() {
        User student = new User();
        student.setEmail("student@test.com");
        student.setRole(ERole.STUDENT);

        when(jwtUtil.getEmailFromToken(request)).thenReturn("student@test.com");
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(student));
        when(classScheduleRepository.findById(1L)).thenReturn(Optional.of(scheduleActive));

        // Cả hai điều kiện đều thỏa → phải throw NOT_PERMISSION
        WebToeicException ex = assertThrows(WebToeicException.class,
                () -> attendanceService.updateAttendance(request, buildRequests(1L, 10L, 1, 5L)));
        assertEquals(ResponseCode.NOT_PERMISSION, ex.getResponseCode());
    }

    // ----------------------------------------------------------
    // [OFF-143] | ✅ Pass
    // Mục tiêu: Báo lỗi NOT_START khi điểm danh trước giờ học.
    //
    // Service kiểm tra: now phải nằm trong [startAt - 15ph, endAt + 15ph].
    // scheduleEarlyFuture có startAt = now + 2h → now < start - 15ph → NOT_START
    // ----------------------------------------------------------
    @Test
    void updateAttendance_TimeBeforeStart_ThrowsNotStartException() {
        when(jwtUtil.getEmailFromToken(request)).thenReturn(TEACHER_EMAIL);
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(teacher));

        // Dùng scheduleId=2 → map tới scheduleEarlyFuture (startAt=now+2h)
        when(classScheduleRepository.findById(2L)).thenReturn(Optional.of(scheduleEarlyFuture));

        WebToeicException ex = assertThrows(WebToeicException.class,
                () -> attendanceService.updateAttendance(request, buildRequests(2L, 10L, 1, 5L)));
        assertEquals(ResponseCode.NOT_START, ex.getResponseCode());
    }

    // ----------------------------------------------------------
    // [OFF-144] | ✅ Pass
    // Mục tiêu: Báo lỗi OVER_DUE khi điểm danh sau giờ học.
    //
    // schedulePast có endAt = now - 2h → now > end + 15ph → OVER_DUE
    // ----------------------------------------------------------
    @Test
    void updateAttendance_TimeAfterEnd_ThrowsOverDueException() {
        when(jwtUtil.getEmailFromToken(request)).thenReturn(TEACHER_EMAIL);
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(teacher));

        // Dùng scheduleId=3 → map tới schedulePast (endAt=now-2h)
        when(classScheduleRepository.findById(3L)).thenReturn(Optional.of(schedulePast));

        WebToeicException ex = assertThrows(WebToeicException.class,
                () -> attendanceService.updateAttendance(request, buildRequests(3L, 10L, 1, 5L)));
        assertEquals(ResponseCode.OVER_DUE, ex.getResponseCode());
    }

    // ----------------------------------------------------------
    // [OFF-145] | ✅ Pass
    // Mục tiêu: Báo lỗi khi attendanceId không tồn tại trong DB.
    // Thời gian hợp lệ (scheduleActive) nhưng bản ghi điểm danh không tồn tại.
    // ----------------------------------------------------------
    @Test
    void updateAttendance_AttendanceNotFound_ThrowsException() {
        when(jwtUtil.getEmailFromToken(request)).thenReturn(TEACHER_EMAIL);
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(teacher));
        when(classScheduleRepository.findById(1L)).thenReturn(Optional.of(scheduleActive));

        // attendanceId=10 không có trong DB → Optional.empty()
        when(attendanceRepository.findById(10L)).thenReturn(Optional.empty());

        assertThrows(WebToeicException.class,
                () -> attendanceService.updateAttendance(request, buildRequests(1L, 10L, 1, 5L)));
    }

    // ----------------------------------------------------------
    // [OFF-146] | ✅ Pass
    // Mục tiêu: Cập nhật điểm danh thành công khi status mới KHÁC status cũ.
    //
    // FieldUpdateUtil: so sánh currentValue và newValue — nếu khác nhau thì cập nhật.
    // attendance.status = ABSENT (2) → request status = 1 (PRESENT) → khác → cập nhật
    // ----------------------------------------------------------
    @Test
    void updateAttendance_ValidRequest_StatusDiffers_UpdatesAndSavesAll() {
        // Bản ghi điểm danh hiện tại có status=ABSENT
        Attendance attendance = new Attendance();
        attendance.setStatus(EAttendanceStatus.ABSENT);

        when(jwtUtil.getEmailFromToken(request)).thenReturn(TEACHER_EMAIL);
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(teacher));
        when(classScheduleRepository.findById(1L)).thenReturn(Optional.of(scheduleActive));
        when(attendanceRepository.findById(10L)).thenReturn(Optional.of(attendance));

        attendanceService.updateAttendance(request, buildRequests(1L, 10L, 1, 5L));

        // Sau khi update: status phải được đổi thành PRESENT
        assertEquals(EAttendanceStatus.PRESENT, attendance.getStatus());
        // saveAll() phải được gọi để lưu thay đổi vào DB
        verify(attendanceRepository).saveAll(any());
    }

    // ----------------------------------------------------------
    // [OFF-146 — sub-case] | ✅ Pass (code chạy đúng)
    // Mục tiêu: FieldUpdateUtil bỏ qua cập nhật khi status mới GIỐNG status cũ.
    //
    // attendance.status = PRESENT (1) → request status = 1 (PRESENT) → giống → no-op
    // Nhưng saveAll() vẫn được gọi (chỉ là không có thay đổi gì trong list)
    // ----------------------------------------------------------
    @Test
    void updateAttendance_ValidRequest_StatusSame_FieldUpdateUtilSkips() {
        // Bản ghi điểm danh hiện tại đã là PRESENT
        Attendance attendance = new Attendance();
        attendance.setStatus(EAttendanceStatus.PRESENT);

        when(jwtUtil.getEmailFromToken(request)).thenReturn(TEACHER_EMAIL);
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(teacher));
        when(classScheduleRepository.findById(1L)).thenReturn(Optional.of(scheduleActive));
        when(attendanceRepository.findById(10L)).thenReturn(Optional.of(attendance));

        attendanceService.updateAttendance(request, buildRequests(1L, 10L, 1, 5L));

        // FieldUpdateUtil không thay đổi gì — status vẫn là PRESENT
        assertEquals(EAttendanceStatus.PRESENT, attendance.getStatus());
        // saveAll() vẫn được gọi (dù list không đổi)
        verify(attendanceRepository).saveAll(any());
    }


    // ============================================================
    // NHÓM TEST: attendance() — Tạo điểm danh mới
    // ============================================================

    // ----------------------------------------------------------
    // [OFF-147] | ✅ Pass
    // Mục tiêu: Báo lỗi khi token không map được user trong DB.
    // Guard đầu tiên giống OFF-138.
    // ----------------------------------------------------------
    @Test
    void attendance_UserNotFound_ThrowsException() {
        when(jwtUtil.getEmailFromToken(request)).thenReturn(TEACHER_EMAIL);
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        assertThrows(WebToeicException.class,
                () -> attendanceService.attendance(request, buildRequests(1L, 10L, 1, 5L)));
    }

    // ----------------------------------------------------------
    // [OFF-148] | ✅ Pass
    // Mục tiêu: Báo lỗi khi getAvailableSchedule() trả về null.
    //
    // getAvailableSchedule() query lịch học khả dụng theo danh sách lớp user thuộc về.
    // Nếu trả về null → không có lịch nào → throw NOT_AVAILABLE
    // ----------------------------------------------------------
    @Test
    void attendance_NoAvailableSchedule_ThrowsException() {
        when(jwtUtil.getEmailFromToken(request)).thenReturn(TEACHER_EMAIL);
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(teacher));

        // Không có lịch học khả dụng nào
        when(classScheduleRepository.getAvailableSchedule(any())).thenReturn(null);

        assertThrows(WebToeicException.class,
                () -> attendanceService.attendance(request, buildRequests(1L, 10L, 1, 5L)));
    }

    // ----------------------------------------------------------
    // [OFF-149] | ✅ Pass
    // Mục tiêu: Báo lỗi khi lịch học đã được điểm danh từ trước.
    //
    // findByScheduleId() trả về list các attendanceId đã có → list không null/rỗng
    // → lịch này đã được điểm danh → throw EXISTED
    // ----------------------------------------------------------
    @Test
    void attendance_AlreadyAttended_ThrowsException() {
        when(jwtUtil.getEmailFromToken(request)).thenReturn(TEACHER_EMAIL);
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(teacher));
        when(classScheduleRepository.getAvailableSchedule(any())).thenReturn(List.of(1L));
        when(classScheduleRepository.findAllById(any())).thenReturn(new ArrayList<>(List.of(scheduleActive)));

        // findByScheduleId trả về list có phần tử [100] → đã tồn tại bản ghi điểm danh
        when(attendanceRepository.findByScheduleId(1L)).thenReturn(List.of(100L));

        assertThrows(WebToeicException.class,
                () -> attendanceService.attendance(request, buildRequests(1L, 10L, 1, 5L)));
    }

    // ----------------------------------------------------------
    // [OFF-150] | ✅ Pass
    // Mục tiêu: Báo lỗi khi findById() không tìm thấy lịch học.
    //
    // Vượt qua bước check "đã điểm danh chưa" (findByScheduleId=null)
    // nhưng findById() trả về empty → throw NOT_EXISTED
    // ----------------------------------------------------------
    @Test
    void attendance_ScheduleNotFoundById_ThrowsException() {
        when(jwtUtil.getEmailFromToken(request)).thenReturn(TEACHER_EMAIL);
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(teacher));
        when(classScheduleRepository.getAvailableSchedule(any())).thenReturn(List.of(1L));
        when(classScheduleRepository.findAllById(any())).thenReturn(new ArrayList<>(List.of(scheduleActive)));

        // null → pass bước check "đã điểm danh chưa"
        when(attendanceRepository.findByScheduleId(1L)).thenReturn(null);
        // findById empty → schedule không tồn tại trong DB
        when(classScheduleRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(WebToeicException.class,
                () -> attendanceService.attendance(request, buildRequests(1L, 10L, 1, 5L)));
    }

    // ----------------------------------------------------------
    // [OFF-151] | ✅ Pass
    // Mục tiêu: Báo lỗi khi lịch học đã được đánh dấu isAttendance=true.
    //
    // isAttendance=true nghĩa là lịch này đã từng có phiên điểm danh trước đó.
    // Service kiểm tra cờ này và từ chối tạo mới → throw EXISTED
    // ----------------------------------------------------------
    @Test
    void attendance_ScheduleIsAttendanceTrue_ThrowsException() {
        // Đặt cờ isAttendance=true trên scheduleActive
        scheduleActive.setIsAttendance(true);

        when(jwtUtil.getEmailFromToken(request)).thenReturn(TEACHER_EMAIL);
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(teacher));
        when(classScheduleRepository.getAvailableSchedule(any())).thenReturn(List.of(1L));
        when(classScheduleRepository.findAllById(any())).thenReturn(new ArrayList<>(List.of(scheduleActive)));
        when(attendanceRepository.findByScheduleId(1L)).thenReturn(null);
        when(classScheduleRepository.findById(1L)).thenReturn(Optional.of(scheduleActive));

        assertThrows(WebToeicException.class,
                () -> attendanceService.attendance(request, buildRequests(1L, 10L, 1, 5L)));
    }

    // ----------------------------------------------------------
    // [OFF-152] | ✅ Pass
    // Mục tiêu: Student bị từ chối tạo điểm danh.
    //
    // ⚠️ QUAN TRỌNG: attendance() dùng logic OR (khác với updateAttendance() dùng AND):
    //   if (!isTeacher OR !emailMatch) → throw NOT_PERMISSION
    //
    // STUDENT: !isTeacher=true → OR short-circuit → throw ngay
    // ----------------------------------------------------------
    @Test 
    void attendance_NotTeacherRole_ThrowsPermissionException() {
        User student = new User();
        student.setEmail("student@test.com");
        student.setRole(ERole.STUDENT); // !isTeacher=true → điều kiện OR thỏa ngay

        when(jwtUtil.getEmailFromToken(request)).thenReturn("student@test.com");
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(student));
        when(classScheduleRepository.getAvailableSchedule(any())).thenReturn(List.of(1L));
        when(classScheduleRepository.findAllById(any())).thenReturn(new ArrayList<>(List.of(scheduleActive)));
        when(attendanceRepository.findByScheduleId(1L)).thenReturn(null);
        when(classScheduleRepository.findById(1L)).thenReturn(Optional.of(scheduleActive));

        WebToeicException ex = assertThrows(WebToeicException.class,
                () -> attendanceService.attendance(request, buildRequests(1L, 10L, 1, 5L)));
        assertEquals(ResponseCode.NOT_PERMISSION, ex.getResponseCode());
    }

    // ----------------------------------------------------------
    // [OFF-153] | ✅ Pass
    // Mục tiêu: Teacher có role đúng nhưng email không khớp teacher lớp → bị từ chối.
    //
    // attendance() dùng điều kiện OR:
    //   !isTeacher=false (TEACHER) → kiểm tra tiếp !emailMatch
    //   !emailMatch=true ("wrong" ≠ TEACHER_EMAIL) → OR=true → throw NOT_PERMISSION
    // ----------------------------------------------------------
    @Test
    void attendance_TeacherRoleButWrongEmail_ThrowsPermissionException() {
        User wrongTeacher = new User();
        wrongTeacher.setEmail("wrong@test.com");      // email KHÔNG khớp teacher lớp
        wrongTeacher.setRole(ERole.TEACHER);           // role TEACHER → !isTeacher=false

        when(jwtUtil.getEmailFromToken(request)).thenReturn("wrong@test.com");
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(wrongTeacher));
        when(classScheduleRepository.getAvailableSchedule(any())).thenReturn(List.of(1L));
        when(classScheduleRepository.findAllById(any())).thenReturn(new ArrayList<>(List.of(scheduleActive)));
        when(attendanceRepository.findByScheduleId(1L)).thenReturn(null);
        when(classScheduleRepository.findById(1L)).thenReturn(Optional.of(scheduleActive));

        // !isTeacher=false nhưng !emailMatch=true → OR=true → throw
        WebToeicException ex = assertThrows(WebToeicException.class,
                () -> attendanceService.attendance(request, buildRequests(1L, 10L, 1, 5L)));
        assertEquals(ResponseCode.NOT_PERMISSION, ex.getResponseCode());
    }

    // ----------------------------------------------------------
    // [OFF-154] | ✅ Pass
    // Mục tiêu: Báo lỗi khi studentId không tồn tại trong DB.
    //
    // Đã vượt qua toàn bộ: user check, schedule check, permission check.
    // Tới bước tìm student để tạo bản ghi → không có → throw
    // ----------------------------------------------------------
    @Test
    void attendance_StudentNotFound_ThrowsException() {
        // Permission pass: isTeacher=true AND emailMatch=true → !false OR !false = false → không throw
        when(jwtUtil.getEmailFromToken(request)).thenReturn(TEACHER_EMAIL);
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(teacher));
        when(classScheduleRepository.getAvailableSchedule(any())).thenReturn(List.of(1L));
        when(classScheduleRepository.findAllById(any())).thenReturn(new ArrayList<>(List.of(scheduleActive)));
        when(attendanceRepository.findByScheduleId(1L)).thenReturn(null);
        when(classScheduleRepository.findById(1L)).thenReturn(Optional.of(scheduleActive));

        // studentId=5 không có trong DB
        when(userRepository.findById(5L)).thenReturn(Optional.empty());

        assertThrows(WebToeicException.class,
                () -> attendanceService.attendance(request, buildRequests(1L, 10L, 1, 5L)));
    }

    // ----------------------------------------------------------
    // [OFF-155] | ✅ Pass
    // Mục tiêu: Tạo điểm danh thành công — luồng hạnh phúc (happy path).
    //
    // scheduleActive.endAt = now+90min > now:
    //   → filter: endAt.after(now) = true → findFirst() trả về scheduleActive
    //   → không cần fallback orElse(last)
    // ----------------------------------------------------------
    @Test
    void attendance_ValidData_ScheduleCurrentlyActive_SavesAll() {
        User student = new User();
        student.setId(5L);

        when(jwtUtil.getEmailFromToken(request)).thenReturn(TEACHER_EMAIL);
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(teacher));
        when(classScheduleRepository.getAvailableSchedule(any())).thenReturn(List.of(1L));
        when(classScheduleRepository.findAllById(any())).thenReturn(new ArrayList<>(List.of(scheduleActive)));
        when(attendanceRepository.findByScheduleId(1L)).thenReturn(null);
        when(classScheduleRepository.findById(1L)).thenReturn(Optional.of(scheduleActive));
        when(userRepository.findById(5L)).thenReturn(Optional.of(student));

        attendanceService.attendance(request, buildRequests(1L, 10L, 1, 5L));

        // saveAll() phải được gọi để lưu toàn bộ bản ghi điểm danh mới
        verify(attendanceRepository).saveAll(anyList());
    }

    // ----------------------------------------------------------
    // [OFF-156] | ✅ Pass
    // Mục tiêu: Khi tất cả lịch học đã kết thúc, fallback dùng lịch cuối cùng.
    //
    // scheduleEnded.endAt = now-2h < now:
    //   → filter: endAt.after(now) = false → không tìm được schedule nào
    //   → .orElse(last): lấy phần tử cuối của danh sách làm fallback
    //   → vẫn tiếp tục tạo điểm danh với schedule đó
    // ----------------------------------------------------------
    @Test
    void attendance_AllSchedulesEnded_FallsBackToLastSchedule() {
        User student = new User();
        student.setId(5L);

        when(jwtUtil.getEmailFromToken(request)).thenReturn(TEACHER_EMAIL);
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(teacher));

        // scheduleEnded.id=4 → endAt đã qua → filter sẽ trả về empty → orElse(last)
        when(classScheduleRepository.getAvailableSchedule(any())).thenReturn(List.of(4L));
        when(classScheduleRepository.findAllById(any())).thenReturn(new ArrayList<>(List.of(scheduleEnded)));
        when(attendanceRepository.findByScheduleId(4L)).thenReturn(null);
        when(classScheduleRepository.findById(4L)).thenReturn(Optional.of(scheduleEnded));
        when(userRepository.findById(5L)).thenReturn(Optional.of(student));

        attendanceService.attendance(request, buildRequests(1L, 10L, 1, 5L));

        // saveAll() vẫn được gọi dù dùng lịch fallback
        verify(attendanceRepository).saveAll(anyList());
    }

    // ----------------------------------------------------------
    // [OFF-158] | ❌ Fail — Test phát hiện BUG
    // Mục tiêu: attendance() THIẾU kiểm tra thời gian (khác với updateAttendance()).
    //
    // updateAttendance() kiểm tra [start-15ph, end+15ph] → throw NOT_START nếu quá sớm.
    // attendance()       KHÔNG kiểm tra thời gian → saveAll() chạy bình thường dù chưa đến giờ.
    //
    // Test này viết đúng đặc tả (kỳ vọng NOT_START) nhưng sẽ FAIL vì service chưa implement.
    // Mục đích: expose bug, buộc team phải fix attendance() để thêm kiểm tra thời gian.
    // ----------------------------------------------------------
    @Test
    void attendance_TimeBeforeStart_ShouldThrowNotStart_ButCurrentlyMissing() {
        User student = new User();
        student.setId(5L);

        when(jwtUtil.getEmailFromToken(request)).thenReturn(TEACHER_EMAIL);
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(teacher));

        // scheduleEarlyFuture: startAt=now+2h → chưa đến giờ
        when(classScheduleRepository.getAvailableSchedule(any()))
                .thenReturn(List.of(scheduleEarlyFuture.getId()));
        when(classScheduleRepository.findAllById(any()))
                .thenReturn(new ArrayList<>(List.of(scheduleEarlyFuture)));
        when(attendanceRepository.findByScheduleId(scheduleEarlyFuture.getId()))
                .thenReturn(null);
        when(classScheduleRepository.findById(scheduleEarlyFuture.getId()))
                .thenReturn(Optional.of(scheduleEarlyFuture));
        when(userRepository.findById(5L)).thenReturn(Optional.of(student));

        // ĐẶC TẢ YÊU CẦU: phải throw NOT_START
        // THỰC TẾ: không throw gì → assertThrows fail → BUG được lộ ra
        WebToeicException ex = assertThrows(WebToeicException.class,
                () -> attendanceService.attendance(request, buildRequests(2L, 10L, 1, 5L)));
        assertEquals(ResponseCode.NOT_START, ex.getResponseCode());
    }

    // ----------------------------------------------------------
    // [OFF-142] | ✅ Pass
    // Mục tiêu: User có email khớp teacher lớp vượt qua phân quyền dù không phải TEACHER.
    //
    // updateAttendance() dùng điều kiện AND:
    //   if (!isTeacher AND !emailMatch) → throw NOT_PERMISSION
    //
    // MANAGER: !isTeacher=true, nhưng email TRÙNG → !emailMatch=false
    //   → AND: true AND false = false → KHÔNG throw → vượt qua permission check
    //
    // Đây là "back door" hợp lệ trong thiết kế: ai có email giống teacher lớp
    // đều được phép cập nhật điểm danh, kể cả không phải TEACHER.
    // ----------------------------------------------------------
    @Test
    void updateAttendance_NonTeacherRoleButEmailMatchesClassTeacher_PassesPermissionCheck() {
        User manager = new User();
        manager.setEmail(TEACHER_EMAIL); // email TRÙNG với teacher của lớp → !emailMatch=false
        manager.setRole(ERole.MANAGER);  // không phải TEACHER → !isTeacher=true

        Attendance attendance = new Attendance();
        attendance.setStatus(EAttendanceStatus.ABSENT);

        when(jwtUtil.getEmailFromToken(request)).thenReturn(TEACHER_EMAIL);
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(manager));
        when(classScheduleRepository.findById(1L)).thenReturn(Optional.of(scheduleActive));
        when(attendanceRepository.findById(10L)).thenReturn(Optional.of(attendance));

        // Không throw bất kỳ exception nào — permission được pass
        assertDoesNotThrow(() ->
                attendanceService.updateAttendance(request, buildRequests(1L, 10L, 1, 5L)));
        verify(attendanceRepository).saveAll(any());
    }

    // ----------------------------------------------------------
    // [OFF-157] | ✅ Pass
    // Mục tiêu: Khi schedule.endAt=null, filter bị short-circuit → fallback lịch cuối.
    //
    // Trong filter của attendance():
    //   s -> s.getEndAt() != null && s.getEndAt().after(now)
    //
    // Khi endAt=null:
    //   → điều kiện đầu (endAt != null) = false → short-circuit (Java không tính điều kiện sau)
    //   → tránh NullPointerException khi gọi endAt.after(now)
    //   → schedule bị loại ra khỏi filter → orElse(last) fallback sang lịch cuối
    // ----------------------------------------------------------
    @Test
    void attendance_ScheduleWithNullEndAt_ShortCircuitsFilter_FallsBackToLast() {
        Class clazz = new Class();
        clazz.setTeacher(teacher);

        // Tạo schedule đặc biệt: endAt = null
        ClassSchedule nullEndAtSchedule = new ClassSchedule();
        nullEndAtSchedule.setId(9L);
        nullEndAtSchedule.setClazz(clazz);
        nullEndAtSchedule.setStartAt(Timestamp.from(Instant.now().minusSeconds(3600)));
        nullEndAtSchedule.setEndAt(null); // ← edge case: endAt bị null
        nullEndAtSchedule.setIsAttendance(false);

        User student = new User();
        student.setId(5L);

        when(jwtUtil.getEmailFromToken(request)).thenReturn(TEACHER_EMAIL);
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(teacher));
        when(classScheduleRepository.getAvailableSchedule(any())).thenReturn(List.of(9L));
        when(classScheduleRepository.findAllById(any()))
                .thenReturn(new ArrayList<>(List.of(nullEndAtSchedule)));
        when(attendanceRepository.findByScheduleId(9L)).thenReturn(null);
        when(classScheduleRepository.findById(9L)).thenReturn(Optional.of(nullEndAtSchedule));
        when(userRepository.findById(5L)).thenReturn(Optional.of(student));

        // Service phải xử lý null endAt mà không NullPointerException → saveAll được gọi
        attendanceService.attendance(request, buildRequests(1L, 10L, 1, 5L));
        verify(attendanceRepository).saveAll(anyList());
    }
}