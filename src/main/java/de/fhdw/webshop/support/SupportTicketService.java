package de.fhdw.webshop.support;

import de.fhdw.webshop.notification.SystemNotificationService;
import de.fhdw.webshop.order.Order;
import de.fhdw.webshop.order.OrderRepository;
import de.fhdw.webshop.support.dto.CreateSupportTicketMessageRequest;
import de.fhdw.webshop.support.dto.CreateSupportTicketRequest;
import de.fhdw.webshop.support.dto.RecentSupportOrderResponse;
import de.fhdw.webshop.support.dto.SupportTicketMessageResponse;
import de.fhdw.webshop.support.dto.SupportTicketResponse;
import de.fhdw.webshop.support.dto.UpdateSupportTicketStatusRequest;
import de.fhdw.webshop.user.User;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.Year;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SupportTicketService {

    private final SupportTicketRepository ticketRepository;
    private final SupportTicketMessageRepository messageRepository;
    private final OrderRepository orderRepository;
    private final SystemNotificationService notificationService;

    @Transactional(readOnly = true)
    public List<SupportTicketResponse> listMine(User currentUser) {
        return ticketRepository.findByCustomerIdOrderByUpdatedAtDesc(currentUser.getId())
                .stream()
                .map(ticket -> toResponse(ticket, false))
                .toList();
    }

    @Transactional(readOnly = true)
    public SupportTicketResponse getMine(Long ticketId, User currentUser) {
        return toResponse(findMine(ticketId, currentUser), false);
    }

    @Transactional(readOnly = true)
    public List<RecentSupportOrderResponse> listRecentOrders(User currentUser) {
        return orderRepository.findTop10ByCustomerIdOrderByCreatedAtDesc(currentUser.getId())
                .stream()
                .map(order -> new RecentSupportOrderResponse(
                        order.getId(),
                        order.getOrderNumber(),
                        order.getCreatedAt()
                ))
                .toList();
    }

    @Transactional
    public SupportTicketResponse createMine(User currentUser, CreateSupportTicketRequest request) {
        SupportTicket ticket = new SupportTicket();
        ticket.setTicketNumber("HD-TMP-" + UUID.randomUUID().toString().substring(0, 12));
        ticket.setCustomer(currentUser);
        ticket.setSubject(normalize(request.subject(), 180, "Bitte gib einen Betreff ein."));
        ticket.setStatus(SupportTicketStatus.OPEN);
        if (request.orderId() != null) {
            ticket.setRelatedOrder(orderRepository.findByIdAndCustomerId(request.orderId(), currentUser.getId())
                    .orElseThrow(() -> new EntityNotFoundException("Order not found: " + request.orderId())));
        }

        SupportTicket savedTicket = ticketRepository.saveAndFlush(ticket);
        savedTicket.setTicketNumber(String.format("HD-%d-%06d", Year.now().getValue(), savedTicket.getId()));
        addMessage(savedTicket, currentUser, request.message(), false);

        return toResponse(ticketRepository.save(savedTicket), false);
    }

    @Transactional
    public SupportTicketResponse addCustomerMessage(
            Long ticketId,
            User currentUser,
            CreateSupportTicketMessageRequest request
    ) {
        SupportTicket ticket = findMine(ticketId, currentUser);
        if (ticket.getStatus() == SupportTicketStatus.CLOSED) {
            ticket.setStatus(SupportTicketStatus.OPEN);
        }
        addMessage(ticket, currentUser, request.message(), false);
        return toResponse(ticketRepository.save(ticket), false);
    }

    @Transactional(readOnly = true)
    public List<SupportTicketResponse> listAdmin() {
        return ticketRepository.findAllByOrderByUpdatedAtDesc()
                .stream()
                .map(ticket -> toResponse(ticket, true))
                .toList();
    }

    @Transactional(readOnly = true)
    public SupportTicketResponse getAdmin(Long ticketId) {
        return toResponse(findAny(ticketId), true);
    }

    @Transactional
    public SupportTicketResponse addAdminMessage(
            Long ticketId,
            User currentUser,
            CreateSupportTicketMessageRequest request
    ) {
        SupportTicket ticket = findAny(ticketId);
        boolean internalNote = Boolean.TRUE.equals(request.internalNote());
        addMessage(ticket, currentUser, request.message(), internalNote);
        if (!internalNote) {
            if (ticket.getStatus() == SupportTicketStatus.OPEN) {
                ticket.setStatus(SupportTicketStatus.IN_PROGRESS);
            }
            notificationService.createSupportTicketReplyNotification(
                    ticket.getCustomer(),
                    ticket.getId(),
                    ticket.getTicketNumber(),
                    ticket.getSubject(),
                    currentUser.getUsername()
            );
        }
        return toResponse(ticketRepository.save(ticket), true);
    }

    @Transactional
    public SupportTicketResponse updateStatus(
            Long ticketId,
            User currentUser,
            UpdateSupportTicketStatusRequest request
    ) {
        SupportTicket ticket = findAny(ticketId);
        SupportTicketStatus previousStatus = ticket.getStatus();
        ticket.setStatus(request.status());
        if (previousStatus != SupportTicketStatus.CLOSED && request.status() == SupportTicketStatus.CLOSED) {
            notificationService.createSupportTicketClosedNotification(
                    ticket.getCustomer(),
                    ticket.getId(),
                    ticket.getTicketNumber(),
                    ticket.getSubject()
            );
        }
        return toResponse(ticketRepository.save(ticket), true);
    }

    private SupportTicket findMine(Long ticketId, User currentUser) {
        return ticketRepository.findByIdAndCustomerId(ticketId, currentUser.getId())
                .orElseThrow(() -> new EntityNotFoundException("Support ticket not found: " + ticketId));
    }

    private SupportTicket findAny(Long ticketId) {
        return ticketRepository.findById(ticketId)
                .orElseThrow(() -> new EntityNotFoundException("Support ticket not found: " + ticketId));
    }

    private void addMessage(SupportTicket ticket, User author, String text, boolean internalNote) {
        SupportTicketMessage message = new SupportTicketMessage();
        message.setTicket(ticket);
        message.setAuthor(author);
        message.setMessageText(normalize(text, 5000, "Bitte gib eine Nachricht ein."));
        message.setInternalNote(internalNote);
        messageRepository.save(message);
        ticket.getMessages().add(message);
        ticket.setUpdatedAt(Instant.now());
    }

    private String normalize(String value, int maxLength, String emptyMessage) {
        String normalized = String.valueOf(value == null ? "" : value).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(emptyMessage);
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException("Der Text darf maximal " + maxLength + " Zeichen lang sein.");
        }
        return normalized;
    }

    private SupportTicketResponse toResponse(SupportTicket ticket, boolean includeInternalNotes) {
        Order relatedOrder = ticket.getRelatedOrder();
        User customer = ticket.getCustomer();
        return new SupportTicketResponse(
                ticket.getId(),
                ticket.getTicketNumber(),
                ticket.getSubject(),
                ticket.getStatus(),
                customer.getId(),
                customer.getUsername(),
                customer.getEmail(),
                relatedOrder != null ? relatedOrder.getId() : null,
                relatedOrder != null ? relatedOrder.getOrderNumber() : null,
                ticket.getCreatedAt(),
                ticket.getUpdatedAt(),
                ticket.getMessages().stream()
                        .filter(message -> includeInternalNotes || !message.isInternalNote())
                        .map(this::toResponse)
                        .toList()
        );
    }

    private SupportTicketMessageResponse toResponse(SupportTicketMessage message) {
        User author = message.getAuthor();
        return new SupportTicketMessageResponse(
                message.getId(),
                author.getId(),
                author.getUsername(),
                message.getMessageText(),
                message.isInternalNote(),
                message.getCreatedAt()
        );
    }
}
