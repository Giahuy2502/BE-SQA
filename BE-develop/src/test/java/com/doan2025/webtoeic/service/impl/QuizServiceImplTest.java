package com.doan2025.webtoeic.service.impl;

import com.doan2025.webtoeic.constants.enums.ERole;
import com.doan2025.webtoeic.domain.*;
import com.doan2025.webtoeic.dto.SearchQuizDto;
import com.doan2025.webtoeic.dto.SearchSubmittedDto;
import com.doan2025.webtoeic.dto.request.QuizRequest;
import com.doan2025.webtoeic.dto.request.SharedQuizRequest;
import com.doan2025.webtoeic.dto.request.SubmitRequest;
import com.doan2025.webtoeic.dto.response.QuizResponse;
import com.doan2025.webtoeic.dto.response.ShareQuizResponse;
import com.doan2025.webtoeic.dto.response.SubmitResponse;
import com.doan2025.webtoeic.dto.response.OverviewResponse;
import com.doan2025.webtoeic.exception.WebToeicException;
import com.doan2025.webtoeic.repository.*;
import com.doan2025.webtoeic.utils.ConvertUtil;
import com.doan2025.webtoeic.utils.JwtUtil;
import com.doan2025.webtoeic.utils.NotiUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
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

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuizServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private ConvertUtil convertUtil;
    @Mock
    private AnswerRepository answerRepository;
    @Mock
    private QuestionRepository questionRepository;
    @Mock
    private QuestionBankRepository questionBankRepository;
    @Mock
    private QuizRepository quizRepository;
    @Mock
    private QuestionQuizRepository questionQuizRepository;
    @Mock
    private ClassRepository classRepository;
    @Mock
    private ShareQuizRepository shareQuizRepository;
    @Mock
    private StudentQuizRepository studentQuizRepository;
    @Mock
    private StudentAnswerRepository studentAnswerRepository;
    @Mock
    private ClassMemberRepository classMemberRepository;
    @Mock
    private NotiUtils notiUtils;
    @Mock
    private ModelMapper modelMapper;

    @InjectMocks
    private QuizServiceImpl quizService;

    @Mock
    private HttpServletRequest request;

    private User student;
    private User teacher;

    @BeforeEach
    void setUp() {
        student = new User();
        student.setId(10L);
        student.setEmail("student@test.com");
        student.setRole(ERole.STUDENT);

        teacher = new User();
        teacher.setId(11L);
        teacher.setEmail("teacher@test.com");
        teacher.setRole(ERole.TEACHER);
    }

    @Test
    void getListSubmitQuiz_studentNotInClass_throwWebToeicException() {
        SearchSubmittedDto dto = new SearchSubmittedDto();
        when(jwtUtil.getEmailFromToken(request)).thenReturn(student.getEmail());
        when(userRepository.findByEmail(student.getEmail())).thenReturn(Optional.of(student));
        when(classMemberRepository.existsMemberInClass(1L, student.getId())).thenReturn(false);

        assertThrows(WebToeicException.class,
                () -> quizService.getListSubmitQuiz(request, 2L, 1L, dto, PageRequest.of(0, 10)));

        verify(studentQuizRepository, never()).filter(anyLong(), anyLong(), any(), any(), any());
    }

    @Test
    void getListSubmitQuiz_teacherInClass_returnMappedPage() {
        SearchSubmittedDto dto = new SearchSubmittedDto();
        StudentQuiz studentQuiz = StudentQuiz.builder().id(7L).build();
        SubmitResponse response = SubmitResponse.builder().build();

        when(jwtUtil.getEmailFromToken(request)).thenReturn(teacher.getEmail());
        when(userRepository.findByEmail(teacher.getEmail())).thenReturn(Optional.of(teacher));
        when(classMemberRepository.existsMemberInClass(5L, teacher.getId())).thenReturn(true);
        when(studentQuizRepository.filter(3L, 5L, dto, PageRequest.of(0, 10), null))
                .thenReturn(new PageImpl<>(List.of(studentQuiz), PageRequest.of(0, 10), 1));
        when(convertUtil.convertSubmitToDto(request, studentQuiz, true)).thenReturn(response);

        Page<SubmitResponse> result = quizService.getListSubmitQuiz(request, 3L, 5L, dto, PageRequest.of(0, 10));

        assertEquals(1, result.getTotalElements());
        assertSame(response, result.getContent().get(0));
    }

    @Test
    void submitQuiz_withAnswers_calculateAndSaveScore() {
        when(jwtUtil.getEmailFromToken(request)).thenReturn(student.getEmail());
        when(userRepository.findByEmail(student.getEmail())).thenReturn(Optional.of(student));

        Quiz quiz = Quiz.builder().id(2L).totalQuestions(2L).build();
        when(quizRepository.findById(2L)).thenReturn(Optional.of(quiz));

        com.doan2025.webtoeic.domain.Class clazz = com.doan2025.webtoeic.domain.Class.builder().id(4L).build();
        when(classRepository.findById(4L)).thenReturn(Optional.of(clazz));

        Question q1 = Question.builder().id(100L).build();
        Question q2 = Question.builder().id(101L).build();
        when(questionRepository.findById(100L)).thenReturn(Optional.of(q1));
        when(questionRepository.findById(101L)).thenReturn(Optional.of(q2));

        Answer a1 = Answer.builder().id(200L).isCorrect(true).build();
        Answer a2 = Answer.builder().id(201L).isCorrect(false).build();
        when(answerRepository.findById(200L)).thenReturn(Optional.of(a1));
        when(answerRepository.findById(201L)).thenReturn(Optional.of(a2));

        when(studentQuizRepository.save(any(StudentQuiz.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SubmitRequest r1 = new SubmitRequest();
        r1.setQuestionId(100L);
        r1.setAnswerId(200L);
        r1.setStartAt(new Date());
        r1.setEndAt(new Date());

        SubmitRequest r2 = new SubmitRequest();
        r2.setQuestionId(101L);
        r2.setAnswerId(201L);
        r2.setStartAt(new Date());
        r2.setEndAt(new Date());

        quizService.submitQuiz(request, 2L, List.of(r1, r2), 4L, "done");

        ArgumentCaptor<StudentQuiz> captor = ArgumentCaptor.forClass(StudentQuiz.class);
        verify(studentQuizRepository, atLeast(2)).save(captor.capture());
        StudentQuiz lastSaved = captor.getValue();
        assertEquals(new BigDecimal("5.00"), lastSaved.getScore());
        verify(studentAnswerRepository, times(2)).save(any(StudentAnswer.class));
    }

    @Test
    void updateQuizInClass_teacherNotMember_throwWebToeicException() {
        SharedQuizRequest req = new SharedQuizRequest();
        req.setClassId(9L);
        req.setSharedQuizId(99L);

        when(jwtUtil.getEmailFromToken(request)).thenReturn(teacher.getEmail());
        when(userRepository.findByEmail(teacher.getEmail())).thenReturn(Optional.of(teacher));
        when(classMemberRepository.existsMemberInClass(9L, teacher.getId())).thenReturn(false);

        assertThrows(WebToeicException.class, () -> quizService.updateQuizInClass(request, req));
        verify(shareQuizRepository, never()).save(any());
    }

    @Test
    void pullQuizToClass_success_saveAndNotify() {
        SharedQuizRequest req = new SharedQuizRequest();
        req.setClassId(3L);
        req.setQuizId(6L);
        req.setStartAt(new Date());
        req.setEndAt(new Date());

        User manager = new User();
        manager.setId(1L);
        manager.setEmail("manager@test.com");
        manager.setRole(ERole.MANAGER);

        Quiz quiz = Quiz.builder().id(6L).build();
        com.doan2025.webtoeic.domain.Class clazz = com.doan2025.webtoeic.domain.Class.builder().id(3L).build();
        when(jwtUtil.getEmailFromToken(request)).thenReturn(manager.getEmail());
        when(userRepository.findByEmail(manager.getEmail())).thenReturn(Optional.of(manager));
        when(quizRepository.findById(6L)).thenReturn(Optional.of(quiz));
        when(classRepository.findById(3L)).thenReturn(Optional.of(clazz));
        when(classMemberRepository.findMembersInClass(3L)).thenReturn(List.of(student));

        quizService.pullQuizToClass(request, req);

        verify(shareQuizRepository).save(any(SharedQuiz.class));
        verify(notiUtils).sendNoti(anyList(), any(), anyString(), anyString(), eq(3L));
    }

    @Test
    void createQuiz_success_returnConvertedResponse() {
        QuizRequest req = new QuizRequest();
        req.setTitle("Mock quiz");
        req.setDescription("desc");

        Quiz saved = Quiz.builder().id(7L).title("Mock quiz").build();
        QuizResponse mapped = QuizResponse.builder().id(7L).title("Mock quiz").build();

        when(jwtUtil.getEmailFromToken(request)).thenReturn(teacher.getEmail());
        when(userRepository.findByEmail(teacher.getEmail())).thenReturn(Optional.of(teacher));
        when(quizRepository.save(any(Quiz.class))).thenReturn(saved);
        when(convertUtil.convertQuizToDto(saved)).thenReturn(mapped);

        QuizResponse result = quizService.createQuiz(request, req);

        assertSame(mapped, result);
    }

    @Test
    void getDetailSubmitQuiz_success_returnConvertedSubmit() {
        StudentQuiz studentQuiz = StudentQuiz.builder().id(9L).build();
        SubmitResponse submitResponse = SubmitResponse.builder().build();

        when(jwtUtil.getEmailFromToken(request)).thenReturn(teacher.getEmail());
        when(userRepository.findByEmail(teacher.getEmail())).thenReturn(Optional.of(teacher));
        when(studentQuizRepository.findById(9L)).thenReturn(Optional.of(studentQuiz));
        when(convertUtil.convertSubmitToDto(request, studentQuiz, false)).thenReturn(submitResponse);

        SubmitResponse result = quizService.getDetailSubmitQuiz(request, 9L);

        assertSame(submitResponse, result);
    }

    @Test
    void getQuiz_success_returnConvertedQuiz() {
        Quiz quiz = Quiz.builder().id(88L).build();
        QuizResponse response = QuizResponse.builder().id(88L).build();

        when(quizRepository.findById(88L)).thenReturn(Optional.of(quiz));
        when(convertUtil.convertQuizToDto(quiz)).thenReturn(response);

        QuizResponse result = quizService.getQuiz(request, 88L);

        assertSame(response, result);
    }

    @Test
    void getQuizes_success_returnMappedPage() {
        SearchQuizDto dto = new SearchQuizDto();
        Quiz quiz = Quiz.builder().id(12L).build();
        QuizResponse response = QuizResponse.builder().id(12L).build();

        when(quizRepository.filter(dto, PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of(quiz), PageRequest.of(0, 10), 1));
        when(convertUtil.convertQuizToDto(quiz)).thenReturn(response);

        Page<QuizResponse> result = quizService.getQuizes(request, dto, PageRequest.of(0, 10));

        assertEquals(1, result.getTotalElements());
        assertSame(response, result.getContent().get(0));
    }

    @Test
    void statisticOverviewQuizInClass_success_calculateFields() {
        SearchQuizDto dto = new SearchQuizDto();
        when(jwtUtil.getEmailFromToken(request)).thenReturn(teacher.getEmail());
        when(userRepository.findByEmail(teacher.getEmail())).thenReturn(Optional.of(teacher));
        when(shareQuizRepository.filter(dto, 4L)).thenReturn(List.of(new SharedQuiz(), new SharedQuiz()));
        when(shareQuizRepository.statisticOverviewOverScoreQuizInClass(4L, dto, 5L)).thenReturn(1L);

        OverviewResponse result = quizService.statisticOverviewQuizInClass(request, 4L, 5L, dto);

        assertEquals(2L, result.getTotal());
        assertEquals(1L, result.getOverScore());
        assertEquals(1L, result.getUnderScore());
    }

    @Test
    void statisticDetailQuizInClass_success_calculateFields() {
        SearchSubmittedDto dto = new SearchSubmittedDto();
        when(jwtUtil.getEmailFromToken(request)).thenReturn(teacher.getEmail());
        when(userRepository.findByEmail(teacher.getEmail())).thenReturn(Optional.of(teacher));
        when(studentQuizRepository.filter(3L, 2L, dto, null, null))
                .thenReturn(new PageImpl<>(List.of(new StudentQuiz(), new StudentQuiz())));
        when(studentQuizRepository.countOver(3L, dto, 6L)).thenReturn(1L);

        OverviewResponse result = quizService.statisticDetailQuizInClass(request, 3L, 2L, 6L, dto);

        assertEquals(2L, result.getTotal());
        assertEquals(1L, result.getOverScore());
        assertEquals(1L, result.getUnderScore());
    }

    @Test
    void getListQuizInClass_studentMember_returnMappedPage() {
        SharedQuiz item = SharedQuiz.builder().id(1L).build();

        when(jwtUtil.getEmailFromToken(request)).thenReturn(student.getEmail());
        when(userRepository.findByEmail(student.getEmail())).thenReturn(Optional.of(student));
        when(classMemberRepository.existsMemberInClass(7L, student.getId())).thenReturn(true);
        when(shareQuizRepository.filter(any(SearchQuizDto.class), eq(7L), eq(PageRequest.of(0, 10))))
                .thenReturn(new PageImpl<>(List.of(item), PageRequest.of(0, 10), 1));
        when(convertUtil.convertShareQuizToDto(request, item)).thenReturn(null);

        Page<ShareQuizResponse> result = quizService.getListQuizInClass(request, 7L, new SearchQuizDto(),
                PageRequest.of(0, 10));

        assertEquals(1, result.getTotalElements());
        verify(convertUtil).convertShareQuizToDto(request, item);
    }

    @Test
    void updateQuizInClass_success_updateAndSave() {
        SharedQuizRequest req = new SharedQuizRequest();
        req.setSharedQuizId(20L);
        req.setClassId(9L);
        req.setStartAt(new Date());
        req.setEndAt(new Date());
        req.setIsActive(false);
        req.setIsDelete(true);

        SharedQuiz sharedQuiz = SharedQuiz.builder().id(20L).isActive(true).isDelete(false).build();

        when(jwtUtil.getEmailFromToken(request)).thenReturn(teacher.getEmail());
        when(userRepository.findByEmail(teacher.getEmail())).thenReturn(Optional.of(teacher));
        when(classMemberRepository.existsMemberInClass(9L, teacher.getId())).thenReturn(true);
        when(shareQuizRepository.findById(20L)).thenReturn(Optional.of(sharedQuiz));

        quizService.updateQuizInClass(request, req);

        verify(shareQuizRepository).save(sharedQuiz);
        assertFalse(sharedQuiz.getIsActive());
        assertTrue(sharedQuiz.getIsDelete());
        assertSame(teacher, sharedQuiz.getUpdatedBy());
    }

    @Test
    void convertBankToQuiz_success_saveQuestionLinksAndReturnDto() {
        User manager = new User();
        manager.setEmail("manager@test.com");

        QuestionBank bank = QuestionBank.builder().id(5L).title("Bank A").build();
        Question q1 = Question.builder().id(1L).build();
        Question q2 = Question.builder().id(2L).build();
        Quiz savedQuiz = Quiz.builder().id(100L).build();
        QuizResponse mapped = QuizResponse.builder().id(100L).build();

        when(jwtUtil.getEmailFromToken(request)).thenReturn(manager.getEmail());
        when(userRepository.findByEmail(manager.getEmail())).thenReturn(Optional.of(manager));
        when(questionBankRepository.findById(5L)).thenReturn(Optional.of(bank));
        when(questionRepository.findByQuestionBankId(5L)).thenReturn(List.of(q1, q2));
        when(quizRepository.save(any(Quiz.class))).thenReturn(savedQuiz);
        when(convertUtil.convertQuizToDto(savedQuiz)).thenReturn(mapped);

        QuizResponse result = quizService.convertBankToQuiz(request, 5L);

        assertSame(mapped, result);
        verify(questionQuizRepository, times(2)).save(any(QuestionQuiz.class));
    }

    @Test
    void updateQuiz_success_updateFieldsAndSave() {
        QuizRequest req = new QuizRequest();
        req.setId(50L);
        req.setTitle("Updated");
        req.setDescription("Desc");
        req.setIsActive(false);
        req.setIsDelete(true);

        Quiz quiz = Quiz.builder().id(50L).title("Old").isActive(true).isDelete(false).build();
        QuizResponse mapped = QuizResponse.builder().id(50L).title("Updated").build();

        when(jwtUtil.getEmailFromToken(request)).thenReturn(teacher.getEmail());
        when(userRepository.findByEmail(teacher.getEmail())).thenReturn(Optional.of(teacher));
        when(quizRepository.findById(50L)).thenReturn(Optional.of(quiz));
        when(quizRepository.save(quiz)).thenReturn(quiz);
        when(convertUtil.convertQuizToDto(quiz)).thenReturn(mapped);

        QuizResponse result = quizService.updateQuiz(request, req);

        assertSame(mapped, result);
        assertEquals("Updated", quiz.getTitle());
        assertFalse(quiz.getIsActive());
        assertTrue(quiz.getIsDelete());
    }

    @Test
    void addQuestionToQuiz_success_increaseTotalAndSaveLinks() {
        QuizRequest req = new QuizRequest();
        req.setId(51L);
        req.setIdQuestions(List.of(101L, 102L));

        Quiz quiz = Quiz.builder().id(51L).totalQuestions(0L).build();
        QuizResponse mapped = QuizResponse.builder().id(51L).build();

        when(jwtUtil.getEmailFromToken(request)).thenReturn(teacher.getEmail());
        when(userRepository.findByEmail(teacher.getEmail())).thenReturn(Optional.of(teacher));
        when(quizRepository.findById(51L)).thenReturn(Optional.of(quiz));
        when(questionQuizRepository.countByQuizId(51L)).thenReturn(1L);
        when(questionRepository.findById(101L)).thenReturn(Optional.of(Question.builder().id(101L).build()));
        when(questionRepository.findById(102L)).thenReturn(Optional.of(Question.builder().id(102L).build()));
        when(quizRepository.save(quiz)).thenReturn(quiz);
        when(convertUtil.convertQuizToDto(quiz)).thenReturn(mapped);

        QuizResponse result = quizService.addQuestionToQuiz(request, req);

        assertSame(mapped, result);
        assertEquals(3L, quiz.getTotalQuestions());
        verify(questionQuizRepository, times(2)).save(any(QuestionQuiz.class));
    }

    @Test
    void removeQuestionFromQuiz_success_decreaseTotalAndDeleteLinks() {
        QuizRequest req = new QuizRequest();
        req.setId(52L);
        req.setIdQuestions(List.of(201L, 202L));

        Quiz quiz = Quiz.builder().id(52L).totalQuestions(4L).build();
        QuizResponse mapped = QuizResponse.builder().id(52L).build();

        when(jwtUtil.getEmailFromToken(request)).thenReturn(teacher.getEmail());
        when(userRepository.findByEmail(teacher.getEmail())).thenReturn(Optional.of(teacher));
        when(quizRepository.findById(52L)).thenReturn(Optional.of(quiz));
        when(questionQuizRepository.countByQuizId(52L)).thenReturn(4L);
        when(quizRepository.save(quiz)).thenReturn(quiz);
        when(convertUtil.convertQuizToDto(quiz)).thenReturn(mapped);

        QuizResponse result = quizService.removeQuestionFromQuiz(request, req);

        assertSame(mapped, result);
        assertEquals(2L, quiz.getTotalQuestions());
        verify(questionQuizRepository).deleteQuestionQuizByQuizIdAndQuestionId(52L, 201L);
        verify(questionQuizRepository).deleteQuestionQuizByQuizIdAndQuestionId(52L, 202L);
    }
}
