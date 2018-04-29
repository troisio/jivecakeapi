package com.jivecake.api.service;

import java.util.HashSet;
import java.util.Set;

import org.bson.types.ObjectId;

import com.jivecake.api.model.EntityAsset;
import com.jivecake.api.model.FormField;
import com.jivecake.api.model.FormFieldResponse;
import com.jivecake.api.model.FormFieldType;
import com.jivecake.api.model.OrganizationInvitation;
import com.jivecake.api.model.PaypalPaymentProfile;
import com.jivecake.api.model.Transaction;
import com.jivecake.api.model.UserInterfaceEvent;
import com.jivecake.api.request.Auth0UserUpdateEntity;
import com.jivecake.api.request.EntityQuantity;
import com.jivecake.api.request.OrderData;
import com.jivecake.api.request.PaypalAuthorizationPayload;
import com.jivecake.api.request.StripeOAuthCode;
import com.jivecake.api.request.StripeOrderPayload;
import com.jivecake.api.request.TransactionResponse;
import com.jivecake.api.request.UserEmailVerificationBody;

import jersey.repackaged.com.google.common.base.Objects;

public class ValidationService {
    public static boolean isValid(PaypalAuthorizationPayload object) {
        if (object.responses == null) {
            return false;
        }

        for (FormFieldResponse response: object.responses) {
            if (!ValidationService.isValid(response)) {
                return false;
            }
        }

        return object != null && object.payerID != null && object.paymentID != null;
    }

    public static boolean isValid(Auth0UserUpdateEntity entity) {
        return entity.email != null && entity.email.contains("@") && entity.email.length() <= 100 &&
            (entity.user_metadata.given_name == null || entity.user_metadata.given_name.length() <= 100) &&
            (entity.user_metadata.family_name == null || entity.user_metadata.family_name.length() <= 100);
    }

    public static boolean isValid(UserInterfaceEvent entity) {
        boolean validParameters;

        if (entity.parameters == null) {
            validParameters = true;
        } else {
            Set<String> keys = new HashSet<>(entity.parameters.keySet());
            keys.remove("duration");
            validParameters = keys.isEmpty();
        }

        return "cacheUserData".equals(entity.event) &&
            (entity.ip == null || entity.ip.length() <= 100) &&
            (entity.event == null || entity.event.length() <= 100) &&
            (entity.agent == null || entity.agent.length() <= 100) &&
            (entity.userId == null || entity.userId.length() <= 100) &&
            validParameters;
    }

    public static boolean isValid(OrganizationInvitation entity) {
        return entity.email != null &&
               entity.email.contains("@") &&
               (entity.write || entity.read);
    }

    public static boolean isValid(EntityAsset entity) {
        return entity != null &&
            entity.name != null &&
            entity.name.length() > 0 &&
            entity.name.length() <= 100 &&
            entity.data != null;
    }

    public static boolean isValid(UserEmailVerificationBody entity) {
        return entity != null && entity.user_id != null;
    }

    public static boolean isValid(PaypalPaymentProfile entity) {
        return entity != null &&
            entity.email != null &&
            entity.email.contains("@") &&
            entity.email.length() <= 100;
    }

    public static boolean isValid(StripeOAuthCode entity) {
        return entity != null && entity.code != null;
    }

    public static boolean isValid(OrderData entity) {
        if (entity.responses == null) {
            return false;
        }

        Set<ObjectId> formFieldIds = new HashSet<>();

        for (FormFieldResponse response: entity.responses) {
            if (ValidationService.isValid(response)) {
                formFieldIds.add(response.formFieldId);
            } else {
                return false;
            }
        }

        if (entity.order == null) {
            return false;
        }

        for (EntityQuantity<?> subject: entity.order) {
            if (!ValidationService.isValid(subject)) {
                return false;
            }
        }

        if (formFieldIds.size() != entity.responses.size()) {
            return false;
        }

        return entity != null && entity.order != null && !entity.order.isEmpty();
    }

