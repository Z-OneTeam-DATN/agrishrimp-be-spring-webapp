package com.zone.agri.service;

import com.zone.agri.dto.response.admin.BranchDTO;
import com.zone.agri.dto.request.branch.CheckStockItemRequest;
import com.zone.agri.dto.response.geo.CoordinateDto;
import com.zone.agri.entity.Branch;
import com.zone.agri.entity.Inventory;
import com.zone.agri.entity.User;
import com.zone.agri.entity.enums.BranchStatus;
import com.zone.agri.exception.NotFoundException;
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

    public BranchDTO getPublicBranchById(Long id) {
        Branch branch = branchRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Chi nhánh không tồn tại."));
        if (branch.getStatus() != BranchStatus.ACTIVE) {
            throw new NotFoundException("Chi nhánh không tồn tại hoặc đã ngừng hoạt động.");
        }
        return mapToDTO(branch);
    }

    @Transactional
    public BranchDTO create(BranchDTO dto) {
        if (branchRepository.existsByBranchCode(dto.getBranchCode())) {
            throw new RuntimeException("Lỗi: Mã chi nhánh [" + dto.getBranchCode() + "] đã tồn tại!");
        }

        Branch branch = new Branch();
        mapToEntity(branch, dto);

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

        mapToEntity(branch, dto);

        // Re-geocode nếu địa chỉ thay đổi
        if (dto.getAddressDetail() != null && !dto.getAddressDetail().equals(oldAddress)) {
            geocodeBranchSilently(branch);
        }

        // Xử lý cập nhật danh sách quản lý
        updateBranchManagers(branch, dto.getManagerIds());

        return mapToDTO(branchRepository.save(branch));
    }

    private void mapToEntity(Branch entity, BranchDTO dto) {
        entity.setName(dto.getName());
        entity.setBranchCode(dto.getBranchCode());
        entity.setBranchType(dto.getBranchType());
        entity.setPhone(dto.getPhone());
        entity.setEmail(dto.getEmail());
        entity.setAddressDetail(dto.getAddressDetail());
        entity.setProvinceId(dto.getProvinceId());
        entity.setDistrictId(dto.getDistrictId());
        entity.setWardId(dto.getWardId());
        entity.setWardCode(dto.getWardCode());
        entity.setStatus(dto.getStatus());
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
        } else if (branch.getUsers() != null) {
            branch.getUsers().clear();
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

        // 3. Gom nhóm tồn kho theo từng Chi nhánh (Key là Branch, Value là Map<VariantId, Quantity>)
        // Tối ưu lookup bằng cách chuyển list tồn kho thành Map<Long, Integer>
        Map<Branch, Map<Long, Integer>> branchInventoryMap = inventories.stream()
                .collect(Collectors.groupingBy(
                        Inventory::getBranch,
                        Collectors.toMap(
                                inv -> inv.getProductVariant().getId(),
                                Inventory::getQuantity,
                                Integer::sum // handle duplicate variantId if any
                        )
                ));

        // 4. Quét từng chi nhánh xem có "vượt qua bài test" không
        return branchInventoryMap.entrySet().stream()
                .filter(entry -> entry.getKey().getStatus() == BranchStatus.ACTIVE)
                .filter(entry -> {
                    Map<Long, Integer> branchStock = entry.getValue();
                    for (CheckStockItemRequest requestedItem : items) {
                        Integer currentStock = branchStock.getOrDefault(requestedItem.getVariantId(), 0);
                        if (currentStock < requestedItem.getQuantity()) {
                            return false;
                        }
                    }
                    return true;
                })
                .map(entry -> mapToDTO(entry.getKey()))
                .toList();
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
        dto.setWardCode(entity.getWardCode());
        dto.setLat(entity.getLat());
        dto.setLng(entity.getLng());
        dto.setStatus(entity.getStatus());

        if (entity.getUsers() != null) {
            dto.setManagerIds(entity.getUsers().stream().map(User::getId).toList());
            dto.setManagerNames(entity.getUsers().stream().map(User::getFullName).toList());
            dto.setManagerAvatarUrls(entity.getUsers().stream().map(User::getAvatarUrl).toList());
        }
        return dto;
    }
}
