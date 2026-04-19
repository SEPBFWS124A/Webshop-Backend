# Webshop Backend — Windows (PowerShell)

> **Linux-Nutzer:** → [README-Linux.md](README-Linux.md)

Spring Boot Backend für den Webshop. Stellt eine REST API bereit, die vom React-Frontend unter [SEPBFWS124A/Webshop](https://github.com/SEPBFWS124A/Webshop) verwendet wird.

> Alle Befehle in diesem Dokument sind für **Windows PowerShell** geschrieben.

---

## Inhaltsverzeichnis

1. [Architektur-Überblick](#1-architektur-überblick)
2. [Technologie-Stack](#2-technologie-stack)
3. [Architektur im Detail — was macht was?](#3-architektur-im-detail--was-macht-was)
4. [Voraussetzungen](#4-voraussetzungen)
5. [Lokales Setup](#5-lokales-setup)
6. [Projektstruktur](#6-projektstruktur)
7. [Datenbank & Migrations](#7-datenbank--migrations)
8. [API-Dokumentation (OpenAPI)](#8-api-dokumentation-openapi)
9. [Frontend-Integration](#9-frontend-integration)
10. [Umgebungsvariablen](#10-umgebungsvariablen)
11. [Git-Workflow im Team](#11-git-workflow-im-team)
12. [Lokales Testen (curl-Beispiele)](#12-lokales-testen-curl-beispiele)
13. [User Story Abdeckung](#13-user-story-abdeckung)
14. [Deployment](#14-deployment)

---

## 1. Architektur-Überblick

```
Browser
  └── Frontend (React + Vite, Port 5173)
        └──[HTTP / REST API / JSON]──► Backend (Spring Boot, Port 8080)
                                            ├──[JPA / SQL]──► PostgreSQL (Port 5432)
                                            └──[REST]──────► Ollama (Port 11434, lokal)
```

Das Frontend schickt HTTP-Anfragen an das Backend (z.B. `GET /api/products`).
Das Backend verarbeitet die Anfrage, liest/schreibt in die Datenbank und antwortet mit JSON.
Jede Anfrage wird über einen JWT-Token authentifiziert (außer Login/Register).

---

## 2. Technologie-Stack

| Technologie | Zweck | Version |
|---|---|---|
| Java | Programmiersprache (läuft im Docker-Container) | 21 |
| Maven | Build-Tool (läuft im Docker-Container, kein lokales Maven nötig) | 3.9.9 |
| Spring Boot | Framework (Web, Security, JPA) | 3.4.4 |
| PostgreSQL | Datenbank | 16 |
| Flyway | Datenbank-Migrations | - |
| JWT (jjwt) | Authentifizierung | 0.12.6 |
| springdoc-openapi | OpenAPI Spec generieren | 2.8.6 |
| Docker + Compose | Lokale Entwicklungsumgebung | - |
| Ollama | Lokale KI-Laufzeitumgebung für Shoppi | latest |
| NGINX | Reverse Proxy + HTTPS (Produktion) | - |
| GitHub Actions | CI/CD-Pipelines | - |

---

## 3. Architektur im Detail — was macht was?

### Java 21 — die Sprache
Die Basis von allem. Java ist stark typisiert, was bei einem größeren Team Fehler früh erkennt. Version 21 ist die aktuelle LTS-Version (Long Term Support) — die empfohlene stabile Version für neue Projekte.

### Maven — das Build-Tool
Entspricht `npm` im Frontend. Verwaltet Abhängigkeiten (`pom.xml` = `package.json`), baut das Projekt und führt Tests aus. Maven läuft vollständig **innerhalb des Docker-Containers** — kein lokales Java oder Maven nötig. In der CI-Pipeline (GitHub Actions) läuft Maven direkt, da dort Java verfügbar ist.

### Spring Boot — das Framework
Das Herzstück. Spring Boot bündelt mehrere Sub-Frameworks:

**Spring Web** stellt den HTTP-Server bereit und ermöglicht REST-Endpunkte:
```java
@GetMapping("/api/products")
public List<Product> getAll() { ... }
```

**Spring Security** schützt Endpunkte — prüft bei jeder Anfrage ob der Nutzer eingeloggt ist und die nötige Rolle hat (CUSTOMER, EMPLOYEE, SALES_EMPLOYEE, ADMIN).

**Spring Data JPA** ist eine Abstraktionsschicht über die Datenbank. Anstatt SQL von Hand zu schreiben, definiert man ein Interface — Spring generiert die SQL-Abfragen automatisch:
```java
// Spring generiert daraus: SELECT * FROM products WHERE name LIKE ?
List<Product> findByNameContaining(String keyword);
```

### JWT (JSON Web Token) — Authentifizierung
Wenn ein Nutzer sich einloggt, bekommt er einen Token — einen langen verschlüsselten String. Bei jeder weiteren Anfrage schickt das Frontend diesen Token mit:
```
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```
Das Backend prüft den Token und weiß damit: wer ist der Nutzer, welche Rolle hat er. Kein Session-Speicher nötig — der Token trägt alle Infos selbst. Token-Gültigkeit: 24h (konfigurierbar via `JWT_EXPIRATION_MS`).

**Logout** invalidiert den Token serverseitig über eine In-Memory-Blacklist (`TokenBlacklist.java`).

### PostgreSQL — die Datenbank
Relationale Datenbank: Daten liegen in Tabellen mit Zeilen und Spalten, verknüpft über IDs. Läuft als eigener Prozess (lokal via Docker). Alle persistenten Daten landen hier: Nutzer, Artikel, Bestellungen, Warenkorb.

### Flyway — Datenbank-Migrations
Versioniert die Datenbankstruktur — ähnlich wie Git für den Code. Jede Schemaänderung wird als nummerierte SQL-Datei angelegt:
```
V1__create_users.sql
V2__create_user_details.sql
...
V9__create_audit_log.sql
```
Beim Start führt Spring Boot automatisch alle noch nicht ausgeführten Migrations aus. So hat jedes Teammitglied automatisch dieselbe Datenbankstruktur.

### Docker & Docker Compose — lokale Entwicklung
Docker lässt PostgreSQL in einem isolierten Container laufen — kein manuelles Installieren nötig. Das `dev.bat`-Script startet den Container automatisch vor dem Backend.

### springdoc-openapi — API-Beschreibung
Liest den Spring Boot Code und generiert automatisch eine maschinenlesbare Beschreibung aller Endpunkte (`/v3/api-docs`). Das Frontend-Team kann daraus ablesen wie ein API-Call aussehen muss, ohne beim Backend-Team nachzufragen.

### Ollama — lokale KI-Laufzeitumgebung

Ollama führt KI-Sprachmodelle lokal auf dem Server aus. Dadurch werden keine Daten an Drittanbieter wie OpenAI oder Google Cloud gesendet. Das Backend kommuniziert intern über `http://ollama:11434` mit dem Ollama-Container.

Das verwendete Modell `gemma4:e4b` (Google DeepMind) wird beim ersten Start manuell gezogen (einmalig, ~3 GB):
```bash
docker exec webshop-ollama ollama pull gemma4:e4b
```

**Shoppi** ist der KI-Assistent des Webshops. Der Endpunkt `POST /api/chat/message` ist öffentlich, aber auth-aware: eingeloggte Kunden erhalten einen personalisierten Kontext (Warenkorb, Bestellhistorie, Profil) im System-Prompt.

---

### GitHub Actions — CI/CD
Zwei Pipelines:
- **CI**: Bei jedem Pull Request wird automatisch `mvn test` ausgeführt.
- **CD**: Bei jedem Merge in `main` wird das Backend automatisch deployed.

### NGINX — Webserver / Reverse Proxy (Produktion)
Sitzt auf dem Server vor allem anderen und verteilt eingehende Anfragen:
```
https://domain.de/       → Frontend (statische React-Files aus dist/)
https://domain.de/api/   → Backend (Spring Boot, intern Port 8080)
```

---

## 4. Voraussetzungen

Einzige Voraussetzung: **Docker Desktop**

Das Backend (Spring Boot + Java 21) und die Datenbank (PostgreSQL) laufen vollständig in Docker-Containern — kein Java und kein Maven lokal nötig.

```bash
docker --version
docker compose version
```
Download: https://www.docker.com/products/docker-desktop

### Empfohlene IDE
**VS Code** mit den Extensions:
- [Extension Pack for Java](https://marketplace.visualstudio.com/items?itemName=vscjava.vscode-java-pack)
- [Spring Boot Extension Pack](https://marketplace.visualstudio.com/items?itemName=vmware.vscode-boot-dev-pack)

Alternativ: **IntelliJ IDEA** (Community Edition reicht) mit Spring Boot Plugin.

---

## 5. Lokales Setup

### Schritt 1 — Repository klonen
```bash
git clone https://github.com/SEPBFWS124A/Webshop-Backend.git
cd Webshop-Backend
```

### Schritt 2 — Backend starten
```bat
./dev.bat start
```

Das Script baut und startet alle Container automatisch:
1. PostgreSQL-Container starten (wartet bis er `healthy` ist)
2. Ollama-Container starten
3. Spring Boot-Image bauen (Maven läuft im Container — kein Java lokal nötig)
4. Backend-Container starten und auf `/api/health` warten

Das Backend ist dann erreichbar unter: `http://localhost:8080`

> **Erster Start:** Maven lädt alle Dependencies (~200 MB) **innerhalb des Containers** herunter.
> Das kann beim ersten Mal einige Minuten dauern. Nachfolgende Starts nutzen den Docker Layer-Cache
> und sind deutlich schneller.

**Alle Befehle im Überblick:**

| Befehl | Was passiert |
|--------|-------------|
| `./dev.bat start` | PostgreSQL + Backend bauen und starten |
| `./dev.bat stop` | Alle Container beenden |
| `./dev.bat stop --keep-db` | Nur Backend-Container beenden, PostgreSQL läuft weiter |
| `./dev.bat restart` | Backend-Container neu starten |
| `./dev.bat restart --keep-db` | Neustart ohne PostgreSQL-Neustart |
| `./dev.bat rebuild` | Alle Container neu bauen (kein Layer-Cache) + starten |
| `./dev.bat rebuild --keep-db` | Rebuild Backend ohne PostgreSQL-Neustart |

> **Wann `./dev.bat rebuild` statt `./dev.bat restart`?**
> Nach Änderungen am `Dockerfile` oder wenn der Layer-Cache einen veralteten Stand hat.
> Für normale Code-Änderungen reicht `restart`.

### Schritt 3 — Ollama-Modell ziehen (einmalig, nur für Shoppi KI-Assistent)
```bash
docker exec webshop-ollama ollama pull gemma4:e4b
```
Dieser Download (~10 GB) ist nur einmalig nötig. Das Modell wird im Docker-Volume `ollama_data` gecacht.

> **Fehler: „pull model manifest: 412 — requires a newer version of Ollama"?**
> Das lokale Ollama-Image ist veraltet (z.B. von einer früheren Installation). Image updaten (~4 GB) und Container neu starten:
> ```bash
> docker pull ollama/ollama:latest
> docker compose up -d --no-deps ollama
> docker exec webshop-ollama ollama pull gemma4:e4b
> ```

### Schritt 4 — Testdaten einspielen (einmalig)
```bat
docker exec -i webshop-postgres psql -U webshop -d webshop < src/main/resources/db/dev-seed.sql
```

### Schritt 5 — Verifizieren
```bash
curl http://localhost:8080/api/health
# Erwartet: {"status":"UP"}

curl http://localhost:8080/api/products
# Erwartet: [...] (Liste der Produkte aus dem Seed)
```

---

## 6. Projektstruktur

```
src/
└── main/
    ├── java/de/fhdw/webshop/
    │   ├── WebshopApplication.java         ← Einstiegspunkt (main-Methode)
    │   │
    │   ├── config/                         ← Konfigurationsklassen
    │   │   ├── SecurityConfig.java         ← Spring Security, JWT-Filter, CORS
    │   │   ├── CorsConfig.java
    │   │   ├── JwtAuthenticationFilter.java
    │   │   ├── GlobalExceptionHandler.java ← einheitliche Fehlerantworten
    │   │   └── HealthController.java       ← GET /api/health
    │   │
    │   ├── auth/                           ← Login, Registrierung, JWT
    │   │   ├── AuthController.java         ← POST /api/auth/login, /register, /logout
    │   │   ├── AuthService.java
    │   │   ├── JwtTokenProvider.java       ← Token generieren & validieren
    │   │   ├── TokenBlacklist.java         ← Logout-Invalidierung
    │   │   └── dto/
    │   │
    │   ├── user/                           ← Benutzer, Adressen, Zahlungsarten
    │   │   ├── User.java                   ← Entity + UserDetails
    │   │   ├── UserRepository.java
    │   │   ├── UserService.java
    │   │   ├── UserController.java         ← PUT /api/users/me/...
    │   │   ├── DeliveryAddress.java
    │   │   ├── PaymentMethod.java
    │   │   ├── BusinessInfo.java
    │   │   └── dto/
    │   │
    │   ├── product/                        ← Artikelkatalog
    │   │   ├── Product.java
    │   │   ├── ProductRepository.java
    │   │   ├── ProductService.java         ← inkl. DiscountLookupPort Interface
    │   │   ├── ProductController.java      ← GET/POST/PUT/DELETE /api/products
    │   │   └── dto/
    │   │
    │   ├── cart/                           ← Warenkorb
    │   │   ├── CartItem.java
    │   │   ├── CartRepository.java
    │   │   ├── CartService.java
    │   │   ├── CartController.java         ← /api/cart
    │   │   └── dto/
    │   │
    │   ├── order/                          ← Bestellungen
    │   │   ├── Order.java
    │   │   ├── OrderItem.java
    │   │   ├── OrderRepository.java
    │   │   ├── OrderService.java
    │   │   ├── OrderController.java        ← /api/orders
    │   │   └── dto/
    │   │
    │   ├── discount/                       ← Rabatte & Coupons
    │   │   ├── Discount.java
    │   │   ├── Coupon.java
    │   │   ├── DiscountRepository.java
    │   │   ├── CouponRepository.java
    │   │   ├── DiscountService.java        ← implementiert DiscountLookupPort
    │   │   └── dto/
    │   │
    │   ├── customer/                       ← Mitarbeiter-Endpunkte für Kunden
    │   │   ├── CustomerController.java     ← /api/customers
    │   │   ├── StatisticsService.java
    │   │   └── RevenueStatisticsResponse.java
    │   │
    │   ├── standingorder/                  ← Daueraufträge
    │   │   ├── StandingOrder.java
    │   │   ├── StandingOrderItem.java
    │   │   ├── StandingOrderRepository.java
    │   │   ├── StandingOrderService.java
    │   │   ├── StandingOrderController.java ← /api/standing-orders
    │   │   ├── StandingOrderScheduler.java  ← @Scheduled (täglich 06:00)
    │   │   └── dto/
    │   │
    │   ├── notification/                   ← E-Mail & Benachrichtigungen
    │   │   ├── EmailService.java
    │   │   ├── NotificationScheduler.java  ← @Scheduled (montags 07:00)
    │   │   └── dto/
    │   │
    │   ├── chat/                           ← Shoppi KI-Assistent
    │   │   ├── ChatController.java         ← POST /api/chat/message (public, auth-aware)
    │   │   ├── ChatService.java            ← System-Prompt bauen + Ollama aufrufen
    │   │   ├── OllamaClient.java           ← HTTP-Client für Ollama REST API
    │   │   └── dto/
    │   │       ├── ChatMessageRequest.java
    │   │       ├── ChatMessageResponse.java
    │   │       └── ConversationEntry.java
    │   │
    │   └── admin/                          ← Admin-Endpunkte & Audit-Log
    │       ├── AuditLog.java
    │       ├── AuditLogRepository.java
    │       ├── AuditLogService.java
    │       └── AdminController.java        ← /api/admin
    │
    └── resources/
        ├── application.properties
        └── db/
            ├── migration/                  ← Flyway SQL-Migrations (V1–V9)
            └── dev-seed.sql                ← Testdaten für lokale Entwicklung
```

### Wie ein Package aufgebaut ist

Jedes Feature-Package (z.B. `product/`) enthält immer dieselben Typen:

| Datei | Aufgabe |
|---|---|
| `Product.java` | **Entity** — repräsentiert eine Zeile in der Datenbank-Tabelle |
| `ProductRepository.java` | **Repository** — Datenbankzugriff (SQL wird von Spring generiert) |
| `ProductService.java` | **Service** — Geschäftslogik (was darf wer, wie wird berechnet) |
| `ProductController.java` | **Controller** — HTTP-Endpunkte (welche URL macht was) |

```
HTTP-Anfrage → Controller → Service → Repository → Datenbank
HTTP-Antwort ← Controller ← Service ← Repository ← Datenbank
```

---

## 7. Datenbank & Migrations

### Schema-Übersicht

| Migration | Tabellen |
|---|---|
| V1 | `users` (id, username, email, password_hash, role, user_type, customer_number, active) |
| V2 | `delivery_addresses`, `payment_methods`, `business_info` |
| V3 | `products` (id, name, description, image_url, recommended_retail_price, category, purchasable, promoted) |
| V4 | `discounts` (customer_id, product_id, discount_percent, valid_from, valid_until) |
| V5 | `coupons` (customer_id, code, discount_percent, valid_until, used) |
| V6 | `cart_items` (user_id, product_id, quantity) |
| V7 | `orders`, `order_items` (price_at_order_time, quantity) |
| V8 | `standing_orders`, `standing_order_items` |
| V9 | `audit_log` (user_id, action, entity_type, initiated_by [USER/ADMIN/SYSTEM]) |

### Neue Migration erstellen
1. Neue Datei unter `src/main/resources/db/migration/` anlegen
2. Nächste freie Nummer verwenden: `V10__beschreibung.sql`
3. Backend neu starten → Flyway führt die Migration automatisch aus

**Wichtig:** Bestehende Migration-Dateien **niemals ändern** — immer eine neue anlegen.

---

## 8. API-Dokumentation (OpenAPI + Swagger UI)

Das Backend generiert automatisch eine **OpenAPI Spec** — eine maschinenlesbare Beschreibung aller Endpunkte — und stellt optional eine **Swagger UI** bereit, mit der Endpunkte direkt im Browser getestet werden können.

### Milestone-Dokumentation für das Frontend-Team

Im `docs/`-Ordner dieses Repos liegt eine ausführliche Dokumentation für jedes Frontend-Milestone:

| Datei | Inhalt |
|-------|--------|
| [`docs/ROLLEN.md`](docs/ROLLEN.md) | Rollenkonzept, vollständige Berechtigungsmatrix, Frontend-Patterns |
| [`docs/milestone-1-auth-benutzerverwaltung.md`](docs/milestone-1-auth-benutzerverwaltung.md) | Issues #1–#7, #9, #56, #57 |
| [`docs/milestone-2-produktkatalog.md`](docs/milestone-2-produktkatalog.md) | Issues #8, #10, #13–#18, #20, #23–#26, #28, #31, #34, #46, #47 |
| [`docs/milestone-3-warenkorb-checkout.md`](docs/milestone-3-warenkorb-checkout.md) | Issues #39–#42, #44, #45 |
| [`docs/milestone-4-bestellhistorie.md`](docs/milestone-4-bestellhistorie.md) | Issues #48–#53, #55 |
| [`docs/milestone-5-crm-vertrieb.md`](docs/milestone-5-crm-vertrieb.md) | Issues #24, #26–#27, #29, #33–#38, #43, #54 |
| [`docs/milestone-6-administration-audit.md`](docs/milestone-6-administration-audit.md) | Issues #19, #58–#62 |
| [`docs/milestone-8-kundenservice.md`](docs/milestone-8-kundenservice.md) | Issues #11, #12, #21, #22, #30, #32 |

Jede Datei enthält: exakte Endpunkte, Request/Response-Felder, benötigte Rolle und Frontend-Codebeispiele.

### Swagger UI

Swagger UI ist standardmäßig aktiviert und nach `dev start` direkt erreichbar unter:
```
http://localhost:8080/swagger-ui/index.html
```

### Swagger UI verwenden

Die UI listet alle Endpunkte gruppiert nach Controller. Für jeden Endpunkt siehst du:
- URL, HTTP-Methode, Beschreibung
- Welche Parameter oder welcher Request-Body erwartet wird
- Welche Antwort zurückkommt (inkl. Fehlercodes und Beispiele)

**Mit JWT-Token authentifizieren:**

1. Zuerst `POST /api/auth/login` aufklappen → **Try it out** → Body ausfüllen → **Execute**
2. Den `token`-Wert aus der Antwort kopieren
3. Oben rechts auf **Authorize** klicken
4. Im Feld `Value` eingeben: `Bearer <token>` → **Authorize**

Ab jetzt werden alle Anfragen in der UI automatisch mit diesem Token gesendet.

### OpenAPI Spec (maschinenlesbar)

```bash
# Spec abrufen (Backend muss laufen)
curl http://localhost:8080/v3/api-docs

# Als Datei speichern (z.B. für Client-Generierung)
curl http://localhost:8080/v3/api-docs -o openapi.json
```

### Swagger UI für Produktion deaktivieren

Swagger UI ist lokal aktiviert. Vor dem Produktiv-Deploy in `application.properties` deaktivieren:
```properties
springdoc.swagger-ui.enabled=false
```
Öffentlich zugängliche API-Dokumentation ist ein Sicherheitsrisiko — sie zeigt Angreifern die gesamte Struktur des Backends. Die OpenAPI Spec (`/v3/api-docs`) kann separat aktiviert bleiben wenn sie für CI/CD gebraucht wird.

---

## 9. Frontend-Integration

Dieser Abschnitt richtet sich an das **Frontend-Team** und beschreibt, wie die React-App mit dem Backend kommuniziert.

### Basis-URL konfigurieren

Lege in der React-App eine zentrale API-Konfiguration an, damit die URL nur an einer Stelle geändert werden muss:

```typescript
// src/services/api.ts
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';

export async function apiFetch(path: string, options: RequestInit = {}): Promise<Response> {
  const token = localStorage.getItem('jwt');

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options.headers,
    },
  });

  if (response.status === 401) {
    // Token abgelaufen oder ungültig → ausloggen
    localStorage.removeItem('jwt');
    window.location.href = '/login';
  }

  return response;
}
```

In `.env.local` (nicht committen):
```
VITE_API_BASE_URL=http://localhost:8080
```

In `.env.production`:
```
VITE_API_BASE_URL=https://domain.de
```

---

### Authentifizierung — Login & Token speichern

```typescript
// src/services/authService.ts

export async function login(username: string, password: string): Promise<string> {
  const response = await fetch(`${API_BASE_URL}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  });

  if (!response.ok) throw new Error('Login fehlgeschlagen');

  const data = await response.json();
  // Antwort: { token: "eyJ...", username: "alice", role: "CUSTOMER" }

  localStorage.setItem('jwt', data.token);
  localStorage.setItem('role', data.role);
  return data.token;
}

export function logout() {
  const token = localStorage.getItem('jwt');
  apiFetch('/api/auth/logout', { method: 'POST' });
  localStorage.removeItem('jwt');
  localStorage.removeItem('role');
}

export function getRole(): string | null {
  return localStorage.getItem('role');
}
```

---

### Alle API-Endpunkte

#### Auth

| Aktion | Methode | URL | Body | Auth |
|---|---|---|---|---|
| Login | POST | `/api/auth/login` | `{username, password}` | Nein |
| Registrieren | POST | `/api/auth/register` | `{username, email, password, userType}` | Nein |
| Logout | POST | `/api/auth/logout` | — | JWT |

`userType` bei Register: `"PRIVATE"` oder `"BUSINESS"`

Antwort Login/Register:
```json
{ "token": "eyJ...", "username": "alice", "role": "CUSTOMER" }
```

---

#### Eigenes Profil

| Aktion | Methode | URL | Body | Rolle |
|---|---|---|---|---|
| Profil laden | GET | `/api/users/me` | — | AUTHENTICATED |
| Passwort ändern | PUT | `/api/users/me/password` | `{currentPassword, newPassword}` | AUTHENTICATED |
| E-Mail ändern | PUT | `/api/users/me/email` | `{newEmail}` | AUTHENTICATED |
| Lieferadresse | PUT | `/api/users/me/delivery-address` | `{street, city, postalCode, country}` | AUTHENTICATED |
| Zahlungsart | PUT | `/api/users/me/payment-method` | `{type, maskedDetails}` | AUTHENTICATED |
| Account löschen | DELETE | `/api/users/me` | — | AUTHENTICATED |

`/api/users/me` Antwort enthält u.a. `customerNumber` (US9).

---

#### Produktkatalog

| Aktion | Methode | URL | Query-Params | Rolle |
|---|---|---|---|---|
| Produkte laden | GET | `/api/products` | `purchasable`, `search`, `category` | PUBLIC* |
| Einzelprodukt | GET | `/api/products/{id}` | — | PUBLIC* |
| Kundenpreis | GET | `/api/products/{id}/price-for-customer` | — | CUSTOMER |
| Produkt anlegen | POST | `/api/products` | — | EMPLOYEE |
| Produkt löschen | DELETE | `/api/products/{id}` | — | EMPLOYEE |
| Kaufbar setzen | PUT | `/api/products/{id}/purchasable` | — | EMPLOYEE |
| Beschreibung | PUT | `/api/products/{id}/description` | — | EMPLOYEE |
| Bild | PUT | `/api/products/{id}/image` | — | EMPLOYEE |
| Preis (UVP) | PUT | `/api/products/{id}/price` | — | SALES_EMPLOYEE |
| Promoten | PUT | `/api/products/{id}/promoted` | — | SALES_EMPLOYEE |
| Statistik | GET | `/api/products/{id}/statistics` | `from`, `to` (ISO-Datum) | SALES_EMPLOYEE |

*Öffentlich zugänglich (kein Token nötig zum Lesen des Katalogs).

**Beispiele:**
```typescript
// Kaufbare Produkte für Kunden
const products = await apiFetch('/api/products?purchasable=true').then(r => r.json());

// Suche mit Kategorie
const results = await apiFetch('/api/products?search=laptop&category=Electronics').then(r => r.json());

// Kundenpreis mit Rabatten
const price = await apiFetch('/api/products/42/price-for-customer').then(r => r.json());
// { productId, recommendedRetailPrice, effectivePrice, discountPercent }
```

---

#### Warenkorb

| Aktion | Methode | URL | Body | Rolle |
|---|---|---|---|---|
| Warenkorb laden | GET | `/api/cart` | — | CUSTOMER |
| Artikel hinzufügen | POST | `/api/cart/items` | `{productId, quantity}` | CUSTOMER |
| Artikel entfernen | DELETE | `/api/cart/items/{productId}` | — | CUSTOMER |
| Aus Bestellung nachbestellen | POST | `/api/cart/reorder/{orderId}` | — | CUSTOMER |

`GET /api/cart` Antwort:
```json
{
  "items": [{ "productId": 1, "productName": "...", "quantity": 2, "unitPrice": 49.99 }],
  "subtotal": 99.98,
  "tax": 15.97,
  "shippingCost": 4.99,
  "total": 120.94
}
```

---

#### Bestellungen

| Aktion | Methode | URL | Rolle |
|---|---|---|---|
| Bestellung aufgeben | POST | `/api/orders` | CUSTOMER |
| Bestellhistorie | GET | `/api/orders` | CUSTOMER |
| Bestellung Details | GET | `/api/orders/{id}` | CUSTOMER |
| Artikel der Bestellung | GET | `/api/orders/{id}` → Feld `items[]` | CUSTOMER |

---

#### Daueraufträge

| Aktion | Methode | URL | Body | Rolle |
|---|---|---|---|---|
| Erstellen | POST | `/api/standing-orders` | `{intervalDays, items:[{productId,quantity}]}` | CUSTOMER |
| Alle laden | GET | `/api/standing-orders` | — | CUSTOMER |
| Ändern | PUT | `/api/standing-orders/{id}` | `{intervalDays, items}` | CUSTOMER |
| Stornieren | DELETE | `/api/standing-orders/{id}` | — | CUSTOMER |

---

#### Mitarbeiter-Endpunkte (`/api/customers`)

| Aktion | Methode | URL | Rolle |
|---|---|---|---|
| Alle Kunden | GET | `/api/customers` | EMPLOYEE |
| Kunden filtern | GET | `/api/customers?search=alice` | EMPLOYEE |
| Kunde laden | GET | `/api/customers/{id}` | EMPLOYEE |
| Unternehmensinfo | GET | `/api/customers/{id}/business-info` | SALES_EMPLOYEE |
| Bestellungen des Kunden | GET | `/api/customers/{id}/orders` | SALES_EMPLOYEE |
| Umsatzstatistik | GET | `/api/customers/{id}/revenue?from=...&to=...` | SALES_EMPLOYEE |
| Warenkorb einsehen | GET | `/api/customers/{id}/cart` | EMPLOYEE |
| Artikel in Warenkorb | POST | `/api/customers/{id}/cart/items` | EMPLOYEE |
| Artikel aus Warenkorb | DELETE | `/api/customers/{id}/cart/items/{productId}` | EMPLOYEE |
| Rabatt anlegen | POST | `/api/customers/{id}/discounts` | SALES_EMPLOYEE |
| Coupon anlegen | POST | `/api/customers/{id}/coupons` | SALES_EMPLOYEE |
| E-Mail senden | POST | `/api/customers/{id}/email` | SALES_EMPLOYEE |

Rabatt-Body:
```json
{ "productId": 1, "discountPercent": 15, "validFrom": "2026-01-01", "validUntil": "2026-12-31" }
```
`validUntil: null` = unbefristeter Rabatt.

---

#### Shoppi KI-Assistent (`/api/chat`)

| Aktion | Methode | URL | Body | Auth |
|---|---|---|---|---|
| Nachricht senden | POST | `/api/chat/message` | `{message, history[]}` | Optional (JWT für Kontext) |

Request-Body:
```json
{
  "message": "Was habe ich im Warenkorb?",
  "history": [
    { "role": "user", "content": "Hallo" },
    { "role": "assistant", "content": "Hallo! Ich bin Shoppi..." }
  ]
}
```

Antwort:
```json
{ "reply": "Du hast aktuell 2 Artikel im Warenkorb: ..." }
```

- **Ohne JWT:** Shoppi kennt nur den öffentlichen Produktkatalog.
- **Mit JWT (CUSTOMER-Rolle):** Shoppi erhält zusätzlich Profil, Warenkorb und letzte 5 Bestellungen im System-Prompt.
- Das Modell läuft lokal via Ollama — keine Daten verlassen den Server.

```powershell
# Unauthentifiziert
Invoke-RestMethod -Method Post http://localhost:8080/api/chat/message `
  -ContentType "application/json" `
  -Body '{"message":"Welche Produkte habt ihr?","history":[]}'

# Als eingeloggter Kunde
Invoke-RestMethod -Method Post http://localhost:8080/api/chat/message `
  -Headers @{Authorization="Bearer $TOKEN"} `
  -ContentType "application/json" `
  -Body '{"message":"Was habe ich im Warenkorb?","history":[]}'
```

---

#### Admin-Endpunkte (`/api/admin`)

| Aktion | Methode | URL | Rolle |
|---|---|---|---|
| Alle Benutzer | GET | `/api/admin/users` | ADMIN |
| Benutzer filtern | GET | `/api/admin/users?search=...` | ADMIN |
| Benutzer löschen | DELETE | `/api/admin/users/{id}` | ADMIN |
| Identität annehmen | POST | `/api/admin/impersonate/{userId}` | ADMIN |
| Audit-Log | GET | `/api/admin/audit-log` | ADMIN |

---

### Fehlerbehandlung

Das Backend gibt bei Fehlern einheitliche JSON-Antworten zurück:

```json
{
  "timestamp": "2026-04-10T12:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Artikel nicht gefunden: 99"
}
```

Relevante HTTP-Statuscodes:

| Code | Bedeutung |
|---|---|
| 200 | OK |
| 201 | Erstellt |
| 204 | Gelöscht (kein Body) |
| 400 | Ungültige Anfrage (Validierungsfehler) |
| 401 | Nicht eingeloggt |
| 403 | Keine Berechtigung (falsche Rolle) |
| 404 | Ressource nicht gefunden |
| 409 | Konflikt (z.B. Username bereits vergeben) |
| 500 | Serverfehler |

```typescript
// Einheitliches Error-Handling im Frontend
const response = await apiFetch('/api/products', { method: 'POST', body: JSON.stringify(data) });
if (!response.ok) {
  const error = await response.json();
  console.error(error.message); // z.B. "Name darf nicht leer sein"
}
```

---

### CORS

Das Backend erlaubt Anfragen von `http://localhost:5173` (Vite Dev-Server) — kein Proxy nötig.
Für Produktion wird `CORS_ALLOWED_ORIGINS` auf die echte Domain gesetzt (Umgebungsvariable).

---

## 10. Umgebungsvariablen

Kopiere `.env.example` zu `.env` und fülle die Werte aus.
Die `.env` Datei **nicht** ins Repository committen (steht in `.gitignore`).

```properties
# Datenbank
DB_URL=jdbc:postgresql://localhost:5432/webshop
DB_USERNAME=webshop
DB_PASSWORD=dein-passwort

# JWT
JWT_SECRET=ein-langer-zufaelliger-string-mindestens-32-zeichen
JWT_EXPIRATION_MS=86400000

# Server
SERVER_PORT=8080

# CORS (kommagetrennte Origins)
CORS_ALLOWED_ORIGINS=http://localhost:5173

# Mail (für Benachrichtigungen)
MAIL_HOST=localhost
MAIL_PORT=1025

# Shoppi KI-Assistent (Ollama)
OLLAMA_BASE_URL=http://ollama:11434
OLLAMA_MODEL=gemma4:e4b
```

---

## 11. Git-Workflow im Team

```bash
# 1. Aktuellen Stand holen
git checkout main
git pull

# 2. Branch für das Issue erstellen
git checkout -b feature/US-01-login-endpoint

# 3. Entwickeln und committen
git add src/main/java/de/fhdw/webshop/auth/AuthController.java
git commit -m "US-01: Login-Endpoint mit JWT implementiert"

# 4. Hochladen
git push -u origin feature/US-01-login-endpoint

# 5. Pull Request auf GitHub erstellen
```

**Absprache mit dem Frontend-Team:**
Wenn ein neuer Endpunkt fertig ist, kurz im Team-Kanal melden. Das Frontend kann dann die Mockdaten durch echte API-Calls ersetzen. Die OpenAPI Spec (`/v3/api-docs`) enthält immer den aktuellen Stand.

---

## 12. Lokales Testen (curl-Beispiele)

Alle Befehle setzen voraus, dass das Backend auf `http://localhost:8080` läuft (`dev start`).
Testdaten einspielen (einmalig):
```powershell
Get-Content src/main/resources/db/dev-seed.sql | docker exec -i webshop-postgres psql -U webshop -d webshop
```

Seeded-Accounts (Passwort überall: `Password1!`):

| Username | Rolle | Typ |
|---|---|---|
| alice | CUSTOMER | Privat |
| bob | CUSTOMER | Business |
| carol | EMPLOYEE | — |
| dave | SALES_EMPLOYEE | — |
| admin | ADMIN | — |

---

### Health-Check
```bash
curl http://localhost:8080/api/health
# {"status":"UP"}
```

### Login (Token holen)
```powershell
$TOKEN = (Invoke-RestMethod -Method Post http://localhost:8080/api/auth/login `
  -ContentType "application/json" `
  -Body '{"username":"alice","password":"Password1!"}').token
```

### Produkte
```powershell
# Alle kaufbaren Produkte
Invoke-RestMethod "http://localhost:8080/api/products?purchasable=true"

# Suche + Kategorie
Invoke-RestMethod "http://localhost:8080/api/products?search=laptop&category=Electronics"

# Kundenpreis (mit Rabatten)
Invoke-RestMethod "http://localhost:8080/api/products/1/price-for-customer" -Headers @{Authorization="Bearer $TOKEN"}
```

### Warenkorb & Bestellung
```powershell
# Artikel in Warenkorb
Invoke-RestMethod -Method Post http://localhost:8080/api/cart/items `
  -Headers @{Authorization="Bearer $TOKEN"} `
  -ContentType "application/json" `
  -Body '{"productId":1,"quantity":2}'

# Warenkorb anzeigen
Invoke-RestMethod http://localhost:8080/api/cart -Headers @{Authorization="Bearer $TOKEN"}

# Bestellen
Invoke-RestMethod -Method Post http://localhost:8080/api/orders `
  -Headers @{Authorization="Bearer $TOKEN"} -ContentType "application/json" -Body '{}'

# Bestellhistorie
Invoke-RestMethod http://localhost:8080/api/orders -Headers @{Authorization="Bearer $TOKEN"}
```

### Mitarbeiter-Endpunkte
```powershell
$EMPLOYEE_TOKEN = (Invoke-RestMethod -Method Post http://localhost:8080/api/auth/login `
  -ContentType "application/json" `
  -Body '{"username":"carol","password":"Password1!"}').token

$SALES_TOKEN = (Invoke-RestMethod -Method Post http://localhost:8080/api/auth/login `
  -ContentType "application/json" `
  -Body '{"username":"dave","password":"Password1!"}').token

# Kundenliste
Invoke-RestMethod "http://localhost:8080/api/customers" -Headers @{Authorization="Bearer $EMPLOYEE_TOKEN"}

# Rabatt anlegen (befristet)
Invoke-RestMethod -Method Post http://localhost:8080/api/customers/1/discounts `
  -Headers @{Authorization="Bearer $SALES_TOKEN"} `
  -ContentType "application/json" `
  -Body '{"productId":1,"discountPercent":10,"validFrom":"2026-01-01","validUntil":"2026-12-31"}'

# Unbefristeter Rabatt (validUntil = null)
Invoke-RestMethod -Method Post http://localhost:8080/api/customers/1/discounts `
  -Headers @{Authorization="Bearer $SALES_TOKEN"} `
  -ContentType "application/json" `
  -Body '{"productId":1,"discountPercent":5,"validFrom":"2026-01-01","validUntil":null}'

# Umsatzstatistik
Invoke-RestMethod "http://localhost:8080/api/customers/1/revenue?from=2026-01-01&to=2026-12-31" `
  -Headers @{Authorization="Bearer $SALES_TOKEN"}
```

### Admin-Endpunkte
```powershell
$ADMIN_TOKEN = (Invoke-RestMethod -Method Post http://localhost:8080/api/auth/login `
  -ContentType "application/json" `
  -Body '{"username":"admin","password":"Password1!"}').token

# Alle Benutzer
Invoke-RestMethod http://localhost:8080/api/admin/users -Headers @{Authorization="Bearer $ADMIN_TOKEN"}

# Identität annehmen (gibt Token zurück, der als alice funktioniert)
Invoke-RestMethod -Method Post http://localhost:8080/api/admin/impersonate/1 -Headers @{Authorization="Bearer $ADMIN_TOKEN"}

# Audit-Log
Invoke-RestMethod http://localhost:8080/api/admin/audit-log -Headers @{Authorization="Bearer $ADMIN_TOKEN"}
```

---

## 13. User Story Abdeckung

Alle 62 User Stories sind implementiert. Übersicht:

| US | Beschreibung | Endpunkt / Mechanismus | Rolle |
|---|---|---|---|
| 1 | Login | `POST /api/auth/login` | Public |
| 2 | Registrieren | `POST /api/auth/register` | Public |
| 3 | Rolle bei Registrierung | Automatisch: Default `CUSTOMER`; EMPLOYEE/ADMIN werden von ADMIN gesetzt | System |
| 4 | Passwort ändern | `PUT /api/users/me/password` | AUTHENTICATED |
| 5 | E-Mail ändern | `PUT /api/users/me/email` | AUTHENTICATED |
| 6 | Logout | `POST /api/auth/logout` (Token-Blacklist) | AUTHENTICATED |
| 7 | Deregistrieren | `DELETE /api/users/me` | AUTHENTICATED |
| 8 | Kaufbares Sortiment (Kunde) | `GET /api/products?purchasable=true` | CUSTOMER |
| 9 | Eigene Kundennummer | `GET /api/users/me` → Feld `customerNumber` | CUSTOMER |
| 10 | Volles Sortiment (Mitarbeiter) | `GET /api/products` (ohne purchasable-Filter) | EMPLOYEE |
| 11 | Warenkorb eines Kunden | `GET /api/customers/{id}/cart` | EMPLOYEE |
| 12 | Kundennummer eines Kunden | `GET /api/customers/{id}` → Feld `customerNumber` | EMPLOYEE |
| 13 | Artikel hinzufügen | `POST /api/products` | EMPLOYEE |
| 14 | Artikel entfernen | `DELETE /api/products/{id}` | EMPLOYEE |
| 15 | Artikel als kaufbar markieren | `PUT /api/products/{id}/purchasable` | EMPLOYEE |
| 16 | Beschreibung bearbeiten | `PUT /api/products/{id}/description` | EMPLOYEE |
| 17 | Bild bearbeiten | `PUT /api/products/{id}/image` | EMPLOYEE |
| 18 | UVP bearbeiten | `PUT /api/products/{id}/price` | SALES_EMPLOYEE |
| 19 | Identität annehmen | `POST /api/admin/impersonate/{userId}` | ADMIN |
| 20 | Artikeldaten wie Kunde sieht | `GET /api/products` + `GET /api/products/{id}/price-for-customer` mit Kunden-ID | EMPLOYEE |
| 21 | Artikel in Kunden-Warenkorb | `POST /api/customers/{id}/cart/items` | EMPLOYEE |
| 22 | Artikel aus Kunden-Warenkorb | `DELETE /api/customers/{id}/cart/items/{productId}` | EMPLOYEE |
| 23 | Artikelbeschreibung sehen | `GET /api/products/{id}` → Feld `description` | CUSTOMER |
| 24 | Artikelbild sehen | `GET /api/products/{id}` → Feld `imageUrl` | CUSTOMER |
| 25 | UVP sehen | `GET /api/products/{id}` → Feld `recommendedRetailPrice` | CUSTOMER |
| 26 | Persönlicher Preis (inkl. Rabatte + Steuern) | `GET /api/products/{id}/price-for-customer` | CUSTOMER |
| 27 | Befristeter Rabatt | `POST /api/customers/{id}/discounts` mit `validUntil` gesetzt | SALES_EMPLOYEE |
| 28 | Privat- vs. Unternehmenskunde unterscheiden | Feld `userType` (PRIVATE/BUSINESS) in Profil + Kundenliste | SALES_EMPLOYEE |
| 29 | Branche & Unternehmensgröße | `GET /api/customers/{id}/business-info` | SALES_EMPLOYEE |
| 30 | Unbefristeter Rabatt | `POST /api/customers/{id}/discounts` mit `validUntil: null` | SALES_EMPLOYEE |
| 31 | Coupons gewähren | `POST /api/customers/{id}/coupons` | SALES_EMPLOYEE |
| 32 | Artikel promoten | `PUT /api/products/{id}/promoted` | SALES_EMPLOYEE |
| 33 | Bestellungen eines Kunden | `GET /api/customers/{id}/orders` | SALES_EMPLOYEE |
| 34 | Umsatzstatistik eines Kunden | `GET /api/customers/{id}/revenue?from=&to=` | SALES_EMPLOYEE |
| 35 | Kundenübersicht | `GET /api/customers` | EMPLOYEE |
| 36 | Kunden filtern | `GET /api/customers?search={term}` | EMPLOYEE |
| 37 | Artikel-Statistik (Absatz, Preise) | `GET /api/products/{id}/statistics?from=&to=` | SALES_EMPLOYEE |
| 38 | Benachrichtigung >20% Absatzrückgang | `NotificationScheduler` — läuft jeden Montag 07:00, loggt Warnungen | System |
| 39 | Benachrichtigung 0 Absatz | `NotificationScheduler` — läuft jeden Montag 07:00, loggt Warnungen | System |
| 40 | E-Mail an Kunden senden | `POST /api/customers/{id}/email` | SALES_EMPLOYEE |
| 41 | Artikel in Warenkorb | `POST /api/cart/items` | CUSTOMER |
| 42 | Artikel aus Warenkorb | `DELETE /api/cart/items/{productId}` | CUSTOMER |
| 43 | Warenkorbübersicht (Summe, Steuern, Versand) | `GET /api/cart` — enthält subtotal, tax, shippingCost, total | CUSTOMER |
| 44 | Bestellen | `POST /api/orders` | CUSTOMER |
| 45 | Zahlungsart festlegen | `PUT /api/users/me/payment-method` | CUSTOMER |
| 46 | Lieferadresse festlegen | `PUT /api/users/me/delivery-address` | CUSTOMER |
| 47 | Sortiment nach Suchbegriffen filtern | `GET /api/products?search={term}` | CUSTOMER |
| 48 | Sortiment nach Kategorie filtern | `GET /api/products?category={cat}` | CUSTOMER |
| 49 | Bestellhistorie | `GET /api/orders` | CUSTOMER |
| 50 | Kaufbare Artikel in alter Bestellung | `GET /api/orders/{id}` → Feld `items[].purchasable` zeigt Verfügbarkeit | CUSTOMER |
| 51 | Nachbestellung (Reorder) | `POST /api/cart/reorder/{orderId}` | CUSTOMER |
| 52 | Dauerauftrag erstellen | `POST /api/standing-orders` | CUSTOMER |
| 53 | Dauerauftrag stornieren | `DELETE /api/standing-orders/{id}` | CUSTOMER |
| 54 | Dauerauftrag ändern | `PUT /api/standing-orders/{id}` | CUSTOMER |
| 55 | Benachrichtigung Dauerauftrag (nicht mehr kaufbar) | `StandingOrderScheduler` — täglich 06:00, loggt Warnungen | System |
| 56 | Personenbezogene Daten schützen | Spring Security: rollenbasierter Zugriff, JWT, keine Datenlecks über API-Grenzen | NFR |
| 57 | Unternehmensdaten schützen | Spring Security: EMPLOYEE/SALES_EMPLOYEE-Endpunkte nur mit gültiger Rolle erreichbar | NFR |
| 58 | System-Transaktionen einsehen | `GET /api/admin/audit-log` | ADMIN |
| 59 | Benutzerübersicht | `GET /api/admin/users` | ADMIN |
| 60 | Benutzer filtern | `GET /api/admin/users?search={term}` | ADMIN |
| 61 | Benutzer deregistrieren | `DELETE /api/admin/users/{id}` | ADMIN |
| 62 | Admin- vs. System-Änderungen unterscheiden | `audit_log.initiated_by` = `USER` / `ADMIN` / `SYSTEM` | NFR |

**Ergebnis: 62 von 62 User Stories abgedeckt.**

---

## 14. Deployment

> **Status:** Hosting-Entscheidung noch offen. IP/Domain in den GitHub Secrets eintragen sobald der Server steht.

### Geplante Produktivumgebung
- Server: VPS (z.B. Hetzner) oder Cloud
- NGINX als Reverse Proxy (Port 80/443)
- Backend als Systemd-Service auf Port 8080
- Frontend (statische React-Files) über NGINX
- PostgreSQL lokal auf dem Server
- CI/CD: GitHub Actions deployt automatisch bei Merge in `main`

### CI/CD — GitHub Actions

Die Pipelines liegen unter `.github/workflows/`:

| Datei | Trigger | Was passiert |
|---|---|---|
| `ci.yml` | Pull Request auf `main` | `./mvnw test` — schlägt fehl → PR wird blockiert |
| `cd.yml` | Push/Merge auf `main` | JAR bauen → per SCP auf VPS hochladen → Systemd-Service neustarten |

### GitHub Secrets einrichten

Unter **Settings → Secrets and variables → Actions** im Backend-Repo drei Secrets anlegen:

| Secret | Wert |
|---|---|
| `VPS_HOST` | IP-Adresse oder Domain des Servers |
| `VPS_USER` | SSH-Benutzername (z.B. `deploy`) |
| `VPS_SSH_KEY` | Inhalt von `~/.ssh/id_ed25519` (privater Key) |

### VPS vorbereiten (einmalig)

**1. Deploy-User anlegen:**
```bash
adduser deploy
usermod -aG sudo deploy
```

**2. SSH-Key hinterlegen:**
```bash
# Lokal: Key generieren (falls noch keiner existiert)
ssh-keygen -t ed25519 -C "github-actions"

# Öffentlichen Key auf den Server kopieren
ssh-copy-id deploy@DEINE-IP
```
Den Inhalt von `~/.ssh/id_ed25519` (privater Key) als Secret `VPS_SSH_KEY` in GitHub eintragen.

**3. Verzeichnis + Systemd-Service anlegen:**
```bash
mkdir -p /opt/webshop

cat > /etc/systemd/system/webshop.service << 'EOF'
[Unit]
Description=Webshop Spring Boot Backend
After=network.target postgresql.service

[Service]
User=deploy
WorkingDirectory=/opt/webshop
ExecStart=/usr/bin/java -jar /opt/webshop/webshop-0.0.1-SNAPSHOT.jar
Restart=on-failure
EnvironmentFile=/opt/webshop/.env

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl enable webshop
```

**4. Sudoers-Eintrag (damit GitHub Actions den Service neustarten darf):**
```bash
echo "deploy ALL=(ALL) NOPASSWD: /bin/systemctl restart webshop" \
  >> /etc/sudoers.d/webshop
```

**5. `.env` auf dem Server anlegen:**
```bash
cat > /opt/webshop/.env << 'EOF'
DB_URL=jdbc:postgresql://localhost:5432/webshop
DB_USERNAME=webshop
DB_PASSWORD=sicheres-passwort
JWT_SECRET=langer-zufaelliger-string-mindestens-32-zeichen
JWT_EXPIRATION_MS=86400000
CORS_ALLOWED_ORIGINS=https://deine-domain.de
MAIL_HOST=localhost
MAIL_PORT=25
EOF
chmod 600 /opt/webshop/.env
```

### Ablauf nach Setup

```
git push / PR merge → main
        │
        ▼
CI: ./mvnw test (schlägt fehl → kein Deploy)
        │ ✓
        ▼
CD: ./mvnw package → JAR → SCP → VPS → systemctl restart webshop
        │
        ▼
Backend läuft auf https://domain.de/api/
```
