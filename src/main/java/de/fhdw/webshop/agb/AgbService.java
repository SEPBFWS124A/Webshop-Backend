package de.fhdw.webshop.agb;

import de.fhdw.webshop.admin.AuditInitiator;
import de.fhdw.webshop.admin.AuditLogService;
import de.fhdw.webshop.agb.dto.AgbVersionResponse;
import de.fhdw.webshop.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AgbService {

    static final String INITIAL_AGB_TEXT =
            "# Allgemeine Geschäftsbedingungen\n\n" +
            "## 1. Geltungsbereich\n\n" +
            "Diese Allgemeinen Geschäftsbedingungen gelten für alle Bestellungen, die Verbraucher und " +
            "Unternehmer über den Online-Shop von Fachhochschule der Wirtschaft (FHDW) gGmbH abgeben. " +
            "Verbraucher ist jede natürliche Person, die ein Rechtsgeschäft zu Zwecken abschließt, die " +
            "überwiegend weder ihrer gewerblichen noch ihrer selbstständigen beruflichen Tätigkeit " +
            "zugerechnet werden können. Unternehmer ist jede natürliche oder juristische Person oder " +
            "rechtsfähige Personengesellschaft, die bei Abschluss eines Rechtsgeschäfts in Ausübung " +
            "ihrer gewerblichen oder selbstständigen beruflichen Tätigkeit handelt.\n\n" +
            "## 2. Vertragspartner\n\n" +
            "Der Kaufvertrag kommt zustande mit Fachhochschule der Wirtschaft (FHDW) gGmbH, " +
            "Hauptstraße 2, 51465 Bergisch Gladbach.\n\n" +
            "## 3. Angebot und Vertragsschluss\n\n" +
            "- Die Darstellung der Produkte im Online-Shop stellt noch kein rechtlich bindendes Angebot, " +
            "sondern eine unverbindliche Aufforderung zur Bestellung dar.\n" +
            "- Durch Anklicken des Buttons \"Jetzt verbindlich bestellen\" geben Sie ein verbindliches " +
            "Angebot zum Abschluss eines Kaufvertrags ab.\n" +
            "- Der Vertrag kommt erst zustande, wenn wir die Bestellung durch eine Auftragsbestätigung " +
            "per E-Mail annehmen oder die Ware versenden.\n" +
            "- Wir können Bestellungen ablehnen, wenn Produkte nicht verfügbar sind, Preis- oder " +
            "Systemfehler vorliegen oder Missbrauchsverdacht besteht.\n\n" +
            "## 4. Kundenkonto\n\n" +
            "Bestellungen können - je nach Shop-Funktion - über ein Kundenkonto oder als Gast erfolgen. " +
            "Zugangsdaten sind vertraulich zu behandeln. Nutzer sind verpflichtet, ihre Angaben im " +
            "Kundenkonto aktuell und wahrheitsgemäß zu halten.\n\n" +
            "## 5. Preise und Versandkosten\n\n" +
            "Sämtliche im Shop ausgewiesenen Preise verstehen sich in Euro inklusive gesetzlicher " +
            "Umsatzsteuer, soweit nicht ausdrücklich anders gekennzeichnet. Hinzu kommen die im " +
            "Bestellprozess transparent ausgewiesenen Versandkosten.\n\n" +
            "Rabatte, Gutscheine und kundenspezifische Preislogiken werden nur nach Maßgabe der jeweils " +
            "im Warenkorb und Checkout angezeigten Bedingungen berücksichtigt.\n\n" +
            "## 6. Zahlungsbedingungen\n\n" +
            "Die jeweils verfügbaren Zahlungsarten werden Ihnen im Checkout angezeigt. Die Belastung oder " +
            "Zahlungsanweisung erfolgt gemäß der ausgewählten Zahlungsart. Wir behalten uns vor, " +
            "bestimmte Zahlungsarten im Einzelfall nicht anzubieten.\n\n" +
            "## 7. Lieferbedingungen\n\n" +
            "- Lieferungen erfolgen an die von Ihnen im Bestellprozess angegebene Lieferadresse.\n" +
            "- Angegebene Lieferzeiten verstehen sich als realistische Regellaufzeiten und stehen unter " +
            "dem Vorbehalt ordnungsgemäßer Selbstbelieferung sowie unvorhersehbarer Störungen.\n" +
            "- Teillieferungen sind zulässig, soweit dies für Sie zumutbar ist.\n" +
            "- Ist eine Zustellung wiederholt nicht möglich, können wir vom Vertrag zurücktreten, " +
            "nachdem wir Sie erfolglos kontaktiert haben.\n\n" +
            "## 8. Eigentumsvorbehalt\n\n" +
            "Die Ware bleibt bis zur vollständigen Bezahlung unser Eigentum. Gegenüber Unternehmern gilt " +
            "dies bis zur vollständigen Begleichung sämtlicher Forderungen aus der laufenden " +
            "Geschäftsbeziehung.\n\n" +
            "## 9. Gewährleistung und Haftung für Mängel\n\n" +
            "Es gelten die gesetzlichen Mängelhaftungsrechte. Gegenüber Unternehmern beträgt die " +
            "Gewährleistungsfrist für neu hergestellte Sachen ein Jahr ab Gefahrübergang, soweit " +
            "gesetzlich zulässig und nicht zwingende Vorschriften entgegenstehen.\n\n" +
            "## 10. Haftung\n\n" +
            "- Wir haften unbeschränkt bei Vorsatz, grober Fahrlässigkeit, bei Verletzung von Leben, " +
            "Körper oder Gesundheit sowie nach zwingenden gesetzlichen Vorschriften.\n" +
            "- Bei leicht fahrlässiger Verletzung wesentlicher Vertragspflichten ist unsere Haftung auf " +
            "den vertragstypischen, vorhersehbaren Schaden begrenzt.\n" +
            "- Im Übrigen ist die Haftung für leicht fahrlässige Pflichtverletzungen ausgeschlossen.\n\n" +
            "## 11. Anwendbares Recht\n\n" +
            "Es gilt deutsches Recht unter Ausschluss des UN-Kaufrechts. Gegenüber Verbrauchern gilt " +
            "diese Rechtswahl nur insoweit, als dadurch nicht der Schutz zwingender Bestimmungen des " +
            "Rechts des Staates entzogen wird, in dem der Verbraucher seinen gewöhnlichen Aufenthalt hat.\n\n" +
            "## 12. Streitbeilegung\n\n" +
            "Wir sind grundsätzlich nicht bereit und nicht verpflichtet, an Streitbeilegungsverfahren vor " +
            "einer Verbraucherschlichtungsstelle teilzunehmen, sofern keine gesetzliche Verpflichtung besteht.";

    static final Instant INITIAL_AGB_CREATED_AT = Instant.parse("2026-04-21T00:00:00Z");

    private final AgbVersionRepository agbVersionRepository;
    private final AuditLogService auditLogService;

    public AgbVersionResponse getLatestVersion() {
        return agbVersionRepository.findTopByOrderByCreatedAtDesc()
                .map(v -> new AgbVersionResponse(v.getId(), v.getAgbText(), v.getCreatedAt()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Keine AGB-Version gefunden"));
    }

    @Transactional
    public AgbVersionResponse createNewVersion(String agbText, User admin) {
        AgbVersion version = new AgbVersion();
        version.setAgbText(agbText);
        AgbVersion saved = agbVersionRepository.save(version);
        auditLogService.record(admin, "CREATE_AGB_VERSION", "AgbVersion", saved.getId(),
                AuditInitiator.ADMIN, "Neue AGB-Version erstellt: " + saved.getCreatedAt());
        return new AgbVersionResponse(saved.getId(), saved.getAgbText(), saved.getCreatedAt());
    }

    @Transactional
    public void seedInitialAgbIfMissing() {
        if (agbVersionRepository.count() > 0) {
            return;
        }
        AgbVersion initial = new AgbVersion();
        initial.setAgbText(INITIAL_AGB_TEXT);
        initial.setCreatedAt(INITIAL_AGB_CREATED_AT);
        agbVersionRepository.save(initial);
    }

    public boolean userNeedsToAcceptAgb(User user) {
        if (user.getAgbAcceptedAt() == null) {
            return true;
        }
        var latest = agbVersionRepository.findTopByOrderByCreatedAtDesc();
        if (latest.isEmpty()) {
            return false;
        }
        return user.getAgbAcceptedAt().isBefore(latest.get().getCreatedAt());
    }
}
