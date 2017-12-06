package com.jivecake.api.service;

import com.jivecake.api.model.Item;

public class ItemService {
    public static final int STATUS_ACTIVE = 0;
    public static final int STATUS_INACTIVE = 1;

    public static boolean isValid(Item item) {
        if (item == null) {
            return false;
        }

        boolean hasTimeAndCountViolation = item.timeAmounts != null && !item.timeAmounts.isEmpty() &&
                item.countAmounts != null && !item.countAmounts.isEmpty();

        boolean hasNegativeAmountViolation = item.timeAmounts != null &&
            item.timeAmounts.stream().filter(t -> t.amount < 0).count() > 0 ||
            item.countAmounts != null &&
            item.countAmounts.stream().filter(t -> t.amount < 0).count() > 0;

        return (item.name != null && item.name.length() > 0 && item.name.length() <= 100) &&
            (item.amount >= 0) &&
            (item.maximumPerUser == null || item.maximumPerUser >= 0) &&
            (item.totalAvailible == null || item.totalAvailible >= 0) &&
            (
                item.status == ItemService.STATUS_ACTIVE ||
                item.status == ItemService.STATUS_INACTIVE
            ) &&
            !hasTimeAndCountViolation &&
            !hasNegativeAmountViolation;
    }
}