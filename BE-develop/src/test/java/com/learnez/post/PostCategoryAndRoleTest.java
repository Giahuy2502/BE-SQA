package com.learnez.post;

import com.doan2025.webtoeic.constants.enums.ECategoryPost;
import com.doan2025.webtoeic.constants.enums.ERole;
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
 * PostService edge case and category/role tests per business specification.
 * Tests cover category filtering, role-based creation, and special scenarios.
 * Each test has TC-ID and follows Arrange/Act/Assert/CheckDB/Rollback.
 * Rollback: mock repos used, no real DB changes.
 */
@ExtendWith(MockitoExtension.class)
public class PostCategoryAndRoleTest {

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

    // TC-CAT-001: Create post with valid ARTICLE category
    @Test
    public void TC_CAT_001_createPost_validArticleCategory_shouldSave() {
        // Arrange
        HttpServletRequest request = mock(HttpServletRequest.class);
        String email = "consultant@example.com";
        when(jwtUtil.getEmailFromToken(request)).thenReturn(email);

        User user = new User();
        user.setEmail(email);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        PostRequest req = new PostRequest();
        req.setTitle("Tech Article");
        req.setContent("Article content");
        req.setCategoryId(ECategoryPost.TIPS.getValue()); // Valid category

        Post mapped = new Post();
        mapped.setTitle(req.getTitle());
        mapped.setContent(req.getContent());
        when(modelMapper.map(req, Post.class)).thenReturn(mapped);

        Post saved = new Post();
        saved.setId(1100L);
        saved.setCategoryPost(ECategoryPost.TIPS);
        when(postRepository.save(any(Post.class))).thenReturn(saved);
        when(modelMapper.map(saved, PostResponse.class)).thenReturn(new PostResponse());

        // Act
        postService.createPost(request, req);

        // Assert
        ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(captor.capture());
        assertEquals(ECategoryPost.TIPS, captor.getValue().getCategoryPost(), "Category must be TIPS");
        // Rollback: mock repos
    }


    // TC-CAT-003: Create post with invalid category enum -> throw exception
    @Test
    public void TC_CAT_003_createPost_invalidCategory_shouldThrow() {
        // Arrange
        HttpServletRequest request = mock(HttpServletRequest.class);
        String email = "consultant2@example.com";
        when(jwtUtil.getEmailFromToken(request)).thenReturn(email);

        User user = new User();
        user.setEmail(email);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        PostRequest req = new PostRequest();
        req.setTitle("Post");
        req.setContent("Content");
        req.setCategoryId(9999); // Invalid category ID

        Post mapped = new Post();
        when(modelMapper.map(req, Post.class)).thenReturn(mapped);

        // Act & Assert
        // Business spec: invalid category should throw error
        WebToeicException ex = assertThrows(WebToeicException.class,
                () -> postService.createPost(request, req));
        // Rollback: mock repos
    }

    // TC-CAT-004: getPosts filtered by category
    @Test
    public void TC_CAT_004_getPosts_filterByCategory_shouldApplyFilter() {
        // Arrange
        SearchBaseDto dto = new SearchBaseDto();
        dto.setCategoryPost(List.of("TIPS")); // Filter by category

        PostResponse p1 = new PostResponse();
        Page<PostResponse> expected = new PageImpl<>(List.of(p1));
        when(postRepository.findPostFilter(eq(dto), any(Pageable.class))).thenReturn(expected);

        // Act
        Page<PostResponse> result = postService.getPosts(dto, Pageable.unpaged());

        // Assert
        assertNotNull(result);
        verify(postRepository).findPostFilter(dto, Pageable.unpaged());
        // Business: filter should pass category to repository
        // Rollback: mock repos
    }


}
