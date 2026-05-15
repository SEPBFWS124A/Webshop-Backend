package de.fhdw.webshop.cart.audit;

import de.fhdw.webshop.cart.audit.dto.CartChangeLogResponse;
import de.fhdw.webshop.product.Product;
import de.fhdw.webshop.user.User;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CartChangeLogService {

    private final CartChangeLogRepository cartChangeLogRepository;

    @Transactional
    public void recordEmployeeCartChange(
            User employee,
            User customer,
            Product product,
            Long cartItemId,
            CartChangeAction action,
            int quantityDelta,
            int resultingQuantity) {
        CartChangeLog log = new CartChangeLog();
        log.setActorUser(employee);
        log.setActorUsername(formatActor(employee));
        log.setCustomer(customer);
        log.setCustomerNumber(customer != null ? customer.getCustomerNumber() : null);
        log.setCartItemId(cartItemId);
        log.setProduct(product);
        log.setProductSku(product != null ? product.getSku() : null);
        log.setProductName(product != null ? product.getName() : "Unbekannter Artikel");
        log.setAction(action);
        log.setQuantityDelta(quantityDelta);
        log.setResultingQuantity(resultingQuantity);
        cartChangeLogRepository.save(log);
    }

    @Transactional(readOnly = true)
    public List<CartChangeLogResponse> listForCustomer(Long customerId) {
        return cartChangeLogRepository.findTop30ByCustomerIdOrderByCreatedAtDesc(customerId).stream()
                .map(this::toResponse)
                .toList();
    }

    private CartChangeLogResponse toResponse(CartChangeLog log) {
        Long actorId = log.getActorUser() != null ? log.getActorUser().getId() : null;
        Long customerId = log.getCustomer() != null ? log.getCustomer().getId() : null;
        Long productId = log.getProduct() != null ? log.getProduct().getId() : null;

        return new CartChangeLogResponse(
                log.getId(),
                actorId,
                log.getActorUsername(),
                customerId,
                log.getCustomerNumber(),
                log.getCartItemId(),
                productId,
                log.getProductSku(),
                log.getProductName(),
                log.getAction(),
                log.getQuantityDelta(),
                log.getResultingQuantity(),
                log.getCreatedAt(),
                buildMessage(log));
    }

    private String buildMessage(CartChangeLog log) {
        String actor = log.getActorUsername();
        String article = log.getProductName();
        String sku = log.getProductSku() == null || log.getProductSku().isBlank()
                ? "ohne SKU"
                : "SKU " + log.getProductSku();
        int absoluteQuantity = Math.abs(log.getQuantityDelta());

        return switch (log.getAction()) {
            case ADD, INCREASE -> actor + " hat " + absoluteQuantity + "x " + article
                    + " (" + sku + ") zum Warenkorb hinzugefuegt.";
            case DECREASE -> actor + " hat die Menge von " + article + " (" + sku + ") um "
                    + absoluteQuantity + " reduziert.";
            case REMOVE -> actor + " hat " + absoluteQuantity + "x " + article
                    + " (" + sku + ") aus dem Warenkorb entfernt.";
        };
    }

    private String formatActor(User employee) {
        if (employee == null) {
            return "Unbekannter Mitarbeiter";
        }
        return employee.getUsername() + " (#" + employee.getId() + ")";
    }
}
