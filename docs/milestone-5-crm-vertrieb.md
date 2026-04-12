# Milestone 5: CRM & Vertrieb

> Vollständiges Rollenkonzept & Berechtigungsmatrix: [ROLLEN.md](./ROLLEN.md)

**Issues:** #24, #26, #27, #29, #33, #34, #35, #36, #37, #38, #43, #54

---

## Beteiligte Rollen

| Rolle | Relevanz |
|-------|----------|
| `CUSTOMER` | Sieht seinen eigenen (rabattierten) Preis via `GET /api/products/{id}/price-for-customer` |
| `SALES_EMPLOYEE` | Vollzugriff auf alle CRM-Funktionen |
| `ADMIN` | Identische Rechte wie SALES_EMPLOYEE |

---

## User Stories → Endpunkte

---

### #27 — Bestellungen eines Kunden einsehen (Analyse)

**Endpunkt:** `GET /api/customers/{id}/orders`
**Berechtigung:** SALES_EMPLOYEE, ADMIN

**Beispiel:** `GET /api/customers/1/orders`

**Response `200 OK`:** Array von OrderResponse-Objekten (identisch mit Kunden-Endpunkt, inkl. Items, Preise, Status)

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
    "items": [ ... ]
  }
]
```

---

### #29 — Umsatzstatistiken pro Kunde (Zeitraum)

**Endpunkt:** `GET /api/customers/{id}/revenue?from=YYYY-MM-DD&to=YYYY-MM-DD`
**Berechtigung:** SALES_EMPLOYEE, ADMIN

**Beispiel:** `GET /api/customers/1/revenue?from=2026-01-01&to=2026-04-12`

**Response `200 OK`:**
```json
{
  "customerId": 1,
  "from": "2026-01-01",
  "to": "2026-04-12",
  "orderCount": 5,
  "totalRevenue": 349.95
}
```

---

### #33, #54 — Rabatte vergeben (befristet / unbefristet)

**Endpunkt:** `POST /api/customers/{id}/discounts`
**Berechtigung:** SALES_EMPLOYEE, ADMIN

**Request für befristeten Rabatt (#33):**
```json
{
  "productId": 1,
  "discountPercent": 15.00,
  "validFrom": "2026-04-15",
  "validUntil": "2026-05-15"
}
```

**Request für unbefristeten Rabatt (#54):**
```json
{
  "productId": 2,
  "discountPercent": 10.00,
  "validFrom": "2026-04-15",
  "validUntil": null
}
```

Validierung: `discountPercent` zwischen 0.01 und 100.00

**Response `201 Created`:**
```json
{
  "id": 7,
  "customerId": 1,
  "productId": 2,
  "discountPercent": 10.00,
  "validFrom": "2026-04-15",
  "validUntil": null
}
```

**Was das Backend macht:**
- Der Rabatt gilt automatisch beim nächsten `GET /api/products/{id}/price-for-customer` des Kunden
- Und beim Hinzufügen zum Warenkorb

---

### #24 — Coupons vergeben

**Endpunkt:** `POST /api/customers/{id}/coupons`
**Berechtigung:** SALES_EMPLOYEE, ADMIN

**Request:**
```json
{
  "code": "SOMMER20",
  "discountPercent": 20.00,
  "validUntil": "2026-08-31"
}
```

`validUntil` ist optional (null = kein Ablaufdatum).

**Response `201 Created`** (kein Body)

**Wie der Kunde den Coupon einlöst:** Beim Checkout `POST /api/orders` mit `couponCode`-Feld.

---

### #43 — Unternehmenskunden-Daten einsehen

**Endpunkt:** `GET /api/customers/{id}/business-info`
**Berechtigung:** SALES_EMPLOYEE, ADMIN

**Response `200 OK`:**
```json
{
  "id": 1,
  "companyName": "Bob Corp GmbH",
  "industry": "Technology",
  "companySize": "10-50"
}
```

**Fehler:** `404 Not Found` wenn der Kunde kein Unternehmenskunde ist (kein Business-Info-Datensatz).

**Frontend-Tipp:** Zuerst `user.userType === 'BUSINESS'` prüfen bevor dieser Endpunkt aufgerufen wird.

---

### #37 — E-Mail an Kunden senden

**Endpunkt:** `POST /api/customers/{id}/email`
**Berechtigung:** SALES_EMPLOYEE, ADMIN

**Request:**
```json
{
  "subject": "Ihr persönliches Angebot",
  "body": "Sehr geehrte/r alice,\n\nwir haben ein besonderes Angebot für Sie..."
}
```

**Response `204 No Content`**

**Was das Backend macht:** Versendet die E-Mail via Mail-Service (lokal: Mailpit auf Port 1025).

---

### #34 — Artikel-Verkaufsstatistiken (Zeitraum)

**Endpunkt:** `GET /api/products/{id}/statistics?from=YYYY-MM-DD&to=YYYY-MM-DD`
**Berechtigung:** SALES_EMPLOYEE, ADMIN

**Response `200 OK`:**
```json
{
  "productId": 1,
  "from": "2026-01-01",
  "to": "2026-04-12",
  "unitsSold": 47,
  "totalRevenue": 61099.53
}
```

---

### #26 — Artikel promoten

Siehe [Milestone 2 — #26](./milestone-2-produktkatalog.md#26----artikel-promoten-sales_employee).

---

### #38 — Privatkunde und Unternehmenskunde unterscheiden

Kein eigener Endpunkt. Das User-Objekt enthält `userType`:

```js
user.userType // "PRIVATE" | "BUSINESS"
```

Für Unternehmenskunden-Details: `GET /api/customers/{id}/business-info`

**Frontend-Tipp:** In der Kundenliste (`GET /api/customers`) ist `userType` im Response enthalten:
```json
{ "id": 2, "username": "bob", "userType": "BUSINESS", ... }
```

---

### #35, #36 — Benachrichtigungen bei Absatzänderungen

Diese User Stories werden **vollständig vom Backend** verarbeitet:

- **#35**: Bei >20% Absatzänderung → automatische E-Mail an SALES_EMPLOYEE-Nutzer
- **#36**: Bei 0 Verkäufen eines Artikels über einen definierten Zeitraum → automatische E-Mail

**Kein Frontend-Endpunkt nötig.** Das Backend prüft dies regelmäßig und sendet E-Mails direkt.

---

## Frontend-Patterns für Milestone 5

### Kundendetails laden (Vertriebs-Dashboard)

```jsx
// Bestellhistorie
const orders = await apiFetch(`/api/customers/${customerId}/orders`);

// Umsatzstatistik
const revenue = await apiFetch(
  `/api/customers/${customerId}/revenue?from=2026-01-01&to=2026-04-12`
);

// Business-Info (nur für BUSINESS-Kunden)
if (customer.userType === 'BUSINESS') {
  const businessInfo = await apiFetch(`/api/customers/${customerId}/business-info`);
}
```

### Rabatt vergeben

```jsx
await apiFetch(`/api/customers/${customerId}/discounts`, {
  method: 'POST',
  body: JSON.stringify({
    productId: selectedProductId,
    discountPercent: 15.00,
    validFrom: '2026-04-15',
    validUntil: isLimited ? endDate : null,  // null = unbefristet (#54)
  }),
});
```

### Role-Guard für Vertriebs-Seiten

```jsx
const { user } = useAuth();
const isSalesOrAdmin = ['SALES_EMPLOYEE', 'ADMIN'].includes(user?.role);

// In einer Seite: redirect wenn keine Berechtigung
useEffect(() => {
  if (user && !isSalesOrAdmin) navigate('/');
}, [user]);
```
