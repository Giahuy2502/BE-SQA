package com.learnez.post;

import com.doan2025.webtoeic.constants.enums.ECategoryPost;
import com.doan2025.webtoeic.constants.enums.ERole;
import com.doan2025.webtoeic.constants.enums.ResponseCode;
import com.doan2025.webtoeic.constants.enums.ResponseObject;
import com.doan2025.webtoeic.domain.Post;
import com.doan2025.webtoeic.domain.User;
import com.doan2025.webtoeic.dto.SearchBaseDto;
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
import org.modelmapper.ModelMapper;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PostService following business specification (use cases).
 * Each test contains a Test Case ID comment and follows Arrange / Act / Assert
 * / CheckDB / Rollback structure.
 * Rollback note: Unit test sử dụng mock repository nên không làm thay đổi
 * database thật
 */
@ExtendWith(MockitoExtension.class)
public class PostServiceTest {

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

    // TC-POST-001: Create post - must create PENDING post and persist đầy đủ
    // title, content, category, author, createdAt theo đặc tả.
    // Test case này có thể fail nếu code hiện tại chưa set createdAt đúng theo đặc
    // tả.
    @Test
    public void TC_POST_001_createPost_shouldSaveAsPendingAndReturnResponse() {
        // Arrange
        HttpServletRequest request = mock(HttpServletRequest.class);
        PostRequest req = new PostRequest();
        req.setTitle("How to learn TOEIC");
        req.setContent("Content of blog");
        req.setCategoryId(ECategoryPost.TIPS.getValue());
        req.setThemeUrl("http://img");
        String authorEmail = "author@example.com";

        when(jwtUtil.getEmailFromToken(request)).thenReturn(authorEmail);

        User author = new User();
        author.setId(10L);
        author.setEmail(authorEmail);
        when(userRepository.findByEmail(authorEmail)).thenReturn(Optional.of(author));

        Post mappedPost = new Post();
        mappedPost.setTitle(req.getTitle());
        mappedPost.setContent(req.getContent());
        mappedPost.setThemeUrl(req.getThemeUrl());
        when(modelMapper.map(req, Post.class)).thenReturn(mappedPost);

        Post savedPost = new Post();
        savedPost.setId(100L);
        savedPost.setTitle(req.getTitle());
        savedPost.setContent(req.getContent());
        savedPost.setThemeUrl(req.getThemeUrl());
        savedPost.setAuthor(author);
        savedPost.setCategoryPost(ECategoryPost.TIPS);
        when(postRepository.save(any(Post.class))).thenReturn(savedPost);

        PostResponse resp = new PostResponse();
        resp.setId(savedPost.getId());
        resp.setTitle(savedPost.getTitle());
        when(modelMapper.map(savedPost, PostResponse.class)).thenReturn(resp);

        // Act
        PostResponse result = postService.createPost(request, req);

        // Assert: business expectation (PENDING)
        ArgumentCaptor<Post> postCaptor = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(postCaptor.capture());

        Post captured = postCaptor.getValue();
        assertEquals(req.getTitle(), captured.getTitle(), "Saved post title must match request");
        assertEquals(req.getContent(), captured.getContent(), "Saved post content must match request");
        assertEquals(ECategoryPost.fromValue(req.getCategoryId()), captured.getCategoryPost(),
                "Saved category must match request");
        assertNotNull(captured.getAuthor(), "Author must be set on saved post");
        assertEquals(authorEmail, captured.getAuthor().getEmail(), "Author email must match JWT subject");

        // Business: PENDING => post must not be public and must not be deleted.
        assertFalse(captured.getIsActive() != null && captured.getIsActive(), "Post should not be active (PENDING)");
        assertFalse(captured.getIsDelete() != null && captured.getIsDelete(), "Post should not be deleted");
        assertNotNull(captured.getCreatedAt(), "Post must have createdAt when created per spec");

        // Response mapping
        assertNotNull(result);
        assertEquals(100L, result.getId());

        verify(userRepository, times(1)).findByEmail(authorEmail);
        // Rollback: mock repository used; no DB changes in real DB
    }

