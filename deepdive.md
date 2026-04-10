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
├── mvnw                       ← Maven Wrapper Script (Linux/Mac)
├── mvnw.cmd                   ← Maven Wrapper Script (Windows CMD)
├── docker-compose.yml         ← PostgreSQL Container Definition
├── .env.example               ← Vorlage für Umgebungsvariablen
├── .env                       ← Deine lokalen Secrets (nie committen!)
├── .gitignore                 ← Was Git ignorieren soll
├── dev.bat                    ← Thin-Wrapper: ruft dev.ps1 auf
├── dev.ps1                    ← Das eigentliche Start/Stop/Rebuild Script
├── README.md                  ← Einstiegsdokumentation
├── deepdive.md                ← dieses Dokument
├── .mvn/wrapper/
│   ├── maven-wrapper.jar      ← Maven Wrapper Binary
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
Maven Wrapper Scripts. Statt `mvn` (globales Maven) rufst du `./mvnw` auf.
Beim ersten Aufruf lädt das Script Maven automatisch herunter nach `~/.m2/wrapper/`.
Dadurch braucht kein Teammitglied Maven manuell zu installieren.

- `mvnw` = für Linux/Mac (Bash-Script)
- `mvnw.cmd` = für Windows CMD

### .mvn/wrapper/maven-wrapper.properties
```properties
distributionUrl=https://repo.maven.apache.org/maven2/org/.../apache-maven-3.9.9-bin.zip
```
Sagt dem Wrapper: "Lade Maven 3.9.9 herunter wenn es noch nicht da ist."

### docker-compose.yml
Beschreibt einen PostgreSQL-Container:
```yaml
services:
  postgres:
    image: postgres:16-alpine    # PostgreSQL Version 16, minimal
    container_name: webshop-postgres
    environment:
      POSTGRES_DB: webshop       # Datenbankname
      POSTGRES_USER: webshop     # Benutzername
      POSTGRES_PASSWORD: localdev
    ports:
      - "5432:5432"              # Host:Container Port-Mapping
    healthcheck:                 # wann ist der Container "bereit"?
      test: ["CMD-SHELL", "pg_isready -U webshop"]
```
`dev start` führt `docker compose up -d` aus und wartet bis der `healthcheck` grün ist.

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
Das eigentliche Steuerungsskript. Vier Befehle:

| Befehl | Was passiert |
|---|---|
| `dev start` | Docker starten → warten bis healthy → Spring Boot starten → warten bis "Started" |
| `dev stop` | Spring Boot-Prozess killen → Docker stoppen |
| `dev restart` | stop + start |
| `dev rebuild` | stop + `target/` löschen + start (erzwingt Neukompilierung) |

---

## 3. Maven — wie wird das Projekt gebaut?

Maven ist ein Build-Tool. Es übersetzt Java-Quellcode in ausführbaren Bytecode und
verwaltet alle Libraries (Abhängigkeiten).

### Was Maven macht wenn du `dev start` ausführst

```
./mvnw spring-boot:run
        │
        ├── 1. Lädt fehlende Abhängigkeiten aus dem Internet nach ~/.m2/repository/
        │
        ├── 2. Kompiliert src/main/java/**/*.java → target/classes/**/*.class
        │
        ├── 3. Kopiert src/main/resources/** → target/classes/
        │         (application.properties, SQL-Migrations, dev-seed.sql)
        │
        └── 4. Startet die Anwendung mit den Klassen aus target/classes/
```

### Das ~/.m2 Verzeichnis
Maven speichert alle heruntergeladenen Libraries in `~/.m2/repository/` (dein
Heimverzeichnis). Das ist ein lokaler Cache — beim zweiten Start muss nichts mehr
heruntergeladen werden.

### Warum "Nothing to compile"?
Maven prüft Timestamps. Wenn `target/classes/Foo.class` neuer ist als `src/.../Foo.java`,
überspringt es die Datei. Das ist schnell — aber ein Problem wenn du z.B. nur
`application.properties` änderst: Maven sieht keine `.java`-Änderung und kompiliert nicht neu.
**Lösung: `dev rebuild`** — löscht `target/` und erzwingt eine vollständige Neukompilierung.

---

## 4. Docker — wie läuft die Datenbank?

Docker ist eine Container-Laufzeitumgebung. Ein Container ist ein isolierter Prozess
mit eigenem Dateisystem — wie eine sehr leichtgewichtige virtuelle Maschine.

```
dein Rechner
└── Docker Engine
    └── Container: webshop-postgres
        ├── PostgreSQL 16 Prozess
        ├── Port 5432 (gemapped auf Host-Port 5432)
        └── Volume: postgres_data (persistente Daten)
```

