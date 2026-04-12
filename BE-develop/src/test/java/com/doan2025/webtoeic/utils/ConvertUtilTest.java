package com.doan2025.webtoeic.utils;

import com.doan2025.webtoeic.constants.enums.ECategoryCourse;
import com.doan2025.webtoeic.constants.enums.EClassStatus;
import com.doan2025.webtoeic.constants.enums.EQuizStatus;
import com.doan2025.webtoeic.constants.enums.ERole;
import com.doan2025.webtoeic.domain.Answer;
import com.doan2025.webtoeic.domain.AttachDocumentLesson;
import com.doan2025.webtoeic.domain.Class;
import com.doan2025.webtoeic.domain.Course;
import com.doan2025.webtoeic.domain.Enrollment;
import com.doan2025.webtoeic.domain.ExplanationQuestion;
import com.doan2025.webtoeic.domain.Question;
import com.doan2025.webtoeic.domain.Quiz;
import com.doan2025.webtoeic.domain.RangeTopic;
import com.doan2025.webtoeic.domain.ScoreScale;
import com.doan2025.webtoeic.domain.SharedQuiz;
import com.doan2025.webtoeic.domain.StudentQuiz;
import com.doan2025.webtoeic.domain.SubmitExercise;
import com.doan2025.webtoeic.domain.User;
import com.doan2025.webtoeic.dto.response.AnswerResponse;
import com.doan2025.webtoeic.dto.response.CourseResponse;
import com.doan2025.webtoeic.dto.response.ExplanationQuestionResponse;
import com.doan2025.webtoeic.dto.response.RangeTopicResponse;
import com.doan2025.webtoeic.dto.response.ScoreScaleResponse;
import com.doan2025.webtoeic.dto.response.ShareQuizResponse;
import com.doan2025.webtoeic.dto.response.SubmitExerciseResponse;
import com.doan2025.webtoeic.dto.response.SubmitResponse;
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

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
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

    // UTC-CV-007: convertSubmitToDto khi isList=true — quiz = null, map field cơ bản
    @Test
    void convertSubmitToDto_shouldOmitQuiz_whenIsListTrue() {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        User user = new User();
        user.setId(1L);
        user.setFirstName("F");
        user.setLastName("L");
        Quiz quiz = new Quiz();
        quiz.setId(5L);
        quiz.setTitle("Quiz title");
        StudentQuiz studentQuiz = new StudentQuiz();
        studentQuiz.setId(100L);
        studentQuiz.setUser(user);
        studentQuiz.setQuiz(quiz);
        studentQuiz.setScore(new BigDecimal("8.5"));
        studentQuiz.setDes("done");
        studentQuiz.setStartAt(new Date());
        studentQuiz.setEndAt(new Date());

        SubmitResponse dto = convertUtil.convertSubmitToDto(request, studentQuiz, true);

        assertNotNull(dto);
        assertEquals(100L, dto.getIdSubmitted());
        assertEquals("Quiz title", dto.getTitleQuiz());
        assertNull(dto.getQuiz());
        assertEquals(0, new BigDecimal("8.5").compareTo(dto.getScore()));
    }

    // UTC-CV-008: convertShareQuizToDto — stub question list rỗng cho quiz lồng nhau
    @Test
    void convertShareQuizToDto_shouldMapIdsAndNestedClass() {
        when(questionRepository.findByQuizId(anyLong())).thenReturn(Collections.emptyList());

        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);

        User teacher = new User();
        teacher.setFirstName("Tea");
        teacher.setLastName("Cher");
        User createdBy = new User();
        createdBy.setFirstName("Cr");
        createdBy.setLastName("Eator");

        Class clazz = new Class();
        clazz.setId(1L);
        clazz.setName("Lớp A");
        clazz.setDescription("desc");
        clazz.setTitle("title");
        clazz.setStatus(EClassStatus.ONGOING);
        clazz.setTeacher(teacher);
        clazz.setCreatedBy(createdBy);

        Quiz quiz = new Quiz();
        quiz.setId(2L);
        quiz.setTitle("Q1");
        quiz.setDescription("qd");
        quiz.setTotalQuestions(5L);
        quiz.setStatus(EQuizStatus.PUBLIC);

        SharedQuiz shared = SharedQuiz.builder()
                .id(9L)
                .startAt(new Date())
                .endAt(new Date())
                .createdAt(new Date())
                .updatedAt(new Date())
                .isActive(true)
                .isDelete(false)
                .clazz(clazz)
                .quiz(quiz)
                .build();

        ShareQuizResponse dto = convertUtil.convertShareQuizToDto(request, shared);

        assertNotNull(dto);
        assertEquals(9L, dto.getSharedQuizId());
        assertNotNull(dto.getClazz());
        assertEquals("Lớp A", dto.getClazz().getName());
        assertNotNull(dto.getQuiz());
        assertEquals(2L, dto.getQuiz().getId());
    }

    // UTC-CV-009: convertExplanationQuestionToDto
    @Test
    void convertExplanationQuestionToDto_shouldMapTextFields() {
        ExplanationQuestion eq = ExplanationQuestion.builder()
                .id(3L)
                .explanationEnglish("en text")
                .explanationVietnamese("vi text")
                .isActive(true)
                .isDelete(false)
                .build();

        ExplanationQuestionResponse dto = convertUtil.convertExplanationQuestionToDto(eq);

        assertEquals(3L, dto.getId());
        assertEquals("en text", dto.getExplanationEnglish());
        assertEquals("vi text", dto.getExplanationVietnamese());
    }

    // UTC-CV-010: convertAttachDocumentLessonToDto
    @Test
    void convertAttachDocumentLessonToDto_shouldMapFields() {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        AttachDocumentLesson doc = new AttachDocumentLesson();
        doc.setId(7L);
        doc.setLinkUrl("https://doc.example/file.pdf");
        doc.setIsActive(true);
        doc.setIsDelete(false);

        var dto = convertUtil.convertAttachDocumentLessonToDto(request, doc);

        assertEquals(7L, dto.getId());
        assertEquals("https://doc.example/file.pdf", dto.getLinkUrl());
        assertTrue(dto.getIsActive());
    }

    // UTC-CV-011: convertSubmitExerciseToDto
    @Test
    void convertSubmitExerciseToDto_shouldMapCoreFields() {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        User creator = new User();
        creator.setFirstName("A");
        creator.setLastName("B");
        SubmitExercise se = new SubmitExercise();
        se.setId(11L);
        se.setLinkUrl("https://submit/x");
        se.setIsActive(true);
        se.setIsDelete(false);
        se.setCreatedBy(creator);

        SubmitExerciseResponse dto = convertUtil.convertSubmitExerciseToDto(request, se);

        assertEquals(11L, dto.getId());
        assertEquals("https://submit/x", dto.getLinkUrl());
        assertNotNull(dto.getCreatedBy());
    }

    // UTC-CV-012: convertQuestionToDto với explanation stub và answers rỗng
    @Test
    void convertQuestionToDto_shouldMapQuestionWithEmptyAnswers() {
        Question question = new Question();
        question.setId(40L);
        question.setContent("Q content");
        question.setIsActive(true);
        question.setIsDelete(false);

        ExplanationQuestion expl = ExplanationQuestion.builder()
                .id(50L)
                .explanationEnglish("e")
                .explanationVietnamese("v")
                .isActive(true)
                .isDelete(false)
                .build();
        when(explanationQuestionRepository.findByQuestionId(40L)).thenReturn(expl);
        when(answerRepository.findByQuestionId(40L)).thenReturn(Collections.emptyList());

        var dto = convertUtil.convertQuestionToDto(question);

        assertEquals(40L, dto.getId());
        assertEquals("Q content", dto.getQuestionContent());
        assertNotNull(dto.getExplanation());
        assertEquals(50L, dto.getExplanation().getId());
        assertTrue(dto.getAnswers().isEmpty());
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

