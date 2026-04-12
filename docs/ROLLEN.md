# Rollenkonzept — Webshop Backend

Diese Datei ist die **kanonische Rollenreferenz** für das Gesamtprojekt.
Jede Milestone-Dokumentation verweist hierher für die vollständige Berechtigungsmatrix.

---

## Zwei unabhängige Konzepte

Das Backend unterscheidet zwei orthogonale Konzepte am User-Objekt:

| Feld | Typ | Bedeutung |
|------|-----|-----------|
| `role` | `UserRole` | **Berechtigung** — was darf dieser User? |
| `userType` | `UserType` | **Kundenart** — B2C oder B2B? (nur für CUSTOMERs relevant) |

---

## Rollen (`role`)

| Rolle | Wert (String) | Wer | Registrierung möglich? |
|-------|--------------|-----|------------------------|
| Kunde | `CUSTOMER` | Endkunden (Privat & Business) | **Ja** — automatisch bei `POST /api/auth/register` |
| Mitarbeiter | `EMPLOYEE` | Internes Personal (Lager, Support) | Nein — nur per Admin oder direkt in DB |
| Vertriebsmitarbeiter | `SALES_EMPLOYEE` | Vertriebsteam | Nein — nur per Admin oder direkt in DB |
| Administrator | `ADMIN` | Systemadministrator | Nein — nur per Admin oder direkt in DB |

> **Wichtig:** Die Rolle wird vom Backend vergeben, nie vom Frontend gesetzt.
> Jeder der sich über `/api/auth/register` registriert, bekommt automatisch `CUSTOMER`.
> Die einzige Ausnahme ist der Admin-Impersonation-Endpunkt, der einen Token mit der Rolle des Zielnutzers ausstellt.

---

## Kundenart (`userType`)

| Wert | Bedeutung | Wählbar bei Registrierung? |
|------|-----------|---------------------------|
| `PRIVATE` | Privatkunde (B2C) | Ja |
| `BUSINESS` | Unternehmenskunde (B2B) | Ja |

`userType` ist nur für `CUSTOMER`-Nutzer semantisch relevant. Mitarbeiter, Vertrieb und Admins
haben technisch auch einen `userType`, dieser hat aber keine geschäftliche Bedeutung.

---

## Vollständige Endpunkt-Berechtigungsmatrix

### Auth

| Methode | Endpunkt | Berechtigung | Beschreibung |
|---------|----------|--------------|--------------|
| POST | `/api/auth/login` | Public | Login → JWT-Token + User-Info |
| POST | `/api/auth/register` | Public | Registrierung → JWT-Token + User-Info (Rolle immer CUSTOMER) |
| POST | `/api/auth/logout` | Authenticated | Token blacklisten |

### Produkte

| Methode | Endpunkt | Berechtigung | Beschreibung |
|---------|----------|--------------|--------------|
| GET | `/api/products` | Public | Produktliste (Filter: `purchasable`, `category`, `search`) |
| GET | `/api/products/{id}` | Public | Einzelnes Produkt |
| GET | `/api/products/{id}/price-for-customer` | CUSTOMER | Preis nach Kundenrabatten |
| POST | `/api/products` | EMPLOYEE, SALES_EMPLOYEE, ADMIN | Produkt anlegen |
| DELETE | `/api/products/{id}` | EMPLOYEE, SALES_EMPLOYEE, ADMIN | Produkt löschen |
| PUT | `/api/products/{id}/purchasable` | EMPLOYEE, SALES_EMPLOYEE, ADMIN | Kaufbar-Flag setzen |
| PUT | `/api/products/{id}/description` | EMPLOYEE, SALES_EMPLOYEE, ADMIN | Beschreibung bearbeiten |
| PUT | `/api/products/{id}/image` | EMPLOYEE, SALES_EMPLOYEE, ADMIN | Bild-URL setzen |
| PUT | `/api/products/{id}/price` | SALES_EMPLOYEE, ADMIN | UVP bearbeiten |
| PUT | `/api/products/{id}/promoted` | SALES_EMPLOYEE, ADMIN | Promoted-Flag setzen |
| GET | `/api/products/{id}/statistics` | SALES_EMPLOYEE, ADMIN | Verkaufsstatistiken |

