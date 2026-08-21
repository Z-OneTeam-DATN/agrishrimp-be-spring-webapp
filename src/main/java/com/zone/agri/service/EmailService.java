package com.zone.agri.service;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import com.zone.agri.entity.BlogPost;
import com.zone.agri.entity.Order;
import com.zone.agri.entity.OrderItem;
import com.zone.agri.entity.Product;
import com.zone.agri.entity.ProductImage;
import com.zone.agri.entity.ProductVariant;
import com.zone.agri.entity.PurchaseRequest;
import com.zone.agri.entity.Voucher;
import com.zone.agri.entity.enums.OrderStatus;
import com.zone.agri.entity.enums.PaymentMethod;
import com.zone.agri.entity.enums.PaymentStatus;
import com.zone.agri.exception.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
@Slf4j
public class EmailService {

    private static final String LOGIN_URL = "https://agrishrimp.io.vn/login";
    private static final String LOGO_URL = "https://res.cloudinary.com/demevvyp4/image/upload/v1783706092/logo_arishrimp.jpg";

    @Value("${resend.api-key:}")
    private String resendApiKey;

    @Value("${resend.from-email:onboarding@resend.dev}")
    private String fromEmail;

    @Value("${resend.from-name:AgriShrimp}")
    private String fromName;

    @Value("${app.web-base-url:https://agrishrimp.io.vn}")
    private String webBaseUrl;

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    public void sendAccountInfo(String toEmail, String name, String password) {
        String subject = "[AgriShrimp] 🎉 Tài khoản của bạn đã sẵn sàng";
        String body = """
                <p style="font-size:16px;color:#374151;line-height:1.8;">
                    Xin chào <strong>%s</strong> 👋
                </p>
                <p style="font-size:15px;color:#374151;line-height:1.8;">
                    Tài khoản <strong>AgriShrimp</strong> của bạn đã được tạo thành công.
                    Dưới đây là thông tin để đăng nhập lần đầu:
                </p>

                <div style="background:#f0f9ff;border-left:4px solid #1e40af;border-radius:8px;padding:16px 20px;margin:20px 0;">
                    <p style="margin:6px 0;font-size:14px;color:#374151;">
                        <strong>Email đăng nhập:</strong> %s
                    </p>
                    <p style="margin:6px 0;font-size:14px;color:#374151;">
                        <strong>Mật khẩu tạm thời:</strong>
                        <span style="font-family:monospace;background:#dbeafe;color:#1e40af;padding:3px 10px;border-radius:4px;font-weight:bold;font-size:15px;">%s</span>
                    </p>
                </div>

                <p style="font-size:14px;color:#6b7280;line-height:1.7;">
                    ⚠️ Vui lòng <strong>đổi mật khẩu ngay</strong> sau khi đăng nhập lần đầu để bảo mật tài khoản.
                </p>
                """.formatted(name, toEmail, password);

        String cta = "Đăng nhập ngay →";
        String htmlContent = buildEmailTemplate("Tài Khoản Đăng Nhập Hệ Thống", body, cta);
        sendEmail(toEmail, subject, htmlContent);
    }

    public void sendWelcomeEmail(String toEmail, String name) {
        String subject = "[AgriShrimp] Chào mừng bạn đến với AgriShrimp";
        String body = """
                <p style="font-size:16px;color:#374151;line-height:1.8;">
                    Xin chào <strong>%s</strong>,
                </p>
                <p style="font-size:15px;color:#374151;line-height:1.8;">
                    Cảm ơn bạn đã đăng ký tài khoản <strong>AgriShrimp</strong>. Tài khoản của bạn
                    đã được tạo thành công và có thể sử dụng ngay.
                </p>

                <div style="background:#f0f9ff;border-left:4px solid #1e40af;border-radius:8px;padding:14px 18px;margin:16px 0;">
                    <p style="margin:0;font-size:14px;color:#1e40af;line-height:1.7;">
                        Bạn có thể đăng nhập để theo dõi đơn hàng, lưu địa chỉ nhận hàng và khám phá các dịch vụ hỗ trợ nuôi tôm thông minh.
                    </p>
                </div>
                """.formatted(name);

        String htmlContent = buildEmailTemplate("Chào Mừng Đến Với AgriShrimp", body, "Khám phá ngay →");
        sendEmail(toEmail, subject, htmlContent);
    }

    public void sendWarningEmail(String toEmail, String name, double reputationScore) {
        String subject = "[AgriShrimp] ⚠️ Cảnh báo: Tỉ lệ nhận hàng thấp";
        String body = """
                <p style="font-size:16px;color:#374151;line-height:1.8;">
                    Xin chào <strong>%s</strong>,
                </p>
                <p style="font-size:15px;color:#374151;line-height:1.8;">
                    Hệ thống <strong>AgriShrimp</strong> ghi nhận tỉ lệ nhận hàng thành công của bạn
                    hiện đang ở mức:
                </p>

                <div style="text-align:center;margin:24px 0;">
                    <span style="font-size:48px;font-weight:bold;color:#f59e0b;">%s%%</span>
                    <p style="font-size:13px;color:#9ca3af;margin-top:4px;">Tỉ lệ nhận hàng hiện tại</p>
                </div>

                <div style="background:#fffbeb;border-left:4px solid #f59e0b;border-radius:8px;padding:14px 18px;margin:16px 0;">
                    <p style="margin:0;font-size:14px;color:#92400e;line-height:1.7;">
                        ⚠️ Nếu tỉ lệ này xuống dưới <strong>30%%</strong>, hệ thống sẽ
                        <strong>tự động tạm khóa tài khoản</strong> để bảo vệ quyền lợi cho
                        các đối tác vận chuyển và nhà bán hàng.
                    </p>
                </div>

                <p style="font-size:14px;color:#374151;line-height:1.7;">
                    Bạn vui lòng đăng nhập và chú ý nhận hàng đúng hẹn cho các đơn hàng sắp tới nhé!
                </p>
                """.formatted(name, String.format("%.1f", reputationScore));

        String cta = "Xem đơn hàng của tôi →";
        String htmlContent = buildEmailTemplate("Cảnh Báo Tỉ Lệ Nhận Hàng", body, cta);
        sendEmail(toEmail, subject, htmlContent);
    }

