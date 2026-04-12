package com.doan2025.webtoeic.controller;

import com.doan2025.webtoeic.constants.Constants;
import com.doan2025.webtoeic.constants.enums.ResponseCode;
import com.doan2025.webtoeic.dto.response.PaymentResponse;
import com.doan2025.webtoeic.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.view.RedirectView;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * {@link WebMvcTest} slice for {@link PaymentController}. {@link PaymentService} is mocked — no VNPay or DB I/O.
 * <p>
 * Tests carry {@code Test Case ID}, narrative expectations, explicit {@code CheckDB} (mock verification),
 * and {@code Rollback} (N/A).
 */
@WebMvcTest(controllers = PaymentController.class)
@AutoConfigureMockMvc(addFilters = false)
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PaymentService paymentService;

    /**
     * Test Case ID: UTC-PAY-CTL-001
     * <p>
     * {@code GET /api/v1/payment/create?orderId=} should return {@link ResponseCode#SUCCESS} and expose the payment URL as
     * {@code data.url} (Jackson lowercases {@link com.doan2025.webtoeic.dto.response.PaymentResponse#getURL} to {@code url}).
     * <p>
     * CheckDB: {@code verify(paymentService).createVNPayPayment(eq(12L), any())} ensures order id + request reach persistence-capable code.
     * Rollback: Not applicable — payment side effects are mocked away here.
     */
    @Test
    void createVNPayPayment_delegatesToService() throws Exception {
        PaymentResponse paymentResponse = PaymentResponse.builder()
                .status(Constants.SUCCESS)
                .URL("https://pay.test/url")
                .message("ok")
                .build();
        when(paymentService.createVNPayPayment(eq(12L), any())).thenReturn(paymentResponse);

        mockMvc.perform(get("/api/v1/payment/create").param("orderId", "12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResponseCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.url").value("https://pay.test/url"));

        verify(paymentService).createVNPayPayment(eq(12L), any());
    }

    /**
     * Test Case ID: UTC-PAY-CTL-002
     * <p>
     * {@code GET /api/v1/payment/return} is the VNPay return URL; controller should {@code 302} to the {@link RedirectView} URL from the service.
     * <p>
     * CheckDB: {@code verify(paymentService).handleVNPayReturn(any())} documents that DB updates (order status, etc.) stay inside the service.
     * Rollback: Not applicable — slice test without datasource; integration tests would assert transactional boundaries.
     */
    @Test
    void handleVNPayReturn_delegatesToService_andRedirects() throws Exception {
        RedirectView redirect = new RedirectView();
        redirect.setUrl("http://localhost:4200/order-status?status=success");
        when(paymentService.handleVNPayReturn(any())).thenReturn(redirect);

        mockMvc.perform(get("/api/v1/payment/return")
                        .param(Constants.PAYMENT.VNP_RESPONSE_CODE, "00")
                        .param(Constants.PAYMENT.VNP_TXN_REF, "x_1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "http://localhost:4200/order-status?status=success"));

        verify(paymentService).handleVNPayReturn(any());
    }
}
