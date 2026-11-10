package com.congthanh.paymentservice.adapter.out.provider;

import com.congthanh.paymentservice.application.port.out.PaymentProvider;
import com.congthanh.paymentservice.domain.exception.PaymentException;
import com.congthanh.paymentservice.domain.model.Payment;
import com.congthanh.paymentservice.domain.model.PaymentMethod;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * VNPay (cổng "sale"): khách được redirect sang VNPay, kết quả về qua IPN (server-to-server, có chữ ký
 * HMAC-SHA512). Không có authorize/capture tách rời: trả xong là đã thu tiền. Hoàn tiền qua merchant API.
 */
@Component
@ConditionalOnProperty("payment.vnpay.tmn-code")
class VnPayProvider implements PaymentProvider {

    static final String VERSION = "2.1.0";
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(VN);

    private final String tmnCode;
    private final String hashSecret;
    private final String payUrl;
    private final String returnUrl;
    private final RestClient api;

    VnPayProvider(@Value("${payment.vnpay.tmn-code}") String tmnCode,
                  @Value("${payment.vnpay.hash-secret}") String hashSecret,
                  @Value("${payment.vnpay.pay-url:https://sandbox.vnpayment.vn/paymentv2/vpcpay.html}") String payUrl,
                  @Value("${payment.vnpay.api-url:https://sandbox.vnpayment.vn/merchant_webapi/api/transaction}") String apiUrl,
                  @Value("${payment.vnpay.return-url}") String returnUrl,
                  RestClient.Builder http) {
        this.tmnCode = tmnCode;
        this.hashSecret = hashSecret;
        this.payUrl = payUrl;
        this.returnUrl = returnUrl;
        this.api = http.baseUrl(apiUrl).build();
    }

    @Override
    public String name() {
        return "vnpay";
    }

    @Override
    public Set<PaymentMethod> methods() {
        return Set.of(PaymentMethod.VN_PAY);
    }

    @Override
    public boolean capturesImmediately() {
        return true;
    }

    @Override
    public ProviderResult authorize(Payment payment, String paymentToken, String frontendReturnUrl, String clientIp) {
        if (!"VND".equals(payment.amount().currency())) {
            return new ProviderResult.Declined("VNPay only accepts VND");
        }
        String txnRef = txnRef(payment);
        Instant now = Instant.now();
        Map<String, String> p = new TreeMap<>();
        p.put("vnp_Version", VERSION);
        p.put("vnp_Command", "pay");
        p.put("vnp_TmnCode", tmnCode);
        p.put("vnp_Amount", String.valueOf(payment.amount().minorUnits() * 100));
        p.put("vnp_CurrCode", "VND");
        p.put("vnp_TxnRef", txnRef);
        p.put("vnp_OrderInfo", "Thanh toan don hang " + payment.orderId());
        p.put("vnp_OrderType", "other");
        p.put("vnp_Locale", "vn");
        p.put("vnp_ReturnUrl", frontendReturnUrl != null ? frontendReturnUrl : returnUrl);
        p.put("vnp_IpAddr", clientIp == null ? "127.0.0.1" : clientIp);
        p.put("vnp_CreateDate", TS.format(now));
        p.put("vnp_ExpireDate", TS.format(now.plusSeconds(15 * 60)));
        String query = encode(p);
        String url = payUrl + "?" + query + "&vnp_SecureHash=" + Hmac.hex("HmacSHA512", hashSecret, query);
        return new ProviderResult.ActionRequired(txnRef, url);
    }

    /** IPN: kiểm tra chữ ký trên mọi tham số vnp_* (trừ hash), theo thứ tự tên. */
    @Override
    public CallbackResult verifyCallback(Map<String, String> params) {
        String received = params.get("vnp_SecureHash");
        Map<String, String> signed = new TreeMap<>();
        params.forEach((k, v) -> {
            if (k.startsWith("vnp_") && !k.equals("vnp_SecureHash") && !k.equals("vnp_SecureHashType")
                    && v != null && !v.isEmpty()) {
                signed.put(k, v);
            }
        });
        if (!Hmac.equalsConstantTime(Hmac.hex("HmacSHA512", hashSecret, encode(signed)), received)) {
            throw PaymentException.invalid("bad VNPay signature");
        }
        boolean ok = "00".equals(params.get("vnp_ResponseCode")) && "00".equals(params.get("vnp_TransactionStatus"));
        Long amount = params.get("vnp_Amount") == null ? null : Long.parseLong(params.get("vnp_Amount")) / 100;
        return new CallbackResult(null, params.get("vnp_TxnRef"), ok, ok ? null : "VNPAY_" + params.get("vnp_ResponseCode"), amount);
    }

    @Override
    public boolean capture(Payment payment) {
        return true;
    }

    @Override
    public void voidAuthorization(Payment payment) {
        // Chưa trả thì không có gì để huỷ; link thanh toán tự hết hạn sau 15 phút.
    }

    @Override
    public void refund(Payment payment) {
        String requestId = UUID.randomUUID().toString().replace("-", "");
        String createDate = TS.format(Instant.now());
        String amount = String.valueOf(payment.amount().minorUnits() * 100);
        String txnDate = TS.format(payment.createdAt());
        String info = "Hoan tien don " + payment.orderId();
        String data = String.join("|", requestId, VERSION, "refund", tmnCode, "02", payment.providerRef(), amount,
                "", txnDate, "payment-service", createDate, "127.0.0.1", info);
        Map<String, String> body = new LinkedHashMap<>();
        body.put("vnp_RequestId", requestId);
        body.put("vnp_Version", VERSION);
        body.put("vnp_Command", "refund");
        body.put("vnp_TmnCode", tmnCode);
        body.put("vnp_TransactionType", "02");
        body.put("vnp_TxnRef", payment.providerRef());
        body.put("vnp_Amount", amount);
        body.put("vnp_TransactionDate", txnDate);
        body.put("vnp_CreateBy", "payment-service");
        body.put("vnp_CreateDate", createDate);
        body.put("vnp_IpAddr", "127.0.0.1");
        body.put("vnp_OrderInfo", info);
        body.put("vnp_SecureHash", Hmac.hex("HmacSHA512", hashSecret, data));
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> res = api.post().contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(Map.class);
            String code = res == null ? null : String.valueOf(res.get("vnp_ResponseCode"));
            // 94: đã gửi yêu cầu hoàn tiền này trước đó → coi như thành công (idempotent).
            if (!"00".equals(code) && !"94".equals(code)) {
                throw PaymentException.unavailable("vnpay refund failed: " + code);
            }
        } catch (RestClientException e) {
            throw PaymentException.unavailable("vnpay refund: " + e.getMessage());
        }
    }

    static String txnRef(Payment payment) {
        return payment.id().replace("-", "");
    }

    static String encode(Map<String, String> sorted) {
        StringBuilder sb = new StringBuilder();
        sorted.forEach((k, v) -> {
            if (!sb.isEmpty()) {
                sb.append('&');
            }
            sb.append(URLEncoder.encode(k, StandardCharsets.US_ASCII)).append('=')
                    .append(URLEncoder.encode(v, StandardCharsets.US_ASCII));
        });
        return sb.toString();
    }
}