    public void sendAccountLockedEmail(String toEmail, String name, double reputationScore) {
        String subject = "[AgriShrimp] 🔒 Tài khoản của bạn đã bị tạm khóa";
        String body = """
                <p style="font-size:16px;color:#374151;line-height:1.8;">
                    Xin chào <strong>%s</strong>,
                </p>
                <p style="font-size:15px;color:#374151;line-height:1.8;">
                    Chúng tôi rất tiếc phải thông báo rằng tài khoản của bạn đã bị
                    <strong style="color:#dc2626;">tạm khóa tự động</strong>.
                </p>

                <div style="background:#fef2f2;border-left:4px solid #dc2626;border-radius:8px;padding:14px 18px;margin:16px 0;">
                    <p style="margin:6px 0;font-size:14px;color:#991b1b;line-height:1.7;">
                        🔴 <strong>Lý do:</strong> Tỉ lệ nhận hàng của bạn đã giảm xuống
                        <strong>%s%%</strong> — thấp hơn mức tối thiểu cho phép (<strong>30%%</strong>).
                    </p>
                </div>

                <p style="font-size:14px;color:#374151;line-height:1.7;">
                    Trong thời gian bị khóa, bạn sẽ không thể đặt đơn hàng mới.
                    Nếu bạn cho rằng đây là nhầm lẫn hoặc cần hỗ trợ mở khóa,
                    vui lòng liên hệ bộ phận Chăm sóc khách hàng.
                </p>
                """.formatted(name, String.format("%.1f", reputationScore));

        String cta = "Liên hệ hỗ trợ →";
        String htmlContent = buildEmailTemplate("Thông Báo Khóa Tài Khoản", body, cta);
        sendEmail(toEmail, subject, htmlContent);
    }

    public void sendPurchaseRequestToSupplier(PurchaseRequest purchaseRequest) {
        if (purchaseRequest == null || purchaseRequest.getSupplier() == null) {
            throw new BadRequestException("Khong co thong tin nha cung cap de gui email.");
        }

        String supplierEmail = purchaseRequest.getSupplier().getEmail();
        String supplierName  = purchaseRequest.getSupplier().getName();
        String branchName    = purchaseRequest.getBranch() != null ? purchaseRequest.getBranch().getName() : "—";
        String requestedBy   = purchaseRequest.getCreatedBy() != null ? purchaseRequest.getCreatedBy().getFullName() : "Hệ thống";
        String rows = "";
        if (purchaseRequest.getItems() != null) {
            rows = purchaseRequest.getItems().stream()
                    .map(item -> """
                            <tr>
                                <td style="padding:10px 12px;border:1px solid #d1d5db;font-size:13px;color:#111827;vertical-align:top;">%s</td>
                                <td style="padding:10px 12px;border:1px solid #d1d5db;font-size:13px;color:#111827;vertical-align:top;">%s</td>
                                <td style="padding:10px 12px;border:1px solid #d1d5db;font-size:13px;color:#111827;text-align:right;vertical-align:top;">%s</td>
                                <td style="padding:10px 12px;border:1px solid #d1d5db;font-size:13px;color:#111827;text-align:right;vertical-align:top;">%s</td>
                                <td style="padding:10px 12px;border:1px solid #d1d5db;font-size:13px;color:#111827;vertical-align:top;">%s</td>
                            </tr>
                            """.formatted(
                            item.getProductVariant() != null ? item.getProductVariant().getSku() : "—",
                            item.getProductVariant() != null && item.getProductVariant().getProduct() != null
                                    ? item.getProductVariant().getProduct().getName() : "—",
                            item.getRequestedQty() != null ? item.getRequestedQty() : 0,
                            formatCurrency(item.getUnitPrice()),
                            item.getNote() != null ? item.getNote() : "—"
                    ))
                    .reduce("", String::concat);
        }

        String subject = "[AgriShrimp] Phiếu yêu cầu mua " + purchaseRequest.getCode();
        String body = """
                <p style="font-size:15px;color:#374151;line-height:1.8;">
                    Kính gửi <strong>%s</strong>,
                </p>
                <p style="font-size:14px;color:#374151;line-height:1.8;">
                    <strong>AgriShrimp</strong> gửi đến quý đối tác phiếu yêu cầu mua hàng với thông tin như sau:
                </p>

                <div style="margin:18px 0;">
                    <table style="width:100%%;border-collapse:collapse;border:1px solid #d1d5db;">
                        <tr>
                            <td style="padding:10px 12px;border:1px solid #d1d5db;background:#f8fafc;font-size:13px;color:#374151;width:28%%;font-weight:600;">Mã phiếu</td>
                            <td style="padding:10px 12px;border:1px solid #d1d5db;font-size:14px;color:#1e40af;font-weight:bold;">%s</td>
                        </tr>
                        <tr>
                            <td style="padding:10px 12px;border:1px solid #d1d5db;background:#f8fafc;font-size:13px;color:#374151;font-weight:600;">Kho nhận hàng</td>
                            <td style="padding:10px 12px;border:1px solid #d1d5db;font-size:14px;color:#111827;">%s</td>
                        </tr>
                        <tr>
                            <td style="padding:10px 12px;border:1px solid #d1d5db;background:#f8fafc;font-size:13px;color:#374151;font-weight:600;">Người lập phiếu</td>
                            <td style="padding:10px 12px;border:1px solid #d1d5db;font-size:14px;color:#111827;">%s</td>
                        </tr>
                    </table>
                </div>

                <p style="font-size:14px;font-weight:bold;color:#1e3a8a;margin:20px 0 8px;">Chi tiết hàng hóa yêu cầu:</p>
                <table style="width:100%%;border-collapse:collapse;border:1px solid #d1d5db;font-size:13px;">
                    <thead>
                        <tr style="background:#eaf2ff;color:#111827;">
                            <th style="padding:10px 12px;border:1px solid #d1d5db;text-align:left;font-weight:700;">SKU</th>
                            <th style="padding:10px 12px;border:1px solid #d1d5db;text-align:left;font-weight:700;">Sản phẩm</th>
                            <th style="padding:10px 12px;border:1px solid #d1d5db;text-align:right;font-weight:700;">Số lượng</th>
                            <th style="padding:10px 12px;border:1px solid #d1d5db;text-align:right;font-weight:700;">Đơn giá dự kiến</th>
                            <th style="padding:10px 12px;border:1px solid #d1d5db;text-align:left;font-weight:700;">Ghi chú</th>
                        </tr>
                    </thead>
                    <tbody>%s</tbody>
                </table>

                <div style="text-align:right;margin-top:12px;">
                    <span style="font-size:14px;color:#6b7280;">Tổng giá trị dự kiến: </span>
                    <strong style="font-size:16px;color:#1e40af;">%s</strong>
                </div>

                <p style="font-size:14px;color:#374151;line-height:1.7;margin-top:16px;">
                    Vui lòng phản hồi email này để xác nhận tiến độ cung ứng hàng hóa. Trân trọng cảm ơn!
                </p>
                """.formatted(
                supplierName,
                purchaseRequest.getCode(),
                branchName,
                requestedBy,
                rows,
                formatCurrency(purchaseRequest.getTotalAmount())
        );

        String htmlContent = buildPurchaseRequestEmailTemplate("Phiếu yêu cầu mua gửi nhà cung cấp", body);
        sendEmail(supplierEmail, subject, htmlContent);
    }

