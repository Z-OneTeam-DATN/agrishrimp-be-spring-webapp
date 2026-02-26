# Tài liệu API: Đặt hàng & Thanh toán

> **Base URL (dev):** `http://localhost:8080`
> **Xác thực:** Bearer Token (JWT) — trừ webhook
> **Content-Type:** `application/json`

---

## Mục lục

1. [Tổng quan flow](#1-tổng-quan-flow)
2. [Bước 1 — Chuẩn bị đơn hàng (Prepare)](#2-bước-1--chuẩn-bị-đơn-hàng)
3. [Bước 2 — Xác nhận đơn hàng (Confirm)](#3-bước-2--xác-nhận-đơn-hàng)
4. [Thanh toán payOS](#4-thanh-toán-payos)
5. [Lấy lại link thanh toán](#5-lấy-lại-link-thanh-toán)
6. [Xử lý lỗi & Edge cases](#6-xử-lý-lỗi--edge-cases)
7. [Sơ đồ luồng đầy đủ](#7-sơ-đồ-luồng-đầy-đủ)

---

## 1. Tổng quan flow

Đặt hàng gồm **2 bước bắt buộc** để tránh race condition tồn kho:

```
[FE] POST /api/orders/prepare   →  Nhận prepareToken + bản nháp phân bổ
[FE] POST /api/orders/confirm   →  Xác nhận, trừ kho, tạo đơn chính thức
```

- **Prepare** không ghi DB, chỉ tính toán và lưu bản nháp vào Redis (TTL 30 phút).
- **Confirm** mới thực sự tạo đơn, trừ kho (có pessimistic lock).
- Nếu `paymentMethod = PAYOS`, Confirm trả về `checkoutUrl` để redirect user sang trang thanh toán.

### Phương thức thanh toán hỗ trợ

| Giá trị | Mô tả |
|---------|-------|
| `COD` | Thanh toán khi nhận hàng (mặc định nếu không gửi) |
| `CASH` | Tiền mặt |
| `TRANSFER` | Chuyển khoản thủ công |
| `PAYOS` | Thanh toán online qua payOS (QR / thẻ) |

---

## 2. Bước 1 — Chuẩn bị đơn hàng

### `POST /api/orders/prepare`

**Auth:** Bearer Token bắt buộc.

#### Request Body

```json
{
  "userLat": 10.0341,
  "userLng": 105.7904,
  "deliveryAddress": "123 Nguyễn Văn Cừ, Ninh Kiều, Cần Thơ",
  "deliveryDistrictId": 1452,
  "deliveryWardCode": "550113",
  "cart": [
    { "productVariantId": 12, "quantity": 2 },
    { "productVariantId": 37, "quantity": 1 }
  ]
}
```

| Field | Type | Bắt buộc | Mô tả |
|-------|------|-----------|-------|
| `userLat` | number | Không | Vĩ độ GPS của user. Nếu thiếu → fallback Cần Thơ (10.0341) |
| `userLng` | number | Không | Kinh độ GPS của user. Nếu thiếu → fallback Cần Thơ (105.7904) |
| `deliveryAddress` | string | **Có** | Địa chỉ giao hàng đầy đủ (dạng text) |
| `deliveryDistrictId` | integer | **Có** | District ID theo hệ thống GHN (dùng để tính phí ship) |
| `deliveryWardCode` | string | **Có** | Ward Code theo hệ thống GHN |
| `cart` | array | **Có** | Danh sách sản phẩm |
| `cart[].productVariantId` | long | **Có** | ID của variant sản phẩm |
| `cart[].quantity` | integer | **Có** | Số lượng (≥ 1) |

> **Lưu ý về `deliveryDistrictId` / `deliveryWardCode`:** Lấy từ API GHN
> - Danh sách tỉnh/thành: `GET https://dev-online-gateway.ghn.vn/shiip/public-api/master-data/province`
> - Danh sách quận/huyện: `GET .../district?province_id=...`
> - Danh sách phường/xã: `GET .../ward?district_id=...`

#### Response `200 OK`

```json
{
  "prepareToken": "a3f1c2d4-...",
  "canFulfill": true,
  "totalSubtotal": 350000,
  "totalShippingFee": 45000,
  "totalAmount": 395000,
  "subOrders": [
    {
      "branchId": 1,
      "branchName": "Chi nhánh Ninh Kiều",
      "branchAddress": "45 Hai Bà Trưng, Cần Thơ",
      "distanceKm": 3.2,
      "durationMinutes": 12.0,
      "items": [
        {
          "productVariantId": 12,
          "variantName": "Thức ăn tôm loại A - 1kg",
          "variantSku": "TTA-1KG",
          "quantity": 2,
          "unitPrice": 120000,
          "subtotal": 240000
        },
        {
          "productVariantId": 37,
          "variantName": "Khoáng chất Premium - 500g",
          "variantSku": "KCP-500G",
          "quantity": 1,
          "unitPrice": 110000,
          "subtotal": 110000
        }
      ],
      "subtotal": 350000,
      "shippingFee": 45000,
      "shippingEstimate": false,
      "estimatedDays": "1 - 2",
      "carrier": "GHN"
    }
  ],
  "outOfStockItems": []
}
```

| Field | Mô tả |
|-------|-------|
| `prepareToken` | Token dùng trong bước Confirm. **Hết hạn sau 30 phút.** |
| `canFulfill` | `true` nếu toàn bộ sản phẩm có thể đặt được. Nếu `false` → xem `outOfStockItems` |
| `subOrders` | Đơn hàng được chia theo chi nhánh (mỗi chi nhánh = 1 sub-order) |
| `subOrders[].shippingEstimate` | `true` = phí ship là ước tính (API GHN lỗi, fallback 30.000đ) |
| `outOfStockItems` | Danh sách sản phẩm hết hàng (xem bên dưới) |

**`outOfStockItems` — khi `canFulfill = false`:**

```json
{
  "outOfStockItems": [
    {
      "productVariantId": 55,
      "variantName": "Tôm giống size 10 - 1000 con",
      "variantSku": "TG-S10",
      "requestedQty": 5,
      "availableQty": 2
    }
  ]
}
```

> **FE nên làm gì khi `canFulfill = false`?**
> Hiển thị cảnh báo cho user biết sản phẩm nào không đủ hàng. User có thể chọn:
> - Bỏ sản phẩm đó khỏi giỏ và gọi `/prepare` lại
> - Giảm số lượng xuống `availableQty` và gọi `/prepare` lại
> - Hoặc vẫn confirm (backend sẽ báo lỗi `409 Conflict` khi hàng đã hết)

---

## 3. Bước 2 — Xác nhận đơn hàng

### `POST /api/orders/confirm`

**Auth:** Bearer Token bắt buộc.

#### Request Body

```json
{
  "prepareToken": "a3f1c2d4-...",
  "paymentMethod": "PAYOS",
  "note": "Giao buổi sáng trước 10h"
}
```

| Field | Type | Bắt buộc | Mô tả |
|-------|------|-----------|-------|
| `prepareToken` | string | **Có** | Token từ bước `/prepare` |
| `paymentMethod` | string | Không | `COD` / `CASH` / `TRANSFER` / `PAYOS`. Mặc định: `COD` |
| `note` | string | Không | Ghi chú cho đơn hàng |

#### Response `200 OK` — Không dùng payOS

```json
{
  "orderId": 42,
  "orderCode": "ORD1706789012345",
  "status": "PENDING",
  "totalAmount": 395000,
  "totalShippingFee": 45000,
  "checkoutUrl": null,
  "subOrders": [
    {
      "subOrderId": 7,
      "branchId": 1,
      "branchName": "Chi nhánh Ninh Kiều",
      "status": "PENDING",
      "subtotal": 350000,
      "shippingFee": 45000,
      "estimatedDays": "1 - 2",
      "carrier": "GHN"
    }
  ]
}
```

#### Response `200 OK` — Dùng payOS (`paymentMethod: "PAYOS"`)

```json
{
  "orderId": 42,
  "orderCode": "ORD1706789012345",
  "status": "PENDING",
  "totalAmount": 395000,
  "totalShippingFee": 45000,
  "checkoutUrl": "https://pay.payos.vn/web/abc123xyz",
  "subOrders": [ ... ]
}
```

> **Khi có `checkoutUrl`:** Redirect hoặc mở `checkoutUrl` trong browser/webview để user hoàn tất thanh toán.

---

## 4. Thanh toán payOS

### Luồng đầy đủ

```
FE                          BE                        payOS
│                           │                           │
│─ POST /orders/confirm ───►│                           │
│  paymentMethod: "PAYOS"   │── createPaymentLink ─────►│
│                           │◄─ checkoutUrl ────────────│
│◄── { checkoutUrl } ───────│                           │
│                           │                           │
│── redirect to checkoutUrl ──────────────────────────►│
│                           │                           │ user pays
│                           │◄─ POST /webhooks/payos ───│
│                           │   (signature verified)    │
│                           │   order.paymentStatus=PAID│
│◄── redirect to returnUrl ───────────────────────────►│
│   http://localhost:3000/order-success?orderId=42      │
```

### Return URL & Cancel URL

Sau khi user thanh toán (hoặc huỷ), payOS redirect browser về:

| Kết quả | URL redirect |
|---------|-------------|
| Thanh toán thành công | `http://localhost:3000/order-success` |
| User huỷ thanh toán | `http://localhost:3000/order-cancel` |

payOS có thể đính kèm query params vào URL, ví dụ:
```
http://localhost:3000/order-success?orderCode=42&status=PAID
```

> **FE cần lưu ý:** Redirect về `returnUrl` **không đồng nghĩa** với thanh toán thành công 100% vì đây là redirect browser phía client.
> Trạng thái thanh toán chính xác được BE cập nhật qua **webhook** (server-to-server, độc lập với browser).
> FE nên gọi thêm API kiểm tra trạng thái đơn hàng sau khi về trang success.

### Kiểm tra trạng thái thanh toán sau redirect

Sau khi về trang `order-success`, FE gọi API lấy thông tin đơn để hiển thị:

```
GET /api/orders/{orderId}        (nếu có endpoint này)
```

Hoặc poll ngắn (1–2 lần, cách nhau ~2 giây) để chờ webhook BE xử lý xong rồi hiển thị `paymentStatus: "PAID"`.

---

## 5. Lấy lại link thanh toán

Dùng khi user cần mở lại trang thanh toán (ví dụ: tắt app giữa chừng).

### `GET /api/orders/{orderId}/payment-link`

**Auth:** Bearer Token bắt buộc.

#### Response `200 OK`

```json
{
  "checkoutUrl": "https://pay.payos.vn/web/abc123xyz"
}
```

#### Response `404 Not Found`

```json
{
  "message": "Đơn hàng này không sử dụng thanh toán payOS"
}
```

---

## 6. Xử lý lỗi & Edge cases

### Các lỗi phổ biến

| HTTP | Tình huống | Message mẫu |
|------|-----------|-------------|
| `400` | `prepareToken` không hợp lệ hoặc hết hạn (30 phút) | `"Token đã hết hạn hoặc không hợp lệ..."` |
| `400` | Token không thuộc tài khoản đang đăng nhập | `"Token không thuộc về tài khoản này."` |
| `400` | Không tạo được link payOS (lỗi network / sai credentials) | `"Không thể tạo link thanh toán. Vui lòng thử lại."` |
| `404` | Sản phẩm không tồn tại trong giỏ | `"Một hoặc nhiều sản phẩm không tồn tại trong hệ thống"` |
| `404` | Không có chi nhánh nào trong khu vực | `"Không có chi nhánh nào trong khu vực của bạn..."` |
| `409` | Hàng vừa hết sau khi prepare (race condition) | `"Hàng vừa hết hoặc không đủ số lượng..."` |

### Cấu trúc response lỗi

```json
{
  "status": 400,
  "message": "Token đã hết hạn hoặc không hợp lệ. Vui lòng thực hiện lại bước chuẩn bị đơn."
}
```

### Xử lý timeout token (30 phút)

Nếu user ở trang checkout quá lâu, `prepareToken` hết hạn → `/confirm` trả `400`.

**FE nên:**
```
Nhận lỗi 400 từ /confirm
  → Kiểm tra message có chứa "Token đã hết hạn"
  → Thông báo user: "Phiên đặt hàng đã hết hạn, vui lòng thử lại"
  → Gọi lại /prepare với giỏ hàng hiện tại
```

### Xử lý 409 Conflict (hết hàng khi confirm)

```
Nhận lỗi 409 từ /confirm
  → Thông báo: "Rất tiếc, sản phẩm vừa hết hàng"
  → Gọi lại /prepare để cập nhật bản nháp mới
  → Hiển thị lại trang checkout với thông tin mới
```

---

## 7. Sơ đồ luồng đầy đủ

### Flow COD / CASH / TRANSFER

```
Trang Giỏ hàng                    Trang Checkout             Trang Xác nhận
─────────────────                 ──────────────────         ──────────────
User chọn sản phẩm                User nhập địa chỉ
                                  chọn phương thức
                                  ↓
                           POST /api/orders/prepare
                           { cart, deliveryAddress,
                             deliveryDistrictId,
                             deliveryWardCode,
                             userLat, userLng }
                                  ↓
                           ← { prepareToken, subOrders,
                               totalAmount, canFulfill }
                                  ↓
                           Hiển thị preview:
                           - Đơn chia theo chi nhánh
                           - Phí ship từng chi nhánh
                           - Tổng tiền
                                  ↓
                           User bấm "Đặt hàng"
                                  ↓
                           POST /api/orders/confirm
                           { prepareToken,
                             paymentMethod: "COD",
                             note }
                                  ↓
                           ← { orderId, orderCode,         Hiển thị trang thành công
                               status: "PENDING",     →    "Đơn #ORD... đã được đặt!"
                               checkoutUrl: null }
```

### Flow PAYOS

```
Trang Checkout                                    payOS               Trang Kết quả
──────────────                                    ─────               ─────────────
...  (giống flow COD đến bước POST /confirm)

POST /api/orders/confirm
{ prepareToken,
  paymentMethod: "PAYOS" }
       ↓
← { orderId: 42,
    checkoutUrl: "https://pay.payos.vn/..." }
       ↓
window.location.href = checkoutUrl
                                                User quét QR / nhập thẻ
                                                       ↓
                                               payOS xử lý thanh toán
                                                       ↓
                                  ┌── POST /api/webhooks/payos (server→server)
                                  │   BE xác minh chữ ký, cập nhật
                                  │   order.paymentStatus = PAID
                                  └──────────────────────────────
                                                       ↓
                          payOS redirect browser → http://localhost:3000/order-success
                                                                   ↓
                                                        Hiển thị: "Thanh toán thành công!"
                                                        Gọi API kiểm tra đơn hàng
                                                        để confirm paymentStatus = PAID
```

---

## Tóm tắt nhanh cho dev

```javascript
// Bước 1: Prepare
const { prepareToken, canFulfill, subOrders, totalAmount } = await api.post('/api/orders/prepare', {
  userLat, userLng,
  deliveryAddress,
  deliveryDistrictId,
  deliveryWardCode,
  cart: [{ productVariantId: 12, quantity: 2 }]
});

if (!canFulfill) {
  // Hiển thị cảnh báo hết hàng
  showOutOfStockWarning(response.outOfStockItems);
  return;
}

// Hiển thị preview cho user...

// Bước 2: Confirm
const result = await api.post('/api/orders/confirm', {
  prepareToken,
  paymentMethod: 'PAYOS',  // hoặc 'COD'
  note: 'Giao buổi sáng'
});

if (result.checkoutUrl) {
  // PAYOS: redirect sang trang thanh toán
  window.location.href = result.checkoutUrl;
} else {
  // COD/CASH/TRANSFER: đơn đã xong
  router.push(`/order-success?orderId=${result.orderId}`);
}
```
