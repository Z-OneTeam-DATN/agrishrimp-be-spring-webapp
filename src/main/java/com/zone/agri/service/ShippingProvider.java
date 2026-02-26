package com.zone.agri.service;

import com.zone.agri.dto.geo.ShippingFeeParams;
import com.zone.agri.dto.geo.ShippingFeeResult;

/**
 * Interface cho Shipping Fee API (GHN, GHTK, ...).
 */
public interface ShippingProvider {
    ShippingFeeResult calculateFee(ShippingFeeParams params);
}
