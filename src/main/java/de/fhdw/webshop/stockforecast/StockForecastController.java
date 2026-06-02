package de.fhdw.webshop.stockforecast;

import de.fhdw.webshop.stockforecast.dto.StockForecastResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/stock-forecasts")
@RequiredArgsConstructor
public class StockForecastController {

    private final StockForecastService stockForecastService;

    /** #123 — Forecast for a single product. */
    @GetMapping("/{productId}")
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'SALES_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<StockForecastResponse> getForecast(@PathVariable Long productId) {
        return ResponseEntity.ok(stockForecastService.getForecast(productId));
    }

    /** #123 — Forecast overview for all products. */
    @GetMapping
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'SALES_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<List<StockForecastResponse>> getAllForecasts() {
        return ResponseEntity.ok(stockForecastService.getAllForecasts());
    }
}
