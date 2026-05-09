package com.learnez.ai;

import com.doan2025.webtoeic.domain.RangeTopic;
import com.doan2025.webtoeic.domain.ScoreScale;
import com.doan2025.webtoeic.dto.response.AiResponse;
import com.doan2025.webtoeic.dto.response.QuestionResponse;
import com.doan2025.webtoeic.repository.RangeTopicRepository;
import com.doan2025.webtoeic.repository.ScoreScaleRepository;
import com.doan2025.webtoeic.service.ReaderService;
import com.doan2025.webtoeic.service.impl.AIServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.ParameterizedTypeReference;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AIQuestionGeneratorServiceTest {

    @Mock
    private RangeTopicRepository rangeTopicRepository;
    @Mock
    private ScoreScaleRepository scoreScaleRepository;
    @Mock
    private ReaderService readerService;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ChatClient.Builder chatClientBuilder;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ChatClient chatClient;

    private AIServiceImpl aiService;

    @BeforeEach
    public void setup() {
        when(chatClientBuilder.build()).thenReturn(chatClient);
        aiService = new AIServiceImpl(chatClientBuilder, rangeTopicRepository, scoreScaleRepository, readerService);
    }

    // TC-AI-001: Gọi hàm Ping AI để kiểm tra kết nối thành công
    @Test
    public void should_ReturnAiResponse_When_CheckCallAI() {
        // Arrange
        when(chatClient.prompt(any(Prompt.class)).call().content()).thenReturn("AI is working");

        // Act
        String result = aiService.checkCallAI();

        // Assert
        assertEquals("AI is working", result);

        // CheckDB
        // Hàm này chỉ gọi ChatClient, không tương tác Database
        
        // Rollback: Unit test sử dụng mock repository nên không làm thay đổi database thật
    }

    // TC-AI-002: Đọc file PDF và sinh ra danh sách câu hỏi thành công thông qua AI
    @Test
    public void should_GenerateQuestion_When_Valid() {
        // Arrange
        String url = "http://example.com/test.pdf";
        when(readerService.readContentOfFile(url)).thenReturn("Test content");

        RangeTopic rt = new RangeTopic();
        rt.setContent("Java");
        RangeTopic rt2 = new RangeTopic();
        rt2.setContent("Python");
        when(rangeTopicRepository.findAll()).thenReturn(List.of(rt, rt2));

        ScoreScale ss = new ScoreScale();
        ss.setTitle("EASY");
        ScoreScale ss2 = new ScoreScale();
        ss2.setTitle("HARD");
        when(scoreScaleRepository.findAll()).thenReturn(List.of(ss, ss2));

        QuestionResponse qr = new QuestionResponse();
        qr.setQuestionContent("Test AI?");
        List<QuestionResponse> questions = List.of(qr);

        when(chatClient.prompt()
                .system(anyString())
                .user(anyString())
                .options(any(ChatOptions.class))
                .call()
                .entity(any(ParameterizedTypeReference.class))).thenReturn(questions);

        // Act
        AiResponse response = aiService.analysisWithAI(url);

        // Assert
        assertNotNull(response);
        assertEquals(url, response.getUrl());
        assertEquals(1, response.getQuestions().size());
        assertEquals("Test AI?", response.getQuestions().get(0).getQuestionContent());

        // CheckDB
        verify(rangeTopicRepository, times(1)).findAll();
        verify(scoreScaleRepository, times(1)).findAll();

        // Rollback: Unit test sử dụng mock repository nên không làm thay đổi database thật
    }

    // TC-AI-003: Reflection lấy ra chuỗi cấu hình cho AI (100% Coverage)
    @Test
    public void should_TestUnusedPrivateMethods() throws Exception {
        // Arrange
        Method getRangeTopicStringMethod = AIServiceImpl.class.getDeclaredMethod("getRangeTopicString");
        getRangeTopicStringMethod.setAccessible(true);
        
        Method getScoreScaleStringMethod = AIServiceImpl.class.getDeclaredMethod("getScoreScaleString");
        getScoreScaleStringMethod.setAccessible(true);

        // Act
        String rangeTopicResult = (String) getRangeTopicStringMethod.invoke(aiService);
        String scoreScaleResult = (String) getScoreScaleStringMethod.invoke(aiService);

        // Assert
        assertNotNull(rangeTopicResult);
        assertNotNull(scoreScaleResult);

        // CheckDB
        // Hàm private này xử lý mapping string, không trực tiếp gọi Database

        // Rollback: Unit test sử dụng mock repository nên không làm thay đổi database thật
    }
}
