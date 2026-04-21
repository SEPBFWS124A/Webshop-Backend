package de.fhdw.webshop.standingorder;

public enum IntervalType {
    DAILY,      // Jeden Tag
    WEEKLY,     // Jede Woche am X-ten Wochentag
    MONTHLY,    // Jeden Monat am X-ten
    YEARLY,     // Jedes Jahr am X. im Monat Y
    DAYS        // Alle X Tage (altes Verhalten)
}