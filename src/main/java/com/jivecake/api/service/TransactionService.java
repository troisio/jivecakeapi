package com.jivecake.api.service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.bson.types.ObjectId;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.Key;
import org.mongodb.morphia.query.Query;

import com.fasterxml.jackson.databind.JsonNode;
import com.jivecake.api.model.Event;
import com.jivecake.api.model.Item;
import com.jivecake.api.model.PaypalIPN;
import com.jivecake.api.model.Transaction;

public class TransactionService {
    public static final int PAYMENT_EQUAL= 0;
    public static final int PAYMENT_LESS_THAN = 1;
    public static final int PAYMENT_GREATER_THAN = 2;
    public static final int PAYMENT_UNKNOWN = 3;

    public static final int SETTLED = 0;
    public static final int PENDING = 1;
    public static final int USER_REVOKED = 2;
    public static final int REFUNDED = 3;
    public static final int UNKNOWN = 3;

    private final Datastore datastore;
    private final List<String> currencies = Arrays.asList("EUR", "USD");
    public final Predicate<Transaction> usedForCountFilter = (transaction) -> (
            transaction.paymentStatus == TransactionService.PAYMENT_EQUAL ||
            transaction.paymentStatus == TransactionService.PAYMENT_GREATER_THAN
        ) && (
            transaction.status == TransactionService.SETTLED ||
            transaction.status == TransactionService.PENDING
        );

    @Inject
    public TransactionService(Datastore datastore) {
        this.datastore = datastore;
    }

    public CompletableFuture<List<Transaction>> searchTransactionsFromText(String text, Auth0Service auth0Service) {
        CompletableFuture<List<Transaction>> future = new CompletableFuture<>();

        List<Object> itemIds = this.datastore.createQuery(Item.class)
            .field("name").containsIgnoreCase(text)
            .asKeyList()
            .stream()
            .map(Key::getId)
            .collect(Collectors.toList());

        List<Object> eventIds = this.datastore.createQuery(Event.class)
            .field("name").containsIgnoreCase(text)
            .asKeyList()
            .stream()
            .map(Key::getId)
            .collect(Collectors.toList());

        auth0Service.searchEmailOrNames(text).thenAcceptAsync(auth0Users -> {
            Query<Transaction> query = TransactionService.this.datastore.createQuery(Transaction.class);

            List<String> auth0UserIds = new ArrayList<>();
            auth0Users.forEach(node -> auth0UserIds.add(node.get("user_id").asText()));

            query.or(
                query.criteria("user_id").in(auth0UserIds),
                query.criteria("email").startsWithIgnoreCase(text),
                query.criteria("given_name").startsWithIgnoreCase(text),
                query.criteria("family_name").startsWithIgnoreCase(text),
                query.criteria("itemId").in(itemIds),
                query.criteria("eventId").in(eventIds)
            );

            List<Transaction> transactions = query.asList();
            future.complete(transactions);
        }).exceptionally((exception) -> {
            future.completeExceptionally(exception);
            return null;
        });

        return future;
    }

    public List<Transaction> getTransactionsForItemTotal(ObjectId itemId) {
        List<Transaction> transactions = this.datastore.createQuery(Transaction.class)
            .field("itemId").equal(itemId)
            .asList();

        List<List<Transaction>> forest = this.getTransactionForest(transactions);

        List<Transaction> pendingOrCompleteLeafTransactions = forest.stream()
            .filter(lineage -> lineage.size() == 1)
            .map(lineage -> lineage.get(0))
           .filter(this.usedForCountFilter)
           .collect(Collectors.toList());

        return pendingOrCompleteLeafTransactions;
    }

    public boolean isValidTransaction(Transaction transaction) {
        return transaction.quantity > 0 && this.currencies.contains(transaction.currency);
    }

    public List<List<Transaction>> getTransactionForest(List<Transaction> transactions) {
        Map<Transaction, List<Transaction>> transactionLineageMap = transactions.stream()
            .collect(Collectors.toMap(Function.identity(), transaction -> new ArrayList<>(Arrays.asList(transaction))));

        Map<ObjectId, Transaction> parentTransactionIdMap;

        do {
            List<ObjectId> parentTransactionIds = transactionLineageMap.entrySet().stream().map(entry -> {
                List<Transaction> lineage = entry.getValue();
                return lineage.get(lineage.size() - 1).id;
            }).collect(Collectors.toList());

            parentTransactionIdMap = this.datastore.find(Transaction.class)
                .field("parentTransactionId").in(parentTransactionIds)
                .asList()
                .stream()
                .collect(Collectors.toMap(transaction -> transaction.parentTransactionId, Function.identity()));

            for (Transaction transaction: transactionLineageMap.keySet()) {
                List<Transaction> lineage = transactionLineageMap.get(transaction);
                Transaction last = lineage.get(lineage.size() - 1);

                if (parentTransactionIdMap.containsKey(last.id)) {
                    lineage.add(parentTransactionIdMap.get(last.id));
                }
            }
        } while(!parentTransactionIdMap.isEmpty());

        List<List<Transaction>> result = transactions.stream()
            .map(transaction -> transactionLineageMap.get(transaction))
            .collect(Collectors.toList());

        return result;
    }

