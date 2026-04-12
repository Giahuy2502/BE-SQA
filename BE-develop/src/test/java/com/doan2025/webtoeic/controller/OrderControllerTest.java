package com.doan2025.webtoeic.controller;

import com.doan2025.webtoeic.constants.enums.ResponseCode;
import com.doan2025.webtoeic.dto.SearchOrderDto;
import com.doan2025.webtoeic.dto.response.OrderResponse;
import com.doan2025.webtoeic.dto.response.StatisticOrderResponse;
import com.doan2025.webtoeic.service.OrderService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link WebMvcTest} slice for {@link OrderController}. {@link OrderService} is mocked; no real database.
 * <p>
 * Each test documents {@code Test Case ID}, behavior, {@code CheckDB} (verify service/repository contract via mocks),
 * and {@code Rollback} (N/A for this layer).
 */
@WebMvcTest(controllers = OrderController.class)
@AutoConfigureMockMvc(addFilters = false)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

    /**
     * Test Case ID: UTC-ORD-CTL-001
     * <p>
     * {@code GET /api/v1/order/statistic-orders} exposes learner-facing aggregates ({@link StatisticOrderResponse}).
     * <p>
     * CheckDB: {@code verify(orderService).getStatisticOrder(...)} proves the controller hits the service that queries orders.
     * Rollback: Not applicable — {@link MockBean} only.
     */
    @Test
    void statisticOrders_delegatesToService() throws Exception {
        StatisticOrderResponse stats = StatisticOrderResponse.builder()
                .totalOrders(BigDecimal.TEN)
                .totalPurchases(BigDecimal.ONE)
                .completedOrders(BigDecimal.ONE)
                .pendingOrders(BigDecimal.ZERO)
                .cancelledOrders(BigDecimal.ZERO)
                .build();
        when(orderService.getStatisticOrder(any())).thenReturn(stats);

        mockMvc.perform(get("/api/v1/order/statistic-orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResponseCode.GET_SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.totalOrders").value(10));

        verify(orderService).getStatisticOrder(any());
    }

    /**
     * Test Case ID: UTC-ORD-CTL-002
     * <p>
     * {@code POST /api/v1/order} binds {@link SearchOrderDto} from form fields and paginates owned orders.
     * <p>
     * CheckDB: {@code verify} {@link OrderService#getOwnOrders} — persistence assertions belong in service tests with repository mocks.
     * Rollback: Not applicable.
     */
    @Test
    void getOwnOrders_delegatesToService() throws Exception {
        when(orderService.getOwnOrders(any(), any(SearchOrderDto.class), any()))
                .thenReturn(new PageImpl<>(List.of(new OrderResponse()), PageRequest.of(0, 5), 1));

        mockMvc.perform(post("/api/v1/order")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("searchString", "toeic")
                        .param("page", "0")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResponseCode.GET_SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.content").isArray());

        verify(orderService).getOwnOrders(any(), any(SearchOrderDto.class), any());
    }

    /**
     * Test Case ID: UTC-ORD-CTL-003
     * <p>
     * {@code POST /api/v1/order/delete} cancels one or many orders; repeated {@code id} params become a {@link java.util.List}.
     * <p>
     * CheckDB: {@code verify(orderService).cancelOrder(..., eq(List.of(1L, 2L)))} ensures IDs flow to the transactional service.
     * Rollback: Not applicable in slice tests; production rollback is handled by {@code @Transactional} in the service layer.
     */
    @Test
    void deleteOrder_delegatesToCancelOrder() throws Exception {
        mockMvc.perform(post("/api/v1/order/delete")
                        .param("id", "1", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResponseCode.DELETE_SUCCESS.getCode()));

        verify(orderService).cancelOrder(any(), eq(List.of(1L, 2L)));
    }

    /**
     * Test Case ID: UTC-ORD-CTL-004
     * <p>
     * {@code POST /api/v1/order/create-order-by-cart-item} creates an order from a cart line id query parameter.
     * <p>
     * CheckDB: {@code verify(orderService).createOrderByCartItem(any(), eq(44L))} documents the expected persistence entry point.
     * Rollback: Not applicable — mocked service.
     */
    @Test
    void createOrderByCartItem_delegatesToService() throws Exception {
        OrderResponse dto = OrderResponse.builder().id(9L).build();
        when(orderService.createOrderByCartItem(any(), eq(44L))).thenReturn(dto);

        mockMvc.perform(post("/api/v1/order/create-order-by-cart-item")
                        .param("id", "44"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResponseCode.CREATE_SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.id").value(9));

        verify(orderService).createOrderByCartItem(any(), eq(44L));
    }

    /**
     * Test Case ID: UTC-ORD-CTL-005
     * <p>
     * {@code POST /api/v1/order/create-order-by-course} purchases a course directly via course id.
     * <p>
     * CheckDB: {@code verify(orderService).createOrderByCourseID(any(), eq(200L))} ties the HTTP contract to repository-backed logic.
     * Rollback: Not applicable in {@link WebMvcTest}.
     */
    @Test
    void createOrderByCourseId_delegatesToService() throws Exception {
        when(orderService.createOrderByCourseID(any(), eq(200L))).thenReturn(new OrderResponse());

        mockMvc.perform(post("/api/v1/order/create-order-by-course")
                        .param("id", "200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResponseCode.CREATE_SUCCESS.getCode()));

        verify(orderService).createOrderByCourseID(any(), eq(200L));
    }
}
