package com.zone.agri.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO for creating or updating a role with assigned permissions
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleSaveRequest {

    @NotBlank(message = "Tên vai trò không được để trống")
    private String name;

    private String description;

    @NotEmpty(message = "Danh sách quyền không được để trống")
    private List<Long> permissionIds;
}
