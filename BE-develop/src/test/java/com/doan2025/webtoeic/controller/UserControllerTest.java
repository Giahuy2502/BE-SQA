package com.doan2025.webtoeic.controller;

import com.doan2025.webtoeic.constants.enums.ResponseCode;
import com.doan2025.webtoeic.dto.SearchBaseDto;
import com.doan2025.webtoeic.dto.request.UserRequest;
import com.doan2025.webtoeic.dto.response.UserResponse;
import com.doan2025.webtoeic.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link WebMvcTest} slice for {@link UserController}. {@link UserService} is a {@link MockBean} (no datasource).
 * <p>
 * Each test states {@code Test Case ID}, explains the scenario, and documents {@code CheckDB} / {@code Rollback}:
 * here “CheckDB” means verifying the correct service method runs (persistence is tested in service-layer tests).
 */
@WebMvcTest(controllers = UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    /**
     * Test Case ID: UTC-USR-CTL-001
     * <p>
     * {@code GET /api/v1/user} should return the standard JSON envelope with {@link ResponseCode#GET_SUCCESS}
     * and echo the mocked {@link UserResponse}.
     * <p>
     * CheckDB: {@code verify(userService).getUserCurrent(...)} ensures the controller delegates to the persistence layer entry point.
     * Rollback: Not applicable — {@link MockBean} service; no database in this slice.
     */
    @Test
    void getUserCurrent_delegatesToService_andReturnsEnvelope() throws Exception {
        UserResponse body = new UserResponse();
        body.setFirstName("Me");
        when(userService.getUserCurrent(any())).thenReturn(body);

        mockMvc.perform(get("/api/v1/user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResponseCode.GET_SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.firstName").value("Me"));

        verify(userService).getUserCurrent(any());
    }

    /**
     * Test Case ID: UTC-USR-CTL-002
     * <p>
     * {@code POST /api/v1/user/filter} accepts JSON {@link SearchBaseDto} plus {@code page}/{@code size} and should surface
     * a Spring Data page inside the success envelope.
     * <p>
     * CheckDB: {@code verify} {@link UserService#getListUserFilter} receives request, DTO, and {@link org.springframework.data.domain.Pageable}.
     * Rollback: Not applicable — mocked service only.
     */
    @Test
    void getListUserFilter_delegatesToService() throws Exception {
        SearchBaseDto dto = new SearchBaseDto();
        dto.setSearchString("ann");
        when(userService.getListUserFilter(any(), any(SearchBaseDto.class), any()))
                .thenReturn(new PageImpl<>(List.of(new UserResponse()), PageRequest.of(0, 10), 1));

        mockMvc.perform(post("/api/v1/user/filter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto))
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResponseCode.GET_SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.content").isArray());

        verify(userService).getListUserFilter(any(), any(SearchBaseDto.class), any());
    }

    /**
     * Test Case ID: UTC-USR-CTL-003
     * <p>
     * {@code POST /api/v1/user} loads admin-visible user details based on {@link UserRequest} payload.
     * <p>
     * CheckDB: {@code verify(userService).getUserDetails(...)} ensures the controller forwards the body to the service/repository stack.
     * Rollback: Not applicable — no datasource.
     */
    @Test
    void getUserDetails_delegatesToService() throws Exception {
        UserRequest detailRequest = UserRequest.builder().id(5L).build();
        UserResponse mockedUserResponse = new UserResponse();
        when(userService.getUserDetails(any(UserRequest.class))).thenReturn(mockedUserResponse);

        mockMvc.perform(post("/api/v1/user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(detailRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResponseCode.SUCCESS.getCode()));

        verify(userService).getUserDetails(any(UserRequest.class));
    }

    /**
     * Test Case ID: UTC-USR-CTL-004
     * <p>
     * {@code POST /api/v1/user/update-own-info} lets authenticated users patch their profile through {@link UserService#updateUserDetails}.
     * <p>
     * CheckDB: Verify both {@link jakarta.servlet.http.HttpServletRequest} and {@link UserRequest} reach the service (where repositories run).
     * Rollback: Not applicable in this slice; service transactions own real rollback semantics.
     */
    @Test
    void updateUserDetails_delegatesToService() throws Exception {
        UserRequest updateRequest = new UserRequest();
        updateRequest.setFirstName("A");
        UserResponse mockedUserResponse = new UserResponse();
        when(userService.updateUserDetails(any(), any(UserRequest.class))).thenReturn(mockedUserResponse);

        mockMvc.perform(post("/api/v1/user/update-own-info")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResponseCode.SUCCESS.getCode()));

        verify(userService).updateUserDetails(any(), any(UserRequest.class));
    }

    /**
     * Test Case ID: UTC-USR-CTL-005
     * <p>
     * {@code POST /api/v1/user/delete-user} toggles soft-delete flags via {@link UserService#deleteOrDisableUser}.
     * <p>
     * CheckDB: {@code verify} ensures the service (and downstream repositories when integrated) receives the command.
     * Rollback: Not applicable here; integration tests would assert transactional rollback if required.
     */
    @Test
    void deleteUser_delegatesToService() throws Exception {
        UserRequest deleteRequest = UserRequest.builder().id(2L).isDelete(true).build();
        when(userService.deleteOrDisableUser(any(UserRequest.class))).thenReturn(new UserResponse());

        mockMvc.perform(post("/api/v1/user/delete-user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deleteRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResponseCode.SUCCESS.getCode()));

        verify(userService).deleteOrDisableUser(any(UserRequest.class));
    }

    /**
     * Test Case ID: UTC-USR-CTL-006
     * <p>
     * {@code POST /api/v1/user/disable-user} reuses {@link UserService#deleteOrDisableUser} to flip {@code isActive}.
     * <p>
     * CheckDB: Same as delete-user — verify delegation so repository saves happen only inside the service implementation.
     * Rollback: Not applicable in {@link WebMvcTest}.
     */
    @Test
    void disableUser_delegatesToService() throws Exception {
        UserRequest disableRequest = UserRequest.builder().id(2L).isActive(false).build();
        when(userService.deleteOrDisableUser(any(UserRequest.class))).thenReturn(new UserResponse());

        mockMvc.perform(post("/api/v1/user/disable-user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(disableRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResponseCode.SUCCESS.getCode()));

        verify(userService).deleteOrDisableUser(any(UserRequest.class));
    }
}
