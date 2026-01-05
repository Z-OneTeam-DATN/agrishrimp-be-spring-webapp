package com.agrishrimp.agrishrimpbe.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender javaMailSender;

    public void sendResetPasswordEmail(String toEmail, String resetToken) {
        // Link frontend (ví dụ chạy localhost port 3000)
        String resetLink = "http://localhost:3000/reset-password?token=" + resetToken;

        String subject = "AgriShrimp - Yêu cầu đặt lại mật khẩu";

        // Nội dung Email (HTML)
        String content = "<p>Xin chào,</p>"
                + "<p>Bạn vừa yêu cầu đặt lại mật khẩu cho tài khoản AgriShrimp.</p>"
                + "<p>Vui lòng nhấn vào link bên dưới để thiết lập mật khẩu mới:</p>"
                + "<p><a href=\"" + resetLink + "\">Đặt lại mật khẩu ngay</a></p>"
                + "<br>"
                + "<p>Nếu bạn không yêu cầu, vui lòng bỏ qua email này.</p>";

        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, StandardCharsets.UTF_8.name());

            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(content, true);
            helper.setFrom("AgriShrimp Support <hotro@agrishrimp.com>");

            javaMailSender.send(message);
            log.info("Email reset password đã gửi thành công tới: {}", toEmail);

        } catch (MessagingException e) {
            log.error("Lỗi khi gửi email: {}", e.getMessage());
            throw new RuntimeException("Hệ thống gặp lỗi khi gửi Email. Vui lòng thử lại sau.", e);
        }
    }
}