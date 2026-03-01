package com.zone.agri.service;

import com.zone.agri.common.CloudinaryService;
import com.zone.agri.dto.admin.CategoryDTO;
import com.zone.agri.dto.product.*;
import com.zone.agri.entity.*;
import com.zone.agri.entity.enums.*;
import com.zone.agri.exception.BadRequestException;
import com.zone.agri.exception.ConflictException;
import com.zone.agri.exception.NotFoundException;
import com.zone.agri.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;


import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductImageRepository imageRepository;
    private final BrandRepository brandRepository;
    private final CategoryRepository categoryRepository;
    private final AttributeValueRepository attributeValueRepository;
    private final SKUAttributeValueRepository skuAttributeValueRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final InventoryNoteRepository inventoryNoteRepository;
    private final InventoryNoteDetailRepository inventoryNoteDetailRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryTransferRepository inventoryTransferRepository;
    private final CloudinaryService cloudinaryService;
    private final SettingService settingService;

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
                .baseSku(request.getBaseSku())
                .category(category)
                .createdAt(LocalDateTime.now())
                .build();

        if (request.getBrand() != null && !request.getBrand().isBlank()) {
            product.setBrand(getOrCreateBrand(request.getBrand()));
        }

        Product savedProduct = productRepository.save(product);

        // 👉 FIX LỖI JAVA: Bọc List trong HashSet để ép kiểu về Set cho Entity
        List<ProductImage> savedImages = uploadAndSaveProductImages(savedProduct, productImages);
        savedProduct.setProductImages(new HashSet<>(savedImages));

        List<ProductVariant> savedVariants = saveVariantsWithImages(savedProduct, request.getVariants(), variantImages);
        savedProduct.setVariants(new HashSet<>(savedVariants));

        log.info("Tạo sản phẩm thành công: id={}, name={}", savedProduct.getId(), savedProduct.getName());
        return convertToResponse(savedProduct);
    }

    // =========================================================================
    // UPDATE (JSON - Legacy)
    // =========================================================================

    @Transactional
    public ProductResponse updateProduct(Long id, ProductRequest request,
                                         List<MultipartFile> productImages,
                                         List<MultipartFile> variantImages) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Sản phẩm không tồn tại với ID: " + id));

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

        // 👉 1. XÓA ẢNH CHÍNH BỊ BỎ ĐI & THÊM ẢNH MỚI
        List<String> keepImages = request.getImages() != null ? request.getImages() : new ArrayList<>();
        if (product.getProductImages() != null) {
            // Xóa ảnh khỏi DB nếu FE không gửi lại link đó nữa
            product.getProductImages().removeIf(img -> !keepImages.contains(img.getImageUrl()));
        } else {
            product.setProductImages(new HashSet<>());
        }

        // Up ảnh chính MỚI lên Cloudinary
        if (productImages != null) {
            for (MultipartFile file : productImages) {
                if (file != null && !file.isEmpty()) {
                    CloudinaryService.UploadResult res = cloudinaryService.upload(file, "products/main");
                    product.getProductImages().add(ProductImage.builder().imageUrl(res.secureUrl()).product(product).build());
                }
            }
        }

        // 👉 2. CLEAR VARIANT CŨ VÀ FLUSH (Tránh lỗi OptimisticLocking)
        if (product.getVariants() != null) {
            product.getVariants().clear();
        } else {
            product.setVariants(new HashSet<>());
        }
        productRepository.saveAndFlush(product);

        // 👉 3. THÊM VARIANT VÀ UP ẢNH VARIANT MỚI
        for (int i = 0; i < request.getVariants().size(); i++) {
            ProductRequest.VariantDto vDto = request.getVariants().get(i);
            String finalImageUrl = vDto.getImage(); // URL cũ gửi từ FE (nếu có)

            // Nếu vị trí index này có file mới -> up lên lấy link mới
            if (variantImages != null && i < variantImages.size()) {
                MultipartFile vFile = variantImages.get(i);
                if (vFile != null && !vFile.isEmpty()) {
                    CloudinaryService.UploadResult res = cloudinaryService.upload(vFile, "products/variants");
                    finalImageUrl = res.secureUrl();
                }
            }

            ProductVariant variant = ProductVariant.builder()
                    .product(product)
                    .sku(vDto.getSku())
                    .barcode(vDto.getBarcode())
                    .imageUrl(finalImageUrl) // Dùng link mới up hoặc link cũ
                    .status(VariantStatus.ACTIVE)
                    .build();

            ProductVariant savedVariant = variantRepository.save(variant);

            if (vDto.getAttributeValueIds() != null) {
                List<SKUAttributeValue> attrList = new ArrayList<>();
                for (Long valId : vDto.getAttributeValueIds()) {
                    AttributeValue attrValue = attributeValueRepository.findById(valId)
                            .orElseThrow(() -> new NotFoundException("Thuộc tính ko tồn tại: " + valId));

                    SKUAttributeValue savedAttr = skuAttributeValueRepository.save(SKUAttributeValue.builder()
                            .sku(savedVariant).attribute(attrValue.getAttribute()).attributeValue(attrValue).build());
                    attrList.add(savedAttr);
                }
                savedVariant.setAttributeValues(attrList);
                variantRepository.save(savedVariant);
            }
            product.getVariants().add(savedVariant);
        }

        return convertToResponse(productRepository.save(product));
    }

    // =========================================================================
    // DELETE & DISABLE
    // =========================================================================

    @Transactional
    public void deleteProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Sản phẩm không tồn tại với ID: " + id));

        boolean hasOrder = orderItemRepository.existsByProductVariantProductId(id);
        boolean hasInventoryNote = inventoryNoteDetailRepository.existsByProductVariantProductId(id);
        boolean hasTransaction = inventoryTransactionRepository.existsByInventoryProductVariantProductId(id);

        if (hasOrder || hasInventoryNote || hasTransaction) {
            throw new ConflictException("Sản phẩm đã phát sinh giao dịch, không thể xóa. Vui lòng ngừng kinh doanh.");
        }

        Long totalStock = inventoryRepository.sumQuantityByProductId(id);
        if (totalStock != null && totalStock > 0) {
            throw new ConflictException("Sản phẩm vẫn còn tồn kho, không thể xóa.");
        }

        if (product.getProductImages() != null) {
            product.getProductImages().forEach(img -> {
                if (img.getPublicId() != null) cloudinaryService.delete(img.getPublicId());
            });
        }
        if (product.getVariants() != null) {
            product.getVariants().forEach(variant -> {
                if (variant.getImagePublicId() != null) {
                    cloudinaryService.delete(variant.getImagePublicId());
                }
            });
        }

        productRepository.delete(product);
        log.info("Xóa sản phẩm thành công: id={}", id);
    }

    @Transactional
    public void disableProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Sản phẩm không tồn tại với ID: " + id));

        boolean hasPendingOrder = orderRepository.existsByStatusInAndProductId(
                Arrays.asList(OrderStatus.PENDING, OrderStatus.PROCESSING, OrderStatus.SHIPPING), id);

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

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        return userRepository.findByEmail(auth.getName()).orElse(null);
    }

    public ProductResponse convertToResponse(Product product) {
        User currentUser = getCurrentUser();

        // 👉 LẤY HỆ SỐ NHÂN TỪ SETTING SERVICE (Ví dụ: 30% -> 1.3)
        BigDecimal profitMultiplier = settingService.getProfitMultiplier();

        List<String> imageUrls = product.getProductImages() != null ?
                product.getProductImages().stream().map(ProductImage::getImageUrl).collect(Collectors.toList()) :
                Collections.emptyList();

        // 👉 TRUYỀN HỆ SỐ profitMultiplier VÀO HÀM MAP BIẾN THẾ
        List<ProductVariantResponse> variantResponses = product.getVariants() != null ?
                product.getVariants().stream()
                        .map(variant -> mapVariantToResponse(variant, currentUser, profitMultiplier))
                        .collect(Collectors.toList()) :
                Collections.emptyList();

        int totalInventory = variantResponses.stream()
                .mapToInt(v -> v.getQuantity() != null ? v.getQuantity() : 0)
                .sum();

        // ... (Phần map Category và Brand giữ nguyên như code cũ của Huy)
        CategoryDTO categoryDTO = null;
        if (product.getCategory() != null) {
            categoryDTO = new CategoryDTO();
            categoryDTO.setId(product.getCategory().getId());
            categoryDTO.setName(product.getCategory().getName());
            categoryDTO.setStatus(product.getCategory().getStatus());
        }

        BrandResponse brandResponse = null;
        if (product.getBrand() != null) {
            brandResponse = BrandResponse.builder()
                    .id(product.getBrand().getId())
                    .name(product.getBrand().getName())
                    .build();
        }

        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .slug(product.getSlug())
                .description(product.getDescription())
                .status(product.getStatus() != null ? product.getStatus().name() : null)
                .origin(product.getOrigin())
                .baseSku(product.getBaseSku())
                .categoryName(product.getCategory() != null ? product.getCategory().getName() : null)
                .brandName(product.getBrand() != null ? product.getBrand().getName() : null)
                .category(categoryDTO)
                .brand(brandResponse)
                .inventory(totalInventory)
                .imageUrls(imageUrls)
                .variants(variantResponses)
                .build();
    }

    // 1. Hàm overload để không làm gãy các code cũ đang gọi
    public ProductVariantResponse mapVariantToResponse(ProductVariant variant) {
        return mapVariantToResponse(variant, getCurrentUser(), settingService.getProfitMultiplier());
    }

    public ProductVariantResponse mapVariantToResponse(ProductVariant variant, User currentUser, BigDecimal multiplier) {
        boolean isAdmin = currentUser != null && "ADMIN".equals(currentUser.getRole().getSlug());
        Branch currentBranch = currentUser != null ? currentUser.getBranch() : null;

        List<Inventory> allInventories = inventoryRepository.findByProductVariantId(variant.getId());

        List<Inventory> validBatches = allInventories.stream()
                .filter(inv -> inv.getQuantity() != null && inv.getQuantity() > 0)
                .filter(inv -> isAdmin || (currentBranch != null && inv.getBranch().getId().equals(currentBranch.getId())))
                .collect(Collectors.toList());

        int displayQuantity = validBatches.stream().mapToInt(Inventory::getQuantity).sum();
        BigDecimal maxPrice = BigDecimal.ZERO;

        // 👇 THÊM BIẾN NÀY ĐỂ CHỨA GIÁ NHẬP
        BigDecimal maxImportPrice = BigDecimal.ZERO;

        List<ProductVariantResponse.BatchInfoDto> batchDtos = validBatches.stream().map(inv -> {
            BigDecimal importPrice = inv.getImportPrice() != null ? inv.getImportPrice() : BigDecimal.ZERO;
            BigDecimal sellingPrice = importPrice.multiply(multiplier);

            return ProductVariantResponse.BatchInfoDto.builder()
                    .inventoryId(inv.getId())
                    .branchName(inv.getBranch() != null ? inv.getBranch().getName() : "Kho tổng")
                    .batchNumber(inv.getBatchNumber() != null ? inv.getBatchNumber() : "Chưa xác định")
                    .quantity(inv.getQuantity())
                    .importPrice(isAdmin ? importPrice : null)
                    .sellingPrice(sellingPrice)
                    .build();
        }).collect(Collectors.toList());

        if (!batchDtos.isEmpty()) {
            maxPrice = batchDtos.stream()
                    .map(ProductVariantResponse.BatchInfoDto::getSellingPrice)
                    .max(BigDecimal::compareTo)
                    .orElse(BigDecimal.ZERO);
            maxImportPrice = batchDtos.stream()
                    .filter(b -> b.getImportPrice() != null)
                    .map(ProductVariantResponse.BatchInfoDto::getImportPrice)
                    .max(BigDecimal::compareTo)
                    .orElse(BigDecimal.ZERO);
        }

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
                .productName(variant.getProduct() != null ? variant.getProduct().getName() : null)
                .quantity(displayQuantity)
                .price(maxPrice)
                .importPrice(maxImportPrice)

                .imageUrl(variant.getImageUrl())
                .status(variant.getStatus())
                .attributeValues(attributeValues)
                .batches(batchDtos)
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

    private List<ProductImage> uploadAndSaveProductImages(Product product, List<MultipartFile> files) {
        List<ProductImage> savedImages = new ArrayList<>();
        if (files == null || files.isEmpty()) return savedImages;

        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) continue;
            CloudinaryService.UploadResult result = cloudinaryService.upload(file, "products/main");
            ProductImage img = imageRepository.save(ProductImage.builder()
                    .imageUrl(result.secureUrl())
                    .publicId(result.publicId())
                    .product(product)
                    .build());
            savedImages.add(img);
        }
        return savedImages;
    }

    private List<ProductVariant> saveVariantsWithImages(
            Product product,
            List<VariantRequest> variantRequests,
            List<MultipartFile> variantImages) {

        List<ProductVariant> savedList = new ArrayList<>();
        if (variantRequests == null) return savedList;

        for (int i = 0; i < variantRequests.size(); i++) {
            VariantRequest vReq = variantRequests.get(i);

            String imageUrl = null;
            String imagePublicId = null;

            // 👉 FIX 1: Nhận đúng ảnh từ danh sách MultipartFile gửi lên
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
                    .imageUrl(imageUrl)
                    .imagePublicId(imagePublicId)
                    .status(VariantStatus.ACTIVE)
                    .build();

            // Lưu variant lần 1 để lấy ID
            ProductVariant savedVariant = variantRepository.save(variant);

            if (vReq.getAttributeValueIds() != null) {
                List<SKUAttributeValue> attrList = new ArrayList<>();
                for (Long valueId : vReq.getAttributeValueIds()) {
                    AttributeValue attrValue = attributeValueRepository.findById(valueId)
                            .orElseThrow(() -> new NotFoundException("Giá trị thuộc tính không tồn tại ID: " + valueId));

                    SKUAttributeValue savedAttr = skuAttributeValueRepository.save(SKUAttributeValue.builder()
                            .sku(savedVariant)
                            .attribute(attrValue.getAttribute())
                            .attributeValue(attrValue)
                            .build());
                    attrList.add(savedAttr);
                }
                savedVariant.setAttributeValues(attrList);
                variantRepository.save(savedVariant);
            }
            savedList.add(savedVariant);
        }
        return savedList;
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
        return normalized.replaceAll("[\\p{InCombiningDiacriticalMarks}]", "")
                .toLowerCase(Locale.ENGLISH)
                .replaceAll("[^a-z0-9\\s]", "")
                .replaceAll("\\s+", "-");
    }

    public List<ProductResponse> getProductsForSale() {
        return productRepository.findProductsForSale().stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    public List<ProductResponse> getTopBestSellers(int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return productRepository.findTopBestSellers(pageable).stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    public ProductResponse getProductDetailForUser(String slug) {
        Product product = productRepository.findBySlug(slug)
                .orElseThrow(() -> new NotFoundException("Sản phẩm không tồn tại hoặc đã bị xóa."));

        if (product.getStatus() == ProductStatus.INACTIVE) {
            throw new BadRequestException("Sản phẩm này hiện tại không còn kinh doanh.");
        }

        return convertToResponse(product);
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> getPublicProductsByCategoryId(Long categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy danh mục với ID: " + categoryId));

        if (category.getStatus() != CategoryStatus.ACTIVE) {
            throw new BadRequestException("Danh mục không hoạt động.");
        }

        Page<Long> productIdsPage = productRepository.findPublicProductIds(null, categoryId, null, PageRequest.of(0, Integer.MAX_VALUE));
        List<Long> productIds = productIdsPage.getContent();

        if (productIds.isEmpty()) return Collections.emptyList();
        return productRepository.findPublicByIds(productIds).stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> getPublicProductsByBrandId(Long brandId) {
        Brand brand = brandRepository.findById(brandId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy thương hiệu với ID: " + brandId));

        if (brand.getStatus() != BrandStatus.ACTIVE) {
            throw new BadRequestException("Thương hiệu không hoạt động.");
        }

        Page<Long> productIdsPage = productRepository.findPublicProductIds(null, null, brandId, PageRequest.of(0, Integer.MAX_VALUE));
        List<Long> productIds = productIdsPage.getContent();

        if (productIds.isEmpty()) return Collections.emptyList();
        return productRepository.findPublicByIds(productIds).stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> getPublicProducts(String keyword, Long categoryId, Long brandId, Pageable pageable) {
        Page<Long> productIdsPage = productRepository.findPublicProductIds(keyword, categoryId, brandId, pageable);
        List<Long> productIds = productIdsPage.getContent();

        if (productIds.isEmpty()) return Page.empty(pageable);

        List<ProductResponse> productResponses = productRepository.findPublicByIds(productIds).stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());

        return new PageImpl<>(productResponses, pageable, productIdsPage.getTotalElements());
    }
}