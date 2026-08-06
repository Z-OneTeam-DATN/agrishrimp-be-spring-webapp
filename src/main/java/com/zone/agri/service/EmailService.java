package com.zone.agri.service;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import com.zone.agri.entity.Order;
import com.zone.agri.entity.PurchaseRequest;
import com.zone.agri.entity.Voucher;
import com.zone.agri.entity.enums.OrderStatus;
import com.zone.agri.exception.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
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

    public void sendOrderStatusChangeEmail(Order order, OrderStatus newStatus) {
        String toEmail = order.getUser().getEmail();
        String name = order.getUser().getFullName();
        String statusLabel = orderStatusLabel(newStatus);

        String subject = "[AgriShrimp] Đơn hàng %s: %s".formatted(order.getCode(), statusLabel);
        String body = """
                <p style="font-size:16px;color:#374151;line-height:1.8;">
                    Xin chào <strong>%s</strong>,
                </p>
                <p style="font-size:15px;color:#374151;line-height:1.8;">
                    Đơn hàng <strong>%s</strong> của bạn vừa được cập nhật trạng thái:
                </p>

                <div style="text-align:center;margin:24px 0;">
                    <span style="display:inline-block;background:#eaf2ff;color:#1e40af;font-size:20px;font-weight:bold;padding:10px 24px;border-radius:50px;">%s</span>
                </div>

                <p style="font-size:14px;color:#6b7280;line-height:1.7;">
                    Bạn có thể xem chi tiết đơn hàng và lịch sử cập nhật trong mục "Đơn hàng của tôi" sau khi đăng nhập.
                </p>
                """.formatted(name, order.getCode(), statusLabel);

        String htmlContent = buildEmailTemplate("Cập Nhật Đơn Hàng", body, "Xem đơn hàng của tôi →");
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

    private String orderStatusLabel(OrderStatus status) {
        return switch (status) {
            case CONFIRMED -> "Đã xác nhận";
            case PROCESSING -> "Đang chuẩn bị hàng";
            case SHIPPING -> "Đang giao hàng";
            case COMPLETED -> "Đã hoàn tất";
            case CANCELLED -> "Đã huỷ";
            case RETURNED -> "Đã hoàn trả";
            default -> status.name();
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
}
