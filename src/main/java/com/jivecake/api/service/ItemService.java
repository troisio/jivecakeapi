package com.jivecake.api.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.bson.types.ObjectId;

import com.jivecake.api.model.Item;
import com.jivecake.api.model.Transaction;

public class ItemService {
    public static final int STATUS_ACTIVE = 0;
    public static final int STATUS_INACTIVE = 1;

    public boolean isValid(Item item) {
        boolean hasTimeAndCountViolation = item.timeAmounts != null && !item.timeAmounts.isEmpty() &&
                item.countAmounts != null && !item.countAmounts.isEmpty();

        boolean hasNegativeAmountViolation = item.timeAmounts != null && item.timeAmounts.stream().filter(t -> t.amount < 0).count() > 0 ||
                  item.countAmounts != null && item.countAmounts.stream().filter(t -> t.amount < 0).count() > 0;

        return (item.amount >= 0) &&
            (item.maximumPerUser == null || item.maximumPerUser >= 0) &&
            (item.totalAvailible == null || item.totalAvailible >= 0) &&
            (
                item.status == ItemService.STATUS_ACTIVE ||
                item.status == ItemService.STATUS_INACTIVE
            ) &&
            !hasTimeAndCountViolation &&
            !hasNegativeAmountViolation;
    }

    public double[] getAmounts(List<Item> items, Date date, Collection<Transaction> transactions) {
        double[] result = new double[items.size()];

        Map<ObjectId, List<Transaction>> itemToTransactions = items.stream()
            .collect(
                Collectors.toMap(item -> item.id, item -> new ArrayList<>())
            );

        for (Transaction transaction: transactions) {
            if (itemToTransactions.containsKey(transaction.itemId)) {
                itemToTransactions.get(transaction.itemId).add(transaction);
            }
        }

        for (int index = 0; index < items.size(); index++) {
            Item item = items.get(index);

            double amount = item.getDerivedAmount(
                itemToTransactions.get(item.id).size(),
                date
            );

            result[index] = amount;
        }

        return result;
    }
}