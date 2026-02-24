package com.zone.agri.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zone.agri.common.CloudinaryService;
import com.zone.agri.dto.product.*;
import com.zone.agri.entity.*;
import com.zone.agri.entity.enums.*;
import com.zone.agri.exception.BadRequestException;
import com.zone.agri.exception.ConflictException;
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
    private final AttributeValueRepository attributeValueRepository;
    private final SKUAttributeValueRepository skuAttributeValueRepository;
    private final UnitConversionRepository unitConversionRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final InventoryNoteRepository inventoryNoteRepository;
    private final InventoryNoteDetailRepository inventoryNoteDetailRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryTransferRepository inventoryTransferRepository;
    private final CloudinaryService cloudinaryService;
    private final ObjectMapper objectMapper;

    // =========================================================================
    // READ METHODS
    // =========================================================================

    public List<ProductResponse> getAll(String keyword, Long categoryId, String statusStr) {
        ProductStatus status = null;
        if (statusStr != null && !statusStr.isBlank()) {
            try {
                status = ProductStatus.valueOf(statusStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                // Ignore invalid status
            }
        }

        return productRepository.findAllWithFilter(keyword, categoryId, status).stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    public List<ProductResponse> getAll() {
        return productRepository.findAllWithDetails().stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    public ProductResponse getById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy sản phẩm với ID: " + id));
        return convertToResponse(product);
    }

    // =========================================================================
    // CREATE (Multipart/form-data)
    // =========================================================================

    @Transactional
    public ProductResponse createProduct(
            CreateProductRequest request,
            List<MultipartFile> productImages,
            List<MultipartFile> variantImages) {

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new NotFoundException("Danh mục không tồn tại với ID: " + request.getCategoryId()));

        validateSkusInRequest(request.getVariants());

        Product product = Product.builder()
                .name(request.getName())
                .slug(toSlug(request.getName()) + "-" + System.currentTimeMillis())
                .description(request.getDescription())
                .status(parseProductStatus(request.getStatus()))
                .origin(request.getOrigin())
                .category(category)
                .createdAt(LocalDateTime.now())
                .build();

        if (request.getBrand() != null && !request.getBrand().isBlank()) {
            product.setBrand(getOrCreateBrand(request.getBrand()));
        }

        Product savedProduct = productRepository.save(product);

        // Upload & Save Main Images
        uploadAndSaveProductImages(savedProduct, productImages);

        // Save Variants
        saveVariantsWithImages(savedProduct, request.getVariants(), variantImages);

        log.info("Tạo sản phẩm thành công: id={}, name={}", savedProduct.getId(), savedProduct.getName());

        // Re-fetch to get all relations for response
        return getById(savedProduct.getId());
    }

    // =========================================================================
    // UPDATE (JSON - Legacy & Mixed)
    // =========================================================================

    @Transactional
    public ProductResponse updateProduct(Long id, ProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Sản phẩm không tồn tại với ID: " + id));

        if (request.getVariants() == null || request.getVariants().isEmpty()) {
            throw new BadRequestException("Sản phẩm phải có ít nhất 1 biến thể!");
        }

        // Update basic info
        product.setName(request.getName());
        product.setOrigin(request.getOrigin());
        product.setDescription(request.getDescription());
        product.setStatus(parseProductStatus(request.getStatus()));

        if (request.getCategoryId() != null) {
            categoryRepository.findById(request.getCategoryId()).ifPresent(product::setCategory);
        }

        if (request.getBrand() != null && !request.getBrand().trim().isEmpty()) {
            product.setBrand(getOrCreateBrand(request.getBrand()));
        }

        Product updatedProduct = productRepository.save(product);

        // Clear existing related data (Simple destructive update for now)
        productRepository.deleteVariantAttributesByProduct(updatedProduct);
        productRepository.deleteUnitConversionsByProduct(updatedProduct);
        productRepository.deleteVariantsByProduct(updatedProduct);
        productRepository.deleteImagesByProduct(updatedProduct);

        // Re-save images from URLs in request
        if (request.getImages() != null) {
            request.getImages().forEach(url -> imageRepository.save(ProductImage.builder()
                    .imageUrl(url)
                    .product(updatedProduct)
                    .build()));
        }

        // Re-save variants
        for (ProductRequest.VariantDto vDto : request.getVariants()) {
            ProductVariant variant = ProductVariant.builder()
                    .product(updatedProduct)
                    .sku(vDto.getSku())
                    .barcode(vDto.getBarcode())
                    .price(vDto.getPrice())
                    .importPrice(vDto.getCostPrice())
                    .wholesalePrice(vDto.getWholesalePrice())
                    .quantity(vDto.getInitialStock())
                    .shippingWeight(vDto.getShippingWeight())
                    .imageUrl(vDto.getImage())
                    .status(VariantStatus.ACTIVE)
                    .build();

            ProductVariant savedVariant = variantRepository.save(variant);

            if (vDto.getAttributeValueIds() != null) {
                for (Long valId : vDto.getAttributeValueIds()) {
                    AttributeValue attrValue = attributeValueRepository.findById(valId)
                            .orElseThrow(() -> new NotFoundException("Giá trị thuộc tính không tồn tại ID: " + valId));

                    skuAttributeValueRepository.save(SKUAttributeValue.builder()
                            .sku(savedVariant)
                            .attribute(attrValue.getAttribute())
                            .attributeValue(attrValue)
                            .build());
                }
            }
        }

        return getById(updatedProduct.getId());
    }

    // =========================================================================
    // DELETE & DISABLE
    // =========================================================================

    @Transactional
    public void deleteProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Sản phẩm không tồn tại với ID: " + id));

        // 1. Kiểm tra phát sinh giao dịch
        boolean hasOrder = orderItemRepository.existsByProductVariantProductId(id);
        boolean hasInventoryNote = inventoryNoteDetailRepository.existsByProductVariantProductId(id);
        boolean hasTransaction = inventoryTransactionRepository.existsByInventoryProductVariantProductId(id);

        if (hasOrder || hasInventoryNote || hasTransaction) {
            throw new ConflictException("Sản phẩm đã phát sinh giao dịch, không thể xóa. Vui lòng ngừng kinh doanh.");
        }

        // 2. Kiểm tra tồn kho
        Integer totalStock = inventoryRepository.sumQuantityByProductId(id);
        if (totalStock != null && totalStock > 0) {
            throw new ConflictException("Sản phẩm vẫn còn tồn kho, không thể xóa.");
        }

        // 3. Xóa ảnh trên Cloudinary
        if (product.getProductImages() != null) {
            product.getProductImages().forEach(img -> cloudinaryService.delete(img.getPublicId()));
        }
        if (product.getVariants() != null) {
            product.getVariants().forEach(variant -> cloudinaryService.delete(variant.getImagePublicId()));
        }

        // 4. Xóa từ DB (Sử dụng cơ chế Cascade đã cấu hình trong Entity)
        productRepository.delete(product);
        
        log.info("Xóa sản phẩm thành công: id={}", id);
    }

    @Transactional
    public void disableProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Sản phẩm không tồn tại với ID: " + id));

        // 1. Kiểm tra đơn hàng đang xử lý
        boolean hasPendingOrder = orderRepository.existsByStatusInAndProductId(
                Arrays.asList(OrderStatus.PENDING, OrderStatus.PROCESSING, OrderStatus.SHIPPING), id);

        // 2. Kiểm tra giao dịch kho đang mở (InventoryNote PENDING hoặc InventoryTransfer PENDING/SHIPPING)
        boolean hasPendingNote = inventoryNoteRepository.existsByStatusInAndProductId(
                Collections.singletonList(InventoryNoteStatus.PENDING), id);
        
        boolean hasPendingTransfer = inventoryTransferRepository.existsByStatusInAndProductId(
                Arrays.asList(InventoryTransferStatus.PENDING, InventoryTransferStatus.SHIPPING), id);

        if (hasPendingOrder || hasPendingNote || hasPendingTransfer) {
            throw new ConflictException("Không thể ngừng kinh doanh vì còn giao dịch chưa hoàn tất.");
        }

        product.setStatus(ProductStatus.INACTIVE);
        if (product.getVariants() != null) {
            product.getVariants().forEach(v -> v.setStatus(VariantStatus.INACTIVE));
        }
        productRepository.save(product);
        
        log.info("Ngừng kinh doanh sản phẩm thành công: id={}", id);
    }

    // =========================================================================
    // HELPERS & MAPPERS
    // =========================================================================

    public ProductResponse convertToResponse(Product product) {
        List<String> imageUrls = product.getProductImages() != null ?
                product.getProductImages().stream().map(ProductImage::getImageUrl).collect(Collectors.toList()) :
                Collections.emptyList();

        List<ProductVariantResponse> variantResponses = product.getVariants() != null ?
                product.getVariants().stream().map(this::mapVariantToResponse).collect(Collectors.toList()) :
                Collections.emptyList();

        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .slug(product.getSlug())
                .description(product.getDescription())
                .status(product.getStatus() != null ? product.getStatus().name() : null)
                .origin(product.getOrigin())
                .categoryName(product.getCategory() != null ? product.getCategory().getName() : null)
                .brandName(product.getBrand() != null ? product.getBrand().getName() : null)
                .imageUrls(imageUrls)
                .variants(variantResponses)
                .build();
    }

    private ProductVariantResponse mapVariantToResponse(ProductVariant variant) {
        List<UnitConversionResponse> conversions = variant.getUnitConversions() != null ?
                variant.getUnitConversions().stream().map(uc -> UnitConversionResponse.builder()
                        .id(uc.getId())
                        .fromUnit(uc.getFromUnit())
                        .toUnit(uc.getToUnit())
                        .rate(uc.getRate())
                        .build()).collect(Collectors.toList()) :
                Collections.emptyList();

        List<AttributeValueResponse> attributeValues = variant.getAttributeValues() != null ?
                variant.getAttributeValues().stream().map(sav -> AttributeValueResponse.builder()
                        .attributeId(sav.getAttribute().getId())
                        .attributeName(sav.getAttribute().getName())
                        .attributeCode(sav.getAttribute().getCode())
                        .valueId(sav.getAttributeValue().getId())
                        .value(sav.getAttributeValue().getValue())
                        .build()).collect(Collectors.toList()) :
                Collections.emptyList();

        return ProductVariantResponse.builder()
                .id(variant.getId())
                .sku(variant.getSku())
                .barcode(variant.getBarcode())
                .costPrice(variant.getImportPrice())
                .price(variant.getPrice())
                .wholesalePrice(variant.getWholesalePrice())
                .quantity(variant.getQuantity())
                .shippingWeight(variant.getShippingWeight())
                .imageUrl(variant.getImageUrl())
                .status(variant.getStatus())
                .attributeValues(attributeValues)
                .unitConversions(conversions)
                .build();
    }

    private Brand getOrCreateBrand(String name) {
        return brandRepository.findByName(name.trim())
                .orElseGet(() -> brandRepository.save(Brand.builder()
                        .name(name.trim())
                        .status(BrandStatus.ACTIVE)
                        .build()));
    }

    private void validateSkusInRequest(List<VariantRequest> variants) {
        if (variants == null) return;
        Set<String> seen = new HashSet<>();
        for (VariantRequest v : variants) {
            if (!seen.add(v.getSku())) {
                throw new BadRequestException("Mã SKU bị trùng lặp trong request: " + v.getSku());
            }
        }
    }

    private void uploadAndSaveProductImages(Product product, List<MultipartFile> files) {
        if (files == null || files.isEmpty()) return;
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) continue;
            CloudinaryService.UploadResult result = cloudinaryService.upload(file, "products/main");
            imageRepository.save(ProductImage.builder()
                    .imageUrl(result.secureUrl())
                    .publicId(result.publicId())
                    .product(product)
                    .build());
        }
    }

    private void saveVariantsWithImages(
            Product product,
            List<VariantRequest> variantRequests,
            List<MultipartFile> variantImages) {

        if (variantRequests == null) return;

        for (int i = 0; i < variantRequests.size(); i++) {
            VariantRequest vReq = variantRequests.get(i);

            String imageUrl = null;
            String imagePublicId = null;
            if (variantImages != null && i < variantImages.size()) {
                MultipartFile imgFile = variantImages.get(i);
                if (imgFile != null && !imgFile.isEmpty()) {
                    CloudinaryService.UploadResult result = cloudinaryService.upload(imgFile, "products/variants");
                    imageUrl = result.secureUrl();
                    imagePublicId = result.publicId();
                }
            }

            ProductVariant variant = ProductVariant.builder()
                    .product(product)
                    .sku(vReq.getSku())
                    .barcode(vReq.getBarcode())
                    .price(vReq.getPrice())
                    .importPrice(vReq.getCostPrice())
                    .wholesalePrice(vReq.getWholesalePrice())
                    .quantity(vReq.getInitialStock() != null ? vReq.getInitialStock() : 0L)
                    .shippingWeight(vReq.getShippingWeight())
                    .imageUrl(imageUrl)
                    .imagePublicId(imagePublicId)
                    .status(VariantStatus.ACTIVE)
                    .build();

            ProductVariant savedVariant = variantRepository.save(variant);

            // Save SKU Attribute Values
            if (vReq.getAttributeValueIds() != null) {
                for (Long valueId : vReq.getAttributeValueIds()) {
                    AttributeValue attrValue = attributeValueRepository.findById(valueId)
                            .orElseThrow(() -> new NotFoundException("Giá trị thuộc tính không tồn tại ID: " + valueId));

                    skuAttributeValueRepository.save(SKUAttributeValue.builder()
                            .sku(savedVariant)
                            .attribute(attrValue.getAttribute())
                            .attributeValue(attrValue)
                            .build());
                }
            }

            // Save Unit Conversions
            if (vReq.getUnitConversions() != null) {
                for (UnitConversionRequest conv : vReq.getUnitConversions()) {
                    unitConversionRepository.save(UnitConversion.builder()
                            .variant(savedVariant)
                            .fromUnit(conv.getFromUnit())
                            .toUnit(conv.getToUnit())
                            .rate(conv.getRate())
                            .build());
                }
            }
        }
    }

    private ProductStatus parseProductStatus(String status) {
        if (status == null || status.isBlank()) return ProductStatus.DRAFT;
        try {
            return ProductStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ProductStatus.DRAFT;
        }
    }

    private String toSlug(String input) {
        if (input == null) return "";
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        String slug = normalized.replaceAll("[\\p{InCombiningDiacriticalMarks}]", "")
                .toLowerCase(Locale.ENGLISH)
                .replaceAll("[^a-z0-9\\s]", "")
                .replaceAll("\\s+", "-");
        return slug;
    }
}
