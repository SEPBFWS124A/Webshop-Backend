package de.fhdw.webshop.pricealert;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

/**
 * Listens for ProductPriceChangedEvent and triggers immediate price-alert checks
 * for the affected product. Runs AFTER_COMMIT to ensure the new price is persisted.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PriceAlertEventListener {

    private final PriceAlertService priceAlertService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPriceChanged(ProductPriceChangedEvent event) {
        log.info("Preisänderung erkannt für Produkt {} – prüfe Preisalarme", event.productId());
        priceAlertService.checkAlertsForProduct(event.productId());
    }
}