    public static boolean isValid(StripeOrderPayload entity) {
        return entity != null &&
            entity.data != null &&
            entity.token != null &&
            ValidationService.isValid(entity.data);
    }

    public static boolean isValid(FormField field) {
        if (field.type == FormFieldType.SELECTION) {
            if (field.options == null) {
                return false;
            }

            if (field.options.size() > 10 || field.options.size() < 2) {
                return false;
            }

            for (String option: field.options) {
                if (option == null || option.length() > 100 || option.isEmpty()) {
                    return false;
                }
            }

            boolean hasDuplicates = new HashSet<>(field.options).size() != field.options.size();

            if (hasDuplicates) {
                return false;
            }
        } else {
            if (field.options != null) {
                return false;
            }
        }

        boolean validType =
            field.type == FormFieldType.NON_NEGATIVE_INTEGER ||
            field.type == FormFieldType.INTEGER ||
            field.type == FormFieldType.NUMBER ||
            field.type == FormFieldType.TEXT ||
            field.type == FormFieldType.DATE ||
            field.type == FormFieldType.DATE_AND_TIME ||
            field.type == FormFieldType.TIME ||
            field.type == FormFieldType.SELECTION;

        return validType && field.label != null && field.label.length() < 100 && !field.label.isEmpty();
    }

    public static boolean isValid(FormFieldResponse response) {
        int nullCount = 0;

        if (response.doubleValue == null) {
            nullCount += 1;
        }

        if (response.string == null) {
            nullCount += 1;
        }

        if (response.longValue == null) {
            nullCount += 1;
        }

        return response.formFieldId != null && nullCount > 1;
    }

    public static boolean isValid(FormFieldResponse response, FormField field) {
        if (!Objects.equal(response.formFieldId, field.id)) {
            return false;
        }

        boolean isNullValue = response.doubleValue == null &&
            response.string == null &&
            response.longValue == null;

        if (isNullValue) {
            return !field.required;
        } else {
            if (field.options == null) {
                if (field.type == FormFieldType.DATE) {
                    return response.longValue != null;
                }

                if (field.type == FormFieldType.DATE_AND_TIME) {
                    return response.longValue != null;
                }

                if (field.type == FormFieldType.INTEGER) {
                    return response.longValue != null;
                }

                if (field.type == FormFieldType.NUMBER) {
                    return response.doubleValue != null;
                }

                if (field.type == FormFieldType.NON_NEGATIVE_INTEGER) {
                    return response.longValue != null && response.longValue >= 0;
                }

                if (field.type == FormFieldType.SELECTION) {
                    return field.options.contains(response.string);
                }

                if (field.type == FormFieldType.TEXT) {
                    return response.string != null &&
                        response.string.length() > 0;
                }

                if (field.type == FormFieldType.TIME) {
                    return response.longValue != null;
                }
            } else if (field.type == FormFieldType.SELECTION) {
                return field.options.contains(response.string);
            }
        }

        return true;
    }

    public static boolean isValid(TransactionResponse object) {
        if (object.responses == null) {
            return false;
        }

        for (FormFieldResponse response: object.responses) {
            if (!ValidationService.isValid(response)) {
                return false;
            }
        }

        return ValidationService.isValid(object.transaction);
    }

    public static boolean isValid(Transaction transaction) {
        return transaction != null &&
            transaction.quantity > 0 &&
            (transaction.given_name == null || transaction.given_name.length() <= 100) &&
            (transaction.middleName == null || transaction.middleName.length() <= 100) &&
            (transaction.family_name == null || transaction.family_name.length() <= 100) &&
            (transaction.email == null || (transaction.email.contains("@") && transaction.email.length() <= 100)) &&
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

    public static boolean isValid(EntityQuantity<?> entity) {
        return entity.entity != null && entity.quantity > 0;
    }
}