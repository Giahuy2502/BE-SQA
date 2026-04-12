package com.doan2025.webtoeic.utils;

import com.doan2025.webtoeic.constants.enums.ECategoryCourse;
import com.doan2025.webtoeic.constants.enums.ERole;
import com.doan2025.webtoeic.domain.Answer;
import com.doan2025.webtoeic.domain.Course;
import com.doan2025.webtoeic.domain.Enrollment;
import com.doan2025.webtoeic.domain.RangeTopic;
import com.doan2025.webtoeic.domain.ScoreScale;
import com.doan2025.webtoeic.domain.User;
import com.doan2025.webtoeic.dto.response.AnswerResponse;
import com.doan2025.webtoeic.dto.response.CourseResponse;
import com.doan2025.webtoeic.dto.response.RangeTopicResponse;
import com.doan2025.webtoeic.dto.response.ScoreScaleResponse;
import com.doan2025.webtoeic.repository.AnswerRepository;
import com.doan2025.webtoeic.repository.ExplanationQuestionRepository;
import com.doan2025.webtoeic.repository.QuestionRepository;
import com.doan2025.webtoeic.repository.StudentAnswerRepository;
import com.doan2025.webtoeic.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConvertUtilTest {

    private ConvertUtil convertUtil;

    // Lưu ý: trên JDK hiện tại Mockito inline có thể không mock được ModelMapper/JwtUtil.
    // Vì vậy, dùng ModelMapper thật + JwtUtil giả, các repository dùng mock.
    private ModelMapper modelMapper;

    @Mock
    private UserRepository userRepository;
    private TestJwtUtil jwtUtil;
    @Mock
    private AnswerRepository answerRepository;
    @Mock
    private QuestionRepository questionRepository;
    @Mock
    private ExplanationQuestionRepository explanationQuestionRepository;
    @Mock
    private StudentAnswerRepository studentAnswerRepository;

    @BeforeEach
    void setUp() {
        modelMapper = new ModelMapper();
        jwtUtil = new TestJwtUtil();
        convertUtil = new ConvertUtil(
                modelMapper,
                userRepository,
                jwtUtil,
                answerRepository,
                questionRepository,
                explanationQuestionRepository,
                studentAnswerRepository
        );
    }

    // UTC-CV-001: convertScoreScaleToDto map đúng các field cơ bản
    @Test
    void convertScoreScaleToDto_shouldMapFieldsCorrectly() {
        // Given
        ScoreScale scoreScale = new ScoreScale();
        scoreScale.setId(1L);
        scoreScale.setTitle("A1");
        scoreScale.setFromScore(10);
        scoreScale.setToScore(20);
        scoreScale.setIsActive(true);
        scoreScale.setIsDelete(false);

        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);

        // When
        ScoreScaleResponse dto = convertUtil.convertScoreScaleToDto(request, scoreScale);

        // Then
        assertNotNull(dto);
        assertEquals(1L, dto.getId());
        assertEquals("A1", dto.getTitle());
        assertEquals(10, dto.getFromScore());
        assertEquals(20, dto.getToScore());
        assertEquals(true, dto.getIsActive());
        assertEquals(false, dto.getIsDelete());
    }

    // UTC-CV-002: convertRangeTopicToDto map đúng các field cơ bản
    @Test
    void convertRangeTopicToDto_shouldMapFieldsCorrectly() {
        // Given
        RangeTopic topic = new RangeTopic();
        topic.setId(2L);
        topic.setVietnamese("Từ vựng");
        topic.setContent("Vocabulary");
        topic.setDescription("Desc");
        topic.setIsActive(true);
        topic.setIsDelete(false);

        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);

        // When
        RangeTopicResponse dto = convertUtil.convertRangeTopicToDto(request, topic);

        // Then
        assertNotNull(dto);
        assertEquals(2L, dto.getId());
        assertEquals("Từ vựng", dto.getVietnamese());
        assertEquals("Vocabulary", dto.getContent());
        assertEquals("Desc", dto.getDescription());
        assertEquals(true, dto.getIsActive());
        assertEquals(false, dto.getIsDelete());
    }

    // UTC-CV-003: convertAnswerToDto map đúng thông tin answer cơ bản (không phụ thuộc DB)
    @Test
    void convertAnswerToDto_shouldMapAnswerFieldsCorrectly() {
        // Given: để tránh nhánh modelMapper.map(createdAt/updatedAt), giữ createdAt/updatedAt = null
        Answer answer = new Answer();
        answer.setId(3L);
        answer.setContent("A");
        answer.setIsCorrect(true);
        answer.setIsActive(true);
        answer.setIsDelete(false);

        // When
        AnswerResponse dto = convertUtil.convertAnswerToDto(answer);

        // Then
        assertNotNull(dto);
        assertEquals(3L, dto.getId());
        assertEquals("A", dto.getContent());
        assertEquals(true, dto.getCorrect());
        assertEquals(true, dto.getIsActive());
        assertEquals(false, dto.getIsDelete());
    }

    // UTC-CV-004: convertCourseToDto khi không có Authorization header -> isBought mặc định false
    @Test
    void convertCourseToDto_shouldKeepIsBoughtFalse_whenNoAuthorizationHeader() {
        // Given
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn(null);

        Course course = baseCourse();

        // When
        CourseResponse dto = convertUtil.convertCourseToDto(request, course);

        // Then
        assertNotNull(dto);
        assertFalse(dto.getIsBought());
        assertEquals("Listening", dto.getCategoryName());
    }

    // UTC-CV-005: convertCourseToDto với role MANAGER -> isBought = true
    @Test
    void convertCourseToDto_shouldSetIsBoughtTrue_whenUserRoleManager() {
        // Given
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer token");
        jwtUtil.emailFromToken = "manager@test.com";

        User manager = new User();
        manager.setEmail("manager@test.com");
        manager.setRole(ERole.MANAGER);
        when(userRepository.findByEmail("manager@test.com")).thenReturn(Optional.of(manager));

        Course course = baseCourse();

        // When
        CourseResponse dto = convertUtil.convertCourseToDto(request, course);

        // Then
        assertTrue(dto.getIsBought());
    }

    // UTC-CV-006: convertCourseToDto với STUDENT đã enroll khóa -> isBought = true
    @Test
    void convertCourseToDto_shouldSetIsBoughtTrue_whenStudentEnrolledInCourse() {
        // Given
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer token");
        jwtUtil.emailFromToken = "student@test.com";

        User student = new User();
        student.setEmail("student@test.com");
        student.setRole(ERole.STUDENT);
        when(userRepository.findByEmail("student@test.com")).thenReturn(Optional.of(student));

        Course course = baseCourse();
        Enrollment enrollment = new Enrollment();
        enrollment.setUser(student);
        course.setEnrollments(List.of(enrollment));

        // When
        CourseResponse dto = convertUtil.convertCourseToDto(request, course);

        // Then
        assertTrue(dto.getIsBought());
    }

    private Course baseCourse() {
        Course course = new Course();
        course.setId(10L);
        course.setTitle("C1");
        course.setDescription("Desc");
        course.setPrice(100L);
        course.setThumbnailUrl("thumb");
        course.setCategoryCourse(ECategoryCourse.LISTENING);
        course.setIsActive(true);
        course.setIsDelete(false);
        course.setEnrollments(List.of());

        User author = new User();
        author.setFirstName("Au");
        author.setLastName("Thor");
        course.setAuthor(author);

        User createdBy = new User();
        createdBy.setFirstName("Cre");
        createdBy.setLastName("Ator");
        course.setCreatedBy(createdBy);

        return course;
    }

    private static class TestJwtUtil extends JwtUtil {
        private String emailFromToken;

        @Override
        public String getEmailFromToken(HttpServletRequest request) {
            return emailFromToken;
        }
    }
}