Das Volume `postgres_data` sorgt dafür dass die Datenbank-Daten erhalten bleiben
wenn der Container neu gestartet wird. Ohne Volume würde alles bei jedem Start
gelöscht werden.

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
└── V9__create_audit_log.sql
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
├── notification/    ← E-Mail und automatische Benachrichtigungen
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
Versendet E-Mails über JavaMailSender (konfiguriert in `application.properties`
mit MAIL_HOST/PORT). Lokal läuft kein echter Mail-Server — man kann
[MailHog](https://github.com/mailhog/MailHog) als lokalen Mail-Catcher verwenden.

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
        ├── 1. Prüft ob Port 8080 schon belegt (Get-NetTCPConnection)
        │
        ├── 2. docker compose up -d
        │      → PostgreSQL Container starten (falls nicht schon läuft)
        │
        ├── 3. Wartet bis docker inspect ... = "healthy"
        │      (pg_isready läuft alle 10s im Container)
        │
        ├── 4. Start-Process java ...
        │      Argumente:
        │      -Dmaven.multiModuleProjectDirectory="..."
        │      -classpath ".mvn/wrapper/maven-wrapper.jar"
        │      org.apache.maven.wrapper.MavenWrapperMain
        │      spring-boot:run
        │
        │      → Maven Wrapper startet Maven
        │      → Maven kompiliert (falls nötig)
        │      → Maven startet Spring Boot
        │
        │      Ausgabe geht nach:
        │      target/dev.log     (stdout)
        │      target/dev-err.log (stderr)
        │
        └── 5. Wartet (polling alle 2s) bis "Started WebshopApplication" in Logs
```

### dev stop

```
dev.ps1 stop
  ├── Get-NetTCPConnection -LocalPort 8080 → PID des Java-Prozesses
  ├── Stop-Process -Id $pid -Force         → Java-Prozess killen
  └── docker compose down                  → PostgreSQL Container stoppen
```

`--keep-db` überspringt den `docker compose down` Schritt → DB bleibt laufen.
Das spart beim nächsten `dev start` die Wartezeit bis die DB healthy ist.

### dev restart

```
dev.ps1 restart
  = dev stop + 1 Sekunde warten + dev start
```

**Wann reicht restart?**
- Java-Code geändert → Maven erkennt geänderte `.java`-Dateien, kompiliert neu
- Neue Dependency in `pom.xml` → Maven lädt sie beim nächsten Start

**Wann reicht restart NICHT?**
- `application.properties` geändert → Maven sieht keine `.java`-Änderung, nimmt alten Cache
- Andere Ressourcen-Dateien geändert → gleiches Problem

### dev rebuild

```
dev.ps1 rebuild
  ├── dev stop
  │
  ├── Remove-Item -Recurse -Force target/
  │     → löscht alle kompilierten Klassen
  │     → löscht die gecachten Ressourcen (application.properties etc.)
  │     → löscht dev.log und dev-err.log
  │
  └── dev start
        → Maven findet kein target/ → kompiliert ALLES neu
        → kopiert application.properties frisch aus src/ nach target/classes/
```

**Wann rebuild nötig:**
- `application.properties` geändert (z.B. Swagger UI aktiviert)
- Build-Fehler die sich nicht erklären lassen
- Nach Merge von Branches mit vielen Änderungen
- `pom.xml` geändert (neue Library, Version geändert)

---

## 14. target/ — warum gibt es diesen Ordner?

`target/` ist der **Build-Output-Ordner** — er wird von Maven erstellt und verwaltet.

```
target/
├── classes/
│   ├── de/fhdw/webshop/          ← kompilierte .class Dateien (Java Bytecode)
│   ├── application.properties    ← KOPIE aus src/main/resources/
│   └── db/migration/             ← KOPIE der SQL-Migrations
├── webshop-0.0.1-SNAPSHOT.jar    ← fertiges Deployment-Paket (mvn package)
├── dev.log                       ← Spring Boot Ausgabe
└── dev-err.log                   ← Fehlerausgabe
```

**Warum ist target/ in .gitignore?**
- Kann jederzeit durch `mvn compile` neu erstellt werden
- Enthält kompilierten Bytecode der betriebssystemspezifisch sein kann
- Kann sehr groß werden (JARs, Testberichte)
- Enthält Logs die nicht ins Repository gehören

**Warum sehen src/main/resources/ und target/classes/ gleich aus?**
Maven kopiert beim Build alles aus `src/main/resources/` nach `target/classes/`.
Spring Boot liest zur Laufzeit aus `target/classes/` (dem Classpath).
`src/` ist die Quelle, `target/` ist das Ergebnis.

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
