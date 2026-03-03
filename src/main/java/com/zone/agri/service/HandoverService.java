package com.zone.agri.service;

import com.zone.agri.dto.order.HandoverCreateRequest;
import com.zone.agri.dto.order.HandoverDetailResponse;
import com.zone.agri.dto.order.HandoverResponse;
import com.zone.agri.entity.*;
import com.zone.agri.entity.enums.OrderStatus;
import com.zone.agri.entity.enums.PaymentMethod;
import com.zone.agri.exception.BadRequestException;
import com.zone.agri.exception.NotFoundException;
import com.zone.agri.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HandoverService {

    private final HandoverRepository handoverRepository;
    private final SubOrderRepository subOrderRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final BranchRepository branchRepository;

    @Transactional
    public Handover createHandover(Long userId, Long branchId, HandoverCreateRequest request) {
        User creator = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy người dùng"));
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy chi nhánh"));

        if (request.getSubOrderIds() == null || request.getSubOrderIds().isEmpty()) {
            throw new BadRequestException("Vui lòng chọn ít nhất 1 đơn hàng để bàn giao");
        }

        List<SubOrder> subOrders = subOrderRepository.findAllById(request.getSubOrderIds());
        if (subOrders.size() != request.getSubOrderIds().size()) {
            throw new BadRequestException("Một số đơn hàng không tồn tại hoặc đã bị xóa");
        }

        BigDecimal totalCod = BigDecimal.ZERO;

        for (SubOrder subOrder : subOrders) {
            if (!subOrder.getBranch().getId().equals(branchId)) {
                throw new BadRequestException("Đơn hàng #" + subOrder.getId() + " không thuộc chi nhánh của bạn");
            }

            if (subOrder.getStatus() != OrderStatus.PROCESSING && subOrder.getStatus() != OrderStatus.PENDING) {
                throw new BadRequestException("Đơn hàng #" + subOrder.getId() + " chưa sẵn sàng để bàn giao");
            }

            if (subOrder.getOrder().getPaymentMethod() == PaymentMethod.COD) {
                BigDecimal subAmount = subOrder.getSubtotal().add(
                        subOrder.getShippingFee() != null ? subOrder.getShippingFee() : BigDecimal.ZERO
                );
                totalCod = totalCod.add(subAmount);
            }
        }

        String datePrefix = LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd"));
        long countToday = handoverRepository.countByBranchIdToday(branchId);
        String code = String.format("BG-%s-%03d", datePrefix, countToday + 1);

        Handover handover = Handover.builder()
                .code(code)
                .carrier(request.getCarrier())
                .totalOrders(subOrders.size())
                .totalWeight(request.getTotalWeight() != null ? request.getTotalWeight() : 0.0)
                .totalCod(totalCod)
                .status("WAITING")
                .branch(branch)
                .createdBy(creator)
                .createdAt(LocalDateTime.now())
                .build();

        Handover savedHandover = handoverRepository.save(handover);

        for (SubOrder subOrder : subOrders) {
            subOrder.setHandover(savedHandover);
            subOrder.setStatus(OrderStatus.SHIPPING);
            subOrderRepository.save(subOrder);

            syncMasterOrderStatus(subOrder.getOrder());
        }

        return savedHandover;
    }

    private void syncMasterOrderStatus(Order order) {
        List<SubOrder> allSubs = subOrderRepository.findByOrderId(order.getId());
        boolean allShippingOrHigher = allSubs.stream().allMatch(s ->
                s.getStatus() == OrderStatus.SHIPPING || s.getStatus() == OrderStatus.COMPLETED
        );

        if (allShippingOrHigher && order.getStatus() != OrderStatus.SHIPPING && order.getStatus() != OrderStatus.COMPLETED) {
            order.setStatus(OrderStatus.SHIPPING);
            orderRepository.save(order);
        }
    }

    public List<HandoverResponse> getHandoverList(Long branchId) {
        return handoverRepository.findByBranchIdOrderByCreatedAtDesc(branchId)
                .stream()
                .map(HandoverResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public HandoverDetailResponse getHandoverDetail(Long id) {
        Handover h = handoverRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy phiếu bàn giao ID: " + id));

        return HandoverDetailResponse.builder()
                .id(h.getId())
                .code(h.getCode())
                .carrier(h.getCarrier())
                .totalOrders(h.getTotalOrders())
                .totalWeight(h.getTotalWeight())
                .totalCod(h.getTotalCod())
                .status(h.getStatus())
                .createdAt(h.getCreatedAt())
                .creatorName(h.getCreatedBy() != null ? h.getCreatedBy().getFullName() : "N/A")
                .branchAddress(h.getBranch() != null ? h.getBranch().getAddressDetail() : "")
                .subOrders(h.getSubOrders().stream().map(sub ->
                        HandoverDetailResponse.SubOrderHandoverItem.builder()
                                .id(sub.getId())
                                .orderCode(sub.getOrder().getCode())
                                .customerName(sub.getOrder().getUser().getFullName())
                                .shippingAddress(sub.getOrder().getShippingAddress())
                                .trackingCode(sub.getTrackingCode())
                                .subtotal(sub.getSubtotal())
                                .paymentStatus(sub.getOrder().getPaymentStatus().name())
                                .build()
                ).collect(Collectors.toList()))
                .build();
    }
}