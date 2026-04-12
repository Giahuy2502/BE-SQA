package com.doan2025.webtoeic.utils;

import com.doan2025.webtoeic.constants.Constants;
import com.doan2025.webtoeic.constants.enums.EGender;
import com.doan2025.webtoeic.domain.User;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class CommonUtilTest {

    // =========================
    // Nhóm test cho hmacSHA512
    // =========================

    // UTC-CU-001: Kiểm tra hmacSHA512 với key và data hợp lệ
    @Test
    void hmacSHA512_shouldReturnNonEmptyHex_whenKeyAndDataValid() {
        // Given: key và data hợp lệ
        String key = "secret-key";
        String data = "payload";

        // When: gọi hàm băm
        String result = CommonUtil.hmacSHA512(key, data);

        // Then: kết quả không null, không rỗng, và chỉ chứa ký tự hex
        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertTrue(result.matches("[0-9a-f]+"));
    }

    // UTC-CU-002: Kiểm tra hmacSHA512 khi key = null
    @Test
    void hmacSHA512_shouldReturnEmptyString_whenKeyNull() {
        // Given
        String key = null;
        String data = "payload";

        // When
        String result = CommonUtil.hmacSHA512(key, data);

        // Then
        assertEquals("", result);
    }

    // UTC-CU-003: Kiểm tra hmacSHA512 khi data = null
    @Test
    void hmacSHA512_shouldReturnEmptyString_whenDataNull() {
        // Given
        String key = "secret-key";
        String data = null;

        // When
        String result = CommonUtil.hmacSHA512(key, data);

        // Then
        assertEquals("", result);
    }

    // ============================
    // Nhóm test cho getIpAddress
    // ============================

    // UTC-CU-004: Lấy IP từ header X-FORWARDED-FOR khi tồn tại
    @Test
    void getIpAddress_shouldReturnForwardedIp_whenHeaderPresent() {
        // Given
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        when(request.getHeader("X-FORWARDED-FOR")).thenReturn("1.2.3.4");

        // When
        String ip = CommonUtil.getIpAddress(request);

        // Then
        assertEquals("1.2.3.4", ip);
    }

    // UTC-CU-005: Lấy IP từ remoteAddr khi header không tồn tại
    @Test
    void getIpAddress_shouldReturnRemoteAddr_whenHeaderMissing() {
        // Given
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        when(request.getHeader("X-FORWARDED-FOR")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("5.6.7.8");

        // When
        String ip = CommonUtil.getIpAddress(request);

        // Then
        assertEquals("5.6.7.8", ip);
    }

    // UTC-CU-006: Trả về chuỗi lỗi khi xảy ra exception trong quá trình đọc IP
    @Test
    void getIpAddress_shouldReturnErrorMessage_whenExceptionThrown() {
        // Given: mock ném RuntimeException khi gọi getHeader
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        when(request.getHeader("X-FORWARDED-FOR")).thenThrow(new RuntimeException("boom"));

        // When
        String ip = CommonUtil.getIpAddress(request);

        // Then
        assertTrue(ip.startsWith("Invalid IP:"));
    }

    // =====================================
    // Nhóm test cho replaceValueResetPassword
    // =====================================

    // UTC-CU-007: Thay thế username và OTP đúng trong template email reset password
    @Test
    void replaceValueResetPassword_shouldReplaceUsernameAndOtpCorrectly() {
        // Given
        User user = new User();
        user.setFirstName("Nguyen");
        user.setLastName("An");
        Integer otp = 123456;

        // When
        String body = CommonUtil.replaceValueResetPassword(user, otp);

        // Then: nội dung phải thay thế đúng theo template thật trong Constants
        assertNotNull(body);
        assertFalse(body.contains(Constants.USERNAME)); // "username" không được còn nguyên trong body
        assertFalse(body.contains(Constants.OTP_CODE)); // "OTP_Code" không được còn nguyên trong body
        assertTrue(body.contains("Nguyen An"));
        assertTrue(body.contains(String.valueOf(otp)));
    }

    // ============================
    // Nhóm test cho otpGenerator
    // ============================

    // UTC-CU-008: OTP luôn nằm trong khoảng 100000–999999
    @Test
    void otpGenerator_shouldReturnSixDigitNumberInRange() {
        // Given + When
        Integer otp = CommonUtil.otpGenerator();

        // Then
        assertNotNull(otp);
        assertTrue(otp >= 100_000 && otp <= 999_999);
    }

    // =================================
    // Nhóm test cho parseDate (String->Date)
    // =================================

    // UTC-CU-009: parseDate trả null khi input null hoặc rỗng
    @Test
    void parseDate_shouldReturnNull_whenInputNullOrBlank() {
        // Given
        String nullDate = null;
        String blankDate = "   ";

        // When
        Date resultNull = CommonUtil.parseDate(nullDate);
        Date resultBlank = CommonUtil.parseDate(blankDate);

        // Then
        assertNull(resultNull);
        assertNull(resultBlank);
    }

    // UTC-CU-010: parseDate parse thành công với format yyyy-MM-dd hợp lệ
    @Test
    void parseDate_shouldReturnDate_whenInputValidFormat() {
        // Given
        String dateStr = "2024-12-31";

        // When
        Date result = CommonUtil.parseDate(dateStr);

        // Then
        assertNotNull(result);
    }

    // UTC-CU-011: parseDate ném IllegalArgumentException khi format sai
    @Test
    void parseDate_shouldThrowIllegalArgumentException_whenFormatInvalid() {
        // Given
        String invalid = "31-12-2024";

        // When + Then
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> CommonUtil.parseDate(invalid)
        );
        assertTrue(ex.getMessage().contains("Định dạng ngày không hợp lệ"));
    }

    // ============================================
    // Nhóm test cho convertIntegerToEGender (Integer -> Enum)
    // ============================================

    // UTC-CU-012: convertIntegerToEGender trả null khi input null
    @Test
    void convertIntegerToEGender_shouldReturnNull_whenInputNull() {
        // Given
        Integer num = null;

        // When
        EGender result = CommonUtil.convertIntegerToEGender(num);

        // Then
        assertNull(result);
    }

    // UTC-CU-013: convertIntegerToEGender trả MALE/FEMALE/OTHER với giá trị 1/2/3
    @Test
    void convertIntegerToEGender_shouldReturnCorrectEnum_whenInput123() {
        // Given + When + Then
        assertEquals(EGender.MALE, CommonUtil.convertIntegerToEGender(1));
        assertEquals(EGender.FEMALE, CommonUtil.convertIntegerToEGender(2));
        assertEquals(EGender.OTHER, CommonUtil.convertIntegerToEGender(3));
    }

    // UTC-CU-014: convertIntegerToEGender ném IllegalArgumentException với giá trị khác 1-3
    @Test
    void convertIntegerToEGender_shouldThrowIllegalArgumentException_whenValueOutOfRange() {
        // Given
        Integer invalid = 99;

        // When + Then
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> CommonUtil.convertIntegerToEGender(invalid)
        );
        assertTrue(ex.getMessage().contains("Giá trị giới tính không hợp lệ"));
    }
}

