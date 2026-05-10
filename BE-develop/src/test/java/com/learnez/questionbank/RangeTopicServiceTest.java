package com.learnez.questionbank;

import com.doan2025.webtoeic.constants.enums.ResponseCode;
import com.doan2025.webtoeic.domain.RangeTopic;
import com.doan2025.webtoeic.dto.SearchRangeTopicAndScoreScaleDto;
import com.doan2025.webtoeic.dto.request.RangeTopicRequest;
import com.doan2025.webtoeic.dto.response.RangeTopicResponse;
import com.doan2025.webtoeic.exception.WebToeicException;
import com.doan2025.webtoeic.repository.RangeTopicRepository;
import com.doan2025.webtoeic.service.impl.RangeTopicServiceImpl;
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
public class RangeTopicServiceTest {

    @Mock
    private RangeTopicRepository rangeTopicRepository;

    @Mock
    private ConvertUtil convertUtil;

    @Mock
    private HttpServletRequest httpServletRequest;

    @InjectMocks
    private RangeTopicServiceImpl rangeTopicService;

    // TC-TOPIC-001: Lấy chi tiết Chủ đề thành công
    @Test
    public void should_GetRangeTopic_When_Found() {
        // Arrange
        RangeTopic topic = new RangeTopic();
        topic.setId(1L);
        when(rangeTopicRepository.findById(1L)).thenReturn(Optional.of(topic));

        RangeTopicResponse response = new RangeTopicResponse();
        when(convertUtil.convertRangeTopicToDto(any(), eq(topic))).thenReturn(response);

        // Act
        RangeTopicResponse result = rangeTopicService.getRangeTopic(httpServletRequest, 1L);

        // Assert
        assertNotNull(result);

        // CheckDB
        verify(rangeTopicRepository, times(1)).findById(1L);

        // Rollback: Unit test sử dụng mock repository nên không làm thay đổi database
        // thật
    }

    // TC-TOPIC-002: Lỗi lấy chi tiết Chủ đề không tồn tại
    @Test
    public void should_Fail_ToGetRangeTopic_When_NotFound() {
        // Arrange
        when(rangeTopicRepository.findById(99L)).thenReturn(Optional.empty());

        // Act & Assert
        WebToeicException ex = assertThrows(WebToeicException.class, () -> {
            rangeTopicService.getRangeTopic(httpServletRequest, 99L);
        });
        assertEquals(ResponseCode.NOT_EXISTED, ex.getResponseCode());

        // CheckDB
        verify(rangeTopicRepository, times(1)).findById(99L);

        // Rollback: Unit test sử dụng mock repository nên không làm thay đổi database
        // thật
    }

    // TC-TOPIC-003: Lấy danh sách Chủ đề
    @Test
    public void should_GetRangeTopicsList_When_Valid() {
        // Arrange
        SearchRangeTopicAndScoreScaleDto dto = new SearchRangeTopicAndScoreScaleDto();
        Pageable pageable = Pageable.unpaged();

        RangeTopic topic = new RangeTopic();
        Page<RangeTopic> pages = new PageImpl<>(List.of(topic));
        when(rangeTopicRepository.filter(dto, pageable)).thenReturn(pages);

        RangeTopicResponse response = new RangeTopicResponse();
        when(convertUtil.convertRangeTopicToDto(any(), eq(topic))).thenReturn(response);

        // Act
        Page<RangeTopicResponse> result = rangeTopicService.getRangeTopics(httpServletRequest, dto, pageable);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());

        // CheckDB
        verify(rangeTopicRepository, times(1)).filter(dto, pageable);

        // Rollback: Unit test sử dụng mock repository nên không làm thay đổi database
        // thật
    }

    // TC-TOPIC-004: Tạo Chủ đề mới thành công
    @Test
    public void should_CreateRangeTopic_When_ValidRequest() {
        // Arrange
        RangeTopicRequest req = new RangeTopicRequest();
        req.setContent("Grammar");
        req.setDescription("Grammar rules");
        req.setVietnamese("Ngữ pháp");

        RangeTopic savedTopic = new RangeTopic();
        savedTopic.setId(1L);
        when(rangeTopicRepository.save(any(RangeTopic.class))).thenReturn(savedTopic);

        RangeTopicResponse response = new RangeTopicResponse();
        when(convertUtil.convertRangeTopicToDto(any(), eq(savedTopic))).thenReturn(response);

        // Act
        RangeTopicResponse result = rangeTopicService.createRangeTopic(httpServletRequest, req);

        // Assert
        assertNotNull(result);

        // CheckDB
        ArgumentCaptor<RangeTopic> captor = ArgumentCaptor.forClass(RangeTopic.class);
        verify(rangeTopicRepository, times(1)).save(captor.capture());
        assertEquals("Grammar", captor.getValue().getContent());
        assertEquals("Ngữ pháp", captor.getValue().getVietnamese());

        // Rollback: Unit test sử dụng mock repository nên không làm thay đổi database
        // thật
    }


    // TC-TOPIC-006: Cập nhật Chủ đề thành công
    @Test
    public void should_UpdateRangeTopic_When_ValidRequest() {
        // Arrange
        RangeTopicRequest req = new RangeTopicRequest();
        req.setRangeTopicId(1L);
        req.setContent("Updated Grammar");
        req.setVietnamese("Ngữ pháp mới");
        req.setIsActive(true);

        RangeTopic topic = new RangeTopic();
        topic.setId(1L);
        topic.setContent("Old Grammar");
        when(rangeTopicRepository.findById(1L)).thenReturn(Optional.of(topic));
        when(rangeTopicRepository.save(any(RangeTopic.class))).thenReturn(topic);

        RangeTopicResponse response = new RangeTopicResponse();
        when(convertUtil.convertRangeTopicToDto(any(), eq(topic))).thenReturn(response);

        // Act
        RangeTopicResponse result = rangeTopicService.updateRangeTopic(req, httpServletRequest);

        // Assert
        assertNotNull(result);

        // CheckDB
        ArgumentCaptor<RangeTopic> captor = ArgumentCaptor.forClass(RangeTopic.class);
        verify(rangeTopicRepository, times(1)).save(captor.capture());
        assertEquals("Updated Grammar", captor.getValue().getContent());
        assertEquals("Ngữ pháp mới", captor.getValue().getVietnamese());

        // Rollback: Unit test sử dụng mock repository nên không làm thay đổi database
        // thật
    }

    // TC-TOPIC-006: Lỗi cập nhật Chủ đề không tồn tại
    @Test
    public void should_Fail_ToUpdateRangeTopic_When_NotFound() {
        // Arrange
        RangeTopicRequest req = new RangeTopicRequest();
        req.setRangeTopicId(99L);
        when(rangeTopicRepository.findById(99L)).thenReturn(Optional.empty());

        // Act & Assert
        WebToeicException ex = assertThrows(WebToeicException.class, () -> {
            rangeTopicService.updateRangeTopic(req, httpServletRequest);
        });
        assertEquals(ResponseCode.NOT_EXISTED, ex.getResponseCode());

        // CheckDB
        verify(rangeTopicRepository, never()).save(any());

        // Rollback: Unit test sử dụng mock repository nên không làm thay đổi database
        // thật
    }
}
