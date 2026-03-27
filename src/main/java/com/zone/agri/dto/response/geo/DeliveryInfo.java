package com.zone.agri.dto.response.geo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryInfo {
    private Integer toDistrictId;
    private String toWardCode;
    private String deliveryAddress;
    private double userLat;
    private double userLng;
}
