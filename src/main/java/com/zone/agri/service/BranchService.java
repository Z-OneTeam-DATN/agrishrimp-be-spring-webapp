package com.zone.agri.service;

import com.zone.agri.common.AuthUtils;
import com.zone.agri.common.RoleUtils;
import com.zone.agri.dto.response.admin.BranchDTO;
import com.zone.agri.dto.request.branch.CheckStockItemRequest;
import com.zone.agri.dto.response.geo.CoordinateDto;
import com.zone.agri.dto.response.user.UserDetail;
import com.zone.agri.entity.Branch;
import com.zone.agri.entity.Inventory;
import com.zone.agri.entity.User;
import com.zone.agri.entity.enums.BranchStatus;
import com.zone.agri.exception.BadRequestException;
import com.zone.agri.exception.Forbidden;
import com.zone.agri.exception.NotFoundException;
import com.zone.agri.repository.BranchRepository;
import com.zone.agri.repository.InventoryRepository;
import com.zone.agri.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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

    private static final String BRANCH_VIEW_AUTHORITY = "BRANCH_VIEW";
    private static final List<String> BRANCH_DIRECTORY_AUTHORITIES = List.of(
            BRANCH_VIEW_AUTHORITY,
            "TRANSFER_CREATE",
            "TRANSFER_UPDATE");

    private final BranchRepository branchRepository;
    private final UserRepository userRepository;
    private final InventoryRepository inventoryRepository;
    private final GeocodingService geocodingService;

    @Transactional(readOnly = true)
    public List<BranchDTO> getAll() {
        UserDetail currentUser = AuthUtils.getUserDetail();
        if (currentUser == null) {
            return List.of();
        }

        List<Branch> branches;
        if (canViewAllBranches(currentUser)) {
            branches = branchRepository.findAll();
        } else if (currentUser.getBranchId() == null) {
            branches = List.of();
        } else {
            branches = branchRepository.findById(currentUser.getBranchId()).stream().toList();
        }

        return branches.stream()
                .map(this::mapToDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BranchDTO> getPublicBranches() {
        return branchRepository.findByStatus(BranchStatus.ACTIVE).stream()
                .map(this::mapToDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public BranchDTO getBranchById(Long id) {
        Branch branch = branchRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy chi nhánh với ID: " + id));
        ensureCurrentUserCanViewBranch(branch.getId());
        return mapToDTO(branch);
    }

    @Transactional(readOnly = true)
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
        validateShippingAddress(dto);

        if (branchRepository.existsByBranchCode(dto.getBranchCode())) {
            throw new RuntimeException("Lỗi: Mã chi nhánh [" + dto.getBranchCode() + "] đã tồn tại!");
        }

        Branch branch = new Branch();
        mapToEntity(branch, dto);

        // Geocode địa chỉ → lat/lng (chỉ gọi trong admin flow) nếu chưa có tọa độ
        if (branch.getLat() == null || branch.getLng() == null) {
            geocodeBranchSilently(branch, dto);
        }

        Branch savedBranch = branchRepository.save(branch);

        // Cập nhật chi nhánh cho các user được chọn làm quản lý
        List<Long> managerIds = resolveManagerIds(dto);
        if (!managerIds.isEmpty()) {
            updateBranchManagers(savedBranch, managerIds);
        }

        return mapToDTO(savedBranch);
    }

    @Transactional
    public BranchDTO update(Long id, BranchDTO dto) {
        validateShippingAddress(dto);

        Branch branch = branchRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Chi nhánh không tồn tại"));

        if (branchRepository.existsByBranchCodeForUpdate(dto.getBranchCode(), id)) {
            throw new RuntimeException("Lỗi: Mã chi nhánh mới bị trùng!");
        }

        // Lưu địa chỉ cũ để so sánh trước khi set
        String oldAddress = branch.getAddressDetail();

        mapToEntity(branch, dto);

        // Re-geocode nếu địa chỉ thay đổi và không truyền tọa độ mới
        if (branch.getLat() == null || branch.getLng() == null) {
            if (dto.getAddressDetail() != null && !dto.getAddressDetail().equals(oldAddress)) {
                geocodeBranchSilently(branch, dto);
            }
        }

        // Xử lý cập nhật danh sách quản lý
        updateBranchManagers(branch, resolveManagerIds(dto));

        return mapToDTO(branchRepository.save(branch));
    }

    private List<Long> resolveManagerIds(BranchDTO dto) {
        if (dto.getManagerIds() != null) {
            return dto.getManagerIds();
        }
        if (dto.getManagerId() != null) {
            return List.of(dto.getManagerId());
        }
        return List.of();
    }

    private void validateShippingAddress(BranchDTO dto) {
        Integer districtId = dto.getDistrictId() != null ? dto.getDistrictId() : dto.getDistrictCode();
        String wardCode = dto.getWardCode();

        if (districtId == null) {
            throw new BadRequestException("Chi nhanh bat buoc phai co District ID GHN de tinh phi giao hang");
        }
        if (wardCode == null || wardCode.isBlank()) {
            throw new BadRequestException("Chi nhanh bat buoc phai co Ward Code GHN de tinh phi giao hang");
        }
    }

    private void mapToEntity(Branch entity, BranchDTO dto) {
        entity.setName(dto.getName());
        entity.setBranchCode(dto.getBranchCode());
        entity.setBranchType(dto.getBranchType() != null ? dto.getBranchType() : dto.getType());
        entity.setPhone(dto.getPhone());
        entity.setEmail(dto.getEmail());
        entity.setAddressDetail(dto.getAddressDetail() != null ? dto.getAddressDetail() : dto.getDetailAddress());
        entity.setFullAddress(dto.getFullAddress());
        entity.setMapDisplayName(dto.getMapDisplayName());
        entity.setProvinceId(dto.getProvinceId() != null ? dto.getProvinceId() : dto.getProvinceCode());
        entity.setProvinceName(dto.getProvinceName());
        entity.setDistrictId(dto.getDistrictId() != null ? dto.getDistrictId() : dto.getDistrictCode());
        entity.setDistrictName(dto.getDistrictName());
        entity.setWardId(dto.getWardId());
        entity.setWardName(dto.getWardName());
        entity.setWardCode(dto.getWardCode());
        entity.setStatus(dto.getStatus());
        entity.setLat(dto.getLat() != null ? dto.getLat() : dto.getLatitude());
        entity.setLng(dto.getLng() != null ? dto.getLng() : dto.getLongitude());
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
        UserDetail currentUser = AuthUtils.getUserDetail();
        if (currentUser == null) {
            return List.of();
        }
        if (!canViewAllBranches(currentUser) && currentUser.getBranchId() == null) {
            return List.of();
        }
        Long scopedBranchId = !canViewAllBranches(currentUser)
                ? currentUser.getBranchId()
                : null;

        return branchInventoryMap.entrySet().stream()
                .filter(entry -> entry.getKey().getStatus() == BranchStatus.ACTIVE)
                .filter(entry -> scopedBranchId == null || scopedBranchId.equals(entry.getKey().getId()))
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
    private void ensureCurrentUserCanViewBranch(Long branchId) {
        UserDetail currentUser = AuthUtils.getUserDetail();
        if (currentUser == null || canViewAllBranches(currentUser)) {
            return;
        }
        if (currentUser.getBranchId() == null || !currentUser.getBranchId().equals(branchId)) {
            throw new Forbidden("Bạn chỉ được xem thông tin chi nhánh mình quản lý");
        }
    }

    private boolean canViewAllBranches(UserDetail currentUser) {
        return isAdminLike(currentUser) || BRANCH_DIRECTORY_AUTHORITIES.stream().anyMatch(this::hasAuthority);
    }

    private boolean isAdminLike(UserDetail currentUser) {
        return currentUser.getRole() != null
                && RoleUtils.isAdminLikeRole(currentUser.getRole().getSlug());
    }

    private boolean hasAuthority(String authority) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.getAuthorities() != null
                && authentication.getAuthorities().stream()
                .anyMatch(grantedAuthority -> authority.equalsIgnoreCase(grantedAuthority.getAuthority()));
    }

    private void geocodeBranchSilently(Branch branch, BranchDTO dto) {
        try {
            String detail = branch.getAddressDetail();
            String ward = dto.getWardName();
            String province = dto.getProvinceName();

            List<String> parts = new ArrayList<>();
            if (detail != null && !detail.isBlank()) parts.add(detail);
            if (ward != null && !ward.isBlank()) parts.add(ward);
            if (province != null && !province.isBlank()) parts.add(province);

            String fullAddress = String.join(", ", parts);

            if (!fullAddress.isBlank()) {
                CoordinateDto coord = geocodingService.geocode(fullAddress);
                branch.setLat(coord.getLat());
                branch.setLng(coord.getLng());
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
        dto.setType(entity.getBranchType());
        dto.setName(entity.getName());
        dto.setPhone(entity.getPhone());
        dto.setEmail(entity.getEmail());
        dto.setAddressDetail(entity.getAddressDetail());
        dto.setDetailAddress(entity.getAddressDetail());
        dto.setFullAddress(entity.getFullAddress());
        dto.setMapDisplayName(entity.getMapDisplayName());
        dto.setProvinceId(entity.getProvinceId());
        dto.setProvinceCode(entity.getProvinceId());
        dto.setProvinceName(entity.getProvinceName());
        dto.setDistrictId(entity.getDistrictId());
        dto.setDistrictCode(entity.getDistrictId());
        dto.setDistrictName(entity.getDistrictName());
        dto.setWardId(entity.getWardId());
        dto.setWardName(entity.getWardName());
        dto.setWardCode(entity.getWardCode());
        dto.setLat(entity.getLat());
        dto.setLng(entity.getLng());
        dto.setLatitude(entity.getLat());
        dto.setLongitude(entity.getLng());
        dto.setStatus(entity.getStatus());

        if (entity.getUsers() != null) {
            dto.setManagerIds(entity.getUsers().stream().map(User::getId).toList());
            if (!dto.getManagerIds().isEmpty()) {
                dto.setManagerId(dto.getManagerIds().get(0));
            }
            dto.setManagerNames(entity.getUsers().stream().map(User::getFullName).toList());
            dto.setManagerAvatarUrls(entity.getUsers().stream().map(User::getAvatarUrl).toList());
        }
        return dto;
    }
}
