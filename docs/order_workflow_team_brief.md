# Tài Liệu Nghiệp Vụ Ngắn: Luồng Đơn Hàng Hiện Tại

## 1. Phạm vi tài liệu

Tài liệu này mô tả **đúng theo code đang chạy hiện tại**, dùng để team backend, frontend và vận hành nói cùng một ngôn ngữ khi xử lý đơn hàng.

Điểm rất quan trọng:

- Hệ thống hiện tại đang đi theo hướng **ưu tiên chi nhánh gần khách nhất nào đủ toàn bộ giỏ hàng**.
- Nếu không có chi nhánh nào đủ toàn bộ giỏ hàng, hệ thống sẽ **chọn chi nhánh giao gần khách làm điểm xử lý chính, rồi gom/bổ sung nội bộ từ các chi nhánh khác hoặc kho tổng về đó**.
- Vì vậy, trạng thái `AWAITING_REPLENISHMENT` là **trạng thái nghiệp vụ hợp lệ**, không phải lỗi kỹ thuật.

## 2. Cách đọc đúng mô hình

Có 2 lớp trạng thái:

- `Order` = đơn tổng mà khách nhìn thấy.
- `SubOrder` = phần đơn mà chi nhánh xử lý nội bộ.

Quy ước vận hành nên hiểu như sau:

- Khách hàng thao tác trên `Order`.
- Chi nhánh thao tác chủ yếu trên `SubOrder`.
- `Order` thường được đồng bộ từ trạng thái `SubOrder`.
- Các API admin chủ yếu dùng để giám sát, can thiệp ngoại lệ, hoặc đẩy nhanh bước bàn giao/giao hàng.

## 3. Bước không phải trạng thái DB nhưng cực quan trọng

| Bước | Ai thao tác | Điều kiện vào | Kết quả ra | API |
|---|---|---|---|---|
| Chuẩn bị đơn (`prepare`) | Khách hàng / frontend checkout | Có địa chỉ giao hàng, có giỏ hàng hợp lệ | Sinh `prepareToken`, tính chi nhánh xử lý, phí ship, voucher, khả năng thiếu hàng. **Chưa lưu đơn vào DB** | `POST /api/orders/prepare` |
| Xác nhận đặt đơn (`confirm`) | Khách hàng / frontend checkout | Có `prepareToken` còn hạn, quote chưa thay đổi | Tạo `Order` + `SubOrder`, trừ tồn, sinh link thanh toán nếu PayOS | `POST /api/orders/confirm` |
| Webhook thanh toán PayOS | Hệ thống PayOS | Đơn PayOS đã thanh toán thành công | `AWAITING_PAYMENT -> PROCESSING` hoặc `AWAITING_REPLENISHMENT` nếu vẫn còn thiếu hàng | `POST /api/webhooks/payos` |

## 4. Bảng trạng thái `Order` (đơn tổng)

