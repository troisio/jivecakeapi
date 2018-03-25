package com.jivecake.api.filter;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import javax.validation.Constraint;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import javax.validation.Payload;

import com.jivecake.api.model.Event;
import com.jivecake.api.model.FormField;
import com.jivecake.api.model.FormFieldResponse;
import com.jivecake.api.model.Item;
import com.jivecake.api.model.Organization;
import com.jivecake.api.model.OrganizationInvitation;
import com.jivecake.api.model.PaypalPaymentProfile;
import com.jivecake.api.model.Transaction;
import com.jivecake.api.model.UserInterfaceEvent;
import com.jivecake.api.request.Auth0UserUpdateEntity;
import com.jivecake.api.request.OrderData;
import com.jivecake.api.request.PaypalAuthorizationPayload;
import com.jivecake.api.request.StripeOAuthCode;
import com.jivecake.api.request.StripeOrderPayload;
import com.jivecake.api.request.TransactionResponse;
import com.jivecake.api.request.UserEmailVerificationBody;
import com.jivecake.api.service.EventService;
import com.jivecake.api.service.ItemService;
import com.jivecake.api.service.OrganizationService;
import com.jivecake.api.service.ValidationService;

@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = {ValidEntity.Validator.class})
public @interface ValidEntity {
    String message() default "";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};

    public class Validator implements ConstraintValidator<ValidEntity, Object>  {
        @Override
        public void initialize(ValidEntity validEvent) {
        }

        @Override
        public boolean isValid(Object object, ConstraintValidatorContext context) {
            if (object instanceof Event) {
                return EventService.isValidEvent((Event)object);
            }

            if (object instanceof Item) {
                return ItemService.isValid((Item)object);
            }

            if (object instanceof Organization) {
                return OrganizationService.isValid((Organization)object);
            }

            if (object instanceof Transaction) {
                return ValidationService.isValid((Transaction)object);
            }

            if (object instanceof UserEmailVerificationBody) {
                return ValidationService.isValid((UserEmailVerificationBody)object);
            }

            if (object instanceof Auth0UserUpdateEntity) {
                return ValidationService.isValid((Auth0UserUpdateEntity)object);
            }

            if (object instanceof UserInterfaceEvent) {
                return ValidationService.isValid((UserInterfaceEvent)object);
            }

            if (object instanceof OrganizationInvitation) {
                return ValidationService.isValid((OrganizationInvitation)object);
            }

            if (object instanceof StripeOAuthCode) {
                return ValidationService.isValid((StripeOAuthCode)object);
            }

            if (object instanceof PaypalPaymentProfile) {
                return ValidationService.isValid((PaypalPaymentProfile)object);
            }

            if (object instanceof PaypalAuthorizationPayload) {
                return ValidationService.isValid((PaypalAuthorizationPayload)object);
            }

            if (object instanceof OrderData) {
                return ValidationService.isValid((OrderData)object);
            }

            if (object instanceof StripeOrderPayload) {
                return ValidationService.isValid((StripeOrderPayload)object);
            }

            if (object instanceof FormFieldResponse) {
                return ValidationService.isValid((FormFieldResponse)object);
            }

            if (object instanceof FormField) {
                return ValidationService.isValid((FormField)object);
            }

            if (object instanceof TransactionResponse) {
                return ValidationService.isValid((TransactionResponse)object);
            }

            return true;
        }
    }
}