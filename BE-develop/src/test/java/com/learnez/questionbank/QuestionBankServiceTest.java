package com.learnez.questionbank;

import com.doan2025.webtoeic.constants.enums.ResponseCode;
import com.doan2025.webtoeic.domain.QuestionBank;
import com.doan2025.webtoeic.domain.User;
import com.doan2025.webtoeic.dto.SearchBankDto;
import com.doan2025.webtoeic.dto.request.BankRequest;
import com.doan2025.webtoeic.dto.response.BankResponse;
import com.doan2025.webtoeic.exception.WebToeicException;
import com.doan2025.webtoeic.repository.QuestionBankRepository;
import com.doan2025.webtoeic.repository.UserRepository;
import com.doan2025.webtoeic.service.impl.QuestionBankServiceImpl;
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

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class QuestionBankServiceTest {

    @Mock
    private QuestionBankRepository questionBankRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private ConvertUtil convertUtil;
    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private QuestionBankServiceImpl questionBankService;

    // TC-BANK-001: Lấy chi tiết Ngân hàng đề thành công
    @Test
    public void should_GetQuestionBank_When_Found() {
        // Arrange
        QuestionBank qb = new QuestionBank();
        when(questionBankRepository.findById(1L)).thenReturn(Optional.of(qb));
        BankResponse br = new BankResponse();
        when(convertUtil.convertQuestionBankToDto(qb)).thenReturn(br);

        // Act
        BankResponse result = questionBankService.getQuestionBank(request, 1L);

        // Assert
        assertNotNull(result);

        // CheckDB
        verify(questionBankRepository, times(1)).findById(1L);

        // Rollback: Unit test sử dụng mock repository nên không làm thay đổi database thật
    }

    // TC-BANK-002: Lỗi khi lấy chi tiết Ngân hàng đề không tồn tại
    @Test
    public void should_ThrowException_When_GetQuestionBank_NotFound() {
        // Arrange
        when(questionBankRepository.findById(1L)).thenReturn(Optional.empty());

        // Act & Assert
        WebToeicException ex = assertThrows(WebToeicException.class, () -> {
            questionBankService.getQuestionBank(request, 1L);
        });
        assertEquals(ResponseCode.NOT_EXISTED, ex.getResponseCode());

        // CheckDB
        verify(questionBankRepository, times(1)).findById(1L);

        // Rollback: Unit test sử dụng mock repository nên không làm thay đổi database thật
    }

    // TC-BANK-003: Tìm kiếm danh sách Ngân hàng đề thành công
    @Test
    public void should_GetQuestionBanks() {
        // Arrange
        SearchBankDto dto = new SearchBankDto();
        PageRequest pageable = PageRequest.of(0, 10);
        QuestionBank qb = new QuestionBank();
        Page<QuestionBank> page = new PageImpl<>(List.of(qb));
        
        when(questionBankRepository.filter(dto, pageable)).thenReturn(page);
        when(convertUtil.convertQuestionBankToDto(qb)).thenReturn(new BankResponse());

        // Act
        Page<BankResponse> result = questionBankService.getQuestionBanks(request, dto, pageable);

        // Assert
        assertEquals(1, result.getTotalElements());

        // CheckDB
        verify(questionBankRepository, times(1)).filter(dto, pageable);

        // Rollback: Unit test sử dụng mock repository nên không làm thay đổi database thật
    }

    // TC-BANK-004: Quản lý/Giáo viên tạo mới Ngân hàng đề thành công
    @Test
    public void should_SaveQuestionBank() {
        // Arrange
        BankRequest req = new BankRequest();
        req.setQuestionBankTitle("Title");
        req.setUrl("URL");

        User user = new User();
        when(jwtUtil.getEmailFromToken(request)).thenReturn("test@gmail.com");
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(user));

        QuestionBank saved = new QuestionBank();
        when(questionBankRepository.save(any(QuestionBank.class))).thenReturn(saved);
        when(convertUtil.convertQuestionBankToDto(saved)).thenReturn(new BankResponse());

        // Act
        BankResponse res = questionBankService.saveQuestionBank(request, req);

        // Assert
        assertNotNull(res);

        // CheckDB
        ArgumentCaptor<QuestionBank> captor = ArgumentCaptor.forClass(QuestionBank.class);
        verify(questionBankRepository, times(1)).save(captor.capture());
        QuestionBank capturedBank = captor.getValue();
        assertEquals("Title", capturedBank.getTitle());

        // Rollback: Unit test sử dụng mock repository nên không làm thay đổi database thật
    }

    // TC-BANK-005: Cập nhật Ngân hàng đề thành công
    @Test
    public void should_UpdateQuestionBank() {
        // Arrange
        BankRequest req = new BankRequest();
        req.setId(1L);
        req.setQuestionBankTitle("New Title");
        req.setUrl("New URL");
        req.setIsActive(true);
        req.setIsDeleted(false);

        User user = new User();
        when(jwtUtil.getEmailFromToken(request)).thenReturn("test@gmail.com");
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(user));

        QuestionBank qb = new QuestionBank();
        when(questionBankRepository.findById(1L)).thenReturn(Optional.of(qb));
        when(questionBankRepository.save(any(QuestionBank.class))).thenReturn(qb);
        when(convertUtil.convertQuestionBankToDto(qb)).thenReturn(new BankResponse());

        // Act
        BankResponse res = questionBankService.updateQuestionBank(request, req);

        // Assert
        assertNotNull(res);

        // CheckDB
        ArgumentCaptor<QuestionBank> captor = ArgumentCaptor.forClass(QuestionBank.class);
        verify(questionBankRepository, times(1)).save(captor.capture());
        QuestionBank capturedBank = captor.getValue();
        assertEquals("New Title", capturedBank.getTitle());

        // Rollback: Unit test sử dụng mock repository nên không làm thay đổi database thật
    }
    
    // TC-BANK-006: Lỗi khi User không được phép (Học viên) tạo ngân hàng đề
    @Test
    public void should_Fail_ToCreateQuestionBank_When_UserIsStudent() {
        // Arrange
        BankRequest req = new BankRequest();
        req.setQuestionBankTitle("Ngân hàng đề TOEIC");

        User studentUser = new User();
        when(jwtUtil.getEmailFromToken(request)).thenReturn("student@gmail.com");
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(studentUser));

        // Act & Assert
        WebToeicException ex = assertThrows(WebToeicException.class, () -> {
            questionBankService.saveQuestionBank(request, req);
        });
        assertEquals(ResponseCode.UNAUTHORIZED, ex.getResponseCode(), "Lỗi bảo mật: Học sinh không được phép tạo ngân hàng đề!");

        // CheckDB
        verify(questionBankRepository, never()).save(any(QuestionBank.class));

        // Rollback: Unit test sử dụng mock repository nên không làm thay đổi database thật
    }

    // TC-BANK-007: Lỗi khi tạo ngân hàng đề thiếu tiêu đề bắt buộc
    @Test
    public void should_Fail_ToCreateQuestionBank_When_MissingTitle() {
        // Arrange
        BankRequest req = new BankRequest();
        req.setQuestionBankTitle(""); // Bỏ trống trường bắt buộc

        User validUser = new User();
        when(jwtUtil.getEmailFromToken(request)).thenReturn("teacher@gmail.com");
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(validUser));

        // Act & Assert
        WebToeicException ex = assertThrows(WebToeicException.class, () -> {
            questionBankService.saveQuestionBank(request, req);
        });
        assertEquals(ResponseCode.INVALID, ex.getResponseCode(), "Lỗi nghiệp vụ: Phải validate thông tin bắt buộc!");

        // CheckDB
        verify(questionBankRepository, never()).save(any(QuestionBank.class));

        // Rollback: Unit test sử dụng mock repository nên không làm thay đổi database thật
    }

    // TC-BANK-008: Lỗi khi cập nhật Ngân hàng đề nhưng User không tồn tại
    @Test
    public void should_ThrowException_When_UpdateQuestionBank_UserNotFound() {
        // Arrange
        BankRequest req = new BankRequest();
        req.setId(1L);

        when(jwtUtil.getEmailFromToken(any())).thenReturn("user@gmail.com");
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        // Act & Assert
        WebToeicException ex = assertThrows(WebToeicException.class, () -> {
            questionBankService.updateQuestionBank(request, req);
        });
        assertEquals(ResponseCode.NOT_EXISTED, ex.getResponseCode());

        // CheckDB
        verify(questionBankRepository, never()).save(any());

        // Rollback: Unit test sử dụng mock repository nên không làm thay đổi database thật
    }

    // TC-BANK-009: Lỗi khi cập nhật Ngân hàng đề không tồn tại
    @Test
    public void should_ThrowException_When_UpdateQuestionBank_BankNotFound() {
        // Arrange
        BankRequest req = new BankRequest();
        req.setId(1L);

        when(jwtUtil.getEmailFromToken(any())).thenReturn("user@gmail.com");
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(new User()));
        when(questionBankRepository.findById(1L)).thenReturn(Optional.empty());

        // Act & Assert
        WebToeicException ex = assertThrows(WebToeicException.class, () -> {
            questionBankService.updateQuestionBank(request, req);
        });
        assertEquals(ResponseCode.NOT_EXISTED, ex.getResponseCode());

        // CheckDB
        verify(questionBankRepository, never()).save(any());

        // Rollback: Unit test sử dụng mock repository nên không làm thay đổi database thật
    }

    // TC-BANK-010: Lỗi khi tạo Ngân hàng đề nhưng User không tồn tại
    @Test
    public void should_ThrowException_When_SaveQuestionBank_UserNotFound() {
        // Arrange
        BankRequest req = new BankRequest();
        req.setQuestionBankTitle("Title");
        req.setUrl("URL");

        when(jwtUtil.getEmailFromToken(request)).thenReturn("test@gmail.com");
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        // Act & Assert
        WebToeicException ex = assertThrows(WebToeicException.class, () -> {
            questionBankService.saveQuestionBank(request, req);
        });
        assertEquals(ResponseCode.NOT_EXISTED, ex.getResponseCode());

        // CheckDB
        verify(questionBankRepository, never()).save(any());

        // Rollback: Unit test sử dụng mock repository nên không làm thay đổi database thật
    }
}