    public void sendOrderPlacedEmail(Order order) {
        String toEmail = order.getUser().getEmail();
        String name = order.getUser().getFullName();
        String orderCode = order.getCode();
        String statusLabel = orderStatusLabel(order.getStatus());
        String paymentMethodLabel = paymentMethodLabel(order.getPaymentMethod());
        String paymentStatusLabel = paymentStatusLabel(order.getPaymentStatus());
        String receiverName = order.getReceiverName() != null && !order.getReceiverName().isBlank()
                ? order.getReceiverName()
                : name;
        String receiverPhone = order.getReceiverPhone() != null && !order.getReceiverPhone().isBlank()
                ? order.getReceiverPhone()
                : "Chưa cập nhật";
        String address = order.getDeliveryAddress() != null && !order.getDeliveryAddress().isBlank()
                ? order.getDeliveryAddress()
                : order.getShippingAddress();
        if (address == null || address.isBlank()) {
            address = "Chưa cập nhật";
        }

        String orderUrl = buildWebUrl(order.getId() != null ? "/orders/" + order.getId() : "/orders/list");
        String placedAt = order.getCreatedAt() != null
                ? order.getCreatedAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                : "Chưa cập nhật";

        String paymentNote = "";
        if (order.getPaymentMethod() == PaymentMethod.PAYOS
                && order.getPayosCheckoutUrl() != null
                && !order.getPayosCheckoutUrl().isBlank()) {
            paymentNote = """
                    <tr>
                        <td style="padding:0 20px 18px;">
                            <table width="100%%" cellpadding="0" cellspacing="0" style="background:#fff7ed;border-left:4px solid #f97316;border-radius:6px;">
                                <tr>
                                    <td style="padding:12px 14px;font-size:13px;color:#9a3412;line-height:1.6;">
                                        Đơn hàng đang chờ thanh toán PayOS. Bạn có thể mở lại đơn hàng trên web để tiếp tục thanh toán.
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                    """;
        }

        String subject = "[AgriShrimp] Đã ghi nhận đơn hàng %s".formatted(orderCode);
        String htmlContent = buildOrderPlacedEmailTemplate(
                "Đơn hàng đã được ghi nhận",
                "AgriShrimp đã nhận đơn <strong style=\"color:#1f2329;\">%s</strong>. Bạn có thể theo dõi trạng thái và xem chi tiết hình ảnh sản phẩm ngay trên web.".formatted(escapeEmailText(orderCode)),
                "Xem chi tiết đơn hàng trên web",
                escapeEmailText(name),
                escapeEmailText(orderCode),
                escapeEmailText(statusLabel),
                escapeEmailText(paymentMethodLabel),
                escapeEmailText(paymentStatusLabel),
                escapeEmailText(receiverName),
                escapeEmailText(receiverPhone),
                escapeEmailText(address),
                escapeEmailText(placedAt),
                formatCurrency(order.getTotalAmount()),
                formatCurrency(order.getTotalShippingFee()),
                formatCurrency(order.getDiscountAmount()),
                formatCurrency(order.getFinalAmount()),
                buildOrderItemRows(order),
                paymentNote,
                orderUrl
        );
        sendEmail(toEmail, subject, htmlContent);
    }

    public void sendOrderStatusChangeEmail(Order order, OrderStatus newStatus) {
        String toEmail = order.getUser().getEmail();
        String name = order.getUser().getFullName();
        String orderCode = order.getCode();
        String statusLabel = orderStatusLabel(newStatus);
        String paymentMethodLabel = paymentMethodLabel(order.getPaymentMethod());
        String paymentStatusLabel = paymentStatusLabel(order.getPaymentStatus());
        String receiverName = order.getReceiverName() != null && !order.getReceiverName().isBlank()
                ? order.getReceiverName()
                : name;
        String receiverPhone = order.getReceiverPhone() != null && !order.getReceiverPhone().isBlank()
                ? order.getReceiverPhone()
                : "Chưa cập nhật";
        String address = order.getDeliveryAddress() != null && !order.getDeliveryAddress().isBlank()
                ? order.getDeliveryAddress()
                : order.getShippingAddress();
        if (address == null || address.isBlank()) {
            address = "Chưa cập nhật";
        }

        String orderUrl = buildWebUrl(order.getId() != null ? "/orders/" + order.getId() : "/orders/list");
        String placedAt = order.getCreatedAt() != null
                ? order.getCreatedAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                : "Chưa cập nhật";

        String subject = "[AgriShrimp] Đơn hàng %s: %s".formatted(orderCode, statusLabel);
        String htmlContent = buildOrderPlacedEmailTemplate(
                orderStatusEmailTitle(newStatus),
                "Đơn hàng <strong style=\"color:#1f2329;\">%s</strong> của bạn vừa được cập nhật sang trạng thái <strong style=\"color:#1f2329;\">%s</strong>. Bạn có thể mở web để xem chi tiết và hình ảnh sản phẩm.".formatted(
                        escapeEmailText(orderCode),
                        escapeEmailText(statusLabel)
                ),
                "Xem chi tiết thông tin theo dõi",
                escapeEmailText(name),
                escapeEmailText(orderCode),
                escapeEmailText(statusLabel),
                escapeEmailText(paymentMethodLabel),
                escapeEmailText(paymentStatusLabel),
                escapeEmailText(receiverName),
                escapeEmailText(receiverPhone),
                escapeEmailText(address),
                escapeEmailText(placedAt),
                formatCurrency(order.getTotalAmount()),
                formatCurrency(order.getTotalShippingFee()),
                formatCurrency(order.getDiscountAmount()),
                formatCurrency(order.getFinalAmount()),
                buildOrderItemRows(order),
                "",
                orderUrl
        );
        sendEmail(toEmail, subject, htmlContent);
    }

