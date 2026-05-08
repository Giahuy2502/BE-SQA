package com.learnez.question;

import com.doan2025.webtoeic.constants.enums.ResponseCode;
import com.doan2025.webtoeic.domain.*;
import com.doan2025.webtoeic.dto.SearchQuestionDto;
import com.doan2025.webtoeic.dto.request.AnswerRequest;
import com.doan2025.webtoeic.dto.request.ExplanationQuestionRequest;
import com.doan2025.webtoeic.dto.request.QuestionRequest;
import com.doan2025.webtoeic.dto.response.*;
import com.doan2025.webtoeic.exception.WebToeicException;
import com.doan2025.webtoeic.repository.*;
import com.doan2025.webtoeic.service.AnswerService;
import com.doan2025.webtoeic.service.ExplanationQuestionService;
import com.doan2025.webtoeic.service.impl.QuestionServiceImpl;
import com.doan2025.webtoeic.utils.ConvertUtil;
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
import org.springframework.data.domain.PageRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class QuestionServiceTest {

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
    private ExplanationQuestionRepository explanationQuestionRepository;
    @Mock
    private QuestionBankRepository questionBankRepository;
    @Mock
    private RangeTopicRepository rangeTopicRepository;
    @Mock
    private ScoreScaleRepository scoreScaleRepository;
    @Mock
    private AnswerService answerService;
    @Mock
    private ExplanationQuestionService explanationQuestionService;

    @InjectMocks
    private QuestionServiceImpl questionService;

    @Mock
    private HttpServletRequest httpServletRequest;

    // TC-QUESTION-001: Lấy chi tiết Câu hỏi thành công
    @Test
    public void should_GetDetail() {
        // Arrange
        Question q = new Question();
        when(questionRepository.findById(1L)).thenReturn(Optional.of(q));
        when(convertUtil.convertQuestionToDto(q)).thenReturn(new QuestionResponse());

        // Act
        QuestionResponse res = questionService.getDetail(httpServletRequest, 1L);
        
        // Assert
        assertNotNull(res);

        // CheckDB
        verify(questionRepository, times(1)).findById(1L);

        // Rollback: Unit test sử dụng mock repository nên không làm thay đổi database thật
    }

    // TC-QUESTION-002: Lỗi khi lấy chi tiết Câu hỏi không tồn tại
    @Test
    public void should_ThrowException_GetDetail_NotFound() {
        // Arrange
        when(questionRepository.findById(1L)).thenReturn(Optional.empty());

        // Act & Assert
        WebToeicException ex = assertThrows(WebToeicException.class, () -> {
            questionService.getDetail(httpServletRequest, 1L);
        });
        assertEquals(ResponseCode.NOT_EXISTED, ex.getResponseCode());

        // CheckDB
        verify(questionRepository, times(1)).findById(1L);

        // Rollback: Unit test sử dụng mock repository nên không làm thay đổi database thật
    }

    // TC-QUESTION-003: Tìm kiếm danh sách Câu hỏi (không filter)
    @Test
    public void should_GetQuestionList_EmptyLists() {
        // Arrange
        SearchQuestionDto dto = new SearchQuestionDto();
        dto.setRangeTopics(new ArrayList<>());
        dto.setScoreScales(new ArrayList<>());

        PageRequest pageable = PageRequest.of(0, 10);
        Page<Question> page = new PageImpl<>(List.of(new Question()));

        when(questionRepository.filterQuestion(dto, pageable)).thenReturn(page);
        when(convertUtil.convertQuestionToDto(any(Question.class))).thenReturn(new QuestionResponse());

        // Act
        Page<QuestionResponse> result = questionService.getQuestionList(httpServletRequest, dto, pageable);

        // Assert
        assertEquals(1, result.getTotalElements());

        // CheckDB
        verify(questionRepository, times(1)).filterQuestion(dto, pageable);

        // Rollback: Unit test sử dụng mock repository nên không làm thay đổi database thật
    }

    // TC-QUESTION-004: Tìm kiếm danh sách Câu hỏi (có filter null)
    @Test
    public void should_GetQuestionList_NullLists() {
        // Arrange
        SearchQuestionDto dto = new SearchQuestionDto();
        dto.setRangeTopics(null);
        dto.setScoreScales(null);

        PageRequest pageable = PageRequest.of(0, 10);
        Page<Question> page = new PageImpl<>(List.of(new Question()));

        when(questionRepository.filterQuestion(dto, pageable)).thenReturn(page);
        when(convertUtil.convertQuestionToDto(any(Question.class))).thenReturn(new QuestionResponse());

        // Act
        Page<QuestionResponse> result = questionService.getQuestionList(httpServletRequest, dto, pageable);

        // Assert
        assertEquals(1, result.getTotalElements());

        // CheckDB
        verify(questionRepository, times(1)).filterQuestion(dto, pageable);

        // Rollback: Unit test sử dụng mock repository nên không làm thay đổi database thật
    }

    // TC-QUESTION-005: Tìm kiếm danh sách Câu hỏi (có filter)
    @Test
    public void test_getQuestionList_WithRangeTopics() {
        // Arrange
        SearchQuestionDto dto = new SearchQuestionDto();
        dto.setRangeTopics(List.of(1L, 2L));
        dto.setScoreScales(List.of(1L)); 
        Page<Question> page = new PageImpl<>(List.of(new Question()));
        when(questionRepository.filterQuestion(any(), any())).thenReturn(page);
        when(convertUtil.convertQuestionToDto(any())).thenReturn(new QuestionResponse());

        // Act
        Page<QuestionResponse> result = questionService.getQuestionList(httpServletRequest, dto, PageRequest.of(0, 10));
        
        // Assert
        assertNotNull(result);

        // CheckDB
        verify(questionRepository, times(1)).filterQuestion(any(), any());

        // Rollback: Unit test sử dụng mock repository nên không làm thay đổi database thật
    }

    // TC-QUESTION-006: Xóa mềm Câu hỏi khỏi Ngân hàng đề thành công
    @Test
    public void should_RemoveQuestionFromBank() {
        // Arrange
        User user = new User();
        when(jwtUtil.getEmailFromToken(any())).thenReturn("test@gmail.com");
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(user));

        QuestionBank bank = new QuestionBank();
        when(questionBankRepository.findById(1L)).thenReturn(Optional.of(bank));

        Question question = new Question();
        when(questionRepository.findById(10L)).thenReturn(Optional.of(question));
        when(convertUtil.convertQuestionBankToDto(bank)).thenReturn(new BankResponse());

        // Act
        BankResponse result = questionService.removeQuestionFromBank(httpServletRequest, List.of(10L), 1L);

        // Assert
        assertNotNull(result);

        // CheckDB
        ArgumentCaptor<Question> captor = ArgumentCaptor.forClass(Question.class);
        verify(questionRepository, times(1)).save(captor.capture());
        Question savedQuestion = captor.getValue();
        assertEquals(true, savedQuestion.getIsDelete());

        // Rollback: Unit test sử dụng mock repository nên không làm thay đổi database thật
    }

    // TC-QUESTION-007: Xóa mềm Câu hỏi thất bại (Không tìm thấy User)
    @Test
    public void should_ThrowException_When_RemoveQuestion_UserNotFound() {
        // Arrange
        when(jwtUtil.getEmailFromToken(any())).thenReturn("test@gmail.com");
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        // Act & Assert
        WebToeicException ex = assertThrows(WebToeicException.class, () -> {
            questionService.removeQuestionFromBank(httpServletRequest, List.of(10L), 1L);
        });
        assertEquals(ResponseCode.NOT_EXISTED, ex.getResponseCode());

        // CheckDB
        verify(questionRepository, never()).save(any());

        // Rollback: Unit test sử dụng mock repository nên không làm thay đổi database thật
    }

    // TC-QUESTION-008: Xóa mềm Câu hỏi thất bại (Không tìm thấy Bank)
    @Test
    public void should_ThrowException_When_RemoveQuestion_BankNotFound() {
        // Arrange
        when(jwtUtil.getEmailFromToken(any())).thenReturn("test@gmail.com");
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(new User()));
        when(questionBankRepository.findById(1L)).thenReturn(Optional.empty());

        // Act & Assert
        WebToeicException ex = assertThrows(WebToeicException.class, () -> {
            questionService.removeQuestionFromBank(httpServletRequest, List.of(10L), 1L);
        });
        assertEquals(ResponseCode.NOT_EXISTED, ex.getResponseCode());

        // CheckDB
        verify(questionRepository, never()).save(any());

        // Rollback: Unit test sử dụng mock repository nên không làm thay đổi database thật
    }

    // TC-QUESTION-009: Xóa mềm Câu hỏi thất bại (Không tìm thấy Question)
    @Test
    public void should_ThrowException_When_RemoveQuestion_QuestionNotFound() {
        // Arrange
        when(jwtUtil.getEmailFromToken(any())).thenReturn("test@gmail.com");
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(new User()));
        when(questionBankRepository.findById(1L)).thenReturn(Optional.of(new QuestionBank()));
        when(questionRepository.findById(10L)).thenReturn(Optional.empty());

        // Act & Assert
        WebToeicException ex = assertThrows(WebToeicException.class, () -> {
            questionService.removeQuestionFromBank(httpServletRequest, List.of(10L), 1L);
        });
        assertEquals(ResponseCode.NOT_EXISTED, ex.getResponseCode());

        // CheckDB
        verify(questionRepository, never()).save(any());

        // Rollback: Unit test sử dụng mock repository nên không làm thay đổi database thật
    }

    // TC-QUESTION-010: Cập nhật câu hỏi thành công
    @Test
    public void test_updateQuestion_WithExistingExplanation() {
        // Arrange
        when(jwtUtil.getEmailFromToken(any())).thenReturn("teacher@gmail.com");
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(new User()));
        
        Question question = new Question();
        when(questionRepository.findById(1L)).thenReturn(Optional.of(question));

        QuestionRequest req = new QuestionRequest();
        req.setId(1L);
        req.setQuestionContent("New content");
        req.setCategory("Grammar");
        req.setDifficulty("EASY");
        req.setActive(true);
        req.setDelete(false);
        req.setExplanation(new ExplanationQuestionRequest());
        req.getExplanation().setExplanationVietnamese("abc");
        req.setAnswers(List.of(new AnswerRequest()));

        // Act
        questionService.updateQuestion(httpServletRequest, req);
        
        // Assert
        verify(explanationQuestionService, times(1)).updateExplanationQuestion(any(), any());

        // CheckDB
        ArgumentCaptor<Question> captor = ArgumentCaptor.forClass(Question.class);
        verify(questionRepository, times(1)).save(captor.capture());
        Question savedQuestion = captor.getValue();
        assertEquals("New content", savedQuestion.getContent());

        // Rollback: Unit test sử dụng mock repository nên không làm thay đổi database thật
    }

    // TC-QUESTION-011: Lỗi cập nhật câu hỏi khi User không tồn tại
    @Test
    public void should_ThrowException_When_UpdateQuestion_UserNotFound() {
        // Arrange
        when(jwtUtil.getEmailFromToken(any())).thenReturn("teacher@gmail.com");
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        QuestionRequest req = new QuestionRequest();
        req.setId(1L);

        // Act & Assert
        WebToeicException ex = assertThrows(WebToeicException.class, () -> {
            questionService.updateQuestion(httpServletRequest, req);
        });
        assertEquals(ResponseCode.NOT_EXISTED, ex.getResponseCode());

        // CheckDB
        verify(questionRepository, never()).save(any());

        // Rollback: Unit test sử dụng mock repository nên không làm thay đổi database thật
    }

    // TC-QUESTION-012: Lỗi cập nhật câu hỏi khi Question không tồn tại
    @Test
    public void should_ThrowException_When_UpdateQuestion_QuestionNotFound() {
        // Arrange
        when(jwtUtil.getEmailFromToken(any())).thenReturn("teacher@gmail.com");
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(new User()));
        when(questionRepository.findById(1L)).thenReturn(Optional.empty());

        QuestionRequest req = new QuestionRequest();
        req.setId(1L);

        // Act & Assert
        WebToeicException ex = assertThrows(WebToeicException.class, () -> {
            questionService.updateQuestion(httpServletRequest, req);
        });
        assertEquals(ResponseCode.NOT_EXISTED, ex.getResponseCode());

        // CheckDB
        verify(questionRepository, never()).save(any());

        // Rollback: Unit test sử dụng mock repository nên không làm thay đổi database thật
    }

    // TC-QUESTION-013: Thêm câu hỏi từ AI Response thành công
    @Test
    public void should_SaveQuestion_AIResponse() {
        // Arrange
        User mockUser = new User();
        when(jwtUtil.getEmailFromToken(any())).thenReturn("test@gmail.com");
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(mockUser));

        AiResponse aiResponse = new AiResponse();
        aiResponse.setQuestionBankTitle("Ngân hàng Java");
        aiResponse.setUrl("http://example.com");

        QuestionResponse qr = new QuestionResponse();
        qr.setQuestionContent("OOP?");
        qr.setCategory("Java");
        qr.setDifficulty("EASY");
        
        ExplanationQuestionResponse exr = new ExplanationQuestionResponse();
        exr.setExplanationVietnamese("Đóng gói");
        qr.setExplanation(exr);

        AnswerResponse ar = new AnswerResponse();
        ar.setContent("Đa hình");
        ar.setCorrect(true);
        qr.setAnswers(List.of(ar));

        aiResponse.setQuestions(List.of(qr));

        QuestionBank savedBank = new QuestionBank();
        when(questionBankRepository.save(any(QuestionBank.class))).thenReturn(savedBank);
        when(questionRepository.save(any(Question.class))).thenReturn(new Question());
        when(convertUtil.convertQuestionBankToDto(any(QuestionBank.class))).thenReturn(new BankResponse());

        // Act
        BankResponse result = questionService.saveQuestion(httpServletRequest, aiResponse);

        // Assert
        assertNotNull(result);

        // CheckDB
        ArgumentCaptor<Question> captor = ArgumentCaptor.forClass(Question.class);
        verify(questionRepository, times(1)).save(captor.capture());
        verify(explanationQuestionRepository, times(1)).save(any(ExplanationQuestion.class));
        verify(answerRepository, times(1)).save(any(Answer.class));

        // Rollback: Unit test sử dụng mock repository nên không làm thay đổi database thật
    }

    // TC-QUESTION-014: Lỗi lưu câu hỏi AI khi User không tồn tại
    @Test
    public void should_ThrowException_When_SaveQuestionAI_UserNotFound() {
        // Arrange
        when(jwtUtil.getEmailFromToken(any())).thenReturn("notfound@gmail.com");
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        // Act & Assert
        WebToeicException ex = assertThrows(WebToeicException.class, () -> {
            questionService.saveQuestion(httpServletRequest, new AiResponse());
        });
        assertEquals(ResponseCode.NOT_EXISTED, ex.getResponseCode());

        // CheckDB
        verify(questionBankRepository, never()).save(any());

        // Rollback: Unit test sử dụng mock repository nên không làm thay đổi database thật
    }

    // TC-QUESTION-015: Lỗi thêm câu hỏi khi thiếu nội dung
    @Test
    public void should_Fail_ToAddQuestion_When_MissingRequiredContent() {
        // Arrange
        QuestionRequest req = new QuestionRequest();
        req.setQuestionContent(""); // Bỏ trống nội dung câu hỏi
        
        ExplanationQuestionRequest exReq = new ExplanationQuestionRequest();
        exReq.setExplanationVietnamese("vi");
        req.setExplanation(exReq);

        AnswerRequest ans = new AnswerRequest();
        ans.setContent("Đáp án A");
        ans.setCorrect(true);
        req.setAnswers(List.of(ans));

        User teacher = new User();
        when(jwtUtil.getEmailFromToken(any())).thenReturn("teacher@gmail.com");
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(teacher));

        QuestionBank qb = new QuestionBank();
        when(questionBankRepository.findById(1L)).thenReturn(Optional.of(qb));

        // Act & Assert
        WebToeicException ex = assertThrows(WebToeicException.class, () -> {
            questionService.addQuestionToBank(httpServletRequest, req, 1L);
        });
        assertEquals(ResponseCode.INVALID, ex.getResponseCode(), "Lỗi nghiệp vụ: Phải chặn việc tạo câu hỏi không có nội dung!");

        // CheckDB
        verify(questionRepository, never()).save(any(Question.class));

        // Rollback: Unit test sử dụng mock repository nên không làm thay đổi database thật
    }

    // TC-QUESTION-016: Thêm câu hỏi thành công
    @Test
    public void should_AddQuestionToBank_Success() {
        // Arrange
        QuestionRequest req = new QuestionRequest();
        req.setQuestionContent("Nội dung");
        
        ExplanationQuestionRequest exReq = new ExplanationQuestionRequest();
        exReq.setExplanationVietnamese("vi");
        req.setExplanation(exReq);

        AnswerRequest ans = new AnswerRequest();
        ans.setContent("Đáp án A");
        ans.setCorrect(true);
        req.setAnswers(List.of(ans));

        User teacher = new User();
        when(jwtUtil.getEmailFromToken(any())).thenReturn("teacher@gmail.com");
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(teacher));

        QuestionBank qb = new QuestionBank();
        when(questionBankRepository.findById(1L)).thenReturn(Optional.of(qb));
        
        Question q = new Question();
        when(questionRepository.save(any(Question.class))).thenReturn(q);
        when(convertUtil.convertQuestionBankToDto(any())).thenReturn(new BankResponse());

        // Act
        BankResponse res = questionService.addQuestionToBank(httpServletRequest, req, 1L);
        
        // Assert
        assertNotNull(res);

        // CheckDB
        verify(questionRepository, times(1)).save(any(Question.class));
        verify(explanationQuestionRepository, times(1)).save(any(ExplanationQuestion.class));

        // Rollback: Unit test sử dụng mock repository nên không làm thay đổi database thật
    }

    // TC-QUESTION-017: Lỗi thêm câu hỏi khi User không tồn tại
    @Test
    public void should_ThrowException_When_AddQuestion_UserNotFound() {
        // Arrange
        when(jwtUtil.getEmailFromToken(any())).thenReturn("teacher@gmail.com");
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        // Act & Assert
        WebToeicException ex = assertThrows(WebToeicException.class, () -> {
            questionService.addQuestionToBank(httpServletRequest, new QuestionRequest(), 1L);
        });
        assertEquals(ResponseCode.NOT_EXISTED, ex.getResponseCode());

        // CheckDB
        verify(questionRepository, never()).save(any());

        // Rollback: Unit test sử dụng mock repository nên không làm thay đổi database thật
    }

    // TC-QUESTION-018: Lỗi thêm câu hỏi khi Bank không tồn tại
    @Test
    public void should_ThrowException_When_AddQuestion_BankNotFound() {
        // Arrange
        when(jwtUtil.getEmailFromToken(any())).thenReturn("teacher@gmail.com");
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(new User()));
        when(questionBankRepository.findById(1L)).thenReturn(Optional.empty());

        // Act & Assert
        WebToeicException ex = assertThrows(WebToeicException.class, () -> {
            questionService.addQuestionToBank(httpServletRequest, new QuestionRequest(), 1L);
        });
        assertEquals(ResponseCode.NOT_EXISTED, ex.getResponseCode());

        // CheckDB
        verify(questionRepository, never()).save(any());

        // Rollback: Unit test sử dụng mock repository nên không làm thay đổi database thật
    }
}
