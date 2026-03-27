package com.zone.agri.dto.request.branch;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FindNearestBranchRequest {
    @NotNull(message = "Vĩ độ (lat) là bắt buộc")
    private Double lat;

    @NotNull(message = "Kinh độ (lng) là bắt buộc")
    private Double lng;

    /** Bán kính tìm kiếm (km). Mặc định 15 km. */
    @Min(value = 1, message = "Bán kính tìm kiếm phải lớn hơn hoặc bằng 1 km")
    private Integer radiusKm = 15;

    /** Số chi nhánh tối đa trả về. Mặc định 5. */
    @Min(value = 1, message = "Số lượng chi nhánh trả về phải lớn hơn hoặc bằng 1")
    private Integer limit = 5;
}
