# PROMPT CHO BE AI — HỆ THỐNG TÌM CHI NHÁNH GẦN NHẤT & TÁCH ĐƠN THÔNG MINH
> Stack: **Spring Boot 3.5.5 + Java 21 + MySQL + Redis**  
> Tương thích với FE: **Next.js 15 (App Router)**  
> Tuân thủ kiến trúc hiện tại trong `CLAUDE.md` và cấu trúc package `com.zone.agri`

---

## [CONTEXT — ĐỌC TRƯỚC KHI CODE]

Bạn là senior Java backend engineer đang làm việc trong dự án `agri-shrimp` (Spring Boot 3.5.5, Java 21).  
Nhiệm vụ là **thêm mới** hai nghiệp vụ vào hệ thống hiện có — không được phá vỡ code cũ:

1. **Tìm chi nhánh gần nhất** với người dùng (Spatial Filtering Funnel 3 tầng).
2. **Kiểm tra tồn kho theo `Inventory` entity hiện có** → tách đơn thông minh nếu thiếu hàng → tính phí ship từng đơn con.

### Quy tắc tồn kho (BẮT BUỘC TUÂN THỦ)
- Nguồn dữ liệu tồn kho **duy nhất và tin cậy** là bảng `inventories` thông qua entity `Inventory`.
- Mỗi bản ghi `Inventory` liên kết `branch_id` ↔ `product_variant_id` ↔ `quantity`.
- **KHÔNG** dùng `quantity` trong `ProductVariant` để kiểm tra tồn kho theo chi nhánh.
- Khi trừ hàng: dùng `SELECT ... FOR UPDATE` hoặc `@Lock(LockModeType.PESSIMISTIC_WRITE)` để tránh race condition.

### Nguyên tắc kiến trúc (theo CLAUDE.md)
- Thêm theo đúng thứ tự: `Entity → Repository → DTO → Service → Controller`
- Extend `BaseEntity` cho mọi entity mới.
- Dùng `@Transactional` cho mọi service method thay đổi dữ liệu.
- API key của bên thứ 3 (Geocoding, Distance Matrix, Shipping) chỉ lưu trong `application-dev.yml` / `application-stg.yml` — không hardcode.
- Giữ nguyên convention tên tiếng Việt trong permission/role.
- Tất cả exception phải dùng class có sẵn: `BadRequestException`, `NotFoundException`, `ConflictException`.

---

## [ENVIRONMENT VARIABLES — THÊM VÀO application-dev.yml]

```yaml
# Geocoding
geocoding:
  provider: trackasia          # trackasia | nominatim
  trackasia:
    api-key: ${TRACKASIA_API_KEY}
    url: https://api.trackasia.vn/api/geocode/search
  nominatim:
    url: https://nominatim.openstreetmap.org/search

# Distance Matrix / Routing
routing:
  provider: ors                # ors | trackasia
  ors:
    api-key: ${ORS_API_KEY}
    url: https://api.openrouteservice.org/v2/matrix/driving-car
  trackasia:
    api-key: ${TRACKASIA_ROUTING_KEY}

# Shipping
shipping:
  provider: ghn                # ghn | ghtk
  ghn:
    token: ${GHN_API_KEY}
    shop-id: ${GHN_SHOP_ID}
    url: https://dev-online-gateway.ghn.vn/shiip/public-api/v2

# Search config
location:
  default-radius-km: 15
  max-candidates: 5
  ip-geo-url: http://ip-api.com/json
```

---

## [ENTITY MỚI CẦN TẠO]

### 1. Entity `Order` (đơn cha)

