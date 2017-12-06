package com.jivecake.api.service;

import java.util.HashSet;
import java.util.Set;

import com.jivecake.api.model.EntityAsset;
import com.jivecake.api.model.OrganizationInvitation;
import com.jivecake.api.model.PaypalPaymentProfile;
import com.jivecake.api.model.UserInterfaceEvent;
import com.jivecake.api.request.Auth0UserUpdateEntity;
import com.jivecake.api.request.OrderData;
import com.jivecake.api.request.PaypalAuthorizationPayload;
import com.jivecake.api.request.StripeOAuthCode;
import com.jivecake.api.request.StripeOrderPayload;
import com.jivecake.api.request.UserEmailVerificationBody;

public class ValidationService {
    public static boolean isValid(PaypalAuthorizationPayload object) {
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
        boolean validPermissions;

        if (entity.permissions == null) {
            validPermissions = false;
        } else {
            Set<Integer> permissions = new HashSet<>(entity.permissions);
            permissions.remove(PermissionService.READ);
            permissions.remove(PermissionService.WRITE);
            validPermissions = permissions.isEmpty();
        }

        boolean validIncludeField = entity.include == PermissionService.ALL ||
            entity.include == PermissionService.EXCLUDE ||
            entity.include == PermissionService.INCLUDE;

        return validIncludeField &&
               validPermissions &&
               entity.email != null &&
               entity.email.contains("@");
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
        return entity != null && entity.order != null && !entity.order.isEmpty();
    }

    public static boolean isValid(StripeOrderPayload entity) {
        return entity != null &&
            entity.data != null &&
            entity.data.order != null &&
            !entity.data.order.isEmpty() &&
            entity.token != null;
    }
}