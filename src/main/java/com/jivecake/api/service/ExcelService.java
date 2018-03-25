package com.jivecake.api.service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.bson.types.ObjectId;

import com.auth0.json.mgmt.users.User;
import com.jivecake.api.model.Event;
import com.jivecake.api.model.FormField;
import com.jivecake.api.model.FormFieldResponse;
import com.jivecake.api.model.FormResponseExcelRow;
import com.jivecake.api.model.Item;
import com.jivecake.api.model.Transaction;

public class ExcelService {
    public static void writeResponsesToExcel(
        File file,
        List<Event> events,
        List<Item> items,
        List<FormFieldResponse> responses,
        List<FormField> fields,
        List<Transaction> transactions,
        List<User> users
    ) throws IOException {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Question Responses");
        sheet.createFreezePane(0, 1);

        String[] headers = {
            "Email",
            "Given Name",
            "Family Name",
            "Event",
            "Item",
            "Question",
            "Response"
        };

        Map<ObjectId, Event> eventToId = events
            .stream()
            .collect(Collectors.toMap(event -> event.id, Function.identity()));

        Map<ObjectId, Item> itemToId = items
            .stream()
            .collect(Collectors.toMap(item -> item.id, Function.identity()));

        Map<String, User> userToId = users
            .stream()
            .collect(Collectors.toMap(user -> user.getId(), Function.identity()));

        Map<ObjectId, FormField> fieldToId = fields
            .stream()
            .collect(Collectors.toMap(field -> field.id, Function.identity()));

        Map<ObjectId, FormFieldResponse> idToResponse = responses
            .stream()
            .collect(Collectors.toMap(response -> response.id, Function.identity()));

        Map<FormFieldResponse, List<User>> responsesToUsers = new HashMap<>();
        Map<FormFieldResponse, List<Transaction>> responsesToTransaction = new HashMap<>();

        for (Transaction transaction: transactions) {
            for (ObjectId responseId: transaction.formFieldResponseIds) {
                FormFieldResponse response = idToResponse.get(responseId);

                if (!responsesToTransaction.containsKey(response)) {
                    responsesToTransaction.put(response, new ArrayList<>());
                }

                responsesToTransaction.get(response).add(transaction);

                if (transaction.user_id != null) {
                    if (!responsesToUsers.containsKey(response)) {
                        responsesToUsers.put(response, new ArrayList<>());
                    }

                    User user = userToId.get(transaction.user_id);
                    responsesToUsers.get(response).add(user);
                }
            }
        }

        List<FormResponseExcelRow> rows = responses
            .stream()
            .map(response -> {
                FormResponseExcelRow row = new FormResponseExcelRow();

                row.response = response;
                row.field = fieldToId.get(response.formFieldId);

                if (row.field.item != null) {
                    row.item = itemToId.get(row.field.item);
                }

                row.event = eventToId.get(row.field.eventId);

                if (responsesToTransaction.containsKey(response)) {
                    List<Transaction> responseTransactions = responsesToTransaction.get(response);

                    if (!responseTransactions.isEmpty()) {
                        row.transaction = responseTransactions.get(0);
                    }
                }

                if (responsesToUsers.containsKey(response)) {
                    List<User> responseUsers = responsesToUsers.get(response);

                    if (!responseUsers.isEmpty()) {
                        row.user = responseUsers.get(0);
                    }
                }

                return row;
            })
            .collect(Collectors.toList());

        for (int index = 0; index < rows.size(); index++) {
            Row row = sheet.createRow(index + 1);
            FormResponseExcelRow responseRow = rows.get(index);

            Cell email = row.createCell(0);
            Cell givenName = row.createCell(1);
            Cell familyName = row.createCell(2);
            Cell item = row.createCell(3);
            Cell question = row.createCell(4);
            Cell response = row.createCell(5);

            String givenNameValue = TransactionService.getGivenName(
                responseRow.transaction,
                responseRow.user
            );

            String familyNameValue = TransactionService.getFamilyName(
                responseRow.transaction,
                responseRow.user
            );

            givenName.setCellValue(givenNameValue);
            familyName.setCellValue(familyNameValue);

            if (responseRow.user != null) {
                email.setCellValue(responseRow.user.getEmail());
            }

            item.setCellValue(responseRow.item.name);
            question.setCellValue(responseRow.field.label);

            if (responseRow.response.string != null) {
                response.setCellValue(responseRow.response.string);
            }

            if (responseRow.response.doubleValue != null) {
                response.setCellValue(responseRow.response.doubleValue);
            }

            if (responseRow.response.longValue != null) {
                response.setCellValue(responseRow.response.longValue);
            }
        }

        for (int index = 0; index < headers.length; index++) {
            sheet.autoSizeColumn(index);
        }

        FileOutputStream stream = new FileOutputStream(file);
        workbook.write(stream);
        stream.close();
        workbook.close();
    }
}