```java
// Tạo tại: src/main/java/com/zone/agri/entity/Order.java
// Extend BaseEntity
// Fields:
//   - id: Long (hoặc UUID — theo convention hiện tại của dự án)
//   - userId: Long (FK → User)
//   - status: OrderStatus (enum: PENDING, CONFIRMED, SHIPPING, COMPLETED, CANCELLED)
//   - totalAmount: BigDecimal
//   - totalShippingFee: BigDecimal
//   - userLat: Double
//   - userLng: Double
//   - deliveryAddress: String
//   - note: String
//   - paymentMethod: PaymentMethod (enum: COD, ONLINE)
//   - subOrders: List<SubOrder> @OneToMany(mappedBy = "order", cascade = ALL)
```

### 2. Entity `SubOrder` (đơn con)

```java
// Fields:
//   - id: Long
//   - order: Order @ManyToOne
//   - branch: Branch @ManyToOne
//   - status: OrderStatus
//   - subtotal: BigDecimal
//   - shippingFee: BigDecimal
//   - estimatedDays: String
//   - carrier: String           -- "GHN", "GHTK"
//   - carrierOrderId: String    -- mã đơn bên đơn vị vận chuyển
//   - items: List<SubOrderItem> @OneToMany
```

### 3. Entity `SubOrderItem`

```java
// Fields:
//   - id: Long
//   - subOrder: SubOrder @ManyToOne
//   - productVariant: ProductVariant @ManyToOne
//   - quantity: Integer
//   - unitPrice: BigDecimal
```

### 4. Thêm fields vào entity `Branch` hiện có

```java
// Thêm vào Branch entity (nếu chưa có):
//   - lat: Double              -- vĩ độ (sau khi geocode)
//   - lng: Double              -- kinh độ
//   - districtId: Integer      -- mã quận/huyện cho GHN
//   - wardCode: String         -- mã phường/xã cho GHN
//   - geocodedAt: Instant      -- timestamp geocode gần nhất
```

---

## [REPOSITORY]

### `BranchRepository`

```java
// Thêm vào BranchRepository hiện có:

// Bounding Box query — dùng index trên (lat, lng), KHÔNG full table scan
@Query("""
    SELECT b FROM Branch b
    WHERE b.isActive = true
      AND b.lat BETWEEN :minLat AND :maxLat
      AND b.lng BETWEEN :minLng AND :maxLng
    """)
List<Branch> findBranchesInBoundingBox(
    @Param("minLat") double minLat,
    @Param("maxLat") double maxLat,
    @Param("minLng") double minLng,
    @Param("maxLng") double maxLng
);
```

### `InventoryRepository`

```java
// Thêm vào InventoryRepository hiện có:

// Query gộp tồn kho nhiều chi nhánh + nhiều variant — 1 lần duy nhất
@Query("""
    SELECT i FROM Inventory i
    WHERE i.branch.id IN :branchIds
      AND i.productVariant.id IN :variantIds
    """)
List<Inventory> findInventoryMatrix(
    @Param("branchIds") List<Long> branchIds,
    @Param("variantIds") List<Long> variantIds
);

// Dùng khi confirm đơn — lock để tránh race condition
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT i FROM Inventory i WHERE i.branch.id = :branchId AND i.productVariant.id = :variantId")
Optional<Inventory> findForUpdate(
    @Param("branchId") Long branchId,
    @Param("variantId") Long variantId
);
```

---

## [DTO — Tạo trong dto/order/ và dto/branch/]

### Request DTOs

```java
// dto/branch/FindNearestBranchRequest.java
// Fields: lat (Double), lng (Double), radiusKm (Integer, default 15), limit (Integer, default 5)

// dto/order/PrepareOrderRequest.java
// Fields:
//   - userLat: Double
//   - userLng: Double
//   - deliveryAddress: String @NotBlank
//   - deliveryDistrictId: Integer @NotNull  -- cho GHN
//   - deliveryWardCode: String @NotBlank    -- cho GHN
//   - cart: List<CartItemDto> @NotEmpty
//       CartItemDto: { productVariantId: Long, quantity: Integer (min=1) }

// dto/order/ConfirmOrderRequest.java
// Fields:
//   - prepareToken: String  -- token từ /prepare để validate lại
//   - userId: Long
//   - paymentMethod: PaymentMethod
//   - note: String
```

