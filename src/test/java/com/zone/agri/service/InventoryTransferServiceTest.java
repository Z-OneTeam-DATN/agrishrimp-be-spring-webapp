package com.zone.agri.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.zone.agri.common.AuthUtils;
import com.zone.agri.common.WarehouseContext;
import com.zone.agri.dto.request.transfer.TransferItemRequest;
import com.zone.agri.dto.request.transfer.TransferQCRequest;
import com.zone.agri.dto.request.transfer.TransferRequest;
import com.zone.agri.dto.request.transfer.TransferSettlementRequest;
import com.zone.agri.dto.response.transfer.TransferDetailResponse;
import com.zone.agri.dto.response.user.UserDetail;
import com.zone.agri.entity.Branch;
import com.zone.agri.entity.Inventory;
import com.zone.agri.entity.InventoryTransaction;
import com.zone.agri.entity.InventoryTransfer;
import com.zone.agri.entity.InventoryTransferDetail;
import com.zone.agri.entity.Order;
import com.zone.agri.entity.Product;
import com.zone.agri.entity.ProductVariant;
import com.zone.agri.entity.SubOrder;
import com.zone.agri.entity.SubOrderItem;
import com.zone.agri.entity.User;
import com.zone.agri.entity.enums.BranchStatus;
import com.zone.agri.entity.enums.InventoryTransferStatus;
import com.zone.agri.entity.enums.OrderStatus;
import com.zone.agri.entity.enums.TransactionType;
import com.zone.agri.entity.enums.TransferBusinessType;
import com.zone.agri.entity.enums.TransferSettlementStatus;
import com.zone.agri.repository.BranchRepository;
import com.zone.agri.repository.InventoryRepository;
import com.zone.agri.repository.InventoryTransactionRepository;
import com.zone.agri.repository.InventoryTransferDetailRepository;
import com.zone.agri.repository.InventoryTransferRepository;
import com.zone.agri.repository.ProductVariantRepository;
import com.zone.agri.repository.SubOrderRepository;
import com.zone.agri.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InventoryTransferServiceTest {

    @Mock
    private InventoryTransferRepository transferRepo;

    @Mock
    private BranchRepository branchRepo;

    @Mock
    private ProductVariantRepository variantRepo;

    @Mock
    private InventoryRepository inventoryRepo;

    @Mock
    private InventoryTransactionRepository transactionRepo;

    @Mock
    private SubOrderRepository subOrderRepo;

    @Mock
    private UserRepository userRepo;

    @Mock
    private BackorderService backorderService;

    @Mock
    private InventoryTransferDetailRepository transferDetailRepo;

    @Mock
    private WarehouseContext warehouseContext;

    @Mock
    private InventoryCheckGuardService inventoryCheckGuardService;

    @Mock
    private SettingService settingService;

    @Spy
    private VietnamRegionResolver vietnamRegionResolver = new VietnamRegionResolver();

    @InjectMocks
    private InventoryTransferService inventoryTransferService;

    private Branch warehouse;
    private Branch sourceBranch;
    private Branch destinationBranch;
    private Branch defectBranch;
    private ProductVariant variant;
    private User requesterUser;
    private User sourceUser;
    private User approverUser;
    private User receiverUser;
    private SubOrder replenishmentSubOrder;
    private Map<Long, User> usersById;
    private List<InventoryTransaction> savedTransactions;
    private List<Inventory> savedInventories;

    @BeforeEach
    void setUp() {
        warehouse = Branch.builder()
                .branchCode("MAIN_WH")
                .branchType("WAREHOUSE")
                .name("Kho Tong")
                .provinceName("Tỉnh Sóc Trăng")
                .build();
        setId(warehouse, 1L, "id");
        warehouse.setStatus(BranchStatus.ACTIVE);
        warehouse.setProvinceName("Soc Trang");
        warehouse.setLat(10.10);
        warehouse.setLng(105.70);

        sourceBranch = Branch.builder()
                .branchCode("CN-HCM")
                .branchType("STORE")
                .name("Chi Nhanh HCM")
                .provinceName("Thành phố Hồ Chí Minh")
                .build();
        setId(sourceBranch, 2L, "id");
        sourceBranch.setStatus(BranchStatus.ACTIVE);
        sourceBranch.setProvinceName("Ho Chi Minh");
        sourceBranch.setLat(10.76);
        sourceBranch.setLng(106.67);

        destinationBranch = Branch.builder()
                .branchCode("CN-CT")
                .branchType("STORE")
                .name("Chi Nhanh Can Tho")
                .provinceName("Thành phố Cần Thơ")
                .build();
        setId(destinationBranch, 3L, "id");
        destinationBranch.setStatus(BranchStatus.ACTIVE);
        destinationBranch.setProvinceName("Can Tho");
        destinationBranch.setLat(10.03);
        destinationBranch.setLng(105.78);

        defectBranch = Branch.builder()
                .branchCode("SYSTEM_DEFECT")
                .branchType("WAREHOUSE")
                .name("Kho Rui Ro")
                .build();
        setId(defectBranch, 99L, "id");

        variant = ProductVariant.builder()
                .sku("SKU-TEST-01")
                .product(Product.builder().name("San Pham Test").build())
                .build();
        setId(variant, 10L, "id");

        requesterUser = User.builder()
                .fullName("Nguoi Tao Phieu")
                .branch(destinationBranch)
                .passwordHash("secret")
                .build();
        setId(requesterUser, 100L, "id");

        sourceUser = User.builder()
                .fullName("Nguoi Xac Nhan Nguon")
                .branch(sourceBranch)
                .passwordHash("secret")
                .build();
        setId(sourceUser, 101L, "id");

        approverUser = User.builder()
                .fullName("Nguoi Duyet")
                .branch(null)
                .passwordHash("secret")
                .build();
        setId(approverUser, 102L, "id");

        receiverUser = User.builder()
                .fullName("Nguoi Nhan Hang")
                .branch(destinationBranch)
                .passwordHash("secret")
                .build();
        setId(receiverUser, 103L, "id");

        Order order = Order.builder()
                .code("ORDTEST001")
                .status(OrderStatus.AWAITING_REPLENISHMENT)
                .build();
        setId(order, 1000L, "id");

        replenishmentSubOrder = SubOrder.builder()
                .status(OrderStatus.AWAITING_REPLENISHMENT)
                .branch(destinationBranch)
                .order(order)
                .items(List.of(SubOrderItem.builder()
                        .productVariant(variant)
                        .quantity(5)
                        .missingQuantity(2)
                        .unitPrice(new BigDecimal("210000"))
                        .build()))
                .build();
        setId(replenishmentSubOrder, 34L, "id");

        usersById = new HashMap<>();
        usersById.put(requesterUser.getId(), requesterUser);
        usersById.put(sourceUser.getId(), sourceUser);
        usersById.put(approverUser.getId(), approverUser);
        usersById.put(receiverUser.getId(), receiverUser);

        savedTransactions = new ArrayList<>();
        savedInventories = new ArrayList<>();

        when(userRepo.findById(anyLong()))
                .thenAnswer(invocation -> Optional.ofNullable(usersById.get(invocation.getArgument(0))));
        when(branchRepo.findById(warehouse.getId())).thenReturn(Optional.of(warehouse));
        when(branchRepo.findById(sourceBranch.getId())).thenReturn(Optional.of(sourceBranch));
        when(branchRepo.findById(destinationBranch.getId())).thenReturn(Optional.of(destinationBranch));
        when(branchRepo.findByBranchCode("SYSTEM_DEFECT")).thenReturn(Optional.of(defectBranch));
        when(branchRepo.findAll()).thenReturn(List.of(warehouse, sourceBranch, destinationBranch));
        when(variantRepo.findBySku(variant.getSku())).thenReturn(Optional.of(variant));
        when(settingService.getProfitMarginRaw()).thenReturn("30");
        when(settingService.calculateSellingPrice(any(BigDecimal.class)))
                .thenAnswer(invocation -> ((BigDecimal) invocation.getArgument(0))
                        .multiply(new BigDecimal("1.30"))
                        .setScale(2, BigDecimal.ROUND_HALF_UP));
        when(transferRepo.save(any(InventoryTransfer.class)))
                .thenAnswer(invocation -> {
                    InventoryTransfer transfer = invocation.getArgument(0);
                    if (transfer.getId() == null) {
                        setId(transfer, 500L, "id");
                    }
                    return transfer;
                });
        when(inventoryRepo.save(any(Inventory.class)))
                .thenAnswer(invocation -> {
                    Inventory inventory = invocation.getArgument(0);
                    savedInventories.add(inventory);
                    return inventory;
                });
        when(transactionRepo.save(any(InventoryTransaction.class)))
                .thenAnswer(invocation -> {
                    InventoryTransaction transaction = invocation.getArgument(0);
                    savedTransactions.add(transaction);
                    return transaction;
                });
        when(transactionRepo.findByReferenceCodeAndType(anyString(), eq(TransactionType.TRANSFER_OUT)))
                .thenAnswer(invocation -> {
                    String referenceCode = invocation.getArgument(0);
                    return savedTransactions.stream()
                            .filter(tx -> tx.getType() == TransactionType.TRANSFER_OUT
                                    && referenceCode.equals(tx.getReferenceCode()))
                            .toList();
                });
        when(warehouseContext.resolveWarehouseId()).thenReturn(null);
    }

    @Test
    void createGreedyReplenishmentForSubOrder_returnsUncoveredQuantityWhenNoSourceHasStock() {
        when(subOrderRepo.findByIdWithItems(34L)).thenReturn(Optional.of(replenishmentSubOrder));
        when(transferRepo.findByReferenceCodeAndStatusInOrderByCreatedAtDesc(any(), any())).thenReturn(List.of());
        when(inventoryRepo.findByProductVariantId(10L)).thenReturn(List.of());

        InventoryTransferService.ReplenishmentCreationResult result = callAs(
                requesterUser,
                () -> inventoryTransferService.createGreedyReplenishmentForSubOrder(replenishmentSubOrder));

        assertThat(result.transfers()).isEmpty();
        assertThat(result.uncoveredQuantitiesByVariantId()).containsEntry(variant.getId(), 2);
    }

    @Test
    void createGreedyReplenishmentForSubOrder_allowsReverseTransferIntoWarehouse() {
        requesterUser.setBranch(warehouse);
        replenishmentSubOrder.setBranch(warehouse);

        when(subOrderRepo.findByIdWithItems(34L)).thenReturn(Optional.of(replenishmentSubOrder));
        when(transferRepo.findByReferenceCodeAndStatusInOrderByCreatedAtDesc(any(), any())).thenReturn(List.of());
        when(inventoryRepo.findByProductVariantId(variant.getId()))
                .thenReturn(List.of(createInventory(sourceBranch, variant, 2)));
        when(transferRepo.countTotalTransfers()).thenReturn(40L);

        InventoryTransferService.ReplenishmentCreationResult result = callAs(
                requesterUser,
                () -> inventoryTransferService.createGreedyReplenishmentForSubOrder(replenishmentSubOrder));

        assertThat(result.uncoveredQuantitiesByVariantId()).isEmpty();
        assertThat(result.transfers()).hasSize(1);
        assertThat(result.transfers().get(0).getFromBranch()).isEqualTo(sourceBranch);
        assertThat(result.transfers().get(0).getToBranch()).isEqualTo(warehouse);
        assertThat(result.transfers().get(0).getTotalQuantity()).isEqualTo(2);
    }

    @Test
    void createGreedyReplenishmentForSubOrder_prefersWarehouseSourceWhenDistanceTied() {
        requesterUser.setBranch(warehouse);
        replenishmentSubOrder.setBranch(warehouse);

        Branch warehouseSource = Branch.builder()
                .branchCode("WH-SECONDARY")
                .branchType("WAREHOUSE")
                .name("Kho Phu")
                .provinceName("Tá»‰nh SĂ³c TrÄƒng")
                .build();
        setId(warehouseSource, 4L, "id");
        warehouseSource.setStatus(BranchStatus.ACTIVE);
        warehouseSource.setProvinceName("Soc Trang");
        warehouseSource.setLat(10.30);
        warehouseSource.setLng(105.90);

        Branch storeSource = Branch.builder()
                .branchCode("CN-NEAR")
                .branchType("STORE")
                .name("Chi Nhanh Gan")
                .provinceName("ThĂ nh phá»‘ Cáº§n ThÆ¡")
                .build();
        setId(storeSource, 5L, "id");
        storeSource.setStatus(BranchStatus.ACTIVE);
        storeSource.setProvinceName("Can Tho");
        storeSource.setLat(10.30);
        storeSource.setLng(105.90);

        when(subOrderRepo.findByIdWithItems(34L)).thenReturn(Optional.of(replenishmentSubOrder));
        when(transferRepo.findByReferenceCodeAndStatusInOrderByCreatedAtDesc(any(), any())).thenReturn(List.of());
        when(branchRepo.findAll()).thenReturn(List.of(warehouse, warehouseSource, storeSource));
        when(branchRepo.findById(warehouseSource.getId())).thenReturn(Optional.of(warehouseSource));
        when(branchRepo.findById(storeSource.getId())).thenReturn(Optional.of(storeSource));
        when(inventoryRepo.findByProductVariantId(variant.getId())).thenReturn(List.of(
                createInventory(warehouseSource, variant, 2),
                createInventory(storeSource, variant, 2)));
        when(transferRepo.countTotalTransfers()).thenReturn(41L);

        InventoryTransferService.ReplenishmentCreationResult result = callAs(
                requesterUser,
                () -> inventoryTransferService.createGreedyReplenishmentForSubOrder(replenishmentSubOrder));

        assertThat(result.uncoveredQuantitiesByVariantId()).isEmpty();
        assertThat(result.transfers()).hasSize(1);
        assertThat(result.transfers().get(0).getFromBranch()).isEqualTo(warehouseSource);
    }

    @Test
    void createGreedyReplenishmentForSubOrder_respectsMinStockWhenWarehouseIsPrimary() {
        requesterUser.setBranch(warehouse);
        replenishmentSubOrder.setBranch(warehouse);
        replenishmentSubOrder.getItems().get(0).setMissingQuantity(4);

        when(subOrderRepo.findByIdWithItems(34L)).thenReturn(Optional.of(replenishmentSubOrder));
        when(transferRepo.findByReferenceCodeAndStatusInOrderByCreatedAtDesc(any(), any())).thenReturn(List.of());
        when(inventoryRepo.findByProductVariantId(variant.getId()))
                .thenReturn(List.of(createInventory(sourceBranch, variant, 10, 0, 7)));
        when(transferRepo.countTotalTransfers()).thenReturn(42L);

        InventoryTransferService.ReplenishmentCreationResult result = callAs(
                requesterUser,
                () -> inventoryTransferService.createGreedyReplenishmentForSubOrder(replenishmentSubOrder));

        assertThat(result.transfers()).hasSize(1);
        assertThat(result.transfers().get(0).getTotalQuantity()).isEqualTo(3);
        assertThat(result.uncoveredQuantitiesByVariantId()).containsEntry(variant.getId(), 1);
    }

    @Test
    void createGreedyReplenishmentForSubOrder_returnsPartialCoverageForWarehouseDestination() {
        requesterUser.setBranch(warehouse);
        replenishmentSubOrder.setBranch(warehouse);
        replenishmentSubOrder.getItems().get(0).setMissingQuantity(5);

        Branch warehouseSource = Branch.builder()
                .branchCode("WH-SUPPORT")
                .branchType("WAREHOUSE")
                .name("Kho Ho Tro")
                .provinceName("Tá»‰nh SĂ³c TrÄƒng")
                .build();
        setId(warehouseSource, 4L, "id");
        warehouseSource.setStatus(BranchStatus.ACTIVE);
        warehouseSource.setProvinceName("Soc Trang");
        warehouseSource.setLat(10.50);
        warehouseSource.setLng(105.95);

        when(subOrderRepo.findByIdWithItems(34L)).thenReturn(Optional.of(replenishmentSubOrder));
        when(transferRepo.findByReferenceCodeAndStatusInOrderByCreatedAtDesc(any(), any())).thenReturn(List.of());
        when(branchRepo.findAll()).thenReturn(List.of(warehouse, sourceBranch, warehouseSource));
        when(branchRepo.findById(warehouseSource.getId())).thenReturn(Optional.of(warehouseSource));
        when(inventoryRepo.findByProductVariantId(variant.getId())).thenReturn(List.of(
                createInventory(sourceBranch, variant, 2),
                createInventory(warehouseSource, variant, 1)));
        when(transferRepo.countTotalTransfers()).thenReturn(43L, 44L);

        InventoryTransferService.ReplenishmentCreationResult result = callAs(
                requesterUser,
                () -> inventoryTransferService.createGreedyReplenishmentForSubOrder(replenishmentSubOrder));

        assertThat(result.transfers()).hasSize(2);
        assertThat(result.transfers())
                .extracting(InventoryTransfer::getTotalQuantity)
                .containsExactlyInAnyOrder(2, 1);
        assertThat(result.uncoveredQuantitiesByVariantId()).containsEntry(variant.getId(), 2);
    }

    @Test
    void createReplenishmentTransfersForSubOrder_usesOtherBranchesBeforeWarehouseFallback() {
        Branch supplyingBranch = Branch.builder()
                .branchCode("CN-ST")
                .branchType("STORE")
                .name("Chi Nhanh Soc Trang")
                .build();
        setId(supplyingBranch, 4L, "id");
        supplyingBranch.setStatus(BranchStatus.ACTIVE);
        supplyingBranch.setLat(9.60);
        supplyingBranch.setLng(105.97);

        replenishmentSubOrder.getItems().get(0).setMissingQuantity(5);

        when(subOrderRepo.findByIdWithItems(34L)).thenReturn(Optional.of(replenishmentSubOrder));
        when(transferRepo.findByReferenceCodeAndStatusInOrderByCreatedAtDesc(any(), any())).thenReturn(List.of());
        when(branchRepo.findAll()).thenReturn(List.of(warehouse, sourceBranch, destinationBranch, supplyingBranch));
        when(branchRepo.findById(supplyingBranch.getId())).thenReturn(Optional.of(supplyingBranch));
        when(inventoryRepo.findByProductVariantId(variant.getId())).thenReturn(List.of(
                createInventory(supplyingBranch, variant, 3),
                createInventory(warehouse, variant, 2)));
        when(transferRepo.countTotalTransfers()).thenReturn(10L, 11L);

        InventoryTransferService.ReplenishmentCreationResult result = callAs(
                requesterUser,
                () -> inventoryTransferService.createGreedyReplenishmentForSubOrder(replenishmentSubOrder));
        List<InventoryTransfer> transfers = result.transfers();

        assertThat(transfers).hasSize(2);
        assertThat(result.uncoveredQuantitiesByVariantId()).isEmpty();

        InventoryTransfer branchTransfer = transfers.stream()
                .filter(transfer -> transfer.getFromBranch().getId().equals(supplyingBranch.getId()))
                .findFirst()
                .orElseThrow();
        InventoryTransfer warehouseTransfer = transfers.stream()
                .filter(transfer -> transfer.getFromBranch().getId().equals(warehouse.getId()))
                .findFirst()
                .orElseThrow();

        assertThat(branchTransfer.getTotalQuantity()).isEqualTo(3);
        assertThat(warehouseTransfer.getTotalQuantity()).isEqualTo(2);
    }

    @Test
    void createMainWarehouseReplenishmentTransferIfPossible_ignoresExistingTransferForOtherQuantity() {
        replenishmentSubOrder.getItems().get(0).setMissingQuantity(5);
        InventoryTransfer existingTransfer = InventoryTransfer.builder()
                .transferCode("PDC-OLD")
                .status(InventoryTransferStatus.PENDING)
                .fromBranch(sourceBranch)
                .toBranch(destinationBranch)
                .referenceCode("ORDTEST001-SUB-34")
                .build();
        InventoryTransferDetail existingDetail = InventoryTransferDetail.builder()
                .inventoryTransfer(existingTransfer)
                .productVariant(variant)
                .quantity(3)
                .quantityRequested(3)
                .build();
        existingTransfer.getDetails().add(existingDetail);

        when(subOrderRepo.findByIdWithItems(34L)).thenReturn(Optional.of(replenishmentSubOrder));
        when(transferRepo.findByReferenceCodeAndStatusInOrderByCreatedAtDesc(any(), any()))
                .thenReturn(List.of(existingTransfer));
        when(inventoryRepo.findByProductVariantId(variant.getId())).thenReturn(List.of(
                createInventory(warehouse, variant, 2)));
        when(transferRepo.countTotalTransfers()).thenReturn(12L);

        List<InventoryTransfer> transfers = callAs(
                requesterUser,
                () -> inventoryTransferService.createMainWarehouseReplenishmentTransferIfPossible(34L));

        assertThat(transfers).hasSize(2);
        InventoryTransfer warehouseTransfer = transfers.stream()
                .filter(transfer -> transfer.getFromBranch().getId().equals(warehouse.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(warehouseTransfer.getTotalQuantity()).isEqualTo(2);
    }

    @Test
    void resolveProcurementWarehouseForDestinationBranch_prefersSameRegionWarehouseBeforeFartherRegions() {
        Branch southWarehouse = Branch.builder()
                .branchCode("WH-SOUTH")
                .branchType("WAREHOUSE")
                .name("Kho Bac Lieu")
                .provinceName("Tỉnh Bạc Liêu")
                .lat(9.28)
                .lng(105.73)
                .status(BranchStatus.ACTIVE)
                .build();
        setId(southWarehouse, 70L, "id");

        Branch centralWarehouse = Branch.builder()
                .branchCode("WH-CENTRAL")
                .branchType("WAREHOUSE")
                .name("Kho Da Nang")
                .provinceName("Thành phố Đà Nẵng")
                .lat(16.05)
                .lng(108.20)
                .status(BranchStatus.ACTIVE)
                .build();
        setId(centralWarehouse, 71L, "id");

        Branch northWarehouse = Branch.builder()
                .branchCode("WH-NORTH")
                .branchType("WAREHOUSE")
                .name("Kho Ha Noi")
                .provinceName("Thành phố Hà Nội")
                .lat(21.03)
                .lng(105.85)
                .status(BranchStatus.ACTIVE)
                .build();
        setId(northWarehouse, 72L, "id");

        Branch servingStore = Branch.builder()
                .branchCode("CN-CM")
                .branchType("STORE")
                .name("Chi nhanh Ca Mau")
                .provinceName("Tỉnh Cà Mau")
                .lat(9.18)
                .lng(105.15)
                .status(BranchStatus.ACTIVE)
                .build();
        setId(servingStore, 73L, "id");

        when(branchRepo.findAll()).thenReturn(List.of(
                southWarehouse,
                centralWarehouse,
                northWarehouse,
                servingStore));

        Branch selectedWarehouse = inventoryTransferService.resolveProcurementWarehouseForDestinationBranch(servingStore);

        assertThat(selectedWarehouse).isEqualTo(southWarehouse);
    }

    @Test
    void stockTransfer_happyPath_canCreateApproveShipInspectAndReceive() {
        Inventory sourceBatch = Inventory.builder()
                .id(700L)
                .branch(warehouse)
                .productVariant(variant)
                .batchNumber("BATCH-WH-01")
                .importPrice(new BigDecimal("100"))
                .quantity(10)
                .reservedQuantity(0)
                .defectiveQuantity(0)
                .build();

        TransferRequest request = buildRequest(
                warehouse.getId(),
                destinationBranch.getId(),
                TransferBusinessType.STOCK_TRANSFER,
                4,
                null);

        when(transferRepo.countTotalTransfers()).thenReturn(10L);
        when(inventoryRepo.findByProductVariantId(variant.getId())).thenReturn(List.of(sourceBatch));

        InventoryTransfer transfer = callAs(requesterUser, () -> inventoryTransferService.createTransfer(request));
        when(transferRepo.findByIdWithDetails(transfer.getId())).thenReturn(Optional.of(transfer));
        when(inventoryRepo.findForUpdateFIFO(warehouse.getId(), variant.getId())).thenReturn(List.of(sourceBatch));
        when(inventoryRepo.findExactBatchWithLock(any(Branch.class), eq(variant), anyString(), any(BigDecimal.class)))
                .thenReturn(Optional.empty());

        assertThat(transfer.getStatus()).isEqualTo(InventoryTransferStatus.PENDING);
        assertThat(transfer.getCreatedBy()).isEqualTo(requesterUser);
        assertThat(transfer.getCreatedByBranch()).isEqualTo(destinationBranch);

        runAs(approverUser, () -> inventoryTransferService.approveTransfer(transfer.getId()));
        assertThat(transfer.getStatus()).isEqualTo(InventoryTransferStatus.APPROVED);
        assertThat(sourceBatch.getReservedQuantity()).isEqualTo(4);

        runAs(approverUser, () -> inventoryTransferService.approveAndShip(transfer.getId()));
        assertThat(transfer.getStatus()).isEqualTo(InventoryTransferStatus.SHIPPING);
        assertThat(sourceBatch.getQuantity()).isEqualTo(6);
        assertThat(sourceBatch.getReservedQuantity()).isZero();

        runAs(receiverUser, () -> inventoryTransferService.startInspection(transfer.getId()));
        assertThat(transfer.getStatus()).isEqualTo(InventoryTransferStatus.INSPECTING);

        runAs(receiverUser, () -> inventoryTransferService.receiveTransfer(
                transfer.getId(),
                List.of(TransferQCRequest.builder()
                        .variantId(variant.getId())
                        .quantityReal(4)
                        .quantityAccepted(4)
                        .quantityRejected(0)
                        .note("")
                        .build())));

        assertThat(transfer.getStatus()).isEqualTo(InventoryTransferStatus.COMPLETED);
        assertThat(transfer.getSettlementStatus()).isNull();
        assertThat(transfer.getTransferAmount()).isNull();
        assertThat(savedTransactions)
                .anyMatch(tx -> tx.getType() == TransactionType.TRANSFER_OUT
                        && tx.getReferenceCode().equals(transfer.getTransferCode())
                        && tx.getQuantityChange() == -4);
        assertThat(savedInventories)
                .anyMatch(inv -> inv.getBranch() == destinationBranch
                        && inv.getProductVariant() == variant
                        && inv.getQuantity() == 4);
    }

    @Test
    void internalSale_happyPath_completesAndSettlesBasedOnAcceptedQuantity() {
        Inventory sourceBatch = Inventory.builder()
                .id(701L)
                .branch(sourceBranch)
                .productVariant(variant)
                .batchNumber("BATCH-CN-01")
                .importPrice(new BigDecimal("100"))
                .quantity(5)
                .reservedQuantity(0)
                .defectiveQuantity(0)
                .build();

        TransferRequest request = buildRequest(
                sourceBranch.getId(),
                destinationBranch.getId(),
                TransferBusinessType.INTERNAL_SALE,
                5,
                new BigDecimal("120"));

        when(transferRepo.countTotalTransfers()).thenReturn(20L);
        when(inventoryRepo.findByProductVariantId(variant.getId())).thenReturn(List.of(sourceBatch));

        InventoryTransfer transfer = callAs(requesterUser, () -> inventoryTransferService.createTransfer(request));
        when(transferRepo.findByIdWithDetails(transfer.getId())).thenReturn(Optional.of(transfer));
        when(inventoryRepo.findForUpdateFIFO(sourceBranch.getId(), variant.getId())).thenReturn(List.of(sourceBatch));
        when(inventoryRepo.findExactBatchWithLock(any(Branch.class), eq(variant), anyString(), any(BigDecimal.class)))
                .thenReturn(Optional.empty());

        assertThat(transfer.getTransferBusinessType()).isEqualTo(TransferBusinessType.INTERNAL_SALE);
        assertThat(transfer.getTransferAmount()).isEqualByComparingTo("600");
        assertThat(transfer.getSettlementStatus()).isEqualTo(TransferSettlementStatus.UNPAID);

        runAs(sourceUser, () -> inventoryTransferService.sourceConfirm(transfer.getId()));
        assertThat(transfer.getStatus()).isEqualTo(InventoryTransferStatus.SOURCE_CONFIRMED);
        assertThat(transfer.getSourceConfirmedBy()).isEqualTo(sourceUser);
        assertThat(sourceBatch.getReservedQuantity()).isEqualTo(5);

        runAs(approverUser, () -> inventoryTransferService.approveTransfer(transfer.getId()));
        assertThat(transfer.getStatus()).isEqualTo(InventoryTransferStatus.APPROVED);
        assertThat(transfer.getApprovedBy()).isEqualTo(approverUser);

        runAs(approverUser, () -> inventoryTransferService.approveAndShip(transfer.getId()));
        assertThat(transfer.getStatus()).isEqualTo(InventoryTransferStatus.SHIPPING);
        assertThat(sourceBatch.getQuantity()).isZero();
        assertThat(sourceBatch.getReservedQuantity()).isZero();

        runAs(receiverUser, () -> inventoryTransferService.startInspection(transfer.getId()));
        assertThat(transfer.getStatus()).isEqualTo(InventoryTransferStatus.INSPECTING);

        runAs(receiverUser, () -> inventoryTransferService.receiveTransfer(
                transfer.getId(),
                List.of(TransferQCRequest.builder()
                        .variantId(variant.getId())
                        .quantityReal(5)
                        .quantityAccepted(3)
                        .quantityRejected(2)
                        .note("2 san pham loi")
                        .build())));

        assertThat(transfer.getStatus()).isEqualTo(InventoryTransferStatus.COMPLETED);
        assertThat(transfer.getTransferAmount()).isEqualByComparingTo("360");
        assertThat(transfer.getSourceReceivableAmount()).isEqualByComparingTo("360");
        assertThat(transfer.getDestPayableAmount()).isEqualByComparingTo("360");
        assertThat(transfer.getPaidAmount()).isEqualByComparingTo("0");
        assertThat(transfer.getSettlementStatus()).isEqualTo(TransferSettlementStatus.UNPAID);
        assertThat(savedInventories)
                .anyMatch(inv -> inv.getBranch() == destinationBranch
                        && inv.getProductVariant() == variant
                        && inv.getQuantity() == 3
                        && inv.getImportPrice().compareTo(new BigDecimal("120")) == 0);
        assertThat(savedInventories)
                .anyMatch(inv -> inv.getBranch() == defectBranch
                        && inv.getProductVariant() == variant
                        && inv.getDefectiveQuantity() == 2
                        && inv.getImportPrice().compareTo(new BigDecimal("120")) == 0);

        TransferSettlementRequest partialSettlement = new TransferSettlementRequest();
        partialSettlement.setAmount(new BigDecimal("120"));
        TransferDetailResponse partialResponse = callAs(
                approverUser,
                () -> inventoryTransferService.settleInternalPayment(transfer.getId(), partialSettlement));

        assertThat(partialResponse.getPaidAmount()).isEqualByComparingTo("120");
        assertThat(partialResponse.getOutstandingAmount()).isEqualByComparingTo("240");
        assertThat(partialResponse.getSettlementStatus()).isEqualTo(TransferSettlementStatus.PARTIAL);

        TransferSettlementRequest finalSettlement = new TransferSettlementRequest();
        finalSettlement.setAmount(new BigDecimal("240"));
        TransferDetailResponse finalResponse = callAs(
                approverUser,
                () -> inventoryTransferService.settleInternalPayment(transfer.getId(), finalSettlement));

        assertThat(finalResponse.getPaidAmount()).isEqualByComparingTo("360");
        assertThat(finalResponse.getOutstandingAmount()).isEqualByComparingTo("0");
        assertThat(finalResponse.getSettlementStatus()).isEqualTo(TransferSettlementStatus.PAID);
    }

    @Test
    void pendingTransfer_canUpdateAndCancelBeforeApproval() {
        Inventory sourceBatch = Inventory.builder()
                .id(702L)
                .branch(warehouse)
                .productVariant(variant)
                .batchNumber("BATCH-WH-02")
                .importPrice(new BigDecimal("90"))
                .quantity(20)
                .reservedQuantity(0)
                .defectiveQuantity(0)
                .build();

        TransferRequest createRequest = buildRequest(
                warehouse.getId(),
                destinationBranch.getId(),
                TransferBusinessType.STOCK_TRANSFER,
                3,
                null);

        when(transferRepo.countTotalTransfers()).thenReturn(30L);
        when(inventoryRepo.findByProductVariantId(variant.getId())).thenReturn(List.of(sourceBatch));

        InventoryTransfer transfer = callAs(requesterUser, () -> inventoryTransferService.createTransfer(createRequest));
        when(transferRepo.findByIdWithDetails(transfer.getId())).thenReturn(Optional.of(transfer));
        when(transferRepo.findById(transfer.getId())).thenReturn(Optional.of(transfer));

        TransferRequest updateRequest = buildRequest(
                warehouse.getId(),
                destinationBranch.getId(),
                TransferBusinessType.STOCK_TRANSFER,
                2,
                null);
        updateRequest.setDescription("Cap nhat lai so luong");

        TransferDetailResponse updated = callAs(
                requesterUser,
                () -> inventoryTransferService.updateTransfer(transfer.getId(), updateRequest));

        assertThat(updated.getStatus()).isEqualTo(InventoryTransferStatus.PENDING);
        assertThat(updated.getTotalQuantity()).isEqualTo(2);
        assertThat(updated.getDescription()).isEqualTo("Cap nhat lai so luong");

        inventoryTransferService.cancelTransfer(transfer.getId());
        assertThat(transfer.getStatus()).isEqualTo(InventoryTransferStatus.CANCELLED);
    }

    @Test
    void createTransfer_rejectsManualStockTransferIntoWarehouse() {
        requesterUser.setBranch(warehouse);
        defectBranch.setStatus(BranchStatus.ACTIVE);
        when(branchRepo.findById(defectBranch.getId())).thenReturn(Optional.of(defectBranch));

        TransferRequest request = buildRequest(
                warehouse.getId(),
                defectBranch.getId(),
                TransferBusinessType.STOCK_TRANSFER,
                1,
                null);

        assertThatThrownBy(() -> callAs(requesterUser, () -> inventoryTransferService.createTransfer(request)))
                .isInstanceOf(com.zone.agri.exception.BadRequestException.class);
    }

    private TransferRequest buildRequest(
            Long fromBranchId,
            Long toBranchId,
            TransferBusinessType businessType,
            int quantity,
            BigDecimal unitTransferPrice) {
        TransferItemRequest item = new TransferItemRequest();
        item.setSku(variant.getSku());
        item.setQuantity(quantity);
        item.setItemNote("Test item");
        item.setUnitTransferPrice(unitTransferPrice);

        TransferRequest request = new TransferRequest();
        request.setFromBranchId(fromBranchId);
        request.setToBranchId(toBranchId);
        request.setTransferType(businessType == TransferBusinessType.INTERNAL_SALE ? "INTERNAL" : "BETWEEN_WAREHOUSES");
        request.setTransferBusinessType(businessType.name());
        request.setDescription("Test transfer");
        request.setTransporter("Tai xe A");
        request.setVehicle("Xe tai");
        request.setDispatchOrder("LENH-001");
        request.setReferenceCode("REF-001");
        request.setPriority("NORMAL");
        request.setTransferDate(LocalDateTime.now());
        request.setDeadline(LocalDateTime.now().plusDays(1));
        request.setItems(List.of(item));
        return request;
    }

    private void runAs(User user, Runnable action) {
        callAs(user, () -> {
            action.run();
            return null;
        });
    }

    private <T> T callAs(User user, Supplier<T> supplier) {
        try (MockedStatic<AuthUtils> authUtilsMock = mockStatic(AuthUtils.class)) {
            authUtilsMock.when(AuthUtils::getUserDetail).thenReturn(UserDetail.builder()
                    .id(user.getId())
                    .fullName(user.getFullName())
                    .branchId(user.getBranch() != null ? user.getBranch().getId() : null)
                    .build());
            return supplier.get();
        }
    }

    private Inventory createInventory(Branch branch, ProductVariant productVariant, int quantity) {
        return createInventory(branch, productVariant, quantity, 0, null);
    }

    private Inventory createInventory(
            Branch branch,
            ProductVariant productVariant,
            int quantity,
            int reservedQuantity,
            Integer minStock) {
        return Inventory.builder()
                .branch(branch)
                .productVariant(productVariant)
                .quantity(quantity)
                .reservedQuantity(reservedQuantity)
                .minStock(minStock)
                .build();
    }

    @SuppressWarnings("SameParameterValue")
    private void setId(Object obj, Long id, String fieldName) {
        try {
            java.lang.reflect.Field f = getField(obj.getClass(), fieldName);
            f.setAccessible(true);
            f.set(obj, id);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set id via reflection: " + e.getMessage(), e);
        }
    }

    private java.lang.reflect.Field getField(Class<?> clazz, String name) throws NoSuchFieldException {
        try {
            return clazz.getDeclaredField(name);
        } catch (NoSuchFieldException e) {
            if (clazz.getSuperclass() != null) {
                return getField(clazz.getSuperclass(), name);
            }
            throw e;
        }
    }
}
