package com.zone.agri.repository;

import com.zone.agri.dto.request.geo.ShippingFeeParams;
import com.zone.agri.dto.response.geo.ShippingFeeResult;

/**
 * Interface cho Shipping Fee API (GHN, GHTK, ...).
 */
public interface ShippingProvider {
    ShippingFeeResult calculateFee(ShippingFeeParams params);
}
