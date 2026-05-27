// k6 load test for the Webshop backend.
//
// Simulates a realistic, read-dominant traffic mix (browsing the catalog) with a fraction of
// authenticated sessions (login, cart, order history). Optionally — for a maximum-load run —
// each authenticated session also places a REAL order against a dedicated high-stock product
// (set PLACE_ORDERS=true). Ramps the number of virtual users up in stages to find the point
// where latency / error rate degrade.
//
// Run while the full stack is up (./dev.bat start) and a load-test data volume is seeded
// (see docs/loadtest.md). Watch the Grafana dashboards at http://localhost:3001 during the run.
//
// Example (k6 in Docker, reaching the backend published on the host):
//   docker run --rm -e BASE_URL=http://host.docker.internal:8080 \
//     -v "${PWD}/loadtest:/scripts" grafana/k6 run /scripts/load-test.js
//
// Tunables via environment variables:
//   BASE_URL          backend base URL              (default http://host.docker.internal:8080)
//   LOGIN_PASSWORD    password for the test users   (default Password1!)
//   LOGIN_USER_PREFIX username prefix for login     (default alice — a single dev-seed account)
//   LOGIN_USER_COUNT  number of users <prefix>1..N  (default 0 → use the prefix verbatim)
//   AUTH_SHARE        fraction of iterations that authenticate (default 0.3)
//   PLACE_ORDERS      'true' → authenticated sessions place a real order (max-load mode)
//   ORDER_PRODUCT_SEARCH  search term for the high-stock order product (default 'Bestell Artikel')

import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Rate } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://host.docker.internal:8080';
const LOGIN_PASSWORD = __ENV.LOGIN_PASSWORD || 'Password1!';
const LOGIN_USER_PREFIX = __ENV.LOGIN_USER_PREFIX || 'alice';
const LOGIN_USER_COUNT = Number(__ENV.LOGIN_USER_COUNT || 0);
const AUTH_SHARE = Number(__ENV.AUTH_SHARE || 0.3);
const PLACE_ORDERS = (__ENV.PLACE_ORDERS || 'false') === 'true';
const ORDER_PRODUCT_SEARCH = __ENV.ORDER_PRODUCT_SEARCH || 'Bestell Artikel';

// Custom metric: share of business-flow steps that failed their functional check.
const businessErrors = new Rate('business_errors');

export const options = {
  // Ramp virtual users up in stages so the breaking point is visible in the timeline.
  stages: [
    { duration: '30s', target: 20 },
    { duration: '1m', target: 50 },
    { duration: '1m', target: 100 },
    { duration: '1m', target: 200 },
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    // The run is considered healthy below these limits; crossing them marks the "knee".
    http_req_failed: ['rate<0.01'],     // less than 1% failed HTTP requests
    http_req_duration: ['p(95)<800'],   // 95% of requests faster than 800 ms
    business_errors: ['rate<0.01'],
  },
};

// Resolves the dedicated high-stock order product once (shared by all VUs) when placing orders.
export function setup() {
  if (!PLACE_ORDERS) {
    return {};
  }
  const response = http.get(`${BASE_URL}/api/products?search=${encodeURIComponent(ORDER_PRODUCT_SEARCH)}`);
  const products = response.status === 200 ? response.json() : [];
  const orderProductId = Array.isArray(products) && products.length > 0 ? products[0].id : null;
  if (orderProductId === null) {
    console.warn(
      `PLACE_ORDERS is set but no product matched "${ORDER_PRODUCT_SEARCH}". ` +
      'Did you apply loadtest-seed.sql? Orders will be skipped.',
    );
  }
  return { orderProductId };
}

function pickLoginUsername() {
  if (LOGIN_USER_COUNT > 0) {
    return `${LOGIN_USER_PREFIX}${1 + Math.floor(Math.random() * LOGIN_USER_COUNT)}`;
  }
  return LOGIN_USER_PREFIX;
}

