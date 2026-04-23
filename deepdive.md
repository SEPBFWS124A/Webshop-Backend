# Deep Dive — Webshop Backend

Dieses Dokument erklärt den gesamten Software-Stack von Grund auf: was jede Datei ist,
warum sie existiert, wie alles zusammenhängt — und was passiert wenn du `dev start`,
`dev stop`, `dev restart` oder `dev rebuild` ausführst.

Zielgruppe: jemand der noch nie mit Java, Spring Boot oder Maven gearbeitet hat und
einfach einen Haufen Dateien vor sich sieht.

---

## Inhaltsverzeichnis

1. [Das große Bild — was macht das alles?](#1-das-große-bild--was-macht-das-alles)
2. [Dateien im Root-Verzeichnis](#2-dateien-im-root-verzeichnis)
3. [Maven — wie wird das Projekt gebaut?](#3-maven--wie-wird-das-projekt-gebaut)
4. [Docker — wie läuft die Datenbank?](#4-docker--wie-läuft-die-datenbank)
5. [Spring Boot — wie startet die Anwendung?](#5-spring-boot--wie-startet-die-anwendung)
6. [Konfiguration — application.properties](#6-konfiguration--applicationproperties)
7. [Die Datenbankschicht — Flyway + JPA](#7-die-datenbankschicht--flyway--jpa)
8. [Das Package-System — wie ist der Code organisiert?](#8-das-package-system--wie-ist-der-code-organisiert)
9. [Jede Datei erklärt](#9-jede-datei-erklärt)
10. [Sicherheit — wie funktioniert JWT?](#10-sicherheit--wie-funktioniert-jwt)
11. [Eine Anfrage von Anfang bis Ende](#11-eine-anfrage-von-anfang-bis-ende)
12. [Scheduled Jobs — was läuft automatisch?](#12-scheduled-jobs--was-läuft-automatisch)
13. [dev start / stop / restart / rebuild — was passiert wirklich?](#13-dev-start--stop--restart--rebuild--was-passiert-wirklich)
14. [target/ — warum gibt es diesen Ordner?](#14-target--warum-gibt-es-diesen-ordner)
15. [GitHub Actions — CI/CD erklärt](#15-github-actions--cicd-erklärt)
16. [Monitoring & Alerting — Architektur und Implementierung](#16-monitoring--alerting--architektur-und-implementierung)

---

## 1. Das große Bild — was macht das alles?

Das Backend ist ein **HTTP-Server**. Er hört auf Port 8080 und wartet auf Anfragen.

```
Browser / React-Frontend
        │
        │  HTTP-Anfrage: GET /api/products
        ▼
Spring Boot (Port 8080)        ← das ist dieses Projekt
        │
        │  SQL: SELECT * FROM products
        ▼
PostgreSQL (Port 5432)         ← läuft in Docker
```

Das Frontend schickt eine Anfrage, das Backend liest/schreibt in der Datenbank und
antwortet mit JSON. Das ist alles — der Rest ist Infrastruktur damit das sicher,
wartbar und deploybar ist.

---

## 2. Dateien im Root-Verzeichnis

```
Webshop-Backend/
├── pom.xml                    ← Maven Konfiguration (Abhängigkeiten, Build)
├── Dockerfile                 ← Baut das Spring Boot Image (Multi-Stage: Maven + JRE)
├── .dockerignore              ← Was Docker beim Build ignorieren soll
├── docker-compose.yml         ← Definiert postgres + backend Container
├── .env.example               ← Vorlage für Umgebungsvariablen
├── .env                       ← Deine lokalen Secrets (nie committen!)
├── .gitignore                 ← Was Git ignorieren soll
├── dev.bat                    ← Thin-Wrapper: ruft dev.ps1 auf
├── dev.ps1                    ← Das eigentliche Start/Stop/Rebuild Script (nur Docker)
├── README.md                  ← Einstiegsdokumentation
├── deepdive.md                ← dieses Dokument
├── docs/                      ← API-Dokumentation für das Frontend-Team
│   ├── ROLLEN.md              ← Rollenkonzept + vollständige Berechtigungsmatrix
│   ├── milestone-1-auth-benutzerverwaltung.md
│   ├── milestone-2-produktkatalog.md
│   ├── milestone-monitoring-alerting.md ← Monitoring, Alerting, Admin-UI
│   ├── email-integration.md   ← E-Mail-System, Redirect-Mechanismus, Anbindung neuer Features
│   └── ...                    ← eine Datei pro Frontend-Milestone
├── mvnw / mvnw.cmd            ← Maven Wrapper (wird von GitHub Actions CI genutzt)
├── .mvn/wrapper/
│   └── maven-wrapper.properties ← welche Maven-Version soll heruntergeladen werden
├── .github/workflows/
│   ├── ci.yml                 ← GitHub Actions: Tests bei Pull Request
│   └── cd.yml                 ← GitHub Actions: Deploy bei Merge in main
└── src/                       ← der eigentliche Quellcode
```

### pom.xml
`pom.xml` = Project Object Model. Das ist die Steuerzentrale des Projekts für Maven —
vergleichbar mit `package.json` in einem Node-Projekt.

Es definiert:
- **Abhängigkeiten** (Libraries die das Projekt braucht, z.B. Spring Boot, JWT, PostgreSQL-Treiber)
- **Build-Konfiguration** (Java-Version, Plugins)
- **Projekt-Metadaten** (Name, Version, Gruppe)

Wenn du eine neue Library brauchst, fügst du sie hier ein — Maven lädt sie automatisch herunter.

Besonderheit in dieser `pom.xml`:
```xml
<lombok.version>1.18.38</lombok.version>
```
Lombok 1.18.36 (Spring Boot Standard) crasht mit Java 24. Wir überschreiben die Version.

### mvnw / mvnw.cmd
Maven Wrapper Scripts — werden **nicht mehr für lokale Entwicklung** genutzt.
Lokal läuft Maven jetzt vollständig im Docker-Container (via `Dockerfile`).

Die Wrapper-Scripts bleiben im Repository, weil **GitHub Actions** sie für die
CI/CD-Pipeline verwendet (`./mvnw test`, `./mvnw package`) — dort ist Java direkt
auf dem Runner verfügbar, ohne Docker.

### .mvn/wrapper/maven-wrapper.properties
```properties
distributionUrl=https://repo.maven.apache.org/maven2/org/.../apache-maven-3.9.9-bin.zip
```
Sagt dem Wrapper: "Lade Maven 3.9.9 herunter wenn es noch nicht da ist."

### docker-compose.yml
Beschreibt **zwei Container** — Datenbank und Backend:
```yaml
services:
  postgres:
    image: postgres:16-alpine    # PostgreSQL Version 16, minimal
    container_name: webshop-postgres
    ports:
      - "5432:5432"
    healthcheck:                 # wann ist der Container "bereit"?
      test: ["CMD-SHELL", "pg_isready -U webshop"]

  backend:
    build: .                     # Baut das Image aus dem Dockerfile
    container_name: webshop-backend
    ports:
      - "8080:8080"
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/webshop
    depends_on:
      postgres:
        condition: service_healthy   # startet erst wenn DB healthy ist
```
`dev start` führt `docker compose up -d --build` aus — baut das Backend-Image
und startet beide Container. Anschließend wird `/api/health` gepollt bis `200 OK`.

### .env / .env.example
`.env.example` ist die Vorlage — sie ist im Repository und zeigt welche Variablen
gesetzt werden müssen. `.env` ist deine lokale Kopie mit echten Werten — sie ist in
`.gitignore` und kommt nie ins Repository (würde Passwörter leaken).

Spring Boot liest `application.properties`, und dort stehen Ausdrücke wie:
```properties
spring.datasource.password=${DB_PASSWORD:localdev}
```
Das bedeutet: "Nimm die Umgebungsvariable `DB_PASSWORD`, falls nicht gesetzt nutze `localdev`."

### dev.bat
```bat
@echo off
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0dev.ps1" %*
```
Nichts weiter als ein Wrapper der `dev.ps1` aufruft — mit `-ExecutionPolicy Bypass`
damit PowerShell-Scripts trotz Windows-Sicherheitsrichtlinien ausgeführt werden können.

### dev.ps1
Das eigentliche Steuerungsskript. Alles läuft über Docker — kein Java lokal nötig.

| Befehl | Was passiert |
|---|---|
| `dev start` | `docker compose up -d --build` → HTTP-Poll auf `/api/health` bis bereit |
| `dev stop` | `docker compose down` → alle Container stoppen |
| `dev stop --keep-db` | Nur Backend-Container stoppen, PostgreSQL bleibt laufen |
| `dev restart` | Backend-Container stoppen + neu starten |
| `dev rebuild` | `docker compose up --build --force-recreate` (kein Layer-Cache) |

---

## 3. Maven — wie wird das Projekt gebaut?

Maven ist ein Build-Tool. Es übersetzt Java-Quellcode in ausführbaren Bytecode und
verwaltet alle Libraries (Abhängigkeiten).

### Was Maven macht wenn du `dev start` ausführst

Maven läuft jetzt **innerhalb des Docker-Containers** (nicht mehr lokal). Das Dockerfile
steuert den Build in zwei Stufen:

```
docker compose up --build
        │
        ├── Stage 1: Build-Container (maven:3.9-eclipse-temurin-21-alpine)
        │     ├── 1. Kopiert pom.xml → lädt alle Abhängigkeiten herunter
        │     │         (Layer-Cache: nur bei pom.xml-Änderungen wiederholt)
        │     │
        │     ├── 2. Kopiert src/ → kompiliert Java → erstellt JAR
        │     │
        │     └── Ergebnis: target/webshop-0.0.1-SNAPSHOT.jar (im Container)
        │
        └── Stage 2: Runtime-Container (eclipse-temurin:21-jre-alpine)
              └── Kopiert nur das JAR → minimales Image → startet Spring Boot
```

### Der Docker Layer-Cache
Docker cached jede Zeile des Dockerfiles als Layer. Wenn sich `pom.xml` nicht ändert,
überspringt Docker den `mvn dependency:go-offline`-Schritt — die Dependencies sind
bereits im Cache. Ändert sich nur der Java-Code, läuft nur der `mvn package`-Schritt neu.

### Wann `dev rebuild` statt `dev restart`?
- Nach Änderungen am `Dockerfile` selbst
- Wenn der Cache einen veralteten Stand hat und komische Build-Fehler auftreten
- Für normale Code-Änderungen reicht `dev restart`

---

## 4. Docker — wie laufen Datenbank und Backend?

Docker ist eine Container-Laufzeitumgebung. Ein Container ist ein isolierter Prozess
mit eigenem Dateisystem — wie eine sehr leichtgewichtige virtuelle Maschine.

Das gesamte Backend läuft in Docker. Kein Java, kein Maven lokal nötig.

```
dein Rechner
└── Docker Engine
    ├── Container: webshop-postgres
    │   ├── PostgreSQL 16 Prozess
    │   ├── Port 5432 (gemapped auf Host-Port 5432)
    │   └── Volume: postgres_data (persistente Daten)
    │
    └── Container: webshop-backend
        ├── Java 21 + Spring Boot Prozess (aus dem Dockerfile gebaut)
        ├── Port 8080 (gemapped auf Host-Port 8080)
        └── Verbindet sich intern mit postgres:5432 (Docker-internes Netzwerk)
```

Das Volume `postgres_data` sorgt dafür dass die Datenbank-Daten erhalten bleiben
wenn der Container neu gestartet wird. Ohne Volume würde alles bei jedem Start
gelöscht werden.

Die beiden Container kommunizieren über das interne Docker-Netzwerk. Das Backend
erreicht die Datenbank unter dem Hostnamen `postgres` (= der Service-Name in
`docker-compose.yml`) — nicht `localhost`.

### Nützliche Docker-Befehle
```bash
docker compose ps              # Status der Container
docker compose logs postgres   # PostgreSQL Logs
docker exec -it webshop-postgres psql -U webshop -d webshop  # SQL-Shell
docker compose down -v         # Container + Volume löschen (Daten weg!)
```

---

## 5. Spring Boot — wie startet die Anwendung?

### WebshopApplication.java
```java
@SpringBootApplication
@EnableScheduling
public class WebshopApplication {
    public static void main(String[] args) {
        SpringApplication.run(WebshopApplication.class, args);
    }
}
```
Das ist der Einstiegspunkt — die `main`-Methode. `@SpringBootApplication` ist eine
Kombination aus drei Annotationen:
- `@Configuration` — diese Klasse kann Beans definieren
- `@EnableAutoConfiguration` — Spring Boot konfiguriert sich selbst basierend auf
  den Libraries im Classpath (findet Spring Security → aktiviert es automatisch)
- `@ComponentScan` — durchsucht das Package nach Klassen mit `@Service`, `@Controller` etc.

`@EnableScheduling` aktiviert den Scheduler für zeitgesteuerte Jobs (`@Scheduled`).

### Was passiert beim Start (in Reihenfolge)

```
1. main() wird aufgerufen
2. Spring scannt alle Packages nach Annotationen
3. Spring erstellt alle Beans (Services, Repositories, Controller, ...)
4. Flyway läuft: prüft welche Migrations schon ausgeführt wurden,
   führt neue aus
5. Hibernate initialisiert das JPA EntityManagerFactory
6. Spring Security konfiguriert sich (SecurityConfig.java)
7. Tomcat (eingebetteter Webserver) startet auf Port 8080
8. "Started WebshopApplication in X seconds" — fertig
```

### Was ist ein "Bean"?
Ein Bean ist einfach ein Objekt das Spring verwaltet. Spring erstellt es einmal
(Singleton) und injiziert es überall wo es gebraucht wird. Du erkennst Beans an
Annotationen wie `@Service`, `@Component`, `@Repository`, `@Controller`, `@Bean`.

**Dependency Injection** — statt `new ProductService()` zu schreiben:
```java
@RestController
@RequiredArgsConstructor        // Lombok generiert einen Konstruktor
public class ProductController {
    private final ProductService productService;  // Spring injiziert das hier
}
```
Spring weiß: `ProductController` braucht ein `ProductService` → gibt ihm den
vorher erstellten Bean.

---

## 6. Konfiguration — application.properties

```properties
src/main/resources/application.properties
```

Hier werden alle Laufzeit-Konfigurationen gesetzt. Alle Werte können über
Umgebungsvariablen überschrieben werden (Syntax: `${VARIABLE:default}`).

| Eigenschaft | Bedeutung |
|---|---|
| `server.port=8080` | Tomcat hört auf Port 8080 |
| `spring.datasource.url` | JDBC-URL zur Datenbank |
| `spring.jpa.hibernate.ddl-auto=none` | Hibernate darf das Schema NICHT ändern — Flyway macht das |
| `spring.flyway.locations=classpath:db/migration` | wo Flyway die SQL-Dateien sucht |
| `app.jwt.secret` | Schlüssel zum Signieren von JWT-Tokens |
| `app.cors.allowed-origins` | welche Origins dürfen Anfragen schicken (Frontend-URL) |
| `springdoc.swagger-ui.enabled` | Swagger UI an/aus |

---

## 7. Die Datenbankschicht — Flyway + JPA

### Flyway — Versionskontrolle für die Datenbank

```
src/main/resources/db/migration/
├── V1__create_users.sql
├── V2__create_user_details.sql
├── V3__create_products.sql
├── V4__create_discounts.sql
├── V5__create_coupons.sql
├── V6__create_cart.sql
├── V7__create_orders.sql
├── V8__create_standing_orders.sql
├── V9__create_audit_log.sql
├── V10__mock_data.sql
├── V11__extend_cart_checkout_and_stock.sql
├── V12__add_shipping_method_to_orders.sql
├── V13__create_system_notifications.sql
├── V14__add_standing_order_interval_types.sql
├── V15__create_follow_up_orders.sql
├── V16__mock_reporting_orders.sql
├── V17__create_alerting_tables.sql
├── V18__mock_system_notifications.sql
├── V19__add_warehouse_fulfillment.sql
├── V20__create_loyalty.sql
├── V21__mock_loyalty_data.sql
└── V22__add_wishlist_state_to_users.sql
```

Beim Start prüft Flyway die Tabelle `flyway_schema_history` in der Datenbank.
Dort steht welche Migrations schon ausgeführt wurden. Neue werden automatisch
ausgeführt. Bestehende werden nie geändert — dafür legt man eine neue Datei an.

**Namenskonvention:** `V{Nummer}__{Beschreibung}.sql` — zwei Unterstriche!

### JPA / Hibernate — Datenbank ohne SQL schreiben

JPA (Java Persistence API) ist ein Standard der beschreibt wie Java-Objekte in
Datenbanktabellen gespeichert werden. Hibernate ist die Implementierung dahinter.

Eine Entity-Klasse repräsentiert eine Tabelle:
```java
@Entity
@Table(name = "products")
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;              // → Spalte "id" (Auto-Increment)

    private String name;          // → Spalte "name"
    private boolean purchasable;  // → Spalte "purchasable"
}
```

Ein Repository ist ein Interface — Spring generiert die SQL-Abfragen automatisch:
```java
public interface ProductRepository extends JpaRepository<Product, Long> {
    // Spring generiert: SELECT * FROM products WHERE name LIKE ?
    List<Product> findByNameContaining(String keyword);
}
```

`JpaRepository` gibt dir kostenlos: `findAll()`, `findById()`, `save()`, `delete()` — fertig.

### dev-seed.sql
```
src/main/resources/db/dev-seed.sql
```
Testdaten für die lokale Entwicklung. Enthält Beispiel-User (alice, bob, carol,
dave, admin), Produkte, Rabatte, einen befüllten Warenkorb und eine abgeschlossene
Bestellung. Wird **nicht** automatisch eingespielt — einmalig manuell:
```bash
docker exec -i webshop-postgres psql -U webshop -d webshop < src/main/resources/db/dev-seed.sql
```

---

## 8. Das Package-System — wie ist der Code organisiert?

Der Code ist nach **Features** organisiert, nicht nach Typen. Alles was zu
"Produkten" gehört liegt in `product/`, alles zu "Bestellungen" in `order/` usw.

```
de.fhdw.webshop/
├── config/          ← Konfiguration (Security, CORS, Fehlerbehandlung)
├── auth/            ← Login, Registrierung, JWT
├── user/            ← Benutzerprofile, Adressen, Zahlungsarten
├── product/         ← Artikelkatalog
├── cart/            ← Warenkorb
├── order/           ← Bestellungen
├── discount/        ← Rabatte und Coupons
├── customer/        ← Mitarbeiter-Endpunkte für Kundenverwaltung
├── standingorder/   ← Daueraufträge
├── followuporder/   ← Folgebestellungen
├── notification/    ← E-Mail, System-Benachrichtigungen, Scheduler
├── alerting/        ← Business-Alerting, bekannte E-Mail-Adressen, Admin-Konfiguration
├── recommendation/  ← Shoppi-Produktempfehlungen
├── address/         ← Adress-Lookup (PLZ-Validierung)
├── chat/            ← Shoppi KI-Assistent
└── admin/           ← Admin-Endpunkte und Audit-Log
```

### Das 4-Schichten-Muster

Jedes Feature-Package folgt demselben Aufbau:

```
HTTP-Anfrage
     │
     ▼
Controller      ← nimmt HTTP-Anfrage entgegen, gibt HTTP-Antwort zurück
     │              kennt keine Datenbankdetails
     ▼
Service         ← enthält die Geschäftslogik
     │              "darf dieser User das?", "wie wird der Preis berechnet?"
     ▼
Repository      ← spricht mit der Datenbank
     │              gibt Java-Objekte zurück
     ▼
Datenbank
```

Diese Trennung hat einen Zweck: jede Schicht hat genau eine Aufgabe.
Der Controller weiß nicht wie Preise berechnet werden.
Das Repository weiß nicht wer eingeloggt ist.

---

## 9. Jede Datei erklärt

### config/

**`SecurityConfig.java`**
Konfiguriert Spring Security. Definiert welche Endpunkte ohne Login erreichbar sind
(`permitAll()`) und welche Authentifizierung erfordern (`authenticated()`).
Schaltet Session-Management aus (wir nutzen JWT, keine Sessions).
Registriert den `JwtAuthenticationFilter`.

**`JwtAuthenticationFilter.java`**
Läuft bei **jeder** HTTP-Anfrage. Liest den `Authorization: Bearer <token>`-Header,
validiert den Token mit `JwtTokenProvider`, setzt den eingeloggten User in den
Spring Security Context. Danach kann jeder Controller per
`@AuthenticationPrincipal User currentUser` wissen wer die Anfrage schickt.

**`CorsConfig.java`**
CORS = Cross-Origin Resource Sharing. Browser blockieren standardmäßig Anfragen von
einer anderen Domain. Das Frontend läuft auf `localhost:5173`, das Backend auf
`localhost:8080` — das sind zwei verschiedene "Origins". Diese Konfiguration
sagt dem Browser: "Anfragen von `localhost:5173` sind erlaubt."

**`GlobalExceptionHandler.java`**
Fängt alle Exceptions die in Controllern oder Services geworfen werden und verwandelt
sie in einheitliche JSON-Fehlerantworten:
```json
{ "status": 404, "error": "Not Found", "message": "Product not found: 99" }
```
Ohne diesen Handler würde Spring Boot eigene, unformatierte HTML-Fehlerseiten liefern.

**`HealthController.java`**
```
GET /api/health → { "status": "UP" }
```
Einziger Zweck: prüfen ob der Server überhaupt läuft.

---

### auth/

**`AuthController.java`**
Endpunkte: `POST /api/auth/login`, `POST /api/auth/register`, `POST /api/auth/logout`.
Nimmt die Anfrage entgegen, delegiert an `AuthService`, gibt JWT zurück.

**`AuthService.java`**
Enthält die Login-Logik: Benutzer suchen, Passwort prüfen (BCrypt), JWT generieren.
Bei der Registrierung: prüfen ob Username/E-Mail schon vergeben, Passwort hashen,
User in DB speichern.

**`JwtTokenProvider.java`**
Alles rund um JWT-Tokens:
- `generateToken(user)` → erstellt einen Token (signiert mit dem Secret aus `application.properties`)
- `validateToken(token)` → prüft Signatur und Ablaufzeit
- `getUsernameFromToken(token)` → liest den Username aus dem Token

**`TokenBlacklist.java`**
JWT ist zustandslos — einmal ausgestellt gilt er bis zum Ablauf. Für Logout brauchen
wir trotzdem eine Möglichkeit Token zu invalidieren. `TokenBlacklist` hält eine
In-Memory-Liste abgemeldeter Tokens. Der `JwtAuthenticationFilter` prüft diese Liste.
**Achtung:** Beim Server-Neustart ist die Blacklist leer — abgemeldete Tokens sind
dann wieder gültig bis zu ihrer natürlichen Ablaufzeit (24h).

**`dto/` (Data Transfer Objects)**
- `LoginRequest.java` — `{ username, password }`
- `RegisterRequest.java` — `{ username, email, password, userType }`
- `AuthResponse.java` — `{ token, username, role }`

DTOs sind einfache Datencontainer die beschreiben wie ein Request-Body aussehen muss
oder was zurückgegeben wird. Sie sind **keine** Datenbankentities.

---

### user/

**`User.java`**
Die wichtigste Entity. Repräsentiert einen Benutzer in der Datenbank (`users`-Tabelle).
Implementiert `UserDetails` (Spring Security Interface) damit Spring weiß wie er
Benutzer authentifizieren soll.

Felder: `id`, `username`, `email`, `passwordHash`, `role`, `userType`, `customerNumber`, `active`.

`role` ist ein Enum: `CUSTOMER`, `EMPLOYEE`, `SALES_EMPLOYEE`, `ADMIN`.
`userType` ist ein Enum: `PRIVATE`, `BUSINESS`.

**`UserRole.java` / `UserType.java`**
Java Enums. `UserRole` definiert die vier möglichen Rollen. Wird als `VARCHAR` in der
Datenbank gespeichert (z.B. `"CUSTOMER"`).

**`UserRepository.java`**
Interface für Datenbankzugriffe auf die `users`-Tabelle. Enthält JPQL-Queries für
die Kundensuche (mit `= ''` statt `IS NULL` wegen eines PostgreSQL-Bugs mit
Stringparametern die als `bytea` interpretiert werden).

**`UserService.java`**
Geschäftslogik für Profiländerungen: Passwort ändern (altes Passwort prüfen, neu hashen),
E-Mail ändern, Account löschen.

**`UserController.java`**
Endpunkte unter `/api/users/me/*`: eigenes Profil laden, Passwort ändern, E-Mail
ändern, Lieferadresse setzen, Zahlungsart setzen, Account löschen.

**`UserDetailsServiceImpl.java`**
Implementiert das Spring Security Interface `UserDetailsService`. Spring Security
ruft `loadUserByUsername(username)` auf wenn es einen User authentifizieren will.

**`DeliveryAddress.java` / `PaymentMethod.java` / `BusinessInfo.java`**
Eigene Entities für die 1:1-Beziehung User → Adresse/Zahlungsart/Firmeninfo.
Jede hat ihre eigene Tabelle und ein eigenes Repository (`DeliveryAddressRepository` etc.).

---

### product/

**`Product.java`**
Entity für die `products`-Tabelle. Felder: `name`, `description`, `imageUrl`,
`recommendedRetailPrice`, `category`, `purchasable`, `promoted`, `createdAt`.

`purchasable` = sichtbar und bestellbar für Kunden.
`promoted` = wird oben in der Liste angezeigt (ORDER BY promoted DESC).

**`ProductRepository.java`**
Enthält die `searchProducts`-Methode mit einer JPQL-Query die alle Filter kombiniert:
purchasable-Flag, Kategorie, Suchbegriff. Sonderlösung: `= ''` statt `IS NULL` für
String-Parameter wegen PostgreSQL/Hibernate-Kompatibilität.

**`ProductService.java`**
Geschäftslogik: CRUD, Preis mit Rabatt berechnen. Enthält das Interface
`DiscountLookupPort` — ein funktionales Interface das `ProductService` von
`DiscountService` entkoppelt (damit `product/` nicht direkt von `discount/` abhängt).

**`ProductController.java`**
REST-Endpunkte: `GET /api/products`, `GET /api/products/{id}`,
`GET /api/products/{id}/price-for-customer`, `GET /api/products/{id}/statistics`,
`POST /api/products`, und diverse `PUT`-Endpunkte für einzelne Felder.

**`dto/`**
- `ProductRequest.java` — Body für POST (neues Produkt anlegen)
- `ProductResponse.java` — was zurückgegeben wird (alle Felder)
- `ProductPriceResponse.java` — UVP + effektiver Preis + Rabattprozent
- `ProductStatisticsResponse.java` — Verkaufszahlen in einem Zeitraum
- `SetFlagRequest.java` — `{ value: true/false }` für purchasable/promoted
- `UpdateDescriptionRequest.java` / `UpdateImageRequest.java` / `UpdatePriceRequest.java`

---

### cart/

**`CartItem.java`**
Entity für `cart_items`. Verbindet einen User mit einem Produkt und einer Menge.
Kein eigener Warenkorb-Entity — die Gesamtheit aller `CartItem` eines Users ist sein Warenkorb.

**`CartRepository.java`**
Datenbankzugriffe: nach User filtern, nach User+Produkt suchen (für "schon im Warenkorb?"),
löschen nach User+Produkt, alles löschen nach User (beim Checkout).

**`CartService.java`**
- `getCart()` — lädt alle Items, berechnet effectivePrice (mit Rabatt), subtotal, 19% MwSt.,
  4,99€ Versandkosten, Gesamtpreis
- `addItem()` — prüft ob Produkt kaufbar, erhöht Menge wenn schon im Warenkorb
- `removeItem()` — löscht einen Artikel
- `reorder()` — fügt alle noch kaufbaren Artikel einer alten Bestellung dem Warenkorb hinzu
- `clearCart()` — leert den Warenkorb nach dem Checkout

**`CartController.java`**
Endpunkte: `GET /api/cart`, `POST /api/cart/items`, `DELETE /api/cart/items/{productId}`,
`POST /api/cart/reorder/{orderId}`.

**`dto/CartResponse.java`**
```java
record CartResponse(
    List<CartItemResponse> items,
    BigDecimal subtotal,
    BigDecimal tax,
    BigDecimal shippingCost,
    BigDecimal total
)
```
`record` ist Java-Syntax für eine unveränderliche Datenklasse — automatisch Getter,
`equals()`, `hashCode()`, `toString()` ohne Lombok.

---

### order/

**`Order.java`**
Entity für `orders`. Felder: `customer`, `totalPrice`, `taxAmount`, `shippingCost`,
`status` (PENDING/COMPLETED/CANCELLED), `createdAt`, `items` (Liste von OrderItems).

**`OrderItem.java`**
Entity für `order_items`. Speichert `product`, `quantity` und **`priceAtOrderTime`** —
den Preis zum Zeitpunkt der Bestellung. Wichtig: Produktpreise können sich ändern,
aber der historische Preis in der Bestellung bleibt erhalten.

**`OrderStatus.java`**
Enum: `PENDING`, `COMPLETED`, `CANCELLED`.

**`OrderService.java`**
`placeOrder()` ist die komplexeste Methode: lädt alle Cart-Items, berechnet Preise
(mit Rabatten), prüft Zahlungsart und Lieferadresse, erstellt Order + OrderItems,
leert den Warenkorb, schreibt Audit-Log.

**`OrderController.java`**
`GET /api/orders` (History), `GET /api/orders/{id}` (Einzelbestellung mit Items),
`POST /api/orders` (Bestellung aufgeben).

---

### discount/

**`Discount.java`**
Entity für `discounts`. Ein Rabatt gilt für einen bestimmten Kunden auf ein bestimmtes
Produkt mit optionalem Ablaufdatum (`validUntil = null` = unbefristet).

**`Coupon.java`**
Entity für `coupons`. Einmalrabatt mit Code — wird nach Verwendung als `used = true` markiert.

**`DiscountService.java`**
Implementiert `ProductService.DiscountLookupPort`. Die Methode
`findBestActiveDiscountPercent(customerId, productId)` sucht den besten aktiven Rabatt
(oder Coupon) für einen Kunden auf ein Produkt.

**`dto/`**
- `CreateDiscountRequest.java` — `{ productId, discountPercent, validFrom, validUntil }`
- `CreateCouponRequest.java` — `{ code, discountPercent, validUntil }`
- `DiscountResponse.java` — Antwort beim Anlegen

Kein eigener Controller — Rabatte werden über `CustomerController` verwaltet
(`POST /api/customers/{id}/discounts`, `POST /api/customers/{id}/coupons`).

---

### customer/

**`CustomerController.java`**
Alle Endpunkte die Mitarbeiter für die Kundenbetreuung brauchen:
- `GET /api/customers` — Kundenliste (mit Suche)
- `GET /api/customers/{id}` — einzelner Kunde
- `GET /api/customers/{id}/business-info` — Firmeninfo
- `GET /api/customers/{id}/orders` — Bestellhistorie des Kunden
- `GET /api/customers/{id}/revenue` — Umsatzstatistik
- `GET/POST/DELETE /api/customers/{id}/cart` — Warenkorb des Kunden verwalten
- `POST /api/customers/{id}/discounts` — Rabatt anlegen
- `POST /api/customers/{id}/coupons` — Coupon anlegen
- `POST /api/customers/{id}/email` — E-Mail senden

**`StatisticsService.java`**
Berechnet Umsatzstatistiken für Kunden und Artikel in einem Zeitraum.

**`RevenueStatisticsResponse.java`**
DTO für die Umsatzstatistik-Antwort.

---

### standingorder/

**`StandingOrder.java`**
Entity für `standing_orders`. Felder: `customer`, `intervalDays` (wie oft?),
`nextExecutionDate` (wann das nächste Mal?), `active`, `items`.

**`StandingOrderItem.java`**
Entity für `standing_order_items`. Produkt + Menge für einen Dauerauftrag.

**`StandingOrderService.java`**
CRUD für Daueraufträge. `executeAllDue()` wird vom Scheduler aufgerufen:
sucht alle Daueraufträge mit `nextExecutionDate <= heute`, legt für jeden eine
neue Bestellung an, setzt `nextExecutionDate += intervalDays`.

**`StandingOrderScheduler.java`**
```java
@Scheduled(cron = "0 0 6 * * *")  // täglich um 06:00 Uhr
public void executeScheduledOrders() {
    standingOrderService.executeAllDue();
}
```
Spring führt diese Methode automatisch täglich aus. Kein manueller Aufruf nötig.

**`StandingOrderController.java`**
CRUD-Endpunkte unter `/api/standing-orders`.

---

### notification/

**`EmailService.java`**
Versendet E-Mails über `JavaMailSender`. Alle ausgehenden E-Mails werden an die
konfigurierten `isDefault=true`-Adressen aus `KnownEmailAddressRepository` umgeleitet —
die ursprünglich vorgesehene Empfängeradresse wird im E-Mail-Body dokumentiert.
Das ist eine bewusste Schutzmaßnahme für das Uni-Projekt: echte Kundenemails werden
nie kontaktiert. Sind keine Default-Adressen konfiguriert, sendet das System an die
eigene Absenderadresse (`APP_MAIL_FROM`). Drei öffentliche Methoden:
- `sendEmail(intendedAddress, subject, body)` — allgemeine Methode, alle anderen delegieren hierhin
- `sendEmailToCustomer(customer, subject, body)` — Wrapper für Kunden-E-Mails
- `sendPasswordChangedNotification(user)` — Passwortänderungs-Bestätigung
- `sendAdminAlert(subject, body)` — Monitoring-Alerts an `ALERT_ADMIN_EMAIL`

**`NotificationScheduler.java`**
```java
@Scheduled(cron = "0 0 7 * * MON")  // jeden Montag um 07:00 Uhr
public void checkSalesAlerts() { ... }
```
Zwei Checks:
- US38: Ist der Wochenabsatz eines Artikels um mehr als 20% gefallen?
- US39: Hat ein kaufbarer Artikel in den letzten 30 Tagen 0 Stück verkauft?

Ergebnis: `log.warn("ALERT [US#38]: ...")` — Logs landen in `target/dev.log`.

**`dto/SendEmailRequest.java`**
`{ subject, body }` — Body für `POST /api/customers/{id}/email`.

---

### admin/

**`AuditLog.java`**
Entity für `audit_log`. Jede relevante Aktion wird protokolliert:
`userId`, `action` (z.B. "ORDER_PLACED"), `entityType`, `entityId`,
`initiatedBy` (USER/ADMIN/SYSTEM), `timestamp`, `details`.

**`AuditInitiator.java`**
Enum: `USER`, `ADMIN`, `SYSTEM`. Unterscheidet ob eine Aktion durch einen normalen
User, einen Admin (Impersonation) oder automatisch (Scheduler) ausgelöst wurde.

**`AuditLogService.java`**
`log(userId, action, entityType, entityId, initiatedBy, details)` —
wird aus Services aufgerufen wenn etwas protokolliert werden soll.

**`AuditLogRepository.java`**
Datenbankzugriff auf `audit_log`.

**`AdminController.java`**
- `GET /api/admin/users` — alle Benutzer
- `DELETE /api/admin/users/{id}` — Benutzer deaktivieren
- `POST /api/admin/impersonate/{userId}` — generiert einen JWT der als ein anderer User
  funktioniert (wird im Audit-Log mit `initiatedBy = ADMIN` protokolliert)
- `GET /api/admin/audit-log` — Transaktionshistorie

---

### alerting/

**`AlertEventType.java`**
Enum aller konfigurierbaren Alert-Typen (z.B. `HIGH_ERROR_RATE`, `HIGH_HEAP_USAGE`).
Jeder Wert entspricht einer Zeile in `alert_event_configs`.

**`AlertEventConfig.java`**
Entity für `alert_event_configs`. Speichert pro Event-Typ: `enabled` (an/aus),
`recipientStrategy` (`ALL_DEFAULT` = alle Default-Adressen, `SPECIFIC` = eigene Liste)
und eine Liste von `KnownEmailAddress`-Empfängern. Wird beim ersten Start durch
`AlertingDataInitializer` mit Zeilen für alle bekannten `AlertEventType`-Werte befüllt.

**`KnownEmailAddress.java`**
Entity für `known_email_addresses`. Speichert `email`, `label` und `isDefault`.
Adressen mit `isDefault=true` dienen als Redirect-Ziel für **alle** ausgehenden
E-Mails in `EmailService` — kein Kunden-E-Mail-Versand ohne explizite Konfiguration.

**`BusinessEmailService.java`**
Versendet Business-Alerts pro `AlertEventType`. Liest die Konfiguration aus
`AlertEventConfig`: wenn aktiviert, werden die konfigurierten Empfänger benachrichtigt.
Enthält auch `sendTestAlert()` für manuellen Test aus der Admin-UI.

**`AlertConfigController.java`**
REST-Endpunkte unter `/api/admin/alerting/` (alle ADMIN-geschützt):
- `GET /events` — alle Alert-Typ-Konfigurationen laden
- `PUT /events/{eventType}` — einzelnen Alert-Typ aktivieren/deaktivieren
- `GET /emails` — alle bekannten E-Mail-Adressen
- `POST /emails` — neue Adresse hinzufügen
- `DELETE /emails/{id}` — Adresse entfernen

**`AlertingDataInitializer.java`**
`@Component` mit `@PostConstruct`: legt beim Start fehlende `AlertEventConfig`-Zeilen
in der Datenbank an. Wird nur einmal ausgeführt — bestehende Konfigurationen bleiben
unverändert.

**`RecipientStrategy.java`**
Enum: `ALL_DEFAULT` (alle `isDefault=true`-Adressen) oder `SPECIFIC` (nur die
direkt am `AlertEventConfig` hängenden Adressen).

**`dto/`**
- `AddKnownEmailRequest.java` — `{ email, label, isDefault }`
- `UpdateAlertEventConfigRequest.java` — `{ enabled, recipientStrategy }`
- `AlertEventConfigResponse.java` / `KnownEmailAddressResponse.java`

---

## 10. Sicherheit — wie funktioniert JWT?

### Login-Flow

```
1. POST /api/auth/login { username, password }
        │
        ▼
2. AuthService: User in DB suchen, Passwort mit BCrypt prüfen
        │
        ▼
3. JwtTokenProvider.generateToken(user)
   → erstellt Token: Header.Payload.Signature
   → Payload enthält: username, role, Ablaufzeit (24h)
   → Signature = HMAC-SHA256(Header + Payload, JWT_SECRET)
        │
        ▼
4. Antwort: { token: "eyJ...", username: "alice", role: "CUSTOMER" }
```

### Jede weitere Anfrage

```
GET /api/cart
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
        │
        ▼
JwtAuthenticationFilter (läuft vor JEDEM Request)
   1. Header lesen
   2. Token aus Blacklist prüfen (abgemeldet?)
   3. JwtTokenProvider.validateToken() → Signatur + Ablaufzeit prüfen
   4. Username aus Token lesen
   5. User aus DB laden
   6. User in SecurityContext setzen
        │
        ▼
CartController
   @AuthenticationPrincipal User currentUser  ← das ist jetzt befüllt
```

### Passwort-Hashing
Passwörter werden **nie** im Klartext gespeichert. BCrypt hasht das Passwort:
```
"Password1!" → "$2a$10$X9mQ3..." (60 Zeichen, einmalig, nicht umkehrbar)
```
Beim Login: `BCrypt.verify("Password1!", "$2a$10$X9mQ3...")` → true/false.

---

## 11. Eine Anfrage von Anfang bis Ende

Beispiel: `GET /api/products?purchasable=true` (Kunde lädt Sortiment)

```
1. Browser schickt:
   GET /api/products?purchasable=true
   Authorization: Bearer eyJ...

2. Tomcat nimmt die TCP-Verbindung an

3. JwtAuthenticationFilter läuft:
   → Token valide → alice (CUSTOMER) in SecurityContext setzen

4. Spring Security prüft: darf CUSTOMER /api/products aufrufen? → ja (permitAll)

5. ProductController.listProducts() wird aufgerufen:
   @GetMapping → method matched
   @RequestParam Boolean purchasable = true
   @AuthenticationPrincipal User currentUser = alice

6. ProductController delegiert an ProductService.listProducts(true, "", "")

7. ProductService delegiert an ProductRepository.searchProducts(true, "", "")

8. Hibernate übersetzt JPQL in SQL:
   SELECT p FROM Product p
   WHERE p.purchasable = true
   ORDER BY p.promoted DESC, p.name ASC

   → SQL: SELECT id, name, ... FROM products
          WHERE purchasable = true
          ORDER BY promoted DESC, name ASC

9. PostgreSQL führt die Abfrage aus, gibt Ergebnisse zurück

10. Hibernate baut Java-Objekte (List<Product>)

11. ProductService wandelt in List<ProductResponse> um (nur die Felder die der Client braucht)

12. ProductController gibt ResponseEntity.ok(products) zurück

13. Spring wandelt List<ProductResponse> in JSON um:
    [{ "id": 1, "name": "Laptop", "price": 999.99, ... }, ...]

14. Tomcat sendet HTTP 200 + JSON zurück an den Browser
```

---

## 12. Scheduled Jobs — was läuft automatisch?

Spring führt Methoden mit `@Scheduled` automatisch aus — kein manueller Aufruf nötig.
`@EnableScheduling` in `WebshopApplication.java` aktiviert diesen Mechanismus.

| Klasse | Zeitplan | Was passiert |
|---|---|---|
| `StandingOrderScheduler` | täglich 06:00 | Daueraufträge fällig? → Bestellung anlegen, Datum weiterrücken |
| `NotificationScheduler` | montags 07:00 | Absatz-Checks für alle kaufbaren Artikel (>20% Drop, 0 Stück) |

**Cron-Syntax:** `"0 0 6 * * *"` = Sekunde Minute Stunde Tag Monat Wochentag
- `0 0 6 * * *` = jeden Tag um 06:00:00
- `0 0 7 * * MON` = jeden Montag um 07:00:00

Die Jobs laufen im Hintergrund — sie blockieren nicht den HTTP-Server.

---

## 13. dev start / stop / restart / rebuild — was passiert wirklich?

### dev start

```
dev.bat
  └── powershell -ExecutionPolicy Bypass -File dev.ps1 start
        │
        ├── 1. Prüft ob Docker läuft (docker info)
        │
        ├── 2. docker compose up -d --build
        │      → Baut das Backend-Image aus dem Dockerfile (falls nötig)
        │      → Startet postgres-Container
        │      → Wartet bis postgres "healthy" ist (depends_on condition)
        │      → Startet backend-Container
        │
        └── 3. HTTP-Poll alle 2s auf http://localhost:8080/api/health
               Gibt { "status": "UP" } zurück → bereit
               Nach 90 Versuchen (3 Min.) → Timeout-Meldung
```

**Erster Start:** Docker lädt das Maven-Base-Image und alle Java-Dependencies
herunter (~200 MB) — läuft alles **innerhalb des Containers**, nichts lokal.
Folgestarts nutzen den Docker Layer-Cache und sind viel schneller.

### dev stop

```
dev.ps1 stop
  └── docker compose down   → backend + postgres Container stoppen
```

`--keep-db` → nur `docker compose stop backend` → postgres bleibt laufen.
Beim nächsten `dev start` entfällt die DB-Startzeit.

### dev restart

```
dev.ps1 restart
  = docker compose stop backend + docker compose up -d --build backend
```

**Wann reicht restart?**
- Java-Code geändert → Docker baut das Image neu (Source-Layer neu, Dependencies gecacht)
- `application.properties` geändert → kein Problem mehr, alles läuft im Container

**Wann rebuild statt restart?**
- `Dockerfile` selbst geändert
- Komische Build-Fehler die sich nicht erklären lassen (Cache-Probleme)

### dev rebuild

```
dev.ps1 rebuild
  ├── (ohne --keep-db):
  │     docker compose down
  │     docker volume rm <postgres_data>   → Datenbank-Volume gelöscht (frische DB!)
  │     docker compose up -d --build --force-recreate
  │
  └── (mit --keep-db):
        docker compose up -d --build --force-recreate backend
        → PostgreSQL-Volume bleibt erhalten, Daten bleiben erhalten
```

**Wichtig:** `rebuild` ohne `--keep-db` löscht das PostgreSQL-Volume — alle Daten
gehen verloren. Testdaten danach erneut einspielen:
```powershell
Get-Content src/main/resources/db/dev-seed.sql | docker exec -i webshop-postgres psql -U webshop -d webshop
```

**Wann rebuild nötig:**
- `Dockerfile` geändert
- `pom.xml` geändert und der Layer-Cache hat einen alten Stand
- Build-Fehler die sich nicht durch `restart` beheben lassen
- Saubere Datenbank für Tests gewünscht

---

## 14. target/ — warum gibt es diesen Ordner?

`target/` ist der **Build-Output-Ordner** von Maven — er enthält kompilierten
Java-Bytecode und das fertige JAR.

**Lokal (dein Rechner):** `target/` existiert bei dir nur wenn du Maven manuell
aufgerufen hast oder die IDE das Projekt baut. Für den normalen `dev start` ist er
**nicht nötig** — Maven läuft im Docker-Container und `target/` liegt dort drin,
nicht auf deinem Rechner.

```
Innerhalb des Build-Containers (Docker Stage 1):
target/
├── classes/
│   ├── de/fhdw/webshop/          ← kompilierte .class Dateien (Java Bytecode)
│   ├── application.properties    ← KOPIE aus src/main/resources/
│   └── db/migration/             ← KOPIE der SQL-Migrations
└── webshop-0.0.1-SNAPSHOT.jar    ← fertiges Deployment-Paket
```

Das JAR wird dann in Stage 2 des Dockerfiles in das Runtime-Image kopiert.
Auf deinem Rechner siehst du davon nichts.

**Warum ist target/ in .gitignore?**
- Kann jederzeit durch Maven neu erstellt werden
- Enthält kompilierten Bytecode der betriebssystemspezifisch sein kann
- Kann sehr groß werden (JARs, Testberichte)

**Wann existiert target/ doch lokal?**
Wenn die IDE (IntelliJ / VS Code mit Java Extension) das Projekt baut — für
Code-Navigation, Autovervollständigung und lokale Tests. Für `dev start` spielt das
keine Rolle.

---

## 15. GitHub Actions — CI/CD erklärt

### Was ist CI/CD?

**CI** (Continuous Integration): Bei jedem Code-Push werden automatisch Tests ausgeführt.
Fehler werden sofort erkannt, bevor sie in `main` landen.

**CD** (Continuous Deployment): Bei jedem Merge in `main` wird die Anwendung automatisch
auf den Server deployed. Kein manuelles Hochladen.

### ci.yml — Pull Request Pipeline

```yaml
on:
  pull_request:
    branches: [main]      ← läuft wenn ein PR gegen main geöffnet wird
```

```
GitHub Runner (Ubuntu VM)
  ├── Checkout: Code vom Branch herunterladen
  ├── Java 21 Setup: JDK installieren
  ├── chmod +x mvnw: Script ausführbar machen
  └── ./mvnw test
        ├── Kompiliert den Code
        └── Führt alle Tests aus (src/test/)
              Fehler → GitHub markiert den PR als "failed"
              Erfolg → PR kann gemergt werden
```

### cd.yml — Deploy Pipeline

```yaml
on:
  push:
    branches: [main]      ← läuft nach jedem Merge in main
```

```
GitHub Runner
  ├── Checkout
  ├── Java 21 Setup
  ├── ./mvnw package -DskipTests
  │     → erstellt target/webshop-0.0.1-SNAPSHOT.jar
  │
  ├── scp-action: JAR per SSH auf VPS kopieren
  │     Ziel: /opt/webshop/webshop-0.0.1-SNAPSHOT.jar
  │
  └── ssh-action: Befehl auf VPS ausführen
        ssh deploy@vps → sudo systemctl restart webshop
```

### Systemd auf dem VPS

`systemctl restart webshop` startet den Systemd-Service neu. Der Service ist so
konfiguriert:
```ini
[Service]
ExecStart=/usr/bin/java -jar /opt/webshop/webshop-0.0.1-SNAPSHOT.jar
EnvironmentFile=/opt/webshop/.env   ← Secrets auf dem Server
Restart=on-failure                  ← automatischer Neustart bei Absturz
```

Das neue JAR liegt dann schon bereit — der Service startet neu und lädt das neue JAR.

### GitHub Secrets

Passwörter und SSH-Keys dürfen nicht im Code stehen. Sie werden als
"Secrets" in GitHub hinterlegt (Settings → Secrets and variables → Actions)
und in der Pipeline als `${{ secrets.VPS_HOST }}` verwendet.

| Secret | Wo benutzt |
|---|---|
| `VPS_HOST` | SSH-Zieladresse |
| `VPS_USER` | SSH-Benutzername |
| `VPS_SSH_KEY` | Privater SSH-Key (niemals teilen!) |

---

## 16. Monitoring & Alerting — Architektur und Implementierung

### Netzwerk-Architektur

```
Internet
   │
   ▼ Port 8080 (öffentlich)
backend:8080  — REST API (normaler Traffic)
backend:8081  — Actuator / Prometheus-Endpunkt (NICHT öffentlich)
       │ Docker-internes Netzwerk
       ▼
prometheus:9090  (NICHT öffentlich — kein Ports-Mapping in docker-compose.yml)
       │ Docker-internes Netzwerk
       ▼
grafana:3000  →  Host 127.0.0.1:3001  (nur localhost, kein öffentlicher Zugriff)
```

**Warum separater Management-Port?**
Port 8081 ist in `docker-compose.yml` bewusst nicht in `ports:` eingetragen.
Nur Container im selben Docker-Netzwerk können `backend:8081` erreichen.
Von außerhalb des Containers gilt: Connection refused.

### Spring Boot Actuator + Micrometer

`pom.xml` fügt zwei Dependencies hinzu:
```xml
<dependency>spring-boot-starter-actuator</dependency>      <!-- Actuator-Endpunkte -->
<dependency>micrometer-registry-prometheus</dependency>    <!-- Prometheus-Format -->
```

`application.properties`:
```properties
management.server.port=8081
management.endpoints.web.exposure.include=health,info,prometheus
management.endpoint.prometheus.access=unrestricted
```

Micrometer instrumentiert automatisch — ohne Eingriff in Feature-Code:
- `http.server.requests` — HTTP-Request-Counts, Latenz, Status-Codes
- `jvm.memory.used/max` — Heap und Non-Heap
- `jvm.gc.pause` — GC-Pausen
- `jvm.threads.live` — aktive Threads
- `hikaricp.connections.*` — DB-Connection-Pool

### Prometheus-Konfiguration (`monitoring/prometheus.yml`)

```yaml
scrape_configs:
  - job_name: "webshop-backend"
    metrics_path: "/actuator/prometheus"
    static_configs:
      - targets: ["backend:8081"]   ← interner Docker-Hostname
```

Prometheus fragt alle 15 Sekunden `backend:8081/actuator/prometheus` ab und speichert die Zeitreihendaten in seinem lokalen Volume (`prometheus_data`).

### Grafana Provisioning

Grafana liest beim Start automatisch aus `monitoring/grafana/provisioning/`:

```
datasources/prometheus.yml   → Verbindet Grafana mit prometheus:9090 (intern)
dashboards/dashboards.yml    → Sagt Grafana, wo die JSON-Dashboards liegen
dashboards/jvm-overview.json         → JVM-Dashboard (Heap, GC, Threads)
dashboards/http-requests.json        → HTTP-Dashboard (Rate, Fehler, Latenz)
dashboards/spring-boot-overview.json → Übersichts-Dashboard (Status, Uptime, Pool)
```

Kein manuelles Klicken in der Grafana-UI nötig — Dashboards sind sofort beim ersten Start verfügbar.

### Alert-System (`NotificationScheduler` + `BusinessEmailService`)

Zwei `@Scheduled`-Methoden in `NotificationScheduler` lesen Metriken direkt via `MeterRegistry`:

**`checkHighErrorRate()`** — läuft alle 15 Minuten:
```java
Counter errorCounter = meterRegistry.find("http.server.requests")
    .tag("outcome", "SERVER_ERROR").counter();
double errorsInInterval = currentCount - lastSnapshot;
if (errorsInInterval >= errorRateThreshold)
    businessEmailService.sendAlert(AlertEventType.HIGH_ERROR_RATE, subject, body);
```
Differenzbildung nötig, weil Micrometer kumulative Counter führt.

**`checkJvmHeapUsage()`** — läuft alle 30 Minuten:
```java
double usedPercent = 100.0 * usedBytes / maxBytes;
if (usedPercent >= heapUsageThresholdPercent)
    businessEmailService.sendAlert(AlertEventType.HIGH_HEAP_USAGE, subject, body);
```

**`BusinessEmailService.sendAlert(eventType, subject, body)`:**
1. Liest `AlertEventConfig` für den übergebenen `eventType` aus der Datenbank
2. Wenn deaktiviert → nur `log.debug()`, kein Versand
3. Wenn `recipientStrategy = ALL_DEFAULT` → sendet an alle `KnownEmailAddress` mit `isDefault=true`
4. Wenn `recipientStrategy = SPECIFIC` → sendet an die direkt konfigurierten Empfänger
5. Keine Konfiguration → Fallback auf `ALERT_ADMIN_EMAIL` aus `.env`

**E-Mail-Umleitung (Uni-Projekt):**
`EmailService.sendEmail()` leitet unabhängig vom Aufrufkontext alle E-Mails an
`isDefault=true`-Adressen um. Der ursprüngliche Empfänger erscheint im Body-Header:
```
================================================================
[Universitätsprojekt – E-Mail-Umleitung aktiv]
Ursprünglicher Empfänger: kunde@example.com
================================================================

[eigentlicher E-Mail-Inhalt]
```
Sind keine Default-Adressen konfiguriert, geht die E-Mail an `APP_MAIL_FROM` (Selbstversand).
So können alle Email-Features sicher getestet werden ohne echte Adressen zu kontaktieren.

### Konfigurierbare Schwellenwerte

| Property | Env-Variable | Standard | Bedeutung |
|---|---|---|---|
| `app.mail.from` | `APP_MAIL_FROM` | `noreply@webshop.local` | Absenderadresse + Fallback wenn keine Default-Empfänger |
| `alert.admin-email` | `ALERT_ADMIN_EMAIL` | — | Kommagetrennte Fallback-Empfänger (wenn keine DB-Konfiguration) |
| `alert.error-rate.threshold` | `ALERT_ERROR_RATE_THRESHOLD` | 10 | 5xx-Fehler pro 15 min |
| `alert.heap-usage.threshold-percent` | `ALERT_HEAP_USAGE_THRESHOLD_PERCENT` | 85 | JVM Heap % |

Alle Werte können via `.env`-Datei oder Docker-Compose `environment:` überschrieben werden.
Empfänger-Konfiguration per Admin-UI hat Vorrang vor `.env`-Variablen.

### Admin-UI für Alerting

Die React-Seite `/admin/alerting` nutzt die Endpunkte unter `/api/admin/alerting/`:
- **E-Mail-Adressen verwalten** — Adressen mit `isDefault=true` als globale Redirect-Ziele setzen
- **Alert-Events konfigurieren** — pro Typ aktivieren/deaktivieren, Empfängerstrategie wählen
- **Test-Alert senden** — prüft ob E-Mail-Versand grundsätzlich funktioniert
