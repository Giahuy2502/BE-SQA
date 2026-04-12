package com.doan2025.webtoeic.utils;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimeUtilTest {

    // UTC-TU-001: Kiểm tra getCurrentTimestamp trả về thời gian hiện tại không null
    @Test
    void getCurrentTimestamp_shouldReturnNonNullDate() {
        // Given
        // (Không cần setup vì hàm không phụ thuộc input)

        // When
        Date now = TimeUtil.getCurrentTimestamp();

        // Then
        assertNotNull(now);
    }

    // UTC-TU-002: Kiểm tra getCurrentTimestamp nằm trong khoảng thời gian hợp lý so với thời điểm gọi
    @Test
    void getCurrentTimestamp_shouldBeWithinReasonableRange() {
        // Given
        // (Không dùng so sánh before/after quá chặt vì độ phân giải clock có thể khác nhau giữa Instant/Date)

        // When
        Date now = TimeUtil.getCurrentTimestamp();

        // Then: thời gian trả về phải "gần" thời điểm hiện tại (±5 giây)
        long diffSeconds = Math.abs(Instant.now().getEpochSecond() - now.toInstant().getEpochSecond());
        assertTrue(diffSeconds <= 5);
    }
}

