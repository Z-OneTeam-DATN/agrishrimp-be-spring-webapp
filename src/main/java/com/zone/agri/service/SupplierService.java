package com.zone.agri.service;

import com.zone.agri.dto.request.supplier.SupplierImportDto;
import com.zone.agri.dto.request.supplier.SupplierRequest;
import com.zone.agri.dto.response.supplier.SupplierResponse;
import com.zone.agri.entity.InventoryNote;
import com.zone.agri.entity.Supplier;
import com.zone.agri.entity.enums.SupplierStatus;
import com.zone.agri.repository.InventoryNoteRepository;
import com.zone.agri.entity.enums.InventoryNoteType;
import com.zone.agri.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SupplierService {
    private final SupplierRepository supplierRepository;
    private final InventoryNoteRepository inventoryNoteRepository;

    @Transactional
    public SupplierResponse createSupplier(SupplierRequest request) {
        if(supplierRepository.existsByTaxCode(request.getTaxCode())) {
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
        List<InventoryNote> notes = inventoryNoteRepository.findImportHistoryBySupplierId(supplierId, InventoryNoteType.IMPORT);

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
                                        Objects.requireNonNullElse(detail.getQuantityRequested(), Objects.requireNonNullElse(detail.getQuantity(), 0))))
                                .sum()
                        : 0)
                .build()).collect(Collectors.toList());
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
