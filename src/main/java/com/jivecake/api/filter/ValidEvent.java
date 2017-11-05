package com.jivecake.api.filter;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import javax.validation.Constraint;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import javax.validation.Payload;

import com.jivecake.api.model.Event;
import com.jivecake.api.service.EventService;
import com.jivecake.api.service.TransactionService;

@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = {ValidEvent.Validator.class})
public @interface ValidEvent {
    String message() default "";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};

    public class Validator implements ConstraintValidator<ValidEvent, Event>  {
        @Override
        public void initialize(ValidEvent validEvent) {
        }

        @Override
        public boolean isValid(Event event, ConstraintValidatorContext context) {
            return event.name != null &&
                    event.name.length() > 0 &&
                    event.name.length() <= 100 &&
                    (
                        event.description == null || (
                        event.description.length() >= 0 &&
                        event.description.length() <= 1000 )
                    ) &&
                    (
                        event.status == EventService.STATUS_INACTIVE ||
                        event.status == EventService.STATUS_ACTIVE
                    ) &&
                    (
                        event.paymentProfileId == null ||
                        TransactionService.CURRENCIES.contains(event.currency)
                    ) && (
                        event.websiteUrl == null ||
                        event.websiteUrl.startsWith("https://") ||
                        event.websiteUrl.startsWith("http://")
                    ) && (
                        event.twitterUrl == null ||
                        event.twitterUrl.startsWith("https://twitter.com/")
                    ) && (
                        event.previewImageUrl == null ||
                        event.previewImageUrl.startsWith("https://") ||
                        event.previewImageUrl.startsWith("http://")
                    ) && (
                        event.facebookEventId == null ||
                        event.facebookEventId.matches("\\d+")
                    );
        }
    }
}