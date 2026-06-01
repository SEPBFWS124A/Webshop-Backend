package de.fhdw.webshop.warehouse;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.fhdw.webshop.config.GlobalExceptionHandler;
import de.fhdw.webshop.order.OrderStatus;
import de.fhdw.webshop.warehouse.dto.AdvanceOrderResponse;
import de.fhdw.webshop.warehouse.dto.AdvanceWarehouseOrderRequest;
import de.fhdw.webshop.warehouse.dto.AutoAssignTruckIdentifiersResponse;
import de.fhdw.webshop.warehouse.dto.CompletePackingResponse;
import de.fhdw.webshop.warehouse.dto.WarehouseLocationResponse;
import de.fhdw.webshop.warehouse.dto.WarehouseOrderResponse;
import de.fhdw.webshop.warehouse.dto.WarehouseTruckResponse;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class WarehouseControllerTest {

    @Mock
    private WarehouseService warehouseService;

    @Mock
    private WarehouseStockBalanceService warehouseStockBalanceService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        WarehouseController controller = new WarehouseController(warehouseService, warehouseStockBalanceService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // -------------------------------------------------------------------------
    // GET /api/warehouse/orders
    // -------------------------------------------------------------------------

    @Test
    void listOrdersReturnsOk() throws Exception {
        WarehouseOrderResponse order = sampleOrderResponse(1L, "ORD-001", OrderStatus.CONFIRMED);
        when(warehouseService.listOrders((List<OrderStatus>) isNull(), isNull())).thenReturn(List.of(order));

        mockMvc.perform(get("/api/warehouse/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].orderId").value(1))
                .andExpect(jsonPath("$[0].orderNumber").value("ORD-001"))
                .andExpect(jsonPath("$[0].status").value("CONFIRMED"));
    }

    @Test
    void listOrdersFiltersByStatus() throws Exception {
        WarehouseOrderResponse order = sampleOrderResponse(2L, "ORD-002", OrderStatus.PACKED_IN_WAREHOUSE);
        when(warehouseService.listOrders(eq(List.of(OrderStatus.PACKED_IN_WAREHOUSE)), isNull()))
                .thenReturn(List.of(order));

        mockMvc.perform(get("/api/warehouse/orders").param("status", "PACKED_IN_WAREHOUSE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("PACKED_IN_WAREHOUSE"));
    }

    @Test
    void listOrdersFiltersByWarehouseLocation() throws Exception {
        when(warehouseService.listOrders((List<OrderStatus>) isNull(), eq(10L))).thenReturn(List.of());

        mockMvc.perform(get("/api/warehouse/orders").param("warehouseLocationId", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // -------------------------------------------------------------------------
    // GET /api/warehouse/locations
    // -------------------------------------------------------------------------

    @Test
    void listLocationsReturnsOk() throws Exception {
        WarehouseLocationResponse location = new WarehouseLocationResponse(
                10L, "WH-MAIN", "Main Warehouse", "Main Street 1",
                "33602", "Bielefeld", "Germany", true, 52.02, 8.53, true);
        when(warehouseService.listLocations()).thenReturn(List.of(location));

        mockMvc.perform(get("/api/warehouse/locations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].code").value("WH-MAIN"))
                .andExpect(jsonPath("$[0].mainLocation").value(true));
    }

    // -------------------------------------------------------------------------
    // GET /api/warehouse/trucks
    // -------------------------------------------------------------------------

    @Test
    void listTrucksReturnsOk() throws Exception {
        WarehouseTruckResponse truck = new WarehouseTruckResponse(
                "LKW-001", TruckStatus.AVAILABLE, null, null,
                null, null, null, null, null, null,
                10, 0, 10, false, "Main Warehouse");
        when(warehouseService.listTrucks(isNull())).thenReturn(List.of(truck));

        mockMvc.perform(get("/api/warehouse/trucks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].truckIdentifier").value("LKW-001"))
                .andExpect(jsonPath("$[0].status").value("AVAILABLE"));
    }

    // -------------------------------------------------------------------------
    // GET /api/warehouse/trucks/{truckIdentifier}/orders
    // -------------------------------------------------------------------------

    @Test
    void listTruckOrdersReturnsOk() throws Exception {
        WarehouseOrderResponse order = sampleOrderResponse(3L, "ORD-003", OrderStatus.IN_TRUCK);
        when(warehouseService.listOrdersForTruck("LKW-001")).thenReturn(List.of(order));

        mockMvc.perform(get("/api/warehouse/trucks/LKW-001/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].orderId").value(3));
    }

    // -------------------------------------------------------------------------
    // PUT /api/warehouse/orders/{id}/advance
    // -------------------------------------------------------------------------

    @Test
    void advanceOrderReturnsOk() throws Exception {
        AdvanceOrderResponse response = new AdvanceOrderResponse(
                true,
                new AdvanceOrderResponse.OrderSummary(1L, OrderStatus.IN_TRUCK, "LKW-001", Instant.now()),
                "Order loaded into truck",
                1);
        when(warehouseService.advanceOrderWithNextStatus(eq(1L), any(), any())).thenReturn(response);

        AdvanceWarehouseOrderRequest request = new AdvanceWarehouseOrderRequest(
                OrderStatus.IN_TRUCK, "LKW-001", null, null, null);

        mockMvc.perform(put("/api/warehouse/orders/1/advance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.order.status").value("IN_TRUCK"))
                .andExpect(jsonPath("$.order.truckIdentifier").value("LKW-001"))
                .andExpect(jsonPath("$.batchUpdatedCount").value(1));
    }

    @Test
    void advanceOrderReturns404WhenNotFound() throws Exception {
        when(warehouseService.advanceOrderWithNextStatus(eq(999L), any(), any()))
                .thenThrow(new EntityNotFoundException("Order not found: 999"));

        AdvanceWarehouseOrderRequest request = new AdvanceWarehouseOrderRequest(
                OrderStatus.IN_TRUCK, "LKW-001", null, null, null);

        mockMvc.perform(put("/api/warehouse/orders/999/advance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    // -------------------------------------------------------------------------
    // POST /api/warehouse/orders/{orderId}/complete-packing
    // -------------------------------------------------------------------------

    @Test
    void completePackingReturnsOk() throws Exception {
        CompletePackingResponse response = new CompletePackingResponse(
                1L, OrderStatus.PACKED_IN_WAREHOUSE, "LKW-001", null, true,
                "Packing completed");
        when(warehouseService.completePacking(eq(1L), any())).thenReturn(response);

        mockMvc.perform(post("/api/warehouse/orders/1/complete-packing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(1))
                .andExpect(jsonPath("$.newStatus").value("PACKED_IN_WAREHOUSE"))
                .andExpect(jsonPath("$.routePlanningTriggered").value(true));
    }

    @Test
    void completePackingReturns409WhenNotAllItemsPicked() throws Exception {
        when(warehouseService.completePacking(eq(2L), any()))
                .thenThrow(new IllegalStateException("All items must be fully picked before packing"));

        mockMvc.perform(post("/api/warehouse/orders/2/complete-packing"))
                .andExpect(status().isConflict());
    }

    // -------------------------------------------------------------------------
    // PUT /api/warehouse/orders/{id}/truck
    // -------------------------------------------------------------------------

    @Test
    void updateTruckIdentifierReturnsOk() throws Exception {
        WarehouseOrderResponse order = sampleOrderResponse(1L, "ORD-001", OrderStatus.CONFIRMED);
        when(warehouseService.updateTruckIdentifier(eq(1L), eq("LKW-002"))).thenReturn(order);

        AdvanceWarehouseOrderRequest request = new AdvanceWarehouseOrderRequest(
                null, "LKW-002", null, null, null);

        mockMvc.perform(put("/api/warehouse/orders/1/truck")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(1));
    }

    // -------------------------------------------------------------------------
    // DELETE /api/warehouse/orders/{orderId}/truck
    // -------------------------------------------------------------------------

    @Test
    void removeTruckIdentifierReturnsOk() throws Exception {
        WarehouseOrderResponse order = sampleOrderResponse(1L, "ORD-001", OrderStatus.PACKED_IN_WAREHOUSE);
        when(warehouseService.removeTruckIdentifier(eq(1L), any())).thenReturn(order);

        mockMvc.perform(delete("/api/warehouse/orders/1/truck"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(1));
    }

    // -------------------------------------------------------------------------
    // POST /api/warehouse/orders/auto-assign-trucks
    // -------------------------------------------------------------------------

    @Test
    void autoAssignTrucksReturnsOk() throws Exception {
        AutoAssignTruckIdentifiersResponse response = new AutoAssignTruckIdentifiersResponse(
                List.of(), List.of(), 0, List.of(), List.of());
        when(warehouseService.autoAssignTruckIdentifiers()).thenReturn(response);

        mockMvc.perform(post("/api/warehouse/orders/auto-assign-trucks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedCount").value(0));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static WarehouseOrderResponse sampleOrderResponse(Long id, String orderNumber, OrderStatus status) {
        return new WarehouseOrderResponse(
                id,
                id,
                orderNumber,
                "Max Mustermann",
                "max@example.test",
                status,
                "DE-33602",
                "Germany · Bielefeld",
                null,
                null,
                null,
                true,
                List.of(),
                null,
                false,
                "Main Street 5, 33602 Bielefeld",
                "Main Street 5",
                "Bielefeld",
                "33602",
                "Germany",
                Instant.now(),
                Instant.now(),
                List.of()
        );
    }
}



