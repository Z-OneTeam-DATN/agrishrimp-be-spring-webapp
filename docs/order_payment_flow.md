# Order Payment Flow

## 1. Checkout prepare

Frontend:
- `agrishrimp-fe-react/app/(shop)/checkout/page.tsx`
- `agrishrimp-fe-react/hooks/usePrepareOrder.ts`
- `agrishrimp-fe-react/app/services/order.service.ts`

API:
- `POST /api/orders/prepare`

Backend:
- `OrderController.prepareOrder(...)`
- `OrderService.prepareOrder(...)`
- `BranchSearchService`
- `InventoryAllocationService`
- `ShippingService`
- `VoucherService`

Ket qua:
- Kiem tra dia chi cua user tren backend.
- Chon `primaryBranch`.
- Tinh `stockStatus`, `suggestedTransfers`, phi ship, voucher.
- Luu draft vao Redis bang `prepareToken`.
- Chua tao order, chua tru kho, chua consume voucher.

## 2. Confirm order

Frontend:
- `agrishrimp-fe-react/hooks/useConfirmOrder.ts`
- `agrishrimp-fe-react/app/(shop)/checkout/page.tsx`

API:
- `POST /api/orders/confirm`

Backend:
- `OrderController.confirmOrder(...)`
- `OrderService.confirmOrder(...)`
- `OrderInventoryReservationService`

Ket qua:
- Doc draft tu Redis va re-quote lai du lieu live.
- Bat buoc `idempotencyKey` de tranh tao 2 don.
- Tao `Order` va `SubOrder`.
- Chi `giu hang` bang `reservedQuantity`, chua tru `quantity`.
- Tao transaction `ORDER_RESERVE` theo tung lo FIFO.
- PAYOS: don vao `AWAITING_PAYMENT`.
- COD/CASH/TRANSFER hien tai di vao luong status hien co cua he thong.

## 3. Thanh toan

PAYOS:
- `PayOSService.createPaymentLink(...)`
- `PaymentController.handlePayOSWebhook(...)`
- `PayOSService.markOrderPaid(...)`

Luot di:
1. Confirm tao don va giu hang.
2. Tao `checkoutUrl` cua PayOS.
3. Webhook thanh cong -> `paymentStatus = PAID`.
4. Don du hang -> `PENDING`.
5. Don thieu hang -> `AWAITING_REPLENISHMENT`.

Het han thanh toan:
- `OrderCompletionScheduler.expireUnpaidPaymentOrders()`
- `OrderService.expireUnpaidPaymentOrders()`

Ket qua:
- Don `AWAITING_PAYMENT` qua han se bi `CANCELLED`.
- Giai phong hang da giu.
- Hoan voucher.
- Huy payOS payment link neu con mo.

## 4. Chuan bi va xuat kho

Chi nhanh:
- `OrderService.updateSubOrderStatus(...)`
- `HandoverService.createHandover(...)`
- `AdminOrderWorkflowService.approvePackingAndShip(...)`

Nguyen tac moi:
- `CONFIRM` chi giu hang.
- Khi chuyen sang `SHIPPING` moi tru kho that.
- `OrderInventoryReservationService.shipReservedInventory(...)`
  se:
  - giam `quantity`
  - giam `reservedQuantity`
  - tao transaction `SALE`

Huong di thuc te:
1. Sub-order duoc chuan bi.
2. Khi ban giao van chuyen / chuyen sang `SHIPPING`, he thong moi xuat kho.
3. Khi khach nhan hang, COD moi duoc danh dau `PAID`.

## 5. Huy don

Backend:
- `OrderService.cancelMyOrder(...)`
- `OrderService.updateOrderStatus(..., CANCELLED)`
- `OrderInventoryReservationService.releaseReservedInventory(...)`

Ket qua:
- Don chua giao co the huy.
- Hang da giu duoc giai phong.
- Neu la don cu da tru kho theo flow cu, he thong van fallback hoan kho bang `SALE -> CANCEL_RELEASE`.

## 6. Don thieu hang

Backend:
- `ImmediateReplenishmentService`
- `InventoryTransferService`
- `BackorderService.fulfillBackordersOnStockReceive(...)`

Luot di:
1. Confirm tao sub-order `AWAITING_REPLENISHMENT` neu con thieu.
2. He thong co the tao transfer bo sung.
3. Khi hang ve kho dich, `BackorderService` se tiep tuc `reserve` phan con thieu.
4. Khi da du hang, sub-order quay lai luong xu ly hien tai.

## 7. File anh huong chinh

Backend:
- `src/main/java/com/zone/agri/service/OrderService.java`
- `src/main/java/com/zone/agri/service/OrderInventoryReservationService.java`
- `src/main/java/com/zone/agri/service/BackorderService.java`
- `src/main/java/com/zone/agri/service/HandoverService.java`
- `src/main/java/com/zone/agri/service/AdminOrderWorkflowService.java`
- `src/main/java/com/zone/agri/service/PayOSService.java`
- `src/main/java/com/zone/agri/service/OrderCompletionScheduler.java`
- `src/main/java/com/zone/agri/entity/enums/TransactionType.java`
- `src/main/java/com/zone/agri/repository/InventoryRepository.java`

Frontend:
- `agrishrimp-fe-react/app/(shop)/checkout/page.tsx`
- `agrishrimp-fe-react/hooks/usePrepareOrder.ts`
- `agrishrimp-fe-react/hooks/useConfirmOrder.ts`
- `agrishrimp-fe-react/components/order/PrepareOrderSummary.tsx`
- `agrishrimp-fe-react/app/(shop)/order-success/page.tsx`

## 8. Ghi chu

- Endpoint legacy `POST /api/orders/checkout` da cu, nen uu tien flow `prepare -> confirm`.
- Mo hinh hien tai da doi tu `tru kho luc confirm` sang `giu hang luc confirm, xuat kho luc shipping`.
