package com.ninimum.api.security;

import com.ninimum.api.user.service.IUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Customer APIs use the authenticated account, never an account ID supplied in a request body. */
@Component
@RequiredArgsConstructor
public class CurrentCustomer {
    private final IUserService users;
    public Long id() throws Exception {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if(auth==null||!auth.isAuthenticated()||auth.getAuthorities().stream().noneMatch(a->"USER".equals(a.getAuthority())))
            throw new IllegalStateException("Customer authentication required");
        var user=users.getUserByPhone(auth.getName());
        if(user==null||user.getId()==null)throw new IllegalStateException("Authenticated customer not found");
        return user.getId();
    }
}
