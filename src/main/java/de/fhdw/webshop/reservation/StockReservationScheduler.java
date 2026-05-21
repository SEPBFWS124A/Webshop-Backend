package de.fhdw.webshop.reservation;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StockReservationScheduler {

    private final StockReservationService stockReservationService;

    @Scheduled(fixedDelayString = "${app.stock-reservations.cleanup-ms:60000}")
    public void maintainReservations() {
        stockReservationService.expireOverdueReservations();
        stockReservationService.sendAvailabilityNotifications();
    }
}
