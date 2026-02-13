package com.zone.agri.service;


import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    public void sendAccountInfo(String toEmail, String name, String password) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(toEmail);
            helper.setSubject("[AgriShrimp] Thông tin tài khoản khách hàng");

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
            """.formatted(name, toEmail, password);

            helper.setText(htmlContent, true); // true = html
            mailSender.send(message);

        } catch (MessagingException e) {
            e.printStackTrace();
            throw new RuntimeException("Lỗi gửi email: " + e.getMessage());
        }
    }
}