package de.fhdw.webshop.wishlist;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.fhdw.webshop.user.User;
import de.fhdw.webshop.user.UserRepository;
import de.fhdw.webshop.wishlist.dto.WishlistItemDto;
import de.fhdw.webshop.wishlist.dto.WishlistListDto;
import de.fhdw.webshop.wishlist.dto.WishlistStateDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WishlistService {

    private static final String DEFAULT_WISHLIST_ID = "wishlist-default";
    private static final String DEFAULT_WISHLIST_NAME = "Meine Liste";
    private static final int CUSTOM_LIST_NAME_MAX_LENGTH = 30;

    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public WishlistStateDto getWishlistState(User currentUser) {
        if (currentUser.getWishlistState() == null || currentUser.getWishlistState().isBlank()) {
            return createDefaultWishlistState();
        }

        try {
            WishlistStateDto parsedState = objectMapper.readValue(currentUser.getWishlistState(), WishlistStateDto.class);
            return normalizeWishlistState(parsedState);
        } catch (JsonProcessingException ignored) {
            return createDefaultWishlistState();
        }
    }

    @Transactional
    public WishlistStateDto saveWishlistState(User currentUser, WishlistStateDto wishlistState) {
        WishlistStateDto normalizedState = normalizeWishlistState(wishlistState);
        try {
            currentUser.setWishlistState(objectMapper.writeValueAsString(normalizedState));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Wishlist state could not be stored");
        }

        userRepository.save(currentUser);
        return normalizedState;
    }

    private WishlistStateDto createDefaultWishlistState() {
        return new WishlistStateDto(
                List.of(new WishlistListDto(DEFAULT_WISHLIST_ID, DEFAULT_WISHLIST_NAME, Instant.now().toString())),
                DEFAULT_WISHLIST_ID,
                List.of()
        );
    }

    private WishlistStateDto normalizeWishlistState(WishlistStateDto rawState) {
        WishlistStateDto state = rawState != null ? rawState : createDefaultWishlistState();
        List<WishlistListDto> inputLists = state.lists() != null ? state.lists() : List.of();

        Map<String, WishlistListDto> listsById = new LinkedHashMap<>();
        listsById.put(DEFAULT_WISHLIST_ID, new WishlistListDto(DEFAULT_WISHLIST_ID, DEFAULT_WISHLIST_NAME, Instant.now().toString()));

        inputLists.stream()
                .filter(list -> list != null && list.id() != null && !list.id().isBlank())
                .sorted(Comparator.comparing(list -> DEFAULT_WISHLIST_ID.equals(list.id()) ? 0 : 1))
                .forEach(list -> {
                    String listId = list.id().trim();
                    String listName = DEFAULT_WISHLIST_ID.equals(listId)
                            ? DEFAULT_WISHLIST_NAME
                            : normalizeCustomListName(list.name());
                    String createdAt = normalizeText(list.createdAt(), Instant.now().toString());
                    listsById.put(listId, new WishlistListDto(listId, listName, createdAt));
                });

        List<WishlistItemDto> normalizedItems = new ArrayList<>();
        List<WishlistItemDto> inputItems = state.items() != null ? state.items() : List.of();
        for (WishlistItemDto item : inputItems) {
            if (item == null || item.productId() == null) {
                continue;
            }

            String resolvedListId = listsById.containsKey(item.listId()) ? item.listId() : DEFAULT_WISHLIST_ID;
            normalizedItems.add(new WishlistItemDto(
                    resolvedListId,
                    item.productId(),
                    normalizeText(item.name(), "Produkt #" + item.productId()),
                    normalizeText(item.description(), ""),
                    normalizeText(item.category(), ""),
                    normalizeText(item.imageUrl(), ""),
                    item.recommendedRetailPrice(),
                    item.purchasable(),
                    item.promoted(),
                    item.stock(),
                    item.inventory(),
                    normalizeText(item.savedAt(), Instant.now().toString()),
                    normalizeText(item.note(), "")
            ));
        }

        String activeListId = listsById.containsKey(state.activeListId()) ? state.activeListId() : DEFAULT_WISHLIST_ID;
        return new WishlistStateDto(List.copyOf(listsById.values()), activeListId, List.copyOf(normalizedItems));
    }

    private String normalizeText(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private String normalizeCustomListName(String value) {
        String normalizedName = normalizeText(value, "Liste");
        if (normalizedName.length() <= CUSTOM_LIST_NAME_MAX_LENGTH) {
            return normalizedName;
        }
        return normalizedName.substring(0, CUSTOM_LIST_NAME_MAX_LENGTH);
    }
}