    public void writeToExcel(List<Transaction> transactions, List<JsonNode> users, File file) throws IOException {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Transactions");
        sheet.createFreezePane(0, 1);

        List<ObjectId> itemIds = transactions.stream()
            .map(transaction -> transaction.itemId)
            .collect(Collectors.toList());

        Map<ObjectId, List<Item>> itemById = this.datastore.createQuery(Item.class)
            .field("id").in(itemIds)
            .asList()
            .stream()
            .collect(Collectors.groupingBy(item -> item.id));

        Map<String, List<JsonNode>> userById = users.stream()
            .collect(Collectors.groupingBy(user -> user.get("user_id").asText()));

        String[] headers = {
            "givenName",
            "middleName",
            "familyName",
            "email",
            "itemName",
            "amount",
            "currency",
            "timeCreated",
            "system"
        };

        CellStyle dateStyle = workbook.createCellStyle();
        CreationHelper createHelper = workbook.getCreationHelper();
        dateStyle.setDataFormat(createHelper.createDataFormat().getFormat("m/d/yy h:mm"));

        Row firstRow = sheet.createRow(0);

        for (int index = 0; index < headers.length; index++) {
            Cell cell = firstRow.createCell(index);
            cell.setCellValue(headers[index]);
        }

        for (int index = 0; index < transactions.size(); index++) {
            Row row = sheet.createRow(index + 1);

            Transaction transaction = transactions.get(index);
            Item item = itemById.containsKey(transaction.itemId) ? itemById.get(transaction.itemId).get(0) : null;
            JsonNode user = userById.containsKey(transaction.user_id) ? userById.get(transaction.user_id).get(0) : null;

            this.writeRow(transaction, item, user, row, dateStyle);
        }

        for (int index = 0; index < headers.length; index++) {
            sheet.autoSizeColumn(index);
        }

        FileOutputStream stream = new FileOutputStream(file);
        workbook.write(stream);
        stream.close();
        workbook.close();
    }

    public void writeRow(Transaction subject, Item item, JsonNode user, Row row, CellStyle dateStyle) {
        Cell givenName = row.createCell(0);
        Cell middleName = row.createCell(1);
        Cell familyName = row.createCell(2);
        Cell email = row.createCell(3);
        Cell itemName = row.createCell(4);
        Cell amount = row.createCell(5);
        Cell currency = row.createCell(6);
        Cell timeCreated = row.createCell(7);
        Cell system = row.createCell(8);

        amount.setCellValue(subject.amount);
        currency.setCellValue(subject.currency);

        if (PaypalIPN.class.getSimpleName().equals(subject.linkedObjectClass)) {
            system.setCellValue("paypal");
        }

        if (subject.timeCreated != null) {
            timeCreated.setCellValue(subject.timeCreated);
        }

        timeCreated.setCellStyle(dateStyle);

        if (item != null) {
            itemName.setCellValue(item.name);
        }

        if (user == null) {
            givenName.setCellValue(subject.given_name);
            middleName.setCellValue(subject.middleName);
            familyName.setCellValue(subject.family_name);
            email.setCellValue(subject.email);
        } else {
            if (user.has("email")) {
                email.setCellValue(user.get("email").asText());
            }

            if (user.has("user_metadata")) {
                JsonNode meta = user.get("user_metadata");

                if (meta.has("given_name")) {
                    givenName.setCellValue(meta.get("given_name").asText());
                }

                if (meta.has("family_name")) {
                    familyName.setCellValue(meta.get("family_name").asText());
                }
            } else {
                if (user.has("given_name")) {
                    givenName.setCellValue(user.get("given_name").asText());
                }

                if (user.has("family_name")) {
                    familyName.setCellValue(user.get("family_name").asText());
                }
            }
        }
    }

    public boolean isVendorTransaction(Transaction transaction) {
        return PaypalIPN.class.getSimpleName().equals(transaction.linkedObjectClass);
    }
}