    public void sendOrderReplenishmentAlertEmail(String toEmail, String recipientName, Order order) {
        String branchName = order.getBranch() != null ? order.getBranch().getName() : "—";

        String subject = "[AgriShrimp] ⚠️ Đơn hàng %s đang chờ nhập bù kho".formatted(order.getCode());
        String body = """
                <p style="font-size:16px;color:#374151;line-height:1.8;">
                    Xin chào <strong>%s</strong>,
                </p>
                <p style="font-size:15px;color:#374151;line-height:1.8;">
                    Đơn hàng <strong>%s</strong> (chi nhánh: <strong>%s</strong>) đang tạm dừng do
                    thiếu hàng trong kho và cần nhập bù để tiếp tục xử lý.
                </p>

                <div style="background:#fffbeb;border-left:4px solid #f59e0b;border-radius:8px;padding:14px 18px;margin:16px 0;">
                    <p style="margin:0;font-size:14px;color:#92400e;line-height:1.7;">
                        ⚠️ Vui lòng kiểm tra và xử lý yêu cầu nhập hàng liên quan sớm để tránh
                        làm chậm tiến độ giao hàng cho khách.
                    </p>
                </div>
                """.formatted(recipientName, order.getCode(), branchName);

        String htmlContent = buildEmailTemplate("Đơn Hàng Chờ Nhập Bù Kho", body, "Xem đơn hàng →");
        sendEmail(toEmail, subject, htmlContent);
    }

    public void sendPurchaseRequestApprovalNeededEmail(String toEmail, String recipientName, PurchaseRequest purchaseRequest) {
        String branchName = purchaseRequest.getBranch() != null ? purchaseRequest.getBranch().getName() : "—";
        String requestedBy = purchaseRequest.getCreatedBy() != null ? purchaseRequest.getCreatedBy().getFullName() : "Hệ thống";

        String subject = "[AgriShrimp] Yêu cầu mua %s đang chờ bạn duyệt".formatted(purchaseRequest.getCode());
        String body = """
                <p style="font-size:16px;color:#374151;line-height:1.8;">
                    Xin chào <strong>%s</strong>,
                </p>
                <p style="font-size:15px;color:#374151;line-height:1.8;">
                    Phiếu yêu cầu mua <strong>%s</strong> (kho: <strong>%s</strong>, người lập:
                    <strong>%s</strong>) vừa được tạo và đang chờ bạn duyệt.
                </p>

                <div style="background:#fffbeb;border-left:4px solid #f59e0b;border-radius:8px;padding:14px 18px;margin:16px 0;">
                    <p style="margin:0;font-size:14px;color:#92400e;line-height:1.7;">
                        ⚠️ Quá trình nhập hàng chỉ tiếp tục sau khi phiếu này được duyệt.
                    </p>
                </div>
                """.formatted(recipientName, purchaseRequest.getCode(), branchName, requestedBy);

        String htmlContent = buildEmailTemplate("Yêu Cầu Mua Chờ Duyệt", body, "Xem phiếu yêu cầu →");
        sendEmail(toEmail, subject, htmlContent);
    }

    public void sendPasswordResetEmail(String toEmail, String name, String resetLink) {
        String subject = "[AgriShrimp] Yêu cầu đặt lại mật khẩu";
        String body = """
                <p style="font-size:16px;color:#374151;line-height:1.8;">
                    Xin chào <strong>%s</strong>,
                </p>
                <p style="font-size:15px;color:#374151;line-height:1.8;">
                    Chúng tôi nhận được yêu cầu đặt lại mật khẩu cho tài khoản <strong>AgriShrimp</strong> của bạn.
                    Nhấn vào nút bên dưới để đặt mật khẩu mới.
                </p>

                <div style="background:#fffbeb;border-left:4px solid #f59e0b;border-radius:8px;padding:14px 18px;margin:16px 0;">
                    <p style="margin:0;font-size:14px;color:#92400e;line-height:1.7;">
                        ⚠️ Liên kết này chỉ có hiệu lực trong <strong>15 phút</strong> và chỉ dùng được một lần.
                    </p>
                </div>

                <p style="font-size:13px;color:#9ca3af;line-height:1.7;">
                    Nếu bạn không yêu cầu đặt lại mật khẩu, vui lòng bỏ qua email này — mật khẩu hiện tại của bạn vẫn an toàn.
                </p>
                """.formatted(name);

        String htmlContent = buildEmailTemplate("Đặt Lại Mật Khẩu", body, resetLink, "Đặt lại mật khẩu →");
        sendEmail(toEmail, subject, htmlContent);
    }

    public void sendPasswordResetGoogleAccountNotice(String toEmail, String name) {
        String subject = "[AgriShrimp] Tài khoản của bạn đăng nhập bằng Google";
        String body = """
                <p style="font-size:16px;color:#374151;line-height:1.8;">
                    Xin chào <strong>%s</strong>,
                </p>
                <p style="font-size:15px;color:#374151;line-height:1.8;">
                    Chúng tôi nhận được yêu cầu đặt lại mật khẩu cho email này, nhưng tài khoản của bạn được tạo bằng
                    <strong>Đăng nhập Google</strong> nên không có mật khẩu riêng để đặt lại.
                </p>

                <div style="background:#f0f9ff;border-left:4px solid #1e40af;border-radius:8px;padding:14px 18px;margin:16px 0;">
                    <p style="margin:0;font-size:14px;color:#1e40af;line-height:1.7;">
                        Vui lòng dùng nút <strong>"Tiếp tục với Google"</strong> ở trang đăng nhập để truy cập tài khoản.
                    </p>
                </div>
                """.formatted(name);

        String htmlContent = buildEmailTemplate("Tài Khoản Đăng Nhập Google", body, "Đăng nhập ngay →");
        sendEmail(toEmail, subject, htmlContent);
    }