### Response DTOs

```java
// dto/branch/NearestBranchResponse.java
// Fields: id, name, addressText, phone, distanceKm, durationMinutes

// dto/order/PrepareOrderResponse.java
// Fields:
//   - canFulfill: Boolean
//   - subOrders: List<SubOrderDraftDto>
//       SubOrderDraftDto: { branchId, branchName, branchAddress, durationMinutes,
//                           items (List<OrderItemDto>), subtotal, shippingFee, estimatedDays, carrier }
//   - totalSubtotal, totalShippingFee, totalAmount
//   - outOfStockItems: List<OutOfStockItemDto>
//       OutOfStockItemDto: { productVariantId, variantName, requestedQty, availableQty }

// dto/order/ConfirmOrderResponse.java
// Fields: orderId, status, subOrders (List<SubOrderSummaryDto>), totalAmount, totalShippingFee
```

---

## [SERVICE LAYER]

### `LocationService` — Xử lý tọa độ người dùng

```java
// src/main/java/com/zone/agri/service/LocationService.java
// Method:
//   UserLocationDto resolveLocationFromIp(String ipAddress)
//   -- Gọi ip-api.com/json/{ip}
//   -- Parse JSON trả về { lat, lon, city }
//   -- Dùng làm fallback khi FE không gửi tọa độ
```

### `GeocodingService` — Geocode địa chỉ chi nhánh (chỉ chạy khi tạo/sửa Branch)

```java
// src/main/java/com/zone/agri/service/GeocodingService.java
//
// Interface GeocodingProvider:
//   Optional<CoordinateDto> geocode(String address)
//
// Implement 2 class:
//   - TrackAsiaGeocodingProvider  (dùng khi market=VN)
//   - NominatimGeocodingProvider  (dùng khi market=GLOBAL)
//
// GeocodingService inject provider theo config geocoding.provider
// Dùng RestTemplate hoặc WebClient để gọi HTTP
// Nếu geocode thất bại: throw BadRequestException("Không thể xác định tọa độ cho địa chỉ này")
```

### `BranchSearchService` — Tìm chi nhánh gần nhất (3 tầng)

```java
// src/main/java/com/zone/agri/service/BranchSearchService.java

// Tầng 1 — Bounding Box:
List<Branch> findCandidates(double userLat, double userLng, double radiusKm) {
    // Tính 4 ranh giới:
    // double latDelta = radiusKm / 111.0
    // double lngDelta = radiusKm / (111.0 * Math.cos(Math.toRadians(userLat)))
    // Gọi branchRepository.findBranchesInBoundingBox(...)
}

// Tầng 2 — Haversine sort:
List<BranchWithDistance> sortByHaversine(double userLat, double userLng, List<Branch> candidates, int limit) {
    // Công thức Haversine chuẩn, R = 6371 km
    // Sort tăng dần, lấy top `limit`
}

// Tầng 3 — Distance Matrix API:
List<BranchWithRealDistance> enrichWithRealDistance(double userLat, double userLng, List<BranchWithDistance> top5) {
    // Gọi RoutingProvider.getDistanceMatrix(origin, destinations)
    // 1 request duy nhất: 1 origin → 5 destinations
    // Sort theo duration (giây) tăng dần — KHÔNG sort theo km
}

// Public method kết hợp 3 tầng:
List<BranchWithRealDistance> findNearestBranches(double userLat, double userLng)
```

### `InventoryAllocationService` — Thuật toán Greedy tách đơn

