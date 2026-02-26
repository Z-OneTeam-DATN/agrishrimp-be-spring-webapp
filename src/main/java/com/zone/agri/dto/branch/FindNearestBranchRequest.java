package com.zone.agri.dto.branch;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FindNearestBranchRequest {
    @NotNull(message = "lat là bắt buộc")
    private Double lat;

    @NotNull(message = "lng là bắt buộc")
    private Double lng;

    /** Bán kính tìm kiếm (km). Mặc định 15 km. */
    private Integer radiusKm = 15;

    /** Số chi nhánh tối đa trả về. Mặc định 5. */
    private Integer limit = 5;
}
