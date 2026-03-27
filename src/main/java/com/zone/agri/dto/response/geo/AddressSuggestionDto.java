package com.zone.agri.dto.response.geo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AddressSuggestionDto {
    /** Địa chỉ đầy đủ để hiển thị trong gợi ý */
    private String label;
    /** Tỉnh/Thành phố (region) */
    private String province;
    /** Quận/Huyện (county) */
    private String district;
    /** Phường/Xã (locality) */
    private String ward;
    private Double lat;
    private Double lng;
}
