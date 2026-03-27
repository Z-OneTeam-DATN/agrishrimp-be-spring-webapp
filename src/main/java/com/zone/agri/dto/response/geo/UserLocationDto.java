package com.zone.agri.dto.response.geo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserLocationDto {
    private double lat;
    private double lng;
    private String city;
}
