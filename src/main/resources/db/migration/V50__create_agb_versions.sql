CREATE TABLE agb_versions (
    id         BIGSERIAL    PRIMARY KEY,
    agb_text   TEXT         NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

ALTER TABLE users
    ADD COLUMN agb_accepted_at TIMESTAMPTZ;

INSERT INTO agb_versions (agb_text, created_at)
SELECT $$# Allgemeine Geschäftsbedingungen

## 1. Geltungsbereich

Diese Allgemeinen Geschäftsbedingungen gelten für alle Bestellungen, die Verbraucher und Unternehmer über den Online-Shop von Fachhochschule der Wirtschaft (FHDW) gGmbH abgeben. Verbraucher ist jede natürliche Person, die ein Rechtsgeschäft zu Zwecken abschließt, die überwiegend weder ihrer gewerblichen noch ihrer selbstständigen beruflichen Tätigkeit zugerechnet werden können. Unternehmer ist jede natürliche oder juristische Person oder rechtsfähige Personengesellschaft, die bei Abschluss eines Rechtsgeschäfts in Ausübung ihrer gewerblichen oder selbstständigen beruflichen Tätigkeit handelt.

## 2. Vertragspartner

Der Kaufvertrag kommt zustande mit Fachhochschule der Wirtschaft (FHDW) gGmbH, Hauptstraße 2, 51465 Bergisch Gladbach.

## 3. Angebot und Vertragsschluss

- Die Darstellung der Produkte im Online-Shop stellt noch kein rechtlich bindendes Angebot, sondern eine unverbindliche Aufforderung zur Bestellung dar.
- Durch Anklicken des Buttons "Jetzt verbindlich bestellen" geben Sie ein verbindliches Angebot zum Abschluss eines Kaufvertrags ab.
- Der Vertrag kommt erst zustande, wenn wir die Bestellung durch eine Auftragsbestätigung per E-Mail annehmen oder die Ware versenden.
- Wir können Bestellungen ablehnen, wenn Produkte nicht verfügbar sind, Preis- oder Systemfehler vorliegen oder Missbrauchsverdacht besteht.

## 4. Kundenkonto

Bestellungen können - je nach Shop-Funktion - über ein Kundenkonto oder als Gast erfolgen. Zugangsdaten sind vertraulich zu behandeln. Nutzer sind verpflichtet, ihre Angaben im Kundenkonto aktuell und wahrheitsgemäß zu halten.

## 5. Preise und Versandkosten

Sämtliche im Shop ausgewiesenen Preise verstehen sich in Euro inklusive gesetzlicher Umsatzsteuer, soweit nicht ausdrücklich anders gekennzeichnet. Hinzu kommen die im Bestellprozess transparent ausgewiesenen Versandkosten.

Rabatte, Gutscheine und kundenspezifische Preislogiken werden nur nach Maßgabe der jeweils im Warenkorb und Checkout angezeigten Bedingungen berücksichtigt.

## 6. Zahlungsbedingungen

Die jeweils verfügbaren Zahlungsarten werden Ihnen im Checkout angezeigt. Die Belastung oder Zahlungsanweisung erfolgt gemäß der ausgewählten Zahlungsart. Wir behalten uns vor, bestimmte Zahlungsarten im Einzelfall nicht anzubieten.

## 7. Lieferbedingungen

- Lieferungen erfolgen an die von Ihnen im Bestellprozess angegebene Lieferadresse.
- Angegebene Lieferzeiten verstehen sich als realistische Regellaufzeiten und stehen unter dem Vorbehalt ordnungsgemäßer Selbstbelieferung sowie unvorhersehbarer Störungen.
- Teillieferungen sind zulässig, soweit dies für Sie zumutbar ist.
- Ist eine Zustellung wiederholt nicht möglich, können wir vom Vertrag zurücktreten, nachdem wir Sie erfolglos kontaktiert haben.

## 8. Eigentumsvorbehalt

Die Ware bleibt bis zur vollständigen Bezahlung unser Eigentum. Gegenüber Unternehmern gilt dies bis zur vollständigen Begleichung sämtlicher Forderungen aus der laufenden Geschäftsbeziehung.

## 9. Gewährleistung und Haftung für Mängel

Es gelten die gesetzlichen Mängelhaftungsrechte. Gegenüber Unternehmern beträgt die Gewährleistungsfrist für neu hergestellte Sachen ein Jahr ab Gefahrübergang, soweit gesetzlich zulässig und nicht zwingende Vorschriften entgegenstehen.

## 10. Haftung

- Wir haften unbeschränkt bei Vorsatz, grober Fahrlässigkeit, bei Verletzung von Leben, Körper oder Gesundheit sowie nach zwingenden gesetzlichen Vorschriften.
- Bei leicht fahrlässiger Verletzung wesentlicher Vertragspflichten ist unsere Haftung auf den vertragstypischen, vorhersehbaren Schaden begrenzt.
- Im Übrigen ist die Haftung für leicht fahrlässige Pflichtverletzungen ausgeschlossen.

## 11. Anwendbares Recht

Es gilt deutsches Recht unter Ausschluss des UN-Kaufrechts. Gegenüber Verbrauchern gilt diese Rechtswahl nur insoweit, als dadurch nicht der Schutz zwingender Bestimmungen des Rechts des Staates entzogen wird, in dem der Verbraucher seinen gewöhnlichen Aufenthalt hat.

## 12. Streitbeilegung

Wir sind grundsätzlich nicht bereit und nicht verpflichtet, an Streitbeilegungsverfahren vor einer Verbraucherschlichtungsstelle teilzunehmen, sofern keine gesetzliche Verpflichtung besteht.$$,
'2026-04-21 00:00:00+00'
WHERE NOT EXISTS (SELECT 1 FROM agb_versions);
