package com.learnez.questionbank;

import com.doan2025.webtoeic.constants.enums.ResponseCode;
import com.doan2025.webtoeic.domain.ScoreScale;
import com.doan2025.webtoeic.dto.SearchRangeTopicAndScoreScaleDto;
import com.doan2025.webtoeic.dto.request.ScoreScaleRequest;
import com.doan2025.webtoeic.dto.response.ScoreScaleResponse;
import com.doan2025.webtoeic.exception.WebToeicException;
import com.doan2025.webtoeic.repository.ScoreScaleRepository;
import com.doan2025.webtoeic.service.impl.ScoreScaleServiceImpl;
import com.doan2025.webtoeic.utils.ConvertUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ScoreScaleServiceTest {

    @Mock
    private ScoreScaleRepository scoreScaleRepository;

    @Mock
    private ConvertUtil convertUtil;

    @Mock
    private HttpServletRequest httpServletRequest;

    @InjectMocks
    private ScoreScaleServiceImpl scoreScaleService;

    // TC-SCALE-001: Lấy chi tiết Mức độ thành công
    @Test
    public void should_GetScoreScale_When_Found() {
        // Arrange
        ScoreScale scale = new ScoreScale();
        scale.setId(1L);
        when(scoreScaleRepository.findById(1L)).thenReturn(Optional.of(scale));

        ScoreScaleResponse response = new ScoreScaleResponse();
        when(convertUtil.convertScoreScaleToDto(any(), eq(scale))).thenReturn(response);

        // Act
        ScoreScaleResponse result = scoreScaleService.getScoreScale(httpServletRequest, 1L);

        // Assert
        assertNotNull(result);

        // CheckDB
        verify(scoreScaleRepository, times(1)).findById(1L);

        // Rollback: Unit test sử dụng mock repository nên không làm thay đổi database
        // thật
    }

    // TC-SCALE-002: Lỗi lấy Mức độ không tồn tại
    @Test
    public void should_Fail_ToGetScoreScale_When_NotFound() {
        // Arrange
        when(scoreScaleRepository.findById(99L)).thenReturn(Optional.empty());

        // Act & Assert
        WebToeicException ex = assertThrows(WebToeicException.class, () -> {
            scoreScaleService.getScoreScale(httpServletRequest, 99L);
        });
        assertEquals(ResponseCode.NOT_EXISTED, ex.getResponseCode());

        // CheckDB
        verify(scoreScaleRepository, times(1)).findById(99L);

        // Rollback: Unit test sử dụng mock repository nên không làm thay đổi database
        // thật
    }

    // TC-SCALE-003: Lấy danh sách Mức độ
    @Test
    public void should_GetScoreScalesList_When_Valid() {
        // Arrange
        SearchRangeTopicAndScoreScaleDto dto = new SearchRangeTopicAndScoreScaleDto();
        Pageable pageable = Pageable.unpaged();

        ScoreScale scale = new ScoreScale();
        Page<ScoreScale> pages = new PageImpl<>(List.of(scale));
        when(scoreScaleRepository.filter(dto, pageable)).thenReturn(pages);

        ScoreScaleResponse response = new ScoreScaleResponse();
        when(convertUtil.convertScoreScaleToDto(any(), eq(scale))).thenReturn(response);

        // Act
        Page<ScoreScaleResponse> result = scoreScaleService.getScoreScales(httpServletRequest, dto, pageable);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());

        // CheckDB
        verify(scoreScaleRepository, times(1)).filter(dto, pageable);

        // Rollback: Unit test sử dụng mock repository nên không làm thay đổi database
        // thật
    }

    // TC-SCALE-004: Tạo Mức độ mới thành công
    @Test
    public void should_CreateScoreScale_When_ValidRequest() {
        // Arrange
        ScoreScaleRequest req = new ScoreScaleRequest();
        req.setTitle("Dễ");
        req.setFromScore(0);
        req.setToScore(3);

        ScoreScale savedScale = new ScoreScale();
        savedScale.setId(1L);
        when(scoreScaleRepository.save(any(ScoreScale.class))).thenReturn(savedScale);

        ScoreScaleResponse response = new ScoreScaleResponse();
        when(convertUtil.convertScoreScaleToDto(any(), eq(savedScale))).thenReturn(response);

        // Act
        ScoreScaleResponse result = scoreScaleService.createScoreScale(httpServletRequest, req);

        // Assert
        assertNotNull(result);

        // CheckDB
        ArgumentCaptor<ScoreScale> captor = ArgumentCaptor.forClass(ScoreScale.class);
        verify(scoreScaleRepository, times(1)).save(captor.capture());
        assertEquals("Dễ", captor.getValue().getTitle());
        assertEquals(0, captor.getValue().getFromScore());
        assertEquals(3, captor.getValue().getToScore());

        // Rollback: Unit test sử dụng mock repository nên không làm thay đổi database
        // thật
    }


    // TC-SCALE-006: Cập nhật Mức độ thành công
    @Test
    public void should_UpdateScoreScale_When_ValidRequest() {
        // Arrange
        ScoreScaleRequest req = new ScoreScaleRequest();
        req.setScoreScaleId(1L);
        req.setTitle("Khó");
        req.setFromScore(8);
        req.setToScore(10);

        ScoreScale scale = new ScoreScale();
        scale.setId(1L);
        when(scoreScaleRepository.findById(1L)).thenReturn(Optional.of(scale));
        when(scoreScaleRepository.save(any(ScoreScale.class))).thenReturn(scale);

        ScoreScaleResponse response = new ScoreScaleResponse();
        when(convertUtil.convertScoreScaleToDto(any(), eq(scale))).thenReturn(response);

        // Act
        ScoreScaleResponse result = scoreScaleService.updateScoreScale(req, httpServletRequest);

        // Assert
        assertNotNull(result);

        // CheckDB
        ArgumentCaptor<ScoreScale> captor = ArgumentCaptor.forClass(ScoreScale.class);
        verify(scoreScaleRepository, times(1)).save(captor.capture());
        assertEquals("Khó", captor.getValue().getTitle());
        assertEquals(8, captor.getValue().getFromScore());

        // Rollback: Unit test sử dụng mock repository nên không làm thay đổi database
        // thật
    }

    // TC-SCALE-006: Lỗi cập nhật Mức độ không tồn tại
    @Test
    public void should_Fail_ToUpdateScoreScale_When_NotFound() {
        // Arrange
        ScoreScaleRequest req = new ScoreScaleRequest();
        req.setScoreScaleId(99L);
        when(scoreScaleRepository.findById(99L)).thenReturn(Optional.empty());

        // Act & Assert
        WebToeicException ex = assertThrows(WebToeicException.class, () -> {
            scoreScaleService.updateScoreScale(req, httpServletRequest);
        });
        assertEquals(ResponseCode.NOT_EXISTED, ex.getResponseCode());

        // CheckDB
        verify(scoreScaleRepository, never()).save(any());

        // Rollback: Unit test sử dụng mock repository nên không làm thay đổi database
        // thật
    }
}