    // TC-POST-008: Update post after approved should be disallowed per spec
    // (bài đã APPROVED thì không cho author chỉnh sửa nữa).
    @Test
    public void TC_POST_008_updateAfterApproved_shouldThrowNotPermission() {
        // Arrange
        HttpServletRequest request = mock(HttpServletRequest.class);
        String authorEmail = "author2@example.com";
        when(jwtUtil.getEmailFromToken(request)).thenReturn(authorEmail);

        User author = new User();
        author.setEmail(authorEmail);
        author.setRole(ERole.CONSULTANT);
        when(userRepository.findByEmail(authorEmail)).thenReturn(Optional.of(author));

        Post existing = new Post();
        existing.setId(500L);
        existing.setAuthor(author);
        existing.setIsActive(true); // already approved
        when(postRepository.findById(500L)).thenReturn(Optional.of(existing));

        PostRequest updateReq = new PostRequest();
        updateReq.setId(500L);
        updateReq.setTitle("Attempted Edit After Approve");
        updateReq.setContent("Attempted content update after approval");

        // Act & Assert
        // Business spec: author cannot update after APPROVED -> expect NOT_PERMISSION.
        // Test case này có thể fail nếu code hiện tại chưa xử lý đúng theo đặc tả.
        assertThrows(Exception.class, () -> postService.updatePost(request, updateReq));
        // CheckDB: ensure no save
        verify(postRepository, never()).save(any());
    }

    // TC-POST-009: getPostDetail returns detail when token present and when absent
    @Test
    public void TC_POST_009_getPostDetail_withAndWithoutToken_shouldReturnDetail() {
        // Arrange: with token
        HttpServletRequest requestWithToken = mock(HttpServletRequest.class);
        when(requestWithToken.getHeader("Authorization")).thenReturn("Bearer abc");
        when(jwtUtil.getEmailFromToken(requestWithToken)).thenReturn("viewer@example.com");

        PostResponse pr = new PostResponse();
        pr.setId(600L);
        when(postRepository.findPostDetail(600L, "viewer@example.com")).thenReturn(java.util.Optional.of(pr));

        // Act
        PostResponse res = postService.getPostDetail(requestWithToken, 600L);
        assertNotNull(res);

        // Arrange: without token
        HttpServletRequest requestNoToken = mock(HttpServletRequest.class);
        when(requestNoToken.getHeader("Authorization")).thenReturn(null);
        when(postRepository.findPostDetail(601L, "")).thenReturn(java.util.Optional.of(pr));

        // Act
        PostResponse res2 = postService.getPostDetail(requestNoToken, 601L);
        assertNotNull(res2);
    }

    // TC-POST-002: Create post missing title -> expect validation exception and no
    // save
    @Test
    public void TC_POST_002_createPost_missingTitle_shouldThrowIsNullAndNotSave() {
        // Arrange
        HttpServletRequest request = mock(HttpServletRequest.class);
        PostRequest req = new PostRequest();
        req.setTitle(null);
        req.setContent("some content");
        req.setCategoryId(ECategoryPost.TIPS.getValue());

        // Act & Assert
        WebToeicException ex = assertThrows(WebToeicException.class,
                () -> postService.createPost(request, req),
                "Creating post without title must throw IS_NULL WebToeicException");

        // CheckDB: ensure no save performed
        verify(postRepository, never()).save(any());
        // Rollback: mock repository
    }

    // TC-POST-005: Soft-delete (mark isDelete=true) should call save with isDelete
    // true
    @Test
    public void TC_POST_005_disableOrDeletePost_softDelete_shouldSetIsDeleteTrue() {
        // Arrange
        HttpServletRequest request = mock(HttpServletRequest.class);
        String managerEmail = "manager@example.com";
        when(jwtUtil.getEmailFromToken(request)).thenReturn(managerEmail);

        User manager = new User();
        manager.setEmail(managerEmail);
        when(userRepository.findByEmail(managerEmail)).thenReturn(Optional.of(manager));

        Post existing = new Post();
        existing.setId(300L);
        existing.setIsDelete(false);
        when(postRepository.findById(300L)).thenReturn(Optional.of(existing));

        PostRequest req = new PostRequest();
        req.setId(300L);
        req.setIsDelete(Boolean.TRUE);

        Post saved = new Post();
        saved.setId(300L);
        saved.setIsDelete(true);
        when(postRepository.save(any(Post.class))).thenReturn(saved);

        when(modelMapper.map(saved, PostResponse.class)).thenReturn(new PostResponse());

        // Act
        postService.disableOrDeletePost(request, req);

        // CheckDB: capture saved post and assert isDelete true
        ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(captor.capture());
        assertTrue(Boolean.TRUE.equals(captor.getValue().getIsDelete()), "Saved post must have isDelete=true");

        // Rollback note: mock repository used; no DB change
    }

