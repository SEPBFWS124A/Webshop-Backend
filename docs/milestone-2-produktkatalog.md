# Milestone 2: Produktkatalog

> Vollständiges Rollenkonzept & Berechtigungsmatrix: [ROLLEN.md](./ROLLEN.md)

**Issues:** #8, #10, #13, #14, #15, #16, #17, #18, #20, #23, #25, #26, #28, #31, #46, #47

---

## Beteiligte Rollen

| Rolle | Was sie im Produktkatalog darf |
|-------|-------------------------------|
| (kein Login) | Öffentliche Produktliste + Einzelansicht lesen |
| `CUSTOMER` | Öffentliche Produktliste + kundenspezifischen Preis abrufen |
| `EMPLOYEE` | Alle Produkte sehen + Artikel anlegen/löschen/kaufbar setzen/Beschreibung+Bild bearbeiten |
| `SALES_EMPLOYEE` | Alles was EMPLOYEE darf + UVP bearbeiten + Promoted setzen + Verkaufsstatistiken |
| `ADMIN` | Alle Rechte wie SALES_EMPLOYEE |

---

## User Stories → Endpunkte

---

### #8 — Gesamtes kaufbares Sortiment sehen (Kunde)
### #46 — Sortiment nach Suchbegriffen filtern
### #47 — Sortiment nach Kategorien filtern

**Endpunkt:** `GET /api/products`
**Berechtigung:** Public

**Query-Parameter:**

| Parameter | Typ | Beschreibung |
|-----------|-----|--------------|
| `purchasable` | boolean | `true` = nur kaufbare Artikel (für Kunden), `false` oder leer = alle |
| `search` | string | Freitextsuche in Name + Beschreibung |
| `category` | string | Exakte Kategorie (z.B. `Electronics`, `Furniture`) |

**Beispiele:**
```
GET /api/products?purchasable=true                        → Nur kaufbare Artikel
GET /api/products?purchasable=true&search=laptop          → Suche nach "laptop"
GET /api/products?purchasable=true&category=Electronics   → Kategorie Electronics
GET /api/products                                         → Alle (auch nicht-kaufbare)
```

**Response `200 OK`:**
```json
[
  {
    "id": 1,
    "name": "Laptop Pro 15",
    "description": "High-performance laptop with 15\" display",
    "imageUrl": "https://placehold.co/400x300?text=Laptop",
    "recommendedRetailPrice": 1299.99,
    "category": "Electronics",
    "purchasable": true,
    "promoted": true,
    "createdAt": "2026-04-01T10:00:00Z"
  }
]
```

