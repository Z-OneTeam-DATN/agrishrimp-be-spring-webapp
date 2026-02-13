package com.zone.agri.service;

import com.zone.agri.dto.supplier.SupplierRequest;
import com.zone.agri.dto.supplier.SupplierResponse;
import com.zone.agri.entity.Category;
import com.zone.agri.entity.Supplier;
import com.zone.agri.entity.enums.SupplierStatus;
import com.zone.agri.repository.CategoryRepository;
import com.zone.agri.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class SupplierService {
    private final SupplierRepository supplierRepository;
    private final CategoryRepository categoryRepository;

    @Transactional
    public SupplierResponse createSupplier(SupplierRequest request) {
        if(supplierRepository.existsByTaxCode(request.getTaxCode())) {
            throw new RuntimeException("Mã số thuế " + request.getTaxCode() + " đã tồn tại");
        }

        Supplier supplier = new Supplier();
        mapRequestToEntity(request, supplier);

        supplier.setCode("NCC-" + System.currentTimeMillis());
        supplier.setCurrentDebt(BigDecimal.ZERO);

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

    // --- 3. LẤY CHI TIẾT ---
    public SupplierResponse getSupplierById(Long id) {
        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy NCC"));
        return SupplierResponse.fromEntity(supplier);
    }

    // --- 4. LẤY DANH SÁCH (TÍCH HỢP LỌC DANH MỤC ĐỘNG) ---
    public Page<SupplierResponse> getAllSuppliers(String keyword, String category, String status, Pageable pageable) {
        // Chuyển category từ String sang Long (vì là ID danh mục từ database)
        Long categoryId = null;
        if (category != null && !"all".equalsIgnoreCase(category) && !category.isEmpty()) {
            try {
                categoryId = Long.parseLong(category);
            } catch (NumberFormatException e) {
                categoryId = null;
            }
        }

        SupplierStatus supplierStatus = null;
        if (status != null && !"all".equalsIgnoreCase(status) && !status.isEmpty()) {
            try {
                supplierStatus = SupplierStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                supplierStatus = null;
            }
        }

        return supplierRepository.searchSuppliers(keyword, categoryId, supplierStatus, pageable)
                .map(SupplierResponse::fromEntity);
    }

    // --- 5. XÓA ---
    @Transactional
    public void deleteSupplier(Long id) {
        if (!supplierRepository.existsById(id)) {
            throw new RuntimeException("Không tìm thấy nhà cung cấp để xóa");
        }
        supplierRepository.deleteById(id);
    }

    // --- HÀM HỖ TRỢ: MAP DATA ---
    private void mapRequestToEntity(SupplierRequest req, Supplier supplier) {
        supplier.setName(req.getName());
        supplier.setTaxCode(req.getTaxCode());

        // Trong hàm mapRequestToEntity
        if (req.getCategory() != null && !req.getCategory().isEmpty()) {
            try {
                // Frontend gửi lên ID (String "1", "2"...), ta parse sang Long
                Long catId = Long.parseLong(req.getCategory());

                // Tìm Object Category trong database bằng Repo
                Category category = categoryRepository.findById(catId)
                        .orElseThrow(() -> new RuntimeException("Danh mục không tồn tại"));

                // Gán Object vào Entity Supplier (Đúng kiểu Category)
                supplier.setCategory(category);
            } catch (NumberFormatException e) {
                // Xử lý nếu category gửi lên không phải là số ID
                System.out.println("Lỗi parse ID danh mục: " + req.getCategory());
            }
        }

        supplier.setContactName(req.getContactName());
        supplier.setPhone(req.getPhone());
        supplier.setEmail(req.getEmail());
        supplier.setProvinceId(req.getProvinceId());
        supplier.setAddressDetail(req.getAddressDetail());
        supplier.setPaymentTerm(req.getPaymentTerms());
        supplier.setCreditLimit(req.getCreditLimit());
        supplier.setDiscount(req.getDiscount());
        supplier.setBankAccountNumber(req.getBankAccountNumber());
        supplier.setBankName(req.getBankName());
        supplier.setBankAccountHolder(req.getBankAccountHolder());
        supplier.setStatus(req.getStatus());
        supplier.setNote(req.getNote());
    }
}