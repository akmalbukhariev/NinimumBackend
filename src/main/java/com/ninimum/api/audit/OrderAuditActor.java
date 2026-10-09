package com.ninimum.api.audit;

import org.springframework.security.core.context.SecurityContextHolder;

/** Audit identity comes from the authenticated session, never request body fields. */
public final class OrderAuditActor {
    private OrderAuditActor() {}
    public static String current() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) return "SYSTEM";
        return auth.getName();
    }
}
