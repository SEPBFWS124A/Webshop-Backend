# Webshop Backend

Spring Boot Backend für den Webshop. Stellt eine REST API bereit, die vom React-Frontend unter [SEPBFWS124A/Webshop](https://github.com/SEPBFWS124A/Webshop) verwendet wird.

---

## Inhaltsverzeichnis

1. [Architektur-Überblick](#1-architektur-überblick)
2. [Technologie-Stack](#2-technologie-stack)
3. [Voraussetzungen](#3-voraussetzungen)
4. [Lokales Setup](#4-lokales-setup)
5. [Projektstruktur](#5-projektstruktur)
6. [Datenbank & Migrations](#6-datenbank--migrations)
7. [API-Dokumentation](#7-api-dokumentation)
8. [Umgebungsvariablen](#8-umgebungsvariablen)
9. [Git-Workflow im Team](#9-git-workflow-im-team)
10. [Deployment](#10-deployment)

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
| Java | Programmiersprache | 21 |
| Spring Boot | Framework (Web, Security, JPA) | 3.x |
| PostgreSQL | Datenbank | 16 |
| Flyway | Datenbank-Migrations | - |
| JWT (jjwt) | Authentifizierung | - |
| springdoc-openapi | OpenAPI Spec generieren | - |
| Docker + Compose | Lokale Entwicklungsumgebung | - |
| Maven | Build-Tool | - |

---

## 3. Voraussetzungen

Folgendes muss auf deinem Rechner installiert sein:

### Java 21
Prüfen ob installiert:
```bash
java -version
# Erwartet: openjdk 21 ...
```
Download: https://adoptium.net (Eclipse Temurin 21 LTS)

### Maven
Prüfen ob installiert:
```bash
mvn -version
```
Download: https://maven.apache.org/download.cgi — oder über IntelliJ (kommt automatisch mit)

### Docker Desktop
Wird für die lokale PostgreSQL-Datenbank benötigt — kein manuelles DB-Setup nötig.

Prüfen ob installiert:
```bash
docker --version
docker compose version
```
Download: https://www.docker.com/products/docker-desktop

### Empfohlene IDE
**IntelliJ IDEA** (Community Edition reicht) — hat erstklassigen Spring Boot Support.
Download: https://www.jetbrains.com/idea/download

---

## 4. Lokales Setup

### Schritt 1 — Repository klonen
```bash
git clone https://github.com/SEPBFWS124A/Webshop-Backend.git
cd Webshop-Backend
```

### Schritt 2 — Umgebungsvariablen anlegen
Kopiere die Beispieldatei und fülle sie aus:
```bash
cp .env.example .env
```

Die `.env` Datei ist in `.gitignore` — kommt **nie** ins Repository.

### Schritt 3 — Datenbank starten (via Docker)
```bash
docker compose up -d
```
Startet PostgreSQL im Hintergrund auf Port 5432. Beim ersten Start wird die Datenbank automatisch angelegt.

Status prüfen:
```bash
docker compose ps
# postgres sollte "running" zeigen
```

### Schritt 4 — Backend starten
```bash
mvn spring-boot:run
```

Oder in IntelliJ: `WebshopApplication.java` öffnen → grüner Play-Button.

Das Backend ist erreichbar unter: `http://localhost:8080`

### Schritt 5 — Verifizieren
```bash
curl http://localhost:8080/api/health
# Erwartet: {"status":"UP"}
```

OpenAPI Spec abrufen (maschinenlesbare API-Beschreibung):
```bash
curl http://localhost:8080/v3/api-docs
```

---

## 5. Projektstruktur

```
src/
└── main/
    ├── java/de/fhdw/webshop/
    │   ├── WebshopApplication.java     ← Einstiegspunkt (main-Methode)
    │   │
    │   ├── config/                     ← Konfigurationsklassen
    │   │   ├── SecurityConfig.java     ← Spring Security, JWT-Filter
    │   │   └── CorsConfig.java         ← CORS für Frontend
    │   │
    │   ├── auth/                       ← Login & Registrierung
    │   │   ├── AuthController.java     ← POST /api/auth/login, /register
    │   │   ├── AuthService.java
    │   │   └── JwtUtil.java            ← Token generieren & validieren
    │   │
    │   ├── user/                       ← Benutzer & Rollen
    │   │   ├── User.java               ← Entity (Tabelle: users)
    │   │   ├── UserRepository.java     ← Datenbankzugriff
    │   │   └── UserService.java
    │   │
    │   ├── product/                    ← Artikel/Sortiment
    │   │   ├── Product.java
    │   │   ├── ProductController.java  ← GET/POST/PUT/DELETE /api/products
    │   │   ├── ProductRepository.java
    │   │   └── ProductService.java
    │   │
    │   ├── cart/                       ← Warenkorb
    │   │   ├── Cart.java
    │   │   ├── CartController.java     ← /api/cart
    │   │   ├── CartRepository.java
    │   │   └── CartService.java
    │   │
    │   └── order/                      ← Bestellungen
    │       ├── Order.java
    │       ├── OrderController.java    ← /api/orders
    │       ├── OrderRepository.java
    │       └── OrderService.java
    │
    └── resources/
        ├── application.properties      ← Konfiguration (liest aus .env)
        └── db/migration/               ← Flyway SQL-Migrations
            ├── V1__create_users.sql
            ├── V2__create_products.sql
            └── V3__create_orders.sql
```

### Wie ein Package aufgebaut ist

Jedes Feature-Package (z.B. `product/`) enthält immer dieselben 4 Typen:

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

## 6. Datenbank & Migrations

### Warum Flyway?
Flyway verwaltet Datenbankänderungen als SQL-Dateien — ähnlich wie Git für die Datenbank.
Jede Änderung am Schema (neue Tabelle, neues Feld) wird als neue Migration-Datei angelegt.
Beim Start führt Spring Boot automatisch alle noch nicht ausgeführten Migrations aus.

### Konvention für Dateinamen
```
V{Nummer}__{Beschreibung}.sql
```
Beispiele:
```
V1__create_users.sql
V2__create_products.sql
V3__add_price_to_products.sql
V4__create_orders.sql
```

**Wichtig:** Bestehende Migration-Dateien **niemals ändern** — immer eine neue anlegen.

### Neue Migration erstellen
1. Neue Datei unter `src/main/resources/db/migration/` anlegen
2. Nächste freie Nummer verwenden (z.B. `V5__create_cart.sql`)
3. SQL schreiben, Backend neu starten → Flyway führt die Migration automatisch aus

---

## 7. API-Dokumentation (OpenAPI)

Das Backend generiert automatisch eine **OpenAPI Spec** — eine maschinenlesbare Beschreibung aller Endpunkte, Parameter und Antworten.

### Spec abrufen
```bash
curl http://localhost:8080/v3/api-docs
# Gibt ein JSON-Dokument zurück mit allen Endpunkten
```

### Wozu das nützlich ist

**Für das Frontend-Team:** Die Spec beschreibt exakt welche Endpunkte existieren, was gesendet werden muss und was zurückkommt — ohne Rückfragen ans Backend-Team.

**API-Client automatisch generieren** (optional):
Statt HTTP-Calls von Hand zu schreiben kann das Frontend einen typisierten Client aus der Spec generieren:
```bash
# Im Frontend-Repo ausführen (einmalig installieren)
npm install -g @openapitools/openapi-generator-cli

# Client generieren (wenn Backend lokal läuft)
openapi-generator-cli generate \
  -i http://localhost:8080/v3/api-docs \
  -g javascript \
  -o src/services/generated
```

**Spec als Datei speichern** (für Offline-Nutzung oder CI):
```bash
curl http://localhost:8080/v3/api-docs -o openapi.json
```

### Spring Boot Konfiguration
Swagger UI ist deaktiviert — nur die Spec wird bereitgestellt:
```properties
# application.properties
springdoc.swagger-ui.enabled=false
springdoc.api-docs.enabled=true
springdoc.api-docs.path=/v3/api-docs
```

---

## 8. Umgebungsvariablen

Kopiere `.env.example` zu `.env` und fülle die Werte aus.
Die `.env` Datei **nicht** ins Repository committen.

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
```

---

## 9. Git-Workflow im Team

Identisch zum Frontend — ein Feature-Branch pro Issue:

```bash
# 1. Aktuellen Stand holen
git checkout develop
git pull

# 2. Branch für das Issue erstellen
git checkout -b feature/US-01-login-endpoint

# 3. Entwickeln und committen
git add src/main/java/de/fhdw/webshop/auth/AuthController.java
git commit -m "US-01: Login-Endpoint mit JWT implementiert"

# 4. Hochladen
git push -u origin feature/US-01-login-endpoint

# 5. Pull Request → develop auf GitHub erstellen
```

**Absprache mit dem Frontend-Team:**
Wenn ein neuer Endpunkt fertig ist, bitte im Team-Kanal kurz melden — das Frontend kann dann die Mockdaten durch echte API-Calls ersetzen.

---

## 10. Deployment

> **Status:** Hosting-Entscheidung noch offen. Dieser Abschnitt wird ergänzt sobald der Server steht.

Geplante Produktivumgebung:
- Server: VPS (z.B. Hetzner) oder Cloud
- NGINX als Reverse Proxy (Port 80/443)
- Backend läuft auf Port 8080 hinter NGINX
- Frontend (statische Files) läuft ebenfalls über NGINX
- PostgreSQL läuft lokal auf dem Server
- CI/CD: GitHub Actions deployt automatisch bei Merge in `main`

Details folgen in Issue [I-9: Server einrichten](https://github.com/SEPBFWS124A/Webshop-Backend/issues/9) und [I-10: NGINX + SSL](https://github.com/SEPBFWS124A/Webshop-Backend/issues/10).
