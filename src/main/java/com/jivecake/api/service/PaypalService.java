package com.jivecake.api.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.InvocationCallback;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

import org.bson.types.ObjectId;
import org.mongodb.morphia.Datastore;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jivecake.api.model.Item;
import com.jivecake.api.model.PaymentDetail;
import com.jivecake.api.model.PaypalIPN;
import com.jivecake.api.model.PaypalItemPayment;
import com.jivecake.api.model.Transaction;

public class PaypalService {
    private final Datastore datastore;
    private final TransactionService transactionService;
    private final ObjectMapper mapper = new ObjectMapper();
    private final List<String> itemPaymentFields = Arrays.asList(
        "mc_gross",
        "quantity",
        "item_name",
        "item_number",
        "mc_shipping",
        "mc_handling",
        "tax"
    );

    private final String itemPaymentFieldRegex = this.itemPaymentFields.stream().map(subject -> {
        String regexComponent = subject;

        if (subject.equals("mc_gross")) {
            regexComponent += "_";
        }

        return String.format("(%s\\d+)", regexComponent);
    }).collect(Collectors.joining("|"));

    @Inject
    public PaypalService(
        Datastore datastore,
        NotificationService notificationService,
        TransactionService transactionService
    ) {
        this.datastore = datastore;
        this.transactionService = transactionService;
    }

    public Future<Response> isValidIPN(MultivaluedMap<String, String> paramaters, String paypalUrl, InvocationCallback<Response> callback) {
        String url = String.format("%s?cmd=_notify-validate", paypalUrl);

        MultivaluedMap<String, String> form = new MultivaluedHashMap<>();
        form.putAll(paramaters);

        return ClientBuilder.newClient()
            .target(url)
            .request()
            .buildPost(Entity.form(form))
            .submit(callback);
    }

    public String getSandboxURL() {
        return "https://www.sandbox.paypal.com/cgi-bin/webscr";
    }

    public String getIPNUrl() {
        return "https://www.paypal.com/cgi-bin/webscr";
    }

    public String getVerified() {
        return "VERIFIED";
    }

    public String getInvalid() {
        return "INVALID";
    }

