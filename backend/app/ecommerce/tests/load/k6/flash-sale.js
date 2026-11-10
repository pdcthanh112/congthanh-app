// Tải flash sale: 2000 người dùng ảo bấm "mua" cùng lúc qua APISIX.
//   k6 run -e BASE_URL=http://localhost:9080 -e TOKEN=... -e SALE=s1 -e PRODUCT=p1 tests/load/k6/flash-sale.js
// Ngưỡng: p95 < 300ms, không có 5xx; 409 (hết hàng / vượt giới hạn) và 429 (rate limit) là kết quả hợp lệ.
import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

const claimed = new Counter('flash_claimed');
const soldOut = new Counter('flash_sold_out');

export const options = {
  scenarios: {
    rush: { executor: 'ramping-arrival-rate', startRate: 100, timeUnit: '1s', preAllocatedVUs: 500, maxVUs: 2000,
      stages: [{ target: 2000, duration: '10s' }, { target: 2000, duration: '30s' }, { target: 0, duration: '10s' }] },
  },
  thresholds: {
    'http_req_duration{expected_response:true}': ['p(95)<300'],
    'checks': ['rate>0.99'],
  },
};

const BASE = __ENV.BASE_URL || 'http://localhost:9080';

export default function () {
  const url = `${BASE}/api/ecommerce/flash-sales/${__ENV.SALE}/products/${__ENV.PRODUCT}/claim`;
  const res = http.post(url, JSON.stringify({ quantity: 1 }), {
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${__ENV.TOKEN}` },
    tags: { name: 'claim' },
  });
  if (res.status === 202) claimed.add(1);
  if (res.status === 409) soldOut.add(1);
  check(res, { 'không có lỗi 5xx': (r) => r.status < 500 });
}
