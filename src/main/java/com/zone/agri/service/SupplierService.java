package com.zone.agri.service;

import com.zone.agri.dto.request.supplier.SupplierProductCatalogRequest;
import com.zone.agri.dto.request.supplier.SupplierImportDto;
import com.zone.agri.dto.request.supplier.SupplierRequest;
import com.zone.agri.dto.response.supplier.SupplierProductCatalogResponse;
import com.zone.agri.dto.response.supplier.SupplierResponse;
import com.zone.agri.entity.Product;
import com.zone.agri.entity.InventoryNote;
import com.zone.agri.entity.Supplier;
import com.zone.agri.entity.SupplierProductCatalog;
import com.zone.agri.entity.enums.SupplierStatus;
import com.zone.agri.repository.InventoryNoteRepository;
import com.zone.agri.entity.enums.InventoryNoteType;
import com.zone.agri.entity.enums.SupplierProductCatalogStatus;
import com.zone.agri.repository.ProductRepository;
import com.zone.agri.repository.SupplierProductCatalogRepository;
import com.zone.agri.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SupplierService {
    private final SupplierRepository supplierRepository;
    private final InventoryNoteRepository inventoryNoteRepository;
    private final ProductRepository productRepository;
    private final SupplierProductCatalogRepository supplierProductCatalogRepository;

    @Transactional
    public SupplierResponse createSupplier(SupplierRequest request) {
        if (supplierRepository.existsByTaxCode(request.getTaxCode())) {
            throw new RuntimeException("Mã số thuế " + request.getTaxCode() + " đã tồn tại");
        }

        Supplier supplier = new Supplier();
        mapRequestToEntity(request, supplier);

        supplier.setCode("NCC-" + System.currentTimeMillis());

        if (supplier.getStatus() == null) {
            supplier.setStatus(SupplierStatus.ACTIVE);
        }

        Supplier savedSupplier = supplierRepository.save(supplier);
        return SupplierResponse.fromEntity(savedSupplier);
    }

    @Transactional
    public SupplierResponse updateSupplier(Long id, SupplierRequest request) {
        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy NCC"));

        if (!supplier.getTaxCode().equals(request.getTaxCode()) &&
                supplierRepository.existsByTaxCode(request.getTaxCode())) {
            throw new RuntimeException("Mã số thuế mới đã tồn tại!");
        }

        mapRequestToEntity(request, supplier);
        Supplier updated = supplierRepository.save(supplier);
        return SupplierResponse.fromEntity(updated);
    }

    public SupplierResponse getSupplierById(Long id) {
        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy NCC"));
        return SupplierResponse.fromEntity(supplier);
    }

    // --- Đã xóa tham số Category ---
    public Page<SupplierResponse> getAllSuppliers(String keyword, String statusStr, Pageable pageable) {
        SupplierStatus supplierStatus = null;
        if (statusStr != null && !"all".equalsIgnoreCase(statusStr) && !statusStr.isEmpty()) {
            try {
                supplierStatus = SupplierStatus.valueOf(statusStr.toUpperCase());
            } catch (IllegalArgumentException e) {
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

    // THÊM HÀM NÀY VÀO CUỐI: Lấy lịch sử nhập hàng
    @Transactional(readOnly = true)
    public List<SupplierImportDto> getImportHistory(Long supplierId) {
        List<InventoryNote> notes = inventoryNoteRepository.findImportHistoryBySupplierId(supplierId,
                InventoryNoteType.IMPORT);

        return notes.stream().map(note -> SupplierImportDto.builder()
                .id(note.getId())
                .code(note.getCode())
                .status(note.getStatus().name()) // PENDING, COMPLETED, CANCELLED
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
        return supplierProductCatalogRepository.findAllBySupplierId(supplierId).stream()
                .map(SupplierProductCatalogResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional
    public List<SupplierProductCatalogResponse> saveProductCatalog(Long supplierId,
            List<SupplierProductCatalogRequest> requests) {
        Supplier supplier = supplierRepository.findById(supplierId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy NCC"));

        List<SupplierProductCatalogRequest> safeRequests = requests != null ? requests : List.of();
        List<Long> productIds = safeRequests.stream()
                .map(SupplierProductCatalogRequest::getProductId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<Long, Product> productMap = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        if (productMap.size() != productIds.size()) {
            List<Long> missing = productIds.stream().filter(id -> !productMap.containsKey(id)).toList();
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
            catalog.setStatus(
                    request.getStatus() != null ? request.getStatus() : SupplierProductCatalogStatus.CHECKING);
            catalog.setNote(request.getNote());
            supplierProductCatalogRepository.save(catalog);
        }

        if (!productIds.isEmpty()) {
            supplierProductCatalogRepository.deleteBySupplierIdAndProductIdNotIn(supplierId, productIds);
        } else {
            supplierProductCatalogRepository.deleteBySupplierId(supplierId);
        }

        return getProductCatalog(supplierId);
    }

    private void mapRequestToEntity(SupplierRequest req, Supplier supplier) {
        supplier.setName(req.getName());
        supplier.setTaxCode(req.getTaxCode());
        supplier.setContactName(req.getContactName());
        supplier.setPhone(req.getPhone());
        supplier.setEmail(req.getEmail());
        supplier.setProvinceId(req.getProvinceId());
        supplier.setAddressDetail(req.getAddressDetail());
        supplier.setStatus(req.getStatus());
    }
}
