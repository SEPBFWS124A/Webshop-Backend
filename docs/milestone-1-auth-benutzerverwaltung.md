# Milestone 1: Auth & Benutzerverwaltung

> Vollständiges Rollenkonzept & Berechtigungsmatrix: [ROLLEN.md](./ROLLEN.md)

**Issues:** #1, #2, #3, #4, #5, #6, #7, #9, #56, #57

---

## Beteiligte Rollen

| Rolle | Relevanz in diesem Milestone |
|-------|------------------------------|
| (kein Login) | Kann sich registrieren und einloggen |
| `CUSTOMER` | Alle Auth-Aktionen + Profilverwaltung |
| `EMPLOYEE`, `SALES_EMPLOYEE`, `ADMIN` | Identische Auth-Aktionen wie CUSTOMER |

> Die Profil-Endpunkte (`/api/users/me/*`) funktionieren für **alle** eingeloggten Rollen.

---

## User Stories → Endpunkte

---

### #1 — Benutzername und Passwort (Login)

**Endpunkt:** `POST /api/auth/login`
**Berechtigung:** Public

**Request:**
```json
{
  "username": "alice",
  "password": "Password1!"
}
```

**Response `200 OK`:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "userId": 1,
  "username": "alice",
  "email": "alice@example.com",
  "role": "CUSTOMER",
  "userType": "PRIVATE",
  "customerNumber": "100001"
}
```

**Was das Backend macht:**
- Prüft Benutzername + BCrypt-Passwort-Hash
- Gibt einen signierten JWT zurück (24 Stunden gültig)
- Bei falschem Passwort: `401 Unauthorized`

**Frontend:**
```js
import { login } from '../api/auth';
const data = await login(username, password);
// data.token im localStorage speichern → useAuth() übernimmt das automatisch
```

---

### #2 — E-Mail Registrierung

**Endpunkt:** `POST /api/auth/register`
**Berechtigung:** Public

**Request:**
```json
{
  "username": "maxmustermann",
  "email": "max@example.com",
  "password": "MeinPasswort1!",
  "userType": "PRIVATE"
}
```

Validierung (Backend):
- `username`: 3–100 Zeichen, nicht leer
- `email`: gültiges E-Mail-Format
- `password`: mind. 8 Zeichen
- `userType`: `"PRIVATE"` oder `"BUSINESS"` (Pflichtfeld)

**Response `201 Created`:** Identisch mit Login-Response (Token + User-Info)

**Was das Backend macht:**
- Prüft ob Username/E-Mail bereits vergeben (`409 Conflict`)
- Setzt Rolle immer auf `CUSTOMER` — unabhängig von der Eingabe
- Vergibt automatisch eine Kundennummer (für CUSTOMER)

---

### #3 — Rollen

Rollen werden **nie vom Frontend vergeben**. Der `userType` (PRIVATE/BUSINESS) wird bei der
Registrierung übergeben. Die `role` (CUSTOMER) vergibt das Backend automatisch.

**Frontend:** Das Registrierungsformular zeigt einen SelectButton für den Kundentyp:
```jsx
const USER_TYPE_OPTIONS = [
  { label: 'Privatkunde', value: 'PRIVATE' },
  { label: 'Unternehmenskunde', value: 'BUSINESS' },
];
```

Nach dem Login enthält das User-Objekt beide Felder:
```js
user.role     // "CUSTOMER"
user.userType // "PRIVATE" | "BUSINESS"
```

---

### #9 — Kundennummer einsehen (als Kunde)

**Endpunkt:** `GET /api/users/me`
**Berechtigung:** Authenticated (jede Rolle)

**Response `200 OK`:**
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

> `customerNumber` ist nur bei `CUSTOMER`-Rolle gesetzt. Bei EMPLOYEE/ADMIN ist das Feld `null`.

**Frontend:** Die Kundennummer ist bereits im Login-Response enthalten (`user.customerNumber`).
Für eine dedizierte Profilseite kann `/api/users/me` aufgerufen werden.

---

### #4 — Passwort ändern

**Endpunkt:** `PUT /api/users/me/password`
**Berechtigung:** Authenticated

**Request:**
```json
{
  "currentPassword": "AltesPasswort1!",
  "newPassword": "NeuesPasswort2!"
}
```

Validierung: `newPassword` mind. 8 Zeichen

**Response `204 No Content`** (kein Body bei Erfolg)

**Fehler:**
- `400 Bad Request`: altes Passwort falsch
- `400 Bad Request`: neues Passwort zu kurz

**Frontend-Hinweis:** Kein neuer Token wird ausgestellt — der bestehende Token bleibt gültig.

---

### #5 — E-Mail verändern

**Endpunkt:** `PUT /api/users/me/email`
**Berechtigung:** Authenticated

**Request:**
```json
{
  "newEmail": "neue@email.de"
}
```

**Response `204 No Content`**

**Fehler:** `409 Conflict` wenn E-Mail bereits vergeben

**Frontend:** Nach Erfolg den User-State im AuthContext aktualisieren:
```js
updateUserEmail('neue@email.de');
```

---

### #6 — Abmelden von Benutzer

**Endpunkt:** `POST /api/auth/logout`
**Berechtigung:** Authenticated (Bearer-Token im Header)

**Request:** Kein Body — Token kommt aus dem `Authorization`-Header

**Response `204 No Content`**

**Was das Backend macht:**
- Trägt den Token in eine Blacklist ein → Token kann nicht mehr genutzt werden
- Auch nach Ablauf der 24h-Gültigkeit ist der Token damit sofort ungültig

**Frontend-Hinweis:** Selbst wenn der Backend-Call fehlschlägt, muss der Token lokal gelöscht werden.
Das ist bereits in `AuthContext.logout()` so implementiert.

---

### #7 — Deregistrieren vom System

**Endpunkt:** `DELETE /api/users/me`
**Berechtigung:** Authenticated

**Request:** Kein Body

**Response `204 No Content`**

**Was das Backend macht:**
- Setzt `active = false` am User (Soft-Delete — Daten bleiben erhalten)
- Nach der Deaktivierung: Login mit diesem Account schlägt fehl

**Frontend:** Nach Erfolg `logout()` aufrufen + zur Startseite navigieren.
Immer mit Bestätigungsdialog absichern (PrimeReact `ConfirmDialog`).

---

### #56 / #57 — Datenschutz & Datensicherheit (NFR)

Diese nicht-funktionalen Anforderungen werden durch die Architektur erfüllt:

| Anforderung | Umsetzung |
|-------------|-----------|
| Passwörter nicht im Klartext | BCrypt-Hashing im Backend, nie im Frontend gespeichert |
| Token sicher übertragen | JWT im `Authorization: Bearer`-Header, nie in der URL |
| Seiten vor unbefugtem Zugriff schützen | `<ProtectedRoute>` in App.jsx wrappen |
| Token nach Logout ungültig | Backend-Blacklist via `POST /api/auth/logout` |

---

## Frontend-Patterns für Milestone 1

### Alle Auth-API-Funktionen

```js
import { login, logout, register, getMe, updatePassword, updateEmail, deleteAccount } from '../api/auth';
```

### Login-Flow

```jsx
import { useAuth } from '../context/AuthContext';
import { useNavigate } from 'react-router-dom';

const { login } = useAuth();
const navigate = useNavigate();

try {
  await login(username, password); // speichert Token + setzt user-State
  navigate('/');
} catch (err) {
  setError(err.message.includes('401') ? 'Zugangsdaten falsch.' : err.message);
}
```

### Seite schützen

```jsx
// In App.jsx:
import ProtectedRoute from './components/auth/ProtectedRoute';

<Route path="/profile" element={
  <ProtectedRoute><ProfilePage /></ProtectedRoute>
} />
```

### Role-Check auf einer Seite

```jsx
const { user } = useAuth();

// Redirect wenn falsche Rolle
useEffect(() => {
  if (user && user.role !== 'CUSTOMER') navigate('/');
}, [user]);
```
