package com.learnez.exam;

import com.doan2025.webtoeic.constants.enums.ERole;
import com.doan2025.webtoeic.constants.enums.ResponseCode;
import com.doan2025.webtoeic.domain.Class;
import com.doan2025.webtoeic.domain.*;
import com.doan2025.webtoeic.dto.SearchMemberInClassDto;
import com.doan2025.webtoeic.dto.SearchQuizDto;
import com.doan2025.webtoeic.dto.SearchSubmittedDto;
import com.doan2025.webtoeic.dto.request.QuizRequest;
import com.doan2025.webtoeic.dto.request.SharedQuizRequest;
import com.doan2025.webtoeic.dto.request.SubmitRequest;
import com.doan2025.webtoeic.dto.response.*;
import com.doan2025.webtoeic.exception.WebToeicException;
import com.doan2025.webtoeic.repository.*;
import com.doan2025.webtoeic.service.impl.QuizServiceImpl;
import com.doan2025.webtoeic.utils.ConvertUtil;
import com.doan2025.webtoeic.utils.JwtUtil;
import com.doan2025.webtoeic.utils.NotiUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ExamServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private JwtUtil jwtUtil;
    @Mock private ConvertUtil convertUtil;
    @Mock private AnswerRepository answerRepository;
    @Mock private QuestionRepository questionRepository;
    @Mock private QuestionBankRepository questionBankRepository;
    @Mock private QuizRepository quizRepository;
    @Mock private QuestionQuizRepository questionQuizRepository;
    @Mock private ClassRepository classRepository;
    @Mock private ShareQuizRepository shareQuizRepository;
    @Mock private StudentQuizRepository studentQuizRepository;
    @Mock private StudentAnswerRepository studentAnswerRepository;
    @Mock private ClassMemberRepository classMemberRepository;
    @Mock private NotiUtils notiUtils;
    @Mock private ModelMapper modelMapper;

    @InjectMocks
    private QuizServiceImpl quizService;

    @Mock
    private HttpServletRequest request;

    private User adminUser;
    private User teacherUser;
    private User studentUser;

    @BeforeEach
    public void setup() {
        adminUser = new User();
        adminUser.setId(1L);
        adminUser.setRole(ERole.MANAGER);

        teacherUser = new User();
        teacherUser.setId(2L);
        teacherUser.setRole(ERole.TEACHER);

        studentUser = new User();
        studentUser.setId(3L);
        studentUser.setRole(ERole.STUDENT);
        studentUser.setEmail("student@gmail.com");
    }

    private void mockAuth(User user) {
        when(jwtUtil.getEmailFromToken(any())).thenReturn("user@gmail.com");
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(user));
    }

    // =========================================================================================
    // 1. statisticDetailQuizInClass
    // =========================================================================================

    // TC-EXAM-001
    @Test
    public void test_statisticDetailQuizInClass_Success() {
        // Arrange
        mockAuth(teacherUser);
        SearchSubmittedDto dto = new SearchSubmittedDto();
        Page<StudentQuiz> page = new PageImpl<>(List.of(new StudentQuiz(), new StudentQuiz()));
        when(studentQuizRepository.filter(anyLong(), anyLong(), any(), any(), any())).thenReturn(page);
        when(studentQuizRepository.countOver(anyLong(), any(), anyLong())).thenReturn(1L); // 1 over, total 2
        
        // Act
        OverviewResponse res = quizService.statisticDetailQuizInClass(request, 1L, 1L, 5L, dto);
        
        // Assert
        assertNotNull(res);
        assertEquals(2L, res.getTotal());
        assertEquals(1L, res.getOverScore());
        assertEquals(1L, res.getUnderScore());

        // CheckDB
        verify(studentQuizRepository, times(1)).filter(anyLong(), anyLong(), any(), any(), any());

        // Rollback: mock
    }

    // TC-EXAM-002
    @Test
    public void should_ThrowException_When_statisticDetailQuizInClass_UserNotFound() {
        // Arrange
        when(jwtUtil.getEmailFromToken(any())).thenReturn("user@gmail.com");
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        // Act & Assert
        WebToeicException ex = assertThrows(WebToeicException.class, () -> {
            quizService.statisticDetailQuizInClass(request, 1L, 1L, 5L, new SearchSubmittedDto());
        });
        assertEquals(ResponseCode.NOT_EXISTED, ex.getResponseCode());

        // CheckDB
        verify(studentQuizRepository, never()).countOver(anyLong(), any(), anyLong());
    }

    // =========================================================================================
    // 2. statisticOverviewQuizInClass
    // =========================================================================================

    // TC-EXAM-003
    @Test
    public void test_statisticOverviewQuizInClass_Success() {
        // Arrange
        mockAuth(teacherUser);
        SearchQuizDto dto = new SearchQuizDto();
        when(shareQuizRepository.filter(dto, 1L)).thenReturn(List.of(new SharedQuiz(), new SharedQuiz()));
        when(shareQuizRepository.statisticOverviewOverScoreQuizInClass(1L, dto, 5L)).thenReturn(1L);
        
        // Act
        OverviewResponse res = quizService.statisticOverviewQuizInClass(request, 1L, 5L, dto);
        
        // Assert
        assertNotNull(res);
        assertEquals(2L, res.getTotal());

        // CheckDB
        verify(shareQuizRepository, times(1)).filter(any(), anyLong());

        // Rollback: mock
    }

    // TC-EXAM-004
    @Test
    public void should_ThrowException_When_statisticOverviewQuizInClass_UserNotFound() {
        // Arrange
        when(jwtUtil.getEmailFromToken(any())).thenReturn("user@gmail.com");
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(WebToeicException.class, () -> {
            quizService.statisticOverviewQuizInClass(request, 1L, 5L, new SearchQuizDto());
        });
    }

    // =========================================================================================
    // 3. getDetailSubmitQuiz
    // =========================================================================================

    // TC-EXAM-005
    @Test
    public void test_getDetailSubmitQuiz_Success() {
        // Arrange
        mockAuth(studentUser);
        when(studentQuizRepository.findById(1L)).thenReturn(Optional.of(new StudentQuiz()));
        when(convertUtil.convertSubmitToDto(any(), any(), eq(false))).thenReturn(new SubmitResponse());
        
        // Act
        SubmitResponse res = quizService.getDetailSubmitQuiz(request, 1L);
        
        // Assert
        assertNotNull(res);
        verify(studentQuizRepository, times(1)).findById(1L);
    }

    // TC-EXAM-006
    @Test
    public void should_ThrowException_When_getDetailSubmitQuiz_SubmitNotFound() {
        // Arrange
        mockAuth(studentUser);
        when(studentQuizRepository.findById(1L)).thenReturn(Optional.empty());

        // Act & Assert
        WebToeicException ex = assertThrows(WebToeicException.class, () -> {
            quizService.getDetailSubmitQuiz(request, 1L);
        });
        assertEquals(ResponseCode.NOT_EXISTED, ex.getResponseCode());
    }

    // =========================================================================================
    // 4. getListSubmitQuiz
    // =========================================================================================

    // TC-EXAM-007
    @Test
    public void test_getListSubmitQuiz_AsStudent_Success() {
        // Arrange
        mockAuth(studentUser);
        when(classMemberRepository.existsMemberInClass(1L, 3L)).thenReturn(true);
        Page<StudentQuiz> page = new PageImpl<>(List.of(new StudentQuiz()));
        when(studentQuizRepository.filter(anyLong(), anyLong(), any(), any(), eq("student@gmail.com"))).thenReturn(page);
        when(convertUtil.convertSubmitToDto(any(), any(), eq(true))).thenReturn(new SubmitResponse());
        
        // Act
        Page<SubmitResponse> res = quizService.getListSubmitQuiz(request, 1L, 1L, new SearchSubmittedDto(), PageRequest.of(0, 10));
        
        // Assert
        assertNotNull(res);
        verify(studentQuizRepository, times(1)).filter(anyLong(), anyLong(), any(), any(), eq("student@gmail.com"));
    }

    // TC-EXAM-008
    @Test
    public void test_getListSubmitQuiz_AsAdmin_Success() {
        // Arrange
        mockAuth(adminUser);
        Page<StudentQuiz> page = new PageImpl<>(List.of(new StudentQuiz()));
        when(studentQuizRepository.filter(anyLong(), anyLong(), any(), any(), isNull())).thenReturn(page);
        
        // Act
        Page<SubmitResponse> res = quizService.getListSubmitQuiz(request, 1L, 1L, new SearchSubmittedDto(), PageRequest.of(0, 10));
        
        // Assert
        assertNotNull(res);
    }

    // TC-EXAM-009
    @Test
    public void should_ThrowException_When_getListSubmitQuiz_StudentNotInClass() {
        // Arrange
        mockAuth(studentUser);
        when(classMemberRepository.existsMemberInClass(1L, 3L)).thenReturn(false);

        // Act & Assert
        WebToeicException ex = assertThrows(WebToeicException.class, () -> {
            quizService.getListSubmitQuiz(request, 1L, 1L, new SearchSubmittedDto(), PageRequest.of(0, 10));
        });
        assertEquals(ResponseCode.NOT_PERMISSION, ex.getResponseCode());
    }

    // =========================================================================================
    // 5. overviewStudentSubmitInClass
    // =========================================================================================

    // TC-EXAM-010
    @Test
    public void test_overviewStudentSubmitInClass_Admin_Success() {
        // Arrange
        mockAuth(adminUser);
        ClassMember member = new ClassMember();
        member.setMember(studentUser);
        when(classMemberRepository.findMembersInClass(any(SearchMemberInClassDto.class))).thenReturn(List.of(member));
        
        SharedQuiz sq = new SharedQuiz();
        Quiz q = new Quiz(); q.setId(1L); sq.setQuiz(q);
        when(shareQuizRepository.filter(any(), anyLong())).thenReturn(List.of(sq));

        StudentQuiz sub = new StudentQuiz(); sub.setQuiz(q); sub.setScore(new BigDecimal("10"));
        when(studentQuizRepository.findByUser_idAndClazz_id(3L, 1L)).thenReturn(List.of(sub));

        // Act
        Page<OverviewStudentSubmit> res = quizService.overviewStudentSubmitInClass(request, 1L, PageRequest.of(0, 10));
        
        // Assert
        assertNotNull(res);
        assertEquals(1, res.getContent().size());
    }

    // TC-EXAM-011
    @Test
    public void should_ThrowException_When_overviewStudentSubmitInClass_StudentRole() {
        // Arrange
        mockAuth(studentUser); // Role student not allowed

        // Act & Assert
        WebToeicException ex = assertThrows(WebToeicException.class, () -> {
            quizService.overviewStudentSubmitInClass(request, 1L, PageRequest.of(0, 10));
        });
        assertEquals(ResponseCode.NOT_PERMISSION, ex.getResponseCode());
    }

    // =========================================================================================
    // 6. submitQuiz
    // =========================================================================================

    // TC-EXAM-012
    @Test
    public void test_submitQuiz_Success() {
        // Arrange
        mockAuth(studentUser);
        Quiz quiz = new Quiz(); quiz.setTotalQuestions(10L);
        when(quizRepository.findById(1L)).thenReturn(Optional.of(quiz));
        when(classRepository.findById(1L)).thenReturn(Optional.of(new Class()));
        
        StudentQuiz sq = new StudentQuiz();
        when(studentQuizRepository.save(any(StudentQuiz.class))).thenReturn(sq);
        
        SubmitRequest req = new SubmitRequest();
        req.setQuestionId(1L); req.setAnswerId(1L); req.setStartAt(new Date()); req.setEndAt(new Date());
        
        when(questionRepository.findById(1L)).thenReturn(Optional.of(new Question()));
        Answer ans = new Answer(); ans.setIsCorrect(true);
        when(answerRepository.findById(1L)).thenReturn(Optional.of(ans));
        
        // Act
        quizService.submitQuiz(request, 1L, List.of(req), 1L, "des");
        
        // Assert
        verify(studentAnswerRepository, times(1)).save(any(StudentAnswer.class));

        // CheckDB
        ArgumentCaptor<StudentQuiz> captor = ArgumentCaptor.forClass(StudentQuiz.class);
        verify(studentQuizRepository, times(2)).save(captor.capture());
        assertNotNull(captor.getValue().getScore());
    }

    // TC-EXAM-013
    @Test
    public void should_ThrowException_When_submitQuiz_QuizNotFound() {
        // Arrange
        mockAuth(studentUser);
        when(quizRepository.findById(1L)).thenReturn(Optional.empty());

        // Act & Assert
        WebToeicException ex = assertThrows(WebToeicException.class, () -> {
            quizService.submitQuiz(request, 1L, new ArrayList<>(), 1L, "des");
        });
        assertEquals(ResponseCode.NOT_EXISTED, ex.getResponseCode());
    }

    // =========================================================================================
    // 7. getListQuizInClass
    // =========================================================================================

    // TC-EXAM-014
    @Test
    public void test_getListQuizInClass_Admin_Success() {
        // Arrange
        mockAuth(adminUser);
        Page<SharedQuiz> page = new PageImpl<>(List.of(new SharedQuiz()));
        when(shareQuizRepository.filter(any(), anyLong(), any())).thenReturn(page);
        when(convertUtil.convertShareQuizToDto(any(), any())).thenReturn(new ShareQuizResponse());
        
        // Act
        Page<ShareQuizResponse> res = quizService.getListQuizInClass(request, 1L, new SearchQuizDto(), PageRequest.of(0, 10));
        
        // Assert
        assertNotNull(res);
        verify(shareQuizRepository, times(1)).filter(any(), anyLong(), any());
    }

    // TC-EXAM-015
    @Test
    public void should_ThrowException_When_getListQuizInClass_StudentNotInClass() {
        // Arrange
        mockAuth(studentUser);
        when(classMemberRepository.existsMemberInClass(1L, 3L)).thenReturn(false);

        // Act & Assert
        assertThrows(WebToeicException.class, () -> {
            quizService.getListQuizInClass(request, 1L, new SearchQuizDto(), PageRequest.of(0, 10));
        });
    }

    // =========================================================================================
    // 8. updateQuizInClass
    // =========================================================================================

    // TC-EXAM-016
    @Test
    public void test_updateQuizInClass_Success() {
        // Arrange
        mockAuth(adminUser);
        SharedQuiz sq = new SharedQuiz();
        when(shareQuizRepository.findById(1L)).thenReturn(Optional.of(sq));
        
        SharedQuizRequest req = new SharedQuizRequest(); req.setSharedQuizId(1L); req.setIsActive(true);
        
        // Act
        quizService.updateQuizInClass(request, req);
        
        // Assert
        ArgumentCaptor<SharedQuiz> captor = ArgumentCaptor.forClass(SharedQuiz.class);
        verify(shareQuizRepository, times(1)).save(captor.capture());
        assertEquals(true, captor.getValue().getIsActive());
    }

    // TC-EXAM-017
    @Test
    public void should_ThrowException_When_updateQuizInClass_NotFound() {
        // Arrange
        mockAuth(adminUser);
        when(shareQuizRepository.findById(1L)).thenReturn(Optional.empty());

        SharedQuizRequest req = new SharedQuizRequest(); req.setSharedQuizId(1L);

        // Act & Assert
        assertThrows(WebToeicException.class, () -> {
            quizService.updateQuizInClass(request, req);
        });
    }

    // =========================================================================================
    // 9. pullQuizToClass
    // =========================================================================================

    // TC-EXAM-018
    @Test
    public void test_pullQuizToClass_Success_WithNoti() {
        // Arrange
        mockAuth(adminUser);
        when(quizRepository.findById(1L)).thenReturn(Optional.of(new Quiz()));
        Class c = new Class(); c.setId(1L);
        when(classRepository.findById(1L)).thenReturn(Optional.of(c));

        when(classMemberRepository.findMembersInClass(anyLong())).thenReturn(List.of(studentUser));
        
        SharedQuizRequest req = new SharedQuizRequest(); req.setQuizId(1L); req.setClassId(1L);
        
        // Act
        quizService.pullQuizToClass(request, req);
        
        // Assert
        ArgumentCaptor<SharedQuiz> captor = ArgumentCaptor.forClass(SharedQuiz.class);
        verify(shareQuizRepository, times(1)).save(captor.capture());
        assertNotNull(captor.getValue());

        verify(notiUtils, times(1)).sendNoti(anyList(), any(), anyString(), anyString(), anyLong());
    }

    // TC-EXAM-019
    @Test
    public void should_ThrowException_When_pullQuizToClass_QuizNotFound() {
        // Arrange
        mockAuth(adminUser);
        when(quizRepository.findById(1L)).thenReturn(Optional.empty());
        SharedQuizRequest req = new SharedQuizRequest(); req.setQuizId(1L); req.setClassId(1L);

        // Act & Assert
        assertThrows(WebToeicException.class, () -> {
            quizService.pullQuizToClass(request, req);
        });
    }

    // =========================================================================================
    // 10, 11, 12, 13, 14, 15, 16. Quiz CRUD & Bank Convert
    // =========================================================================================

    // TC-EXAM-020
    @Test
    public void test_getQuiz() {
        when(quizRepository.findById(1L)).thenReturn(Optional.of(new Quiz()));
        when(convertUtil.convertQuizToDto(any())).thenReturn(new QuizResponse());
        QuizResponse res = quizService.getQuiz(request, 1L);
        assertNotNull(res);
    }

    // TC-EXAM-021
    @Test
    public void should_ThrowException_When_getQuiz_NotFound() {
        when(quizRepository.findById(1L)).thenReturn(Optional.empty());
        assertThrows(WebToeicException.class, () -> {
            quizService.getQuiz(request, 1L);
        });
    }

    // TC-EXAM-022
    @Test
    public void test_getQuizes() {
        Page<Quiz> page = new PageImpl<>(List.of(new Quiz()));
        when(quizRepository.filter(any(), any())).thenReturn(page);
        when(convertUtil.convertQuizToDto(any())).thenReturn(new QuizResponse());
        Page<QuizResponse> res = quizService.getQuizes(request, new SearchQuizDto(), PageRequest.of(0, 10));
        assertNotNull(res);
    }

    // TC-EXAM-023
    @Test
    public void test_createQuiz() {
        mockAuth(adminUser);
        QuizRequest req = new QuizRequest(); req.setTitle("T"); req.setDescription("D");
        when(quizRepository.save(any(Quiz.class))).thenReturn(new Quiz());
        when(convertUtil.convertQuizToDto(any())).thenReturn(new QuizResponse());
        QuizResponse res = quizService.createQuiz(request, req);
        assertNotNull(res);
    }

    // TC-EXAM-024
    @Test
    public void test_updateQuiz() {
        mockAuth(adminUser);
        Quiz q = new Quiz();
        when(quizRepository.findById(1L)).thenReturn(Optional.of(q));
        QuizRequest req = new QuizRequest(); req.setId(1L); req.setTitle("N");
        when(quizRepository.save(any(Quiz.class))).thenReturn(q);
        when(convertUtil.convertQuizToDto(any())).thenReturn(new QuizResponse());
        QuizResponse res = quizService.updateQuiz(request, req);
        assertNotNull(res);
    }

    // TC-EXAM-025
    @Test
    public void test_addQuestionToQuiz_Success() {
        mockAuth(adminUser);
        Quiz q = new Quiz();
        when(quizRepository.findById(1L)).thenReturn(Optional.of(q));
        when(questionQuizRepository.countByQuizId(1L)).thenReturn(0L);
        when(questionRepository.findById(10L)).thenReturn(Optional.of(new Question()));
        when(quizRepository.save(any(Quiz.class))).thenReturn(q);
        
        QuizRequest req = new QuizRequest(); req.setId(1L); req.setIdQuestions(List.of(10L));
        quizService.addQuestionToQuiz(request, req);
        
        verify(questionQuizRepository, times(1)).save(any(QuestionQuiz.class));
    }

    // TC-EXAM-026
    @Test
    public void should_ThrowException_When_addQuestionToQuiz_LimitReached() {
        mockAuth(adminUser);
        Quiz q = new Quiz(); q.setTotalQuestions(1L);
        when(quizRepository.findById(1L)).thenReturn(Optional.of(q));
        when(questionQuizRepository.countByQuizId(1L)).thenReturn(1L); // limit reached
        
        QuizRequest req = new QuizRequest(); req.setId(1L); req.setIdQuestions(List.of(10L));
        
        assertThrows(WebToeicException.class, () -> {
            quizService.addQuestionToQuiz(request, req);
        });
    }

    // TC-EXAM-027
    @Test
    public void test_removeQuestionFromQuiz() {
        mockAuth(adminUser);
        Quiz q = new Quiz();
        when(quizRepository.findById(1L)).thenReturn(Optional.of(q));
        when(questionQuizRepository.countByQuizId(1L)).thenReturn(1L);
        when(quizRepository.save(any(Quiz.class))).thenReturn(q);
        
        QuizRequest req = new QuizRequest(); req.setId(1L); req.setIdQuestions(List.of(10L));
        quizService.removeQuestionFromQuiz(request, req);
        
        verify(questionQuizRepository, times(1)).deleteQuestionQuizByQuizIdAndQuestionId(1L, 10L);
    }

    // TC-EXAM-028
    @Test
    public void should_Fail_ToConvertBankToQuiz_When_BankHasNoQuestions() {
        mockAuth(teacherUser);
        QuestionBank bank = new QuestionBank();
        bank.setId(100L);
        bank.setTitle("Ngân hàng đề trống");
        when(questionBankRepository.findById(100L)).thenReturn(Optional.of(bank));
        when(questionRepository.findByQuestionBankId(100L)).thenReturn(new ArrayList<>());

        WebToeicException ex = assertThrows(WebToeicException.class, () -> {
            quizService.convertBankToQuiz(request, 100L);
        });
        assertEquals(ResponseCode.INVALID, ex.getResponseCode());
    }

    // TC-EXAM-029
    @Test
    public void test_convertBankToQuiz_Success() {
        mockAuth(teacherUser);
        QuestionBank bank = new QuestionBank();
        bank.setId(1L);
        bank.setTitle("Bank");
        when(questionBankRepository.findById(1L)).thenReturn(Optional.of(bank));
        Question q = new Question(); q.setId(10L);
        when(questionRepository.findByQuestionBankId(1L)).thenReturn(List.of(q));
        Quiz savedQuiz = new Quiz();
        when(quizRepository.save(any(Quiz.class))).thenReturn(savedQuiz);
        when(convertUtil.convertQuizToDto(any())).thenReturn(new QuizResponse());

        QuizResponse res = quizService.convertBankToQuiz(request, 1L);

        assertNotNull(res);
        verify(questionQuizRepository, times(1)).save(any(QuestionQuiz.class));
    }
}
