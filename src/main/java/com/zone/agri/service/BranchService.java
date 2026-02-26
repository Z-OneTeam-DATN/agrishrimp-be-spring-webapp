package com.zone.agri.service;

import com.zone.agri.dto.admin.BranchDTO;
import com.zone.agri.dto.branch.CheckStockItemRequest;
import com.zone.agri.dto.geo.CoordinateDto;
import com.zone.agri.entity.Branch;
import com.zone.agri.entity.Inventory;
import com.zone.agri.entity.User;
import com.zone.agri.entity.enums.BranchStatus;
import com.zone.agri.repository.BranchRepository;
import com.zone.agri.repository.InventoryRepository;
import com.zone.agri.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BranchService {

    private final BranchRepository branchRepository;
    private final UserRepository userRepository;
    private final InventoryRepository inventoryRepository;
    private final GeocodingService geocodingService;

    public List<BranchDTO> getAll() {
        return branchRepository.findAll().stream()
                .map(this::mapToDTO)
                .toList();
    }

    public List<BranchDTO> getPublicBranches() {
        return branchRepository.findByStatus(BranchStatus.ACTIVE).stream()
                .map(this::mapToDTO)
                .toList();
    }

    public BranchDTO getBranchById(Long id) {
        Branch branch = branchRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy chi nhánh với ID: " + id));
        return mapToDTO(branch);
    }

    @Transactional
    public BranchDTO create(BranchDTO dto) {
        if (branchRepository.existsByBranchCode(dto.getBranchCode())) {
            throw new RuntimeException("Lỗi: Mã chi nhánh [" + dto.getBranchCode() + "] đã tồn tại!");
        }

        Branch branch = Branch.builder()
                .branchCode(dto.getBranchCode())
                .branchType(dto.getBranchType())
                .name(dto.getName())
                .phone(dto.getPhone())
                .email(dto.getEmail())
                .addressDetail(dto.getAddressDetail())
                .provinceId(dto.getProvinceId())
                .districtId(dto.getDistrictId())
                .wardId(dto.getWardId())
                .status(dto.getStatus())
                .build();

        // Geocode địa chỉ → lat/lng (chỉ gọi trong admin flow)
        geocodeBranchSilently(branch);

        Branch savedBranch = branchRepository.save(branch);

        // Cập nhật chi nhánh cho các user được chọn làm quản lý
        if (dto.getManagerIds() != null && !dto.getManagerIds().isEmpty()) {
            updateBranchManagers(savedBranch, dto.getManagerIds());
        }

        return mapToDTO(savedBranch);
    }

    @Transactional
    public BranchDTO update(Long id, BranchDTO dto) {
        Branch branch = branchRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Chi nhánh không tồn tại"));

        if (branchRepository.existsByBranchCodeForUpdate(dto.getBranchCode(), id)) {
            throw new RuntimeException("Lỗi: Mã chi nhánh mới bị trùng!");
        }

        // Lưu địa chỉ cũ để so sánh trước khi set
        String oldAddress = branch.getAddressDetail();

        branch.setName(dto.getName());
        branch.setBranchCode(dto.getBranchCode());
        branch.setBranchType(dto.getBranchType());
        branch.setPhone(dto.getPhone());
        branch.setEmail(dto.getEmail());
        branch.setAddressDetail(dto.getAddressDetail());
        branch.setProvinceId(dto.getProvinceId());
        branch.setDistrictId(dto.getDistrictId());
        branch.setWardId(dto.getWardId());
        branch.setStatus(dto.getStatus());

        // Re-geocode nếu địa chỉ thay đổi
        if (!dto.getAddressDetail().equals(oldAddress)) {
            geocodeBranchSilently(branch);
        }

        // Xử lý cập nhật danh sách quản lý
        updateBranchManagers(branch, dto.getManagerIds());

        return mapToDTO(branchRepository.save(branch));
    }

    private void updateBranchManagers(Branch branch, List<Long> managerIds) {
        // 1. Gỡ bỏ chi nhánh cũ của các user đang thuộc chi nhánh này
        List<User> currentStaff = branch.getUsers();
        if (currentStaff != null) {
            currentStaff.forEach(user -> user.setBranch(null));
        }

        // 2. Gán chi nhánh mới cho danh sách managerIds truyền lên
        if (managerIds != null && !managerIds.isEmpty()) {
            List<User> newManagers = userRepository.findAllById(managerIds);
            newManagers.forEach(user -> user.setBranch(branch));
            branch.setUsers(newManagers);
        }
    }

    @Transactional
    public void delete(Long id) {
        Branch branch = branchRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy chi nhánh cần xóa"));

        long transactionCount = branchRepository.countRelatedTransactions(id);
        if (transactionCount > 0) {
            // Soft delete nếu có dữ liệu liên quan
            branch.setStatus(BranchStatus.INACTIVE);
            branchRepository.save(branch);
            throw new RuntimeException("Chi nhánh đã phát sinh giao dịch. Hệ thống đã chuyển sang trạng thái 'Ngừng hoạt động' thay vì xóa vĩnh viễn.");
        } else {
            // Xóa liên kết user trước khi xóa branch để tránh lỗi Constraint
            if (branch.getUsers() != null) {
                branch.getUsers().forEach(u -> u.setBranch(null));
            }
            branchRepository.delete(branch);
        }
    }

    public List<BranchDTO> findBranchesWithEnoughStock(List<CheckStockItemRequest> items) {
        if (items == null || items.isEmpty()) {
            return new ArrayList<>();
        }

        // 1. Trích xuất danh sách ID sản phẩm từ Giỏ hàng gửi lên
        List<Long> variantIds = items.stream()
                .map(CheckStockItemRequest::getVariantId)
                .toList();

        // 2. Gọi DB ĐÚNG 1 LẦN để lấy toàn bộ tồn kho của các sản phẩm này tại TẤT CẢ chi nhánh
        List<Inventory> inventories = inventoryRepository.findByProductVariantIdIn(variantIds);

        // 3. Gom nhóm tồn kho theo từng Chi nhánh (Key là Branch, Value là List<Inventory> của Branch đó)
        Map<Branch, List<Inventory>> branchInventoryMap = inventories.stream()
                .collect(Collectors.groupingBy(Inventory::getBranch));

        List<BranchDTO> eligibleBranches = new ArrayList<>();

        // 4. Quét từng chi nhánh xem có "vượt qua bài test" không
        for (Map.Entry<Branch, List<Inventory>> entry : branchInventoryMap.entrySet()) {
            Branch branch = entry.getKey();
            List<Inventory> branchStock = entry.getValue();

            // Chỉ xét các chi nhánh đang hoạt động (Giả sử Huy có Enum status là ACTIVE)
            if (!"ACTIVE".equals(branch.getStatus().name())) continue;

            boolean hasEnoughStockForAll = true;

            // Kiểm tra TỪNG MÓN HÀNG trong giỏ đối chiếu với kho của chi nhánh này
            for (CheckStockItemRequest requestedItem : items) {

                // Tìm tồn kho thực tế của món hàng này trong chi nhánh
                Optional<Inventory> stockOfItem = branchStock.stream()
                        .filter(inv -> inv.getProductVariant().getId().equals(requestedItem.getVariantId()))
                        .findFirst();

                // Nếu chi nhánh KHÔNG CÓ món này, HOẶC số lượng nhỏ hơn số khách đặt -> Rớt bài test!
                if (stockOfItem.isEmpty() || stockOfItem.get().getQuantity() < requestedItem.getQuantity()) {
                    hasEnoughStockForAll = false;
                    break; // Dừng luôn vòng lặp kiểm tra sản phẩm, chuyển qua chi nhánh tiếp theo
                }
            }

            // Nếu xuất sắc vượt qua bài test (đủ hàng cho toàn bộ sản phẩm)
            if (hasEnoughStockForAll) {
                // Chuyển Branch Entity sang DTO (Huy chỉnh lại hàm map cho đúng với code của Huy nhé)
                BranchDTO dto = new BranchDTO();
                dto.setId(branch.getId());
                dto.setName(branch.getName());
                dto.setProvinceId(branch.getProvinceId());
                dto.setAddressDetail(branch.getAddressDetail());
                // ... map các field khác nếu cần

                eligibleBranches.add(dto);
            }
        }

        return eligibleBranches;
    }

    /**
     * Geocode địa chỉ chi nhánh → lat/lng.
     * Silent: lỗi geocoding không dừng việc lưu branch.
     */
    private void geocodeBranchSilently(Branch branch) {
        try {
            if (branch.getAddressDetail() != null && !branch.getAddressDetail().isBlank()) {
                CoordinateDto coord = geocodingService.geocode(branch.getAddressDetail());
                branch.setLat(coord.getLat());
                branch.setLng(coord.getLng());
                branch.setGeocodedAt(Instant.now());
            }
        } catch (Exception e) {
            log.warn("Geocoding failed for branch '{}', sẽ không có tọa độ: {}", branch.getName(), e.getMessage());
        }
    }

    private BranchDTO mapToDTO(Branch entity) {
        BranchDTO dto = new BranchDTO();
        dto.setId(entity.getId());
        dto.setBranchCode(entity.getBranchCode());
        dto.setBranchType(entity.getBranchType());
        dto.setName(entity.getName());
        dto.setPhone(entity.getPhone());
        dto.setEmail(entity.getEmail());
        dto.setAddressDetail(entity.getAddressDetail());
        dto.setProvinceId(entity.getProvinceId());
        dto.setDistrictId(entity.getDistrictId());
        dto.setWardId(entity.getWardId());
        dto.setStatus(entity.getStatus());

        if (entity.getUsers() != null) {
            dto.setManagerIds(entity.getUsers().stream().map(User::getId).toList());
            dto.setManagerNames(entity.getUsers().stream().map(User::getFullName).toList());
        }
        return dto;
    }
}