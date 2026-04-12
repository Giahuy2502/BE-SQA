package com.doan2025.webtoeic.exception;

import com.doan2025.webtoeic.constants.enums.ResponseCode;
import com.doan2025.webtoeic.constants.enums.ResponseObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * TCID nhóm: UTC-WTE-*
 * Kiểm thử {@link WebToeicException} — exception nghiệp vụ của ứng dụng.
 */
class WebToeicExceptionTest {

    /**
     * TCID: UTC-WTE-001
     * Constructor 2 tham số gán đúng responseCode và responseObject.
     * Lưu ý: Lombok {@code @Getter} trên field {@code message} tạo {@code getMessage()} che
     * {@link Throwable#getMessage()} — không assert chuỗi từ {@code ex.getMessage()}.
     */
    @Test
    void constructor_shouldSetResponseCodeAndResponseObject() {
        WebToeicException ex = new WebToeicException(ResponseCode.NOT_EXISTED, ResponseObject.USER);

        assertEquals(ResponseCode.NOT_EXISTED, ex.getResponseCode());
        assertEquals(ResponseObject.USER, ex.getResponseObject());
    }

    /**
     * TCID: UTC-WTE-002
     * Builder Lombok tạo instance với các field tùy chỉnh.
     */
    @Test
    void builder_shouldPopulateOptionalFields() {
        WebToeicException ex = WebToeicException.builder()
                .responseCode(ResponseCode.IS_NULL)
                .responseObject(ResponseObject.ID)
                .message("custom")
                .build();

        assertEquals(ResponseCode.IS_NULL, ex.getResponseCode());
        assertEquals(ResponseObject.ID, ex.getResponseObject());
        assertEquals("custom", ex.getMessage());
    }
}
