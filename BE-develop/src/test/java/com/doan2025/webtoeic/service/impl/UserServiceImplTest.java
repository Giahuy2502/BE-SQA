package com.doan2025.webtoeic.service.impl;

import com.doan2025.webtoeic.constants.Constants;
import com.doan2025.webtoeic.constants.enums.ERole;
import com.doan2025.webtoeic.constants.enums.ResponseCode;
import com.doan2025.webtoeic.constants.enums.ResponseObject;
import com.doan2025.webtoeic.domain.Student;
import com.doan2025.webtoeic.domain.Consultant;
import com.doan2025.webtoeic.domain.Manager;
import com.doan2025.webtoeic.domain.Teacher;
import com.doan2025.webtoeic.domain.User;
import com.doan2025.webtoeic.dto.SearchBaseDto;
import com.doan2025.webtoeic.dto.request.UserRequest;
import com.doan2025.webtoeic.dto.response.ConsultantResponse;
import com.doan2025.webtoeic.dto.response.ManagerResponse;
import com.doan2025.webtoeic.dto.response.StudentResponse;
import com.doan2025.webtoeic.dto.response.TeacherResponse;
import com.doan2025.webtoeic.dto.response.UserResponse;
import com.doan2025.webtoeic.exception.WebToeicException;
import com.doan2025.webtoeic.repository.UserRepository;
import com.doan2025.webtoeic.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.modelmapper.ModelMapper;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link UserServiceImpl} (JUnit + Mockito).
 *
 * Naming convention: MethodName_StateUnderTest_ExpectedBehavior
 */