    public void sendVoucherExpiringSoonEmail(String toEmail, String customerName, Voucher voucher, int daysLeft) {
        String subject = "[AgriShrimp] 🎟️ Voucher %s sắp hết hạn".formatted(voucher.getCode());
        String body = """
                <p style="font-size:16px;color:#374151;line-height:1.8;">
                    Xin chào <strong>%s</strong>,
                </p>
                <p style="font-size:15px;color:#374151;line-height:1.8;">
                    Voucher <strong>%s</strong> bạn đã lưu sẽ hết hạn trong
                    <strong>%d ngày</strong> nữa và bạn chưa sử dụng.
                </p>

                <div style="text-align:center;margin:24px 0;">
                    <span style="display:inline-block;background:#eaf2ff;color:#1e40af;font-size:20px;font-weight:bold;padding:10px 24px;border-radius:50px;letter-spacing:1px;">%s</span>
                </div>

                <p style="font-size:14px;color:#6b7280;line-height:1.7;">
                    Đừng bỏ lỡ ưu đãi này — hãy sử dụng voucher trước khi hết hạn nhé!
                </p>
                """.formatted(customerName, voucher.getCode(), daysLeft, voucher.getCode());

        String htmlContent = buildEmailTemplate("Voucher Sắp Hết Hạn", body, "Mua sắm ngay →");
        sendEmail(toEmail, subject, htmlContent);
    }

    public void sendShrimpPriceBlogReadyEmail(
            String toEmail,
            String recipientName,
            BlogPost post,
            String reviewUrl,
            int priceRowCount,
            String priceRangeLabel,
            String sourceDateLabel) {
        String safeName = recipientName == null || recipientName.isBlank()
                ? "Admin"
                : escapeEmailText(recipientName.trim());
        String safeTitle = escapeEmailText(post != null ? post.getTitle() : "Bài giá tôm hôm nay");
        String safeRange = escapeEmailText(priceRangeLabel);
        String safeSourceDate = escapeEmailText(sourceDateLabel);
        String safeUrl = reviewUrl == null || reviewUrl.isBlank() ? LOGIN_URL : reviewUrl.trim();

        String subject = "[AgriShrimp] Bài giá tôm hôm nay đang chờ duyệt";
        String body = """
                <p style="font-size:16px;color:#374151;line-height:1.8;">
                    Xin chào <strong>%s</strong>,
                </p>
                <p style="font-size:15px;color:#374151;line-height:1.8;">
                    AI đã tạo xong bài viết giá tôm hằng ngày và chuyển vào trạng thái
                    <strong>chờ duyệt</strong> trong admin.
                </p>

                <div style="background:#f0f9ff;border-left:4px solid #1e40af;border-radius:8px;padding:14px 18px;margin:16px 0;">
                    <p style="margin:0 0 8px;font-size:14px;color:#1e40af;line-height:1.7;">
                        <strong>Tiêu đề:</strong> %s
                    </p>
                    <p style="margin:0 0 8px;font-size:14px;color:#1e40af;line-height:1.7;">
                        <strong>Dữ liệu:</strong> %d dòng giá tôm thương phẩm, %s
                    </p>
                    <p style="margin:0;font-size:14px;color:#1e40af;line-height:1.7;">
                        <strong>Ngày cập nhật dữ liệu:</strong> %s
                    </p>
                </div>

                <p style="font-size:14px;color:#6b7280;line-height:1.7;">
                    Vui lòng kiểm tra bảng giá, chỉnh sửa nếu cần rồi duyệt/xuất bản bài viết.
                </p>
                """.formatted(safeName, safeTitle, priceRowCount, safeRange, safeSourceDate);

        String htmlContent = buildEmailTemplate("Bài Giá Tôm Chờ Duyệt", body, safeUrl, "Mở bài trong admin");
        sendEmail(toEmail, subject, htmlContent);
    }

