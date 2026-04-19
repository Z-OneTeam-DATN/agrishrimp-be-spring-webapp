package com.zone.agri.service;

import com.zone.agri.entity.PurchaseRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${sendgrid.api-key:}")
    private String sendGridApiKey;

    @Value("${mail.from:${spring.mail.username:noreply@agrishrimp.vn}}")
    private String fromEmail;

    @Value("${mail.from-name:AgriShrimp}")
    private String fromName;

    @Value("${sendgrid.from-email:noreply@agrishrimp.vn}")
    private String sendGridFromEmail;

    @Value("${sendgrid.from-name:${mail.from-name:AgriShrimp}}")
    private String sendGridFromName;

    private SendGrid getSendGrid() {
        return new SendGrid(sendGridApiKey);
    }

    public void sendAccountInfo(String toEmail, String name, String password) {
        String subject = "[AgriShrimp] Thông tin tài khoản";
        String htmlContent = """
                    <div style="font-family: Arial, sans-serif; padding: 20px; border: 1px solid #ddd;">
                        <h2 style="color: #059669;">Chào mừng %s đến với AgriShrimp!</h2>
                        <p>Tài khoản của bạn đã được khởi tạo thành công. Dưới đây là thông tin đăng nhập:</p>
                        <hr/>
                        <p><strong>Email đăng nhập:</strong> %s</p>
                        <p><strong>Mật khẩu:</strong> <span style="background: #eee; padding: 5px 10px; font-weight: bold;">%s</span></p>
                        <hr/>
                        <p>Vui lòng đăng nhập và đổi mật khẩu ngay trong lần đầu tiên.</p>
                        <p>Trân trọng,<br/>Đội ngũ AgriShrimp</p>
                    </div>
                """
                .formatted(name, toEmail, password);

        sendEmail(toEmail, subject, htmlContent);
    }

    // Thêm hàm gửi email CẢNH CÁO uy tín
    public void sendWarningEmail(String toEmail, String name, double reputationScore) {
        String subject = "[AgriShrimp] Cảnh báo: Tỉ lệ nhận hàng thấp";
        String htmlContent = """
                    <div style="font-family: Arial, sans-serif; padding: 20px; border: 1px solid #ff9800;">
                        <h2 style="color: #e65100;">Cảnh báo tài khoản, %s!</h2>
                        <p>Hệ thống AgriShrimp ghi nhận tỉ lệ nhận hàng thành công của bạn hiện đang ở mức <strong>%s%%</strong>.</p>
                        <p>Theo quy định của chúng tôi, nếu tỉ lệ này giảm xuống dưới 30%%, hệ thống sẽ tự động tạm khóa tài khoản của bạn để đảm bảo quyền lợi cho các đối tác vận chuyển và nhà bán hàng.</p>
                        <p>Vui lòng chú ý nhận hàng cho các đơn hàng tiếp theo để duy trì trạng thái hoạt động của tài khoản.</p>
                        <br/>
                        <p>Trân trọng,<br/>Đội ngũ AgriShrimp</p>
                    </div>
                """
                .formatted(name, String.format("%.2f", reputationScore));

        sendEmail(toEmail, subject, htmlContent);
    }

    // Thêm hàm gửi email KHÓA TÀI KHOẢN
    public void sendAccountLockedEmail(String toEmail, String name, double reputationScore) {
        String subject = "[AgriShrimp] Thông báo: Tài khoản của bạn đã bị tạm khóa";
        String htmlContent = """
                    <div style="font-family: Arial, sans-serif; padding: 20px; border: 1px solid #f44336;">
                        <h2 style="color: #d32f2f;">Thông báo Khóa tài khoản, %s!</h2>
                        <p>Hệ thống AgriShrimp thông báo: Tài khoản của bạn đã bị <strong>tạm khóa tự động</strong>.</p>
                        <p><strong>Lý do:</strong> Tỉ lệ nhận hàng thành công của bạn đã giảm xuống mức <strong>%s%%</strong> (Thấp hơn mức quy định tối thiểu là 30%%).</p>
                        <p>Trong thời gian bị khóa, bạn sẽ không thể thực hiện các giao dịch mua hàng mới.</p>
                        <p>Nếu bạn cho rằng đây là sự nhầm lẫn, vui lòng liên hệ với bộ phận CSKH để được hỗ trợ khôi phục tài khoản.</p>
                        <br/>
                        <p>Trân trọng,<br/>Đội ngũ AgriShrimp</p>
                    </div>
                """
                .formatted(name, String.format("%.2f", reputationScore));

        sendEmail(toEmail, subject, htmlContent);
    }

    public void sendPurchaseRequestToSupplier(PurchaseRequest purchaseRequest) {
        if (purchaseRequest == null || purchaseRequest.getSupplier() == null) {
            throw new RuntimeException("KhĂ´ng cĂ³ thĂ´ng tin nhĂ  cung cáº¥p Ä‘á»ƒ gá»­i email.");
        }

        String supplierEmail = purchaseRequest.getSupplier().getEmail();
        String supplierName = purchaseRequest.getSupplier().getName();
        String branchName = purchaseRequest.getBranch() != null ? purchaseRequest.getBranch().getName() : "";
        String requestedBy = purchaseRequest.getCreatedBy() != null ? purchaseRequest.getCreatedBy().getFullName() : "Hệ thống";

        String rows = "";
        if (purchaseRequest.getItems() != null) {
            rows = purchaseRequest.getItems().stream()
                    .map(item -> """
                            <tr>
                                <td style="padding:8px;border:1px solid #e5e7eb;">%s</td>
                                <td style="padding:8px;border:1px solid #e5e7eb;">%s</td>
                                <td style="padding:8px;border:1px solid #e5e7eb;text-align:right;">%s</td>
                                <td style="padding:8px;border:1px solid #e5e7eb;text-align:right;">%s</td>
                                <td style="padding:8px;border:1px solid #e5e7eb;">%s</td>
                            </tr>
                            """.formatted(
                            item.getProductVariant() != null ? item.getProductVariant().getSku() : "",
                            item.getProductVariant() != null && item.getProductVariant().getProduct() != null
                                    ? item.getProductVariant().getProduct().getName()
                                    : "",
                            item.getRequestedQty() != null ? item.getRequestedQty() : 0,
                            formatCurrency(item.getUnitPrice()),
                            item.getNote() != null ? item.getNote() : ""
                    ))
                    .reduce("", String::concat);
        }

        String subject = "[AgriShrimp] Phiếu yêu cầu mua " + purchaseRequest.getCode();
        String htmlContent = """
                <div style="font-family:Arial,sans-serif;padding:20px;border:1px solid #e5e7eb;color:#0f172a;">
                    <h2 style="color:#1d4ed8;margin-bottom:8px;">Phiếu yêu cầu mua gửi nhà cung cấp</h2>
                    <p>Kính gửi <strong>%s</strong>,</p>
                    <p>AgriShrimp gửi phiếu yêu cầu mua với thông tin như sau:</p>
                    <ul style="padding-left:18px;line-height:1.7;">
                        <li><strong>Mã phiếu:</strong> %s</li>
                        <li><strong>Kho nhận:</strong> %s</li>
                        <li><strong>Người lập:</strong> %s</li>
                        <li><strong>Ngày dự kiến:</strong> %s</li>
                    </ul>
                    <table style="width:100%%;border-collapse:collapse;margin-top:16px;font-size:14px;">
                        <thead>
                            <tr style="background:#eff6ff;">
                                <th style="padding:8px;border:1px solid #dbeafe;text-align:left;">SKU</th>
                                <th style="padding:8px;border:1px solid #dbeafe;text-align:left;">Sản phẩm</th>
                                <th style="padding:8px;border:1px solid #dbeafe;text-align:right;">Số lượng</th>
                                <th style="padding:8px;border:1px solid #dbeafe;text-align:right;">Đơn giá dự kiến</th>
                                <th style="padding:8px;border:1px solid #dbeafe;text-align:left;">Ghi chú</th>
                            </tr>
                        </thead>
                        <tbody>%s</tbody>
                    </table>
                    <p style="margin-top:16px;"><strong>Tổng giá trị dự kiến:</strong> %s</p>
                    <p style="margin-top:16px;">Vui lòng phản hồi lại email này để xác nhận tiến độ cung ứng hàng hóa.</p>
                    <p>Trân trọng,<br/>Đội ngũ AgriShrimp</p>
                </div>
                """.formatted(
                supplierName,
                purchaseRequest.getCode(),
                branchName,
                requestedBy,
                purchaseRequest.getExpectedDeliveryDate() != null ? purchaseRequest.getExpectedDeliveryDate() : "Chưa xác định",
                rows,
                formatCurrency(purchaseRequest.getTotalAmount())
        );

        sendEmail(supplierEmail, subject, htmlContent);
    }

    private String formatCurrency(BigDecimal amount) {
        BigDecimal safeAmount = amount != null ? amount : BigDecimal.ZERO;
        return safeAmount.toPlainString() + " VND";
    }

    private void sendEmail(String toEmail, String subject, String htmlContent) {
        try {
            sendBySmtp(toEmail, subject, htmlContent);
            log.info("Email sent via SMTP to {}", toEmail);
        } catch (Exception smtpEx) {
            log.error("SMTP send failed for {}: {}", toEmail, smtpEx.getMessage());

            if (canFallbackToSendGrid()) {
                try {
                    sendBySendGrid(toEmail, subject, htmlContent);
                    log.info("Email sent via SendGrid fallback to {}", toEmail);
                    return;
                } catch (Exception sendGridEx) {
                    log.error("SendGrid fallback failed for {}: {}", toEmail, sendGridEx.getMessage());
                }
            }

            throw new RuntimeException("Lỗi gửi email tới " + toEmail, smtpEx);
        }
    }

    private void sendBySmtp(String toEmail, String subject, String htmlContent) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
        helper.setFrom(fromEmail, fromName);
        helper.setTo(toEmail);
        helper.setSubject(subject);
        helper.setText(htmlContent, true);
        mailSender.send(message);
    }

    private boolean canFallbackToSendGrid() {
        return sendGridApiKey != null && sendGridApiKey.startsWith("SG.");
    }

    private void sendBySendGrid(String toEmail, String subject, String htmlContent) throws Exception {
        Email from = new Email(sendGridFromEmail, sendGridFromName);
        Email to = new Email(toEmail);
        Content content = new Content("text/html", htmlContent);
        Mail mail = new Mail(from, subject, to, content);

        SendGrid sg = getSendGrid();
        Request request = new Request();
        request.setMethod(Method.POST);
        request.setEndpoint("mail/send");
        request.setBody(mail.build());

        Response response = sg.api(request);
        if (response.getStatusCode() < 200 || response.getStatusCode() >= 300) {
            throw new RuntimeException("SendGrid status=" + response.getStatusCode() + ", body=" + response.getBody());
        }
    }
}