    // TC-POST-011: getPosts - list all public posts (business: hiển thị công khai)
    @Test
    public void TC_POST_011_getPosts_shouldReturnFilteredPublicPosts() {
        // Arrange
        SearchBaseDto dto = new SearchBaseDto();
        dto.setCategoryPost(null);
        Pageable pageable = Pageable.unpaged();

        PostResponse p1 = new PostResponse();
        p1.setId(800L);
        Page<PostResponse> expectedPage = new PageImpl<>(List.of(p1));
        when(postRepository.findPostFilter(eq(dto), eq(pageable))).thenReturn(expectedPage);

        // Act
        Page<PostResponse> result = postService.getPosts(dto, pageable);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getTotalElements(), "getPosts should return filtered public posts");
        verify(postRepository, times(1)).findPostFilter(dto, pageable);
        // Rollback: mock repos
    }

    // TC-POST-014: getPostDetail - post not found should throw NOT_EXISTED
    @Test
    public void TC_POST_014_getPostDetail_postNotFound_shouldThrow() {
        // Arrange
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer xyz");
        when(jwtUtil.getEmailFromToken(request)).thenReturn("viewer@example.com");

        when(postRepository.findPostDetail(999L, "viewer@example.com")).thenReturn(Optional.empty());

        // Act & Assert
        WebToeicException ex = assertThrows(WebToeicException.class,
                () -> postService.getPostDetail(request, 999L));
        // Business: return NOT_EXISTED when post not found
        assertEquals(ResponseCode.NOT_EXISTED, ex.getResponseCode());
        // Rollback: mock repos
    }

    // TC-POST-015: createPost - author user not found should throw
    @Test
    public void TC_POST_015_createPost_authorNotFound_shouldThrow() {
        // Arrange
        HttpServletRequest request = mock(HttpServletRequest.class);
        String nonExistEmail = "nonexist@example.com";
        when(jwtUtil.getEmailFromToken(request)).thenReturn(nonExistEmail);

        when(userRepository.findByEmail(nonExistEmail)).thenReturn(Optional.empty());

        PostRequest req = new PostRequest();
        req.setTitle("Title");
        req.setContent("Content");
        req.setCategoryId(ECategoryPost.TIPS.getValue());

        // Act & Assert
        WebToeicException ex = assertThrows(WebToeicException.class,
                () -> postService.createPost(request, req));
        assertEquals(ResponseCode.NOT_EXISTED, ex.getResponseCode());
        verify(postRepository, never()).save(any());
        // Rollback: mock repos
    }

    // TC-POST-016: updatePost - post not found should throw
    @Test
    public void TC_POST_016_updatePost_postNotFound_shouldThrow() {
        // Arrange
        HttpServletRequest request = mock(HttpServletRequest.class);
        String email = "author@example.com";
        when(jwtUtil.getEmailFromToken(request)).thenReturn(email);

        User user = new User();
        user.setEmail(email);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        when(postRepository.findById(9999L)).thenReturn(Optional.empty());

        PostRequest req = new PostRequest();
        req.setId(9999L);
        req.setTitle("Updated");

        // Act & Assert
        WebToeicException ex = assertThrows(WebToeicException.class,
                () -> postService.updatePost(request, req));
        assertEquals(ResponseCode.NOT_EXISTED, ex.getResponseCode());
        verify(postRepository, never()).save(any());
        // Rollback: mock repos
    }

    // TC-POST-019: Create post with invalid category should throw NOT_EXISTED and
    // must not save any post.
    @Test
    public void TC_POST_019_createPost_invalidCategory_shouldThrowNotExisted() {
        // Arrange
        HttpServletRequest request = mock(HttpServletRequest.class);
        String authorEmail = "invalid-category@author.com";
        when(jwtUtil.getEmailFromToken(request)).thenReturn(authorEmail);

        User author = new User();
        author.setEmail(authorEmail);
        author.setRole(ERole.CONSULTANT);
        when(userRepository.findByEmail(authorEmail)).thenReturn(Optional.of(author));

        PostRequest req = new PostRequest();
        req.setTitle("Invalid category post");
        req.setContent("Content");
        req.setCategoryId(9999);

        Post mappedPost = new Post();
        when(modelMapper.map(req, Post.class)).thenReturn(mappedPost);

        // Act & Assert
        WebToeicException ex = assertThrows(WebToeicException.class,
                () -> postService.createPost(request, req));
        assertEquals(ResponseCode.NOT_EXISTED, ex.getResponseCode());
        verify(postRepository, never()).save(any());
        // Rollback: mock repository used; no DB changes in real DB
    }
}
