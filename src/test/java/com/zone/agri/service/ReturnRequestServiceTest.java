package com.zone.agri.service;

import com.zone.agri.dto.request.returns.CreateReturnRequest;
import com.zone.agri.dto.request.returns.CreateReturnRequestEvidence;
import com.zone.agri.dto.request.returns.CreateReturnRequestItem;
import com.zone.agri.dto.request.returns.ReturnRequestReceiveItemRequest;
import com.zone.agri.dto.request.returns.ReturnRequestReceiveRequest;
import com.zone.agri.dto.request.returns.ReturnRequestRefundRequest;
import com.zone.agri.dto.response.returns.ReturnOrderDraftResponse;
import com.zone.agri.dto.response.returns.ReturnRequestResponse;
import com.zone.agri.entity.Branch;
import com.zone.agri.entity.Inventory;
import com.zone.agri.entity.InventoryNote;
import com.zone.agri.entity.InventoryTransaction;
import com.zone.agri.entity.Order;
import com.zone.agri.entity.OrderItem;
import com.zone.agri.entity.Product;
import com.zone.agri.entity.ProductVariant;
import com.zone.agri.entity.ReturnRequest;
import com.zone.agri.entity.ReturnRequestItem;
import com.zone.agri.entity.User;
import com.zone.agri.entity.enums.InventoryNoteStatus;
import com.zone.agri.entity.enums.InventoryNoteType;
import com.zone.agri.entity.enums.OrderStatus;
import com.zone.agri.entity.enums.ReturnEvidenceType;
import com.zone.agri.entity.enums.ReturnHandlingOption;
import com.zone.agri.entity.enums.ReturnIssueType;
import com.zone.agri.entity.enums.ReturnItemSourceType;
import com.zone.agri.entity.enums.ReturnRefundMethod;
import com.zone.agri.entity.enums.ReturnRequestStatus;
import com.zone.agri.entity.enums.TransactionType;
import com.zone.agri.exception.BadRequestException;
import com.zone.agri.repository.InventoryNoteDetailRepository;
import com.zone.agri.repository.InventoryNoteRepository;
import com.zone.agri.repository.InventoryRepository;
import com.zone.agri.repository.InventoryTransactionRepository;
import com.zone.agri.repository.OrderItemRepository;
import com.zone.agri.repository.OrderRepository;
import com.zone.agri.repository.ProductVariantRepository;
import com.zone.agri.repository.ReturnRequestEvidenceRepository;
import com.zone.agri.repository.ReturnRequestItemRepository;
import com.zone.agri.repository.ReturnRequestRepository;
import com.zone.agri.repository.SubOrderItemRepository;
import com.zone.agri.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReturnRequestServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private ReturnRequestRepository returnRequestRepository;

    @Mock
    private ReturnRequestItemRepository returnRequestItemRepository;

    @Mock
    private ReturnRequestEvidenceRepository returnRequestEvidenceRepository;

    @Mock
    private SubOrderItemRepository subOrderItemRepository;

    @Mock
    private ProductVariantRepository productVariantRepository;

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private InventoryNoteRepository inventoryNoteRepository;

    @Mock
    private InventoryNoteDetailRepository inventoryNoteDetailRepository;

    @Mock
    private InventoryTransactionRepository inventoryTransactionRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ReturnRequestService returnRequestService;

    @Test
    void getReturnDraft_shouldExposeCashRefundMethodWhenOrderIsNearServingBranch() {
        Order order = buildCompletedOrder(10.7627, 106.6602, 10.7681, 106.6665);
        when(orderRepository.findById(11L)).thenReturn(Optional.of(order));

        ReturnOrderDraftResponse response = returnRequestService.getReturnDraft(7L, 11L);

        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getAllowedRefundMethods())
                .containsExactly(ReturnRefundMethod.BANK_TRANSFER, ReturnRefundMethod.CASH);
        assertThat(response.getItems().get(0).getCashRefundEligible()).isTrue();
        assertThat(response.getItems().get(0).getCashRefundDistanceKm()).isNotNull();
        assertThat(response.getItems().get(0).getCashRefundDistanceKm()).isLessThanOrEqualTo(15d);
    }

    @Test
    void getReturnDraft_shouldLimitToBankTransferWhenOrderIsFarFromServingBranch() {
        Order order = buildCompletedOrder(10.7627, 106.6602, 21.0285, 105.8542);
        when(orderRepository.findById(11L)).thenReturn(Optional.of(order));

        ReturnOrderDraftResponse response = returnRequestService.getReturnDraft(7L, 11L);

        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getAllowedRefundMethods())
                .containsExactly(ReturnRefundMethod.BANK_TRANSFER);
        assertThat(response.getItems().get(0).getCashRefundEligible()).isFalse();
        assertThat(response.getItems().get(0).getCashRefundDistanceKm()).isGreaterThan(15d);
    }

    @Test
    void createReturnRequest_shouldAllowCashRefundMethodWhenNearServingBranch() {
        Order order = buildCompletedOrder(10.7627, 106.6602, 10.7681, 106.6665);
        OrderItem orderItem = order.getOrderItems().get(0);

        when(orderRepository.findById(11L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findAllById(List.of(orderItem.getId()))).thenReturn(List.of(orderItem));
        when(returnRequestRepository.save(any(ReturnRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ReturnRequestResponse response = returnRequestService.createReturnRequest(
                7L,
                buildCreateRequest(ReturnRefundMethod.CASH, order.getId(), orderItem.getId(), true)
        );

        assertThat(response.getRefundMethod()).isEqualTo(ReturnRefundMethod.CASH);
        assertThat(response.getBankAccountName()).isNull();
        assertThat(response.getBankAccountNumber()).isNull();
        assertThat(response.getBankName()).isNull();
        assertThat(response.getHandlingOption()).isEqualTo(ReturnHandlingOption.RETURN_AND_REFUND);
    }

    @Test
    void createReturnRequest_shouldRejectCashRefundMethodWhenOrderIsFarFromServingBranch() {
        Order order = buildCompletedOrder(10.7627, 106.6602, 21.0285, 105.8542);
        OrderItem orderItem = order.getOrderItems().get(0);

        when(orderRepository.findById(11L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findAllById(List.of(orderItem.getId()))).thenReturn(List.of(orderItem));

        assertThatThrownBy(() -> returnRequestService.createReturnRequest(
                7L,
                buildCreateRequest(ReturnRefundMethod.CASH, order.getId(), orderItem.getId(), true)
        )).isInstanceOf(BadRequestException.class);
    }

    @Test
    void createReturnRequest_shouldRequireBankInfoForBankTransfer() {
        Order order = buildCompletedOrder(10.7627, 106.6602, 10.7681, 106.6665);
        OrderItem orderItem = order.getOrderItems().get(0);

        when(orderRepository.findById(11L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findAllById(List.of(orderItem.getId()))).thenReturn(List.of(orderItem));

        CreateReturnRequest request = buildCreateRequest(
                ReturnRefundMethod.BANK_TRANSFER,
                order.getId(),
                orderItem.getId(),
                false
        );
        request.setBankName(" ");

        assertThatThrownBy(() -> returnRequestService.createReturnRequest(7L, request))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void refundForAdmin_shouldKeepCashRefundMethodWhenRequestWasCreatedForCash() {
        Order order = buildCompletedOrder(10.7627, 106.6602, 10.7681, 106.6665);
        Branch branch = order.getBranch();

        ReturnRequest entity = ReturnRequest.builder()
                .id(55L)
                .code("RET-55")
                .status(ReturnRequestStatus.APPROVED)
                .issueType(ReturnIssueType.DAMAGED)
                .refundMethod(ReturnRefundMethod.CASH)
                .requiresPhysicalReturn(false)
                .customerName("Nguyen Van A")
                .customerPhone("0900000000")
                .reason("Ly do")
                .description("Mo ta")
                .totalRefundAmount(BigDecimal.ZERO)
                .order(order)
                .branch(branch)
                .build();
        when(returnRequestRepository.findDetailedById(55L)).thenReturn(Optional.of(entity));

        ReturnRequestRefundRequest request = new ReturnRequestRefundRequest();
        request.setRefundAmount(new BigDecimal("125000"));
        request.setRefundMethod(ReturnRefundMethod.CASH);
        request.setInternalNote("Da hoan tien mat");

        ReturnRequestResponse response = returnRequestService.refundForAdmin(55L, request);

        assertThat(entity.getRefundMethod()).isEqualTo(ReturnRefundMethod.CASH);
        assertThat(entity.getStatus()).isEqualTo(ReturnRequestStatus.REFUNDED);
        assertThat(entity.getRefundedAt()).isNotNull();
        assertThat(response.getRefundMethod()).isEqualTo(ReturnRefundMethod.CASH);
        assertThat(response.getTotalRefundAmount()).isEqualByComparingTo("125000");
    }

    @Test
    void createReturnRequest_shouldRejectMissingItemWithReturnAndRefundHandling() {
        Order order = buildCompletedOrder(10.7627, 106.6602, 10.7681, 106.6665);
        OrderItem orderItem = order.getOrderItems().get(0);

        when(orderRepository.findById(11L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findAllById(List.of(orderItem.getId()))).thenReturn(List.of(orderItem));

        CreateReturnRequest request = buildCreateRequest(
                ReturnRefundMethod.BANK_TRANSFER,
                order.getId(),
                orderItem.getId(),
                true
        );
        request.setIssueType(ReturnIssueType.MISSING_ITEM);
        request.setHandlingOption(ReturnHandlingOption.RETURN_AND_REFUND);

        assertThatThrownBy(() -> returnRequestService.createReturnRequest(7L, request))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void receiveForAdmin_shouldImportReturnedGoodsIntoSaleableAndDefectiveStock() {
        Order order = buildCompletedOrder(10.7627, 106.6602, 10.7681, 106.6665);
        Branch branch = order.getBranch();
        ProductVariant variant = order.getOrderItems().get(0).getProductVariant();

        ReturnRequestItem returnItem = ReturnRequestItem.builder()
                .id(801L)
                .sourceType(ReturnItemSourceType.ORDER_ITEM)
                .sourceItemId(order.getOrderItems().get(0).getId())
                .productVariantId(variant.getId())
                .productName("Men vi sinh")
                .variantName("Gói 1kg")
                .sku(variant.getSku())
                .quantity(2)
                .orderedQuantity(2)
                .unitPrice(new BigDecimal("125000"))
                .refundAmount(new BigDecimal("250000"))
                .restockQuantity(0)
                .defectiveQuantity(0)
                .build();

        ReturnRequest entity = ReturnRequest.builder()
                .id(55L)
                .code("RET-55")
                .status(ReturnRequestStatus.APPROVED)
                .issueType(ReturnIssueType.DAMAGED)
                .refundMethod(ReturnRefundMethod.BANK_TRANSFER)
                .requiresPhysicalReturn(true)
                .customerName("Nguyen Van A")
                .customerPhone("0900000000")
                .reason("Ly do")
                .description("Mo ta")
                .totalRefundAmount(new BigDecimal("250000"))
                .order(order)
                .branch(branch)
                .items(new java.util.LinkedHashSet<>(List.of(returnItem)))
                .build();
        returnItem.setReturnRequest(entity);

        ReturnRequestReceiveRequest request = new ReturnRequestReceiveRequest();
        request.setInternalNote("Nhận hàng trả và nhập kho");
        ReturnRequestReceiveItemRequest receiveItem = new ReturnRequestReceiveItemRequest();
        receiveItem.setReturnRequestItemId(returnItem.getId());
        receiveItem.setRestockQuantity(1);
        receiveItem.setDefectiveQuantity(1);
        receiveItem.setItemNote("1 cái còn tốt, 1 cái lỗi");
        request.setItems(List.of(receiveItem));

        when(returnRequestRepository.findDetailedById(55L)).thenReturn(Optional.of(entity));
        when(productVariantRepository.findAllById(List.of(variant.getId()))).thenReturn(List.of(variant));
        when(inventoryTransactionRepository.findByReferenceCodeAndType(order.getCode(), TransactionType.SALE))
                .thenReturn(List.of());
        when(inventoryRepository.findExactBatchWithLock(
                any(Branch.class),
                any(ProductVariant.class),
                any(),
                any(BigDecimal.class)))
                .thenReturn(Optional.empty());
        when(inventoryRepository.save(any(Inventory.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(inventoryNoteRepository.save(any(InventoryNote.class))).thenAnswer(invocation -> {
            InventoryNote note = invocation.getArgument(0);
            if (note.getId() == null) {
                note.setId(901L);
            }
            if (note.getStatus() == null) {
                note.setStatus(InventoryNoteStatus.COMPLETED);
            }
            if (note.getType() == null) {
                note.setType(InventoryNoteType.IMPORT);
            }
            return note;
        });

        ReturnRequestResponse response = returnRequestService.receiveForAdmin(55L, request);

        assertThat(response.getStatus()).isEqualTo(ReturnRequestStatus.RECEIVED);
        assertThat(response.getReceivedAt()).isNotNull();
        assertThat(response.getReceivedInventoryNoteId()).isEqualTo(901L);
        assertThat(response.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getRestockQuantity()).isEqualTo(1);
            assertThat(item.getDefectiveQuantity()).isEqualTo(1);
        });
        assertThat(entity.getReceivedInventoryNote()).isNotNull();
        assertThat(entity.getReceivedInventoryNote().getId()).isEqualTo(901L);
        assertThat(returnItem.getRestockQuantity()).isEqualTo(1);
        assertThat(returnItem.getDefectiveQuantity()).isEqualTo(1);

        verify(inventoryNoteDetailRepository).saveAll(any());
        verify(inventoryTransactionRepository, times(2)).save(any(InventoryTransaction.class));
    }

    @Test
    void receiveForAdmin_shouldRejectReceiveBreakdownWhenQuantitiesDoNotMatchReturnedQuantity() {
        Order order = buildCompletedOrder(10.7627, 106.6602, 10.7681, 106.6665);
        Branch branch = order.getBranch();
        ProductVariant variant = order.getOrderItems().get(0).getProductVariant();

        ReturnRequestItem returnItem = ReturnRequestItem.builder()
                .id(802L)
                .sourceType(ReturnItemSourceType.ORDER_ITEM)
                .sourceItemId(order.getOrderItems().get(0).getId())
                .productVariantId(variant.getId())
                .productName("Men vi sinh")
                .quantity(2)
                .orderedQuantity(2)
                .unitPrice(new BigDecimal("125000"))
                .refundAmount(new BigDecimal("250000"))
                .restockQuantity(0)
                .defectiveQuantity(0)
                .build();

        ReturnRequest entity = ReturnRequest.builder()
                .id(56L)
                .code("RET-56")
                .status(ReturnRequestStatus.APPROVED)
                .issueType(ReturnIssueType.DAMAGED)
                .refundMethod(ReturnRefundMethod.BANK_TRANSFER)
                .requiresPhysicalReturn(true)
                .customerName("Nguyen Van A")
                .customerPhone("0900000000")
                .reason("Ly do")
                .description("Mo ta")
                .totalRefundAmount(new BigDecimal("250000"))
                .order(order)
                .branch(branch)
                .items(new java.util.LinkedHashSet<>(List.of(returnItem)))
                .build();
        returnItem.setReturnRequest(entity);

        ReturnRequestReceiveRequest request = new ReturnRequestReceiveRequest();
        ReturnRequestReceiveItemRequest receiveItem = new ReturnRequestReceiveItemRequest();
        receiveItem.setReturnRequestItemId(returnItem.getId());
        receiveItem.setRestockQuantity(1);
        receiveItem.setDefectiveQuantity(0);
        request.setItems(List.of(receiveItem));

        when(returnRequestRepository.findDetailedById(56L)).thenReturn(Optional.of(entity));
        when(productVariantRepository.findAllById(List.of(variant.getId()))).thenReturn(List.of(variant));

        assertThatThrownBy(() -> returnRequestService.receiveForAdmin(56L, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("tổng số lượng");

        verify(inventoryNoteRepository, never()).save(any(InventoryNote.class));
        verify(inventoryRepository, never()).save(any(Inventory.class));
    }

    private Order buildCompletedOrder(
            double branchLat,
            double branchLng,
            double userLat,
            double userLng
    ) {
        User user = User.builder()
                .id(7L)
                .fullName("Tester")
                .phoneNumber("0900000000")
                .build();
        Branch branch = Branch.builder()
                .id(3L)
                .name("Chi Nhanh Gan")
                .lat(branchLat)
                .lng(branchLng)
                .build();
        Product product = Product.builder()
                .id(21L)
                .name("Men vi sinh")
                .build();
        ProductVariant variant = ProductVariant.builder()
                .id(31L)
                .sku("SKU-31")
                .customSpecs("Goi 1kg")
                .product(product)
                .build();
        Order order = Order.builder()
                .id(11L)
                .code("ORD-11")
                .user(user)
                .status(OrderStatus.COMPLETED)
                .branch(branch)
                .userLat(userLat)
                .userLng(userLng)
                .build();
        OrderItem orderItem = OrderItem.builder()
                .id(101L)
                .quantity(2)
                .price(new BigDecimal("125000"))
                .order(order)
                .productVariant(variant)
                .build();
        order.setOrderItems(List.of(orderItem));
        return order;
    }

    private CreateReturnRequest buildCreateRequest(
            ReturnRefundMethod refundMethod,
            Long orderId,
            Long orderItemId,
            boolean includeBankInfo
    ) {
        CreateReturnRequest request = new CreateReturnRequest();
        request.setOrderId(orderId);
        request.setFullName("Nguyen Van A");
        request.setPhoneNumber("0900000000");
        request.setEmail("test@example.com");
        request.setIssueType(ReturnIssueType.DAMAGED);
        request.setHandlingOption(ReturnHandlingOption.RETURN_AND_REFUND);
        request.setRefundMethod(refundMethod);
        request.setReason("San pham bi hu");
        request.setDescription("Mo ta loi");
        request.setItems(List.of(buildRequestItem(orderItemId)));
        request.setEvidences(List.of(buildEvidence(ReturnEvidenceType.IMAGE), buildEvidence(ReturnEvidenceType.VIDEO)));

        if (includeBankInfo) {
            request.setBankAccountName("NGUYEN VAN A");
            request.setBankAccountNumber("123456789");
            request.setBankName("VCB");
            request.setBankBranch("Can Tho");
        }

        return request;
    }

    private CreateReturnRequestItem buildRequestItem(Long orderItemId) {
        CreateReturnRequestItem item = new CreateReturnRequestItem();
        item.setSourceType(ReturnItemSourceType.ORDER_ITEM);
        item.setSourceItemId(orderItemId);
        item.setQuantity(1);
        return item;
    }

    private CreateReturnRequestEvidence buildEvidence(ReturnEvidenceType mediaType) {
        CreateReturnRequestEvidence evidence = new CreateReturnRequestEvidence();
        evidence.setMediaType(mediaType);
        evidence.setFileUrl(
                mediaType == ReturnEvidenceType.IMAGE
                        ? "https://example.com/image.jpg"
                        : "https://example.com/video.mp4"
        );
        return evidence;
    }
}
