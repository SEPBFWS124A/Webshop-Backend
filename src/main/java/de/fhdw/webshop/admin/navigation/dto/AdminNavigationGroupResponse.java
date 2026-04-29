package de.fhdw.webshop.admin.navigation.dto;

import java.util.List;

public record AdminNavigationGroupResponse(
        String id,
        String label,
        String icon,
        List<AdminNavigationItemResponse> items
) {}
