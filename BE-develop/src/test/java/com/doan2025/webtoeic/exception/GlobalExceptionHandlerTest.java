package com.doan2025.webtoeic.exception;

import com.doan2025.webtoeic.constants.enums.ResponseCode;
import com.doan2025.webtoeic.constants.enums.ResponseObject;
import com.doan2025.webtoeic.dto.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GlobalExceptionHandlerTest {

    // UTC-GE-001: Khi WebToeicException được ném -> handler trả đúng HTTP status và body code/message
    @Test
    void handlingWebToeicException_shouldReturnCorrectStatusAndBody() {
        // Given
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        WebToeicException ex = new WebToeicException(ResponseCode.NOT_EXISTED, ResponseObject.USER);

        // When
        @SuppressWarnings("rawtypes")
        ResponseEntity<ApiResponse> response = handler.handlingWebToeicException(ex);

        // Then
        assertEquals(ResponseCode.NOT_EXISTED.getStatusCode().value(), response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(ResponseCode.NOT_EXISTED.getCode(), response.getBody().getCode());
    }

    // UTC-GE-002: Khi AccessDeniedException -> handler trả UNAUTHORIZED
    @Test
    void handlingAccessDeniedException_shouldReturnUnauthorized() {
        // Given
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        AccessDeniedException ex = new AccessDeniedException("forbidden");

        // When
        @SuppressWarnings("rawtypes")
        ResponseEntity<ApiResponse> response = handler.handlingAccessDeniedException(ex);

        // Then
        assertEquals(ResponseCode.UNAUTHORIZED.getStatusCode().value(), response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(ResponseCode.UNAUTHORIZED.getCode(), response.getBody().getCode());
    }

    // UTC-GE-003: Khi RuntimeException/Exception chung -> handler trả UNCATEGORIZED_EXCEPTION và HTTP 400
    @Test
    void handlingRuntimeException_shouldReturnUncategorizedBadRequest() {
        // Given
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        RuntimeException ex = new RuntimeException("boom");

        // When
        @SuppressWarnings("rawtypes")
        ResponseEntity<ApiResponse> response = handler.handlingRuntimeException(ex);

        // Then
        assertEquals(400, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(ResponseCode.UNCATEGORIZED_EXCEPTION.getCode(), response.getBody().getCode());
    }

    // UTC-GE-005: WebToeicException với mã IS_NULL trả đúng HTTP status từ ResponseCode
    @Test
    void handlingWebToeicException_shouldUseStatusFromResponseCode_whenIsNull() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        WebToeicException ex = new WebToeicException(ResponseCode.IS_NULL, ResponseObject.TOKEN);

        @SuppressWarnings("rawtypes")
        ResponseEntity<ApiResponse> response = handler.handlingWebToeicException(ex);

        assertEquals(ResponseCode.IS_NULL.getStatusCode().value(), response.getStatusCode().value());
        assertEquals(ResponseCode.IS_NULL.getCode(), response.getBody().getCode());
    }

    // UTC-GE-004: Khi validation key không map được vào ResponseCode -> fallback INVALID_KEY
    @Test
    void handlingValidation_shouldReturnInvalidKey_whenEnumKeyUnknown() throws NoSuchMethodException {
        // Given
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        Method method = DummyValidationTarget.class.getDeclaredMethod("dummyMethod", String.class);
        MethodParameter methodParameter = new MethodParameter(method, 0);
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "obj");
        FieldError fieldError = new FieldError("obj", "field", "UNKNOWN_ENUM_KEY");
        bindingResult.addError(fieldError);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(methodParameter, bindingResult);

        // When
        @SuppressWarnings("rawtypes")
        ResponseEntity<ApiResponse> response = handler.handlingValidation(ex);

        // Then
        assertEquals(400, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(ResponseCode.INVALID_KEY.getCode(), response.getBody().getCode());
    }

    private static class DummyValidationTarget {
        @SuppressWarnings("unused")
        void dummyMethod(String value) {
        }
    }
}

