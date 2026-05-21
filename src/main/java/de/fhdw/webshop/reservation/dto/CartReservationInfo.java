package de.fhdw.webshop.reservation.dto;

import java.time.Instant;

public record CartReservationInfo(
        int reservedQuantity,
        Instant reservationExpiresAt,
        long reservationSecondsRemaining,
        String reservationStatus
) {}
