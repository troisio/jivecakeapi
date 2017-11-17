package com.jivecake.api.service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
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
import org.mongodb.morphia.query.Query;

import com.auth0.json.mgmt.users.User;
import com.jivecake.api.model.Event;
import com.jivecake.api.model.Item;
import com.jivecake.api.model.Transaction;
import com.jivecake.api.model.UserData;

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

    public static final List<String> CURRENCIES = Arrays.asList(
        "AUD",
        "BBD",
        "BRL",
        "BSD",
        "CAD",
        "EUR",
        "FJD",
        "GBP",
        "ILS",
        "ISK",
        "NZD",
        "RUS",
        "SEK",
        "TTD",
        "USD",
        "ZAR"
    );
    public static final Predicate<Transaction> usedForCountFilter = transaction -> transaction.leaf &&
        (
            transaction.status == TransactionService.SETTLED ||
            transaction.status == TransactionService.PENDING
        );

    private final Datastore datastore;

    @Inject
    public TransactionService(Datastore datastore) {
        this.datastore = datastore;
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

    public void writeToExcel(
        Event event,
        User[] users,
        List<Transaction> transactions,
        File file
    ) throws IOException {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Transactions");
        sheet.createFreezePane(0, 1);

        Map<String, UserData> idToUserData = event.userData.stream()
            .collect(Collectors.toMap(userData -> userData.userId, Function.identity()));

        List<ObjectId> itemIds = transactions.stream()
            .map(transaction -> transaction.itemId)
            .collect(Collectors.toList());

        Map<ObjectId, List<Item>> itemById = this.datastore.createQuery(Item.class)
            .field("id").in(itemIds)
            .asList()
            .stream()
            .collect(Collectors.groupingBy(item -> item.id));

        Map<String, List<User>> userById = Arrays.asList(users).stream()
            .collect(Collectors.groupingBy(user -> user.getId()));

        String[] headers = {
            "registrationNumber",
            "givenName",
            "middleName",
            "familyName",
            "email",
            "itemName",
            "amount",
            "currency",
            "timeCreated",
            "generatedBy"
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
            User user = userById.containsKey(transaction.user_id) ? userById.get(transaction.user_id).get(0) : null;

            this.writeRow(transaction, idToUserData, item, user, row, dateStyle);
        }

        for (int index = 0; index < headers.length; index++) {
            sheet.autoSizeColumn(index);
        }

        FileOutputStream stream = new FileOutputStream(file);
        workbook.write(stream);
        stream.close();
        workbook.close();
    }

    public void writeRow(
        Transaction subject,
        Map<String, UserData> idToUserData,
        Item item,
        User user,
        Row row,
        CellStyle dateStyle
    ) {
        Cell registrationNumber = row.createCell(0);
        Cell givenName = row.createCell(1);
        Cell middleName = row.createCell(2);
        Cell familyName = row.createCell(3);
        Cell email = row.createCell(4);
        Cell itemName = row.createCell(5);
        Cell amount = row.createCell(6);
        Cell currency = row.createCell(7);
        Cell timeCreated = row.createCell(8);
        Cell linkedObjectClass = row.createCell(9);

        amount.setCellValue(subject.amount);
        currency.setCellValue(subject.currency);

        if (user != null && idToUserData.containsKey(user.getId())) {
            UserData data = idToUserData.get(user.getId());
            registrationNumber.setCellValue(data.number);
        }

        if ("PaypalPayment".equals(subject.linkedObjectClass)) {
            linkedObjectClass.setCellValue("paypal");
        } else if ("StripeCharge".equals(subject.linkedObjectClass)) {
            linkedObjectClass.setCellValue("stripe");
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
            email.setCellValue(user.getEmail());

            Map<String, Object> metadata = user.getUserMetadata();

            if (metadata == null) {
                givenName.setCellValue(user.getGivenName());
                familyName.setCellValue(user.getFamilyName());
            } else {
                if (metadata.containsKey("given_name")) {
                    givenName.setCellValue(metadata.get("given_name").toString());
                }

                if (metadata.containsKey("family_name")) {
                    familyName.setCellValue(metadata.get("family_name").toString());
                }
            }
        }
    }

    public boolean isVendorTransaction(Transaction transaction) {
        return "PaypalPayment".equals(transaction.linkedObjectClass) ||
          "StripeCharge".equals(transaction.linkedObjectClass);
    }
}
