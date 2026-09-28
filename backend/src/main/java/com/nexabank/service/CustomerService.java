package com.nexabank.service;

import com.nexabank.dto.CreateCustomerRequest;
import com.nexabank.dto.CustomerResponse;
import com.nexabank.dto.UpdateCustomerRequest;
import com.nexabank.entity.Customer;
import com.nexabank.entity.CustomerStatus;
import com.nexabank.exception.DuplicateResourceException;
import com.nexabank.exception.ResourceNotFoundException;
import com.nexabank.infra.CacheConfig;
import com.nexabank.repository.CustomerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Customer use cases. Owns number generation, uniqueness checks and DTO mapping.
 * DELETE is soft: the status flips to INACTIVE instead of removing the row.
 */
@Service
public class CustomerService {

    private static final Logger log = LoggerFactory.getLogger(CustomerService.class);

    private static final String NUMBER_PREFIX = "CUST-";
    private static final long FIRST_SEQUENCE = 100001L;

    private final CustomerRepository customers;
    private final CurrentUserService currentUserService;
    private final Object numberLock = new Object();

    public CustomerService(CustomerRepository customers, CurrentUserService currentUserService) {
        this.customers = customers;
        this.currentUserService = currentUserService;
    }

    @Transactional
    public CustomerResponse create(CreateCustomerRequest request) {
        String email = normalized(request.email());
        if (customers.existsByEmail(email)) {
            throw new DuplicateResourceException("Customer with email '" + email + "' already exists");
        }
        Customer saved = saveWithGeneratedNumber(request, email);
        log.info("Created customer {}", saved.getCustomerNumber());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<CustomerResponse> findAll() {
        return customers.findAllByOrderByIdAsc().stream().map(this::toResponse).toList();
    }

    /**
     * Cached profile read (10 min TTL in Redis, in-memory otherwise).
     * Safe to cache: responses carry no balance; callers are authorized
     * before this method runs (controller role gates + ownership checks in
     * the *Authorized variants, which are NOT cached). Evicted on every
     * mutation below.
     */
    @Cacheable(value = CacheConfig.CUSTOMERS, key = "#id")
    @Transactional(readOnly = true)
    public CustomerResponse findById(Long id) {
        return toResponse(requireById(id));
    }

    /**
     * Authorization-aware read: CUSTOMER callers may only read their own
     * linked customer id; BANK_EMPLOYEE and ADMIN have broader access.
     * Runs inside one transaction so ownership checks and the read agree.
     */
    @Transactional(readOnly = true)
    public CustomerResponse findByIdAuthorized(Long id) {
        currentUserService.checkCustomerAccess(id);
        return toResponse(requireById(id));
    }

    /**
     * Own profile of the authenticated CUSTOMER user, resolved from the
     * JWT identity rather than a frontend-supplied id.
     */
    @Transactional(readOnly = true)
    public CustomerResponse findOwnProfile() {
        return toResponse(requireById(currentUserService.requireCustomerId()));
    }

    @Transactional(readOnly = true)
    public Customer requireEntityById(Long id) {
        return requireById(id);
    }

    @CacheEvict(value = CacheConfig.CUSTOMERS, key = "#id")
    @Transactional
    public CustomerResponse update(Long id, UpdateCustomerRequest request) {
        Customer customer = requireById(id);
        String email = normalized(request.email());
        customers.findByEmail(email).ifPresent(other -> {
            if (!other.getId().equals(id)) {
                throw new DuplicateResourceException("Customer with email '" + email + "' already exists");
            }
        });
        customer.setFirstName(request.firstName().trim());
        customer.setLastName(request.lastName().trim());
        customer.setEmail(email);
        customer.setPhone(normalizedPhone(request.phone()));
        return toResponse(customer);
    }

    /**
     * Soft delete: the customer row is kept for the audit trail and all
     * balances/history stay intact; only the status becomes INACTIVE.
     */
    @CacheEvict(value = CacheConfig.CUSTOMERS, key = "#id")
    @Transactional
    public CustomerResponse deactivate(Long id) {
        Customer customer = requireById(id);
        customer.setStatus(CustomerStatus.INACTIVE);
        log.info("Deactivated customer {}", customer.getCustomerNumber());
        return toResponse(customer);
    }

    private Customer requireById(Long id) {
        return customers.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with id " + id));
    }

    private Customer saveWithGeneratedNumber(CreateCustomerRequest request, String email) {
        // Single-instance guard: max + 1 stays collision-free within one node; the
        // unique constraint is the backstop (mapped to HTTP 409) for clustered use.
        // A DB sequence + migration will replace this when the schema is migration-managed.
        synchronized (numberLock) {
            Customer customer = new Customer(
                    nextCustomerNumber(),
                    request.firstName().trim(),
                    request.lastName().trim(),
                    email,
                    normalizedPhone(request.phone()));
            return customers.save(customer);
        }
    }

    /**
     * Phase-2-simple generator: max existing suffix + 1 (see locking note above).
     */
    private String nextCustomerNumber() {
        long max = customers.findMaxCustomerNumber()
                .map(number -> {
                    try {
                        return Long.parseLong(number.substring(NUMBER_PREFIX.length()));
                    } catch (NumberFormatException | IndexOutOfBoundsException ex) {
                        return FIRST_SEQUENCE - 1;
                    }
                })
                .orElse(FIRST_SEQUENCE - 1);
        return NUMBER_PREFIX + Math.max(FIRST_SEQUENCE, max + 1);
    }

    private CustomerResponse toResponse(Customer customer) {
        return new CustomerResponse(
                customer.getId(),
                customer.getCustomerNumber(),
                customer.getFirstName(),
                customer.getLastName(),
                customer.getEmail(),
                customer.getPhone(),
                customer.getStatus(),
                customer.getCreatedAt(),
                customer.getUpdatedAt());
    }

    private String normalized(String value) {
        return value == null ? null : value.trim().toLowerCase();
    }

    private String normalizedPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        return phone.trim();
    }
}
