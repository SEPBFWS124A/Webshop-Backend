# Last-/Ressourcentest (k6 + Grafana/Prometheus)

Dieser Test misst, wie viele Nutzer der Webshop **gleichzeitig** bedienen kann, bevor
Latenz und Fehlerquote einbrechen ("Knie" der Lastkurve). Last wird mit **k6** erzeugt,
beobachtet wird über den vorhandenen **Grafana/Prometheus**-Stack.

- Lastskript: [`../loadtest/load-test.js`](../loadtest/load-test.js)
- Lastdaten-Seed: [`../src/main/resources/db/loadtest-seed.sql`](../src/main/resources/db/loadtest-seed.sql)

---

## 1. Vorbereitung

### 1.1 Stack frisch starten
```bat
./dev.bat rebuild
```
`rebuild` löscht das PostgreSQL-Volume → frische DB. Anschließend laufen Backend,
PostgreSQL, Prometheus und Grafana.

### 1.2 Lastdaten einspielen
Eine realistische Datenmenge (Standard: 5.000 Produkte, 1.000 Kunden) einspielen:
```bat
Get-Content src\main\resources\db\loadtest-seed.sql | docker exec -i webshop-postgres psql -U webshop -d webshop
```
```bash
docker exec -i webshop-postgres psql -U webshop -d webshop < src/main/resources/db/loadtest-seed.sql
```
Die Mengen lassen sich oben in der Seed-Datei über `\set product_count` / `\set customer_count` anpassen.

Die generierten Kunden heißen `loaduser1` … `loaduser1000`, Passwort überall `Password1!`.

### 1.3 Grafana öffnen
```
http://localhost:3001   (admin / admin)
```
Dashboards: **HTTP Requests** (Rate, Fehlerquote, Latenz-Perzentile), **JVM Overview**
(Heap, GC, Threads), **Spring Boot Overview** (Status, DB-Connection-Pool).
Prometheus scrapt `backend:8081` Docker-intern und ist von außen nicht erreichbar.

---

## 2. Lasttest ausführen

> **Schnellweg (ein Befehl):** `./dev.bat loadtest` (bzw. `./dev.sh loadtest`) führt interaktiv
> durch den ganzen Ablauf:
> 1. Sicherheitsabfrage (die DB wird gelöscht) → bestätigen.
> 2. Stack frisch hochfahren (inkl. Mailpit-Override, siehe Abschnitt 5) + Seed einspielen.
> 3. Abfrage **„Lasttest jetzt starten? [j]a / [n]ein / [a] ja inkl. Bestellungen"**:
>    - `j` → read-dominanter Lasttest (`load-test.js`)
>    - `a` → derselbe Test **inkl. echter Bestellungen** (`PLACE_ORDERS`, max. Auslastung)
>    - `n` → überspringen
> 4. Lief nur der read-Test, folgt die Abfrage, ob noch ein **reines Bestell-Szenario**
>    (`order-load-test.js`) laufen soll.
>
> Die k6-Ausgabe (Live-Fortschritt + Abschlussbericht) erscheint direkt in der Konsole. Der Stack
> bleibt danach laufen, damit du Grafana (`:3001`) und Mailpit (`:8025`) ansehen kannst. Die
> folgenden Schritte beschreiben denselben Ablauf manuell / mit mehr Kontrolle.

k6 läuft containerisiert und erreicht das auf dem Host veröffentlichte Backend über
`host.docker.internal:8080`:

```bash
docker run --rm \
  -e BASE_URL=http://host.docker.internal:8080 \
  -e LOGIN_USER_PREFIX=loaduser -e LOGIN_USER_COUNT=1000 \
  -v "${PWD}/loadtest:/scripts" \
  grafana/k6 run /scripts/load-test.js
```

