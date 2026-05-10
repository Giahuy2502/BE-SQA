package com.doan2025.webtoeic.service.impl;

import com.doan2025.webtoeic.domain.Room;
import com.doan2025.webtoeic.dto.SearchRoomDto;
import com.doan2025.webtoeic.dto.request.RoomRequest;
import com.doan2025.webtoeic.dto.response.RoomResponse;
import com.doan2025.webtoeic.exception.WebToeicException;
import com.doan2025.webtoeic.repository.RoomRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RoomServiceImplTest {

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private ModelMapper modelMapper;

    @InjectMocks
    private RoomServiceImpl roomService;

    // =========================================================================
    // 1. CÁC TRƯỜNG HỢP CỦA HÀM CREATE (TẠO PHÒNG)
    // =========================================================================

    @Test
    public void createRoom_ValidRequest_ReturnsSuccess() {
        // Arrange (Chuẩn bị)
        RoomRequest request = new RoomRequest();
        request.setName("Phòng A1");
        request.setDescription("Mô tả phòng A1");

        Room mockRoom = Room.builder()
                .name(request.getName())
                .description(request.getDescription())
                .build();
        
        RoomResponse mockResponse = new RoomResponse();

        // Giả lập Repository lưu thành công
        when(roomRepository.save(any(Room.class))).thenReturn(mockRoom);
        // Giả lập Mapper chuyển đổi Entity sang DTO
        when(modelMapper.map(mockRoom, RoomResponse.class)).thenReturn(mockResponse);

        // Act (Thực thi)
        RoomResponse result = roomService.createRoom(request);

        // Assert (Kiểm tra)
        assertNotNull(result);
        verify(roomRepository, times(1)).save(any(Room.class));
    }

    // =========================================================================
    // 2. CÁC TRƯỜNG HỢP CỦA HÀM UPDATE (CẬP NHẬT PHÒNG)
    // =========================================================================

    @Test
    public void updateRoom_ValidRequestAndRoomExists_ReturnsSuccess() {
        // Arrange
        Long roomId = 1L;
        RoomRequest request = new RoomRequest();
        request.setId(roomId); 
        request.setName("Tên phòng mới");
        
        Room existingRoom = new Room(); // Khởi tạo một Room Entity đang có sẵn trong DB
        RoomResponse mockResponse = new RoomResponse();

        // Giả lập tìm thấy phòng trong DB
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(existingRoom));
        // Giả lập lưu thành công sau khi FieldUpdateUtil đã chạy ngầm và cập nhật existingRoom
        when(roomRepository.save(existingRoom)).thenReturn(existingRoom);
        when(modelMapper.map(existingRoom, RoomResponse.class)).thenReturn(mockResponse);

        // Act
        RoomResponse result = roomService.updateRoom(request);

        // Assert
        assertNotNull(result);
        verify(roomRepository, times(1)).findById(roomId);
        verify(roomRepository, times(1)).save(existingRoom);
        verify(modelMapper, times(1)).map(existingRoom, RoomResponse.class);
    }

    @Test
    public void updateRoom_RoomDoesNotExist_ThrowsException() {
        // Arrange
        Long invalidRoomId = 99L;
        RoomRequest request = new RoomRequest();
        request.setId(invalidRoomId);

        // Giả lập KHÔNG tìm thấy phòng
        when(roomRepository.findById(invalidRoomId)).thenReturn(Optional.empty());

        // Act & Assert
        // Dùng assertThrows để bắt lỗi được ném ra
        WebToeicException exception = assertThrows(WebToeicException.class, () -> {
            roomService.updateRoom(request);
        });

        // Đảm bảo hàm save() KHÔNG BAO GIỜ ĐƯỢC GỌI vì đã văng lỗi trước đó
        verify(roomRepository, never()).save(any(Room.class));
    }

    @Test
    @DisplayName("updateRoom: Các field null trong request KHÔNG ghi đè giá trị cũ")
    public void updateRoom_NullFieldsInRequest_ShouldKeepExistingValues() {
        Long roomId = 1L;
        RoomRequest request = new RoomRequest();
        request.setId(roomId);
        request.setName(null);        // null → KHÔNG ghi đè
        request.setDescription(null); // null → KHÔNG ghi đè

        Room existingRoom = new Room();
        existingRoom.setName("Tên cũ");
        existingRoom.setDescription("Mô tả cũ");

        when(roomRepository.findById(roomId)).thenReturn(Optional.of(existingRoom));
        when(roomRepository.save(any(Room.class))).thenReturn(existingRoom);
        when(modelMapper.map(any(Room.class), eq(RoomResponse.class))).thenReturn(new RoomResponse());

        roomService.updateRoom(request);

        ArgumentCaptor<Room> captor = ArgumentCaptor.forClass(Room.class);
        verify(roomRepository).save(captor.capture());

        // ✅ Giá trị cũ phải được GIỮ NGUYÊN, không bị null ghi đè
        assertEquals("Tên cũ", captor.getValue().getName());
        assertEquals("Mô tả cũ", captor.getValue().getDescription());
    }

    // =========================================================================
    // 3. CÁC TRƯỜNG HỢP CỦA HÀM GET ALL (TÌM KIẾM/LỌC)
    // =========================================================================

    @Test
    public void getAllRooms_WithData_ReturnsList() {
        // Arrange
        SearchRoomDto dto = new SearchRoomDto();
        
        Room room1 = new Room();
        Room room2 = new Room();
        List<Room> mockDbList = Arrays.asList(room1, room2);
        
        RoomResponse response1 = new RoomResponse();
        RoomResponse response2 = new RoomResponse();

        // Giả lập Repository trả về List có 2 phần tử
        when(roomRepository.filter(dto)).thenReturn(mockDbList);
        when(modelMapper.map(room1, RoomResponse.class)).thenReturn(response1);
        when(modelMapper.map(room2, RoomResponse.class)).thenReturn(response2);

        // Act
        List<RoomResponse> result = roomService.getAllRooms(dto);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size()); // Kiểm tra List trả về phải có đúng 2 phần tử
        verify(roomRepository, times(1)).filter(dto);
    }

    @Test
    public void getAllRooms_EmptyData_ReturnsEmptyList() {
        // Arrange
        SearchRoomDto dto = new SearchRoomDto();
        List<Room> emptyDbList = Collections.emptyList();

        // Giả lập Repository trả về mảng rỗng
        when(roomRepository.filter(dto)).thenReturn(emptyDbList);

        // Act
        List<RoomResponse> result = roomService.getAllRooms(dto);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty()); // Kiểm tra List trả về là List rỗng
        verify(roomRepository, times(1)).filter(dto);
        // Đảm bảo hàm map KHÔNG ĐƯỢC GỌI lần nào (vì mảng rỗng thì Stream không có phần tử để map)
        verify(modelMapper, never()).map(any(), any());
    }
}