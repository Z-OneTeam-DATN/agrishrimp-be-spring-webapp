package com.zone.agri.service;

import com.zone.agri.dto.request.supplier.SupplierImportDto;
import com.zone.agri.dto.request.supplier.SupplierProductCatalogRequest;
import com.zone.agri.dto.request.supplier.SupplierRequest;
import com.zone.agri.dto.response.supplier.SupplierProductCatalogResponse;
import com.zone.agri.dto.response.supplier.SupplierResponse;
import com.zone.agri.dto.response.supplier.SupplierWarningResponse;
import com.zone.agri.entity.InventoryNote;
import com.zone.agri.entity.Product;
import com.zone.agri.entity.Supplier;
import com.zone.agri.entity.SupplierProductCatalog;
import com.zone.agri.entity.User;
import com.zone.agri.entity.enums.InventoryNoteType;
import com.zone.agri.entity.enums.SupplierProductCatalogStatus;
import com.zone.agri.entity.enums.SupplierStatus;
import com.zone.agri.repository.InventoryNoteRepository;
import com.zone.agri.repository.ProductRepository;
import com.zone.agri.repository.SupplierProductCatalogRepository;
import com.zone.agri.repository.SupplierRepository;
import com.zone.agri.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SupplierService {
    private static final long CHECKING_TOO_LONG_DAYS = 14L;

    private final SupplierRepository supplierRepository;
    private final InventoryNoteRepository inventoryNoteRepository;
    private final ProductRepository productRepository;
    private final SupplierProductCatalogRepository supplierProductCatalogRepository;
    private final UserRepository userRepository;

    @Transactional
    public SupplierResponse createSupplier(SupplierRequest request) {
        String normalizedTaxCode = normalizeTaxCode(request.getTaxCode());
        if (supplierRepository.existsByTaxCode(normalizedTaxCode)) {
            throw new IllegalArgumentException("Mã số thuế " + normalizedTaxCode + " đã tồn tại");
        }

        Supplier supplier = new Supplier();
        mapRequestToEntity(request, supplier);
        supplier.setCode("NCC-" + System.currentTimeMillis());

        if (supplier.getStatus() == null) {
            supplier.setStatus(SupplierStatus.ACTIVE);
        }

        Supplier savedSupplier = supplierRepository.save(supplier);
        return buildSupplierResponse(savedSupplier, List.of());
    }

    @Transactional
    public SupplierResponse updateSupplier(Long id, SupplierRequest request) {
        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy NCC"));

        String normalizedTaxCode = normalizeTaxCode(request.getTaxCode());
        if (supplierRepository.existsByTaxCodeAndIdNot(normalizedTaxCode, id)) {
            throw new IllegalArgumentException("Mã số thuế mới đã tồn tại");
        }

        mapRequestToEntity(request, supplier);
        Supplier updated = supplierRepository.save(supplier);
        CatalogLoadResult catalogLoadResult = loadCatalogItemsSafely(id);
        return buildSupplierResponse(updated, catalogLoadResult.items(), catalogLoadResult.loaded());
    }

    @Transactional(readOnly = true)
    public SupplierResponse getSupplierById(Long id) {
        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy NCC"));
        CatalogLoadResult catalogLoadResult = loadCatalogItemsSafely(id);
        return buildSupplierResponse(supplier, catalogLoadResult.items(), catalogLoadResult.loaded());
    }

    @Transactional(readOnly = true)
    public Page<SupplierResponse> getAllSuppliers(String keyword, String statusStr, Pageable pageable) {
        SupplierStatus supplierStatus = null;
        if (statusStr != null && !"all".equalsIgnoreCase(statusStr) && !statusStr.isEmpty()) {
            try {
                supplierStatus = SupplierStatus.valueOf(statusStr.toUpperCase());
            } catch (IllegalArgumentException ignored) {
                supplierStatus = null;
            }
        }

        return supplierRepository.searchSuppliers(keyword, supplierStatus, pageable)
                .map(SupplierResponse::fromEntity);
    }

    @Transactional
    public void deleteSupplier(Long id) {
        if (!supplierRepository.existsById(id)) {
            throw new RuntimeException("Không tìm thấy nhà cung cấp để xóa");
        }
        supplierRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<SupplierImportDto> getImportHistory(Long supplierId) {
        List<InventoryNote> notes = inventoryNoteRepository.findImportHistoryBySupplierId(supplierId, InventoryNoteType.IMPORT);

        return notes.stream().map(note -> SupplierImportDto.builder()
                .id(note.getId())
                .code(note.getCode())
                .status(note.getStatus().name())
                .totalAmount(note.getTotalAmount())
                .createdAt(note.getCreatedAt())
                .itemCount(note.getDetails() != null ? note.getDetails().size() : 0)
                .totalQuantity(note.getDetails() != null
                        ? note.getDetails().stream()
                                .mapToInt(detail -> Objects.requireNonNullElse(
                                        detail.getQuantityReal(),
                                        Objects.requireNonNullElse(detail.getQuantityRequested(),
                                                Objects.requireNonNullElse(detail.getQuantity(), 0))))
                                .sum()
                        : 0)
                .build()).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<SupplierProductCatalogResponse> getProductCatalog(Long supplierId) {
        List<SupplierProductCatalog> catalogItems = supplierProductCatalogRepository.findAllBySupplierId(supplierId);
        Map<Long, String> userNames = resolveUserNames(catalogItems.stream()
                .flatMap(item -> java.util.stream.Stream.of(item.getCreatedByUserId(), item.getUpdatedByUserId()))
                .filter(Objects::nonNull)
                .toList());

        return catalogItems.stream()
                .map(item -> toCatalogResponse(item, userNames))
                .collect(Collectors.toList());
    }

    @Transactional
    public List<SupplierProductCatalogResponse> saveProductCatalog(Long supplierId, List<SupplierProductCatalogRequest> requests) {
        Supplier supplier = supplierRepository.findById(supplierId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy NCC"));

        List<SupplierProductCatalogRequest> safeRequests = requests != null ? requests : List.of();
        Set<Long> duplicateProductIds = safeRequests.stream()
                .map(SupplierProductCatalogRequest::getProductId)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(id -> id, Collectors.counting()))
                .entrySet().stream()
                .filter(entry -> entry.getValue() > 1)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        if (!duplicateProductIds.isEmpty()) {
            throw new IllegalArgumentException("Catalog chứa sản phẩm trùng lặp: " + duplicateProductIds);
        }

        List<Long> productIds = safeRequests.stream()
                .map(SupplierProductCatalogRequest::getProductId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<Long, Product> productMap = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, product -> product));

        if (productMap.size() != productIds.size()) {
            List<Long> missing = productIds.stream()
                    .filter(id -> !productMap.containsKey(id))
                    .toList();
            throw new RuntimeException("Không tìm thấy sản phẩm: " + missing);
        }

        Map<Long, SupplierProductCatalog> existingMap = new HashMap<>();
        supplierProductCatalogRepository.findAllBySupplierId(supplierId)
                .forEach(catalog -> existingMap.put(catalog.getProduct().getId(), catalog));

        for (SupplierProductCatalogRequest request : safeRequests) {
            Product product = productMap.get(request.getProductId());
            if (product == null) {
                continue;
            }

            SupplierProductCatalog catalog = existingMap.get(request.getProductId());
            if (catalog == null) {
                catalog = new SupplierProductCatalog();
                catalog.setSupplier(supplier);
                catalog.setProduct(product);
            }

            catalog.setStatus(request.getStatus() != null ? request.getStatus() : SupplierProductCatalogStatus.CHECKING);
            catalog.setNote(normalizeOptionalText(request.getNote()));
            supplierProductCatalogRepository.save(catalog);
        }

        if (!productIds.isEmpty()) {
            supplierProductCatalogRepository.deleteBySupplierIdAndProductIdNotIn(supplierId, productIds);
        } else {
            supplierProductCatalogRepository.deleteBySupplierId(supplierId);
        }

        return getProductCatalog(supplierId);
    }

    private void mapRequestToEntity(SupplierRequest request, Supplier supplier) {
        supplier.setName(normalizeRequiredText(request.getName()));
        supplier.setTaxCode(normalizeTaxCode(request.getTaxCode()));
        supplier.setContactName(normalizeRequiredText(request.getContactName()));
        supplier.setPhone(normalizeOptionalText(request.getPhone()));
        supplier.setEmail(normalizeEmail(request.getEmail()));
        supplier.setProvinceId(normalizeRequiredText(request.getProvinceId()));
        supplier.setAddressDetail(normalizeRequiredText(request.getAddressDetail()));
        supplier.setStatus(request.getStatus());
    }

    private SupplierResponse buildSupplierResponse(Supplier supplier, List<SupplierProductCatalog> catalogItems) {
        return buildSupplierResponse(supplier, catalogItems, true);
    }

    private SupplierResponse buildSupplierResponse(Supplier supplier, List<SupplierProductCatalog> catalogItems, boolean catalogLoaded) {
        SupplierResponse response = SupplierResponse.fromEntity(supplier);
        Map<Long, String> userNames = resolveUserNames(java.util.stream.Stream.of(
                        supplier.getCreatedByUserId(),
                        supplier.getUpdatedByUserId())
                .filter(Objects::nonNull)
                .toList());

        response.setCreatedByName(userNames.get(supplier.getCreatedByUserId()));
        response.setUpdatedByName(userNames.get(supplier.getUpdatedByUserId()));
        response.setCatalogProductCount(catalogItems.size());
        response.setAvailableProductCount((int) catalogItems.stream()
                .filter(item -> item.getStatus() == SupplierProductCatalogStatus.AVAILABLE)
                .count());
        response.setUnavailableProductCount((int) catalogItems.stream()
                .filter(item -> item.getStatus() == SupplierProductCatalogStatus.UNAVAILABLE)
                .count());
        response.setCheckingProductCount((int) catalogItems.stream()
                .filter(item -> item.getStatus() == SupplierProductCatalogStatus.CHECKING)
                .count());
        response.setWarnings(buildWarnings(supplier, catalogItems, catalogLoaded));
        return response;
    }

    private SupplierProductCatalogResponse toCatalogResponse(SupplierProductCatalog catalog, Map<Long, String> userNames) {
        SupplierProductCatalogResponse response = SupplierProductCatalogResponse.fromEntity(catalog);
        response.setCreatedByName(userNames.get(catalog.getCreatedByUserId()));
        response.setUpdatedByName(userNames.get(catalog.getUpdatedByUserId()));

        Long checkingAgeDays = calculateCheckingAgeDays(catalog.getStatus(), catalog.getUpdatedAt());
        response.setCheckingAgeDays(checkingAgeDays);
        response.setCheckingTooLong(checkingAgeDays != null && checkingAgeDays >= CHECKING_TOO_LONG_DAYS);
        return response;
    }

    private List<SupplierWarningResponse> buildWarnings(Supplier supplier, List<SupplierProductCatalog> catalogItems, boolean catalogLoaded) {
        List<SupplierWarningResponse> warnings = new ArrayList<>();

        if (!catalogLoaded) {
            warnings.add(SupplierWarningResponse.builder()
                    .code("CATALOG_DATA_UNAVAILABLE")
                    .severity("WARNING")
                    .message("Khong the doc catalog cua nha cung cap o thoi diem hien tai. Ho so supplier van duoc mo voi du lieu co ban.")
                    .build());
        }

        if (catalogLoaded && supplier.getStatus() == SupplierStatus.ACTIVE && catalogItems.isEmpty()) {
            warnings.add(SupplierWarningResponse.builder()
                    .code("ACTIVE_WITHOUT_CATALOG")
                    .severity("WARNING")
                    .message("Nhà cung cấp đang hoạt động nhưng chưa có catalog sản phẩm.")
                    .build());
        }

        if (catalogLoaded) {
            long checkingTooLongCount = catalogItems.stream()
                .map(item -> calculateCheckingAgeDays(item.getStatus(), item.getUpdatedAt()))
                .filter(Objects::nonNull)
                .filter(days -> days >= CHECKING_TOO_LONG_DAYS)
                .count();
            if (checkingTooLongCount > 0) {
                warnings.add(SupplierWarningResponse.builder()
                    .code("CHECKING_TOO_LONG")
                    .severity("WARNING")
                    .message(checkingTooLongCount + " sản phẩm đang ở trạng thái CHECKING quá " + CHECKING_TOO_LONG_DAYS + " ngày.")
                        .build());
            }
        }

        findDuplicatePhone(supplier).ifPresent(duplicate -> warnings.add(SupplierWarningResponse.builder()
                .code("DUPLICATE_PHONE")
                .severity("WARNING")
                .message("Số điện thoại đang trùng với NCC " + duplicate.getCode() + " - " + duplicate.getName() + ".")
                .build()));

        findDuplicateEmail(supplier).ifPresent(duplicate -> warnings.add(SupplierWarningResponse.builder()
                .code("DUPLICATE_EMAIL")
                .severity("WARNING")
                .message("Email đang trùng với NCC " + duplicate.getCode() + " - " + duplicate.getName() + ".")
                .build()));

        return warnings;
    }

    private CatalogLoadResult loadCatalogItemsSafely(Long supplierId) {
        try {
            return new CatalogLoadResult(supplierProductCatalogRepository.findAllBySupplierId(supplierId), true);
        } catch (Exception exception) {
            log.warn("Unable to load supplier catalog for supplierId={}. Returning supplier detail without catalog summary.", supplierId, exception);
            return new CatalogLoadResult(List.of(), false);
        }
    }

    private record CatalogLoadResult(List<SupplierProductCatalog> items, boolean loaded) {
    }

    private Optional<Supplier> findDuplicatePhone(Supplier supplier) {
        if (supplier.getPhone() == null || supplier.getPhone().isBlank()) {
            return Optional.empty();
        }
        return supplier.getId() == null
                ? supplierRepository.findFirstByPhone(supplier.getPhone())
                : supplierRepository.findFirstByPhoneAndIdNot(supplier.getPhone(), supplier.getId());
    }

    private Optional<Supplier> findDuplicateEmail(Supplier supplier) {
        if (supplier.getEmail() == null || supplier.getEmail().isBlank()) {
            return Optional.empty();
        }
        return supplier.getId() == null
                ? supplierRepository.findFirstByEmailIgnoreCase(supplier.getEmail())
                : supplierRepository.findFirstByEmailIgnoreCaseAndIdNot(supplier.getEmail(), supplier.getId());
    }

    private Map<Long, String> resolveUserNames(Collection<Long> userIds) {
        List<Long> ids = userIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }

        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(
                        User::getId,
                        this::resolveDisplayName,
                        (left, right) -> left,
                        LinkedHashMap::new));
    }

    private String resolveDisplayName(User user) {
        if (user == null) {
            return null;
        }
        if (user.getFullName() != null && !user.getFullName().isBlank()) {
            return user.getFullName();
        }
        if (user.getEmail() != null && !user.getEmail().isBlank()) {
            return user.getEmail();
        }
        return "User #" + user.getId();
    }

    private Long calculateCheckingAgeDays(SupplierProductCatalogStatus status, LocalDateTime updatedAt) {
        if (status != SupplierProductCatalogStatus.CHECKING || updatedAt == null) {
            return null;
        }
        return ChronoUnit.DAYS.between(updatedAt, LocalDateTime.now());
    }

    private String normalizeRequiredText(String value) {
        return value == null ? null : value.trim();
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeTaxCode(String value) {
        String normalized = normalizeRequiredText(value);
        return normalized == null ? null : normalized.replace(" ", "");
    }

    private String normalizeEmail(String value) {
        String normalized = normalizeOptionalText(value);
        return normalized == null ? null : normalized.toLowerCase();
    }
}
