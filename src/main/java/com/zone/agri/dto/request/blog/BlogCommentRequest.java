package com.zone.agri.dto.request.blog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BlogCommentRequest {

    @NotBlank(message = "Nội dung bình luận không được để trống")
    @Size(max = 2000, message = "Bình luận không được vượt quá 2000 ký tự")
    private String content;

    private Long parentId;
}
