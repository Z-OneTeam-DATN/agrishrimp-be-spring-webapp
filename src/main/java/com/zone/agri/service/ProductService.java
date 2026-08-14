package com.zone.agri.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.zone.agri.common.CloudinaryService;
import com.zone.agri.dto.request.product.CreateProductRequest;
import com.zone.agri.dto.request.product.ProductRequest;
import com.zone.agri.dto.request.product.VariantRequest;
import com.zone.agri.dto.response.ImageSearchResult;
import com.zone.agri.dto.response.admin.CategoryDTO;
import com.zone.agri.dto.response.product.AttributeValueResponse;
import com.zone.agri.dto.response.product.BrandResponse;
import com.zone.agri.dto.response.product.ProductResponse;
import com.zone.agri.dto.response.product.ProductVariantResponse;
import com.zone.agri.entity.AttributeValue;
import com.zone.agri.entity.Branch;
import com.zone.agri.entity.Brand;
import com.zone.agri.entity.Category;
import com.zone.agri.entity.Inventory;
import com.zone.agri.entity.Product;
import com.zone.agri.entity.ProductImage;
import com.zone.agri.entity.ProductVariant;
import com.zone.agri.entity.Role;
import com.zone.agri.entity.SKUAttributeValue;
import com.zone.agri.entity.User;
import com.zone.agri.entity.enums.AttributeStatus;
import com.zone.agri.entity.enums.BranchStatus;
import com.zone.agri.entity.enums.BrandStatus;
import com.zone.agri.entity.enums.CategoryStatus;
import com.zone.agri.entity.enums.InventoryNoteStatus;
import com.zone.agri.entity.enums.InventoryTransferStatus;
import com.zone.agri.entity.enums.OrderStatus;
import com.zone.agri.entity.enums.ProductStatus;
import com.zone.agri.entity.enums.VariantStatus;
import com.zone.agri.exception.BadRequestException;
import com.zone.agri.exception.ConflictException;
import com.zone.agri.exception.NotFoundException;
import com.zone.agri.repository.AttributeValueRepository;
import com.zone.agri.repository.BrandRepository;
import com.zone.agri.repository.CategoryRepository;
import com.zone.agri.repository.InventoryNoteDetailRepository;
import com.zone.agri.repository.InventoryNoteRepository;
import com.zone.agri.repository.InventoryRepository;
import com.zone.agri.repository.InventoryTransactionRepository;
import com.zone.agri.repository.InventoryTransferRepository;
import com.zone.agri.repository.OrderItemRepository;
import com.zone.agri.repository.OrderRepository;
import com.zone.agri.repository.ProductImageRepository;
import com.zone.agri.repository.ProductRepository;
import com.zone.agri.repository.ProductVariantRepository;
import com.zone.agri.repository.ProductVectorRepository;
import com.zone.agri.repository.SKUAttributeValueRepository;
import com.zone.agri.repository.UserRepository;
import com.zone.agri.repository.SupplierRepository;
import com.zone.agri.entity.Supplier;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings({ "boxing", "unboxing" })
public class ProductService {

    private static final int PRICE_FILTER_SCAN_LIMIT = 10_000;
    private static final String DEFAULT_PUBLIC_SORT = "featured";

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductImageRepository imageRepository;
    private final BrandRepository brandRepository;
    private final SupplierRepository supplierRepository;
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
    private final ImageSearchService imageSearchService;
    private final ProductVectorRepository productVectorRepository;

    private enum PublicProductSort {
        FEATURED,
        PRICE_ASC,
        PRICE_DESC,
        NAME_ASC,
        NAME_DESC,
        OLDEST,
        NEWEST,
        BEST_SELLING,
        INVENTORY_DESC
    }

    // =========================================================================
    // READ METHODS
    // =========================================================================

