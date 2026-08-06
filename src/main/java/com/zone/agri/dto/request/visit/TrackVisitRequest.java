package com.zone.agri.dto.request.visit;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TrackVisitRequest {

    @NotBlank(message = "visitorId không được để trống")
    private String visitorId;

    private String path;

    private String userAgent;
}