### Warenkorb

| Methode | Endpunkt | Berechtigung | Beschreibung |
|---------|----------|--------------|--------------|
| GET | `/api/cart` | CUSTOMER | Eigenen Warenkorb einsehen |
| POST | `/api/cart/items` | CUSTOMER | Artikel in Warenkorb legen |
| DELETE | `/api/cart/items/{productId}` | CUSTOMER | Artikel aus Warenkorb entfernen |
| POST | `/api/cart/reorder/{orderId}` | CUSTOMER | Artikel aus alter Bestellung in Warenkorb legen |

### Bestellungen

| Methode | Endpunkt | Berechtigung | Beschreibung |
|---------|----------|--------------|--------------|
| GET | `/api/orders` | CUSTOMER | Eigene Bestellhistorie |
| GET | `/api/orders/{id}` | CUSTOMER | Einzelne Bestellung |
| POST | `/api/orders` | CUSTOMER | Warenkorb bestellen |

### Benutzerprofil

| Methode | Endpunkt | Berechtigung | Beschreibung |
|---------|----------|--------------|--------------|
| GET | `/api/users/me` | Authenticated | Eigenes Profil (inkl. Kundennummer) |
| PUT | `/api/users/me/password` | Authenticated | Passwort ändern |
| PUT | `/api/users/me/email` | Authenticated | E-Mail ändern |
| DELETE | `/api/users/me` | Authenticated | Account deaktivieren (Soft-Delete) |
| PUT | `/api/users/me/delivery-address` | Authenticated | Lieferadresse speichern/ersetzen |
| PUT | `/api/users/me/payment-method` | Authenticated | Zahlungsart speichern/ersetzen |

### Daueraufträge *(Standing Orders)*

| Methode | Endpunkt | Berechtigung | Beschreibung |
|---------|----------|--------------|--------------|
| GET | `/api/standing-orders` | CUSTOMER | Eigene Daueraufträge |
| POST | `/api/standing-orders` | CUSTOMER | Dauerauftrag erstellen |
| PUT | `/api/standing-orders/{id}` | CUSTOMER | Dauerauftrag ändern |
| DELETE | `/api/standing-orders/{id}` | CUSTOMER | Dauerauftrag stornieren |

### Kundenverwaltung (Mitarbeiter-Endpunkte)

| Methode | Endpunkt | Berechtigung | Beschreibung |
|---------|----------|--------------|--------------|
| GET | `/api/customers` | EMPLOYEE, SALES_EMPLOYEE, ADMIN | Alle Kunden (Filter: `search`) |
| GET | `/api/customers/{id}` | EMPLOYEE, SALES_EMPLOYEE, ADMIN | Einzelner Kunde |
| GET | `/api/customers/{id}/cart` | EMPLOYEE, SALES_EMPLOYEE, ADMIN | Warenkorb eines Kunden einsehen |
| POST | `/api/customers/{id}/cart/items` | EMPLOYEE, SALES_EMPLOYEE, ADMIN | Artikel für Kunden in Warenkorb legen |
| DELETE | `/api/customers/{id}/cart/items/{productId}` | EMPLOYEE, SALES_EMPLOYEE, ADMIN | Artikel aus Kunden-Warenkorb entfernen |
| GET | `/api/customers/{id}/orders` | SALES_EMPLOYEE, ADMIN | Bestellhistorie eines Kunden |
| GET | `/api/customers/{id}/revenue` | SALES_EMPLOYEE, ADMIN | Umsatzstatistik eines Kunden |
| POST | `/api/customers/{id}/discounts` | SALES_EMPLOYEE, ADMIN | Rabatt vergeben |
| POST | `/api/customers/{id}/coupons` | SALES_EMPLOYEE, ADMIN | Coupon vergeben |
| GET | `/api/customers/{id}/business-info` | SALES_EMPLOYEE, ADMIN | Unternehmenskunden-Daten |
| POST | `/api/customers/{id}/email` | SALES_EMPLOYEE, ADMIN | E-Mail an Kunden senden |

