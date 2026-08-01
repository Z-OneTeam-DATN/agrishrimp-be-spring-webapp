package com.zone.agri.dto.response.blog;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BlogCommentResponse {

    private Long id;
    private Long parentId;
    private Long authorId;
    private String authorName;
    private String authorAvatarUrl;
    private String content;
    private String mentionName;
    private LocalDateTime createdAt;
    private List<BlogCommentResponse> replies;
}
