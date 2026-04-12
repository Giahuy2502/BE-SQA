package com.doan2025.webtoeic.config;

import com.doan2025.webtoeic.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * TCID nhóm: UTC-DI-*
 * Kiểm thử {@link DataInitializer#run} với repository mock — không ghi DB thật.
 */
@ExtendWith(MockitoExtension.class)
class DataInitializerTest {

    @Mock
    private CheckInitRepository checkInitRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private ManagerRepository managerRepository;
    @Mock
    private ConsultantRepository consultantRepository;
    @Mock
    private TeacherRepository teacherRepository;
    @Mock
    private StudentRepository studentRepository;
    @Mock
    private PostRepository postRepository;
    @Mock
    private CourseRepository courseRepository;
    @Mock
    private LessonRepository lessonRepository;
    @Mock
    private ScoreScaleRepository scoreScaleRepository;
    @Mock
    private RangeTopicRepository topicRepository;

    @InjectMocks
    private DataInitializer dataInitializer;

    /**
     * TCID: UTC-DI-001
     * Khi mọi mã init đã tồn tại, không seed user / topic / score scale.
     */
    @Test
    void run_whenAllInitCodesExist_shouldNotSaveSeedData() {
        when(checkInitRepository.existsByCode(anyString())).thenReturn(true);

        assertDoesNotThrow(() -> dataInitializer.run());

        verify(userRepository, never()).save(any());
        verify(topicRepository, never()).save(any());
        verify(scoreScaleRepository, never()).save(any());
    }
}
