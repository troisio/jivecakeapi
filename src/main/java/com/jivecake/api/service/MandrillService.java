package com.jivecake.api.service;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;

import com.jivecake.api.APIConfiguration;
import com.jivecake.api.model.Event;
import com.jivecake.api.model.Item;
import com.jivecake.api.model.Transaction;
import com.paypal.api.payments.Payment;
import com.stripe.model.Token;

public class MandrillService {
    private final APIConfiguration apiConfiguration;

    @Inject
    public MandrillService(APIConfiguration apiConfiguration) {
        this.apiConfiguration = apiConfiguration;
    }

    public Future<Response> send(Map<String, Object> message) {
        Map<String, Object> body = new HashMap<>();
        body.put("key", this.apiConfiguration.mandrill.key);
        body.put("message", message);

        Future<Response> future;

        if (this.apiConfiguration.mandrill.mock) {
            CompletableFuture<Response> completeable = new CompletableFuture<>();
            completeable.complete(Response.ok().build());
            future = completeable;
            System.out.println(message);
        } else {
            future = ClientBuilder.newClient()
                .target("https://mandrillapp.com/api/1.0/messages/send.json")
                .request()
                .buildPost(Entity.json(body))
                .submit();
        }

        return future;
    }

    public Map<String, Object> getTransactionConfirmation(Event event, List<Item> items, List<Transaction> transactions) {
        Map<String, Object> message = new HashMap<>();

        String transactionLines = transactions.stream().map(transaction -> {
            Item foundItem = items.stream().filter(item -> transaction.itemId.equals(item.id)).findFirst().get();
            return String.format("<p><strong>%s</strong> (%s)</p>", foundItem.name, transaction.quantity);
        }).collect(Collectors.joining("\n"));

        message.put(
            "html",
            String.format("<p>Your payment for <strong>%s</strong> has been received.</p>", event.name) + "<h3>Purchased Items</h3>" + transactionLines
        );
        message.put("subject", event.name);
        message.put("from_email", "noreply@jivecake.com");
        message.put("from_name", "JiveCake");

        return message;
    }

    public Map<String, Object> getTransactionConfirmation(Payment payment, Event event, List<Item> items, List<Transaction> transactions) {
        Map<String, Object> message = this.getTransactionConfirmation(event, items, transactions);

        Map<String, String> to = new HashMap<>();
        to.put("email", payment.getPayer().getPayerInfo().getEmail());
        to.put("type", "to");

        message.put("to", Arrays.asList(to));
        return message;
    }

    public Map<String, Object> getTransactionConfirmation(Token token, Event event, List<Item> items, List<Transaction> transactions) {
        Map<String, Object> message = this.getTransactionConfirmation(event, items, transactions);

        Map<String, String> to = new HashMap<>();
        to.put("email", token.getEmail());
        to.put("type", "to");

        message.put("to", Arrays.asList(to));
        return message;
    }
}
