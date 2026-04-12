# Milestone 4: Bestellhistorie & Daueraufträge

> Vollständiges Rollenkonzept & Berechtigungsmatrix: [ROLLEN.md](./ROLLEN.md)

**Issues:** #48, #49, #50, #51, #52, #53, #55

---

## Beteiligte Rollen

| Rolle | Relevanz |
|-------|----------|
| `CUSTOMER` | Eigene Bestellhistorie + Daueraufträge verwalten |
| `SALES_EMPLOYEE`, `ADMIN` | Bestellhistorie von Kunden einsehen → [Milestone 5](./milestone-5-crm-vertrieb.md) |

---

## User Stories → Endpunkte

---

### #48 — Frühere Bestellungen einsehen

**Endpunkt:** `GET /api/orders`
**Berechtigung:** CUSTOMER

**Response `200 OK`:**
```json
[
  {
    "id": 42,
    "status": "DELIVERED",
    "totalPrice": 35.69,
    "taxAmount": 5.70,
    "shippingCost": 0.00,
    "couponCode": null,
    "createdAt": "2026-03-15T08:30:00Z",
    "items": [
      {
        "orderItemId": 1,
        "productId": 2,
        "productName": "Wireless Mouse",
        "productPurchasable": true,
        "quantity": 1,
        "priceAtOrderTime": 29.99,
        "lineTotal": 29.99
      }
    ]
  }
]
```

Mögliche `status`-Werte: `PROCESSING`, `SHIPPED`, `DELIVERED`, `CANCELLED`

---

### #49 — Kaufbare Artikel in alten Bestellungen erkennen

**Endpunkt:** `GET /api/orders/{id}`
**Berechtigung:** CUSTOMER

**Response:** Identisch mit einem Element aus der Listabfrage.

**Was das Backend macht:**
- Das Feld `productPurchasable` zeigt den **aktuellen** Kaufbar-Status des Artikels
- Wenn `productPurchasable: false` → Artikel kann nicht erneut bestellt werden

**Frontend-Tipp:** Den "Erneut bestellen"-Button nur anzeigen wenn alle Artikel
in der Bestellung `productPurchasable: true` haben (oder item-by-item prüfen).

---

### #50 — Artikel aus alter Bestellung erneut in Warenkorb legen

**Endpunkt:** `POST /api/cart/reorder/{orderId}`
**Berechtigung:** CUSTOMER

**Request:** Kein Body

**Response `200 OK`:** Aktualisierter Warenkorb (CartResponse)

**Was das Backend macht:**
- Fügt alle Artikel aus der Bestellung, die noch **kaufbar** sind, in den Warenkorb
- Nicht mehr kaufbare Artikel werden übersprungen (kein Fehler)
- Existierende Warenkorb-Artikel werden nicht überschrieben (Menge wird addiert)

---

### #51 — Dauerauftrag erstellen

**Endpunkt:** `POST /api/standing-orders`
**Berechtigung:** CUSTOMER

**Request:**
```json
{
  "intervalDays": 30,
  "firstExecutionDate": "2026-05-01",
  "items": [
    { "productId": 2, "quantity": 3 },
    { "productId": 4, "quantity": 1 }
  ]
}
```

Validierung: `intervalDays` mind. 1, `items` nicht leer, `quantity` mind. 1

**Response `201 Created`:**
```json
{
  "id": 5,
  "intervalDays": 30,
  "nextExecutionDate": "2026-05-01",
  "active": true,
  "createdAt": "2026-04-12T10:00:00Z",
  "items": [
    { "id": 1, "productId": 2, "productName": "Wireless Mouse", "quantity": 3 },
    { "id": 2, "productId": 4, "productName": "USB-C Hub", "quantity": 1 }
  ]
}
```

---

### #52 — Dauerauftrag stornieren

**Endpunkt:** `DELETE /api/standing-orders/{id}`
**Berechtigung:** CUSTOMER

**Response `204 No Content`**

---

### #53 — Dauerauftrag ändern

**Endpunkt:** `PUT /api/standing-orders/{id}`
**Berechtigung:** CUSTOMER

**Request:**
```json
{
  "intervalDays": 14,
  "items": [
    { "productId": 2, "quantity": 5 }
  ]
}
```

**Response `200 OK`:** Aktualisierter Dauerauftrag (StandingOrderResponse)

---

### #55 — Benachrichtigung bei nicht mehr verfügbarem Dauerauftrag-Artikel

**Kein Frontend-Endpunkt nötig.** Das Backend versendet automatisch eine E-Mail
wenn ein Artikel in einem aktiven Dauerauftrag auf `purchasable = false` gesetzt wird.

**Frontend-Hinweis:** Beim Laden der Daueraufträge kann `GET /api/standing-orders` verwendet
werden — in der Response steht `"active": false` wenn der Dauerauftrag deaktiviert wurde.

---

### Daueraufträge auflisten

**Endpunkt:** `GET /api/standing-orders`
**Berechtigung:** CUSTOMER

**Response `200 OK`:** Array von StandingOrderResponse-Objekten (Struktur wie bei POST-Response)

---

## Frontend-Patterns für Milestone 4

### Bestellhistorie laden

```jsx
useEffect(() => {
  apiFetch('/api/orders').then(setOrders).catch(console.error);
}, []);
```

### Erneut bestellen

```jsx
async function handleReorder(orderId) {
  try {
    const cart = await apiFetch(`/api/cart/reorder/${orderId}`, { method: 'POST' });
    // Erfolg: zum Warenkorb navigieren
    navigate('/cart');
  } catch (err) {
    setError(err.message);
  }
}
```

### Daueraufträge verwalten

```jsx
// Laden
const standingOrders = await apiFetch('/api/standing-orders');

// Erstellen
const newOrder = await apiFetch('/api/standing-orders', {
  method: 'POST',
  body: JSON.stringify({
    intervalDays: 30,
    firstExecutionDate: '2026-05-01',
    items: [{ productId: 2, quantity: 3 }],
  }),
});

// Löschen
await apiFetch(`/api/standing-orders/${id}`, { method: 'DELETE' });
```
