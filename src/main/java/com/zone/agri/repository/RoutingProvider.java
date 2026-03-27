package com.zone.agri.repository;

import com.zone.agri.dto.response.geo.CoordinateDto;
import com.zone.agri.dto.response.geo.RoutingResult;

import java.util.List;

/**
 * Interface cho Distance Matrix API.
 * Input: 1 origin + N destinations (max 5).
 * Output: durations (giây) + distances (mét).
 */
public interface RoutingProvider {
    RoutingResult getDistanceMatrix(CoordinateDto origin, List<CoordinateDto> destinations);
}
