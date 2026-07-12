package com.zone.agri.service;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import com.zone.agri.entity.PurchaseRequest;
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
        String expectedDate  = purchaseRequest.getExpectedDeliveryDate() != null
                ? purchaseRequest.getExpectedDeliveryDate().toString() : "Chưa xác định";

        String rows = "";
        if (purchaseRequest.getItems() != null) {
            rows = purchaseRequest.getItems().stream()
                    .map(item -> """
                            <tr>
                                <td style="padding:10px 12px;border-bottom:1px solid #e5e7eb;font-size:13px;color:#374151;">%s</td>
                                <td style="padding:10px 12px;border-bottom:1px solid #e5e7eb;font-size:13px;color:#374151;">%s</td>
                                <td style="padding:10px 12px;border-bottom:1px solid #e5e7eb;font-size:13px;color:#374151;text-align:center;">%s</td>
                                <td style="padding:10px 12px;border-bottom:1px solid #e5e7eb;font-size:13px;color:#374151;text-align:right;">%s</td>
                                <td style="padding:10px 12px;border-bottom:1px solid #e5e7eb;font-size:13px;color:#6b7280;">%s</td>
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

        String subject = "[AgriShrimp] 📋 Phiếu yêu cầu mua " + purchaseRequest.getCode();
        String body = """
                <p style="font-size:15px;color:#374151;line-height:1.8;">
                    Kính gửi <strong>%s</strong>,
                </p>
                <p style="font-size:14px;color:#374151;line-height:1.8;">
                    <strong>AgriShrimp</strong> gửi đến quý đối tác phiếu yêu cầu mua hàng với thông tin như sau:
                </p>

                <div style="background:#f0f9ff;border-radius:8px;padding:14px 18px;margin:16px 0;">
                    <table style="width:100%%;border-collapse:collapse;">
                        <tr>
                            <td style="padding:4px 0;font-size:13px;color:#6b7280;width:40%%;">📋 Mã phiếu</td>
                            <td style="padding:4px 0;font-size:14px;color:#1e40af;font-weight:bold;">%s</td>
                        </tr>
                        <tr>
                            <td style="padding:4px 0;font-size:13px;color:#6b7280;">🏪 Kho nhận hàng</td>
                            <td style="padding:4px 0;font-size:14px;color:#374151;">%s</td>
                        </tr>
                        <tr>
                            <td style="padding:4px 0;font-size:13px;color:#6b7280;">👤 Người lập phiếu</td>
                            <td style="padding:4px 0;font-size:14px;color:#374151;">%s</td>
                        </tr>
                        <tr>
                            <td style="padding:4px 0;font-size:13px;color:#6b7280;">📅 Ngày giao dự kiến</td>
                            <td style="padding:4px 0;font-size:14px;color:#374151;">%s</td>
                        </tr>
                    </table>
                </div>

                <p style="font-size:14px;font-weight:bold;color:#1e3a8a;margin:20px 0 8px;">Chi tiết hàng hóa yêu cầu:</p>
                <table style="width:100%%;border-collapse:collapse;border-radius:8px;overflow:hidden;font-size:13px;">
                    <thead>
                        <tr style="background:#1e40af;color:#fff;">
                            <th style="padding:10px 12px;text-align:left;font-weight:600;">SKU</th>
                            <th style="padding:10px 12px;text-align:left;font-weight:600;">Sản phẩm</th>
                            <th style="padding:10px 12px;text-align:center;font-weight:600;">SL</th>
                            <th style="padding:10px 12px;text-align:right;font-weight:600;">Đơn giá</th>
                            <th style="padding:10px 12px;text-align:left;font-weight:600;">Ghi chú</th>
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
                expectedDate,
                rows,
                formatCurrency(purchaseRequest.getTotalAmount())
        );

        String cta = "Xem chi tiết đơn hàng →";
        String htmlContent = buildEmailTemplate("Phiếu Yêu Cầu Mua Hàng", body, cta);
        sendEmail(supplierEmail, subject, htmlContent);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Shared email layout builder
    // ─────────────────────────────────────────────────────────────────────────

    private String buildEmailTemplate(String headerTitle, String bodyHtml, String ctaText) {
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
                """.formatted(headerTitle, bodyHtml, LOGIN_URL, ctaText);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void sendEmail(String toEmail, String subject, String htmlContent) {
        if (resendApiKey == null || resendApiKey.isBlank()) {
            throw new BadRequestException(
                    "Chưa cấu hình RESEND_API_KEY. Vui lòng thêm vào .env.local rồi khởi động lại backend.");
        }

        try {
            Resend resend = new Resend(resendApiKey);

            CreateEmailOptions params = CreateEmailOptions.builder()
                    .from(fromName + " <" + fromEmail + ">")
                    .to(List.of(toEmail))
                    .subject(subject)
                    .html(htmlContent)
                    .build();

            resend.emails().send(params);
            log.info("Email sent via Resend to {}", toEmail);

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
