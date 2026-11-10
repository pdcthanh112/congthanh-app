// Hành vi mua sắm thường ngày: xem tồn kho, thêm vào giỏ, validate giỏ. SLO: p95 < 200ms, lỗi < 0.1%.
import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [{ duration: '1m', target: 200 }, { duration: '5m', target: 200 }, { duration: '1m', target: 0 }],
  thresholds: { http_req_failed: ['rate<0.001'], http_req_duration: ['p(95)<200'] },
};

const BASE = __ENV.BASE_URL || 'http://localhost:9080';
const H = { headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${__ENV.TOKEN}` } };

export function setup() {
  const res = http.post(`${BASE}/api/ecommerce/carts/`, JSON.stringify({ name: 'k6' }), H);
  return { cartId: res.json('data.id') };
}

export default function ({ cartId }) {
  const p = `p${Math.floor(Math.random() * 50)}`;
  check(http.get(`${BASE}/api/ecommerce/inventory/SKU-${p}/in-stock?quantity=1`), { 'in-stock 200': (r) => r.status === 200 });
  check(http.post(`${BASE}/api/ecommerce/carts/${cartId}/items`, JSON.stringify({ productId: p, quantity: 1 }), H),
    { 'add item': (r) => r.status === 200 || r.status === 400 });
  check(http.post(`${BASE}/api/ecommerce/carts/${cartId}/validate`, null, H), { 'validate 200': (r) => r.status === 200 });
  sleep(1);
}
