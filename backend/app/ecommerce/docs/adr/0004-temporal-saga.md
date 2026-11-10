# 0004. Saga checkout bằng Temporal

- Trạng thái: Accepted, đã triển khai ở đợt 2 (thay Axon Framework)

## Quyết định
Checkout là workflow Temporal `Checkout` (task queue `checkout`) do order-service chạy. Activity thanh toán
chạy ở payment-service (task queue `payment`). Hợp đồng chung nằm ở `libs/java/workflow-contracts`.

```
redeem voucher → giữ hàng → authorize → [chờ cổng thanh toán] → commit kho (pivot) → capture → confirm
```

- Mỗi bước trước pivot đăng ký compensation *trước khi* gọi: `CancelRedemption`, `ReleaseInventory`, `Refund`.
  Lỗi ở bất kỳ bước nào trước pivot thì bù trừ ngược rồi huỷ đơn.
- Sau commit kho thì chỉ đi tiếp: capture và confirm retry không giới hạn.
- Workflow id `checkout-<orderId>`, reuse policy `ALLOW_DUPLICATE_FAILED_ONLY`: start lại cùng đơn là no-op.
  Reservation id tồn kho dùng chung giá trị này.
- Cổng kiểu redirect (VNPay, PayPal, Stripe 3DS): workflow chờ signal `paymentCompleted` bằng timer bền vững
  (mặc định 15 phút), hết hạn thì bù trừ. Khách huỷ bằng signal `cancel` (chỉ có tác dụng trước pivot).
  Query `status` trả bước hiện tại và URL thanh toán cho frontend.
- Đơn được lưu cùng outbox trong một transaction, workflow start sau commit. `CheckoutRecovery` quét đơn
  PENDING quá hạn và start lại (idempotent nhờ workflow id), nên không mất saga nếu pod chết giữa hai bước.

## Lý do
Trạng thái saga nằm trong lịch sử event của Temporal: pod chết thì worker khác replay và chạy tiếp đúng bước.
Retry, timeout, timer khai báo trong code; xem từng đơn trên Temporal UI. Không cần event store hay bảng saga tự viết,
và không khoá cả hệ thống vào một framework như Axon (Axon Server, aggregate event-sourced, serializer riêng).

## Hệ quả
- Activity phải idempotent theo `orderId`; payment dùng `order_id UNIQUE` làm hàng rào cuối.
- Đổi logic workflow sau khi đã chạy production phải dùng `Workflow.getVersion` để không vỡ replay.
- Test saga bằng `TestWorkflowEnvironment` (time-skipping): `CheckoutWorkflowTest` phủ happy path, thẻ bị từ chối,
  redirect + signal, hết hạn chờ thanh toán, khách huỷ.
- Không còn đường Axon cũ nên không có flag bật dần; rollout qua canary của Argo Rollouts.
