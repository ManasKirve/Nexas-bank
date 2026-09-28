package com.nexabank.service;

import com.nexabank.dto.AccountResponse;
import com.nexabank.dto.CreateAccountRequest;
import com.nexabank.entity.Account;
import com.nexabank.entity.AccountStatus;
import com.nexabank.entity.Customer;
import com.nexabank.exception.InvalidStateTransitionException;
import com.nexabank.exception.ResourceNotFoundException;
import com.nexabank.repository.AccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Account use cases. Balances always start at zero and are never mutated
 * here — the future transaction system owns all balance changes.
 */
@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private static final long FIRST_ACCOUNT_NUMBER = 100000000001L;

    private static final Map<AccountStatus, Set<AccountStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(AccountStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(AccountStatus.ACTIVE, EnumSet.of(AccountStatus.FROZEN, AccountStatus.CLOSED));
        ALLOWED_TRANSITIONS.put(AccountStatus.FROZEN, EnumSet.of(AccountStatus.ACTIVE, AccountStatus.CLOSED));
        ALLOWED_TRANSITIONS.put(AccountStatus.CLOSED, EnumSet.noneOf(AccountStatus.class));
    }

    private final AccountRepository accounts;
    private final CustomerService customerService;
    private final CurrentUserService currentUserService;
    private final Object numberLock = new Object();

    public AccountService(
            AccountRepository accounts,
            CustomerService customerService,
            CurrentUserService currentUserService) {
        this.accounts = accounts;
        this.customerService = customerService;
        this.currentUserService = currentUserService;
    }

    @Transactional
    public AccountResponse create(CreateAccountRequest request) {
        Customer customer = customerService.requireEntityById(request.customerId());
        Account saved;
        synchronized (numberLock) {
            saved = accounts.save(new Account(
                    nextAccountNumber(), customer, request.accountType(), request.currency()));
        }
        log.info("Created account {} for customer {}", saved.getAccountNumber(), customer.getCustomerNumber());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> findAll() {
        return accounts.findAllByOrderByIdAsc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public AccountResponse findById(Long id) {
        return toResponse(requireById(id));
    }

    /**
     * Authorization-aware read: CUSTOMER callers may only read accounts
     * owned by their linked customer; BANK_EMPLOYEE and ADMIN have
     * broader access. Ownership is verified inside the same transaction.
     */
    @Transactional(readOnly = true)
    public AccountResponse findByIdAuthorized(Long id) {
        Account account = requireById(id);
        currentUserService.checkAccountAccess(account);
        return toResponse(account);
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> findByCustomer(Long customerId) {
        customerService.requireEntityById(customerId);
        return accounts.findByCustomerIdOrderByIdAsc(customerId).stream().map(this::toResponse).toList();
    }

    /**
     * Authorization-aware variant of {@link #findByCustomer(Long)} plus a
     * convenience for the authenticated customer's own accounts.
     */
    @Transactional(readOnly = true)
    public List<AccountResponse> findByCustomerAuthorized(Long customerId) {
        currentUserService.checkCustomerAccess(customerId);
        return findByCustomer(customerId);
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> findOwnAccounts() {
        return findByCustomer(currentUserService.requireCustomerId());
    }

    @Transactional
    public AccountResponse changeStatus(Long id, AccountStatus target) {
        Account account = requireById(id);
        AccountStatus current = account.getStatus();
        if (!ALLOWED_TRANSITIONS.getOrDefault(current, Set.of()).contains(target)) {
            throw new InvalidStateTransitionException(
                    "Cannot transition account " + account.getAccountNumber()
                            + " from " + current + " to " + target);
        }
        account.setStatus(target);
        log.info("Account {} status {} -> {}", account.getAccountNumber(), current, target);
        return toResponse(account);
    }

    @Transactional(readOnly = true)
    public long countByStatus(AccountStatus status) {
        return accounts.countByStatus(status);
    }

    @Transactional(readOnly = true)
    public long countAll() {
        return accounts.count();
    }

    private Account requireById(Long id) {
        return accounts.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with id " + id));
    }

    private String nextAccountNumber() {
        long max = accounts.findMaxAccountNumber()
                .map(number -> {
                    try {
                        return Long.parseLong(number);
                    } catch (NumberFormatException ex) {
                        return FIRST_ACCOUNT_NUMBER - 1;
                    }
                })
                .orElse(FIRST_ACCOUNT_NUMBER - 1);
        return String.valueOf(Math.max(FIRST_ACCOUNT_NUMBER, max + 1));
    }

    private AccountResponse toResponse(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getAccountNumber(),
                account.getCustomer().getId(),
                account.getCustomer().getCustomerNumber(),
                account.getAccountType(),
                account.getBalance(),
                account.getCurrency(),
                account.getStatus(),
                account.getCreatedAt(),
                account.getUpdatedAt());
    }
}