```java
// src/main/java/com/zone/agri/service/InventoryAllocationService.java

// Step 1: Query gộp tồn kho 1 lần
Map<Long, Map<Long, Integer>> buildInventoryMatrix(List<Long> branchIds, List<Long> variantIds) {
    // Gọi inventoryRepository.findInventoryMatrix(branchIds, variantIds)
    // Chuyển thành Map<branchId, Map<variantId, quantity>>
    // Dùng để lookup O(1) trong thuật toán
}

// Step 2: Greedy allocation
AllocationResult allocate(List<CartItemDto> cart,
                          List<BranchWithRealDistance> branchesSortedByDistance,
                          Map<Long, Map<Long, Integer>> inventoryMatrix) {
    // remaining = new ArrayList<>(cart)
    // subOrders = []
    //
    // FOR EACH branch (gần → xa):
    //   IF remaining.isEmpty(): break
    //   allocated = [], stillRemaining = []
    //
    //   FOR EACH item in remaining:
    //     int stock = inventoryMatrix.get(branch.id).getOrDefault(item.variantId, 0)
    //
    //     IF stock >= item.quantity:
    //       allocated.add(item với branchId)
    //     ELSE IF stock > 0:
    //       allocated.add(item với quantity=stock, branchId)
    //       stillRemaining.add(item với quantity=item.qty - stock)
    //     ELSE:
    //       stillRemaining.add(item)
    //
    //   IF !allocated.isEmpty():
    //     subOrders.add(SubOrderDraft(branch, allocated))
    //   remaining = stillRemaining
    //
    // return AllocationResult(subOrders, outOfStockItems=remaining)
}
```

### `ShippingService` — Tính phí ship song song

```java
// src/main/java/com/zone/agri/service/ShippingService.java
//
// Interface ShippingProvider:
//   ShippingFeeResult calculateFee(ShippingFeeParams params)
//
// Implement:
//   - GHNShippingProvider
//   - GHTKShippingProvider
//
// ShippingFeeParams: { fromDistrictId, toDistrictId, toWardCode, weightGram, codAmount }
// ShippingFeeResult: { totalFee, estimatedDays, carrier }
//
// Method tính song song:
List<SubOrderDraft> enrichWithShippingFees(List<SubOrderDraft> subOrders, DeliveryInfo deliveryInfo) {
    // Dùng CompletableFuture.allOf() — KHÔNG dùng for loop tuần tự
    // Mỗi subOrder: tính totalWeight từ items (weightGram * quantity)
    // Gọi shippingProvider.calculateFee() async cho từng subOrder
    // Nếu 1 subOrder fail sau 2 lần retry: set shippingFee = -1, isEstimate = true
    // Không để lỗi 1 subOrder làm fail toàn bộ request
}
```

### `OrderService` — Nghiệp vụ đặt hàng

```java
// src/main/java/com/zone/agri/service/OrderService.java

// Chuẩn bị đơn (KHÔNG lưu DB)
PrepareOrderResponse prepareOrder(PrepareOrderRequest request) {
    // 1. Validate tất cả productVariantId tồn tại trong DB
    // 2. Gọi BranchSearchService.findNearestBranches(lat, lng)
    //    - Nếu không có chi nhánh trong bán kính: tăng radius x2, thử lại 1 lần
    //    - Nếu vẫn không có: throw NotFoundException("Không có chi nhánh nào trong khu vực")
    // 3. Gọi InventoryAllocationService.buildInventoryMatrix(...)
    // 4. Gọi InventoryAllocationService.allocate(...)
    // 5. Gọi ShippingService.enrichWithShippingFees(...)
    // 6. Tính tổng, build response
    // 7. KHÔNG lưu gì vào DB ở bước này
}

// Xác nhận đơn (LƯU DB + TRANSACTION)
@Transactional
ConfirmOrderResponse confirmOrder(ConfirmOrderRequest request) {
    // 1. Parse và validate prepareToken (có thể dùng cache Redis để lưu tạm)
    // 2. LOCK và kiểm tra lại tồn kho lần cuối (double-check)
    //    - Nếu tồn kho thay đổi: throw ConflictException("Hàng vừa hết, vui lòng thử lại")
    // 3. Tạo bản ghi Order (đơn cha)
    // 4. Tạo các bản ghi SubOrder và SubOrderItem
    // 5. Trừ tồn kho: inventoryRepository.findForUpdate(branchId, variantId)
    //    inventory.setQuantity(inventory.getQuantity() - item.getQuantity())
    // 6. (Optional) Gọi Shipping API để tạo đơn vận chuyển thật → lưu carrierOrderId
    // 7. Return ConfirmOrderResponse
}
```