    private String buildOrderPlacedEmailTemplate(
            String emailTitle,
            String emailIntro,
            String ctaText,
            String customerName,
            String orderCode,
            String statusLabel,
            String paymentMethodLabel,
            String paymentStatusLabel,
            String receiverName,
            String receiverPhone,
            String address,
            String placedAt,
            String subtotal,
            String shippingFee,
            String discountAmount,
            String finalAmount,
            String itemRows,
            String paymentNote,
            String orderUrl) {
        return """
                <!DOCTYPE html>
                <html lang="vi">
                <head>
                    <meta charset="UTF-8"/>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
                    <title>AgriShrimp Shop</title>
                </head>
                <body style="margin:0;padding:0;background:#ffffff;font-family:Arial,'Segoe UI',sans-serif;color:#1f2329;">
                    <table width="100%%" cellpadding="0" cellspacing="0" style="width:100%%;background:#ffffff;">
                        <tr>
                            <td align="center" style="padding:24px 10px;">
                                <table width="430" cellpadding="0" cellspacing="0" style="width:100%%;max-width:430px;background:#ffffff;border-collapse:collapse;">
                                    <tr>
                                        <td style="background:#050505;padding:22px 20px 16px;">
                                            <table width="100%%" cellpadding="0" cellspacing="0">
                                                <tr>
                                                    <td style="vertical-align:middle;">
                                                        <img src="%s" width="38" height="38" alt="AgriShrimp" style="display:inline-block;vertical-align:middle;border-radius:8px;margin-right:10px;"/>
                                                        <span style="display:inline-block;vertical-align:middle;color:#ffffff;font-size:25px;font-weight:800;line-height:1;">AgriShrimp<br/><span style="font-size:20px;font-weight:700;">Shop</span></span>
                                                    </td>
                                                    <td width="86" align="right" style="vertical-align:top;">
                                                        <table cellpadding="0" cellspacing="0" width="86">
                                                            <tr>
                                                                <td style="height:34px;background:#78eee8;border-radius:0 0 0 42px;"></td>
                                                            </tr>
                                                            <tr>
                                                                <td style="height:28px;background:#ef4866;border-radius:42px 0 0 0;"></td>
                                                            </tr>
                                                        </table>
                                                    </td>
                                                </tr>
                                            </table>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td style="background:#f5f5f5;border-bottom:1px solid #eeeeee;">
                                            <table width="100%%" cellpadding="0" cellspacing="0">
                                                <tr>
                                                    <td align="center" style="width:50%%;padding:13px 8px;font-size:14px;font-weight:700;color:#111111;">Đơn hàng</td>
                                                    <td align="center" style="width:1px;color:#cfcfcf;font-size:13px;">|</td>
                                                    <td align="center" style="width:50%%;padding:13px 8px;font-size:14px;font-weight:700;color:#111111;">Giỏ hàng</td>
                                                </tr>
                                            </table>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td style="padding:30px 20px 10px;">
                                            <h1 style="margin:0 0 16px;font-size:29px;line-height:1.35;color:#1f2329;font-weight:800;">
                                                %s
                                            </h1>
                                            <p style="margin:0 0 14px;font-size:13px;line-height:1.7;color:#5f6368;">Xin chào %s!</p>
                                            <p style="margin:0 0 14px;font-size:13px;line-height:1.7;color:#5f6368;">
                                                %s
                                            </p>
                                            <p style="margin:0 0 18px;font-size:13px;line-height:1.7;color:#5f6368;">Đội ngũ AgriShrimp Shop</p>
                                            <table width="100%%" cellpadding="0" cellspacing="0">
                                                <tr>
                                                    <td align="center" style="background:#ef4866;border-radius:6px;">
                                                        <a href="%s" style="display:block;padding:14px 16px;color:#ffffff;text-decoration:none;font-size:14px;font-weight:800;">
                                                            %s
                                                        </a>
                                                    </td>
                                                </tr>
                                            </table>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td style="height:10px;border-bottom:8px solid #f5f5f5;"></td>
                                    </tr>
                                    <tr>
                                        <td style="padding:22px 20px 8px;">
                                            <p style="margin:0 0 14px;font-size:15px;color:#111111;font-weight:800;">AgriShrimp Store</p>
                                            %s
                                            <table width="100%%" cellpadding="0" cellspacing="0" style="margin-top:12px;">
                                                <tr>
                                                    <td style="padding:6px 0;font-size:13px;color:#777777;">ID đơn hàng</td>
                                                    <td align="right" style="padding:6px 0;font-size:13px;color:#111111;font-weight:600;">%s</td>
                                                </tr>
                                                <tr>
                                                    <td style="padding:6px 0;font-size:13px;color:#777777;">Ngày đặt hàng</td>
                                                    <td align="right" style="padding:6px 0;font-size:13px;color:#111111;">%s</td>
                                                </tr>
                                                <tr>
                                                    <td style="padding:6px 0;font-size:13px;color:#777777;">Trạng thái</td>
                                                    <td align="right" style="padding:6px 0;font-size:13px;color:#111111;">%s</td>
                                                </tr>
                                                <tr>
                                                    <td style="padding:6px 0;font-size:13px;color:#777777;">Thanh toán</td>
                                                    <td align="right" style="padding:6px 0;font-size:13px;color:#111111;">%s - %s</td>
                                                </tr>
                                            </table>
                                        </td>
                                    </tr>
                                    %s
                                    <tr>
                                        <td style="height:10px;border-bottom:8px solid #f5f5f5;"></td>
                                    </tr>
                                    <tr>
                                        <td style="padding:20px;">
                                            <h2 style="margin:0 0 16px;font-size:16px;color:#111111;font-weight:800;">Tóm tắt kiện hàng</h2>
                                            <table width="100%%" cellpadding="0" cellspacing="0">
                                                <tr>
                                                    <td style="padding:6px 0;font-size:14px;color:#737373;">Tổng phụ</td>
                                                    <td align="right" style="padding:6px 0;font-size:14px;color:#111111;">%s</td>
                                                </tr>
                                                <tr>
                                                    <td style="padding:6px 0;font-size:14px;color:#737373;">Vận chuyển</td>
                                                    <td align="right" style="padding:6px 0;font-size:14px;color:#111111;">%s</td>
                                                </tr>
                                                <tr>
                                                    <td style="padding:6px 0;font-size:14px;color:#737373;">Giảm giá</td>
                                                    <td align="right" style="padding:6px 0;font-size:14px;color:#111111;">- %s</td>
                                                </tr>
                                                <tr>
                                                    <td style="padding:8px 0 0;font-size:15px;color:#111111;font-weight:800;">Tổng thanh toán</td>
                                                    <td align="right" style="padding:8px 0 0;font-size:15px;color:#111111;font-weight:800;">%s</td>
                                                </tr>
                                            </table>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td style="height:10px;border-bottom:8px solid #f5f5f5;"></td>
                                    </tr>
                                    <tr>
                                        <td style="padding:20px;">
                                            <h2 style="margin:0 0 14px;font-size:16px;color:#111111;font-weight:800;">Địa chỉ vận chuyển</h2>
                                            <p style="margin:0 0 8px;font-size:14px;line-height:1.6;color:#4a4a4a;">%s</p>
                                            <p style="margin:0 0 8px;font-size:14px;line-height:1.6;color:#4a4a4a;">%s</p>
                                            <p style="margin:0;font-size:14px;line-height:1.6;color:#4a4a4a;">%s</p>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td style="padding:6px 20px 22px;">
                                            <h2 style="margin:0 0 12px;font-size:16px;color:#111111;font-weight:800;">Bạn gặp vấn đề?</h2>
                                            <table width="100%%" cellpadding="0" cellspacing="0">
                                                <tr>
                                                    <td style="padding:10px 0;font-size:13px;color:#111111;">Xem tất cả vấn đề</td>
                                                    <td align="right" style="font-size:20px;color:#777777;">&gt;</td>
                                                </tr>
                                                <tr>
                                                    <td colspan="2" align="center" style="border:1px solid #dddddd;border-radius:5px;">
                                                        <a href="%s" style="display:block;padding:12px;color:#111111;text-decoration:none;font-size:14px;font-weight:700;">Truy cập Trung tâm trợ giúp</a>
                                                    </td>
                                                </tr>
                                            </table>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td style="background:#f5f5f5;padding:22px 20px;text-align:center;">
                                            <p style="margin:0 0 14px;font-size:11px;line-height:1.6;color:#888888;">
                                                Thông tin đơn hàng phản ánh dữ liệu tại thời điểm email được gửi và có thể thay đổi khi hệ thống cập nhật.
                                            </p>
                                            <p style="margin:0 0 14px;font-size:11px;line-height:1.7;color:#777777;">
                                                Tin nhắn này được gửi tới bạn vì có hoạt động mua hàng gần đây trên AgriShrimp Shop.
                                            </p>
                                            <p style="margin:0;font-size:18px;font-weight:800;color:#111111;">AgriShrimp Shop</p>
                                        </td>
                                    </tr>
                                </table>
                            </td>
                        </tr>
                    </table>
                </body>
                </html>
                """.formatted(
                LOGO_URL,
                emailTitle,
                customerName,
                emailIntro,
                escapeEmailText(orderUrl),
                ctaText,
                itemRows,
                orderCode,
                placedAt,
                statusLabel,
                paymentMethodLabel,
                paymentStatusLabel,
                paymentNote,
                subtotal,
                shippingFee,
                discountAmount,
                finalAmount,
                receiverName,
                receiverPhone,
                address,
                escapeEmailText(buildWebUrl("/contact"))
        );
    }