| Trạng thái | Ai thao tác chính | Điều kiện vào | Điều kiện ra | API tương ứng |
|---|---|---|---|---|
| `PENDING` | Admin / nội bộ / dữ liệu legacy | Trạng thái chờ xác nhận còn tồn tại trong enum và rule chuyển trạng thái, nhưng checkout hiện tại hầu như không tạo mới theo nhánh này | Sang `CONFIRMED`; hoặc có thể hủy | `PUT /api/admin/{id}/status`, `POST /api/orders/{id}/cancel` |
| `AWAITING_PAYMENT` | Khách hàng, PayOS | Đơn vừa `confirm`, chọn `PAYOS`, và phần đơn không bị thiếu hàng tại thời điểm tạo đơn | Thanh toán thành công để sang `PROCESSING`; hoặc nếu sau thanh toán phát hiện phần thiếu thì sang `AWAITING_REPLENISHMENT`; hoặc hủy trước khi giao | `POST /api/orders/confirm`, `POST /api/webhooks/payos`, `POST /api/orders/{id}/cancel`, `GET /api/orders/{orderId}/payment-link` |
| `AWAITING_REPLENISHMENT` | Chi nhánh, admin, hệ thống điều chuyển | Đơn vừa `confirm` nhưng có ít nhất một item thiếu hàng; hoặc PayOS thanh toán xong nhưng sub-order vẫn thiếu hàng | Khi hàng được bù đủ cho phần đơn thì sang `PROCESSING`; có thể hủy nếu chưa giao | `POST /api/orders/confirm`, `POST /api/webhooks/payos`, `POST /api/admin/{id}/request-replenishment`, `POST /api/branch/orders/{orderId}/request-replenishment`, `POST /api/orders/{id}/cancel`, `GET /api/admin/backorders` |
| `CONFIRMED` | Admin / nội bộ | Trạng thái chuyển tiếp nội bộ, được code cho phép nhưng không phải điểm rơi chính của luồng checkout hiện tại | Sang `PROCESSING` | `PUT /api/admin/{id}/status` |
| `PROCESSING` | Chi nhánh xử lý chính | Đơn COD sau `confirm` và đủ hàng; hoặc đơn PayOS đã thanh toán xong; hoặc đơn thiếu hàng đã được bổ sung đủ | Khi chi nhánh soạn xong thì sang `READY_FOR_PICKUP`; có thể hủy nếu chưa giao | `POST /api/orders/confirm`, `POST /api/webhooks/payos`, `PUT /api/branch/orders/{orderId}/status`, `PUT /api/admin/{id}/status`, `POST /api/orders/{id}/cancel` |
| `READY_FOR_PICKUP` | Chi nhánh / admin | Chi nhánh đã xử lý xong, sẵn sàng bàn giao vận chuyển | Tạo phiếu bàn giao để sang `SHIPPING`, hoặc admin ép duyệt đóng gói và chuyển giao | `PUT /api/branch/orders/{orderId}/status`, `POST /api/branch/handovers`, `PUT /api/admin/{id}/approve-packed-and-ship` |
| `SHIPPING` | Chi nhánh, admin, khách hàng | Đã tạo bàn giao hoặc admin ép chuyển qua giao hàng | Khách xác nhận nhận hàng để sang `RECEIVED`; hoặc có thể sang `RETURNED` | `POST /api/branch/handovers`, `PUT /api/admin/{id}/approve-packed-and-ship`, `POST /api/v1/orders/{id}/confirm-received`, `PUT /api/branch/orders/{orderId}/status`, `PUT /api/admin/{id}/status` |
| `RECEIVED` | Khách hàng là tác nhân chuẩn; chi nhánh chỉ xử lý ngoại lệ | Khách xác nhận đã nhận hàng khi đơn đang `SHIPPING`; hoặc chi nhánh xác nhận tay cho sub-order sau khi quá hạn giao | Sang `COMPLETED` | `POST /api/v1/orders/{id}/confirm-received`, `PUT /api/branch/orders/{orderId}/status`, `PUT /api/admin/{id}/status` |
| `COMPLETED` | Hệ thống / admin | Tất cả phần đơn đã ở `COMPLETED`, hoặc đơn `RECEIVED` được hoàn tất tiếp | Trạng thái đóng, không đi tiếp | `PUT /api/admin/{id}/status`, đồng bộ nội bộ từ sub-order |
| `CANCELLED` | Khách hàng, admin | Đơn chưa đóng và chưa ở `SHIPPING` | Trạng thái đóng, không đi tiếp | `POST /api/orders/{id}/cancel`, `PUT /api/admin/{id}/status` |
| `RETURNED` | Chi nhánh / admin | Đơn đang `SHIPPING` và phát sinh trả hàng | Trạng thái đóng, không đi tiếp | `PUT /api/branch/orders/{orderId}/status`, `PUT /api/admin/{id}/status` |

### Ghi chú cần hiểu đúng ở mức `Order`

