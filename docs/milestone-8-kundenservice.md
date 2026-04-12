# Milestone 8: Kundenservice (Mitarbeiter)

> Vollständiges Rollenkonzept & Berechtigungsmatrix: [ROLLEN.md](./ROLLEN.md)

**Issues:** #11, #12, #21, #22, #30, #32

---

## Beteiligte Rollen

| Rolle | Relevanz |
|-------|----------|
| `EMPLOYEE` | Vollzugriff auf alle Endpunkte in diesem Milestone |
| `SALES_EMPLOYEE` | Identische Rechte wie EMPLOYEE für Kundenservice-Funktionen |
| `ADMIN` | Identische Rechte wie EMPLOYEE |

> Diese Endpunkte ermöglichen es Mitarbeitern, **telefonisch** im Namen des Kunden zu handeln:
> Warenkorb einsehen, Artikel hinzufügen/entfernen, Kunden suchen und Kundennummern nachschlagen.

---

## User Stories → Endpunkte

---

### #30 — Alle Kunden anzeigen (Mitarbeiter)
### #32 — Kunden nach Suchbegriffen filtern

**Endpunkt:** `GET /api/customers`
**Berechtigung:** EMPLOYEE, SALES_EMPLOYEE, ADMIN

**Query-Parameter:**

| Parameter | Beschreibung |
|-----------|--------------|
| `search` | Optional — Freitextsuche in Username und E-Mail |

**Beispiele:**
```
GET /api/customers                 → Alle Kunden
GET /api/customers?search=alice    → Kunden mit "alice" in Name oder E-Mail
```

**Response `200 OK`:**
```json
[
  {
    "id": 1,
    "username": "alice",
    "email": "alice@example.com",
    "role": "CUSTOMER",
    "userType": "PRIVATE",
    "customerNumber": "100001"
  },
  {
    "id": 2,
    "username": "bob",
    "email": "bob@example.com",
    "role": "CUSTOMER",
    "userType": "BUSINESS",
    "customerNumber": "100002"
  }
]
```

> Dieser Endpunkt gibt **nur Kunden** zurück (`role = CUSTOMER`).
> Für alle User-Typen → `GET /api/admin/users` (nur ADMIN).

---

### #12 — Kundennummer einsehen (Mitarbeiter)

**Endpunkt:** `GET /api/customers/{id}`
**Berechtigung:** EMPLOYEE, SALES_EMPLOYEE, ADMIN

**Response `200 OK`:** Identisch mit einem Element aus der Kundenliste (inkl. `customerNumber`).

```json
{
  "id": 1,
  "username": "alice",
  "email": "alice@example.com",
  "role": "CUSTOMER",
  "userType": "PRIVATE",
  "customerNumber": "100001"
}
```

---

### #11 — Warenkorb von Kunden einsehen

**Endpunkt:** `GET /api/customers/{id}/cart`
**Berechtigung:** EMPLOYEE, SALES_EMPLOYEE, ADMIN

**Response `200 OK`:** Identisch mit `GET /api/cart` des Kunden (CartResponse):
```json
{
  "items": [
    {
      "cartItemId": 3,
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

---

### #21 — Artikel für Kunden in Warenkorb legen

**Endpunkt:** `POST /api/customers/{id}/cart/items`
**Berechtigung:** EMPLOYEE, SALES_EMPLOYEE, ADMIN

**Request:**
```json
{
  "productId": 1,
  "quantity": 1
}
```

**Response `200 OK`:** Aktualisierter Warenkorb des Kunden (CartResponse)

**Was das Backend macht:** Identisch mit dem Kunden-Endpunkt `POST /api/cart/items`,
aber für den Kunden mit der angegebenen ID — nicht für den eingeloggten Mitarbeiter.

---

### #22 — Artikel aus Warenkorb des Kunden entfernen

**Endpunkt:** `DELETE /api/customers/{id}/cart/items/{productId}`
**Berechtigung:** EMPLOYEE, SALES_EMPLOYEE, ADMIN

**Response `200 OK`:** Aktualisierter Warenkorb des Kunden

> Der zweite Pfad-Parameter ist die **Produkt-ID** des zu entfernenden Artikels.

---

## Frontend-Patterns für Milestone 8

### Kundenservice-Seite absichern

```jsx
const { user } = useAuth();
const isStaff = ['EMPLOYEE', 'SALES_EMPLOYEE', 'ADMIN'].includes(user?.role);

useEffect(() => {
  if (user && !isStaff) navigate('/');
}, [user]);
```

### Kundensuche mit Debounce

```jsx
const [search, setSearch] = useState('');
const [customers, setCustomers] = useState([]);

useEffect(() => {
  const timeout = setTimeout(() => {
    const url = search
      ? `/api/customers?search=${encodeURIComponent(search)}`
      : '/api/customers';
    apiFetch(url).then(setCustomers).catch(console.error);
  }, 300); // 300ms Debounce
  return () => clearTimeout(timeout);
}, [search]);
```

### Warenkorb eines Kunden verwalten

```jsx
// Warenkorb laden
const cart = await apiFetch(`/api/customers/${customerId}/cart`);

// Artikel hinzufügen
const updatedCart = await apiFetch(`/api/customers/${customerId}/cart/items`, {
  method: 'POST',
  body: JSON.stringify({ productId: selectedProduct.id, quantity: 1 }),
});

// Artikel entfernen
const updatedCart = await apiFetch(
  `/api/customers/${customerId}/cart/items/${productId}`,
  { method: 'DELETE' }
);
```