    private String buildOrderItemRows(Order order) {
        if (order.getOrderItems() == null || order.getOrderItems().isEmpty()) {
            return """
                    <table width="100%%" cellpadding="0" cellspacing="0">
                        <tr>
                            <td style="padding:10px 0;font-size:13px;color:#777777;">Chưa có thông tin sản phẩm trong email này.</td>
                        </tr>
                    </table>
                    """;
        }

        return order.getOrderItems().stream()
                .map(this::buildOrderItemRow)
                .reduce("", String::concat);
    }

    private String buildOrderItemRow(OrderItem item) {
        ProductVariant variant = item.getProductVariant();
        Product product = variant != null ? variant.getProduct() : null;
        String productName = product != null && product.getName() != null && !product.getName().isBlank()
                ? product.getName()
                : "Sản phẩm AgriShrimp";
        String sku = variant != null && variant.getSku() != null && !variant.getSku().isBlank()
                ? variant.getSku()
                : "SKU đang cập nhật";
        String imageUrl = firstImageUrl(variant, product);
        String productUrl = productDetailUrl(product);
        int quantity = item.getQuantity() != null ? item.getQuantity() : 0;
        BigDecimal unitPrice = item.getPrice() != null ? item.getPrice() : BigDecimal.ZERO;
        BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(Math.max(quantity, 0L)));

