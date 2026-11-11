// Tải flash sale qua APISIX: người dùng ảo xếp hàng vào phòng chờ, hỏi vị trí tới khi tới lượt, giành suất rồi mở
// phiên checkout được tạo sẵn cho suất đó.
//   k6 run -e BASE_URL=http://localhost:9080 -e TOKENS_FILE=./tokens.json -e SALE=s1 -e PRODUCT=p1 tests/load/k6/flash-sale.js
// TOKENS_FILE: mảng JSON access token, mỗi token một khách (giới hạn mua tính theo khách nên một token dùng
// chung sẽ chỉ giành được một lần). Không có file thì dùng -e TOKEN=... cho mọi VU.
// Ngưỡng: p95 < 300ms, không có 5xx. 409 (hết hàng / chưa tới lượt / vượt giới hạn) và 429 (rate limit ở gateway)
// là kết quả hợp lệ. Sale không bật phòng chờ (admitPerSecond = 0) thì bước xếp hàng trả canClaim ngay.
import http from 'k6/http';
import { check, sleep } from 'k6';
import { SharedArray } from 'k6/data';
import { Counter, Trend } from 'k6/metrics';

const claimed = new Counter('flash_claimed');
const soldOut = new Counter('flash_sold_out');
const sessionsOpened = new Counter('flash_checkout_sessions');
const queueWait = new Trend('flash_queue_wait_seconds');
const sessionDelay = new Trend('flash_checkout_session_delay_ms', true);

export const options = {
  scenarios: {
    rush: { executor: 'ramping-arrival-rate', startRate: 100, timeUnit: '1s', preAllocatedVUs: 500, maxVUs: 2000,
      stages: [{ target: 2000, duration: '10s' }, { target: 2000, duration: '30s' }, { target: 0, duration: '10s' }] },
  },
  thresholds: {
    'http_req_duration{expected_response:true}': ['p(95)<300'],
    'checks': ['rate>0.99'],
    // Phiên checkout phải xuất hiện nhanh sau khi giành được suất (đi qua Kafka).
    'flash_checkout_session_delay_ms': ['p(95)<3000'],
  },
};

const BASE = __ENV.BASE_URL || 'http://localhost:9080';
const tokens = new SharedArray('tokens', () => (__ENV.TOKENS_FILE ? JSON.parse(open(__ENV.TOKENS_FILE)) : [__ENV.TOKEN]));
const sale = `${BASE}/api/ecommerce/flash-sales/${__ENV.SALE}/products/${__ENV.PRODUCT}`;
const ok5xx = { 'không có lỗi 5xx': (r) => r.status < 500 };

export default function () {
  const headers = { 'Content-Type': 'application/json', Authorization: `Bearer ${tokens[(__VU - 1) % tokens.length]}` };

  // 1. Xếp hàng rồi hỏi lại theo retryAfterSeconds cho tới lượt (tối đa 60 giây).
  const started = Date.now();
  let res = http.post(`${sale}/queue`, null, { headers, tags: { name: 'queue-join' } });
  check(res, ok5xx);
  let q = res.status === 200 ? res.json('data') : null;
  while (q && !q.canClaim && !q.soldOut && Date.now() - started < 60000) {
    sleep(q.retryAfterSeconds || 2);
    res = http.get(`${sale}/queue`, { headers, tags: { name: 'queue-status' } });
    check(res, ok5xx);
    q = res.status === 200 ? res.json('data') : q;
  }
  if (!q || !q.canClaim) {
    if (q && q.soldOut) soldOut.add(1);
    return;
  }
  queueWait.add((Date.now() - started) / 1000);

  // 2. Giành suất.
  res = http.post(`${sale}/claim`, JSON.stringify({ quantity: 1 }), { headers, tags: { name: 'claim' } });
  check(res, ok5xx);
  if (res.status === 409) soldOut.add(1);
  if (res.status !== 202) return;
  claimed.add(1);

  // 3. Phiên checkout của suất được tạo bất đồng bộ: 404 vài lần đầu là bình thường.
  const sessionUrl = `${BASE}/api/ecommerce/checkout/sessions/${res.json('data.checkoutSessionId')}`;
  const claimedAt = Date.now();
  for (let i = 0; i < 10; i++) {
    res = http.get(sessionUrl, { headers, tags: { name: 'checkout-session' }, responseCallback: http.expectedStatuses(200, 404) });
    check(res, ok5xx);
    if (res.status === 200) {
      sessionsOpened.add(1);
      sessionDelay.add(Date.now() - claimedAt);
      return;
    }
    sleep(0.5);
  }
}