- `Order` không phải lúc nào cũng được con người thao tác trực tiếp từng bước.
- Trong nhiều trường hợp, `Order` chỉ là ảnh chiếu từ trạng thái của các `SubOrder`.
- Nếu tất cả `SubOrder` active đều `RECEIVED|COMPLETED` thì `Order` lên `RECEIVED`.
- Nếu tất cả `SubOrder` active đều `COMPLETED` thì `Order` lên `COMPLETED`.
- Nếu tất cả `SubOrder` đều hủy thì `Order` thành `CANCELLED`.

## 5. Bảng trạng thái `SubOrder` (phần đơn chi nhánh)

| Trạng thái | Ai thao tác chính | Điều kiện vào | Điều kiện ra | API tương ứng |
|---|---|---|---|---|
| `PENDING` | Admin / nội bộ / dữ liệu legacy | Trạng thái chờ xác nhận còn tồn tại trong enum và rule chuyển trạng thái, nhưng không phải trạng thái tạo ra chính từ flow checkout hiện tại | Sang `CONFIRMED`; hoặc có thể hủy | `PUT /api/branch/orders/{orderId}/status`, `PUT /api/admin/{id}/status` |
| `AWAITING_PAYMENT` | Hệ thống, PayOS | Đơn PayOS vừa tạo và phần đơn không thiếu hàng | Thanh toán xong để sang `PROCESSING`; có thể bị hủy trước khi giao | `POST /api/orders/confirm`, `POST /api/webhooks/payos` |
| `AWAITING_REPLENISHMENT` | Chi nhánh, admin, hệ thống điều chuyển | Phần đơn còn `missingQuantity > 0` | Khi bổ sung đủ thì sang `PROCESSING` | `POST /api/orders/confirm`, `POST /api/webhooks/payos`, `POST /api/branch/orders/{orderId}/request-replenishment`, `POST /api/admin/{id}/request-replenishment` |
| `CONFIRMED` | Admin / nội bộ | Trạng thái trung gian do hệ thống cho phép | Sang `PROCESSING` | `PUT /api/branch/orders/{orderId}/status`, `PUT /api/admin/{id}/status` |
| `PROCESSING` | Chi nhánh | Đã đủ hàng và đang soạn | Sang `READY_FOR_PICKUP` | `PUT /api/branch/orders/{orderId}/status` |
| `READY_FOR_PICKUP` | Chi nhánh | Soạn xong, chờ bàn giao | Tạo handover để sang `SHIPPING` | `PUT /api/branch/orders/{orderId}/status`, `POST /api/branch/handovers` |
| `SHIPPING` | Chi nhánh / admin | Đã bàn giao cho vận chuyển hoặc admin ép ship | Sang `RECEIVED` hoặc `RETURNED` | `POST /api/branch/handovers`, `PUT /api/branch/orders/{orderId}/status`, `PUT /api/admin/{id}/approve-packed-and-ship` |
| `RECEIVED` | Khách là chuẩn; chi nhánh xử lý tay khi quá hạn | Khách xác nhận nhận hàng ở đơn tổng; hoặc chi nhánh xác nhận tay sau ngưỡng quá hạn | Sang `COMPLETED` | `POST /api/v1/orders/{id}/confirm-received`, `PUT /api/branch/orders/{orderId}/status` |
| `COMPLETED` | Hệ thống / chi nhánh / admin | Phần đơn đã nhận hàng và hoàn tất | Trạng thái đóng | `PUT /api/branch/orders/{orderId}/status`, `PUT /api/admin/{id}/status` |
| `CANCELLED` | Chi nhánh / admin / đồng bộ khi khách hủy đơn | Phần đơn chưa đóng và chưa ở giao xong | Trạng thái đóng, đồng thời nhả tồn đã giữ | `PUT /api/branch/orders/{orderId}/status`, `PUT /api/admin/{id}/status`, `POST /api/orders/{id}/cancel` |
| `RETURNED` | Chi nhánh / admin | Phần đơn đang giao nhưng bị hoàn | Trạng thái đóng | `PUT /api/branch/orders/{orderId}/status`, `PUT /api/admin/{id}/status` |