---

## [CONTROLLER]

### `BranchController`

```java
// src/main/java/com/zone/agri/controller/BranchController.java
// @RequestMapping("/api/branches")

// Endpoint tìm chi nhánh gần nhất
// POST /api/branches/nearest
// Body: FindNearestBranchRequest
// Response: List<NearestBranchResponse>
// Gọi BranchSearchService.findNearestBranches()

// Endpoint admin geocode chi nhánh (khi tạo/sửa)
// POST /api/admin/branches
// PUT  /api/admin/branches/{id}
// Gọi GeocodingService.geocode() trước khi lưu Branch
```

### `OrderController`

```java
// src/main/java/com/zone/agri/controller/OrderController.java
// @RequestMapping("/api/orders")

// POST /api/orders/prepare
// Body: PrepareOrderRequest
// Response: PrepareOrderResponse

// POST /api/orders/confirm
// Body: ConfirmOrderRequest
// Response: ConfirmOrderResponse
```

---

## [PERMISSION — THÊM VÀO DataSeeder]

Thêm vào `DataSeeder.java` theo đúng convention Vietnamese hiện có:

```java
// MODULE
Permission orderManage = Permission("ORDER_MANAGE", "Quản lý đơn hàng",
    PermissionGroup.SALES_MANAGEMENT, PermissionType.MODULE, null)

// ACTIONS (parentId = orderManage.id)
Permission orderView    = ("ORDER_VIEW",    "Xem đơn hàng",        ..., orderManage.id)
Permission orderCreate  = ("ORDER_CREATE",  "Tạo đơn hàng",        ..., orderManage.id)
Permission orderConfirm = ("ORDER_CONFIRM", "Xác nhận đơn hàng",   ..., orderManage.id)
Permission orderCancel  = ("ORDER_CANCEL",  "Huỷ đơn hàng",        ..., orderManage.id)

// Gán cho roles:
// ADMIN: tất cả 4 quyền
// STAFF_KHO: ORDER_VIEW, ORDER_CONFIRM
// CUSTOMER: ORDER_VIEW, ORDER_CREATE, ORDER_CANCEL (đơn của chính họ)
```

---

## [UTILITY CLASSES]

### `HaversineUtils.java`

```java
// src/main/java/com/zone/agri/utils/HaversineUtils.java
// static double distanceKm(double lat1, double lng1, double lat2, double lng2)
// Công thức chuẩn, R = 6371 km
// Viết unit test cho class này
```

### `BoundingBoxUtils.java`

```java
// src/main/java/com/zone/agri/utils/BoundingBoxUtils.java
// static BoundingBox calculate(double userLat, double userLng, double radiusKm)
// BoundingBox record: minLat, maxLat, minLng, maxLng
// Viết unit test với các case biên
```

---

## [HTTP CLIENT CONFIG]

```java
// src/main/java/com/zone/agri/config/RestTemplateConfig.java
// Tạo RestTemplate bean với:
//   - connectTimeout: 5000ms
//   - readTimeout: 10000ms
// Dùng cho tất cả external API call (Geocoding, Routing, Shipping)
// Không dùng RestTemplate mặc định không có timeout
```

---

## [API THAM KHẢO — IMPLEMENT THEO ĐÂY]

### TrackAsia Geocoding
```
GET https://api.trackasia.vn/api/geocode/search?query={address}&api_key={key}
Response: { features: [{ geometry: { coordinates: [lng, lat] } }] }
```

