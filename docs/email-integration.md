# E-Mail-Integration — Entwicklerdoku

---

## Warum eine Umleitung?

Dieses Projekt ist ein Universitätsprojekt mit Testdaten (z. B. `alice@example.com`).
Damit niemals echte externe Adressen angeschrieben werden, leitet `EmailService` **jede** ausgehende E-Mail automatisch an die konfigurierten Standard-Empfänger um.

Im empfangenen E-Mail-Body steht dann, an wen die Nachricht eigentlich gedacht war:

```
================================================================
[Universitätsprojekt – E-Mail-Umleitung aktiv]
Ursprünglicher Empfänger: alice@example.com
================================================================

Hallo Alice, deine Bestellung #42 wurde aufgegeben...
```

---

## Architektur

```
Dein Feature-Code
      │
      ▼
EmailService.sendEmail(intendedAddress, subject, body)
      │
      ├─ KnownEmailAddressRepository.findAllByIsDefaultTrue()
      │
      ├─ [Default-Empfänger vorhanden] → sendet an alle isDefault=true-Adressen
      │                                   + "Ursprünglicher Empfänger" im Body
      │
      └─ [Keine Default-Empfänger]    → sendet an app.mail.from (die sendende Adresse)
                                        → niemals an die ursprüngliche Adresse
```

**Standard-Empfänger** werden über das Admin-Frontend (`/admin/alerting`) verwaltet
oder direkt per `ALERT_ADMIN_EMAIL` in der `.env` beim ersten Start geseedet.

---

## E-Mails in eigenem Feature versenden

### 1. `EmailService` per Dependency Injection einbinden

```java
@Service
@RequiredArgsConstructor
public class MeinFeatureService {

    private final EmailService emailService;

    public void sendeKundenbenachrichtigung(User customer, String orderNumber) {
        String subject = "Deine Bestellung " + orderNumber + " ist eingegangen";
        String body = "Hallo " + customer.getUsername() + ",\n\n"
                    + "wir haben deine Bestellung erhalten...";

        emailService.sendEmail(customer.getEmail(), subject, body);
    }
}
```

### 2. Verfügbare Methoden

| Methode | Wann verwenden |
|---|---|
| `sendEmail(address, subject, body)` | Allgemeiner Versand an eine beliebige Adresse |
| `sendEmailToCustomer(user, subject, body)` | Shortcut für Kunden-E-Mails (liest `user.getEmail()`) |
| `sendPasswordChangedNotification(user)` | Passwort-Änderungsbenachrichtigung (vorgefertigter Text) |
| `sendAdminAlert(subject, body)` | Monitoring-Alerts (liest `ALERT_ADMIN_EMAIL` aus der `.env`) |

> **Hinweis:** Alle Methoden laufen intern über `sendEmail()` — die Umleitung greift also immer automatisch.

### 3. Für Alert-Events: `BusinessEmailService` verwenden

Wenn dein Feature konfigurierbare Empfänger pro Event-Typ braucht (z. B. "bei Ereignis X sollen Empfänger A und B informiert werden"), nutze `BusinessEmailService` statt `EmailService` direkt:

```java
@Service
@RequiredArgsConstructor
public class MeinAlertService {

    private final BusinessEmailService businessEmailService;

    public void sendeBestandsAlarm(String produktName) {
        businessEmailService.sendAlert(
            AlertEventType.LOW_STOCK,          // dein Event-Typ (in AlertEventType ergänzen)
            "Niedriger Bestand: " + produktName,
            "Der Bestand von " + produktName + " ist unter den Schwellenwert gefallen."
        );
    }
}
```

Empfänger für den Event-Typ werden im Admin-Frontend unter `E-Mail & Alerting` konfiguriert.
Falls für den Event-Typ keine Empfänger eingetragen sind, fällt `BusinessEmailService` auf `ALERT_ADMIN_EMAIL` zurück.

---

## Konfiguration

### `.env` (lokal, nicht einchecken)

Kopiere `.env.example` nach `.env` und trage deine Werte ein:

```env
# Sendender Account (Gmail empfohlen)
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=ekthochschuleproj@gmail.com
MAIL_PASSWORD=xxxx xxxx xxxx xxxx   # Google App-Passwort (nicht das normale Passwort)
MAIL_SMTP_AUTH=true
MAIL_SMTP_STARTTLS=true
MAIL_SMTP_SSL_ENABLE=false
MAIL_FROM=ekthochschuleproj@gmail.com

# Standard-Empfänger für Alerts (wird beim ersten Start in DB geseedet)
ALERT_ADMIN_EMAIL=ekthochschuleproj@gmail.com,teams-webhook@example.com
```

**Gmail App-Passwort erstellen:**
Google-Konto → Sicherheit → 2-Faktor-Authentifizierung → App-Passwörter → "Mail" + "Windows-Computer"

### Standard-Empfänger zur Laufzeit ändern

Über das Admin-Frontend (`/admin/alerting`) können Empfänger hinzugefügt,
entfernt und als Standard (`isDefault`) markiert werden.
`isDefault=true` bedeutet: diese Adresse empfängt **alle** umgeleiteten E-Mails.

---

## Neuen Alert-Event-Typ anlegen

1. `AlertEventType.java` — neuen Enum-Wert ergänzen:
   ```java
   public enum AlertEventType {
       HIGH_ERROR_RATE,
       HIGH_HEAP_USAGE,
       LOW_STOCK          // ← neu
   }
   ```

2. Flyway-Migration anlegen (z. B. `V14__add_low_stock_alert_event.sql`):
   ```sql
   INSERT INTO alert_event_config (event_type, enabled)
   VALUES ('LOW_STOCK', true)
   ON CONFLICT (event_type) DO NOTHING;
   ```

3. `BusinessEmailService.sendAlert(AlertEventType.LOW_STOCK, ...)` aufrufen.

Empfänger für den neuen Typ werden dann im Admin-Frontend konfiguriert.

---

## Zusammenfassung

| Was | Wie |
|---|---|
| Einfache transaktionale E-Mail | `emailService.sendEmail(address, subject, body)` |
| E-Mail an eingeloggten Kunden | `emailService.sendEmailToCustomer(user, subject, body)` |
| Konfigurierbare Alert-E-Mail | `businessEmailService.sendAlert(eventType, subject, body)` |
| Neue Empfänger hinzufügen | Admin-UI `/admin/alerting` oder `ALERT_ADMIN_EMAIL` in `.env` |
| Neuen Event-Typ anlegen | `AlertEventType` + Flyway-Migration + `sendAlert()`-Aufruf |
| Wohin gehen E-Mails wirklich? | An alle `isDefault=true`-Adressen in `known_email_address` |
