package com.zone.agri.dto.request.geo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShippingFeeParams {
    /** Mã quận/huyện của chi nhánh xuất hàng (GHN from_district_id) */
    private Integer fromDistrictId;
    /** Mã quận/huyện giao hàng (GHN to_district_id) */
    private Integer toDistrictId;
    /** Mã phường/xã giao hàng (GHN to_ward_code) */
    private String toWardCode;
    /** Tổng trọng lượng đơn (gram) */
    private int weightGram;
    /** Giá trị COD (đồng) - 0 nếu không COD */
    private long codAmount;
}