    public PaypalIPN create(Map<String, List<String>> parameters) {
        Map<String, String> copy = parameters.entrySet()
            .stream()
            .collect(
                Collectors.toMap(
                    Map.Entry::getKey,
                    entry -> {
                        List<String> list = entry.getValue();
                        return list.isEmpty() ? "" : list.get(0);
                    }
                )
            );

        boolean hasNumCartItems = copy.containsKey("num_cart_items");
        List<PaypalItemPayment> payments = new ArrayList<>();

        if (hasNumCartItems) {
            String num_cart_items = copy.get("num_cart_items");
            int itemCount = Integer.parseInt(num_cart_items);

            for (int index = 1; index <= itemCount; index++) {
                PaypalItemPayment payment = new PaypalItemPayment();

                for (String fieldPrefix: itemPaymentFields) {
                    String field = fieldPrefix;

                    if (fieldPrefix.equals("mc_gross")) {
                        field += "_";
                    }

                    field += index;

                    String value = copy.get(field);

                    if (fieldPrefix.equals("mc_gross")) {
                        payment.mc_gross = value;
                    } else if (fieldPrefix.equals("quantity")) {
                        payment.quantity = value;
                    } else if (fieldPrefix.equals("item_name")) {
                        payment.item_name = value;
                    } else if (fieldPrefix.equals("item_number")) {
                        payment.item_number = value;
                    } else if (fieldPrefix.equals("mc_shipping")) {
                        payment.mc_shipping = value;
                    } else if (fieldPrefix.equals("mc_handling")) {
                        payment.mc_handling = value;
                    } else if (fieldPrefix.equals("tax")) {
                        payment.tax = value;
                    }
                }

                payments.add(payment);
            }
        } else {
            PaypalItemPayment payment = new PaypalItemPayment();
            payment.mc_gross = copy.get("mc_gross");
            payment.quantity = copy.get("quantity");
            payment.item_name = copy.get("item_name");
            payment.item_number = copy.get("item_number");
            payment.mc_shipping = copy.get("mc_shipping");
            payment.mc_handling = copy.get("mc_handling");
            payment.tax = copy.get("tax");

            payments.add(payment);
        }

        Set<String> removeKeys = copy.keySet().stream().filter(subject -> subject.matches(this.itemPaymentFieldRegex)).collect(Collectors.toSet());
        copy.keySet().removeAll(removeKeys);

        String json;

        try {
            json = this.mapper.writeValueAsString(copy);
        } catch (JsonProcessingException e) {
            json = null;
            e.printStackTrace();
        }

        PaypalIPN result = null;

        if (json != null) {
            try {
                result = this.mapper.readValue(json, PaypalIPN.class);
                result.payments = payments;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return result;
    }

    public List<Transaction> processTransactions(PaypalIPN ipn) {
        PaypalIPN previousPendingIpn = this.datastore.createQuery(PaypalIPN.class)
            .field("txn_id").equal(ipn.txn_id)
            .field("payment_status").equal("Pending")
            .get();

        boolean isCompletionOfPreviousPendingTransaction = "Completed".equals(ipn.payment_status) &&
            previousPendingIpn != null;

        List<Transaction> result;

        if (isCompletionOfPreviousPendingTransaction) {
            List<Transaction> previousPendingTransactions = this.datastore.find(Transaction.class)
                .field("linkedObjectClass").equal(PaypalIPN.class.getSimpleName())
                .field("linkedId").equal(previousPendingIpn.id)
                .asList();

            for (Transaction pendingTransaction: previousPendingTransactions) {
                pendingTransaction.status = TransactionService.SETTLED;
            }

            result = previousPendingTransactions;
        } else {
            Date currentDate = new Date();
            ObjectId custom;

            try {
                custom = new ObjectId(ipn.custom);
            } catch (IllegalArgumentException e) {
                custom = null;
            }

            PaymentDetail details = this.datastore.find(PaymentDetail.class)
                .field("custom").equal(custom)
                .get();

            List<Transaction> transactions = new ArrayList<>();

            for (PaypalItemPayment payment: ipn.payments) {
                Transaction transaction = new Transaction();
                transaction.currency = ipn.mc_currency;
                transaction.timeCreated = currentDate;
                transaction.linkedId = ipn.id;
                transaction.linkedObjectClass = PaypalIPN.class.getSimpleName();
                transaction.quantity = payment.quantity == null ? 0 : new Long(payment.quantity);
                transaction.leaf = true;

                try {
                    transaction.amount = Double.parseDouble(ipn.mc_gross);
                } catch (NumberFormatException e) {
                }

                ObjectId id;

                try {
                    id = new ObjectId(payment.item_number);
                } catch (IllegalArgumentException e) {
                    id = null;
                }

                Item item = this.datastore.get(Item.class, id);

                if (item == null) {
                    transaction.paymentStatus = TransactionService.PAYMENT_UNKNOWN;
                    transaction.status = TransactionService.SETTLED;
                } else {
                    Double amount;

                    if (item.countAmounts != null) {
                        long count = this.transactionService.getTransactionsForItemTotal(item.id)
                            .stream()
                            .map(subject -> subject.quantity)
                            .reduce(0L, Long::sum);

                        amount = item.getDerivedAmountFromCounts(count);
                    } else if (item.timeAmounts != null) {
                        amount = item.getDerivedAmountFromTime(currentDate);
                    } else {
                        amount = item.amount;
                    }

                    double amountPaid = Double.parseDouble(payment.mc_gross);
                    double difference = amountPaid - amount * transaction.quantity;

                    if (difference < 0.01 && difference > -0.01) {
                        transaction.paymentStatus = TransactionService.PAYMENT_EQUAL;
                    } else if (difference >= 0.01) {
                        transaction.paymentStatus = TransactionService.PAYMENT_LESS_THAN;
                    } else {
                        transaction.paymentStatus = TransactionService.PAYMENT_GREATER_THAN;
                    }

                    if ("Pending".equals(ipn.payment_status)) {
                        transaction.status = TransactionService.PENDING;
                    } else if ("Completed".equals(ipn.payment_status)) {
                        transaction.status = TransactionService.SETTLED;
                    } else {
                        transaction.status = TransactionService.UNKNOWN;
                    }

                    transaction.amount = amountPaid;
                }

                if (details == null || details.user_id == null) {
                    transaction.email = ipn.payer_email;
                    transaction.given_name = ipn.first_name;
                    transaction.family_name = ipn.last_name;
                } else {
                    transaction.user_id = details.user_id;
                }

                PaypalIPN parentIpn = this.datastore.createQuery(PaypalIPN.class)
                    .field("txn_id").equal(ipn.parent_txn_id)
                    .get();

                if (parentIpn != null) {
                    Transaction parentTransaction = this.datastore.createQuery(Transaction.class)
                        .field("linkedId").equal(parentIpn.id)
                        .field("linkedObjectClass").equal(PaypalIPN.class.getSimpleName())
                        .get();

                    parentTransaction.leaf = false;

                    if (parentTransaction != null) {
                        transaction.parentTransactionId = parentTransaction.id;
                    }

                    transactions.add(parentTransaction);
                }

                transaction.itemId = item.id;
                transaction.eventId = item.eventId;
                transaction.organizationId = item.organizationId;

                transactions.add(transaction);
            }

            boolean transactionsWereSigned = details != null;

            if (transactionsWereSigned) {
                result = transactions;
            } else {
                result = new ArrayList<>();
            }
        }

        return result;
    }

    public List<Transaction> processRefund(PaypalIPN ipn) {
        ObjectId custom;

        try {
            custom = new ObjectId(ipn.custom);
        } catch (IllegalArgumentException e) {
            custom = null;
        }

        PaymentDetail details = this.datastore.find(PaymentDetail.class)
            .field("custom").equal(custom)
            .get();

        PaypalIPN parentIpn = this.datastore.createQuery(PaypalIPN.class)
            .field("txn_id").equal(ipn.parent_txn_id)
            .get();

        List<Transaction> result = new ArrayList<>();

        if (parentIpn != null) {
            List<Transaction> parentTransactions = this.datastore.createQuery(Transaction.class)
                .field("linkedId").equal(parentIpn.id)
                .field("linkedObjectClass").equal(PaypalIPN.class.getSimpleName())
                .asList();

            for (Transaction transaction: parentTransactions) {
                transaction.leaf = false;
            }

            List<Transaction> refundTransactions = parentTransactions.stream().map(parentTransaction -> {
                Transaction transaction = new Transaction();
                transaction.quantity = parentTransaction.quantity;
                transaction.currency = ipn.mc_currency;
                transaction.linkedId = ipn.id;
                transaction.linkedObjectClass = PaypalIPN.class.getSimpleName();
                transaction.parentTransactionId = parentTransaction.id;
                transaction.itemId = parentTransaction.itemId;
                transaction.eventId = parentTransaction.eventId;
                transaction.organizationId = parentTransaction.organizationId;
                transaction.email = ipn.payer_email;
                transaction.given_name = ipn.first_name;
                transaction.family_name = ipn.last_name;
                transaction.status = TransactionService.REFUNDED;
                transaction.paymentStatus = TransactionService.PAYMENT_UNKNOWN;
                transaction.leaf = true;
                transaction.timeCreated = new Date();

                try {
                    transaction.amount = Double.parseDouble(ipn.mc_gross);
                } catch (NumberFormatException e) {
                }

                try {
                    transaction.itemId = new ObjectId(ipn.item_number);
                } catch (IllegalArgumentException e) {
                }

                if (details != null) {
                    transaction.user_id = details.user_id;
                }

                return transaction;
            })
            .collect(Collectors.toList());

            boolean canUniquelyDetermineRefundTransaction = refundTransactions.size() == 1;
            double refundedAmount = 0;

            try {
                refundedAmount = Double.parseDouble(ipn.mc_gross);
            } catch (NumberFormatException e) {
                throw e;
            }

            double parentTransactionsTotal = parentTransactions.stream()
                .map(transaction -> transaction.quantity * transaction.amount)
                .reduce(0.0, Double::sum);

            boolean refundedAmountEqualsTotalPaid = Math.abs(parentTransactionsTotal - refundedAmount) < 0.01;

            if (canUniquelyDetermineRefundTransaction || refundedAmountEqualsTotalPaid) {
                result.addAll(parentTransactions);
                result.addAll(refundTransactions);
            }
        }

        return result;
    }
}