PowerShell-Variante (Pfad-Mount mit absolutem Pfad):
```powershell
docker run --rm `
  -e BASE_URL=http://host.docker.internal:8080 `
  -e LOGIN_USER_PREFIX=loaduser -e LOGIN_USER_COUNT=1000 `
  -v "${PWD}\loadtest:/scripts" `
  grafana/k6 run /scripts/load-test.js
```

### Parameter (Environment-Variablen)

| Variable | Default | Bedeutung |
|---|---|---|
| `BASE_URL` | `http://host.docker.internal:8080` | Backend-URL |
| `LOGIN_PASSWORD` | `Password1!` | Passwort der Test-Nutzer |
| `LOGIN_USER_PREFIX` | `alice` | Login-Benutzer(-Präfix) |
| `LOGIN_USER_COUNT` | `0` | Anzahl Nutzer `<Präfix>1..N` (0 = Präfix unverändert nutzen) |
| `AUTH_SHARE` | `0.3` | Anteil der Iterationen mit Login/Warenkorb/Bestellhistorie |

### Lastprofil & Schwellenwerte

Das Skript rampt virtuelle Nutzer (VUs) stufenweise hoch: 20 → 50 → 100 → 200, dann
herunter. Pro Iteration: Katalog laden + Produktdetail (öffentlich) und mit
`AUTH_SHARE`-Wahrscheinlichkeit eine authentifizierte Session (Login → Warenkorb →
Artikel hinzufügen → Bestellhistorie).

Definierte Schwellen (k6 markiert den Lauf als fehlgeschlagen, wenn überschritten):

| Schwelle | Bedeutung |
|---|---|
| `http_req_failed < 1 %` | Anteil fehlgeschlagener HTTP-Requests |
| `http_req_duration p95 < 800 ms` | 95. Perzentil der Antwortzeit |
| `business_errors < 1 %` | fachliche Checks (Status/Token korrekt) |

---

## 3. Auswertung

Während des Laufs im **HTTP Requests**-Dashboard beobachten, ab welcher VU-Stufe:
- die p95/p99-Latenz deutlich ansteigt,
- die 5xx-Fehlerquote über 0 steigt,
- der HikariCP-Connection-Pool (Spring Boot Overview) ausgelastet ist,
- der JVM-Heap (JVM Overview) an seine Grenze stößt.

Der k6-Abschlussbericht liefert pro Endpunkt `http_req_duration` (avg/p90/p95/p99) und
`http_req_failed`. Die niedrigste VU-Stufe, bei der eine Schwelle reißt, ist die
**Belastungsgrenze**.

---

## 4. Optional: echte Bestellungen unter Last

Der Standard-Lasttest ([`load-test.js`](../loadtest/load-test.js)) ist bewusst
**read-dominant** (Katalog/Detail öffentlich, ein Teil authentifiziert mit Warenkorb +
Bestell**historie**) und ruft **kein** `POST /api/orders` auf. Gründe:

1. **Stock-Abbau:** Jede Bestellung reduziert den Lagerbestand. Bei vielen VUs ist der Bestand
   schnell 0 → Folgebestellungen schlagen mit Validierungsfehlern fehl → die Schwelle
   `http_req_failed < 1 %` würde durch **erwartete fachliche** Fehler gerissen, nicht durch echte
   Performance-Probleme. Das verfälscht das Messsignal.
2. **Datenexplosion + Seiteneffekte:** Tausende Bestellungen blähen die DB auf, und pro Bestellung
   wird ein Mailversand getriggert (siehe Abschnitt 5).
3. **Komplexer Request-Body:** `POST /api/orders` braucht einen vollständigen Body (Lieferadresse,
   Zahlungsart, AGB-/Datenschutz-Flags) — sonst 400.
4. **Freigabe-Flow:** Unternehmenskunden über Budget landen in `Pending_Approval` — zusätzliche
   Verzweigung.

Für **echte** Bestell-Last gibt es zwei Wege — beide nutzen den von `loadtest-seed.sql` angelegten
**Hochlager-Artikel** „Load Test Bestell Artikel" (Stock praktisch unbegrenzt, damit nichts ausgeht)
und sind dank Mailpit (Abschnitt 5) versand-sicher:

1. **Gemischt / max. Auslastung** — beim `dev loadtest` die Option `a` („ja inkl. Bestellungen")
   wählen, oder den Haupttest direkt mit `PLACE_ORDERS=true` starten. Dann platziert jede
   authentifizierte Session zusätzlich eine echte Bestellung gegen den Hochlager-Artikel.
   ```bash
   docker run --rm \
     -e BASE_URL=http://host.docker.internal:8080 \
     -e LOGIN_USER_PREFIX=loaduser -e LOGIN_USER_COUNT=1000 \
     -e PLACE_ORDERS=true \
     -v "${PWD}/loadtest:/scripts" grafana/k6 run /scripts/load-test.js
   ```
2. **Fokussiert / nur Bestellungen** — das separate Szenario
   ([`order-load-test.js`](../loadtest/order-load-test.js)) mit weniger VUs (sauberes Signal nur
   für den Checkout-Pfad). Es loggt sich als `loaduser` ein, legt den Artikel in den Warenkorb und
   platziert eine echte Bestellung.

```bash
docker run --rm \
  -e BASE_URL=http://host.docker.internal:8080 \
  -e LOGIN_USER_PREFIX=loaduser -e LOGIN_USER_COUNT=1000 \
  -v "${PWD}/loadtest:/scripts" \
  grafana/k6 run /scripts/order-load-test.js
```

---

## 5. E-Mails abfangen (Mailpit)

Bei echten Bestellungen versendet das Backend Bestätigungs-E-Mails. Damit beim Lasttest
**keine echten** Mails rausgehen — auch nicht, wenn in der `.env` echte SMTP-Daten stehen —
bindet `./dev.bat loadtest` automatisch das Override [`../docker-compose.mailpit.yml`](../docker-compose.mailpit.yml)
ein. Darin überschreiben **literale** `SPRING_MAIL_*`-Werte die `${MAIL_*}`-Interpolation der
Basis-`docker-compose.yml` (die zuletzt angegebene Compose-Datei gewinnt). Der gesamte
Mailverkehr landet so im lokalen **Mailpit**-Container statt bei einem echten Server.

- Mailpit-Web-UI (abgefangene Mails ansehen): **http://localhost:8025**
- SMTP läuft Docker-intern auf `mailpit:1025` (nicht nach außen exponiert).
- Unabhängig davon fängt der `EmailService` Sendefehler ohnehin ab (loggt nur, kein Crash) —
  ohne Mailpit liefe die Last also auch durch, nur ohne dass man die Mails sehen könnte.

> Hinweis: Der Standard-Lasttest (`load-test.js`) platziert keine Bestellungen, erzeugt also
> kaum Mails. Sobald du gegen den laufenden Stack das **Bestell-Szenario** (Abschnitt 4) startest,
> werden dessen Bestätigungs-Mails in Mailpit sichtbar.

**Manuelle Nutzung** des Overrides (ohne `dev loadtest`):
```bash
docker compose -f docker-compose.yml -f docker-compose.mailpit.yml up -d --build
```

---

## 6. Ergebnisse

**Lauf:** read-dominanter Test (`load-test.js`), Profil 20 → 50 → 100 → 200 VUs.
**Datenmenge:** 5.000 Produkte, 1.000 Kunden (loadtest-seed).
**Umgebung:** Docker Desktop (Windows), **kompletter Stack + k6-Lastgenerator auf derselben Maschine**
— die Zahlen sind dadurch indikativ (CPU-Konkurrenz), nicht repräsentativ für einen dedizierten Server.

### Aggregat über den gesamten Lauf (k6)

| Metrik | Wert |
|---|---|
| Requests gesamt / Rate | 1.046 / ~3,9 req/s |
| Antwortzeit Median | 20,7 s |
| Antwortzeit p90 / p95 | 36,6 s / 37,4 s |
| Antwortzeit max | 59,7 s |
| Fehlerquote (HTTP) | **17,0 %** (178 / 1.046) |
| Iterationen (abgeschlossen) | 364 |
| Daten empfangen | **~1,1 GB** |
| max. gleichzeitige VUs | 200 |

### Erfolgsquote je Endpunkt (k6-Checks)

| Endpunkt | Status 200/2xx |
|---|---|
| `GET /api/products` (Katalog) | 76 % |
| `GET /api/products/{id}` | 88 % |
| `POST /api/auth/login` | 79 % |
| `GET /api/cart` | 92 % |
| `POST /api/cart/items` | 90 % |
| `GET /api/orders` | 92 % |

### Verdikt

Alle drei Schwellen (`http_req_failed<1 %`, `http_req_duration p95<800 ms`, `business_errors<1 %`)
wurden **deutlich gerissen**. Das System sättigt bei diesem Profil **weit unterhalb von 200 VUs** —
die Latenz steigt in den **Sekunden-/Zehnersekunden-Bereich**, statt im Millisekundenbereich zu bleiben.

### Hauptengpass

**Der ungepagte Produktkatalog.** `GET /api/products?purchasable=true` liefert **alle 5.000 Produkte
in einer Antwort** (~1 MB pro Aufruf → ~1,1 GB Gesamt-Transfer bei nur ~1.046 Requests). Unter Last ist
das der dominierende Faktor: Serialisierung großer JSON-Antworten + Bandbreite + GC-Druck.
(In einem Lauf führte das sogar dazu, dass der Backend-Container unter Volllast nicht mehr erreichbar war.)

### Bestell-Szenario (`order-load-test.js`)

Separater Lauf, Profil 10 → 25 → 50 VUs, gegen den Hochlager-Artikel; Mails von Mailpit abgefangen.

| Metrik | Wert |
|---|---|
| Bestellungen platziert | 373 (369 erfolgreich, 4 Fehler) |
| `POST /api/orders` Erfolg | 98 % |
| Fehlerquote (HTTP) | 0,35 % ✓ |
| order_errors | 0,53 % ✓ |
| Antwortzeit Median / p95 / max | 3,2 s / **10,7 s** / 15,8 s |
| Rate | ~5,9 req/s, ~2,0 Bestellungen/s |
| Daten empfangen | ~1,4 MB |
| max. VUs | 50 |

**Befund:** Der Checkout ist **funktional robust** (Bestellungen gehen durch, <1 % Fehler), aber die
**Latenz kippt** schon bei 50 VUs (p95 ~10,7 s, Schwelle 1,5 s gerissen). Anders als beim Katalog ist
das **kein Payload-Problem** (nur ~1,4 MB), sondern die **synchrone Checkout-Verarbeitung**
(DB-Schreibvorgänge, Bestandslogik, synchroner Mailversand im Request-Pfad).

### Empfehlungen

- **Pagination / Limit** für `GET /api/products` (z.B. `page`/`size`), statt den ganzen Katalog auszuliefern.
- **Schlankere Katalog-DTOs** (Listenansicht ohne Volltext-Beschreibung; Details erst über `/{id}`).
- **Checkout entlasten:** Mailversand aus dem Request-Pfad nehmen (asynchron/Queue), damit die
  Bestell-Latenz nicht am synchronen SMTP-Call hängt.
- Optional HTTP-Caching/ETag für den Katalog, Connection-Pool (HikariCP) und JVM-Heap unter Last in Grafana beobachten.
- Für belastbare Zahlen: Last von einer **separaten Maschine** gegen einen dedizierten Backend-Host fahren.

> Detaillierter Zeitverlauf (ab welcher VU-Stufe die Latenz kippt, Heap-/Pool-Auslastung) ist im
> Grafana-Dashboard **HTTP Requests** / **JVM Overview** über das Lauf-Zeitfenster sichtbar.
