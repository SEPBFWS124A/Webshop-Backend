# Milestone: Monitoring & Alerting

**Issues (Backend):** #20, #21, #22, #23, #24, #25 (Epic)

---

## Ziel

Laufende Beobachtung von Systemmetriken (JVM, HTTP, DB-Pool) über einen abgesicherten internen Kanal, Visualisierung in Grafana und automatische E-Mail-Alerts bei kritischen Schwellenwerten.

---

## Beteiligte Rollen

| Rolle | Relevanz |
|---|---|
| `ADMIN` | Zugriff auf Admin-Monitoring-Seite im Frontend |
| System (intern) | Prometheus scrapet Metriken, NotificationScheduler sendet Alerts |

---

## Sicherheitskonzept

Port 8081 (Actuator/Prometheus) ist in `docker-compose.yml` bewusst **nicht** in `ports:` gemappt.
Prometheus kommuniziert ausschließlich über das interne Docker-Netzwerk.

```
Internet → backend:8080 (API, öffentlich)
            backend:8081 (Actuator, NICHT öffentlich)
                │ intern
                ▼
         prometheus:9090 (NICHT öffentlich)
                │ intern
                ▼
          grafana:3000 → Host 127.0.0.1:3001
```

---

## Neue Dateien

| Datei | Zweck |
|---|---|
| `monitoring/prometheus.yml` | Scrape-Konfiguration: `backend:8081/actuator/prometheus`, alle 15s |
| `monitoring/grafana/provisioning/datasources/prometheus.yml` | Grafana-Datasource: `prometheus:9090` |
| `monitoring/grafana/provisioning/dashboards/dashboards.yml` | Dashboard-Provider-Konfiguration |
| `monitoring/grafana/provisioning/dashboards/jvm-overview.json` | Heap, GC, Threads, Loaded Classes |
| `monitoring/grafana/provisioning/dashboards/http-requests.json` | Request-Rate, Fehlerquote, Latenz p50/p95/p99 |
| `monitoring/grafana/provisioning/dashboards/spring-boot-overview.json` | Status, Uptime, DB-Pool, 5xx (15 min) |
| `src/main/resources/META-INF/additional-spring-configuration-metadata.json` | IDE-Metadaten für `alert.*`-Properties |

---

## Geänderte Dateien

| Datei | Änderung |
|---|---|
| `pom.xml` | `spring-boot-starter-actuator` + `micrometer-registry-prometheus` |
| `application.properties` | `management.server.port=8081`, Actuator-Exposition, Alert-Schwellenwerte |
| `docker-compose.yml` | `prometheus` + `grafana` Services, Volumes; Port 8081 nicht gemappt |
| `notification/NotificationScheduler.java` | `checkHighErrorRate()` + `checkJvmHeapUsage()` via `MeterRegistry` |
| `notification/EmailService.java` | `sendAdminAlert(subject, body)` mit konfigurierbarer Admin-E-Mail |
| `dev.ps1` + `dev.sh` | Grafana-Hinweis in `Show-SeedHint` |

---

## Alert-Methoden

### `checkHighErrorRate()` — alle 15 Minuten

Liest den kumulativen `http.server.requests`-Counter (Tag `outcome=SERVER_ERROR`) aus der `MeterRegistry`.
Bildet die Differenz zum letzten Intervall-Snapshot.
Bei Überschreitung von `alert.error-rate.threshold` (Standard: 5) → `EmailService.sendAdminAlert()`.

### `checkJvmHeapUsage()` — alle 30 Minuten

Liest `jvm.memory.used` und `jvm.memory.max` (Tag `area=heap`).
Bei Überschreitung von `alert.heap-usage.threshold-percent` (Standard: 80 %) → `EmailService.sendAdminAlert()`.

---

## Konfiguration

```properties
# application.properties / Umgebungsvariablen
management.server.port=8081
# Kommagetrennte Empfängerliste. Standard: MAIL_USERNAME (self-send).
alert.admin-email=${ALERT_ADMIN_EMAIL:${spring.mail.username:}}
alert.error-rate.threshold=${ALERT_ERROR_RATE_THRESHOLD:5}
alert.heap-usage.threshold-percent=${ALERT_HEAP_USAGE_THRESHOLD_PERCENT:80}
```

---

## Grafana

```
URL:       http://localhost:3001
Login:     admin / admin
Dashboards (auto-provisioned):
  - JVM Overview
  - HTTP Requests
  - Spring Boot Overview
  - Ollama Overview
```

---

## Verifikation

```bash
# Prometheus-Metriken intern abfragen
docker exec webshop-backend curl -s http://backend:8081/actuator/prometheus | head -20

# Grafana Datasource-Status
# http://localhost:3001 → Connections → Data Sources → Prometheus → "Save & Test" → grün

# Alert manuell auslösen (niedrigen Threshold setzen)
# ALERT_ERROR_RATE_THRESHOLD=1 → 2x HTTP 500 erzeugen → 15-min-Job → E-Mail
```