@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private ModelMapper modelMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private UserServiceImpl userServiceImplUnderTest;

    private static User newUserWithRole(String email, String encodedPassword, ERole role) {
        User user = new User(email, encodedPassword, "First", "Last", role);
        user.setId(10L);
        user.setCode("CODE10");
        user.setIsActive(true);
        user.setIsDelete(false);
        if (role == ERole.STUDENT) {
            Student student = new Student("Uni", "CS");
            student.setId(20L);
            user.setStudent(student);
        }
        return user;
    }

    /**
     * TC-USER-001: Kiểm tra lỗi email null khi lọc danh sách người dùng
     * Mục đích: Đảm bảo hệ thống đưa ra ngoại lệ khi email token null
     * Dữ liệu đầu vào: HttpServletRequest với getEmailFromToken() trả về null
     * Kết quả kỳ vọng: Đưa ra WebToeicException với ResponseCode.NOT_EXISTED, ResponseObject.EMAIL
     */
    @Test
    void getListUserFilter_TokenEmailNull_ShouldThrowNotExistedEmail() {
        HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn(null);

        assertThatThrownBy(() -> userServiceImplUnderTest.getListUserFilter(
                httpServletRequest, new SearchBaseDto(), PageRequest.of(0, 10)))
                .isInstanceOf(WebToeicException.class)
                .extracting(e -> ((WebToeicException) e).getResponseCode(), e -> ((WebToeicException) e).getResponseObject())
                .containsExactly(ResponseCode.NOT_EXISTED, ResponseObject.EMAIL);
    }

    /**
     * TC-USER-002: Kiểm tra lỗi người dùng không tồn tại khi lọc danh sách
     * Mục đích: Xác minh hệ thống xử lý lỗi khi email từ token không tìm thấy trong database
     * Dữ liệu đầu vào: Email từ token: x@x.com, repository không tìm thấy
     * Kết quả kỳ vọng: Đưa ra WebToeicException với ResponseCode.NOT_EXISTED, ResponseObject.USER
     */
    @Test
    void getListUserFilter_UserNotFound_ShouldThrowNotExistedUser() {
        HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn("x@x.com");
        when(userRepository.findByEmail("x@x.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userServiceImplUnderTest.getListUserFilter(
                httpServletRequest, new SearchBaseDto(), PageRequest.of(0, 10)))
                .isInstanceOf(WebToeicException.class)
                .extracting(e -> ((WebToeicException) e).getResponseCode(), e -> ((WebToeicException) e).getResponseObject())
                .containsExactly(ResponseCode.NOT_EXISTED, ResponseObject.USER);
    }

    /**
     * TC-USER-003: Kiểm tra lọc danh sách người dùng với vai trò MANAGER
     * Mục đích: Xác minh người dùng MANAGER chỉ có thể xem người dùng có vai trò thấp hơn
     * Dữ liệu đầu vào: Email token: m@x.com, người dùng có vai trò MANAGER
     * Kết quả kỳ vọng: Repository được gọi với danh sách vai trò dưới MANAGER (Constants.ROLE_BELOW_MANAGER)
     */
    @Test
    void getListUserFilter_ManagerRole_ShouldQueryWithRolesBelowManager() {
        HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
        SearchBaseDto dto = new SearchBaseDto();
        Pageable pageable = PageRequest.of(0, 10);

        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn("m@x.com");
        when(userRepository.findByEmail("m@x.com"))
                .thenReturn(Optional.of(newUserWithRole("m@x.com", "encoded", ERole.MANAGER)));

        Page<UserResponse> expected = new PageImpl<>(List.of());
        when(userRepository.findListUserFilter(eq(dto), anyList(), eq(pageable))).thenReturn(expected);

        Page<UserResponse> actual = userServiceImplUnderTest.getListUserFilter(httpServletRequest, dto, pageable);

        assertThat(actual).isSameAs(expected);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ERole>> rolesCaptor = ArgumentCaptor.forClass((Class) List.class);
        verify(userRepository).findListUserFilter(eq(dto), rolesCaptor.capture(), eq(pageable));
        assertThat(rolesCaptor.getValue()).containsExactlyElementsOf(Constants.ROLE_BELOW_MANAGER);
    }

    /**
     * TC-USER-004: Kiểm tra lọc danh sách khi CONSULTANT cung cấp danh sách vai trò rỗng
     * Mục đích: Xác minh hệ thống xử lý khi dữ liệu vai trò rỗng, sẽ cập nhật thành null
     * Dữ liệu đầu vào: Email token: c@x.com, người dùng CONSULTANT, dto.userRoles = []
     * Kết quả kỳ vọng: dto.userRoles được đặt thành null, repository được gọi với vai trò dưới CONSULTANT
     */
    @Test
    void getListUserFilter_ConsultantRoleAndEmptyDtoUserRoles_ShouldSetDtoUserRolesNullAndQuery() {
        HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
        SearchBaseDto dto = new SearchBaseDto();
        dto.setUserRoles(List.of());
        Pageable pageable = PageRequest.of(0, 10);

        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn("c@x.com");
        when(userRepository.findByEmail("c@x.com"))
                .thenReturn(Optional.of(newUserWithRole("c@x.com", "encoded", ERole.CONSULTANT)));

        Page<UserResponse> expected = new PageImpl<>(List.of());
        when(userRepository.findListUserFilter(any(SearchBaseDto.class), anyList(), eq(pageable))).thenReturn(expected);

        Page<UserResponse> actual = userServiceImplUnderTest.getListUserFilter(httpServletRequest, dto, pageable);

        assertThat(dto.getUserRoles()).isNull();
        assertThat(actual).isSameAs(expected);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ERole>> rolesCaptor = ArgumentCaptor.forClass((Class) List.class);
        verify(userRepository).findListUserFilter(eq(dto), rolesCaptor.capture(), eq(pageable));
        assertThat(rolesCaptor.getValue()).containsExactlyElementsOf(Constants.ROLE_BELOW_CONSULTANT);
    }

    /**
     * TC-USER-005: Kiểm tra lọc danh sách khi cung cấp danh sách vai trò không rỗng
     * Mục đích: Xác minh hệ thống giữ nguyên danh sách vai trò khi người dùng cung cấp
     * Dữ liệu đầu vào: Email token: c@x.com, CONSULTANT, dto.userRoles = [STUDENT]
     * Kết quả kỳ vọng: dto.userRoles vẫn giữ [STUDENT], không bị thay đổi
     */
    @Test
    void getListUserFilter_DtoUserRolesNonEmpty_ShouldKeepUserRolesAndQuery() {
        HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
        SearchBaseDto dto = new SearchBaseDto();
        dto.setUserRoles(List.of("STUDENT"));
        Pageable pageable = PageRequest.of(0, 10);

        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn("c@x.com");
        when(userRepository.findByEmail("c@x.com"))
                .thenReturn(Optional.of(newUserWithRole("c@x.com", "encoded", ERole.CONSULTANT)));

        Page<UserResponse> expected = new PageImpl<>(List.of());
        when(userRepository.findListUserFilter(eq(dto), anyList(), eq(pageable))).thenReturn(expected);

        Page<UserResponse> actual = userServiceImplUnderTest.getListUserFilter(httpServletRequest, dto, pageable);

        assertThat(actual).isSameAs(expected);
        assertThat(dto.getUserRoles()).containsExactly("STUDENT");
    }

    /**
     * TC-USER-006: Kiểm tra lọc danh sách khi không có kết quả
     * Mục đích: Xác minh hệ thống xử lý chính xác khi query trả về trang rỗng
     * Dữ liệu đầu vào: Email token: t@x.com, TEACHER, không có user phù hợp trong database
     * Kết quả kỳ vọng: Trả về Page rỗng, totalElements = 0, content = []
     */
    @Test
    void getListUserFilter_NoResults_ShouldReturnEmptyPage() {
        HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
        SearchBaseDto dto = new SearchBaseDto();
        Pageable pageable = PageRequest.of(0, 10);

        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn("t@x.com");
        when(userRepository.findByEmail("t@x.com"))
                .thenReturn(Optional.of(newUserWithRole("t@x.com", "encoded", ERole.TEACHER)));

        Page<UserResponse> emptyPage = Page.empty(pageable);
        when(userRepository.findListUserFilter(eq(dto), anyList(), eq(pageable))).thenReturn(emptyPage);

        Page<UserResponse> actual = userServiceImplUnderTest.getListUserFilter(httpServletRequest, dto, pageable);

        assertThat(actual.getTotalElements()).isZero();
        assertThat(actual.getContent()).isEmpty();
    }

    /**
     * TC-USER-007: Kiểm tra truyền Pageable chính xác tới repository
     * Mục đích: Xác minh hệ thống truyền thông tin phân trang chính xác tới repository
     * Dữ liệu đầu vào: Email token: t@x.com, TEACHER, Pageable: page=0, size=10
     * Kết quả kỳ vọng: Repository.findListUserFilter() được gọi với Pageable giống nhau
     */
    @Test
    void getListUserFilter_PageableProvided_ShouldPassSamePageableToRepository() {
        HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
        SearchBaseDto dto = new SearchBaseDto();
        Pageable pageable = PageRequest.of(0, 10);

        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn("t@x.com");
        when(userRepository.findByEmail("t@x.com"))
                .thenReturn(Optional.of(newUserWithRole("t@x.com", "encoded", ERole.TEACHER)));
        when(userRepository.findListUserFilter(eq(dto), anyList(), eq(pageable))).thenReturn(Page.empty(pageable));

        userServiceImplUnderTest.getListUserFilter(httpServletRequest, dto, pageable);

        verify(userRepository).findListUserFilter(eq(dto), anyList(), eq(pageable));
    }

    /**
     * TC-USER-008: Kiểm tra lỗi email null khi lấy thông tin người dùng hiện tại
     * Mục đích: Đảm bảo hệ thống xác thực email từ token trước khi truy xuất
     * Dữ liệu đầu vào: HttpServletRequest với getEmailFromToken() trả về null
     * Kết quả kỳ vọng: Đưa ra WebToeicException với ResponseCode.NOT_EXISTED, ResponseObject.EMAIL
     */
    @Test
    void getUserCurrent_TokenEmailNull_ShouldThrowNotExistedEmail() {
        HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn(null);

        assertThatThrownBy(() -> userServiceImplUnderTest.getUserCurrent(httpServletRequest))
                .isInstanceOf(WebToeicException.class)
                .extracting(e -> ((WebToeicException) e).getResponseCode(), e -> ((WebToeicException) e).getResponseObject())
                .containsExactly(ResponseCode.NOT_EXISTED, ResponseObject.EMAIL);
    }

    /**
     * TC-USER-009: Kiểm tra lỗi người dùng không tồn tại khi lấy thông tin hiện tại
     * Mục đích: Xác minh xử lý lỗi khi email từ token không có trong database
     * Dữ liệu đầu vào: Email token: x@x.com, repository không tìm thấy
     * Kết quả kỳ vọng: Đưa ra WebToeicException với ResponseCode.NOT_EXISTED, ResponseObject.USER
     */
    @Test
    void getUserCurrent_UserNotFound_ShouldThrowNotExistedUser() {
        HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn("x@x.com");
        when(userRepository.findUser("x@x.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userServiceImplUnderTest.getUserCurrent(httpServletRequest))
                .isInstanceOf(WebToeicException.class)
                .extracting(e -> ((WebToeicException) e).getResponseCode(), e -> ((WebToeicException) e).getResponseObject())
                .containsExactly(ResponseCode.NOT_EXISTED, ResponseObject.USER);
    }

    /**
     * TC-USER-010: Kiểm tra lấy thông tin người dùng hiện tại thành công
     * Mục đích: Xác minh hệ thống trả về thông tin người dùng chính xác từ token
     * Dữ liệu đầu vào: Email token: ok@x.com, người dùng tồn tại trong database
     * Kết quả kỳ vọng: Trả về UserResponse từ repository
     */
    @Test
    void getUserCurrent_TokenValidUserExists_ShouldReturnUserResponse() {
        HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn("ok@x.com");
        UserResponse expected = mock(UserResponse.class);
        when(userRepository.findUser("ok@x.com")).thenReturn(Optional.of(expected));

        UserResponse actual = userServiceImplUnderTest.getUserCurrent(httpServletRequest);

        assertThat(actual).isSameAs(expected);
    }

    /**
     * TC-USER-011: Kiểm tra lỗi ID null khi lấy chi tiết người dùng
     * Mục đích: Đảm bảo hệ thống yêu cầu ID bắt buộc
     * Dữ liệu đầu vào: UserRequest với id = null
     * Kết quả kỳ vọng: Đưa ra WebToeicException với ResponseCode.IS_NULL, ResponseObject.ID
     */
    @Test
    void getUserDetails_IdNull_ShouldThrowIsNullId() {
        UserRequest request = UserRequest.builder().id(null).build();

        assertThatThrownBy(() -> userServiceImplUnderTest.getUserDetails(request))
                .isInstanceOf(WebToeicException.class)
                .extracting(e -> ((WebToeicException) e).getResponseCode(), e -> ((WebToeicException) e).getResponseObject())
                .containsExactly(ResponseCode.IS_NULL, ResponseObject.ID);
    }

    /**
     * TC-USER-012: Kiểm tra người dùng không tồn tại khi lấy chi tiết
     * Mục đích: Xác minh xử lý khi ID không tồn tại trong database
     * Dữ liệu đầu vào: UserRequest với id = 999L, không có trong repository
     * Kết quả kỳ vọng: Đưa ra WebToeicException với ResponseCode.NOT_EXISTED, ResponseObject.USER
     */
    @Test
    void getUserDetails_UserNotFound_ShouldThrowNotExistedUser() {
        UserRequest request = UserRequest.builder().id(999L).build();
        when(userRepository.findUserById(request)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userServiceImplUnderTest.getUserDetails(request))
                .isInstanceOf(WebToeicException.class)
                .extracting(e -> ((WebToeicException) e).getResponseCode(), e -> ((WebToeicException) e).getResponseObject())
                .containsExactly(ResponseCode.NOT_EXISTED, ResponseObject.USER);
    }

    /**
     * TC-USER-013: Kiểm tra lấy chi tiết người dùng thành công
     * Mục đích: Xác minh hệ thống trả về thông tin người dùng chính xác
     * Dữ liệu đầu vào: UserRequest với id = 10L, người dùng tồn tại
     * Kết quả kỳ vọng: Trả về UserResponse từ repository
     */
    @Test
    void getUserDetails_UserExists_ShouldReturnUserResponse() {
        UserRequest request = UserRequest.builder().id(10L).build();
        UserResponse expected = mock(UserResponse.class);
        when(userRepository.findUserById(request)).thenReturn(Optional.of(expected));

        UserResponse actual = userServiceImplUnderTest.getUserDetails(request);

        assertThat(actual).isSameAs(expected);
    }

    /**
     * TC-USER-014: Kiểm tra lỗi email null khi cập nhật chi tiết người dùng
     * Mục đích: Đảm bảo email token không null trước khi cập nhật
     * Dữ liệu đầu vào: HttpServletRequest với getEmailFromToken() trả về null
     * Kết quả kỳ vọng: Đưa ra WebToeicException với ResponseCode.NOT_EXISTED, ResponseObject.USER
     */
    @Test
    void updateUserDetails_TokenEmailNull_ShouldThrowNotExistedUser() {
        HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn(null);

        assertThatThrownBy(() -> userServiceImplUnderTest.updateUserDetails(httpServletRequest, UserRequest.builder().build()))
                .isInstanceOf(WebToeicException.class)
                .extracting(e -> ((WebToeicException) e).getResponseCode(), e -> ((WebToeicException) e).getResponseObject())
                .containsExactly(ResponseCode.NOT_EXISTED, ResponseObject.USER);
    }

    /**
     * TC-USER-015: Kiểm tra người dùng không tồn tại khi cập nhật
     * Mục đích: Xác minh xử lý lỗi khi email từ token không có trong database
     * Dữ liệu đầu vào: Email token: x@x.com, không có trong repository
     * Kết quả kỳ vọng: Đưa ra WebToeicException với ResponseCode.NOT_EXISTED, ResponseObject.USER
     */
    @Test
    void updateUserDetails_UserNotFound_ShouldThrowNotExistedUser() {
        HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn("x@x.com");
        when(userRepository.findByEmail("x@x.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userServiceImplUnderTest.updateUserDetails(httpServletRequest, UserRequest.builder().build()))
                .isInstanceOf(WebToeicException.class)
                .extracting(e -> ((WebToeicException) e).getResponseCode(), e -> ((WebToeicException) e).getResponseObject())
                .containsExactly(ResponseCode.NOT_EXISTED, ResponseObject.USER);
    }

    /**
     * TC-USER-016: Kiểm tra mật khẩu cũ không khớp khi thay đổi mật khẩu
     * Mục đích: Xác minh hệ thống xác thực mật khẩu cũ chính xác
     * Dữ liệu đầu vào: Email: s@x.com, oldPassword: wrong-old, mật khẩu cũ encode: encoded-old
     * Kết quả kỳ vọng: Đưa ra WebToeicException với ResponseCode.NOT_MATCHED, ResponseObject.PASSWORD
     */
    @Test
    void updateUserDetails_ChangePasswordOldPasswordNotMatched_ShouldThrowNotMatchedPassword() {
        HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn("s@x.com");
        User user = newUserWithRole("s@x.com", "encoded-old", ERole.STUDENT);
        when(userRepository.findByEmail("s@x.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-old", "encoded-old")).thenReturn(false);

        UserRequest request = UserRequest.builder()
                .oldPassword("wrong-old")
                .password("new-pass")
                .build();

        assertThatThrownBy(() -> userServiceImplUnderTest.updateUserDetails(httpServletRequest, request))
                .isInstanceOf(WebToeicException.class)
                .extracting(e -> ((WebToeicException) e).getResponseCode(), e -> ((WebToeicException) e).getResponseObject())
                .containsExactly(ResponseCode.NOT_MATCHED, ResponseObject.PASSWORD);
    }

    /**
     * TC-USER-017: Kiểm tra thay đổi mật khẩu thành công
     * Mục đích: Xác minh hệ thống encode mật khẩu mới và lưu chính xác
     * Dữ liệu đầu vào: Email: s@x.com, oldPassword: old (khớp), newPassword: new
     * Kết quả kỳ vọng: Mật khẩu được encode và lưu, trả về UserResponse
     */
    @Test
    void updateUserDetails_ChangePasswordOldPasswordMatched_ShouldEncodeSaveAndMap() {
        HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn("s@x.com");
        User user = newUserWithRole("s@x.com", "encoded-old", ERole.STUDENT);
        when(userRepository.findByEmail("s@x.com")).thenReturn(Optional.of(user));

        when(passwordEncoder.matches("old", "encoded-old")).thenReturn(true);
        when(passwordEncoder.encode("new")).thenReturn("encoded-new");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0, User.class));

        UserResponse mapped = mock(UserResponse.class);
        when(modelMapper.map(any(User.class), eq(UserResponse.class))).thenReturn(mapped);

        UserRequest request = UserRequest.builder()
                .oldPassword("old")
                .password("new")
                .build();

        UserResponse actual = userServiceImplUnderTest.updateUserDetails(httpServletRequest, request);

        assertThat(actual).isSameAs(mapped);
        assertThat(user.getPassword()).isEqualTo("encoded-new");
        verify(userRepository).save(user);
        verify(modelMapper).map(user, UserResponse.class);
    }

    /**
     * TC-USER-018: Kiểm tra cập nhật thông tin với tất cả field null
     * Mục đích: Xác minh hệ thống vẫn lưu thành công ngay cả khi không cập nhật gì
     * Dữ liệu đầu vào: Email: s@x.com, UserRequest với tất cả field null
     * Kết quả kỳ vọng: User được lưu, trả về UserResponse đã map
     */
    @Test
    void updateUserDetails_UpdateInfoAllNullFields_ShouldStillSaveAndReturnMappedResponse() {
        HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn("s@x.com");

        User user = newUserWithRole("s@x.com", "encoded", ERole.STUDENT);
        user.setStudent(new Student("OldEdu", "OldMajor"));
        when(userRepository.findByEmail("s@x.com")).thenReturn(Optional.of(user));

        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0, User.class));

        UserResponse baseResponse = new UserResponse();
        when(modelMapper.map(any(User.class), eq(UserResponse.class))).thenReturn(baseResponse);

        StudentResponse studentResponse = new StudentResponse();
        when(modelMapper.map(any(Student.class), eq(StudentResponse.class))).thenReturn(studentResponse);

        UserRequest request = UserRequest.builder().build();

        UserResponse actual = userServiceImplUnderTest.updateUserDetails(httpServletRequest, request);

        assertThat(actual).isSameAs(baseResponse);
        assertThat(actual.getStudent()).isSameAs(studentResponse);
        verify(userRepository).save(user);
        verify(modelMapper).map(user, UserResponse.class);
        verify(modelMapper).map(user.getStudent(), StudentResponse.class);
    }

    /**
     * TC-USER-019: Kiểm tra cập nhật mật khẩu mới nhưng mật khẩu cũ null
     * Mục đích: Xác minh hệ thống xử lý khi chỉ cung cấp mật khẩu mới
     * Dữ liệu đầu vào: Email: s@x.com, password: new-pass, oldPassword: null
     * Kết quả kỳ vọng: Không kiểm tra mật khẩu cũ, cập nhật thông tin bình thường
     */
    @Test
    void updateUserDetails_PasswordProvidedOldPasswordNull_ShouldTreatAsUpdateInfoAndNotMatchOldPassword() {
        HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn("s@x.com");

        User user = newUserWithRole("s@x.com", "encoded", ERole.STUDENT);
        user.setStudent(new Student("OldEdu", "OldMajor"));
        when(userRepository.findByEmail("s@x.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0, User.class));

        UserResponse baseResponse = new UserResponse();
        when(modelMapper.map(any(User.class), eq(UserResponse.class))).thenReturn(baseResponse);
        StudentResponse studentResponse = new StudentResponse();
        when(modelMapper.map(any(Student.class), eq(StudentResponse.class))).thenReturn(studentResponse);

        UserRequest request = UserRequest.builder()
                .password("new-pass")
                .oldPassword(null)
                .build();

        UserResponse actual = userServiceImplUnderTest.updateUserDetails(httpServletRequest, request);

        assertThat(actual).isSameAs(baseResponse);
        verify(passwordEncoder, never()).matches(any(), any());
    }

    /**
     * TC-USER-020: Kiểm tra cập nhật mật khẩu cũ nhưng mật khẩu mới null
     * Mục đích: Xác minh hệ thống xử lý khi chỉ cung cấp mật khẩu cũ
     * Dữ liệu đầu vào: Email: s@x.com, oldPassword: old-pass, password: null
     * Kết quả kỳ vọng: Không kiểm tra mật khẩu cũ, cập nhật thông tin bình thường
     */
    @Test
    void updateUserDetails_OldPasswordProvidedPasswordNull_ShouldTreatAsUpdateInfoAndNotMatchOldPassword() {
        HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn("s@x.com");

        User user = newUserWithRole("s@x.com", "encoded", ERole.STUDENT);
        user.setStudent(new Student("OldEdu", "OldMajor"));
        when(userRepository.findByEmail("s@x.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0, User.class));

        UserResponse baseResponse = new UserResponse();
        when(modelMapper.map(any(User.class), eq(UserResponse.class))).thenReturn(baseResponse);
        StudentResponse studentResponse = new StudentResponse();
        when(modelMapper.map(any(Student.class), eq(StudentResponse.class))).thenReturn(studentResponse);

        UserRequest request = UserRequest.builder()
                .oldPassword("old-pass")
                .password(null)
                .build();

        UserResponse actual = userServiceImplUnderTest.updateUserDetails(httpServletRequest, request);

        assertThat(actual).isSameAs(baseResponse);
        verify(passwordEncoder, never()).matches(any(), any());
    }

    /**
     * TC-USER-021: Kiểm tra cập nhật thông tin với vai trò MANAGER
     * Mục đích: Xác minh hệ thống lỊpManagerResponse vào UserResponse
     * Dữ liệu đầu vào: Email: m@x.com, vai trò MANAGER, có Manager entity
     * Kết quả kỳ vọng: UserResponse có manager field được map
     */
    @Test
    void updateUserDetails_UpdateInfoRoleManager_ShouldSetManagerResponseOnUserResponse() {
        HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn("m@x.com");

        User managerUser = newUserWithRole("m@x.com", "encoded", ERole.MANAGER);
        Manager manager = new Manager();
        manager.setId(100L);
        managerUser.setManager(manager);

        when(userRepository.findByEmail("m@x.com")).thenReturn(Optional.of(managerUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0, User.class));

        UserResponse baseResponse = new UserResponse();
        when(modelMapper.map(any(User.class), eq(UserResponse.class))).thenReturn(baseResponse);

        ManagerResponse managerResponse = new ManagerResponse();
        managerResponse.setId(100L);
        when(modelMapper.map(any(Manager.class), eq(ManagerResponse.class))).thenReturn(managerResponse);

        UserResponse actual = userServiceImplUnderTest.updateUserDetails(httpServletRequest, UserRequest.builder().build());

        assertThat(actual).isSameAs(baseResponse);
        assertThat(actual.getManager()).isSameAs(managerResponse);
        verify(modelMapper).map(manager, ManagerResponse.class);
    }

    /**
     * TC-USER-022: Kiểm tra cập nhật thông tin với vai trò CONSULTANT
     * Mục đích: Xác minh hệ thống lỊpConsultantResponse vào UserResponse
     * Dữ liệu đầu vào: Email: c@x.com, vai trò CONSULTANT, có Consultant entity
     * Kết quả kỳ vọng: UserResponse có consultant field được map
     */
    @Test
    void updateUserDetails_UpdateInfoRoleConsultant_ShouldSetConsultantResponseOnUserResponse() {
        HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn("c@x.com");

        User consultantUser = newUserWithRole("c@x.com", "encoded", ERole.CONSULTANT);
        Consultant consultant = new Consultant();
        consultant.setId(200L);
        consultantUser.setConsultant(consultant);

        when(userRepository.findByEmail("c@x.com")).thenReturn(Optional.of(consultantUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0, User.class));

        UserResponse baseResponse = new UserResponse();
        when(modelMapper.map(any(User.class), eq(UserResponse.class))).thenReturn(baseResponse);

        ConsultantResponse consultantResponse = new ConsultantResponse();
        consultantResponse.setId(200L);
        when(modelMapper.map(any(Consultant.class), eq(ConsultantResponse.class))).thenReturn(consultantResponse);

        UserResponse actual = userServiceImplUnderTest.updateUserDetails(httpServletRequest, UserRequest.builder().build());

        assertThat(actual).isSameAs(baseResponse);
        assertThat(actual.getConsultant()).isSameAs(consultantResponse);
        verify(modelMapper).map(consultant, ConsultantResponse.class);
    }

    /**
     * TC-USER-023: Kiểm tra cập nhật thông tin với vai trò TEACHER
     * Mục đích: Xác minh hệ thống lỊpTeacherResponse vào UserResponse
     * Dữ liệu đầu vào: Email: t@x.com, vai trò TEACHER, có Teacher entity
     * Kết quả kỳ vọng: UserResponse có teacher field được map
     */
    @Test
    void updateUserDetails_UpdateInfoRoleTeacher_ShouldSetTeacherResponseOnUserResponse() {
        HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn("t@x.com");

        User teacherUser = newUserWithRole("t@x.com", "encoded", ERole.TEACHER);
        Teacher teacher = new Teacher("Edu", "Degree");
        teacher.setId(300L);
        teacherUser.setTeacher(teacher);

        when(userRepository.findByEmail("t@x.com")).thenReturn(Optional.of(teacherUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0, User.class));

        UserResponse baseResponse = new UserResponse();
        when(modelMapper.map(any(User.class), eq(UserResponse.class))).thenReturn(baseResponse);

        TeacherResponse teacherResponse = new TeacherResponse();
        teacherResponse.setId(300L);
        when(modelMapper.map(any(Teacher.class), eq(TeacherResponse.class))).thenReturn(teacherResponse);

        UserResponse actual = userServiceImplUnderTest.updateUserDetails(httpServletRequest, UserRequest.builder().build());

        assertThat(actual).isSameAs(baseResponse);
        assertThat(actual.getTeacher()).isSameAs(teacherResponse);
        verify(modelMapper).map(teacher, TeacherResponse.class);
    }

    /**
     * TC-USER-024: Kiểm tra lỗi người dùng không tồn tại khi xoá hoặc vô hiệu hóa
     * Mục đích: Xác minh xử lý khi ID không tồn tại trong database
     * Dữ liệu đầu vào: UserRequest với id = 99L, không có trong repository
     * Kết quả kỳ vọng: Đưa ra WebToeicException với ResponseCode.NOT_EXISTED, ResponseObject.USER
     */
    @Test
    void deleteOrDisableUser_UserNotFound_ShouldThrowNotExistedUser() {
        UserRequest request = UserRequest.builder().id(99L).isActive(false).build();
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userServiceImplUnderTest.deleteOrDisableUser(request))
                .isInstanceOf(WebToeicException.class)
                .extracting(e -> ((WebToeicException) e).getResponseCode(), e -> ((WebToeicException) e).getResponseObject())
                .containsExactly(ResponseCode.NOT_EXISTED, ResponseObject.USER);
    }

    /**
     * TC-USER-025: Kiểm tra cập nhật cở isActive và isDelete
     * Mục đích: Xác minh hệ thống cập nhật đúng giá trị trạng thái
     * Dữ liệu đầu vào: UserRequest với id = 10L, isActive = false, isDelete = true
     * Kết quả kỳ vọng: User.isActive = false, User.isDelete = true, lưu và map thành công
     */
    @Test
    void deleteOrDisableUser_RequestHasDifferentFlags_ShouldUpdateFlagsSaveAndMap() {
        User existing = newUserWithRole("u@x.com", "p", ERole.STUDENT);
        existing.setIsActive(true);
        existing.setIsDelete(false);

        when(userRepository.findById(10L)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0, User.class));

        UserResponse mapped = new UserResponse();
        when(modelMapper.map(any(User.class), eq(UserResponse.class))).thenReturn(mapped);

        UserRequest request = UserRequest.builder()
                .id(10L)
                .isActive(false)
                .isDelete(true)
                .build();

        UserResponse actual = userServiceImplUnderTest.deleteOrDisableUser(request);

        assertThat(actual).isSameAs(mapped);
        assertThat(existing.getIsActive()).isFalse();
        assertThat(existing.getIsDelete()).isTrue();
        verify(userRepository).save(existing);
        verify(modelMapper).map(existing, UserResponse.class);
    }

    /**
     * TC-USER-026: Kiểm tra xoá/vô hiệu hóa với flag null
     * Mục đích: Xác minh hệ thống không thay đổi flag khi null
     * Dữ liệu đầu vào: UserRequest với isActive = null, isDelete = null
     * Kết quả kỳ vọng: Cấc flag giữ nguyên giá trị cũ, vẫn lưu thành công
     */
    @Test
    void deleteOrDisableUser_RequestFlagsNull_ShouldNotChangeFlagsButStillSaveAndMap() {
        User existing = newUserWithRole("u@x.com", "p", ERole.STUDENT);
        existing.setIsActive(true);
        existing.setIsDelete(false);

        when(userRepository.findById(10L)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0, User.class));

        UserResponse mapped = new UserResponse();
        when(modelMapper.map(any(User.class), eq(UserResponse.class))).thenReturn(mapped);

        UserRequest request = UserRequest.builder()
                .id(10L)
                .isActive(null)
                .isDelete(null)
                .build();

        UserResponse actual = userServiceImplUnderTest.deleteOrDisableUser(request);

        assertThat(actual).isSameAs(mapped);
        assertThat(existing.getIsActive()).isTrue();
        assertThat(existing.getIsDelete()).isFalse();
        verify(userRepository).save(existing);
        verify(modelMapper).map(existing, UserResponse.class);
    }

    /**
     * TC-USER-027: Kiểm tra xoá/vô hiệu hóa với flag giờng hiện tại
     * Mục đích: Xác minh hệ thống xử lý khi flag đã giờng
     * Dữ liệu đầu vào: UserRequest với isActive = true, isDelete = false (giờng hiện tại)
     * Kết quả kỳ vọng: Flag không được thay đổi, vẫn lưu và trả về UserResponse
     */
    @Test
    void deleteOrDisableUser_RequestFlagsSameAsExisting_ShouldNotChangeFlagsButStillSaveAndMap() {
        User existing = newUserWithRole("u@x.com", "p", ERole.STUDENT);
        existing.setIsActive(true);
        existing.setIsDelete(false);

        when(userRepository.findById(10L)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0, User.class));

        UserResponse mapped = new UserResponse();
        when(modelMapper.map(any(User.class), eq(UserResponse.class))).thenReturn(mapped);

        UserRequest request = UserRequest.builder()
                .id(10L)
                .isActive(true)
                .isDelete(false)
                .build();

        UserResponse actual = userServiceImplUnderTest.deleteOrDisableUser(request);

        assertThat(actual).isSameAs(mapped);
        assertThat(existing.getIsActive()).isTrue();
        assertThat(existing.getIsDelete()).isFalse();
        verify(userRepository).save(existing);
        verify(modelMapper).map(existing, UserResponse.class);
    }

    /**
     * TC-USER-028: Kiểm tra lỗi token null khi đặt lại mật khẩu
     * Mục đích: Đảm bảo token bắt buộc khi đặt lại mật khẩu
     * Dữ liệu đầu vào: UserRequest với token = null
     * Kết quả kỳ vọng: Đưa ra WebToeicException với ResponseCode.IS_NULL, ResponseObject.TOKEN
     */
    @Test
    void resetPassword_TokenNull_ShouldThrowIsNullToken() {
        UserRequest request = UserRequest.builder().token(null).build();

        assertThatThrownBy(() -> userServiceImplUnderTest.resetPassword(request))
                .isInstanceOf(WebToeicException.class)
                .extracting(e -> ((WebToeicException) e).getResponseCode(), e -> ((WebToeicException) e).getResponseObject())
                .containsExactly(ResponseCode.IS_NULL, ResponseObject.TOKEN);
    }

    /**
     * TC-USER-029: Kiểm tra lỗi email null trong token khi đặt lại mật khẩu
     * Mục đích: Xác minh hệ thống xác thực email từ token
     * Dữ liệu đầu vào: Token hợp lệ nhưng getEmailFromTokenString() trả về null
     * Kết quả kỳ vọng: Đưa ra WebToeicException với ResponseCode.NOT_EXISTED, ResponseObject.EMAIL
     */
    @Test
    void resetPassword_TokenEmailNull_ShouldThrowNotExistedEmail() {
        when(jwtUtil.getEmailFromTokenString("t")).thenReturn(null);
        UserRequest request = UserRequest.builder().token("t").password("new").build();

        assertThatThrownBy(() -> userServiceImplUnderTest.resetPassword(request))
                .isInstanceOf(WebToeicException.class)
                .extracting(e -> ((WebToeicException) e).getResponseCode(), e -> ((WebToeicException) e).getResponseObject())
                .containsExactly(ResponseCode.NOT_EXISTED, ResponseObject.EMAIL);
    }

    /**
     * TC-USER-030: Kiểm tra lỗi người dùng không tồn tại khi đặt lại mật khẩu
     * Mục đích: Xác minh xử lý khi email từ token không có trong database
     * Dữ liệu đầu vào: Token với email: x@x.com, không có trong repository
     * Kết quả kỳ vọng: Đưa ra WebToeicException với ResponseCode.NOT_EXISTED, ResponseObject.USER
     */
    @Test
    void resetPassword_UserNotFound_ShouldThrowNotExistedUser() {
        when(jwtUtil.getEmailFromTokenString("t")).thenReturn("x@x.com");
        when(userRepository.findByEmail("x@x.com")).thenReturn(Optional.empty());

        UserRequest request = UserRequest.builder().token("t").password("new").build();

        assertThatThrownBy(() -> userServiceImplUnderTest.resetPassword(request))
                .isInstanceOf(WebToeicException.class)
                .extracting(e -> ((WebToeicException) e).getResponseCode(), e -> ((WebToeicException) e).getResponseObject())
                .containsExactly(ResponseCode.NOT_EXISTED, ResponseObject.USER);
    }

    /**
     * TC-USER-031: Kiểm tra người dùng bị khóa và xoá khi đặt lại mật khẩu
     * Mục đích: Đảm bảo người dùng xoá/khóa không thể đặt lại mật khẩu
     * Dữ liệu đầu vào: Token với email: x@x.com, người dùng có isActive=false, isDelete=true
     * Kết quả kỳ vọng: Đưa ra WebToeicException với ResponseCode.NOT_AVAILABLE, ResponseObject.USER
     */
    @Test
    void resetPassword_UserInactiveAndDeleted_ShouldThrowNotAvailableUser() {
        when(jwtUtil.getEmailFromTokenString("t")).thenReturn("x@x.com");
        User user = newUserWithRole("x@x.com", "p", ERole.STUDENT);
        user.setIsActive(false);
        user.setIsDelete(true);
        when(userRepository.findByEmail("x@x.com")).thenReturn(Optional.of(user));

        UserRequest request = UserRequest.builder().token("t").password("new").build();

        assertThatThrownBy(() -> userServiceImplUnderTest.resetPassword(request))
                .isInstanceOf(WebToeicException.class)
                .extracting(e -> ((WebToeicException) e).getResponseCode(), e -> ((WebToeicException) e).getResponseObject())
                .containsExactly(ResponseCode.NOT_AVAILABLE, ResponseObject.USER);
    }

    /**
     * TC-USER-032: Kiểm tra đặt lại mật khẩu cho người dùng bị khóa (không xoá)
     * Mục đích: Cho phép người dùng bị khóa nhưng chưa xoá đặt lại mật khẩu
     * Dữ liệu đầu vào: Token với email: x@x.com, người dùng có isActive=false, isDelete=false
     * Kết quả kỳ vọng: Mật khẩu được encode và lưu
     */
    @Test
    void resetPassword_UserInactiveButNotDeleted_ShouldEncodeAndSave() {
        when(jwtUtil.getEmailFromTokenString("t")).thenReturn("x@x.com");
        User user = newUserWithRole("x@x.com", "old", ERole.STUDENT);
        user.setIsActive(false);
        user.setIsDelete(false);
        when(userRepository.findByEmail("x@x.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("new-pass")).thenReturn("encoded-new-pass");

        UserRequest request = UserRequest.builder().token("t").password("new-pass").build();

        userServiceImplUnderTest.resetPassword(request);

        assertThat(user.getPassword()).isEqualTo("encoded-new-pass");
        verify(userRepository).save(user);
    }

    /**
     * TC-USER-033: Kiểm tra đặt lại mật khẩu thành công
     * Mục đích: Xác minh hệ thống encode mật khẩu mới và lưu chính xác
     * Dữ liệu đầu vào: Token với email: x@x.com, password: new-pass, người dùng hoạt động
     * Kết quả kỳ vọng: Mật khẩu được encode thành encoded-new-pass và lưu
     */
    @Test
    void resetPassword_TokenValidPasswordProvided_ShouldEncodeAndSave() {
        when(jwtUtil.getEmailFromTokenString("t")).thenReturn("x@x.com");
        User user = newUserWithRole("x@x.com", "old", ERole.STUDENT);
        user.setIsActive(true);
        user.setIsDelete(false);
        when(userRepository.findByEmail("x@x.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("new-pass")).thenReturn("encoded-new-pass");

        UserRequest request = UserRequest.builder().token("t").password("new-pass").build();

        userServiceImplUnderTest.resetPassword(request);

        assertThat(user.getPassword()).isEqualTo("encoded-new-pass");
        verify(userRepository).save(user);
    }

    /**
     * TC-USER-034: Kiểm tra lỗi mật khẩu null khi đặt lại
     * Mục đích: Xác minh hệ thống xác thực mật khẩu không null
     * Dữ liệu đầu vào: Token hợp lệ, password: null, passwordEncoder.encode(null) đưa ra ngoại lệ
     * Kết quả kỳ vọng: Đưa ra IllegalArgumentException, không lưu user
     */
    @Test
    void resetPassword_PasswordNull_ShouldPropagatePasswordEncoderException() {
        when(jwtUtil.getEmailFromTokenString("t")).thenReturn("x@x.com");
        User user = newUserWithRole("x@x.com", "old", ERole.STUDENT);
        when(userRepository.findByEmail("x@x.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode(isNull())).thenThrow(new IllegalArgumentException("rawPassword cannot be null"));

        UserRequest request = UserRequest.builder().token("t").password(null).build();

        assertThatThrownBy(() -> userServiceImplUnderTest.resetPassword(request))
                .isInstanceOf(IllegalArgumentException.class);
        verify(userRepository, never()).save(any());
    }


    /**
     * TC-USER-035: Kiem tra updateUserDetails() phai trim firstName co khoang trang
     * Muc dich: Xac minh he thong tuan thu business logic: firstName phai duoc trim truoc khi luu vao database.
     * Du lieu dau vao: UserRequest voi firstName = " NewFirst " (co khoang trang dau/cuoi)
     * Ket qua ky vong: user.firstName = "NewFirst" (da trim)
     */
    @Test
    void updateUserDetails_FirstNameWithSpaces_ShouldSaveTrimmedFirstName() {
        HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn("s@x.com");

        User user = newUserWithRole("s@x.com", "encoded", ERole.STUDENT);
        user.setStudent(new Student("OldEdu", "OldMajor"));
        when(userRepository.findByEmail("s@x.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0, User.class));

        UserResponse baseResponse = new UserResponse();
        when(modelMapper.map(any(User.class), eq(UserResponse.class))).thenReturn(baseResponse);
        StudentResponse studentResponse = new StudentResponse();
        when(modelMapper.map(any(Student.class), eq(StudentResponse.class))).thenReturn(studentResponse);

        UserRequest request = UserRequest.builder()
                .firstName(" NewFirst ")
                .build();

        userServiceImplUnderTest.updateUserDetails(httpServletRequest, request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getFirstName())
                .as("Business logic: firstName phai duoc trim")
                .isEqualTo("NewFirst");
    }

    /**
     * TC-USER-036: Kiem tra updateUserDetails() phai validate phone du 10 so
     * Muc dich: Xac minh he thong validate dinh dang phone.
     *           Theo business logic (CSV QT), phone phai co du 10 chu so va chi chua so.
     * Du lieu dau vao: UserRequest voi phone = "123" (chi 3 so, khong hop le)
     * Ket qua ky vong: Nem WebToeicException INVALID ve phone
     */
    @Test
    void updateUserDetails_PhoneWithLessThan10Digits_ShouldRejectInvalidPhone() {
        HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn("s@x.com");

        User user = newUserWithRole("s@x.com", "encoded", ERole.STUDENT);
        user.setStudent(new Student("OldEdu", "OldMajor"));
        when(userRepository.findByEmail("s@x.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0, User.class));

        UserResponse baseResponse = new UserResponse();
        when(modelMapper.map(any(User.class), eq(UserResponse.class))).thenReturn(baseResponse);
        StudentResponse studentResponse = new StudentResponse();
        when(modelMapper.map(any(Student.class), eq(StudentResponse.class))).thenReturn(studentResponse);

        UserRequest request = UserRequest.builder()
                .phone("123")
                .build();

        // Business logic dung: nen nem exception vi phone khong du 10 so
        assertThatThrownBy(() -> userServiceImplUnderTest.updateUserDetails(httpServletRequest, request))
                .isInstanceOf(WebToeicException.class)
                .extracting("responseCode")
                .isEqualTo(ResponseCode.INVALID);
    }

    /**
     * TC-USER-037: Kiem tra getListUserFilter() voi STUDENT role dung ROLE_BELOW_CONSULTANT
     * Muc dich: Xac minh code path khi caller co role STUDENT.
     *           UserServiceImpl: if(MANAGER) -> ROLE_BELOW_MANAGER, else -> ROLE_BELOW_CONSULTANT.
     *           STUDENT (khong phai MANAGER) -> roi vao else -> dung ROLE_BELOW_CONSULTANT.
     * Du lieu dau vao: Email token: s@x.com, nguoi dung co vai tro STUDENT
     * Ket qua ky vong: repository duoc goi voi ROLE_BELOW_CONSULTANT
     */
    @Test
    void getListUserFilter_StudentRole_ShouldQueryWithRolesBelowConsultant() {
        HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
        SearchBaseDto dto = new SearchBaseDto();
        Pageable pageable = PageRequest.of(0, 10);

        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn("s@x.com");
        when(userRepository.findByEmail("s@x.com"))
                .thenReturn(Optional.of(newUserWithRole("s@x.com", "encoded", ERole.STUDENT)));

        Page<UserResponse> expected = new PageImpl<>(List.of());
        when(userRepository.findListUserFilter(eq(dto), anyList(), eq(pageable))).thenReturn(expected);

        userServiceImplUnderTest.getListUserFilter(httpServletRequest, dto, pageable);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ERole>> rolesCaptor = ArgumentCaptor.forClass((Class) List.class);
        verify(userRepository).findListUserFilter(eq(dto), rolesCaptor.capture(), eq(pageable));
        // STUDENT khong phai MANAGER -> code vao else branch -> dung ROLE_BELOW_CONSULTANT
        assertThat(rolesCaptor.getValue()).containsExactlyElementsOf(Constants.ROLE_BELOW_CONSULTANT);
    }

    /**
     * TC-USER-038: Kiem tra deleteOrDisableUser() voi id null trong request
     * Muc dich: Xac minh behavior khi request.getId() = null.
     *           Code goi findById(null) -> JPA nem IllegalArgumentException.
     *           Business logic dung: nen kiem tra null va nem WebToeicException IS_NULL truoc.
     * Du lieu dau vao: UserRequest voi id = null
     * Ket qua ky vong (business logic dung): Nem WebToeicException IS_NULL ve ID
     * Ket qua thuc te: Repository nem IllegalArgumentException (chua co null check)
     */
    @Test
    void deleteOrDisableUser_IdNull_ShouldThrowException() {
        UserRequest request = UserRequest.builder().id(null).isActive(false).build();
        when(userRepository.findById(null))
                .thenThrow(new IllegalArgumentException("The given id must not be null"));

        assertThatThrownBy(() -> userServiceImplUnderTest.deleteOrDisableUser(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be null");
        // TODO: Fix dung la them null check: if(id == null) throw new WebToeicException(IS_NULL, ID)
    }

    /**
     * TC-USER-039: Kiem tra updateUserDetails() khi firstName = "" (chuoi rong) khong duoc ghi de
     * Muc dich: Xac minh FieldUpdateUtil.updateIfNeeded() chi cap nhat chuoi co noi dung.
     *           Business logic dung: firstName rong nen duoc coi la khong thay doi (giu nguyen).
     * Du lieu dau vao: UserRequest voi firstName = "", user hien co firstName = "First"
     * Ket qua ky vong: user.firstName giu nguyen = "First"
     */
    @Test
    void updateUserDetails_FirstNameEmptyString_BusinessLogicShouldNotOverwriteWithEmpty() {
        HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn("s@x.com");

        User user = newUserWithRole("s@x.com", "encoded", ERole.STUDENT);
        user.setStudent(new Student("OldEdu", "OldMajor"));
        when(userRepository.findByEmail("s@x.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0, User.class));

        UserResponse baseResponse = new UserResponse();
        when(modelMapper.map(any(User.class), eq(UserResponse.class))).thenReturn(baseResponse);
        StudentResponse studentResponse = new StudentResponse();
        when(modelMapper.map(any(Student.class), eq(StudentResponse.class))).thenReturn(studentResponse);

        UserRequest request = UserRequest.builder()
                .firstName("")
                .build();

        userServiceImplUnderTest.updateUserDetails(httpServletRequest, request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        // Business logic dung: firstName empty string khong duoc ghi de firstName hien tai
        // Neu FieldUpdateUtil chi check != null -> "" duoc luu -> BUG
        assertThat(userCaptor.getValue().getFirstName())
                .as("Business logic: empty string khong duoc ghi de firstName hien tai")
                .isEqualTo("First");
    }



    /**
     * TC-USER-040: Kiem tra updateUserDetails() phai trim lastName co khoang trang
     * Muc dich: Xac minh he thong tuan thu business logic: lastName phai duoc trim truoc khi luu.
     * Du lieu dau vao: UserRequest voi lastName = " NewLast " (co khoang trang dau/cuoi)
     * Ket qua ky vong: user.lastName = "NewLast" (da trim)
     */
    @Test
    void updateUserDetails_LastNameWithSpaces_ShouldSaveTrimmedLastName() {
        HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn("s@x.com");

        User user = newUserWithRole("s@x.com", "encoded", ERole.STUDENT);
        user.setStudent(new Student("OldEdu", "OldMajor"));
        when(userRepository.findByEmail("s@x.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0, User.class));

        UserResponse baseResponse = new UserResponse();
        when(modelMapper.map(any(User.class), eq(UserResponse.class))).thenReturn(baseResponse);
        StudentResponse studentResponse = new StudentResponse();
        when(modelMapper.map(any(Student.class), eq(StudentResponse.class))).thenReturn(studentResponse);

        UserRequest request = UserRequest.builder()
                .lastName(" NewLast ")
                .build();

        userServiceImplUnderTest.updateUserDetails(httpServletRequest, request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        assertThat(userCaptor.getValue().getLastName())
                .as("Business logic: lastName phai duoc trim")
                .isEqualTo("NewLast");
    }

    /**
     * TC-USER-041: Kiem tra updateUserDetails() khi lastName = "" (chuoi rong) khong duoc ghi de
     * Muc dich: Xac minh FieldUpdateUtil.updateIfNeeded() chi cap nhat chuoi co noi dung.
     *           Business logic dung: lastName rong nen duoc coi la khong thay doi (giu nguyen).
     * Du lieu dau vao: UserRequest voi lastName = "", user hien co lastName = "Last"
     * Ket qua ky vong: user.lastName giu nguyen = "Last"
     */
    @Test
    void updateUserDetails_LastNameEmptyString_BusinessLogicShouldNotOverwriteWithEmpty() {
        HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn("s@x.com");

        User user = newUserWithRole("s@x.com", "encoded", ERole.STUDENT);
        user.setStudent(new Student("OldEdu", "OldMajor"));
        when(userRepository.findByEmail("s@x.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0, User.class));

        UserResponse baseResponse = new UserResponse();
        when(modelMapper.map(any(User.class), eq(UserResponse.class))).thenReturn(baseResponse);
        StudentResponse studentResponse = new StudentResponse();
        when(modelMapper.map(any(Student.class), eq(StudentResponse.class))).thenReturn(studentResponse);

        UserRequest request = UserRequest.builder()
                .lastName("")
                .build();

        userServiceImplUnderTest.updateUserDetails(httpServletRequest, request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        assertThat(userCaptor.getValue().getLastName())
                .as("Business logic: empty string khong duoc ghi de lastName hien tai")
                .isEqualTo("Last");
    }


    /**
     * TC-USER-042: Kiem tra updateUserDetails() phai bao loi neu Ngay Sinh (dob) o tuong lai
     * Muc dich: Xac minh he thong kiem tra tinh hop le cua ngay sinh (khong duoc lon hon ngay hien tai).
     * Du lieu dau vao: UserRequest voi dob la 100 ngay trong tuong lai
     * Ket qua ky vong: Nem WebToeicException INVALID ve ngay sinh
     */
    @Test
    void updateUserDetails_DobInFuture_ShouldRejectInvalidDob() {
        HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn("s@x.com");

        User user = newUserWithRole("s@x.com", "encoded", ERole.STUDENT);
        user.setStudent(new Student("OldEdu", "OldMajor"));
        when(userRepository.findByEmail("s@x.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0, User.class));

        UserResponse baseResponse = new UserResponse();
        when(modelMapper.map(any(User.class), eq(UserResponse.class))).thenReturn(baseResponse);
        StudentResponse studentResponse = new StudentResponse();
        when(modelMapper.map(any(Student.class), eq(StudentResponse.class))).thenReturn(studentResponse);

        UserRequest request = UserRequest.builder()
                .dob("2030-01-01")
                .build();

        assertThatThrownBy(() -> userServiceImplUnderTest.updateUserDetails(httpServletRequest, request))
                .isInstanceOf(WebToeicException.class)
                .extracting("responseCode")
                .isEqualTo(ResponseCode.INVALID);
    }

    /**
     * TC-USER-043: Kiem tra updateUserDetails() phai reject phone chua chu cai hoac ky tu dac biet
     * Muc dich: Xac minh he thong validate phone chi chua ky tu so va du 10 so.
     * Du lieu dau vao: UserRequest voi phone = "abcdefghij" va "@#$%^&*()!"
     * Ket qua ky vong: Nem WebToeicException INVALID ve phone
     */
    @Test
    void updateUserDetails_PhoneWithAlphabetsAndSpecialChars_ShouldRejectInvalidPhone() {
        HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn("s@x.com");

        User user = newUserWithRole("s@x.com", "encoded", ERole.STUDENT);
        user.setStudent(new Student("OldEdu", "OldMajor"));
        when(userRepository.findByEmail("s@x.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0, User.class));

        UserResponse baseResponse = new UserResponse();
        when(modelMapper.map(any(User.class), eq(UserResponse.class))).thenReturn(baseResponse);
        StudentResponse studentResponse = new StudentResponse();
        when(modelMapper.map(any(Student.class), eq(StudentResponse.class))).thenReturn(studentResponse);

        UserRequest request1 = UserRequest.builder().phone("abcdefghij").build();
        UserRequest request2 = UserRequest.builder().phone("@#$%^&*()!").build();

        assertThatThrownBy(() -> userServiceImplUnderTest.updateUserDetails(httpServletRequest, request1))
                .isInstanceOf(WebToeicException.class)
                .extracting("responseCode")
                .isEqualTo(ResponseCode.INVALID);
                
        assertThatThrownBy(() -> userServiceImplUnderTest.updateUserDetails(httpServletRequest, request2))
                .isInstanceOf(WebToeicException.class)
                .extracting("responseCode")
                .isEqualTo(ResponseCode.INVALID);
    }

    /**
     * TC-USER-044: Kiem tra getListUserFilter() phai trim keyword truoc khi tim kiem
     * Muc dich: Xac minh he thong tu dong trim khoang trang o keyword de tim kiem chinh xac.
     * Du lieu dau vao: SearchBaseDto voi keyword = "  consultant1@gmail.com  "
     * Ket qua ky vong: Repository phai duoc goi voi dto co keyword = "consultant1@gmail.com" (da trim)
     */
    @Test
    void getListUserFilter_KeywordWithSpaces_ShouldTrimKeywordBeforeSearch() {
        HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
        SearchBaseDto dto = new SearchBaseDto();
        dto.setSearchString("  consultant1@gmail.com  ");
        Pageable pageable = PageRequest.of(0, 10);

        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn("m@x.com");
        when(userRepository.findByEmail("m@x.com"))
                .thenReturn(Optional.of(newUserWithRole("m@x.com", "encoded", ERole.MANAGER)));

        Page<UserResponse> expected = new PageImpl<>(List.of());
        when(userRepository.findListUserFilter(any(SearchBaseDto.class), anyList(), eq(pageable))).thenReturn(expected);

        userServiceImplUnderTest.getListUserFilter(httpServletRequest, dto, pageable);

        ArgumentCaptor<SearchBaseDto> dtoCaptor = ArgumentCaptor.forClass(SearchBaseDto.class);
        verify(userRepository).findListUserFilter(dtoCaptor.capture(), anyList(), eq(pageable));
        
        assertThat(dtoCaptor.getValue().getSearchString())
                .as("Business logic: searchString phai duoc trim")
                .isEqualTo("consultant1@gmail.com");
    }

    /**
     * TC-USER-045: Kiem tra updateUserDetails() phai trim phone co khoang trang
     * Muc dich: Xac minh he thong tuan thu business logic: phone phai duoc trim truoc khi luu hoac validate.
     * Du lieu dau vao: UserRequest voi phone = "  0987654321  "
     * Ket qua ky vong: user.phone = "0987654321" (da trim)
     */
    @Test
    void updateUserDetails_PhoneWithSpaces_ShouldSaveTrimmedPhone() {
        HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn("s@x.com");

        User user = newUserWithRole("s@x.com", "encoded", ERole.STUDENT);
        user.setStudent(new Student("OldEdu", "OldMajor"));
        when(userRepository.findByEmail("s@x.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0, User.class));

        UserResponse baseResponse = new UserResponse();
        when(modelMapper.map(any(User.class), eq(UserResponse.class))).thenReturn(baseResponse);
        StudentResponse studentResponse = new StudentResponse();
        when(modelMapper.map(any(Student.class), eq(StudentResponse.class))).thenReturn(studentResponse);

        UserRequest request = UserRequest.builder()
                .phone("  0987654321  ")
                .build();

        userServiceImplUnderTest.updateUserDetails(httpServletRequest, request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        assertThat(userCaptor.getValue().getPhone())
                .as("Business logic: phone phai duoc trim")
                .isEqualTo("0987654321");
    }

}