function browseCatalog() {
  let randomProductId = null;
  group('browse catalog', () => {
    const catalog = http.get(`${BASE_URL}/api/products?purchasable=true`, {
      tags: { name: 'GET /api/products' },
    });
    const ok = check(catalog, {
      'catalog status 200': (response) => response.status === 200,
    });
    businessErrors.add(!ok);

    if (ok) {
      const products = catalog.json();
      if (Array.isArray(products) && products.length > 0) {
        randomProductId = products[Math.floor(Math.random() * products.length)].id;
      }
    }

    if (randomProductId !== null) {
      const detail = http.get(`${BASE_URL}/api/products/${randomProductId}`, {
        tags: { name: 'GET /api/products/{id}' },
      });
      businessErrors.add(!check(detail, {
        'product detail status 200': (response) => response.status === 200,
      }));
    }
  });
  return randomProductId;
}

function placeOrder(authHeaders) {
  const orderBody = JSON.stringify({
    email: 'loadtest@example.test',
    customerName: 'Load Test',
    deliveryAddress: { street: 'Hauptstrasse 1', city: 'Bielefeld', postalCode: '33602', country: 'Germany' },
    shippingMethod: 'STANDARD',
    paymentMethod: { methodType: 'BANK_TRANSFER', maskedDetails: 'Rechnung' },
    allowUnverifiedAddress: true,   // skip the external address-validation call under load
    acceptedTermsAndConditions: true,
    acceptedPrivacyPolicy: true,
    items: null,                    // POST /api/orders builds the order from the cart
  });
  const order = http.post(`${BASE_URL}/api/orders`, orderBody, { ...authHeaders, tags: { name: 'POST /api/orders' } });
  businessErrors.add(!check(order, {
    'order placed 2xx': (response) => response.status >= 200 && response.status < 300,
  }));
}

function authenticatedSession(browsedProductId, orderProductId) {
  group('authenticated session', () => {
    const loginResponse = http.post(
      `${BASE_URL}/api/auth/login`,
      JSON.stringify({ username: pickLoginUsername(), password: LOGIN_PASSWORD }),
      { headers: { 'Content-Type': 'application/json' }, tags: { name: 'POST /api/auth/login' } },
    );
    const loggedIn = check(loginResponse, {
      'login status 200': (response) => response.status === 200,
      'login returns token': (response) => !!response.json('token'),
    });
    businessErrors.add(!loggedIn);
    if (!loggedIn) {
      return;
    }

    const authHeaders = {
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${loginResponse.json('token')}`,
      },
    };

    const cart = http.get(`${BASE_URL}/api/cart`, { ...authHeaders, tags: { name: 'GET /api/cart' } });
    businessErrors.add(!check(cart, { 'cart status 200': (response) => response.status === 200 }));

    // When placing orders, fill the cart with the high-stock product so nothing runs out of stock.
    const placingOrder = PLACE_ORDERS && orderProductId;
    const cartProductId = placingOrder ? orderProductId : browsedProductId;
    if (cartProductId !== null && cartProductId !== undefined) {
      const addToCart = http.post(
        `${BASE_URL}/api/cart/items`,
        JSON.stringify({ productId: cartProductId, quantity: 1 }),
        { ...authHeaders, tags: { name: 'POST /api/cart/items' } },
      );
      businessErrors.add(!check(addToCart, {
        'add to cart 2xx': (response) => response.status >= 200 && response.status < 300,
      }));
    }

    const orderHistory = http.get(`${BASE_URL}/api/orders`, { ...authHeaders, tags: { name: 'GET /api/orders' } });
    businessErrors.add(!check(orderHistory, {
      'order history status 200': (response) => response.status === 200,
    }));

    if (placingOrder) {
      placeOrder(authHeaders);
    }
  });
}

export default function (data) {
  const browsedProductId = browseCatalog();

  if (Math.random() < AUTH_SHARE) {
    authenticatedSession(browsedProductId, data ? data.orderProductId : null);
  }

  // Short think-time between iterations, like a real user.
  sleep(Math.random() * 2 + 1);
}
