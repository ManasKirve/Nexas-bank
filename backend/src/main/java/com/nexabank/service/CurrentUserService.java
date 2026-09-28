package com.nexabank.service;

import com.nexabank.entity.Account;
import com.nexabank.entity.Role;
import com.nexabank.entity.User;
import com.nexabank.exception.AuthenticationFailedException;
import com.nexabank.exception.ForbiddenOperationException;
import com.nexabank.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Single place that translates Spring Security's context into application
 * concepts. Services/controllers must use this instead of reaching into
 * {@code SecurityContextHolder} directly.
 */
@Service
public class CurrentUserService {

    private final UserRepository users;

    public CurrentUserService(UserRepository users) {
        this.users = users;
    }

    /**
     * Username of the currently authenticated principal, or {@code null}
     * when the request is unauthenticated.
     */
    public String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return null;
        }
        return authentication.getName();
    }

    /**
     * Loads the application {@link User} for the current authentication.
     * Throws a 401-mapped exception when there is none.
     */
    @Transactional(readOnly = true)
    public User requireUser() {
        String username = currentUsername();
        if (username == null) {
            throw new AuthenticationFailedException("Authentication required");
        }
        return users.findByUsername(username)
                .orElseThrow(() -> new AuthenticationFailedException("Authentication required"));
    }

    /**
     * Resolves the linked customer id for CUSTOMER users.
     */
    @Transactional(readOnly = true)
    public Long requireCustomerId() {
        User user = requireUser();
        if (user.getRole() != Role.CUSTOMER) {
            throw new ForbiddenOperationException("Only customer users have a linked customer profile");
        }
        if (user.getCustomer() == null) {
            throw new ForbiddenOperationException("No customer profile is linked to this user");
        }
        return user.getCustomer().getId();
    }

    public boolean hasAnyRole(User user, Role... roles) {
        for (Role role : roles) {
            if (user.getRole() == role) {
                return true;
            }
        }
        return false;
    }

    /**
     * CUSTOMER users may only touch their own linked customer id;
     * staff roles (BANK_EMPLOYEE, ADMIN) have broader access.
     * FRAUD_ANALYST has no customer-data access through this path.
     */
    @Transactional(readOnly = true)
    public void checkCustomerAccess(Long customerId) {
        User user = requireUser();
        if (hasAnyRole(user, Role.BANK_EMPLOYEE, Role.ADMIN)) {
            return;
        }
        if (user.getRole() == Role.CUSTOMER
                && user.getCustomer() != null
                && user.getCustomer().getId().equals(customerId)) {
            return;
        }
        throw new ForbiddenOperationException("You do not have access to this customer");
    }

    /**
     * CUSTOMER users may only touch accounts owned by their linked
     * customer; staff roles (BANK_EMPLOYEE, ADMIN) have broader access.
     */
    @Transactional(readOnly = true)
    public void checkAccountAccess(Account account) {
        User user = requireUser();
        if (hasAnyRole(user, Role.BANK_EMPLOYEE, Role.ADMIN)) {
            return;
        }
        if (user.getRole() == Role.CUSTOMER
                && user.getCustomer() != null
                && account.getCustomer().getId().equals(user.getCustomer().getId())) {
            return;
        }
        throw new ForbiddenOperationException("You do not have access to this account");
    }
}
