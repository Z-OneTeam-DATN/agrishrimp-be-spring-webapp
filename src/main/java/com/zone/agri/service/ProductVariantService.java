package com.zone.agri.service;

import com.zone.agri.dto.product.VariantSearchResponse;
import com.zone.agri.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductVariantService {

    private final ProductVariantRepository variantRepo;

    public List<VariantSearchResponse> searchVariants(String keyword) {

        String searchKey = (keyword == null) ? "" : keyword.trim();
        return variantRepo.searchVariants(searchKey);
    }
}