### OpenRouteService Distance Matrix
```
POST https://api.openrouteservice.org/v2/matrix/driving-car
Header: Authorization: {ORS_API_KEY}
Body: {
  "locations": [[lng, lat], [lng, lat], ...],
  "sources": [0],
  "destinations": [1, 2, 3, 4, 5]
}
Response: { durations: [[sec, sec, ...]], distances: [[m, m, ...]] }
```

### GHN Tính Phí Ship
```
POST https://dev-online-gateway.ghn.vn/shiip/public-api/v2/shipping-order/fee
Header: Token: {GHN_API_KEY}, ShopId: {GHN_SHOP_ID}
Body: {
  "from_district_id": 1442,
  "to_district_id": 1444,
  "to_ward_code": "20308",
  "weight": 1000,
  "cod_value": 300000,
  "service_type_id": 2
}
Response: { data: { total: 25000, expected_delivery_time: "..." } }
```

### IP Geolocation (không cần key)
```
GET http://ip-api.com/json/{ip}
Response: { lat: 10.07, lon: 105.07, city: "Cần Thơ" }
```

---

## [INTEGRATION VỚI FRONTEND Next.js 15]

FE gọi qua `NEXT_PUBLIC_API_URL=http://localhost:8001/api`.  
Đảm bảo CORS cho phép origin `http://localhost:3000` trong `SecurityConfig.java`.

### Endpoint FE sẽ gọi

| Method | Path | Mô tả |
|--------|------|-------|
| POST | `/api/branches/nearest` | FE gửi `{lat, lng}` nhận danh sách chi nhánh gần nhất |
| POST | `/api/orders/prepare` | FE gửi giỏ hàng + vị trí, nhận bản nháp đơn tách + phí ship |
| POST | `/api/orders/confirm` | FE gửi xác nhận, BE lưu DB + trừ kho |

### Response format thống nhất (theo convention hiện tại)

```json
{
  "success": true,
  "data": { ... },
  "message": "Thành công"
}
```

Lỗi dùng `ErrorDetail` hiện có:
```json
{
  "success": false,
  "error": {
    "code": "OUT_OF_STOCK",
    "message": "Sản phẩm X đã hết hàng tại tất cả chi nhánh"
  }
}
```

---

## [CHECKLIST TRƯỚC KHI SUBMIT]

- [ ] Tất cả entity mới extend `BaseEntity`
- [ ] Tất cả service method thay đổi data có `@Transactional`
- [ ] Tồn kho chỉ đọc/ghi từ bảng `inventories` — không dùng `productVariant.quantity`
- [ ] `findForUpdate` dùng `@Lock(PESSIMISTIC_WRITE)` khi confirm đơn
- [ ] Không gọi Geocoding API trong search flow — chỉ gọi trong admin flow
- [ ] Distance Matrix chỉ nhận tối đa 5 destinations
- [ ] Shipping fee dùng `CompletableFuture.allOf()` — không dùng `for await` tuần tự
- [ ] Tất cả API key đọc từ `@Value("${...}")` — không hardcode
- [ ] CORS cho phép `localhost:3000` trong SecurityConfig
- [ ] Thêm permission `ORDER_*` vào DataSeeder theo convention Vietnamese
- [ ] RestTemplate có timeout (connect: 5s, read: 10s)
- [ ] Exception dùng class có sẵn: `BadRequestException`, `NotFoundException`, `ConflictException`
- [ ] Unit test cho `HaversineUtils` và `BoundingBoxUtils`
- [ ] Unit test cho `InventoryAllocationService.allocate()` — cover đủ 4 case:
  - a. 1 chi nhánh đủ hàng
  - b. phải tách 2 chi nhánh
  - c. 1 sản phẩm không có ở đâu
  - d. có hàng nhưng không đủ số lượng (partial)

---

*Prompt version: 2.0 — Spring Boot + Java 21 + MySQL — Production Ready*  
*Compatible: Next.js 15 App Router Frontend*
