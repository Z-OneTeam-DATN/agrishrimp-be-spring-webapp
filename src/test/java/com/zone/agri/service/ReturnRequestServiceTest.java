package com.zone.agri.service;

import com.zone.agri.dto.request.returns.CreateReturnRequest;
import com.zone.agri.dto.request.returns.CreateReturnRequestEvidence;
import com.zone.agri.dto.request.returns.ReturnRequestRefundRequest;
import com.zone.agri.dto.response.returns.ReturnRequestResponse;
import com.zone.agri.entity.Order;
import com.zone.agri.entity.ReturnRequest;
import com.zone.agri.entity.User;
import com.zone.agri.entity.enums.OrderStatus;
import com.zone.agri.entity.enums.ReturnEvidenceType;
import com.zone.agri.entity.enums.ReturnHandlingOption;
import com.zone.agri.entity.enums.ReturnIssueType;
import com.zone.agri.entity.enums.ReturnRefundMethod;
import com.zone.agri.entity.enums.ReturnRequestStatus;
import com.zone.agri.exception.BadRequestException;
import com.zone.agri.repository.OrderItemRepository;
import com.zone.agri.repository.OrderRepository;
import com.zone.agri.repository.ReturnRequestEvidenceRepository;
import com.zone.agri.repository.ReturnRequestItemRepository;
import com.zone.agri.repository.ReturnRequestRepository;
import com.zone.agri.repository.SubOrderItemRepository;
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

    @InjectMocks
    private ReturnRequestService returnRequestService;

    @Test
    void createReturnRequest_shouldRejectCashRefundMethod() {
        User user = User.builder()
                .id(7L)
                .fullName("Tester")
                .build();
        Order order = Order.builder()
                .id(11L)
                .user(user)
                .status(OrderStatus.COMPLETED)
                .build();
        when(orderRepository.findById(11L)).thenReturn(Optional.of(order));

        CreateReturnRequest request = new CreateReturnRequest();
        request.setOrderId(11L);
        request.setFullName("Nguyen Van A");
        request.setPhoneNumber("0900000000");
        request.setBankAccountName("NGUYEN VAN A");
        request.setBankAccountNumber("123456789");
        request.setBankName("VCB");
        request.setIssueType(ReturnIssueType.DAMAGED);
        request.setRefundMethod(ReturnRefundMethod.CASH);
        request.setReason("San pham bi hu");
        request.setDescription("Mo ta loi");
        request.setItems(List.of());
        request.setEvidences(List.of());

        assertThatThrownBy(() -> returnRequestService.createReturnRequest(7L, request))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void refundForAdmin_shouldRejectCashRefundMethod() {
        ReturnRequest entity = ReturnRequest.builder()
                .id(55L)
                .status(ReturnRequestStatus.APPROVED)
                .issueType(ReturnIssueType.DAMAGED)
                .refundMethod(ReturnRefundMethod.BANK_TRANSFER)
                .requiresPhysicalReturn(false)
                .customerName("Nguyen Van A")
                .customerPhone("0900000000")
                .bankAccountName("NGUYEN VAN A")
                .bankAccountNumber("123456789")
                .bankName("VCB")
                .reason("Ly do")
                .description("Mo ta")
                .totalRefundAmount(BigDecimal.ZERO)
                .build();
        when(returnRequestRepository.findDetailedById(55L)).thenReturn(Optional.of(entity));

        ReturnRequestRefundRequest request = new ReturnRequestRefundRequest();
        request.setRefundAmount(new BigDecimal("100000"));
        request.setRefundMethod(ReturnRefundMethod.CASH);

        assertThatThrownBy(() -> returnRequestService.refundForAdmin(55L, request))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void refundForAdmin_shouldNormalizeLegacyCashRefundMethodToBankTransfer() {
        Order order = Order.builder()
                .id(21L)
                .code("ORD-21")
                .build();
        ReturnRequest entity = ReturnRequest.builder()
                .id(56L)
                .code("RET-56")
                .status(ReturnRequestStatus.APPROVED)
                .issueType(ReturnIssueType.DAMAGED)
                .refundMethod(ReturnRefundMethod.CASH)
                .requiresPhysicalReturn(false)
                .customerName("Nguyen Van A")
                .customerPhone("0900000000")
                .bankAccountName("NGUYEN VAN A")
                .bankAccountNumber("123456789")
                .bankName("VCB")
                .reason("Ly do")
                .description("Mo ta")
                .totalRefundAmount(BigDecimal.ZERO)
                .order(order)
                .build();
        when(returnRequestRepository.findDetailedById(56L)).thenReturn(Optional.of(entity));

        ReturnRequestRefundRequest request = new ReturnRequestRefundRequest();
        request.setRefundAmount(new BigDecimal("125000"));
        request.setInternalNote("Da chuyen khoan");

        ReturnRequestResponse response = returnRequestService.refundForAdmin(56L, request);

        assertThat(entity.getRefundMethod()).isEqualTo(ReturnRefundMethod.BANK_TRANSFER);
        assertThat(entity.getStatus()).isEqualTo(ReturnRequestStatus.REFUNDED);
        assertThat(entity.getRefundedAt()).isNotNull();
        assertThat(response.getRefundMethod()).isEqualTo(ReturnRefundMethod.BANK_TRANSFER);
        assertThat(response.getHandlingOption()).isEqualTo(ReturnHandlingOption.REFUND_ONLY);
        assertThat(response.getTotalRefundAmount()).isEqualByComparingTo("125000");
    }

    @Test
    void createReturnRequest_shouldRejectMissingItemWithReturnAndRefundHandling() {
        User user = User.builder()
                .id(7L)
                .fullName("Tester")
                .build();
        Order order = Order.builder()
                .id(11L)
                .user(user)
                .status(OrderStatus.COMPLETED)
                .build();
        when(orderRepository.findById(11L)).thenReturn(Optional.of(order));

        CreateReturnRequest request = new CreateReturnRequest();
        request.setOrderId(11L);
        request.setFullName("Nguyen Van A");
        request.setPhoneNumber("0900000000");
        request.setBankAccountName("NGUYEN VAN A");
        request.setBankAccountNumber("123456789");
        request.setBankName("VCB");
        request.setIssueType(ReturnIssueType.MISSING_ITEM);
        request.setHandlingOption(ReturnHandlingOption.RETURN_AND_REFUND);
        request.setRefundMethod(ReturnRefundMethod.BANK_TRANSFER);
        request.setReason("Thieu hang");
        request.setDescription("Mo ta loi");
        request.setItems(List.of());
        CreateReturnRequestEvidence imageEvidence = new CreateReturnRequestEvidence();
        imageEvidence.setMediaType(ReturnEvidenceType.IMAGE);
        imageEvidence.setFileUrl("https://example.com/image.jpg");
        CreateReturnRequestEvidence videoEvidence = new CreateReturnRequestEvidence();
        videoEvidence.setMediaType(ReturnEvidenceType.VIDEO);
        videoEvidence.setFileUrl("https://example.com/video.mp4");
        request.setEvidences(List.of(imageEvidence, videoEvidence));

        assertThatThrownBy(() -> returnRequestService.createReturnRequest(7L, request))
                .isInstanceOf(BadRequestException.class);
    }
}
