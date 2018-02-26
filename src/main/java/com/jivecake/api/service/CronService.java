package com.jivecake.api.service;

import java.lang.reflect.Method;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import com.jivecake.api.cron.Hourly;

import io.sentry.SentryClient;
import io.sentry.event.Event;
import io.sentry.event.EventBuilder;
import io.sentry.event.interfaces.ExceptionInterface;

public class CronService {
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final SentryClient sentry;
    private final Hourly hourly;

    @Inject
    public CronService(
        SentryClient sentry,
        Hourly hourly
    ) {
        this.sentry = sentry;
        this.hourly = hourly;
    }

    public void start() {
        this.executor.scheduleAtFixedRate(() -> {
            for (Method method: Hourly.class.getDeclaredMethods()) {
                try {
                    method.invoke(this.hourly);
                } catch (Exception exception) {
                    try {
                        this.sentry.sendEvent(
                            new EventBuilder()
                                .withEnvironment(this.sentry.getEnvironment())
                                .withMessage(exception.getMessage())
                                .withLevel(Event.Level.ERROR)
                                .withSentryInterface(new ExceptionInterface(exception))
                                .build()
                        );
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }

            try {
                this.sentry.sendEvent(
                    new EventBuilder()
                    .withEnvironment(this.sentry.getEnvironment())
                    .withMessage("Finished hourly cron")
                    .withLevel(Event.Level.INFO)
                    .build()
                );
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 0, 1, TimeUnit.HOURS);
    }
}