### Administration

| Methode | Endpunkt | Berechtigung | Beschreibung |
|---------|----------|--------------|--------------|
| GET | `/api/admin/users` | ADMIN | Alle User (Filter: `search`) |
| DELETE | `/api/admin/users/{id}` | ADMIN | User deaktivieren |
| POST | `/api/admin/impersonate/{userId}` | ADMIN | Token im Namen eines Users ausstellen |
| GET | `/api/admin/audit-log` | ADMIN | Vollständiges Audit-Log |

---

## Frontend-Integration

### User-Objekt nach Login/Register (`useAuth()`)

```js
const { user } = useAuth();

// user ist null wenn nicht eingeloggt
user.id             // Long   — interne User-ID
user.username       // String — z.B. "alice"
user.email          // String — z.B. "alice@example.com"
user.role           // String — 'CUSTOMER' | 'EMPLOYEE' | 'SALES_EMPLOYEE' | 'ADMIN'
user.userType       // String — 'PRIVATE' | 'BUSINESS'
user.customerNumber // String | null — z.B. "100001" (nur bei CUSTOMER gesetzt)
```

### Rollenbasiertes Rendering

```jsx
const { user } = useAuth();

// Nur für eingeloggte Mitarbeiter (alle Staff-Rollen)
const isStaff = ['EMPLOYEE', 'SALES_EMPLOYEE', 'ADMIN'].includes(user?.role);

// Nur für Vertrieb + Admin
const isSalesOrAdmin = ['SALES_EMPLOYEE', 'ADMIN'].includes(user?.role);

// Beispiel: Bearbeitungs-Button nur für Staff anzeigen
{isStaff && <Button label="Artikel bearbeiten" icon="pi pi-pencil" />}

// Beispiel: Preis-Bearbeitung nur für Vertrieb/Admin
{isSalesOrAdmin && <Button label="UVP ändern" icon="pi pi-euro" />}

// Beispiel: Unternehmenskunden-spezifischer Bereich
{user?.userType === 'BUSINESS' && <BusinessInfoSection />}
```

### Seiten schützen (`ProtectedRoute`)

```jsx
// In App.jsx — Seite nur für eingeloggte User:
<Route path="/orders" element={
  <ProtectedRoute><OrdersPage /></ProtectedRoute>
} />
```

> Für rollenbasierte Route-Guards (z.B. `/admin` nur für ADMIN) den `role`-Check
> innerhalb der Seite selbst vornehmen und bei falsche Rolle auf `/` redirecten.

### Umgang mit 401 / 403

- **401 Unauthorized**: Token abgelaufen oder ungültig → zu `/login` navigieren
- **403 Forbidden**: Eingeloggt, aber falsche Rolle → Fehlermeldung anzeigen

```jsx
try {
  const data = await apiFetch('/api/orders');
} catch (err) {
  if (err.message.includes('401')) navigate('/login');
  else if (err.message.includes('403')) setError('Keine Berechtigung.');
  else setError(err.message);
}
```

---

## Seed-User (lokale Entwicklung)

Alle Passwörter: `Password1!`

| Username | E-Mail | Rolle | UserType | Kundennummer |
|----------|--------|-------|----------|--------------|
| `alice` | alice@example.com | CUSTOMER | PRIVATE | 100001 |
| `bob` | bob@example.com | CUSTOMER | BUSINESS | 100002 |
| `carol` | carol@example.com | EMPLOYEE | PRIVATE | — |
| `dave` | dave@example.com | SALES_EMPLOYEE | PRIVATE | — |
| `admin` | admin@example.com | ADMIN | PRIVATE | — |

Seed laden:
```bash
docker exec -i webshop-postgres psql -U webshop -d webshop < src/main/resources/db/dev-seed.sql
```
