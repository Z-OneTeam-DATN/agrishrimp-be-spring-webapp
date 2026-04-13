# Danh sách sản phẩm cần nhập DB — AI Knowledge Base

## Brands cần tạo trước

| Tên Brand |
|---|
| BIO PHARMA |
| VINA AQUA |
| AQUA PRO |
| VIETNAM AQUATIC |
| CLEAN POND |

---

## Categories cần tạo trước

> Tạo parent trước, sau đó tạo các category con với parent = `Thuốc & Chế phẩm thủy sản`

| Loại | Tên Category |
|---|---|
| Parent | Thuốc & Chế phẩm thủy sản |
| Con | Kháng sinh thủy sản |
| Con | Men vi sinh / Probiotic |
| Con | Hỗ trợ gan & Đường ruột |
| Con | Vitamin & Khoáng chất thủy sản |
| Con | Tăng cường miễn dịch tôm |
| Con | Hóa chất & Diệt khuẩn ao nuôi |
| Con | Vôi & Khoáng xử lý ao |

---

## Danh sách sản phẩm (20 sản phẩm)

### Kháng sinh thủy sản

| # | Tên sản phẩm | Hãng | Ghi chú |
|---|---|---|---|
| 1 | Florfenicol 10% Bột trộn thức ăn | BIO PHARMA | Ưu tiên hơn Oxytetracycline, trị Vibriosis/AHPND |
| 2 | Oxytetracycline HCl Bột trộn thức ăn | BIO PHARMA | Trị bệnh mang (BG), lưu ý nhiều Vibrio đã kháng |
| 3 | Doxycycline Hyclate Bột trộn thức ăn | BIO PHARMA | Dùng khi có kháng sinh đồ chỉ định |

### Men vi sinh / Probiotic

| # | Tên sản phẩm | Hãng | Ghi chú |
|---|---|---|---|
| 4 | Probiotic Bacillus spp. Đa chủng | VINA AQUA | Dùng được cho tất cả bệnh, liều 1–2 ppm/ngày |
| 5 | Probiotic Bacillus subtilis + B. licheniformis | AQUA PRO | Chuyên Vibriosis/AHPND, giai đoạn xử lý môi trường |
| 6 | Probiotic Bacillus subtilis + Lactobacillus | AQUA PRO | Chuyên WFS/EHP, ổn định đường ruột |

### Hỗ trợ gan & Đường ruột

| # | Tên sản phẩm | Hãng | Ghi chú |
|---|---|---|---|
| 7 | Sorbitol + Methionine Hỗ trợ gan tụy | VIETNAM AQUATIC | Dùng trong liệu trình kháng sinh Vibriosis/AHPND |
| 8 | Sorbitol + Choline + Methionine Phục hồi gan | VIETNAM AQUATIC | Dùng 10–14 ngày liên tục cho WFS/EHP |
| 9 | Men tiêu hóa Enzyme (Protease + Amylase) | AQUA PRO | Hỗ trợ hấp thu, giảm tải gan trong Vibriosis/AHPND |
| 10 | Men tiêu hóa Enzyme (Protease + Lipase + Amylase) | AQUA PRO | Bù đắp chức năng gan tụy bị EHP phá hủy |

### Vitamin & Khoáng chất thủy sản

| # | Tên sản phẩm | Hãng | Ghi chú |
|---|---|---|---|
| 11 | Vitamin C Ascorbyl Phosphate Dạng bền | VINA AQUA | Dùng cho WSSV, BG, Yellowhead — ổn định nhiệt |
| 12 | Vitamin B Complex + Vitamin E Thủy sản | VINA AQUA | Phục hồi tế bào gan tụy trong WFS/EHP |
| 13 | Khoáng tổng hợp Ca-Mg-K Hỗ trợ lột xác | VINA AQUA | Giai đoạn phục hồi sau kháng sinh, tái tạo mang |

### Tăng cường miễn dịch tôm

| # | Tên sản phẩm | Hãng | Ghi chú |
|---|---|---|---|
| 14 | Beta-Glucan β-1,3/1,6 Kích thích miễn dịch tôm | VIETNAM AQUATIC | Kích thích haemocyte chống WSSV/YHV, 0,1–0,2 g/kg |

### Hóa chất & Diệt khuẩn ao nuôi

| # | Tên sản phẩm | Hãng | Ghi chú |
|---|---|---|---|
| 15 | BKC Benzalkonium Chloride 50% Diệt khuẩn ao | CLEAN POND | Trị BG, liều 0,3–0,5 ppm, KHÔNG dùng > 1 ppm |
| 16 | EDTA Disodium Chelate kim loại nặng ao nuôi | CLEAN POND | WFS/EHP, 2–3 ppm chelate kim loại nặng |
| 17 | Zeolite Hấp thụ NH₃ Cải thiện chất lượng nước | CLEAN POND | Vibriosis/AHPND, 10–15 kg/1.000 m³ khi NH₃ cao |

### Vôi & Khoáng xử lý ao

| # | Tên sản phẩm | Hãng | Ghi chú |
|---|---|---|---|
| 18 | Vôi CaO Calcium Oxide Xử lý ao nuôi | CLEAN POND | Bất hoạt WSSV/YHV, 20 kg/1.000 m³, KHÔNG dùng khi tôm còn sống |
| 19 | Vôi Nông nghiệp CaCO₃ Cải tạo đáy ao | CLEAN POND | Phơi ao sau WSSV/Yellowhead, 1.000–2.000 kg/ha |
| 20 | Chlorine bột 70% Khử trùng nước thải ao | CLEAN POND | 30 ppm/48 giờ trước khi xả theo QCVN 02-19:2014 Điều 2.4 |

---

## Mapping sản phẩm theo bệnh

| Bệnh | Sản phẩm được chỉ định |
|---|---|
| **WSSV** (Đốm trắng) | #4 #11 #13 #14 #18 #19 #20 |
| **BACTERIAL_GROUP** (Vibriosis/AHPND) | #1 #2 #5 #7 #9 #11 #13 #17 |
| **BG** (Bệnh mang vi khuẩn) | #2 #3 #11 #13 #15 |
| **WSSV_BG** (WFS/Phân trắng) | #6 #8 #10 #12 #13 #16 |
| **Yellowhead** (Đầu vàng) | #11 #18 #19 #20 |
