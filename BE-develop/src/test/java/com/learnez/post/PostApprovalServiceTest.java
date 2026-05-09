package com.learnez.post;

import com.doan2025.webtoeic.constants.enums.ERole;
import com.doan2025.webtoeic.domain.Post;
import com.doan2025.webtoeic.domain.User;
import com.doan2025.webtoeic.dto.request.PostRequest;
import com.doan2025.webtoeic.dto.response.PostResponse;
import com.doan2025.webtoeic.exception.WebToeicException;
import com.doan2025.webtoeic.repository.PostRepository;
import com.doan2025.webtoeic.repository.UserRepository;
import com.doan2025.webtoeic.service.impl.PostServiceImpl;
import com.doan2025.webtoeic.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for post approval flows and permission matrix according to business
 * spec.
 * Each test has a Test Case ID and uses Arrange/Act/Assert/CheckDB/Rollback
 * structure.
 * Rollback: mocks used, so no real DB changes.
 */
@ExtendWith(MockitoExtension.class)
public class PostApprovalServiceTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private ModelMapper modelMapper;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private PostServiceImpl postService;

    // TC-APR-001: Manager approves post -> isActive=true saved
    @Test
    public void TC_APR_001_managerApprove_shouldSetIsActiveTrue() {
        // Arrange
        HttpServletRequest request = mock(HttpServletRequest.class);
        String managerEmail = "mgr@example.com";
        when(jwtUtil.getEmailFromToken(request)).thenReturn(managerEmail);

        User manager = new User();
        manager.setEmail(managerEmail);
        manager.setRole(ERole.MANAGER);
        when(userRepository.findByEmail(managerEmail)).thenReturn(Optional.of(manager));

        Post existing = new Post();
        existing.setId(1000L);
        existing.setIsActive(false);
        when(postRepository.findById(1000L)).thenReturn(Optional.of(existing));

        PostRequest req = new PostRequest();
        req.setId(1000L);
        req.setIsActive(Boolean.TRUE);

        Post saved = new Post();
        saved.setId(1000L);
        saved.setIsActive(true);
        when(postRepository.save(any(Post.class))).thenReturn(saved);
        when(modelMapper.map(saved, PostResponse.class)).thenReturn(new PostResponse());

        // Act
        postService.disableOrDeletePost(request, req);

        // Assert / CheckDB
        ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(captor.capture());
        assertTrue(Boolean.TRUE.equals(captor.getValue().getIsActive()), "Manager approval must set isActive=true");
        // Rollback: mock repos used
    }

    // TC-APR-002: Consultant approves post -> isActive=true saved
    @Test
    public void TC_APR_002_consultantApprove_shouldSetIsActiveTrue() {
        // Arrange
        HttpServletRequest request = mock(HttpServletRequest.class);
        String consulEmail = "consul@example.com";
        when(jwtUtil.getEmailFromToken(request)).thenReturn(consulEmail);

        User consul = new User();
        consul.setEmail(consulEmail);
        consul.setRole(ERole.CONSULTANT);
        when(userRepository.findByEmail(consulEmail)).thenReturn(Optional.of(consul));

        Post existing = new Post();
        existing.setId(1001L);
        existing.setIsActive(false);
        when(postRepository.findById(1001L)).thenReturn(Optional.of(existing));

        PostRequest req = new PostRequest();
        req.setId(1001L);
        req.setIsActive(Boolean.TRUE);
        Post saved = new Post();
        saved.setId(1001L);
        saved.setIsActive(true);
        when(postRepository.save(any(Post.class))).thenReturn(saved);
        when(modelMapper.map(saved, PostResponse.class)).thenReturn(new PostResponse());

        // Act
        postService.disableOrDeletePost(request, req);

        // Assert
        ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(captor.capture());
        assertTrue(Boolean.TRUE.equals(captor.getValue().getIsActive()), "Consultant approval must set isActive=true");
    }

    // TC-APR-003: Teacher attempts to approve -> should be denied per spec
    // (NOT_PERMISSION)
    @Test
    public void TC_APR_003_teacherApprove_shouldBeDenied() {
        // Arrange
        HttpServletRequest request = mock(HttpServletRequest.class);
        String teacherEmail = "teacher@example.com";
        when(jwtUtil.getEmailFromToken(request)).thenReturn(teacherEmail);

        User teacher = new User();
        teacher.setEmail(teacherEmail);
        teacher.setRole(ERole.TEACHER);
        when(userRepository.findByEmail(teacherEmail)).thenReturn(Optional.of(teacher));

        Post existing = new Post();
        existing.setId(1002L);
        existing.setIsActive(false);
        when(postRepository.findById(1002L)).thenReturn(Optional.of(existing));

        PostRequest req = new PostRequest();
        req.setId(1002L);
        req.setIsActive(Boolean.TRUE);

        // Act & Assert
        // Business: only manager or consultant can approve; teacher should be denied.
        assertThrows(WebToeicException.class, () -> postService.disableOrDeletePost(request, req));
        verify(postRepository, never()).save(any());
    }

    // TC-APR-004: Consultant author approving own post -> allowed per spec
    @Test
    public void TC_APR_004_consultantAuthorApprove_shouldBeAllowed() {
        // Arrange
        HttpServletRequest request = mock(HttpServletRequest.class);
        String consulEmail = "author.consul@example.com";
        when(jwtUtil.getEmailFromToken(request)).thenReturn(consulEmail);

        User consul = new User();
        consul.setEmail(consulEmail);
        consul.setRole(ERole.CONSULTANT);
        when(userRepository.findByEmail(consulEmail)).thenReturn(Optional.of(consul));

        Post existing = new Post();
        existing.setId(1003L);
        existing.setIsActive(false);
        User author = new User();
        author.setEmail(consulEmail);
        existing.setAuthor(author);
        when(postRepository.findById(1003L)).thenReturn(Optional.of(existing));

        PostRequest req = new PostRequest();
        req.setId(1003L);
        req.setIsActive(Boolean.TRUE);
        Post saved = new Post();
        saved.setId(1003L);
        saved.setIsActive(true);
        when(postRepository.save(any(Post.class))).thenReturn(saved);
        when(modelMapper.map(saved, PostResponse.class)).thenReturn(new PostResponse());

        // Act
        postService.disableOrDeletePost(request, req);

        // Assert
        ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(captor.capture());
        assertTrue(Boolean.TRUE.equals(captor.getValue().getIsActive()),
                "Consultant author approval must set isActive=true");
    }

    // TC-APR-005: Random user (no privilege) tries to approve -> denied
    @Test
    public void TC_APR_005_randomUserApprove_shouldBeDenied() {
        // Arrange
        HttpServletRequest request = mock(HttpServletRequest.class);
        String userEmail = "user@example.com";
        when(jwtUtil.getEmailFromToken(request)).thenReturn(userEmail);

        User user = new User();
        user.setEmail(userEmail);
        user.setRole(null);
        when(userRepository.findByEmail(userEmail)).thenReturn(Optional.of(user));

        Post existing = new Post();
        existing.setId(1004L);
        existing.setIsActive(false);
        when(postRepository.findById(1004L)).thenReturn(Optional.of(existing));

        PostRequest req = new PostRequest();
        req.setId(1004L);
        req.setIsActive(Boolean.TRUE);

        // Act & Assert
        assertThrows(WebToeicException.class, () -> postService.disableOrDeletePost(request, req));
        verify(postRepository, never()).save(any());
    }
}
