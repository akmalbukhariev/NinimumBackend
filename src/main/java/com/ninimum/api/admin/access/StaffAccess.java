package com.ninimum.api.admin.access;

import com.ninimum.api.constants.Constant;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import java.util.*;

/**
 * M14 (TZ v10 §10): web admin staff roles and per-employee permissions. The owner (SUPER_ADMIN) can do everything.
 * A staff account keeps authority ADMIN; its role gives default permissions, and the owner can add or remove single ones.
 * Permissions travel as "PERM:x" authorities set by the JWT filter on every request.
 */
public final class StaffAccess {
    private StaffAccess() {}
    public static final String PREFIX = "PERM:";
    /** Added by the JWT filter with the permissions; an ADMIN without it gets the restricted-admin defaults. */
    public static final String LOADED = PREFIX + "*";
    public static final String MONEY = "money.view", APPROVE = "approve", PRICES = "prices.edit";
    /** Any signed-in staff member may call it. */
    public static final String ANY = "";
    public static final List<String> AREAS = List.of("dashboard","problems","orders","customers","products","categories","subscriptions",
            "promotions","reviews","delivery","warehouse","suppliers","inventory");
    public static final List<String> ALL;
    public static final Map<String,Set<String>> ROLES;
    static {
        var all = new ArrayList<String>();
        for (String area : AREAS) { all.add(area + ".view"); all.add(area + ".edit"); }
        all.addAll(List.of(MONEY, APPROVE, PRICES));
        ALL = List.copyOf(all);
        var roles = new LinkedHashMap<String,Set<String>>();
        // ADMIN: the restricted administrator before M14; keeps exactly the pages and actions it had.
        roles.put("ADMIN", Set.of("problems.view","orders.edit","products.edit","categories.edit","delivery.edit",PRICES));
        var director = new LinkedHashSet<String>();
        for (String area : AREAS) director.add(area + ".edit");
        director.addAll(List.of(MONEY, APPROVE, PRICES));
        roles.put("DIRECTOR", Set.copyOf(director));
        roles.put("PURCHASING", Set.of("dashboard.view","suppliers.edit","warehouse.edit","inventory.view","products.view","categories.view"));
        roles.put("OPERATOR", Set.of("dashboard.view","problems.view","orders.edit","customers.edit","delivery.edit","reviews.edit","products.view","categories.view"));
        var observer = new LinkedHashSet<String>();
        for (String area : AREAS) observer.add(area + ".view");
        roles.put("OBSERVER", Set.copyOf(observer));
        ROLES = Collections.unmodifiableMap(roles);
    }

    /** Role defaults plus the owner's additions, minus removals; "x.edit" includes "x.view". */
    public static Set<String> effective(String role, String added, String removed) {
        var result = withViews(ROLES.getOrDefault(role == null ? "ADMIN" : role, ROLES.get("ADMIN")));
        result.addAll(split(added));
        result.removeAll(split(removed));
        result = withViews(result);
        result.retainAll(ALL);
        return result;
    }
    private static TreeSet<String> withViews(Collection<String> permissions) {
        var result = new TreeSet<>(permissions);
        for (String p : permissions) if (p.endsWith(".edit")) result.add(p.substring(0, p.length() - 5) + ".view");
        return result;
    }
    public static Set<String> split(String value) {
        var set = new LinkedHashSet<String>();
        if (value != null) for (String p : value.split(",")) if (!p.isBlank()) set.add(p.trim());
        return set;
    }

    public static boolean isOwner() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> Constant.ROLE_SUPER_ADMIN.equals(a.getAuthority()));
    }
    /** True for the owner, or a staff member holding the permission ("x.view" is also met by "x.edit"). */
    public static boolean can(String permission) {
        if (isOwner()) return true;
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        var held = new HashSet<String>();
        for (GrantedAuthority a : auth.getAuthorities()) held.add(a.getAuthority());
        if (held.contains(Constant.ROLE_ADMIN) && !held.contains(LOADED)) return allows(effective("ADMIN", null, null), permission);
        return allowsAuthorities(held, permission);
    }
    private static boolean allowsAuthorities(Set<String> authorities, String permission) {
        if (permission == null) return false;
        if (permission.isEmpty()) return true;
        if (authorities.contains(PREFIX + permission)) return true;
        return permission.endsWith(".view") && authorities.contains(PREFIX + permission.substring(0, permission.length() - 5) + ".edit");
    }
    public static boolean allows(Collection<String> permissions, String required) {
        var authorities = new HashSet<String>();
        for (String p : permissions) authorities.add(PREFIX + p);
        return allowsAuthorities(authorities, required);
    }

    private static final AntPathMatcher PATHS = new AntPathMatcher();
    private static final String API = "/ninimum/api/v1";
    // First match wins. AREA:x → x.view for GET, x.edit otherwise. Anything not listed is owner-only.
    private static final List<String[]> RULES = List.of(
        r("/admin/me", "ANY"), r("/admin/warehouse/status", "ANY"),
        r("/admin/dashboard", "AREA:dashboard"),
        r("/admin/management/orders/problems/**", "AREA:problems"),
        r("/admin/management/orders/*/correction", "OWNER"),
        r("/admin/management/orders/**", "AREA:orders"),
        r("/admin/management/customers/duplicates", "OWNER"), r("/admin/management/customers/*/merge", "OWNER"),
        r("/admin/management/customers/**", "AREA:customers"),
        r("/admin/management/products/**", "AREA:products"), r("/admin/product/**", "AREA:products"),
        r("/admin/management/categories/**", "AREA:categories"), r("/admin/category/**", "AREA:categories"),
        r("/admin/management/subscriptions/**", "AREA:subscriptions"), r("/admin/management/tariffs/**", "AREA:subscriptions"),
        r("/admin/management/promotions/**", "AREA:promotions"), r("/admin/management/banners/**", "AREA:promotions"), r("/admin/management/coupons/**", "AREA:promotions"),
        r("/admin/management/reviews/**", "AREA:reviews"), r("/admin/management/questions/**", "AREA:reviews"),
        r("/admin/management/delivery/**", "AREA:delivery"), r("/delivery-app/admin/**", "AREA:delivery"),
        r("/admin/warehouse/suppliers/**", "AREA:suppliers"),
        r("/admin/warehouse/receipts/*/post", "APPROVE"), r("/admin/warehouse/receipts/*/reverse", "APPROVE"),
        r("/admin/warehouse/inventory/documents/*/approve", "APPROVE"), r("/admin/warehouse/inventory/documents/*/void", "APPROVE"),
        r("/admin/warehouse/inventory/settings", "OWNER"), r("/admin/warehouse/inventory/writeoff-summary", "OWNER"),
        r("/admin/warehouse/inventory/**", "AREA:inventory"),
        r("/admin/warehouse/**", "AREA:warehouse"));
    private static String[] r(String pattern, String rule) { return new String[]{API + pattern, rule}; }

    /** The permission an admin API call needs: ANY (""), a permission, or null when only the owner may call it. */
    public static String required(String method, String uri) {
        for (String[] rule : RULES) {
            if (!PATHS.match(rule[0], uri)) continue;
            String r = rule[1];
            if ("ANY".equals(r)) return ANY;
            if ("OWNER".equals(r)) return null;
            if ("APPROVE".equals(r)) return APPROVE;
            return r.substring(5) + ("GET".equalsIgnoreCase(method) ? ".view" : ".edit");
        }
        return null;
    }
}
