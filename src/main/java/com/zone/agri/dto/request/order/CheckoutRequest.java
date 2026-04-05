package com.zone.agri.dto.request.order;

import com.zone.agri.entity.enums.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

/**
 * @deprecated Lớp này đại diện cho một luồng đặt hàng cũ (legacy), không còn được khuyến khích sử dụng.
 * Luồng này yêu cầu client phải chỉ định một chi nhánh cụ thể và gửi thông tin địa chỉ dạng thô,
 * điều này không tận dụng được logic phân bổ chi nhánh thông minh của hệ thống.
 * <p>
 * Thay vào đó, hãy sử dụng quy trình 2 bước mới:
 * 1. {@link PrepareOrderRequest} - Gửi yêu cầu đến endpoint {@code /api/orders/prepare}.
 * 2. {@link ConfirmOrderRequest} - Gửi yêu cầu đến endpoint {@code /api/orders/confirm}.
 */
@Data
@Deprecated
public class CheckoutRequest {
    /**
     * Địa chỉ giao hàng dạng thô.
     * @deprecated Nên sử dụng userAddressId từ sổ địa chỉ để đảm bảo tính nhất quán.
     */
    @NotBlank(message = "Địa chỉ nhận hàng không được để trống")
    private String shippingAddress;

    /**
     * Số điện thoại người nhận.
     */
    @NotBlank(message = "Số điện thoại không được để trống")
    private String phone;

    /**
     * Tên đầy đủ của người nhận.
     */
    @NotBlank(message = "Họ tên người nhận không được để trống")
    private String fullName;

    private String note;

    private String voucherCode;

    private PaymentMethod paymentMethod;

    /**
     * ID của chi nhánh được chọn thủ công.
     * @deprecated Hệ thống mới sẽ tự động tìm chi nhánh tối ưu.
     */
    @NotNull(message = "Chi nhánh không được để trống")
    private Long branchId;

    @NotEmpty(message = "Đơn hàng phải có ít nhất 1 sản phẩm")
    @Valid // Thêm validation cho các đối tượng bên trong list
    private List<CheckoutItemRequest> items;
}