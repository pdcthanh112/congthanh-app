--liquibase formatted sql

--changeset payment-service:0002-international-methods
-- ADR 0015: bỏ VNPay, MoMo, COD; Stripe gom về CARD. Payment cũ của VNPay/MoMo/COD không có
-- phương thức tương ứng nên chuyển hết sang VOIDED trước khi đổi tên, để service vẫn đọc được bản ghi.
UPDATE payments SET method = 'CARD' WHERE method IN ('STRIPE', 'CREDIT_CARD');
UPDATE payments SET status = 'VOIDED', method = 'CARD', provider = 'legacy'
 WHERE method IN ('VN_PAY', 'MOMO', 'CASH_ON_DELIVERY') AND status NOT IN ('CAPTURED', 'REFUNDED', 'VOIDED', 'DECLINED');
UPDATE payments SET method = 'CARD', provider = 'legacy' WHERE method IN ('VN_PAY', 'MOMO', 'CASH_ON_DELIVERY');
--rollback SELECT 1;
