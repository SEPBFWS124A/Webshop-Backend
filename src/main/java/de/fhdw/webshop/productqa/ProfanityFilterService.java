package de.fhdw.webshop.productqa;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class ProfanityFilterService {

    private static final List<String> BLOCKED_TERMS = List.of(
            "fuck",
            "shit",
            "bitch",
            "asshole",
            "idiot",
            "arschloch",
            "scheisse",
            "scheiße",
            "hurensohn",
            "wichser"
    );

    public void validateClean(String text) {
        String normalized = String.valueOf(text).toLowerCase(Locale.ROOT);
        boolean containsBlockedTerm = BLOCKED_TERMS.stream().anyMatch(normalized::contains);
        if (containsBlockedTerm) {
            throw new IllegalArgumentException("Bitte formuliere deinen Beitrag sachlich. Der Text enthält gesperrte Begriffe.");
        }
    }
}
