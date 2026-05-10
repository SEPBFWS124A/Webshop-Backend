# Verkäuferkonto beantragen

Diese Ergänzung setzt US #247 für den öffentlichen Marktplatzpfad um.

## API

- `POST /api/seller-applications`
- öffentlich erreichbar, kein Login notwendig

## Gespeicherte Daten

- Firmenname
- Ansprechpartner
- E-Mail
- Telefon
- Website
- Produktkategorie
- Zusatznachricht
- Status `RECEIVED`

## Verhalten

- Pflichtfelder werden serverseitig validiert.
- Erfolgreiche Anträge werden in `seller_applications` gespeichert.
- Zur Nachvollziehbarkeit wird zusätzlich ein System-Audit-Eintrag geschrieben.
- Die Umsetzung simuliert bewusst nur den Antragsprozess und erstellt noch kein echtes Verkäuferkonto.
