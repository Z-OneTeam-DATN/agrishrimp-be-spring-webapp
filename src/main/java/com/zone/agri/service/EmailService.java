package com.zone.agri.service;

import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    @Value("${sendgrid.api-key}")
    private String sendGridApiKey;

    private SendGrid getSendGrid() {
        return new SendGrid(sendGridApiKey);
    }

    public void sendAccountInfo(String toEmail, String name, String password) {
        try {
            Email from = new Email("noreply@agrishrimp.vn");
            String subject = "[AgriShrimp] Thông tin tài khoản khách hàng";
            Email to = new Email(toEmail);

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

            Content content = new Content("text/html", htmlContent);
            Mail mail = new Mail(from, subject, to, content);

            SendGrid sg = getSendGrid();
            Request request = new Request();
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());
            Response response = sg.api(request);

            if (response.getStatusCode() >= 200 && response.getStatusCode() < 300) {
                System.out.println("Email sent successfully to " + toEmail);
            } else {
                System.err.println("Failed to send email: " + response.getStatusCode() + " - " + response.getBody());
            }

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Lỗi gửi email: " + e.getMessage());
        }
    }

    // Thêm hàm gửi email CẢNH CÁO uy tín
    public void sendWarningEmail(String toEmail, String name, double reputationScore) {
        try {
            Email from = new Email("noreply@agrishrimp.vn");
            String subject = "[AgriShrimp] Cảnh báo: Tỉ lệ nhận hàng thấp";
            Email to = new Email(toEmail);

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

            Content content = new Content("text/html", htmlContent);
            Mail mail = new Mail(from, subject, to, content);

            SendGrid sg = getSendGrid();
            Request request = new Request();
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());
            Response response = sg.api(request);

            if (response.getStatusCode() >= 200 && response.getStatusCode() < 300) {
                System.out.println("Warning email sent successfully to " + toEmail);
            } else {
                System.err.println(
                        "Failed to send warning email: " + response.getStatusCode() + " - " + response.getBody());
            }

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Lỗi gửi email cảnh báo: " + e.getMessage());
        }
    }

    // Thêm hàm gửi email KHÓA TÀI KHOẢN
    public void sendAccountLockedEmail(String toEmail, String name, double reputationScore) {
        try {
            Email from = new Email("noreply@agrishrimp.vn");
            String subject = "[AgriShrimp] Thông báo: Tài khoản của bạn đã bị tạm khóa";
            Email to = new Email(toEmail);

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

            Content content = new Content("text/html", htmlContent);
            Mail mail = new Mail(from, subject, to, content);

            SendGrid sg = getSendGrid();
            Request request = new Request();
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());
            Response response = sg.api(request);

            if (response.getStatusCode() >= 200 && response.getStatusCode() < 300) {
                System.out.println("Account locked email sent successfully to " + toEmail);
            } else {
                System.err.println("Failed to send account locked email: " + response.getStatusCode() + " - "
                        + response.getBody());
            }

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Lỗi gửi email khóa tài khoản: " + e.getMessage());
        }
    }
}