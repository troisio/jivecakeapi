package com.jivecake.api.service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
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
import com.jivecake.api.model.Transaction;

public class TransactionService {
    public static final int PAYMENT_EQUAL = 0;
    public static final int SETTLED = 0;
    public static final int PENDING = 1;
    public static final int USER_REVOKED = 2;
    public static final int REFUNDED = 3;
    public static final int UNKNOWN = 4;

    public static final DecimalFormat DEFAULT_DECIMAL_FORMAT = new DecimalFormat();

    static {
        TransactionService.DEFAULT_DECIMAL_FORMAT.setMinimumFractionDigits(2);
        TransactionService.DEFAULT_DECIMAL_FORMAT.setMaximumFractionDigits(2);
        TransactionService.DEFAULT_DECIMAL_FORMAT.setGroupingUsed(false);
    }

    private final Datastore datastore;
    public static final List<String> CURRENCIES = Arrays.asList("EUR", "USD");
    public final Predicate<Transaction> usedForCountFilter = transaction -> transaction.leaf &&
        (
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

    public Query<Transaction> getTransactionQueryForCounting() {
        Query<Transaction> query = this.datastore.createQuery(Transaction.class);

        query.and(
            query.criteria("leaf").equal(true),
            query.or(
                query.criteria("status").equal(TransactionService.SETTLED),
                query.criteria("status").equal(TransactionService.PENDING)
            )
         );

        return query;
    }

    public boolean isValidTransaction(Transaction transaction) {
        return transaction.quantity > 0 &&
            TransactionService.CURRENCIES.contains(transaction.currency) &&
            (
                transaction.status == TransactionService.PENDING ||
                transaction.status == TransactionService.REFUNDED ||
                transaction.status == TransactionService.SETTLED ||
                transaction.status == TransactionService.USER_REVOKED
            ) &&
            (
                transaction.paymentStatus == TransactionService.PAYMENT_EQUAL
            );
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

        if ("PaypalPayment".equals(subject.linkedObjectClass)) {
            system.setCellValue("paypal");
        } else if ("StripeCharge".equals(subject.linkedObjectClass)) {
            system.setCellValue("stripe");
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
        return "PaypalPayment".equals(transaction.linkedObjectClass) ||
          "StripeCharge".equals(transaction.linkedObjectClass);
    }
}
