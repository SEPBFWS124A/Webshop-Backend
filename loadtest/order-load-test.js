// OPTIONAL k6 scenario: real order placement under load.
//
// Unlike the main load-test.js (read-dominant), this script actually places orders via
// POST /api/orders. It is kept separate and deliberately uses fewer virtual users because
// order placement mutates state. To avoid out-of-stock errors polluting the results it targets
// a dedicated high-stock product seeded by loadtest-seed.sql ("Load Test Bestell Artikel").
//
// Prerequisites (see docs/loadtest.md):
//   - stack running, loadtest-seed.sql applied (provides the high-stock product + loaduser accounts)
//   - ideally a mail catcher (Mailpit/MailHog) so order-confirmation emails are intercepted
//
// Example:
//   docker run --rm -e BASE_URL=http://host.docker.internal:8080 \
//     -e LOGIN_USER_PREFIX=loaduser -e LOGIN_USER_COUNT=1000 \
//     -v "${PWD}/loadtest:/scripts" grafana/k6 run /scripts/order-load-test.js

import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Rate } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://host.docker.internal:8080';
const LOGIN_PASSWORD = __ENV.LOGIN_PASSWORD || 'Password1!';
const LOGIN_USER_PREFIX = __ENV.LOGIN_USER_PREFIX || 'loaduser';
const LOGIN_USER_COUNT = Number(__ENV.LOGIN_USER_COUNT || 1000);
const ORDER_PRODUCT_SEARCH = __ENV.ORDER_PRODUCT_SEARCH || 'Bestell Artikel';

const orderErrors = new Rate('order_errors');

export const options = {
  // Fewer VUs than the read load — order placement is heavier and stateful.
  stages: [
    { duration: '30s', target: 10 },
    { duration: '1m', target: 25 },
    { duration: '1m', target: 50 },
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    http_req_failed: ['rate<0.02'],
    http_req_duration: ['p(95)<1500'],
    order_errors: ['rate<0.02'],
  },
};

function pickLoginUsername() {
  if (LOGIN_USER_COUNT > 0) {
    return `${LOGIN_USER_PREFIX}${1 + Math.floor(Math.random() * LOGIN_USER_COUNT)}`;
  }
  return LOGIN_USER_PREFIX;
}

function authHeaders(token) {
  return {
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
  };
}

export function setup() {
  // Resolve the dedicated high-stock product id once, shared by all VUs.
  const response = http.get(`${BASE_URL}/api/products?search=${encodeURIComponent(ORDER_PRODUCT_SEARCH)}`);
  const products = response.status === 200 ? response.json() : [];
  if (!Array.isArray(products) || products.length === 0) {
    throw new Error(
      `No product matched "${ORDER_PRODUCT_SEARCH}". Did you apply loadtest-seed.sql? (status ${response.status})`,
    );
  }
  return { productId: products[0].id };
}

export default function (data) {
  group('place order', () => {
    const login = http.post(
      `${BASE_URL}/api/auth/login`,
      JSON.stringify({ username: pickLoginUsername(), password: LOGIN_PASSWORD }),
      { headers: { 'Content-Type': 'application/json' }, tags: { name: 'POST /api/auth/login' } },
    );
    if (!check(login, { 'login 200': (r) => r.status === 200 && !!r.json('token') })) {
      orderErrors.add(true);
      return;
    }
    const headers = authHeaders(login.json('token'));

    const addToCart = http.post(
      `${BASE_URL}/api/cart/items`,
      JSON.stringify({ productId: data.productId, quantity: 1 }),
      { ...headers, tags: { name: 'POST /api/cart/items' } },
    );
    orderErrors.add(!check(addToCart, { 'add to cart 2xx': (r) => r.status >= 200 && r.status < 300 }));

    // POST /api/orders builds the order from the cart when items is null.
    const orderBody = JSON.stringify({
      email: 'loadtest@example.test',
      customerName: 'Load Test',
      deliveryAddress: { street: 'Hauptstrasse 1', city: 'Bielefeld', postalCode: '33602', country: 'Germany' },
      shippingMethod: 'STANDARD',
      paymentMethod: { methodType: 'BANK_TRANSFER', maskedDetails: 'Rechnung' },
      allowUnverifiedAddress: true,   // skip the external address-validation call under load
      acceptedTermsAndConditions: true,
      acceptedPrivacyPolicy: true,
      items: null,
    });
    const order = http.post(`${BASE_URL}/api/orders`, orderBody, { ...headers, tags: { name: 'POST /api/orders' } });
    orderErrors.add(!check(order, { 'order placed 2xx': (r) => r.status >= 200 && r.status < 300 }));
  });

  sleep(Math.random() * 2 + 1);
}
