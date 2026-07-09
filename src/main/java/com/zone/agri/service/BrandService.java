package com.zone.agri.service;

import com.zone.agri.dto.request.product.BrandRequest;
import com.zone.agri.dto.response.product.BrandResponse;
import com.zone.agri.entity.Brand;
import com.zone.agri.entity.enums.BrandStatus;
import com.zone.agri.exception.BadRequestException;
import com.zone.agri.exception.ConflictException;
import com.zone.agri.exception.NotFoundException;
import com.zone.agri.repository.BrandRepository;
import com.zone.agri.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class BrandService {

    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;
    private final com.zone.agri.common.CloudinaryService cloudinaryService;

    @Transactional(readOnly = true)
    public List<BrandResponse> getPublicBrands() {
        List<Brand> brands = brandRepository.findByStatus(BrandStatus.ACTIVE);
        return brands.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<BrandResponse> getAllBrands(String keyword) {
        List<Brand> brands;
        if (keyword != null && !keyword.trim().isEmpty()) {
            brands = brandRepository.findByNameContainingIgnoreCaseOrderByIdDesc(keyword.trim());
        } else {
            brands = brandRepository.findAllByOrderByIdDesc();
        }
        return brands.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public BrandResponse getBrandById(Long id) {
        Brand brand = brandRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy thương hiệu với ID: " + id));
        return convertToResponse(brand);
    }

    public BrandResponse createBrand(BrandRequest request) {
        if (brandRepository.existsByNameIgnoreCase(request.getName().trim())) {
            throw new ConflictException("Tên thương hiệu đã tồn tại: " + request.getName());
        }

        BrandStatus status = request.getStatus() != null ? request.getStatus() : BrandStatus.ACTIVE;

        Brand brand = Brand.builder()
                .name(request.getName().trim())
                .status(status)
                .build();
        handleImageUpload(brand, request.getLogoUrl());

        Brand savedBrand = brandRepository.save(brand);
        return convertToResponse(savedBrand);
    }

    public BrandResponse updateBrand(Long id, BrandRequest request) {
        Brand brand = brandRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy thương hiệu với ID: " + id));

        if (brandRepository.existsByNameIgnoreCaseAndIdNot(request.getName().trim(), id)) {
            throw new ConflictException("Tên thương hiệu đã tồn tại: " + request.getName());
        }

        brand.setName(request.getName().trim());
        handleImageUpload(brand, request.getLogoUrl());
        if (request.getStatus() != null) {
            brand.setStatus(request.getStatus());
        }

        Brand updatedBrand = brandRepository.save(brand);
        return convertToResponse(updatedBrand);
    }

    private void handleImageUpload(Brand brand, String logoUrl) {
        if (logoUrl != null && logoUrl.startsWith("data:image")) {
            brand.setLogoUrl(cloudinaryService.uploadImage(logoUrl));
        } else {
            brand.setLogoUrl(logoUrl != null ? logoUrl.trim() : null);
        }
    }

    public void deleteBrand(Long id) {
        Brand brand = brandRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy thương hiệu với ID: " + id));

        if (productRepository.existsByBrandId(id)) {
            throw new BadRequestException("Không thể xóa thương hiệu này vì đang có sản phẩm liên kết");
        }

        brandRepository.delete(brand);
    }

    private BrandResponse convertToResponse(Brand brand) {
        return BrandResponse.builder()
                .id(brand.getId())
                .name(brand.getName())
                .logoUrl(brand.getLogoUrl())
                .status(brand.getStatus())
                .build();
    }
}

