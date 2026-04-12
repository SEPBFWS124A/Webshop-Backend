# Milestone 3: Warenkorb & Checkout

> Vollständiges Rollenkonzept & Berechtigungsmatrix: [ROLLEN.md](./ROLLEN.md)

**Issues:** #39, #40, #41, #42, #44, #45

---

## Beteiligte Rollen

| Rolle | Relevanz |
|-------|----------|
| `CUSTOMER` | Alle Warenkorb- und Checkout-Funktionen |
| `EMPLOYEE`, `SALES_EMPLOYEE`, `ADMIN` | Warenkorb-Verwaltung für Kunden → siehe [Milestone 8](./milestone-8-kundenservice.md) |

---

## User Stories → Endpunkte

---

### #41 — Warenkorbübersicht (Preis, Steuern, Versand)

**Endpunkt:** `GET /api/cart`
**Berechtigung:** CUSTOMER

**Response `200 OK`:**
```json
{
  "items": [
    {
      "cartItemId": 1,
      "productId": 2,
      "productName": "Wireless Mouse",
      "imageUrl": "https://placehold.co/400x300?text=Mouse",
      "unitPrice": 26.99,
      "quantity": 2,
      "lineTotal": 53.98,
      "addedAt": "2026-04-10T14:30:00Z"
    }
  ],
  "subtotal": 53.98,
  "tax": 8.64,
  "shippingCost": 0.00,
  "total": 62.62
}
```

**Was das Backend macht:**
- `unitPrice` enthält bereits den kundenspezifischen Preis (nach Rabatten)
- Steuern und Versandkosten werden serverseitig berechnet

---

### #39 — Artikel in Warenkorb legen

**Endpunkt:** `POST /api/cart/items`
**Berechtigung:** CUSTOMER

**Request:**
```json
{
  "productId": 2,
  "quantity": 1
}
```

Validierung: `quantity` mind. 1

**Response `200 OK`:** Aktualisierter Warenkorb (CartResponse — identisch mit GET /api/cart)

**Was das Backend macht:**
- Wenn der Artikel bereits im Warenkorb: Menge wird addiert
- Nur kaufbare Produkte (`purchasable = true`) können hinzugefügt werden

---

### #40 — Artikel aus Warenkorb entfernen

**Endpunkt:** `DELETE /api/cart/items/{productId}`
**Berechtigung:** CUSTOMER

**Response `200 OK`:** Aktualisierter Warenkorb

> Der Pfad-Parameter ist die **Produkt-ID**, nicht die `cartItemId`.

---

### #42 — Warenkorb bestellen

**Endpunkt:** `POST /api/orders`
**Berechtigung:** CUSTOMER

**Request (optional):**
```json
{
  "couponCode": "WELCOME10"
}
```

Body kann auch leer sein (kein Coupon):
```json
{}
```

**Response `201 Created`:**
```json
{
  "id": 42,
  "status": "PROCESSING",
  "totalPrice": 56.21,
  "taxAmount": 7.78,
  "shippingCost": 0.00,
  "couponCode": "WELCOME10",
  "createdAt": "2026-04-12T09:15:00Z",
  "items": [
    {
      "orderItemId": 1,
      "productId": 2,
      "productName": "Wireless Mouse",
      "productPurchasable": true,
      "quantity": 2,
      "priceAtOrderTime": 26.99,
      "lineTotal": 53.98
    }
  ]
}
```

**Was das Backend macht:**
- Übernimmt alle Artikel aus dem Warenkorb
- Wendet Coupon-Rabatt an (wenn gültig)
- Speichert `priceAtOrderTime` (Preis zum Zeitpunkt der Bestellung — ändert sich nie mehr)
- Leert den Warenkorb nach erfolgreicher Bestellung

**Mögliche Fehler:**
- `400 Bad Request`: Warenkorb leer
- `400 Bad Request`: Coupon ungültig oder bereits verwendet
- `400 Bad Request`: Ein Artikel ist nicht mehr kaufbar

---

### #44 — Zahlungsart sicher speichern/auswählen

**Endpunkt:** `PUT /api/users/me/payment-method`
**Berechtigung:** Authenticated

**Request:**
```json
{
  "methodType": "SEPA_DIRECT_DEBIT",
  "maskedDetails": "DE89****4321"
}
```

Verfügbare `methodType`-Werte: `SEPA_DIRECT_DEBIT`, `CREDIT_CARD`, `PAYPAL`
*(genaue Liste → `PaymentMethodType`-Enum im Backend)*

**Response `204 No Content`**

**Was das Backend macht:**
- Speichert immer nur die **maskierten** Zahlungsdaten (keine echten Kartennummern)
- Ersetzt eine eventuell vorhandene Zahlungsart (1 Zahlungsart pro User)

> Das Backend speichert bewusst nur maskierte Details — echte Zahlungsabwicklung
> würde einen Payment-Provider (Stripe, etc.) erfordern, der hier nicht integriert ist.

---

### #45 — Lieferadresse sicher speichern/auswählen

**Endpunkt:** `PUT /api/users/me/delivery-address`
**Berechtigung:** Authenticated

**Request:**
```json
{
  "street": "Hauptstraße 1",
  "city": "Bielefeld",
  "postalCode": "33602",
  "country": "Germany"
}
```

**Response `204 No Content`**

**Was das Backend macht:**
- Ersetzt eine eventuell vorhandene Lieferadresse (1 Adresse pro User)

---

## Frontend-Patterns für Milestone 3

### Warenkorb laden und anzeigen

```jsx
import { useEffect, useState } from 'react';
import { apiFetch } from '../api/client';

function CartPage() {
  const [cart, setCart] = useState(null);

  useEffect(() => {
    apiFetch('/api/cart').then(setCart).catch(console.error);
  }, []);

  async function handleRemove(productId) {
    const updated = await apiFetch(`/api/cart/items/${productId}`, { method: 'DELETE' });
    setCart(updated);
  }

  async function handleOrder(couponCode) {
    const body = couponCode ? { couponCode } : {};
    const order = await apiFetch('/api/orders', {
      method: 'POST',
      body: JSON.stringify(body),
    });
    // Weiterleitung zu Bestellbestätigung
    navigate(`/orders/${order.id}`);
  }
}
```

### Artikel zum Warenkorb hinzufügen (von Produktseite)

```jsx
async function handleAddToCart(productId) {
  try {
    await apiFetch('/api/cart/items', {
      method: 'POST',
      body: JSON.stringify({ productId, quantity: 1 }),
    });
    // Erfolgs-Feedback (Toast o.ä.)
  } catch (err) {
    setError(err.message);
  }
}
```

### Zahlungsart speichern

```jsx
await apiFetch('/api/users/me/payment-method', {
  method: 'PUT',
  body: JSON.stringify({
    methodType: 'SEPA_DIRECT_DEBIT',
    maskedDetails: 'DE89****4321',
  }),
});
```
