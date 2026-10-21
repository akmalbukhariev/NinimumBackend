package com.ninimum.api.admin.access;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class StaffAccessTest {
    static final String A = "/ninimum/api/v1/admin";
    boolean ok(String role, String method, String uri) { return StaffAccess.allows(StaffAccess.effective(role, null, null), StaffAccess.required(method, uri)); }

    @Test void existingRestrictedAdminKeepsExactlyTheOldPages() {
        for (String uri : new String[]{A+"/me", A+"/warehouse/status", A+"/management/orders", A+"/management/orders/5", A+"/management/orders/problems",
                A+"/management/products/3", A+"/management/categories", A+"/product/createProduct", A+"/category/getCategories",
                A+"/management/delivery/jobs", "/ninimum/api/v1/delivery-app/admin/workers"})
            assertTrue(ok("ADMIN", "GET", uri), uri);
        assertTrue(ok("ADMIN", "PUT", A+"/management/orders/5/status"));
        for (String uri : new String[]{A+"/dashboard", A+"/management/customers", A+"/management/settings", A+"/management/admins",
                A+"/warehouse/stock", A+"/warehouse/suppliers", A+"/management/subscriptions", A+"/management/access"})
            assertFalse(ok("ADMIN", "GET", uri), uri);
    }

    @Test void ownerOnlyActionsStayOwnerOnlyForEveryRole() {
        for (String role : StaffAccess.ROLES.keySet())
            for (String uri : new String[]{A+"/management/orders/5/correction", A+"/management/customers/2/merge", A+"/management/customers/duplicates",
                    A+"/management/settings", A+"/management/admins", A+"/management/access", A+"/register", A+"/management/order-timers",
                    A+"/warehouse/inventory/settings", A+"/management/security/login-audit", A+"/management/dispatch/history", A+"/management/unknown"})
                assertFalse(ok(role, "POST", uri), role + " " + uri);
    }

    @Test void observerSeesButChangesNothing() {
        assertTrue(ok("OBSERVER", "GET", A+"/management/customers"));
        assertTrue(ok("OBSERVER", "GET", A+"/warehouse/inventory/balances"));
        assertFalse(ok("OBSERVER", "PUT", A+"/management/customers/1/active"));
        assertFalse(ok("OBSERVER", "POST", A+"/warehouse/receipts/1/post"));
        assertFalse(StaffAccess.effective("OBSERVER", null, null).contains(StaffAccess.MONEY));
    }

    @Test void operatorAndPurchasingFollowTzV10() {
        assertTrue(ok("OPERATOR", "PUT", A+"/management/orders/1/status"));
        assertTrue(ok("OPERATOR", "PUT", A+"/management/questions/1/answer"));
        assertFalse(ok("OPERATOR", "GET", A+"/warehouse/receipts"));
        assertFalse(ok("OPERATOR", "PUT", A+"/management/products/1"));
        assertTrue(ok("PURCHASING", "POST", A+"/warehouse/suppliers/1/payment-records"));
        assertTrue(ok("PURCHASING", "POST", A+"/warehouse/receipts"));
        assertFalse(ok("PURCHASING", "POST", A+"/warehouse/receipts/1/post"));
        assertFalse(ok("PURCHASING", "GET", A+"/management/orders"));
        assertTrue(ok("DIRECTOR", "POST", A+"/warehouse/inventory/documents/4/approve"));
        assertTrue(StaffAccess.effective("DIRECTOR", null, null).containsAll(Set.of(StaffAccess.MONEY, StaffAccess.APPROVE, StaffAccess.PRICES)));
    }

    @Test void ownerAdditionsAndRemovalsChangeTheDefaults() {
        var p = StaffAccess.effective("OPERATOR", "customers.view,approve,unknown.x", "orders.edit");
        assertTrue(p.contains("approve"));assertFalse(p.contains("unknown.x"));
        assertFalse(p.contains("orders.edit"));assertTrue(p.contains("orders.view"), "edit removed, view kept");
        assertEquals(StaffAccess.effective("ADMIN", null, null), StaffAccess.effective(null, null, null), "unknown role falls back to ADMIN");
    }
}