**Frontend-Tipp:** Für die Kundenansicht immer `?purchasable=true` setzen.
Für Mitarbeiteransicht (#10, #20) ohne den Parameter aufrufen.

---

### #23 — Produktbeschreibung sehen
### #25 — Produktbild sehen
### #28 — UVP sehen

**Endpunkt:** `GET /api/products/{id}`
**Berechtigung:** Public

**Response `200 OK`:** Identisch mit dem einzelnen Objekt aus der Listenabfrage (alle Felder enthalten).

```json
{
  "id": 1,
  "name": "Laptop Pro 15",
  "description": "High-performance laptop with 15\" display",
  "imageUrl": "https://placehold.co/400x300?text=Laptop",
  "recommendedRetailPrice": 1299.99,
  "category": "Electronics",
  "purchasable": true,
  "promoted": false,
  "createdAt": "2026-04-01T10:00:00Z"
}
```

---

### #31 — Preis mit Rabatten sehen (Kunde)

**Endpunkt:** `GET /api/products/{id}/price-for-customer`
**Berechtigung:** CUSTOMER

**Response `200 OK`:**
```json
{
  "productId": 2,
  "recommendedRetailPrice": 29.99,
  "effectivePrice": 26.99,
  "discountPercent": 10.00
}
```

**Was das Backend macht:**
- Sucht alle aktiven Rabatte für diesen Kunden auf dieses Produkt
- Berechnet den günstigsten Preis
- Wenn kein Rabatt: `effectivePrice == recommendedRetailPrice`, `discountPercent == 0`

**Frontend-Tipp:** Diesen Endpunkt auf der Produktdetailseite aufrufen wenn der User eingeloggt ist
und die Rolle `CUSTOMER` hat. Für nicht eingeloggte User die UVP aus `GET /api/products/{id}` anzeigen.

---

### #13 — Artikel zum Sortiment hinzufügen (EMPLOYEE+)

**Endpunkt:** `POST /api/products`
**Berechtigung:** EMPLOYEE, SALES_EMPLOYEE, ADMIN

**Request:**
```json
{
  "name": "Neues Produkt",
  "description": "Produktbeschreibung",
  "imageUrl": "https://example.com/bild.jpg",
  "recommendedRetailPrice": 49.99,
  "category": "Electronics"
}
```

Pflichtfelder: `name`, `recommendedRetailPrice` (mind. 0.01). Alle anderen optional.

**Response `201 Created`:** Vollständiges Produkt-Objekt

---

### #14 — Artikel vom Sortiment entfernen (EMPLOYEE+)

**Endpunkt:** `DELETE /api/products/{id}`
**Berechtigung:** EMPLOYEE, SALES_EMPLOYEE, ADMIN

**Response `204 No Content`**

---

### #15 — Artikel kaufbar markieren (EMPLOYEE+)

**Endpunkt:** `PUT /api/products/{id}/purchasable`
**Berechtigung:** EMPLOYEE, SALES_EMPLOYEE, ADMIN

**Request:**
```json
{ "value": true }
```

**Response `200 OK`:** Aktualisiertes Produkt-Objekt

---

### #16 — Beschreibung bearbeiten (EMPLOYEE+)

**Endpunkt:** `PUT /api/products/{id}/description`
**Berechtigung:** EMPLOYEE, SALES_EMPLOYEE, ADMIN

**Request:**
```json
{ "description": "Neue ausführliche Beschreibung des Produkts." }
```

**Response `200 OK`:** Aktualisiertes Produkt-Objekt

---

### #17 — Bild bearbeiten (EMPLOYEE+)

**Endpunkt:** `PUT /api/products/{id}/image`
**Berechtigung:** EMPLOYEE, SALES_EMPLOYEE, ADMIN

**Request:**
```json
{ "imageUrl": "https://example.com/neues-bild.jpg" }
```

**Response `200 OK`:** Aktualisiertes Produkt-Objekt

> Das Backend speichert nur die URL — kein Datei-Upload. Die Bild-URL muss extern gehostet sein.

---

### #18 — UVP bearbeiten (SALES_EMPLOYEE+)

**Endpunkt:** `PUT /api/products/{id}/price`
**Berechtigung:** SALES_EMPLOYEE, ADMIN

**Request:**
```json
{ "recommendedRetailPrice": 59.99 }
```

**Response `200 OK`:** Aktualisiertes Produkt-Objekt

---

### #26 — Artikel promoten (SALES_EMPLOYEE+)

**Endpunkt:** `PUT /api/products/{id}/promoted`
**Berechtigung:** SALES_EMPLOYEE, ADMIN

**Request:**
```json
{ "value": true }
```

**Response `200 OK`:** Aktualisiertes Produkt-Objekt

**Frontend-Tipp:** Promoted-Artikel auf der Startseite/Landingpage hervorheben.
Im Response-Objekt steht `"promoted": true/false`.

---

### #34 — Artikel-Verkaufsstatistiken (SALES_EMPLOYEE+)

**Endpunkt:** `GET /api/products/{id}/statistics?from=YYYY-MM-DD&to=YYYY-MM-DD`
**Berechtigung:** SALES_EMPLOYEE, ADMIN

**Beispiel:**
```
GET /api/products/1/statistics?from=2026-01-01&to=2026-04-12
```

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

### #10, #20 — Sortiment als Mitarbeiter einsehen (inkl. nicht-kaufbarer Artikel)

Kein eigener Endpunkt — `GET /api/products` ohne `purchasable`-Parameter aufrufen.
Das gibt alle Produkte zurück, inklusive derer mit `"purchasable": false`.

---

## Frontend-Patterns für Milestone 2

### Produktliste laden

```jsx
import { useState, useEffect } from 'react';
import { apiFetch } from '../api/client';

function ProductListPage() {
  const [products, setProducts] = useState([]);
  const { user } = useAuth();

  useEffect(() => {
    // Kunden sehen nur kaufbare, Mitarbeiter alle
    const isStaff = ['EMPLOYEE', 'SALES_EMPLOYEE', 'ADMIN'].includes(user?.role);
    const url = isStaff ? '/api/products' : '/api/products?purchasable=true';
    apiFetch(url).then(setProducts).catch(console.error);
  }, [user]);
}
```

### Kundenpreis abrufen

```jsx
const { user } = useAuth();

useEffect(() => {
  if (user?.role === 'CUSTOMER') {
    apiFetch(`/api/products/${productId}/price-for-customer`)
      .then(setPriceInfo)
      .catch(console.error);
  }
}, [productId, user]);
```

### Bearbeitungs-Buttons nur für Staff

```jsx
const isStaff = ['EMPLOYEE', 'SALES_EMPLOYEE', 'ADMIN'].includes(user?.role);
const isSalesOrAdmin = ['SALES_EMPLOYEE', 'ADMIN'].includes(user?.role);

{isStaff && (
  <>
    <Button label="Beschreibung bearbeiten" onClick={handleEditDescription} />
    <Button label="Bild ändern" onClick={handleEditImage} />
    <Button label="Kaufbar" onClick={() => togglePurchasable(product.id)} />
  </>
)}

{isSalesOrAdmin && (
  <>
    <Button label="UVP bearbeiten" onClick={handleEditPrice} />
    <Button label="Promoten" onClick={() => togglePromoted(product.id)} />
  </>
)}
```
