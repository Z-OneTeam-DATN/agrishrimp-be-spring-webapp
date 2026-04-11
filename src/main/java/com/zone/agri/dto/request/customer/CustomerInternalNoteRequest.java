package com.zone.agri.dto.request.customer;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CustomerInternalNoteRequest {
    @NotBlank(message = "Nội dung ghi chú không được để trống")
    private String content;
}
