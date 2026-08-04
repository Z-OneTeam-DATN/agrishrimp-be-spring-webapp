package com.zone.agri.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.zone.agri.dto.request.blog.BlogPostRequest;
import com.zone.agri.dto.response.blog.BlogPostResponse;
import com.zone.agri.entity.BlogPost;
import com.zone.agri.entity.enums.BlogPostStatus;
import com.zone.agri.repository.BlogPostRepository;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kiem tra "khong tu xuat ban": nguoi khong co BLOG_APPROVE/ROLE_ADMIN gui status=PUBLISHED phai bi
 * ha xuong IN_REVIEW, admin thi khong bi anh huong gi (giu nguyen hanh vi "Xuat ban ngay" hien tai).
 * Chay that voi DB H2 trong bo nho (khong can Docker), khong mock BlogService.
 */
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
    "security.jwt.secret-key=test-secret-key-for-jwt-util-in-test",
    "security.jwt.issuer=test-issuer",
    "security.jwt.expiry-time-in-seconds=86400",
    "security.jwt.refreshable-duration=86400",
    "mnl.tmp-dir=mnt/",
    "spring.datasource.url=jdbc:h2:mem:agri-blog-test;MODE=MySQL;NON_KEYWORDS=VALUE;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "app.startup.schema-patches.enabled=false",
    "app.startup.seed-data.enabled=false",
    "spring.data.redis.repositories.enabled=false"
})
@Transactional
class BlogServiceTest {

    @Autowired
    private BlogService blogService;

    @Autowired
    private BlogPostRepository postRepo;

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String... authorities) {
        List<SimpleGrantedAuthority> grants = Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("tester@agrishrimp.vn", null, grants));
    }

    private BlogPostRequest buildRequest(String title, String status) {
        BlogPostRequest req = new BlogPostRequest();
        req.setTitle(title);
        req.setContent("<p>Noi dung test</p>");
        req.setStatus(status);
        return req;
    }

    private BlogPost seedPost(String code, BlogPostStatus status) {
        return postRepo.save(BlogPost.builder()
                .title("Bai viet " + code)
                .slug("bai-viet-" + code)
                .content("<p>Noi dung</p>")
                .status(status)
                .build());
    }

    @Test
    void create_withPublishedStatus_byUserWithoutBlogApprove_downgradesToInReview() {
        authenticateAs("BLOG_CREATE");

        BlogPostResponse response = blogService.create(
                buildRequest("Ky su gui duyet", "PUBLISHED"), null);

        assertThat(response.getStatus()).isEqualTo(BlogPostStatus.IN_REVIEW.name());
    }

    @Test
    void create_withPublishedStatus_byAdminAuthority_staysPublished() {
        authenticateAs("ROLE_ADMIN");

        BlogPostResponse response = blogService.create(
                buildRequest("Admin xuat ban ngay", "PUBLISHED"), null);

        assertThat(response.getStatus()).isEqualTo(BlogPostStatus.PUBLISHED.name());
    }

    @Test
    void reject_savesReviewNoteAndResetsToDraft() {
        BlogPost post = seedPost("reject-1", BlogPostStatus.IN_REVIEW);

        blogService.reject(post.getId(), "Bo sung anh minh hoa ro hon");

        BlogPost updated = postRepo.findById(post.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(BlogPostStatus.DRAFT);
        assertThat(updated.getReviewNote()).isEqualTo("Bo sung anh minh hoa ro hon");
    }

    @Test
    void approve_clearsReviewNoteAndPublishes() {
        BlogPost post = seedPost("approve-1", BlogPostStatus.IN_REVIEW);
        post.setReviewNote("Ly do tu choi truoc do");
        postRepo.save(post);

        blogService.approve(post.getId());

        BlogPost updated = postRepo.findById(post.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(BlogPostStatus.PUBLISHED);
        assertThat(updated.getReviewNote()).isNull();
        assertThat(updated.getPublishedAt()).isNotNull();
    }
}