    @Transactional(readOnly = true)
    public List<ProductResponse> getAll(String keyword, Long categoryId, String statusStr) {
        ProductStatus status = null;
        if (statusStr != null && !statusStr.isBlank()) {
            try {
                status = ProductStatus.valueOf(statusStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                // Ignore invalid status
            }
        }

        List<Product> products = productRepository.findAllWithFilter(keyword, categoryId, status);
        Map<Long, Long> soldCountMap = buildSoldCountMap(products.stream().map(Product::getId).toList());

        return convertToResponseList(products, soldCountMap);
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> getAll() {
        List<Product> products = productRepository.findAllWithDetails();
        Map<Long, Long> soldCountMap = buildSoldCountMap(products.stream().map(Product::getId).toList());

        return convertToResponseList(products, soldCountMap);
    }

    @Transactional(readOnly = true)
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

        if (request.getName() != null && productRepository.existsByNameIgnoreCase(request.getName().trim())) {
            throw new ConflictException("Tên sản phẩm đã tồn tại trong hệ thống. Vui lòng chọn tên khác!", true);
        }

        validateSkusInRequest(request.getVariants());
        ProductStatus targetStatus = parseProductStatus(request.getStatus());

        if (targetStatus == ProductStatus.ACTIVE && request.getBrandId() == null) {
            throw new BadRequestException(
                    "Sản phẩm ở trạng thái ACTIVE bắt buộc phải có thương hiệu.");
        }

        Brand brand = null;
        if (request.getBrandId() != null) {
            brand = brandRepository.findById(request.getBrandId())
                    .orElseThrow(() -> new NotFoundException("Thương hiệu không tồn tại với ID: " + request.getBrandId()));
        }

        Product product = Product.builder()
                .name(request.getName())
                .slug(toSlug(request.getName()) + "-" + System.currentTimeMillis())
                .description(request.getDescription())
                .status(targetStatus)
                .baseSku(request.getBaseSku())
                .category(category)
                .brand(brand)
                .createdAt(LocalDateTime.now())
                .build();

        Product savedProduct = productRepository.save(product);
        List<ProductImage> savedImages = uploadAndSaveProductImages(savedProduct, productImages);
        savedProduct.setProductImages(new HashSet<>(savedImages));

        List<ProductVariant> savedVariants = saveVariantsWithImages(savedProduct, request.getVariants(), variantImages);
        savedProduct.setVariants(new HashSet<>(savedVariants));

        log.info("Tạo sản phẩm thành công: id={}, name={}", savedProduct.getId(), savedProduct.getName());

        // Auto-index vào AI service (fire-and-forget, không block nếu AI service bị
        // down)
        resolveIndexImageUrl(savedProduct)
                .ifPresent(imageUrl -> imageSearchService.indexProduct(savedProduct.getId(), imageUrl));

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

        if (request.getName() != null && productRepository.existsByNameIgnoreCaseAndIdNot(request.getName().trim(), id)) {
            throw new ConflictException("Tên sản phẩm đã tồn tại trong hệ thống. Vui lòng chọn tên khác!", true);
        }

        ProductStatus targetStatus = parseProductStatus(request.getStatus());
        if (targetStatus == ProductStatus.ACTIVE && request.getBrandId() == null) {
            throw new BadRequestException("Sản phẩm ở trạng thái ACTIVE bắt buộc phải có thương hiệu.");
        }

        Brand brand = null;
        if (request.getBrandId() != null) {
            brand = brandRepository.findById(request.getBrandId())
                    .orElseThrow(() -> new NotFoundException("Thương hiệu không tồn tại với ID: " + request.getBrandId()));
        }

        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setStatus(targetStatus);
        product.setBrand(brand);

        if (request.getCategoryId() != null) {
            categoryRepository.findById(request.getCategoryId()).ifPresent(product::setCategory);
        }

        // 1. XỬ LÝ ẢNH CHÍNH
        List<String> keepImages = request.getImages() != null ? request.getImages() : new ArrayList<>();

        // Lấy URL ảnh đã index lần trước từ product_vectors (null nếu chưa index)
        String indexedImageUrl = productVectorRepository.findByProductId(id)
                .map(pv -> pv.getImageUrl())
                .orElse(null);
        String requestedIndexImageUrl = null;
        boolean mainImageChanged = false;
        boolean variantImageChanged = false;

        if (product.getProductImages() != null) {
            product.getProductImages().removeIf(img -> !keepImages.contains(img.getImageUrl()));
        } else {
            product.setProductImages(new HashSet<>());
        }

        if (productImages != null) {
            for (MultipartFile file : productImages) {
                if (file != null && !file.isEmpty()) {
                    CloudinaryService.UploadResult res = cloudinaryService.upload(file, "products/main");
                    product.getProductImages()
                            .add(ProductImage.builder().imageUrl(res.secureUrl()).product(product).build());
                    mainImageChanged = true;
                    if (requestedIndexImageUrl == null) {
                        requestedIndexImageUrl = res.secureUrl();
                    }
                }
            }
        }

        // TỪ ĐÂY TRỞ XUỐNG LÀ LOGIC CẬP NHẬT BIẾN THỂ MỚI (KHÔNG DÙNG CLEAR NỮA)

        // Tạo một Map chứa các biến thể cũ để dễ bề tra cứu theo SKU
        Map<String, ProductVariant> existingVariantsMap = new java.util.HashMap<>();
        if (product.getVariants() != null) {
            for (ProductVariant v : product.getVariants()) {
                existingVariantsMap.put(v.getSku(), v);
            }
        }

        Set<ProductVariant> updatedVariants = new HashSet<>();

        // Duyệt qua danh sách biến thể FE gửi lên
        for (int i = 0; i < request.getVariants().size(); i++) {
            ProductRequest.VariantDto vDto = request.getVariants().get(i);
            String finalImageUrl = vDto.getImage();

            if (variantImages != null && i < variantImages.size()) {
                MultipartFile vFile = variantImages.get(i);
                if (vFile != null && !vFile.isEmpty()) {
                    CloudinaryService.UploadResult res = cloudinaryService.upload(vFile, "products/variants");
                    if (finalImageUrl != null && !finalImageUrl.trim().isEmpty()) {
                        finalImageUrl = finalImageUrl + "," + res.secureUrl();
                    } else {
                        finalImageUrl = res.secureUrl();
                    }
                }
            }

            // Kiểm tra xem biến thể này đã tồn tại trong DB chưa (Dựa vào SKU)
            ProductVariant variant = existingVariantsMap.get(vDto.getSku());
            String previousVariantImageUrl = variant != null ? variant.getImageUrl() : null;

            if (variant != null) {
                // NẾU ĐÃ TỒN TẠI -> Cập nhật thông tin đè lên, KHÔNG TẠO MỚI
                variant.setBarcode(vDto.getBarcode());
                if (vDto.getShippingWeight() != null) {
                    variant.setShippingWeight(normalizeShippingWeight(vDto.getShippingWeight()));
                }
                if (finalImageUrl != null) {
                    variant.setImageUrl(finalImageUrl);
                }
                // Xóa list thuộc tính cũ của biến thể này để gán cái mới
                if (variant.getAttributeValues() != null) {
                    skuAttributeValueRepository.deleteAll(variant.getAttributeValues());
                    variant.getAttributeValues().clear();
                }
            } else {
                // NẾU LÀ BIẾN THỂ HOÀN TOÀN MỚI -> Tạo mới
                variant = ProductVariant.builder()
                        .product(product)
                        .sku(vDto.getSku())
                        .barcode(vDto.getBarcode())
                        .imageUrl(finalImageUrl)
                        .shippingWeight(normalizeShippingWeight(vDto.getShippingWeight()))
                        .status(VariantStatus.ACTIVE)
                        .build();
                variant = variantRepository.save(variant);
            }

            // Cập nhật lại danh sách Thuộc tính (Màu sắc, Kích thước...)
            if (vDto.getAttributeValueIds() != null) {
                List<SKUAttributeValue> attrList = new ArrayList<>();
                for (Long valId : vDto.getAttributeValueIds()) {
                    AttributeValue attrValue = attributeValueRepository.findById(valId)
                            .orElseThrow(() -> new NotFoundException("Thuộc tính ko tồn tại: " + valId));

                    SKUAttributeValue savedAttr = skuAttributeValueRepository.save(SKUAttributeValue.builder()
                            .sku(variant).attribute(attrValue.getAttribute()).attributeValue(attrValue).build());
                    attrList.add(savedAttr);
                }
                variant.setAttributeValues(attrList);
                variantRepository.save(variant);
            }

            if (!mainImageChanged && finalImageUrl != null && !Objects.equals(previousVariantImageUrl, finalImageUrl)) {
                variantImageChanged = true;
                if (requestedIndexImageUrl == null) {
                    requestedIndexImageUrl = finalImageUrl;
                }
            }

            updatedVariants.add(variant);
            // Gỡ biến thể này khỏi Map cũ (Những thằng nào còn sót lại trong Map nghĩa là
            // đã bị FE xóa đi)
            existingVariantsMap.remove(vDto.getSku());
        }

        // XÓA CÁC BIẾN THỂ BỊ NGƯỜI DÙNG XÓA TRÊN GIAO DIỆN
        for (ProductVariant deletedVariant : existingVariantsMap.values()) {
            // Kiểm tra xem có hàng trong kho không, nếu có thì không cho xóa
            Long currentStock = inventoryRepository.sumQuantityByProductVariantId(deletedVariant.getId());
            if (currentStock != null && currentStock > 0) {
                throw new ConflictException("Không thể lưu: Biến thể " + deletedVariant.getSku()
                        + " đã bị xóa trên giao diện nhưng vẫn còn tồn kho.");
            }
            // Soft delete — không xóa thật để giữ lịch sử
            deletedVariant.setStatus(VariantStatus.INACTIVE);
        }

        product.getVariants().clear();
        product.getVariants().addAll(updatedVariants);

        // Lưu sản phẩm
        Product updatedProduct = productRepository.save(product);

        // Re-index AI khi ảnh chính hoặc ảnh biến thể thay đổi, hoặc khi ảnh đang index
        // không còn tồn tại
        String resolvedIndexImageUrl = resolveIndexImageUrl(updatedProduct).orElse(null);
        Set<String> availableImageUrls = collectIndexImageUrls(updatedProduct);
        boolean indexedImageGone = indexedImageUrl != null && !availableImageUrls.contains(indexedImageUrl);
        String nextIndexImageUrl = requestedIndexImageUrl != null ? requestedIndexImageUrl : resolvedIndexImageUrl;

        if ((mainImageChanged || variantImageChanged) && nextIndexImageUrl != null) {
            imageSearchService.indexProduct(updatedProduct.getId(), nextIndexImageUrl);
        } else if (indexedImageGone && resolvedIndexImageUrl != null) {
            imageSearchService.indexProduct(updatedProduct.getId(), resolvedIndexImageUrl);
        } else if (indexedImageUrl != null && resolvedIndexImageUrl == null) {
            imageSearchService.deleteIndex(updatedProduct.getId());
        }

        return convertToResponse(updatedProduct);
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
                if (img.getPublicId() != null)
                    cloudinaryService.delete(img.getPublicId());
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

        // Xóa index khỏi AI service (fire-and-forget)
        imageSearchService.deleteIndex(id);
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

    @Transactional
    public void enableProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy sản phẩm có ID: " + id));

        // Kiểm tra danh mục sản phẩm có đang bị khóa (INACTIVE) không
        Category category = product.getCategory();
        if (category != null) {
            Category current = category;
            while (current != null) {
                if (CategoryStatus.INACTIVE.equals(current.getStatus())) {
                    throw new BadRequestException(
                            "Không thể kinh doanh lại do danh mục sản phẩm '" + current.getName() + "' đang bị khóa.");
                }
                current = current.getParent();
            }
        }

        product.setStatus(ProductStatus.ACTIVE);
        if (product.getVariants() != null) {
            product.getVariants().forEach(v -> v.setStatus(VariantStatus.ACTIVE));
        }

        productRepository.save(product);
        log.info("Kích hoạt kinh doanh sản phẩm thành công: id={}", id);
    }

    // =========================================================================
    // IMAGE SEARCH
    // =========================================================================

    /**
     * Tìm kiếm sản phẩm bằng hình ảnh — forward ảnh sang Python AI service.
     * Ném exception nếu AI service bị down (caller xử lý 503).
     */
    public List<ProductResponse> searchByImage(MultipartFile image) {
        List<ImageSearchResult> results = imageSearchService.searchByImage(image);
        List<Long> productIds = results.stream()
                .map(ImageSearchResult::getProductId)
                .toList();
        Map<Long, Product> productMap = productRepository.findAllById(productIds)
                .stream()
                .collect(Collectors.toMap(Product::getId, product -> product));

        return results.stream()
                .sorted(Comparator.comparingDouble(r -> -r.getScore()))
                .map(r -> {
                    Product product = productMap.get(r.getProductId());
                    if (product == null) {
                        return null;
                    }
                    ProductResponse response = convertToResponse(product);
                    response.setSimilarity(r.getScore());
                    return response;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Index toàn bộ sản phẩm có ảnh vào AI service — dùng một lần khi deploy.
     * Trả về số lượng sản phẩm đã được index.
     */
    @Transactional(readOnly = true)
    public int indexAll() {
        List<Product> products = productRepository.findAllWithDetails();
        List<Map<String, Object>> payload = products.stream()
                .map(p -> resolveIndexImageUrl(p)
                        .map(imageUrl -> Map.<String, Object>of("productId", p.getId(), "imageUrl", imageUrl))
                        .orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (payload.isEmpty())
            return 0;
        return imageSearchService.indexBatch(payload);
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

    private Optional<String> resolveIndexImageUrl(Product product) {
        if (product == null) {
            return Optional.empty();
        }

        Optional<String> mainImageUrl = Optional.ofNullable(product.getProductImages())
                .orElse(Collections.emptySet())
                .stream()
                .sorted(Comparator.comparing(ProductImage::getId, Comparator.nullsLast(Long::compareTo)))
                .map(ProductImage::getImageUrl)
                .filter(Objects::nonNull)
                .filter(url -> !url.isBlank())
                .findFirst();

        if (mainImageUrl.isPresent()) {
            return mainImageUrl;
        }

        return Optional.ofNullable(product.getVariants())
                .orElse(Collections.emptySet())
                .stream()
                .sorted(Comparator.comparing(ProductVariant::getId, Comparator.nullsLast(Long::compareTo)))
                .map(ProductVariant::getImageUrl)
                .filter(Objects::nonNull)
                .filter(url -> !url.isBlank())
                .findFirst();
    }

    private Set<String> collectIndexImageUrls(Product product) {
        if (product == null) {
            return Collections.emptySet();
        }

        Set<String> imageUrls = new LinkedHashSet<>();

        Optional.ofNullable(product.getProductImages())
                .orElse(Collections.emptySet())
                .stream()
                .map(ProductImage::getImageUrl)
                .filter(Objects::nonNull)
                .filter(url -> !url.isBlank())
                .forEach(imageUrls::add);

        Optional.ofNullable(product.getVariants())
                .orElse(Collections.emptySet())
                .stream()
                .map(ProductVariant::getImageUrl)
                .filter(Objects::nonNull)
                .filter(url -> !url.isBlank())
                .forEach(imageUrls::add);

        return imageUrls;
    }

    @SuppressWarnings("all")
    private Map<Long, Long> buildSoldCountMap(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, AtomicLong> soldCountMap = new java.util.HashMap<>();

        productRepository.sumLegacySoldQuantityByProductIds(productIds)
                .forEach(row -> {
                    Number productIdValue = (Number) row[0];
                    Number soldQuantityValue = (Number) row[1];
                    if (productIdValue == null || soldQuantityValue == null) {
                        return;
                    }

                    long productId = productIdValue.longValue();
                    long soldQuantity = soldQuantityValue.longValue();
                    soldCountMap.computeIfAbsent(productId, key -> new AtomicLong()).addAndGet(soldQuantity);
                });

        productRepository.sumSubOrderSoldQuantityByProductIds(productIds)
                .forEach(row -> {
                    Number productIdValue = (Number) row[0];
                    Number soldQuantityValue = (Number) row[1];
                    if (productIdValue == null || soldQuantityValue == null) {
                        return;
                    }

                    long productId = productIdValue.longValue();
                    long soldQuantity = soldQuantityValue.longValue();
                    soldCountMap.computeIfAbsent(productId, key -> new AtomicLong()).addAndGet(soldQuantity);
                });

        Map<Long, Long> result = new LinkedHashMap<>();
        soldCountMap.forEach((productId, totalSold) -> result.put(productId, totalSold.get()));
        return result;
    }

    public ProductResponse convertToResponse(Product product) {
        if (product == null) {
            return null;
        }
        return convertToResponse(product, buildSoldCountMap(List.of(product.getId())));
    }

    private List<ProductResponse> convertToResponseList(List<Product> products, Map<Long, Long> soldCountMap) {
        if (products == null || products.isEmpty()) {
            return Collections.emptyList();
        }

        User currentUser = getCurrentUser();
        BigDecimal profitMultiplier = settingService.getProfitMultiplier();
        String roundingRule = settingService.getProfitRoundingRuleRaw();
        Map<Long, List<ProductVariant>> activeVariantsByProductId = buildActiveVariantsByProductId(products);
        Map<Long, List<Inventory>> inventoryMap = loadInventoryMapForVariants(
                activeVariantsByProductId.values().stream()
                        .flatMap(List::stream)
                        .collect(Collectors.toList()));

        return products.stream()
                .map(product -> convertToResponse(
                        product,
                        soldCountMap,
                        currentUser,
                        profitMultiplier,
                        roundingRule,
                        activeVariantsByProductId,
                        inventoryMap))
                .collect(Collectors.toList());
    }

    private List<ProductResponse> convertToPublicListResponseList(List<Product> products, Map<Long, Long> soldCountMap) {
        if (products == null || products.isEmpty()) {
            return Collections.emptyList();
        }

        BigDecimal profitMultiplier = settingService.getProfitMultiplier();
        String roundingRule = settingService.getProfitRoundingRuleRaw();
        boolean multiTierPricingEnabled = settingService.isMultiTierPricingEnabled();
        Map<Long, List<ProductVariant>> activeVariantsByProductId = buildActiveVariantsByProductId(products);
        Map<Long, List<Inventory>> inventoryMap = loadInventoryMapForVariants(
                activeVariantsByProductId.values().stream()
                        .flatMap(List::stream)
                        .collect(Collectors.toList()));

        return products.stream()
                .map(product -> convertToPublicListResponse(
                        product,
                        soldCountMap,
                        profitMultiplier,
                        roundingRule,
                        multiTierPricingEnabled,
                        activeVariantsByProductId,
                        inventoryMap))
                .collect(Collectors.toList());
    }

    private ProductResponse convertToPublicListResponse(Product product,
            Map<Long, Long> soldCountMap,
            BigDecimal profitMultiplier,
            String roundingRule,
            boolean multiTierPricingEnabled,
            Map<Long, List<ProductVariant>> activeVariantsByProductId,
            Map<Long, List<Inventory>> inventoryMap) {

        List<String> imageUrls = product.getProductImages() != null
                ? product.getProductImages().stream().map(ProductImage::getImageUrl).collect(Collectors.toList())
                : Collections.emptyList();

        List<ProductVariant> activeVariants = activeVariantsByProductId != null
                ? activeVariantsByProductId.getOrDefault(product.getId(), Collections.emptyList())
                : getActiveDisplayVariants(product);

        Map<Long, List<Inventory>> safeInventoryMap = inventoryMap != null
                ? inventoryMap
                : Collections.emptyMap();
        List<ProductVariantResponse> variantResponses = activeVariants.stream()
                .map(variant -> mapPublicListVariantToResponse(
                        product,
                        variant,
                        profitMultiplier,
                        roundingRule,
                        multiTierPricingEnabled,
                        safeInventoryMap.getOrDefault(variant.getId(), List.of())))
                .filter(variant -> variant.getQuantity() != null && variant.getQuantity() > 0)
                .collect(Collectors.toList());

        int totalInventory = variantResponses.stream()
                .map(ProductVariantResponse::getQuantity)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();

        CategoryDTO categoryDTO = null;
        if (product.getCategory() != null) {
            categoryDTO = new CategoryDTO();
            categoryDTO.setId(product.getCategory().getId());
            categoryDTO.setName(product.getCategory().getName());
            categoryDTO.setStatus(product.getCategory().getStatus());
        }

        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .slug(product.getSlug())
                .shortDesc(product.getShortDesc())
                .description(product.getDescription())
                .status(product.getStatus() != null ? product.getStatus().name() : null)
                .brandId(product.getBrand() != null ? product.getBrand().getId() : null)
                .brandName(product.getBrand() != null ? product.getBrand().getName() : null)
                .baseSku(product.getBaseSku())
                .categoryName(product.getCategory() != null ? product.getCategory().getName() : null)
                .soldCount(soldCountMap.getOrDefault(product.getId(), 0L))
                .ratingAverage(product.getRatingAverage())
                .reviewCount(product.getReviewCount())
                .category(categoryDTO)
                .inventory(totalInventory)
                .imageUrls(imageUrls)
                .variants(variantResponses)
                .build();
    }

    private ProductVariantResponse mapPublicListVariantToResponse(Product product,
            ProductVariant variant,
            BigDecimal profitMultiplier,
            String roundingRule,
            boolean multiTierPricingEnabled,
            List<Inventory> allInventories) {

        List<Inventory> validBatches = allInventories.stream()
                .filter(inv -> inv.getQuantity() != null && inv.getQuantity() > 0)
                .filter(inv -> inv.getBranch() != null && inv.getBranch().getStatus() == BranchStatus.ACTIVE)
                .collect(Collectors.toList());

        int displayQuantity = validBatches.stream()
                .map(Inventory::getQuantity)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();

        Map<Long, BigDecimal> transferImportPriceCache = new HashMap<>();
        BigDecimal averageImportPrice = validBatches.isEmpty() ? null
                : validBatches.stream()
                        .map(inv -> resolveDisplayImportPrice(inv, variant.getId(), transferImportPriceCache))
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(validBatches.size()), 4, RoundingMode.HALF_UP);

        Long categoryId = product.getCategory() != null ? product.getCategory().getId() : null;
        BigDecimal sellingPrice = averageImportPrice == null
                ? null
                : (multiTierPricingEnabled && categoryId != null
                        ? settingService.calculateSellingPrice(averageImportPrice, categoryId, null)
                        : settingService.calculateSellingPrice(averageImportPrice, profitMultiplier, roundingRule));

        List<AttributeValueResponse> attributeValues = variant.getAttributeValues() != null
                ? variant.getAttributeValues().stream().map(sav -> AttributeValueResponse.builder()
                        .attributeId(sav.getAttribute().getId())
                        .attributeName(sav.getAttribute().getName())
                        .attributeCode(sav.getAttribute().getCode())
                        .valueId(sav.getAttributeValue().getId())
                        .value(sav.getAttributeValue().getValue())
                        .build()).collect(Collectors.toList())
                : Collections.emptyList();

        return ProductVariantResponse.builder()
                .id(variant.getId())
                .sku(variant.getSku())
                .barcode(variant.getBarcode())
                .productName(product.getName())
                .quantity(displayQuantity)
                .price(sellingPrice)
                .importPrice(null)
                .shippingWeight(variant.getShippingWeight())
                .imageUrl(variant.getImageUrl())
                .status(variant.getStatus())
                .attributeValues(attributeValues)
                .batches(Collections.emptyList())
                .build();
    }

    private List<ProductVariant> getActiveDisplayVariants(Product product) {
        if (product == null || product.getVariants() == null) {
            return Collections.emptyList();
        }

        return product.getVariants().stream()
                .filter(variant -> variant.getStatus() == VariantStatus.ACTIVE)
                .filter(this::hasOnlyActiveAttributes)
                .collect(Collectors.toList());
    }

    private Map<Long, List<ProductVariant>> buildActiveVariantsByProductId(List<Product> products) {
        Map<Long, List<ProductVariant>> result = new LinkedHashMap<>();
        for (Product product : products) {
            if (product != null) {
                result.put(product.getId(), getActiveDisplayVariants(product));
            }
        }
        return result;
    }

    private Map<Long, List<Inventory>> loadInventoryMapForVariants(List<ProductVariant> variants) {
        if (variants == null || variants.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Long> variantIds = variants.stream()
                .map(ProductVariant::getId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        if (variantIds.isEmpty()) {
            return Collections.emptyMap();
        }

        return inventoryRepository.findByProductVariantIdInWithBranch(variantIds).stream()
                .filter(inv -> inv.getProductVariant() != null && inv.getProductVariant().getId() != null)
                .collect(Collectors.groupingBy(inv -> inv.getProductVariant().getId()));
    }

    private ProductResponse convertToResponse(Product product, Map<Long, Long> soldCountMap) {
        User currentUser = getCurrentUser();

        // LẤY HỆ SỐ NHÂN TỪ SETTING SERVICE (Ví dụ: 30% -> 1.3)
        BigDecimal profitMultiplier = settingService.getProfitMultiplier();
        String roundingRule = settingService.getProfitRoundingRuleRaw();

        List<String> imageUrls = product.getProductImages() != null
                ? product.getProductImages().stream().map(ProductImage::getImageUrl).collect(Collectors.toList())
                : Collections.emptyList();

        // TRUYỀN HỆ SỐ profitMultiplier VÀO HÀM MAP BIẾN THẾ (Tải kho gộp để tránh N+1 Query)
        List<ProductVariant> activeVariants = product.getVariants() != null ? product.getVariants().stream()
                .filter(variant -> variant.getStatus() == VariantStatus.ACTIVE)
                .filter(this::hasOnlyActiveAttributes)
                .collect(Collectors.toList()) : Collections.emptyList();

        Map<Long, List<Inventory>> inventoryMap = new HashMap<>();
        if (!activeVariants.isEmpty()) {
            List<Long> activeVariantIds = activeVariants.stream().map(ProductVariant::getId).toList();
            List<Inventory> allInventories = inventoryRepository.findByProductVariantIdInWithBranch(activeVariantIds);
            inventoryMap = allInventories.stream()
                    .collect(Collectors.groupingBy(inv -> inv.getProductVariant().getId()));
        }

        final Map<Long, List<Inventory>> finalInventoryMap = inventoryMap;
        List<ProductVariantResponse> variantResponses = activeVariants.stream()
                .map(variant -> {
                    List<Inventory> variantInventories = finalInventoryMap.getOrDefault(variant.getId(), List.of());
                    return mapVariantToResponse(variant, currentUser, profitMultiplier, roundingRule, variantInventories);
                })
                .collect(Collectors.toList());

        int totalInventory = variantResponses.stream()
                .map(ProductVariantResponse::getQuantity)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();

        // ... (Phần map Category và Brand giữ nguyên như code cũ của Huy)
        CategoryDTO categoryDTO = null;
        if (product.getCategory() != null) {
            categoryDTO = new CategoryDTO();
            categoryDTO.setId(product.getCategory().getId());
            categoryDTO.setName(product.getCategory().getName());
            categoryDTO.setStatus(product.getCategory().getStatus());
        }

        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .slug(product.getSlug())
                .shortDesc(product.getShortDesc())
                .description(product.getDescription())
                .status(product.getStatus() != null ? product.getStatus().name() : null)
                .brandId(product.getBrand() != null ? product.getBrand().getId() : null)
                .brandName(product.getBrand() != null ? product.getBrand().getName() : null)
                .baseSku(product.getBaseSku())
                .categoryName(product.getCategory() != null ? product.getCategory().getName() : null)
                .soldCount(soldCountMap.getOrDefault(product.getId(), 0L))
                .ratingAverage(product.getRatingAverage())
                .reviewCount(product.getReviewCount())
                .category(categoryDTO)
                .inventory(totalInventory)
                .imageUrls(imageUrls)
                .variants(variantResponses)
                .build();
    }

    private ProductResponse convertToResponse(Product product,
            Map<Long, Long> soldCountMap,
            User currentUser,
            BigDecimal profitMultiplier,
            String roundingRule,
            Map<Long, List<ProductVariant>> activeVariantsByProductId,
            Map<Long, List<Inventory>> inventoryMap) {

        List<String> imageUrls = product.getProductImages() != null
                ? product.getProductImages().stream().map(ProductImage::getImageUrl).collect(Collectors.toList())
                : Collections.emptyList();

        List<ProductVariant> activeVariants = activeVariantsByProductId != null
                ? activeVariantsByProductId.getOrDefault(product.getId(), Collections.emptyList())
                : getActiveDisplayVariants(product);

        Map<Long, List<Inventory>> safeInventoryMap = inventoryMap != null
                ? inventoryMap
                : Collections.emptyMap();
        List<ProductVariantResponse> variantResponses = activeVariants.stream()
                .map(variant -> {
                    List<Inventory> variantInventories = safeInventoryMap.getOrDefault(variant.getId(), List.of());
                    return mapVariantToResponse(variant, currentUser, profitMultiplier, roundingRule, variantInventories);
                })
                .collect(Collectors.toList());

        int totalInventory = variantResponses.stream()
                .map(ProductVariantResponse::getQuantity)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();

        CategoryDTO categoryDTO = null;
        if (product.getCategory() != null) {
            categoryDTO = new CategoryDTO();
            categoryDTO.setId(product.getCategory().getId());
            categoryDTO.setName(product.getCategory().getName());
            categoryDTO.setStatus(product.getCategory().getStatus());
        }

        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .slug(product.getSlug())
                .shortDesc(product.getShortDesc())
                .description(product.getDescription())
                .status(product.getStatus() != null ? product.getStatus().name() : null)
                .brandId(product.getBrand() != null ? product.getBrand().getId() : null)
                .brandName(product.getBrand() != null ? product.getBrand().getName() : null)
                .baseSku(product.getBaseSku())
                .categoryName(product.getCategory() != null ? product.getCategory().getName() : null)
                .soldCount(soldCountMap.getOrDefault(product.getId(), 0L))
                .ratingAverage(product.getRatingAverage())
                .reviewCount(product.getReviewCount())
                .category(categoryDTO)
                .inventory(totalInventory)
                .imageUrls(imageUrls)
                .variants(variantResponses)
                .build();
    }

    // 1. Hàm overload để không làm gãy các code cũ đang gọi
    public ProductVariantResponse mapVariantToResponse(ProductVariant variant) {
        return mapVariantToResponse(
                variant,
                getCurrentUser(),
                settingService.getProfitMultiplier(),
                settingService.getProfitRoundingRuleRaw());
    }

    public ProductVariantResponse mapVariantToResponse(ProductVariant variant, User currentUser,
            BigDecimal multiplier) {
        return mapVariantToResponse(variant, currentUser, multiplier, settingService.getProfitRoundingRuleRaw());
    }

    public ProductVariantResponse mapVariantToResponse(ProductVariant variant, User currentUser, BigDecimal multiplier,
            String roundingRule) {
        List<Inventory> allInventories = inventoryRepository.findByProductVariantIdWithBranch(variant.getId());
        return mapVariantToResponse(variant, currentUser, multiplier, roundingRule, allInventories);
    }

    public ProductVariantResponse mapVariantToResponse(ProductVariant variant, User currentUser, BigDecimal multiplier,
            String roundingRule, List<Inventory> allInventories) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Set<String> authorities = auth == null
                ? Set.of()
                : auth.getAuthorities().stream()
                        .map(a -> a.getAuthority())
                        .collect(Collectors.toSet());
        boolean canSeeImportPrice = authorities.contains("REPORT_FINANCE_VIEW")
                || authorities.contains("IMPORT_VIEW")
                || authorities.contains("EXPORT_CREATE")
                || authorities.contains("TRANSFER_CREATE")
                || authorities.contains("PURCHASE_REQUEST_VIEW");

        Branch currentBranch = currentUser != null ? currentUser.getBranch() : null;
        boolean canSeeAllBranches = currentBranch == null;

        List<Inventory> validBatches = allInventories.stream()
                .filter(inv -> inv.getQuantity() != null && inv.getQuantity() > 0)

                // Chỉ lấy tồn kho từ chi nhánh ACTIVE (Admin xem được tất cả)
                .filter(inv -> canSeeAllBranches
                        || (inv.getBranch() != null && inv.getBranch().getStatus() == BranchStatus.ACTIVE))
                // Staff/Manager thông thường chỉ thấy chi nhánh của họ; Admin thấy tất
                // cả
                .filter(inv -> currentUser == null || canSeeAllBranches
                        || currentBranch == null
                        || (inv.getBranch() != null && inv.getBranch().getId().equals(currentBranch.getId())))

                .collect(Collectors.toList());

        int displayQuantity = validBatches.stream()
                .map(Inventory::getQuantity)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();

        Map<Long, BigDecimal> transferImportPriceCache = new HashMap<>();

        Long categoryId = (variant.getProduct() != null && variant.getProduct().getCategory() != null)
                ? variant.getProduct().getCategory().getId()
                : null;

        List<ProductVariantResponse.BatchInfoDto> batchDtos = validBatches.stream().map(inv -> {
            BigDecimal importPrice = resolveDisplayImportPrice(inv, variant.getId(), transferImportPriceCache);
            BigDecimal sellingPrice = settingService.calculateSellingPrice(importPrice, categoryId, inv.getExpiryDate());

            return ProductVariantResponse.BatchInfoDto.builder()
                    .inventoryId(inv.getId())
                    .branchName(inv.getBranch() != null ? inv.getBranch().getName() : "Kho tổng")
                    .batchNumber(inv.getBatchNumber() != null ? inv.getBatchNumber() : "Chưa xác định")
                    .quantity(inv.getQuantity())
                    .importPrice(canSeeImportPrice ? importPrice : null) // Admin/Manager/Exporter được thấy giá nhập
                    .sellingPrice(sellingPrice)
                    .expiryDate(inv.getExpiryDate() != null ? inv.getExpiryDate().toLocalDate().toString() : null)
                    .marginCapped(settingService.isMarginCapped(categoryId, inv.getExpiryDate()))
                    .build();
        }).collect(Collectors.toList());

        // null = chưa nhập hàng → FE ẩn giá, không hiển thị "0đ"
        BigDecimal averageImportPrice = validBatches.isEmpty() ? null
                : validBatches.stream()
                        .map(inv -> resolveDisplayImportPrice(inv, variant.getId(), transferImportPriceCache))
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(validBatches.size()), 4, RoundingMode.HALF_UP);

        BigDecimal sellingPriceByAverageImport = averageImportPrice == null
                ? null
                : settingService.calculateSellingPrice(averageImportPrice, categoryId, null);

        List<AttributeValueResponse> attributeValues = variant.getAttributeValues() != null
                ? variant.getAttributeValues().stream().map(sav -> AttributeValueResponse.builder()
                        .attributeId(sav.getAttribute().getId())
                        .attributeName(sav.getAttribute().getName())
                        .attributeCode(sav.getAttribute().getCode())
                        .valueId(sav.getAttributeValue().getId())
                        .value(sav.getAttributeValue().getValue())
                        .build()).collect(Collectors.toList())
                : Collections.emptyList();

        return ProductVariantResponse.builder()
                .id(variant.getId())
                .sku(variant.getSku())
                .barcode(variant.getBarcode())
                .productName(variant.getProduct() != null ? variant.getProduct().getName() : null)
                .quantity(displayQuantity)
                .price(sellingPriceByAverageImport)
                .importPrice(averageImportPrice)
                .shippingWeight(variant.getShippingWeight())
                .imageUrl(variant.getImageUrl())
                .status(variant.getStatus())
                .attributeValues(attributeValues)
                .batches(batchDtos)
                .build();
    }

    private BigDecimal normalizeShippingWeight(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0 ? value : null;
    }

    private BigDecimal resolveDisplayImportPrice(Inventory inventory, Long variantId, Map<Long, BigDecimal> transferImportPriceCache) {
        BigDecimal importPrice = inventory.getImportPrice();
        if (!isTransferBatchWithoutCost(inventory)) {
            return importPrice != null ? importPrice : BigDecimal.ZERO;
        }

        Long branchId = inventory.getBranch() != null ? inventory.getBranch().getId() : null;
        if (branchId == null || variantId == null) {
            return BigDecimal.ZERO;
        }

        return transferImportPriceCache.computeIfAbsent(branchId,
                ignored -> resolveInboundTransferAverageImportPrice(branchId, variantId));
    }

    private boolean isTransferBatchWithoutCost(Inventory inventory) {
        if (inventory == null) {
            return false;
        }

        String batchNumber = inventory.getBatchNumber();
        BigDecimal importPrice = inventory.getImportPrice();
        boolean isTransferBatch = batchNumber != null && batchNumber.toUpperCase(Locale.ROOT).startsWith("TRANSFER");
        boolean missingCost = importPrice == null || BigDecimal.ZERO.compareTo(importPrice) == 0;
        return isTransferBatch && missingCost;
    }

    private BigDecimal resolveInboundTransferAverageImportPrice(Long branchId, Long variantId) {
        Object[] summary = inventoryTransactionRepository.summarizeCompletedInboundTransferCost(branchId, variantId);
        if (summary == null || summary.length < 2 || summary[0] == null || summary[1] == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal totalCost = (BigDecimal) summary[0];
        Number totalQty = (Number) summary[1];
        if (totalQty.longValue() <= 0) {
            return BigDecimal.ZERO;
        }

        return totalCost.divide(BigDecimal.valueOf(totalQty.longValue()), 4, RoundingMode.HALF_UP);
    }

    private boolean hasOnlyActiveAttributes(ProductVariant variant) {
        if (variant == null || variant.getAttributeValues() == null || variant.getAttributeValues().isEmpty()) {
            return true;
        }

        return variant.getAttributeValues().stream()
                .allMatch(sav -> sav.getAttribute() != null
                        && sav.getAttribute().getStatus() == AttributeStatus.ACTIVE);
    }



    private void validateSkusInRequest(List<VariantRequest> variants) {
        if (variants == null)
            return;
        Set<String> seen = new HashSet<>();
        for (VariantRequest v : variants) {
            if (!seen.add(v.getSku())) {
                throw new BadRequestException("Mã SKU bị trùng lặp trong request: " + v.getSku());
            }
        }
    }

    private List<ProductImage> uploadAndSaveProductImages(Product product, List<MultipartFile> files) {
        List<ProductImage> savedImages = new ArrayList<>();
        if (files == null || files.isEmpty())
            return savedImages;

        for (MultipartFile file : files) {
            if (file == null || file.isEmpty())
                continue;
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
        if (variantRequests == null)
            return savedList;

        for (int i = 0; i < variantRequests.size(); i++) {
            VariantRequest vReq = variantRequests.get(i);

            String imageUrl = vReq.getImage() != null ? vReq.getImage() : vReq.getImageUrl();
            String imagePublicId = null;
            if (variantImages != null && i < variantImages.size()) {
                MultipartFile imgFile = variantImages.get(i);
                if (imgFile != null && !imgFile.isEmpty()) {
                    CloudinaryService.UploadResult result = cloudinaryService.upload(imgFile, "products/variants");
                    if (imageUrl != null && !imageUrl.trim().isEmpty()) {
                        imageUrl = imageUrl + "," + result.secureUrl();
                    } else {
                        imageUrl = result.secureUrl();
                    }
                    imagePublicId = result.publicId();
                }
            }

            ProductVariant variant = ProductVariant.builder()
                    .product(product)
                    .sku(vReq.getSku())
                    .barcode(vReq.getBarcode())
                    .imageUrl(imageUrl)
                    .imagePublicId(imagePublicId)
                    .shippingWeight(normalizeShippingWeight(vReq.getShippingWeight()))
                    .status(VariantStatus.ACTIVE)
                    .build();

            // Lưu variant lần 1 để lấy ID
            ProductVariant savedVariant = variantRepository.save(variant);

            if (vReq.getAttributeValueIds() != null) {
                List<SKUAttributeValue> attrList = new ArrayList<>();
                for (Long valueId : vReq.getAttributeValueIds()) {
                    AttributeValue attrValue = attributeValueRepository.findById(valueId)
                            .orElseThrow(
                                    () -> new NotFoundException("Giá trị thuộc tính không tồn tại ID: " + valueId));

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
        if (status == null || status.isBlank())
            return ProductStatus.DRAFT;
        try {
            return ProductStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ProductStatus.DRAFT;
        }
    }

    private String toSlug(String input) {
        if (input == null)
            return "";
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        return normalized.replaceAll("[\\p{InCombiningDiacriticalMarks}]", "")
                .toLowerCase(Locale.ENGLISH)
                .replaceAll("[^a-z0-9\\s]", "")
                .replaceAll("\\s+", "-");
    }

    public List<ProductResponse> getProductsForSale() {
        List<Product> products = productRepository.findProductsForSale();
        Map<Long, Long> soldCountMap = buildSoldCountMap(
                products.stream().map(Product::getId).toList());

        return convertToPublicListResponseList(products, soldCountMap).stream()
                .filter(p -> p.getInventory() != null && p.getInventory() > 0)
                .collect(Collectors.toList());
    }

    public List<ProductResponse> getTopBestSellers(int limit) {
        List<Product> products = productRepository.findProductsForSale();
        Map<Long, Long> soldCountMap = buildSoldCountMap(
                products.stream().map(Product::getId).toList());

        return convertToResponseList(products, soldCountMap).stream()
                .filter(p -> p.getInventory() != null && p.getInventory() > 0)
                .sorted(Comparator
                        .comparing(this::safeLong, Comparator.reverseOrder())
                        .thenComparing(ProductResponse::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ProductResponse getProductDetailForUser(String slug) {
        Product product = productRepository.findBySlugWithPublicDetails(slug)
                .orElseThrow(() -> new NotFoundException("Sản phẩm không tồn tại hoặc đã bị xóa."));

        if (product.getStatus() != ProductStatus.ACTIVE) {
            throw new NotFoundException("Sản phẩm không tồn tại hoặc đã ngừng kinh doanh.");
        }

        if (product.getCategory() == null || product.getCategory().getStatus() != CategoryStatus.ACTIVE) {
            throw new NotFoundException("Sản phẩm không tồn tại hoặc danh mục đã ngừng hoạt động.");
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

        Page<Long> productIdsPage = productRepository.findPublicProductIds(null, categoryId, null,
                PageRequest.of(0, Integer.MAX_VALUE));
        List<Long> productIds = productIdsPage.getContent();

        if (productIds.isEmpty())
            return Collections.emptyList();

        List<Product> products = productRepository.findPublicByIds(productIds);
        Map<Long, Long> soldCountMap = buildSoldCountMap(productIds);

        return convertToPublicListResponseList(products, soldCountMap).stream()
                .filter(p -> p.getInventory() != null && p.getInventory() > 0)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> getPublicProductsByBrandId(Long brandId) {
        Brand brand = brandRepository.findById(brandId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy thương hiệu với ID: " + brandId));

        if (brand.getStatus() != BrandStatus.ACTIVE) {
            throw new BadRequestException("Thương hiệu không hoạt động.");
        }

        Page<Long> productIdsPage = productRepository.findPublicProductIds(null, null, brandId,
                PageRequest.of(0, Integer.MAX_VALUE));
        List<Long> productIds = productIdsPage.getContent();

        if (productIds.isEmpty())
            return Collections.emptyList();

        List<Product> products = productRepository.findPublicByIds(productIds);
        Map<Long, Long> soldCountMap = buildSoldCountMap(productIds);

        return convertToResponseList(products, soldCountMap).stream()
                .filter(p -> p.getInventory() != null && p.getInventory() > 0)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> getPublicProducts(
            String keyword,
            Long categoryId,
            Long brandId,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            String packaging,
            String packagingValueIds,
            String sort,
            Pageable pageable) {
        validatePriceRange(minPrice, maxPrice);

        List<Long> packagingValueIdList = normalizePackagingValueIds(packagingValueIds);
        List<String> packagingValues = resolvePackagingValues(
                normalizePackagingValues(packaging),
                packagingValueIdList);
        PublicProductSort sortOption = parsePublicProductSort(sort);
        boolean hasPackagingValueIdFilter = !packagingValueIdList.isEmpty();
        boolean hasPackagingFilter = hasPackagingValueIdFilter || !packagingValues.isEmpty();
        boolean hasPriceFilter = minPrice != null || maxPrice != null;
        boolean needsBestSellingPostSort = sortOption == PublicProductSort.BEST_SELLING;
        boolean needsPostMappingPagination = hasPriceFilter || hasPackagingFilter || needsBestSellingPostSort;
        List<Long> categoryIds = resolveCategoryFilterIds(categoryId);
        boolean hasCategoryFilter = !categoryIds.isEmpty();
        String normalizedKeyword = blankToNull(keyword);
        List<Long> keywordCategoryIds = resolveKeywordCategoryFilterIds(normalizedKeyword);
        List<Long> keywordBrandIds = resolveKeywordBrandFilterIds(normalizedKeyword);
        boolean hasKeywordCategoryFilter = !keywordCategoryIds.isEmpty();
        boolean hasKeywordBrandFilter = !keywordBrandIds.isEmpty();
        Pageable queryPageable = pageable.isPaged()
                ? PageRequest.of(pageable.getPageNumber(), pageable.getPageSize())
                : Pageable.unpaged();
        Pageable lookupPageable = needsPostMappingPagination
                ? PageRequest.of(0, PRICE_FILTER_SCAN_LIMIT)
                : queryPageable;

        Page<Long> productIdsPage = productRepository.findPublicProductIdsFiltered(
                normalizedKeyword,
                hasKeywordCategoryFilter,
                hasKeywordCategoryFilter ? keywordCategoryIds : List.of(-1L),
                hasKeywordBrandFilter,
                hasKeywordBrandFilter ? keywordBrandIds : List.of(-1L),
                hasCategoryFilter,
                hasCategoryFilter ? categoryIds : List.of(-1L),
                brandId,
                hasPackagingFilter,
                hasPackagingValueIdFilter,
                hasPackagingValueIdFilter ? packagingValueIdList : List.of(-1L),
                hasPackagingFilter ? packagingValues : List.of("__no_packaging_filter__"),
                lookupPageable);

        List<Long> productIds = productIdsPage.getContent();
        if (productIds.isEmpty())
            return Page.empty(pageable);

        Map<Long, Long> soldCountMap = buildSoldCountMap(productIds);

        Map<Long, Product> productsById = productRepository.findPublicByIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, product -> product));

        List<Product> orderedProducts = productIds.stream()
                .map(productsById::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        List<ProductResponse> productResponses = convertToPublicListResponseList(orderedProducts, soldCountMap).stream()
                .map(p -> applyPublicListVariantFilters(p, minPrice, maxPrice, packagingValueIdList, packagingValues))
                .filter(p -> p.getVariants() != null && !p.getVariants().isEmpty())
                .collect(Collectors.toList());

        productResponses.sort(buildPublicProductComparator(sortOption, productsById));

        if (!needsPostMappingPagination) {
            return new PageImpl<>(productResponses, pageable, productIdsPage.getTotalElements());
        }

        int fromIndex = (int) Math.min(pageable.getOffset(), productResponses.size());
        int toIndex = Math.min(fromIndex + pageable.getPageSize(), productResponses.size());
        List<ProductResponse> pageContent = productResponses.subList(fromIndex, toIndex);

        return new PageImpl<>(pageContent, pageable, productResponses.size());
    }

    private PublicProductSort parsePublicProductSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return PublicProductSort.FEATURED;
        }

        return switch (sort.trim().toLowerCase(Locale.ROOT)) {
            case "price-asc" -> PublicProductSort.PRICE_ASC;
            case "price-desc" -> PublicProductSort.PRICE_DESC;
            case "name-asc" -> PublicProductSort.NAME_ASC;
            case "name-desc" -> PublicProductSort.NAME_DESC;
            case "oldest" -> PublicProductSort.OLDEST;
            case "newest" -> PublicProductSort.NEWEST;
            case "best-selling" -> PublicProductSort.BEST_SELLING;
            case "inventory-desc" -> PublicProductSort.INVENTORY_DESC;
            default -> PublicProductSort.FEATURED;
        };
    }

    private Comparator<ProductResponse> buildPublicProductComparator(
            PublicProductSort sortOption,
            Map<Long, Product> productsById) {
        Comparator<ProductResponse> newestTieBreaker = Comparator
                .comparing(
                        (ProductResponse response) -> resolveProductCreatedAt(productsById.get(response.getId())),
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(ProductResponse::getId, Comparator.nullsLast(Comparator.reverseOrder()));

        return switch (sortOption) {
            case PRICE_ASC -> Comparator
                    .comparing(this::resolveLowestVariantPrice, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(newestTieBreaker);
            case PRICE_DESC -> Comparator
                    .comparing(this::resolveLowestVariantPrice, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(newestTieBreaker);
            case NAME_ASC -> Comparator
                    .comparing(
                            (ProductResponse response) -> blankToEmpty(response.getName()),
                            String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(newestTieBreaker);
            case NAME_DESC -> Comparator
                    .comparing(
                            (ProductResponse response) -> blankToEmpty(response.getName()),
                            String.CASE_INSENSITIVE_ORDER.reversed())
                    .thenComparing(newestTieBreaker);
            case OLDEST -> Comparator
                    .comparing(
                            (ProductResponse response) -> resolveProductCreatedAt(productsById.get(response.getId())),
                            Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(ProductResponse::getId, Comparator.nullsLast(Comparator.naturalOrder()));
            case NEWEST -> newestTieBreaker;
            case BEST_SELLING -> Comparator
                    .comparing(this::safeLong, Comparator.reverseOrder())
                    .thenComparing(newestTieBreaker);
            case INVENTORY_DESC -> Comparator
                    .comparing(this::safeInventory, Comparator.reverseOrder())
                    .thenComparing(newestTieBreaker);
            case FEATURED -> Comparator
                    .comparing(this::safeLong, Comparator.reverseOrder())
                    .thenComparing(this::safeRating, Comparator.reverseOrder())
                    .thenComparing(this::safeReviewCount, Comparator.reverseOrder())
                    .thenComparing(this::safeInventory, Comparator.reverseOrder())
                    .thenComparing(newestTieBreaker);
        };
    }

    private BigDecimal resolveLowestVariantPrice(ProductResponse response) {
        if (response == null || response.getVariants() == null || response.getVariants().isEmpty()) {
            return null;
        }

        return response.getVariants().stream()
                .map(ProductVariantResponse::getPrice)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(null);
    }

    private LocalDateTime resolveProductCreatedAt(Product product) {
        return product != null ? product.getCreatedAt() : null;
    }

    private Long safeLong(ProductResponse response) {
        return response != null && response.getSoldCount() != null ? response.getSoldCount() : 0L;
    }

    private Integer safeInventory(ProductResponse response) {
        return response != null && response.getInventory() != null ? response.getInventory() : 0;
    }

    private Float safeRating(ProductResponse response) {
        return response != null && response.getRatingAverage() != null ? response.getRatingAverage() : 0F;
    }

    private Integer safeReviewCount(ProductResponse response) {
        return response != null && response.getReviewCount() != null ? response.getReviewCount() : 0;
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value;
    }

    private List<Long> resolveKeywordCategoryFilterIds(String keyword) {
        String normalizedKeyword = normalizeSearchText(keyword);
        if (normalizedKeyword.isBlank()) {
            return Collections.emptyList();
        }

        List<Category> activeCategories = categoryRepository.findAll().stream()
                .filter(category -> category.getStatus() == CategoryStatus.ACTIVE)
                .collect(Collectors.toList());

        Map<Long, List<Category>> childrenByParentId = activeCategories.stream()
                .filter(category -> category.getParent() != null && category.getParent().getId() != null)
                .collect(Collectors.groupingBy(category -> category.getParent().getId()));

        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        activeCategories.stream()
                .filter(category -> matchesNormalizedKeyword(category.getName(), normalizedKeyword))
                .forEach(category -> collectCategoryAndChildren(category.getId(), childrenByParentId, ids));

        return new ArrayList<>(ids);
    }

    private List<Long> resolveKeywordBrandFilterIds(String keyword) {
        String normalizedKeyword = normalizeSearchText(keyword);
        if (normalizedKeyword.isBlank()) {
            return Collections.emptyList();
        }

        return brandRepository.findAll().stream()
                .filter(brand -> brand.getStatus() == BrandStatus.ACTIVE)
                .filter(brand -> matchesNormalizedKeyword(brand.getName(), normalizedKeyword))
                .map(Brand::getId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    private boolean matchesNormalizedKeyword(String value, String normalizedKeyword) {
        String normalizedValue = normalizeSearchText(value);
        if (normalizedValue.isBlank()) {
            return false;
        }

        if (normalizedValue.contains(normalizedKeyword)) {
            return true;
        }

        List<String> tokens = Arrays.stream(normalizedKeyword.split("\\s+"))
                .filter(token -> token.length() > 1)
                .collect(Collectors.toList());

        return !tokens.isEmpty() && tokens.stream().allMatch(normalizedValue::contains);
    }

    private String normalizeSearchText(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("[\\p{InCombiningDiacriticalMarks}]", "")
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }

    private List<Long> resolveCategoryFilterIds(Long categoryId) {
        if (categoryId == null) {
            return Collections.emptyList();
        }

        Category root = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy danh mục với ID: " + categoryId));

        if (root.getStatus() != CategoryStatus.ACTIVE) {
            throw new BadRequestException("Danh mục không hoạt động.");
        }

        List<Category> activeCategories = categoryRepository.findAll().stream()
                .filter(category -> category.getStatus() == CategoryStatus.ACTIVE)
                .collect(Collectors.toList());

        Map<Long, List<Category>> childrenByParentId = activeCategories.stream()
                .filter(category -> category.getParent() != null && category.getParent().getId() != null)
                .collect(Collectors.groupingBy(category -> category.getParent().getId()));

        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        collectCategoryAndChildren(root.getId(), childrenByParentId, ids);
        return new ArrayList<>(ids);
    }

    private void collectCategoryAndChildren(
            Long categoryId,
            Map<Long, List<Category>> childrenByParentId,
            Set<Long> targetIds) {
        if (categoryId == null || !targetIds.add(categoryId)) {
            return;
        }

        childrenByParentId.getOrDefault(categoryId, Collections.emptyList())
                .forEach(child -> collectCategoryAndChildren(child.getId(), childrenByParentId, targetIds));
    }

    private void validatePriceRange(BigDecimal minPrice, BigDecimal maxPrice) {
        if (minPrice != null && minPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("Giá tối thiểu không được âm.");
        }

        if (maxPrice != null && maxPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("Giá tối đa không được âm.");
        }

        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new BadRequestException("Giá tối thiểu không được lớn hơn giá tối đa.");
        }
    }

    private List<String> normalizePackagingValues(String packaging) {
        if (packaging == null || packaging.isBlank()) {
            return Collections.emptyList();
        }

        return Arrays.stream(packaging.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .distinct()
                .limit(20)
                .collect(Collectors.toList());
    }

    private List<String> resolvePackagingValues(List<String> packagingValues, List<Long> packagingValueIds) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (packagingValues != null) {
            values.addAll(packagingValues);
        }

        if (packagingValueIds != null && !packagingValueIds.isEmpty()) {
            attributeValueRepository.findAllById(packagingValueIds).stream()
                    .map(AttributeValue::getValue)
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .map(value -> value.toLowerCase(Locale.ROOT))
                    .forEach(values::add);
        }

        return values.stream()
                .limit(50)
                .collect(Collectors.toList());
    }

    private List<Long> normalizePackagingValueIds(String packagingValueIds) {
        if (packagingValueIds == null || packagingValueIds.isBlank()) {
            return Collections.emptyList();
        }

        return Arrays.stream(packagingValueIds.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(value -> {
                    try {
                        return Long.valueOf(value);
                    } catch (NumberFormatException ex) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .distinct()
                .limit(50)
                .collect(Collectors.toList());
    }

    private ProductResponse applyPublicListVariantFilters(
            ProductResponse product,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            List<Long> packagingValueIds,
            List<String> packagingValues) {
        if (product == null || product.getVariants() == null) {
            return product;
        }

        boolean hasPackagingFilter = (packagingValueIds != null && !packagingValueIds.isEmpty())
                || (packagingValues != null && !packagingValues.isEmpty());

        List<ProductVariantResponse> filteredVariants = product.getVariants().stream()
                .filter(variant -> variant.getQuantity() != null && variant.getQuantity() > 0)
                .filter(variant -> matchesVariantPriceRange(variant, minPrice, maxPrice))
                .filter(variant -> !hasPackagingFilter || matchesVariantPackaging(variant, packagingValueIds, packagingValues))
                .collect(Collectors.toList());

        product.setVariants(filteredVariants);
        product.setInventory(filteredVariants.stream()
                .map(ProductVariantResponse::getQuantity)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum());

        return product;
    }

    private boolean matchesVariantPriceRange(ProductVariantResponse variant, BigDecimal minPrice, BigDecimal maxPrice) {
        if (minPrice == null && maxPrice == null) {
            return true;
        }

        BigDecimal price = variant != null ? variant.getPrice() : null;
        return price != null
                && (minPrice == null || price.compareTo(minPrice) >= 0)
                && (maxPrice == null || price.compareTo(maxPrice) <= 0);
    }

    private boolean matchesVariantPackaging(
            ProductVariantResponse variant,
            List<Long> packagingValueIds,
            List<String> packagingValues) {
        boolean hasValueIdFilter = packagingValueIds != null && !packagingValueIds.isEmpty();
        boolean hasValueFilter = packagingValues != null && !packagingValues.isEmpty();

        if (!hasValueIdFilter && !hasValueFilter) {
            return true;
        }

        if (variant == null || variant.getAttributeValues() == null) {
            return false;
        }

        if (hasValueIdFilter) {
            boolean matchedById = variant.getAttributeValues().stream()
                    .map(AttributeValueResponse::getValueId)
                    .filter(Objects::nonNull)
                    .anyMatch(packagingValueIds::contains);
            if (matchedById) {
                return true;
            }
        }

        return variant.getAttributeValues().stream()
                .map(AttributeValueResponse::getValue)
                .filter(Objects::nonNull)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(packagingValues::contains);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