        return """
                <table width="100%%" cellpadding="0" cellspacing="0" style="margin-bottom:14px;">
                    <tr>
                        <td width="92" valign="top" style="padding-right:12px;">
                            <a href="%s" style="text-decoration:none;">
                                <img src="%s" width="82" height="82" alt="%s" style="display:block;width:82px;height:82px;object-fit:cover;border-radius:6px;border:1px solid #eeeeee;background:#f4f4f4;"/>
                            </a>
                        </td>
                        <td valign="top">
                            <a href="%s" style="text-decoration:none;color:#111111;">
                                <p style="margin:0 0 5px;font-size:14px;line-height:1.35;color:#111111;font-weight:700;">%s</p>
                            </a>
                            <p style="margin:0 0 12px;font-size:12px;line-height:1.4;color:#777777;">%s</p>
                            <table width="100%%" cellpadding="0" cellspacing="0">
                                <tr>
                                    <td style="font-size:13px;color:#111111;font-weight:800;">%s</td>
                                    <td align="right" style="font-size:13px;color:#111111;font-weight:700;">x %d</td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                </table>
                """.formatted(
                escapeEmailText(productUrl),
                escapeEmailText(imageUrl),
                escapeEmailText(productName),
                escapeEmailText(productUrl),
                escapeEmailText(productName),
                escapeEmailText(sku),
                formatCurrency(lineTotal),
                quantity
        );
    }

    private String firstImageUrl(ProductVariant variant, Product product) {
        if (variant != null && variant.getImageUrl() != null && !variant.getImageUrl().isBlank()) {
            return firstCommaSeparatedValue(variant.getImageUrl());
        }
        if (product != null && product.getProductImages() != null) {
            return product.getProductImages().stream()
                    .filter(image -> image.getImageUrl() != null && !image.getImageUrl().isBlank())
                    .sorted(Comparator.comparing(ProductImage::getId, Comparator.nullsLast(Long::compareTo)))
                    .map(ProductImage::getImageUrl)
                    .findFirst()
                    .orElse(LOGO_URL);
        }
        return LOGO_URL;
    }

    private String firstCommaSeparatedValue(String value) {
        String first = value.split(",")[0].trim();
        return first.isBlank() ? LOGO_URL : first;
    }

    private String productDetailUrl(Product product) {
        if (product == null) {
            return buildWebUrl("/san-pham");
        }
        if (product.getSlug() != null && !product.getSlug().isBlank()) {
            return buildWebUrl("/san-pham/" + product.getSlug());
        }
        return product.getId() != null ? buildWebUrl("/product/" + product.getId()) : buildWebUrl("/san-pham");
    }

    private String buildWebUrl(String path) {
        String base = webBaseUrl == null || webBaseUrl.isBlank() ? "https://agrishrimp.io.vn" : webBaseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (path == null || path.isBlank()) {
            return base;
        }
        return path.startsWith("/") ? base + path : base + "/" + path;
    }

    private String orderStatusEmailTitle(OrderStatus status) {
        if (status == null) {
            return "Đơn hàng đã được cập nhật";
        }
        return switch (status) {
            case PENDING -> "Đơn hàng đang chờ xác nhận";
            case CONFIRMED -> "Đơn hàng đã được xác nhận";
            case SHIPPING -> "Đơn hàng đang được vận chuyển";
            case COMPLETED -> "Đơn hàng đã hoàn tất";
            case CANCELLED -> "Đơn hàng đã được huỷ";
            case RETURNED -> "Đơn hàng đã được hoàn trả";
            default -> "Đơn hàng đã được cập nhật";
        };
    }

    private String orderStatusLabel(OrderStatus status) {
        if (status == null) {
            return "Chưa cập nhật";
        }
        return switch (status) {
            case PENDING -> "Chờ xác nhận";
            case AWAITING_PAYMENT -> "Chờ thanh toán";
            case AWAITING_REPLENISHMENT -> "Chờ nhập bù kho";
            case CONFIRMED -> "Đã xác nhận";
            case PROCESSING -> "Đang chuẩn bị hàng";
            case READY_FOR_PICKUP -> "Chờ bàn giao";
            case SHIPPING -> "Đang giao hàng";
            case RECEIVED -> "Đã nhận hàng";
            case COMPLETED -> "Đã hoàn tất";
            case CANCELLED -> "Đã huỷ";
            case RETURNED -> "Đã hoàn trả";
        };
    }

    private String paymentMethodLabel(PaymentMethod method) {
        if (method == null) {
            return "Chưa cập nhật";
        }
        return switch (method) {
            case CASH -> "Tiền mặt";
            case TRANSFER -> "Chuyển khoản";
            case COD -> "Thanh toán khi nhận hàng";
            case PAYOS -> "PayOS";
        };
    }

    private String paymentStatusLabel(PaymentStatus status) {
        if (status == null) {
            return "Chưa cập nhật";
        }
        return switch (status) {
            case UNPAID -> "Chưa thanh toán";
            case PENDING -> "Đang chờ";
            case PENDING_VERIFICATION -> "Chờ xác minh";
            case PARTIALLY_PAID -> "Thanh toán một phần";
            case PAID -> "Đã thanh toán";
            case FAILED -> "Thanh toán lỗi";
            case EXPIRED -> "Đã hết hạn";
            case REFUND_PENDING -> "Chờ hoàn tiền";
            case REFUNDED -> "Đã hoàn tiền";
        };
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Shared email layout builder
    // ─────────────────────────────────────────────────────────────────────────

    private String buildPurchaseRequestEmailTemplate(String headerTitle, String bodyHtml) {
        return """
                <!DOCTYPE html>
                <html lang="vi">
                <head>
                    <meta charset="UTF-8"/>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
                    <title>AgriShrimp</title>
                </head>
                <body style="margin:0;padding:0;background-color:#ffffff;font-family:'Segoe UI',Arial,sans-serif;color:#111827;">
                    <table width="100%%" cellpadding="0" cellspacing="0" style="width:100%%;background:#ffffff;">
                        <tr>
                            <td style="padding:28px 20px;">
                                <table width="1120" cellpadding="0" cellspacing="0" style="max-width:1120px;width:100%%;margin:0 auto;background:#ffffff;border:1px solid #d1d5db;border-collapse:collapse;">
                                    <tr>
                                        <td style="padding:22px 24px;border-bottom:3px solid #1e40af;">
                                            <h1 style="margin:0;font-size:22px;font-weight:700;color:#1d4ed8;line-height:1.3;">
                                                %s
                                            </h1>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td style="padding:24px;">
                                            %s
                                        </td>
                                    </tr>
                                    <tr>
                                        <td style="padding:16px 24px 22px;border-top:1px solid #d1d5db;">
                                            <p style="margin:0;font-size:12px;color:#6b7280;line-height:1.6;">
                                                AgriShrimp - He thong quan ly nong san thong minh.
                                            </p>
                                            <p style="margin:4px 0 0;font-size:12px;color:#6b7280;line-height:1.6;">
                                                Day la email tu dong tu he thong AgriShrimp.
                                            </p>
                                        </td>
                                    </tr>
                                </table>
                            </td>
                        </tr>
                    </table>
                </body>
                </html>
                """.formatted(headerTitle, bodyHtml);
    }

    private String buildEmailTemplate(String headerTitle, String bodyHtml, String ctaText) {
        return buildEmailTemplate(headerTitle, bodyHtml, LOGIN_URL, ctaText);
    }

    private String buildEmailTemplate(String headerTitle, String bodyHtml, String ctaUrl, String ctaText) {
        return """
                <!DOCTYPE html>
                <html lang="vi">
                <head>
                    <meta charset="UTF-8"/>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
                    <title>AgriShrimp</title>
                </head>
                <body style="margin:0;padding:0;background-color:#f1f5f9;font-family:'Segoe UI',Arial,sans-serif;">

                    <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f1f5f9;padding:32px 16px;">
                        <tr>
                            <td align="center">
                                <table width="600" cellpadding="0" cellspacing="0"
                                       style="max-width:600px;width:100%%;background:#ffffff;border-radius:16px;overflow:hidden;box-shadow:0 4px 24px rgba(30,64,175,0.06);">

                                    <!-- HEADER (Flat Blue, No Gradient loang màu trắng, No Logo) -->
                                    <tr>
                                        <td style="background-color:#1e40af;padding:32px 32px 32px;text-align:center;">
                                            <h1 style="margin:0;font-size:22px;font-weight:700;color:#ffffff;line-height:1.2;text-align:center;">
                                                %s
                                            </h1>
                                        </td>
                                    </tr>

                                    <!-- BODY -->
                                    <tr>
                                        <td style="padding:32px 32px 8px;">
                                            %s
                                        </td>
                                    </tr>

                                    <!-- CTA BUTTON -->
                                    <tr>
                                        <td style="padding:16px 32px 32px;text-align:center;">
                                            <a href="%s"
                                               style="display:inline-block;background-color:#1e40af;color:#ffffff;text-decoration:none;font-size:15px;font-weight:600;padding:14px 36px;border-radius:50px;letter-spacing:0.5px;box-shadow:0 4px 12px rgba(30,64,175,0.25);">
                                                %s
                                            </a>
                                        </td>
                                    </tr>

                                    <!-- DIVIDER -->
                                    <tr>
                                        <td style="padding:0 32px;">
                                            <hr style="border:none;border-top:1px solid #e5e7eb;margin:0;"/>
                                        </td>
                                    </tr>

                                    <!-- FOOTER -->
                                    <tr>
                                        <td style="padding:20px 32px 28px;text-align:center;">
                                            <p style="margin:0 0 4px;font-size:12px;color:#9ca3af;">
                                                AgriShrimp · Hệ thống quản lý nông sản thông minh
                                            </p>
                                            <p style="margin:0;font-size:11px;color:#d1d5db;">
                                                Đây là email tự động, vui lòng không trả lời trực tiếp email này.
                                            </p>
                                        </td>
                                    </tr>

                                </table>
                            </td>
                        </tr>
                    </table>

                </body>
                </html>
                """.formatted(headerTitle, bodyHtml, ctaUrl, ctaText);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void sendEmail(String toEmail, String subject, String htmlContent) {
        if (resendApiKey == null || resendApiKey.isBlank()) {
            throw new BadRequestException(
                    "Chưa cấu hình RESEND_API_KEY. Vui lòng thêm vào .env, .env.local hoặc biến môi trường của server rồi khởi động lại backend.");
        }

        try {
            Resend resend = new Resend(resendApiKey);

            CreateEmailOptions params = CreateEmailOptions.builder()
                    .from(fromName + " <" + fromEmail + ">")
                    .to(List.of(toEmail))
                    .subject(subject)
                    .html(htmlContent)
                    .build();

            CreateEmailResponse response = resend.emails().send(params);
            String emailId = response != null ? response.getId() : "unknown";
            log.info("Email sent via Resend to {} (id={})", toEmail, emailId);

        } catch (ResendException e) {
            log.error("Resend failed for {}: {}", toEmail, e.getMessage());
            throw new BadRequestException("Không gửi được email tới " + toEmail + ". Lỗi Resend: " + e.getMessage());
        }
    }

    private String formatCurrency(BigDecimal amount) {
        BigDecimal safeAmount = amount != null ? amount : BigDecimal.ZERO;
        NumberFormat formatter = NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN"));
        formatter.setMinimumFractionDigits(0);
        formatter.setMaximumFractionDigits(0);
        formatter.setRoundingMode(RoundingMode.HALF_UP);
        return formatter.format(safeAmount) + " VND";
    }

    private String escapeEmailText(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