### Ghi chú cần hiểu đúng ở mức `SubOrder`

- `SubOrder` là đơn vị thao tác thực tế của chi nhánh.
- Chi nhánh không nên coi `AWAITING_REPLENISHMENT` là “kẹt hệ thống”; đây là hàng thiếu đang chờ điều chuyển hoặc nhập thêm.
- Từ `PROCESSING`, code hiện tại chỉ cho đi sang `READY_FOR_PICKUP`.
- Từ `READY_FOR_PICKUP`, bước chuẩn là tạo handover để sang `SHIPPING`.
- Với `SHIPPING`, chi nhánh không được nhảy thẳng sang `COMPLETED`; phải qua `RECEIVED`.
- Xác nhận tay `RECEIVED` ở chi nhánh chỉ nên coi là đường ngoại lệ khi đơn giao quá hạn, không phải thao tác chuẩn hằng ngày.

## 6. Luồng vận hành ngắn gọn cho team

### Luồng chuẩn đủ hàng

`prepare -> confirm -> PROCESSING -> READY_FOR_PICKUP -> SHIPPING -> RECEIVED -> COMPLETED`

### Luồng thiếu hàng nhưng vẫn chốt đơn

`prepare -> confirm -> AWAITING_REPLENISHMENT -> PROCESSING -> READY_FOR_PICKUP -> SHIPPING -> RECEIVED -> COMPLETED`

### Luồng PayOS

`prepare -> confirm -> AWAITING_PAYMENT -> webhook PayOS -> PROCESSING hoặc AWAITING_REPLENISHMENT`

### Luồng hủy

- Khách được hủy khi đơn **chưa giao**.
- Khi hủy, hệ thống nhả tồn đã giữ và hoàn lại quyền dùng voucher nếu cần.
- Đơn đang `SHIPPING` thì không cho khách hủy.

## 7. Cách phân vai tối ưu để team làm việc ít vênh nhau nhất

### Khách hàng

- Chỉ nên thấy các cột mốc lớn: chờ thanh toán, chờ xử lý, đang giao, đã nhận, hoàn tất.
- Không cần expose quá sâu logic `SubOrder`.

### Chi nhánh

- Là owner chính của luồng vận hành sau khi đơn đã tạo.
- Theo dõi `SubOrder` theo các mốc: `AWAITING_REPLENISHMENT -> PROCESSING -> READY_FOR_PICKUP -> SHIPPING`.
- Khi thiếu hàng, ưu tiên tạo/yêu cầu replenishment thay vì cố đổi tay trạng thái.

### Admin

- Chủ yếu giám sát toàn cục, xử lý ngoại lệ, theo dõi backorder, hỗ trợ approve ship khi cần.
- Không nên biến admin thành người thao tác thay chi nhánh cho các bước thường ngày, trừ khi có nhu cầu điều phối thực sự.

## 8. Kết luận nghiệp vụ nên chốt với team

Nếu cần một câu để cả team cùng hiểu, thì nên chốt như sau:

> Hệ thống hiện tại ưu tiên "chi nhánh gần khách nhất nhưng vẫn đủ toàn bộ giỏ hàng"; nếu không có chi nhánh nào đủ trọn bộ, hệ thống chọn một chi nhánh giao gần khách làm điểm gom, rồi kéo phần thiếu từ nguồn nội bộ về để giao một đầu cho khách.

Từ câu chốt này, cách phát triển tối ưu nhất ở giai đoạn hiện tại là:

- Giữ `Order` đơn giản cho khách nhìn.
- Dồn nghiệp vụ vận hành vào `SubOrder`.
- Xem `AWAITING_REPLENISHMENT` là trạng thái chuẩn của thiếu hàng.
- Xem handover là cổng chính để chuyển từ chuẩn bị sang giao hàng.
- Chỉ mở rộng sang chia đơn nhiều chi nhánh thực sự khi business thật sự cần giao tách nhiều kiện cho khách.
