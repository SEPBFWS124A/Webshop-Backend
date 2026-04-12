# Milestone 6: Administration & Audit

> Vollständiges Rollenkonzept & Berechtigungsmatrix: [ROLLEN.md](./ROLLEN.md)

**Issues:** #19, #58, #59, #60, #61, #62

---

## Beteiligte Rollen

| Rolle | Relevanz |
|-------|----------|
| `ADMIN` | Alle Endpunkte in diesem Milestone — exklusiv |

> Alle Admin-Endpunkte (`/api/admin/*`) sind mit `@PreAuthorize("hasRole('ADMIN')")` auf
> Klassen-Ebene geschützt. Jeder andere Aufruf führt zu `403 Forbidden`.

---

## User Stories → Endpunkte

---

### #59, #60 — Alle Benutzer anzeigen + nach Suchbegriffen filtern

**Endpunkt:** `GET /api/admin/users`
**Berechtigung:** ADMIN

**Query-Parameter:**

| Parameter | Beschreibung |
|-----------|--------------|
| `search` | Optional — Freitextsuche in Username und E-Mail |

**Beispiele:**
```
GET /api/admin/users               → Alle User
GET /api/admin/users?search=alice  → Suche nach "alice"
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
    "id": 5,
    "username": "admin",
    "email": "admin@example.com",
    "role": "ADMIN",
    "userType": "PRIVATE",
    "customerNumber": null
  }
]
```

> Unterschied zu `GET /api/customers`: dieser Endpunkt gibt **alle** User zurück
> (CUSTOMER, EMPLOYEE, SALES_EMPLOYEE, ADMIN), nicht nur Kunden.

---

### #61 — Benutzer deregistrieren (Admin)

**Endpunkt:** `DELETE /api/admin/users/{id}`
**Berechtigung:** ADMIN

**Response `204 No Content`**

**Was das Backend macht:**
- Setzt `active = false` (Soft-Delete — Daten bleiben erhalten)
- Schreibt einen Eintrag ins Audit-Log: `DEACTIVATE_USER` mit `initiator = ADMIN`
- Der deaktivierte User kann sich nicht mehr einloggen

---

### #19 — Admin kann jede Rolle wählen / Impersonation

**Endpunkt:** `POST /api/admin/impersonate/{userId}`
**Berechtigung:** ADMIN

**Beispiel:** `POST /api/admin/impersonate/1`

**Request:** Kein Body

**Response `200 OK`:** AuthResponse-Objekt mit Token des Ziel-Users:
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
- Stellt einen vollwertigen JWT für den Ziel-User aus
- Schreibt `IMPERSONATE_USER`-Eintrag ins Audit-Log mit `initiator = ADMIN`
- Mit diesem Token kann der Admin im Namen des Kunden alle Aktionen ausführen

**Frontend-Nutzung:**
```js
const response = await apiFetch(`/api/admin/impersonate/${targetUserId}`, { method: 'POST' });
// response.token im localStorage speichern → jetzt verhält sich die App wie der Ziel-User
localStorage.setItem('auth_token', response.token);
window.location.reload(); // AuthContext neu laden
```

---

### #58 — System-Transaktionen einsehen (Audit-Log)

**Endpunkt:** `GET /api/admin/audit-log`
**Berechtigung:** ADMIN

**Response `200 OK`:**
```json
[
  {
    "id": 1,
    "actorUsername": "admin",
    "action": "IMPERSONATE_USER",
    "entityType": "User",
    "entityId": 1,
    "initiator": "ADMIN",
    "description": "Admin impersonated user: alice",
    "timestamp": "2026-04-12T10:30:00Z"
  },
  {
    "id": 2,
    "actorUsername": "admin",
    "action": "DEACTIVATE_USER",
    "entityType": "User",
    "entityId": 3,
    "initiator": "ADMIN",
    "description": "Admin deactivated user: carol",
    "timestamp": "2026-04-12T11:00:00Z"
  }
]
```

Sortierung: neueste zuerst (`ORDER BY timestamp DESC`)

---

### #62 — Admin-Änderungen von Systemänderungen unterscheiden (NFR)

Das Audit-Log enthält das Feld `initiator`:

| Wert | Bedeutung |
|------|-----------|
| `ADMIN` | Eine Person mit Admin-Rolle hat die Aktion ausgelöst |
| `SYSTEM` | Das Backend hat die Aktion automatisch ausgelöst (z.B. Dauerauftrag-Ausführung) |

**Frontend:** Im Audit-Log-Table das `initiator`-Feld als Badge darstellen:
```jsx
<Tag
  value={log.initiator}
  severity={log.initiator === 'ADMIN' ? 'warning' : 'info'}
/>
```

---

## Frontend-Patterns für Milestone 6

### Admin-Bereich absichern

```jsx
// AdminPage.jsx
const { user } = useAuth();

useEffect(() => {
  if (user && user.role !== 'ADMIN') navigate('/');
}, [user]);
```

### Benutzerliste mit Suche

```jsx
const [search, setSearch] = useState('');
const [users, setUsers] = useState([]);

useEffect(() => {
  const url = search
    ? `/api/admin/users?search=${encodeURIComponent(search)}`
    : '/api/admin/users';
  apiFetch(url).then(setUsers).catch(console.error);
}, [search]);
```

### Audit-Log laden

```jsx
useEffect(() => {
  apiFetch('/api/admin/audit-log').then(setAuditLogs).catch(console.error);
}, []);
```

### Impersonation

```jsx
async function handleImpersonate(userId) {
  const response = await apiFetch(`/api/admin/impersonate/${userId}`, { method: 'POST' });
  localStorage.setItem('auth_token', response.token);
  window.location.href = '/'; // vollständiger Reload, damit AuthContext neu lädt
}
```
