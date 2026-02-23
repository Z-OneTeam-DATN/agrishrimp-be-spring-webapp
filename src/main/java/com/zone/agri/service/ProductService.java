package com.zone.agri.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zone.agri.common.CloudinaryService;
import com.zone.agri.dto.product.*;
import com.zone.agri.entity.*;
import com.zone.agri.entity.enums.*;
import com.zone.agri.exception.BadRequestException;
import com.zone.agri.exception.NotFoundException;
import com.zone.agri.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductImageRepository imageRepository;
    private final BrandRepository brandRepository;
    private final CategoryRepository categoryRepository;
    private final AttributeRepository attributeRepository;
    private final VariantAttributeRepository variantAttributeRepository;
    private final UnitConversionRepository unitConversionRepository;
    private final CloudinaryService cloudinaryService;
    private final ObjectMapper objectMapper;

    // =========================================================================
    // CREATE (API MỚI – multipart/form-data + Cloudinary)
    // =========================================================================

    @Transactional
    public CreateProductResponse createProduct(
            CreateProductRequest request,
            List<MultipartFile> productImages,
            List<MultipartFile> variantImages) {

        // 1. Validate danh mục tồn tại
        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new NotFoundException(
                        "Danh mục không tồn tại với ID: " + request.getCategoryId()));

        // 2. Validate SKU trong request không trùng nhau
        validateSkusInRequest(request.getVariants());

        // 3. Xây dựng entity Product
        Product product = Product.builder()
                .name(request.getName())
                .slug(toSlug(request.getName()) + "-" + System.currentTimeMillis())
                .description(request.getDescription())
                .status(parseProductStatus(request.getStatus()))
                .origin(request.getOrigin())
                .baseSku(request.getBaseSku())
                .category(category)
                .createdAt(LocalDateTime.now())
                .build();

        // 4. Xử lý Thương hiệu (tìm hoặc tự tạo)
        if (request.getBrand() != null && !request.getBrand().isBlank()) {
            Brand brand = brandRepository.findByName(request.getBrand().trim())
                    .orElseGet(() -> brandRepository.save(Brand.builder()
                            .name(request.getBrand().trim())
                            .status(BrandStatus.ACTIVE)
                            .build()));
            product.setBrand(brand);
        }

        Product savedProduct = productRepository.save(product);

        // 5. Upload ảnh sản phẩm lên Cloudinary
        List<String> savedImageUrls = uploadAndSaveProductImages(savedProduct, productImages);

        // 6. Lưu biến thể kèm ảnh và quy đổi đơn vị
        List<ProductVariantResponse> variantResponses = saveVariantsWithImages(
                savedProduct, request.getVariants(), variantImages);

        log.info("Tạo sản phẩm thành công: id={}, name={}", savedProduct.getId(), savedProduct.getName());

        return buildResponse(savedProduct, category, savedImageUrls, variantResponses);
    }

    // =========================================================================
    // UPDATE (giữ nguyên API cũ – tương thích với FE hiện tại)
    // =========================================================================

    @Transactional
    public Product updateProduct(Long id, ProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Sản phẩm không tồn tại với ID: " + id));

        if (request.getVariants() == null || request.getVariants().isEmpty()) {
            throw new BadRequestException("Sản phẩm phải có ít nhất 1 biến thể!");
        }

        mapBasicInfoLegacy(product, request);
        Product updatedProduct = productRepository.save(product);

        productRepository.deleteVariantAttributesByProduct(updatedProduct);
        productRepository.deleteUnitConversionsByProduct(updatedProduct);
        productRepository.deleteVariantsByProduct(updatedProduct);
        productRepository.deleteImagesByProduct(updatedProduct);

        saveLegacyImages(updatedProduct, request.getImages());
        saveLegacyVariants(updatedProduct, request.getVariants());

        return updatedProduct;
    }

    public List<Product> getAll() {
        return productRepository.findAllWithDetails();
    }

    public Product getById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy sản phẩm với ID: " + id));
    }

    @Transactional
    public void delete(Long id) {
        Product product = getById(id);
        productRepository.deleteVariantAttributesByProduct(product);
        productRepository.deleteUnitConversionsByProduct(product);
        productRepository.deleteVariantsByProduct(product);
        productRepository.deleteImagesByProduct(product);
        productRepository.delete(product);
    }

    // =========================================================================
    // PRIVATE HELPERS – CREATE
    // =========================================================================

    private void validateSkusInRequest(List<VariantRequest> variants) {
        Set<String> seen = new HashSet<>();
        for (VariantRequest v : variants) {
            if (!seen.add(v.getSku())) {
                throw new BadRequestException("Mã SKU bị trùng lặp trong request: " + v.getSku());
            }
        }
    }

    private List<String> uploadAndSaveProductImages(Product product, List<MultipartFile> files) {
        List<String> urls = new ArrayList<>();
        if (files == null || files.isEmpty()) return urls;

        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) continue;
            CloudinaryService.UploadResult result = cloudinaryService.upload(file, "main");
            ProductImage image = ProductImage.builder()
                    .imageUrl(result.secureUrl())
                    .publicId(result.publicId())
                    .product(product)
                    .build();
            imageRepository.save(image);
            urls.add(result.secureUrl());
        }
        return urls;
    }

    private List<ProductVariantResponse> saveVariantsWithImages(
            Product product,
            List<VariantRequest> variantRequests,
            List<MultipartFile> variantImages) {

        List<ProductVariantResponse> responses = new ArrayList<>();

        for (int i = 0; i < variantRequests.size(); i++) {
            VariantRequest vReq = variantRequests.get(i);

            // Upload ảnh biến thể nếu có
            String imageUrl = null;
            String imagePublicId = null;
            if (variantImages != null && i < variantImages.size()) {
                MultipartFile imgFile = variantImages.get(i);
                if (imgFile != null && !imgFile.isEmpty()) {
                    CloudinaryService.UploadResult result = cloudinaryService.upload(imgFile, "variants");
                    imageUrl = result.secureUrl();
                    imagePublicId = result.publicId();
                }
            }

            // Serialize customSpecs sang JSON text
            String customSpecsJson = serializeCustomSpecs(vReq.getCustomSpecs());

            ProductVariant variant = ProductVariant.builder()
                    .product(product)
                    .sku(vReq.getSku())
                    .barcode(vReq.getBarcode())
                    .formulation(vReq.getFormulation())
                    .packaging(vReq.getPackaging())
                    .unit(vReq.getUnit())
                    .price(vReq.getPrice())
                    .importPrice(vReq.getCostPrice())
                    .wholesalePrice(vReq.getWholesalePrice())
                    .quantity(vReq.getInitialStock() != null ? vReq.getInitialStock() : 0)
                    .weightValue(vReq.getNetWeight())
                    .netWeightUnit(vReq.getNetWeightUnit())
                    .shippingWeight(vReq.getShippingWeight())
                    .imageUrl(imageUrl)
                    .imagePublicId(imagePublicId)
                    .customSpecs(customSpecsJson)
                    .status(VariantStatus.ACTIVE)
                    .build();

            ProductVariant savedVariant = variantRepository.save(variant);

            // Lưu quy đổi đơn vị
            List<UnitConversionResponse> conversionResponses = saveUnitConversions(
                    savedVariant, vReq.getUnitConversions());

            responses.add(ProductVariantResponse.builder()
                    .id(savedVariant.getId())
                    .sku(savedVariant.getSku())
                    .barcode(savedVariant.getBarcode())
                    .formulation(savedVariant.getFormulation())
                    .packaging(savedVariant.getPackaging())
                    .unit(savedVariant.getUnit())
                    .importPrice(savedVariant.getImportPrice())
                    .price(savedVariant.getPrice())
                    .wholesalePrice(savedVariant.getWholesalePrice())
                    .quantity(savedVariant.getQuantity())
                    .weightValue(savedVariant.getWeightValue())
                    .netWeightUnit(savedVariant.getNetWeightUnit())
                    .shippingWeight(savedVariant.getShippingWeight())
                    .imageUrl(savedVariant.getImageUrl())
                    .status(savedVariant.getStatus())
                    .unitConversions(conversionResponses)
                    .build());
        }
        return responses;
    }

    private List<UnitConversionResponse> saveUnitConversions(
            ProductVariant variant, List<UnitConversionRequest> conversions) {

        if (conversions == null || conversions.isEmpty()) return Collections.emptyList();

        // Validate không trùng cặp (fromUnit, toUnit)
        Set<String> seenPairs = new HashSet<>();
        for (UnitConversionRequest conv : conversions) {
            String pair = conv.getFromUnit().toLowerCase() + "::" + conv.getToUnit().toLowerCase();
            if (!seenPairs.add(pair)) {
                throw new BadRequestException(
                        "Quy đổi đơn vị bị trùng lặp: " + conv.getFromUnit() + " → " + conv.getToUnit());
            }
        }

        return conversions.stream().map(conv -> {
            UnitConversion entity = UnitConversion.builder()
                    .variant(variant)
                    .fromUnit(conv.getFromUnit())
                    .toUnit(conv.getToUnit())
                    .rate(conv.getRate())
                    .build();
            UnitConversion saved = unitConversionRepository.save(entity);
            return UnitConversionResponse.builder()
                    .id(saved.getId())
                    .fromUnit(saved.getFromUnit())
                    .toUnit(saved.getToUnit())
                    .rate(saved.getRate())
                    .build();
        }).collect(Collectors.toList());
    }

    private String serializeCustomSpecs(List<VariantRequest.CustomSpecDto> specs) {
        if (specs == null || specs.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(specs);
        } catch (JsonProcessingException e) {
            log.warn("Không thể serialize customSpecs: {}", e.getMessage());
            return null;
        }
    }

    private CreateProductResponse buildResponse(
            Product product,
            Category category,
            List<String> imageUrls,
            List<ProductVariantResponse> variants) {

        return CreateProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .slug(product.getSlug())
                .description(product.getDescription())
                .status(product.getStatus() != null ? product.getStatus().name() : null)
                .origin(product.getOrigin())
                .baseSku(product.getBaseSku())
                .categoryName(category.getName())
                .brandName(product.getBrand() != null ? product.getBrand().getName() : null)
                .imageUrls(imageUrls)
                .variants(variants)
                .build();
    }

    private ProductStatus parseProductStatus(String status) {
        if (status == null || status.isBlank()) return ProductStatus.DRAFT;
        try {
            return ProductStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ProductStatus.DRAFT;
        }
    }

    // =========================================================================
    // PRIVATE HELPERS – LEGACY UPDATE
    // =========================================================================

    private void mapBasicInfoLegacy(Product product, ProductRequest request) {
        product.setName(request.getName());
        product.setOrigin(request.getOrigin());
        product.setBaseSku(request.getBaseSku());
        product.setDescription(request.getDescription());

        if ("active".equalsIgnoreCase(request.getStatus())) {
            product.setStatus(ProductStatus.ACTIVE);
        } else if ("inactive".equalsIgnoreCase(request.getStatus())) {
            product.setStatus(ProductStatus.INACTIVE);
        } else {
            product.setStatus(ProductStatus.DRAFT);
        }

        if (request.getCategoryId() != null) {
            categoryRepository.findById(request.getCategoryId()).ifPresent(product::setCategory);
        }

        if (request.getBrand() != null && !request.getBrand().trim().isEmpty()) {
            String brandName = request.getBrand().trim();
            Brand brand = brandRepository.findByName(brandName)
                    .orElseGet(() -> brandRepository.save(Brand.builder()
                            .name(brandName)
                            .status(BrandStatus.ACTIVE)
                            .build()));
            product.setBrand(brand);
        }
    }

    private void saveLegacyImages(Product product, List<String> imageUrls) {
        if (imageUrls != null) {
            imageUrls.forEach(url -> imageRepository.save(ProductImage.builder()
                    .imageUrl(url)
                    .product(product)
                    .build()));
        }
    }

    private void saveLegacyVariants(Product product, List<ProductRequest.VariantDto> variants) {
        for (ProductRequest.VariantDto vDto : variants) {
            ProductVariant variant = ProductVariant.builder()
                    .product(product)
                    .sku(vDto.getSku())
                    .barcode(vDto.getBarcode())
                    .price(vDto.getPrice())
                    .importPrice(vDto.getCostPrice())
                    .wholesalePrice(vDto.getWholesalePrice())
                    .quantity(vDto.getInitialStock())
                    .weightValue(vDto.getNetWeight())
                    .netWeightUnit(vDto.getNetWeightUnit())
                    .shippingWeight(vDto.getShippingWeight())
                    .imageUrl(vDto.getImage())
                    .status(VariantStatus.ACTIVE)
                    .build();

            ProductVariant savedVariant = variantRepository.save(variant);

            if (vDto.getAttributes() != null) {
                for (ProductRequest.AttributeDto attrDto : vDto.getAttributes()) {
                    if (attrDto.getName() == null || attrDto.getName().isBlank()) continue;
                    String attrName = attrDto.getName().trim();
                    Attribute attribute = attributeRepository.findByNameIgnoreCase(attrName)
                            .orElseGet(() -> attributeRepository.save(Attribute.builder()
                                    .name(attrName)
                                    .code(toSlug(attrName))
                                    .status(AttributeStatus.ACTIVE)
                                    .build()));
                    variantAttributeRepository.save(VariantAttribute.builder()
                            .variant(savedVariant)
                            .attribute(attribute)
                            .value(attrDto.getValue())
                            .build());
                }
            }
        }
    }

    // =========================================================================
    // UTILITY
    // =========================================================================

    private String toSlug(String input) {
        if (input == null) return "";
        String noWhitespace = input.trim().replaceAll("\\s+", "-");
        String normalized = Normalizer.normalize(noWhitespace, Normalizer.Form.NFD);
        String slug = normalized.replaceAll("[^\\w-]", "");
        return slug.toLowerCase(Locale.ENGLISH);
    }
}
