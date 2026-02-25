package com.zone.agri.service;

import com.zone.agri.dto.product.BrandResponse;
import com.zone.agri.entity.Brand;
import com.zone.agri.entity.enums.BrandStatus;
import com.zone.agri.repository.BrandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BrandService {

    private final BrandRepository brandRepository;

    public List<BrandResponse> getPublicBrands() {
        List<Brand> brands = brandRepository.findByStatus(BrandStatus.ACTIVE);
        return brands.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    private BrandResponse convertToResponse(Brand brand) {
        return BrandResponse.builder()
                .id(brand.getId())
                .name(brand.getName())
                .logoUrl(brand.getLogoUrl())
                .build();
    }
}
