# Webshop Backend — Linux / macOS (bash)

> **Windows-Nutzer:** → [README.md](README.md)

Spring Boot Backend für den Webshop. Stellt eine REST API bereit, die vom React-Frontend unter [SEPBFWS124A/Webshop](https://github.com/SEPBFWS124A/Webshop) verwendet wird.

> Alle Befehle in diesem Dokument sind für **bash / zsh** geschrieben.

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
                                            └──[JPA / SQL]──► PostgreSQL (Port 5432)
```

Das Frontend schickt HTTP-Anfragen an das Backend (z.B. `GET /api/products`).
Das Backend verarbeitet die Anfrage, liest/schreibt in die Datenbank und antwortet mit JSON.
Jede Anfrage wird über einen JWT-Token authentifiziert (außer Login/Register).

---

## 2. Technologie-Stack

| Technologie | Zweck | Version |
|---|---|---|
| Java | Programmiersprache (läuft im Docker Container) | 21 |
| Maven | Build-Tool (läuft im Docker Container) | 3.9 |
| Spring Boot | Framework (Web, Security, JPA) | 3.4.4 |
| PostgreSQL | Datenbank | 16 |
| Flyway | Datenbank-Migrations | - |
| JWT (jjwt) | Authentifizierung | 0.12.6 |
| springdoc-openapi | OpenAPI Spec generieren | 2.8.6 |
| Docker + Compose | Lokale Entwicklungsumgebung | - |
| NGINX | Reverse Proxy + HTTPS (Produktion) | - |
| GitHub Actions | CI/CD-Pipelines | - |

---

## 3. Architektur im Detail — was macht was?

### Java 21 — die Sprache
Die Basis von allem. Java ist stark typisiert, was bei einem größeren Team Fehler früh erkennt. Version 21 ist die aktuelle LTS-Version (Long Term Support) — die empfohlene stabile Version für neue Projekte.

### Maven — das Build-Tool
Entspricht `npm` im Frontend. Verwaltet Abhängigkeiten (`pom.xml` = `package.json`), baut das Projekt und führt Tests aus. Maven läuft vollständig im Docker Build-Container — **kein lokales Java oder Maven nötig**.

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
Docker Compose startet zwei Container: **PostgreSQL** (Datenbank) und **Spring Boot Backend** (inkl. Maven Build). Kein lokales Java, Maven oder PostgreSQL nötig — einzige Voraussetzung ist Docker Desktop.

### springdoc-openapi — API-Beschreibung
Liest den Spring Boot Code und generiert automatisch eine maschinenlesbare Beschreibung aller Endpunkte (`/v3/api-docs`). Das Frontend-Team kann daraus ablesen wie ein API-Call aussehen muss, ohne beim Backend-Team nachzufragen.

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

Folgendes muss auf deinem Rechner installiert sein:

### Docker
Wird für PostgreSQL **und** das Spring Boot Backend benötigt. Kein Java oder Maven lokal nötig.
```bash
docker --version
docker compose version
```
Download: https://www.docker.com/products/docker-desktop (oder `sudo apt install docker.io docker-compose-plugin`)

### PowerShell Core (pwsh)
Das Dev-Script ist in PowerShell geschrieben und läuft auch auf Linux via PowerShell Core.
```bash
pwsh --version
```
Installation: https://learn.microsoft.com/powershell/scripting/install/installing-powershell-on-linux

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
```bash
pwsh dev.ps1 start
```

Das Script startet automatisch:
1. Den PostgreSQL Docker-Container (wartet bis er `healthy` ist)
2. Das Spring Boot Backend im Docker-Container bauen + starten (wartet bis `/api/health` antwortet)

Das Backend ist dann erreichbar unter: `http://localhost:8080`

> **Erster Start:** Maven lädt alle Dependencies im Container herunter (~200 MB) — das kann einige Minuten dauern. Danach ist der Layer gecacht und folgende Starts sind deutlich schneller.

**Alle Befehle im Überblick:**

| Befehl | Was passiert |
|--------|-------------|
| `pwsh dev.ps1 start` | PostgreSQL + Spring Boot Container starten |
| `pwsh dev.ps1 stop` | Alle Container beenden |
| `pwsh dev.ps1 stop --keep-db` | Nur Backend-Container beenden, DB läuft weiter (schnellerer Neustart) |
| `pwsh dev.ps1 restart` | Backend-Container stoppen + neu starten |
| `pwsh dev.ps1 restart --keep-db` | Backend-Neustart ohne PostgreSQL-Neustart |
| `pwsh dev.ps1 rebuild` | Docker-Image neu bauen + starten (kein Layer-Cache) |
| `pwsh dev.ps1 rebuild --keep-db` | Rebuild ohne PostgreSQL-Neustart |

> **Wann `pwsh dev.ps1 rebuild` statt `pwsh dev.ps1 restart`?**
> Nach Änderungen an `pom.xml` (neue Dependencies) oder wenn das Docker-Image
> aus einem anderen Grund neu gebaut werden muss. `rebuild` übergibt `--force-recreate`
> und `--no-cache` an Docker Compose.

### Schritt 3 — Testdaten einspielen (einmalig)
```bash
docker exec -i webshop-postgres psql -U webshop -d webshop \
  < src/main/resources/db/dev-seed.sql
```

### Schritt 4 — Verifizieren
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
    │   ├── auth/                           ← Login, Register, Logout, JWT
    │   │   ├── AuthController.java
    │   │   ├── AuthService.java
    │   │   ├── JwtTokenProvider.java
    │   │   ├── TokenBlacklist.java
    │   │   └── dto/
    │   │
    │   ├── config/                         ← Spring Security, CORS, Filter
    │   │   ├── SecurityConfig.java
    │   │   ├── CorsConfig.java
    │   │   └── JwtAuthenticationFilter.java
    │   │
    │   ├── user/                           ← Nutzer-Entity, Profil, Passwort/Email ändern
    │   ├── product/                        ← Artikel-Verwaltung
    │   ├── cart/                           ← Warenkorb
    │   ├── order/                          ← Bestellungen
    │   ├── discount/                       ← Rabatte & Coupons
    │   ├── customer/                       ← Kundenübersicht (Mitarbeiter-Sicht)
    │   ├── standingorder/                  ← Daueraufträge
    │   ├── notification/                   ← E-Mail-Versand, Scheduler
    │   └── admin/                          ← Admin-Endpunkte, Audit-Log
    │
    └── resources/
        ├── application.properties          ← Konfiguration (DB, JWT, Mail)
        └── db/
            ├── migration/                  ← Flyway SQL-Migrations (V1–V9)
            └── dev-seed.sql                ← Testdaten für lokale Entwicklung
```

---

## 7. Datenbank & Migrations

Flyway führt beim Start automatisch alle noch nicht angewendeten Migrations aus.

**Neue Migration anlegen:**
```bash
# Dateiname-Schema: V<nummer>__<beschreibung>.sql
touch src/main/resources/db/migration/V10__add_column_xyz.sql
```

Die Nummer muss streng monoton steigen. Flyway prüft den Checksum jeder bereits ausgeführten Migration — **niemals eine bestehende Migrations-Datei nachträglich ändern**.

---

## 8. API-Dokumentation (OpenAPI)

Wenn das Backend läuft:

| URL | Inhalt |
|---|---|
| `http://localhost:8080/swagger-ui.html` | Interaktive API-Oberfläche (Swagger UI) |
| `http://localhost:8080/v3/api-docs` | Maschinenlesbare OpenAPI 3.0 Spec (JSON) |

Alle Endpunkte sind dort aufgelistet — inkl. Request-Body-Schema, Response-Typen und Authentifizierungs-Anforderungen.

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

---

## 9. Frontend-Integration

Das Frontend liegt im separaten Repo [SEPBFWS124A/Webshop](https://github.com/SEPBFWS124A/Webshop).

CORS ist für `http://localhost:5173` konfiguriert (`CorsConfig.java`). Weitere Origins lassen sich über die Umgebungsvariable `CORS_ALLOWED_ORIGINS` (kommagetrennt) ergänzen.

---

## 10. Umgebungsvariablen

Alle Variablen können in einer `.env`-Datei im Root-Verzeichnis gesetzt werden (wird von `docker-compose.yml` eingelesen).

| Variable | Standard | Beschreibung |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/webshop` | JDBC-URL |
| `DB_USERNAME` | `webshop` | DB-Benutzername |
| `DB_PASSWORD` | `webshop` | DB-Passwort |
| `JWT_SECRET` | (fest im Code) | HMAC-Schlüssel für JWT-Signierung |
| `JWT_EXPIRATION_MS` | `86400000` (24h) | Token-Gültigkeit in Millisekunden |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | Erlaubte Frontend-Origins (kommagetrennt) |
| `MAIL_HOST` | `smtp.gmail.com` | SMTP-Server |
| `MAIL_PORT` | `587` | SMTP-Port |
| `MAIL_USERNAME` | — | SMTP-Benutzername |
| `MAIL_PASSWORD` | — | SMTP-Passwort / App-Passwort |

---

## 11. Git-Workflow im Team

Branching-Strategie: Feature-Branches von `develop`, PRs in `develop`, Release-PRs von `develop` in `main`.

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

Alle Befehle setzen voraus, dass das Backend auf `http://localhost:8080` läuft (`pwsh dev.ps1 start`).

Testdaten einspielen (einmalig):
```bash
docker exec -i webshop-postgres psql -U webshop -d webshop < src/main/resources/db/dev-seed.sql
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
```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"Password1!"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")
```

### Produkte
```bash
# Alle kaufbaren Produkte
curl "http://localhost:8080/api/products?purchasable=true"

# Suche + Kategorie
curl "http://localhost:8080/api/products?search=laptop&category=Electronics"

# Kundenpreis (mit Rabatten)
curl "http://localhost:8080/api/products/1/price-for-customer" -H "Authorization: Bearer $TOKEN"
```

### Warenkorb & Bestellung
```bash
# Artikel in Warenkorb
curl -X POST http://localhost:8080/api/cart/items \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"productId":1,"quantity":2}'

# Warenkorb anzeigen
curl http://localhost:8080/api/cart -H "Authorization: Bearer $TOKEN"

# Bestellen
curl -X POST http://localhost:8080/api/orders \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{}'

# Bestellhistorie
curl http://localhost:8080/api/orders -H "Authorization: Bearer $TOKEN"
```

### Mitarbeiter-Endpunkte
```bash
EMPLOYEE_TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"carol","password":"Password1!"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")

SALES_TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"dave","password":"Password1!"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")

# Kundenliste
curl "http://localhost:8080/api/customers" -H "Authorization: Bearer $EMPLOYEE_TOKEN"

# Rabatt anlegen (befristet)
curl -X POST http://localhost:8080/api/customers/1/discounts \
  -H "Authorization: Bearer $SALES_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"productId":1,"discountPercent":10,"validFrom":"2026-01-01","validUntil":"2026-12-31"}'

# Unbefristeter Rabatt (validUntil = null)
curl -X POST http://localhost:8080/api/customers/1/discounts \
  -H "Authorization: Bearer $SALES_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"productId":1,"discountPercent":5,"validFrom":"2026-01-01","validUntil":null}'

# Umsatzstatistik
curl "http://localhost:8080/api/customers/1/revenue?from=2026-01-01&to=2026-12-31" \
  -H "Authorization: Bearer $SALES_TOKEN"
```

### Admin-Endpunkte
```bash
ADMIN_TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Password1!"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")

# Alle Benutzer
curl http://localhost:8080/api/admin/users -H "Authorization: Bearer $ADMIN_TOKEN"

# Identität annehmen (gibt Token zurück, der als alice funktioniert)
curl -X POST http://localhost:8080/api/admin/impersonate/1 -H "Authorization: Bearer $ADMIN_TOKEN"

# Audit-Log
curl http://localhost:8080/api/admin/audit-log -H "Authorization: Bearer $ADMIN_TOKEN"
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
