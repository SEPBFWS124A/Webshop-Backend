package de.fhdw.webshop.stockforecast;

import de.fhdw.webshop.product.Product;
import de.fhdw.webshop.product.ProductRepository;
import de.fhdw.webshop.stockforecast.dto.StockForecastResponse;
import de.fhdw.webshop.stockforecast.dto.StockForecastResponse.ForecastStatus;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StockForecastService {

    private static final int HISTORY_DAYS = 30;
    private static final int DEFAULT_LEAD_TIME_DAYS = 7;

    private final ProductRepository productRepository;
    private final SoldQuantityRepository soldQuantityRepository;

    @Transactional(readOnly = true)
    public StockForecastResponse getForecast(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("Product not found: " + productId));
        return buildForecast(product);
    }

    @Transactional(readOnly = true)
    public List<StockForecastResponse> getAllForecasts() {
        return productRepository.findAll().stream()
                .filter(p -> p.getParentProduct() == null)
                .map(this::buildForecast)
                .toList();
    }

    private StockForecastResponse buildForecast(Product product) {
        Instant since = Instant.now().minus(HISTORY_DAYS, ChronoUnit.DAYS);
        int soldInPeriod = soldQuantityRepository.sumSoldQuantityForProduct(product.getId(), since);
        double avgDailySales = (double) soldInPeriod / HISTORY_DAYS;

        int leadTimeDays = product.getSupplierLeadTimeDays() != null
                ? product.getSupplierLeadTimeDays()
                : DEFAULT_LEAD_TIME_DAYS;

        int warningThresholdDays = leadTimeDays + 3;

        int estimatedDaysRemaining;
        if (avgDailySales <= 0) {
            estimatedDaysRemaining = product.getStock() > 0 ? Integer.MAX_VALUE : 0;
        } else {
            estimatedDaysRemaining = (int) Math.floor(product.getStock() / avgDailySales);
        }

        ForecastStatus status;
        if (product.getStock() <= 0) {
            status = ForecastStatus.CRITICAL;
        } else if (estimatedDaysRemaining <= warningThresholdDays) {
            status = ForecastStatus.WARNING;
        } else {
            status = ForecastStatus.OK;
        }

        int displayedDays = estimatedDaysRemaining == Integer.MAX_VALUE ? 999 : estimatedDaysRemaining;

        return new StockForecastResponse(
                product.getId(),
                product.getName(),
                product.getSku(),
                product.getStock(),
                Math.round(avgDailySales * 100.0) / 100.0,
                displayedDays,
                warningThresholdDays,
                status,
                product.getSupplierName(),
                leadTimeDays
        );
    }
}
