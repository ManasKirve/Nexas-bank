package com.nexabank.service;

import com.nexabank.dto.BeneficiaryResponse;
import com.nexabank.dto.CreateBeneficiaryRequest;
import com.nexabank.entity.Account;
import com.nexabank.entity.AccountStatus;
import com.nexabank.entity.Beneficiary;
import com.nexabank.entity.BeneficiaryStatus;
import com.nexabank.entity.Customer;
import com.nexabank.entity.Role;
import com.nexabank.entity.User;
import com.nexabank.exception.BeneficiaryNotFoundException;
import com.nexabank.exception.DuplicateResourceException;
import com.nexabank.exception.ForbiddenOperationException;
import com.nexabank.exception.InvalidTransactionException;
import com.nexabank.exception.ResourceNotFoundException;
import com.nexabank.repository.AccountRepository;
import com.nexabank.repository.BeneficiaryRepository;
import com.nexabank.repository.CustomerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Beneficiary use cases. The owning customer is always derived from the
 * authenticated user — creation is CUSTOMER-scoped; staff roles get
 * operational read/disable access.
 */
@Service
public class BeneficiaryService {

    private static final Logger log = LoggerFactory.getLogger(BeneficiaryService.class);

    private final BeneficiaryRepository beneficiaries;
    private final CustomerRepository customers;
    private final AccountRepository accounts;
    private final CurrentUserService currentUserService;

    public BeneficiaryService(
            BeneficiaryRepository beneficiaries,
            CustomerRepository customers,
            AccountRepository accounts,
            CurrentUserService currentUserService) {
        this.beneficiaries = beneficiaries;
        this.customers = customers;
        this.accounts = accounts;
        this.currentUserService = currentUserService;
    }

    /**
     * Creates a beneficiary owned by the authenticated CUSTOMER. The
     * destination account must exist and be ACTIVE. Re-adding a disabled
     * beneficiary re-activates it instead of duplicating history.
     */
    @Transactional
    public BeneficiaryResponse create(CreateBeneficiaryRequest request) {
        Customer owner = requireOwnCustomer();
        Account destination = accounts.findById(request.accountId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Account not found with id " + request.accountId()));
        if (destination.getStatus() != AccountStatus.ACTIVE) {
            throw new InvalidTransactionException(
                    "Account " + destination.getAccountNumber() + " is not ACTIVE and cannot be a beneficiary");
        }
        String nickname = request.nickname() == null || request.nickname().isBlank()
                ? destination.getAccountNumber()
                : request.nickname().trim();

        return beneficiaries.findByCustomerIdAndBeneficiaryAccountId(owner.getId(), destination.getId())
                .map(existing -> {
                    if (existing.getStatus() == BeneficiaryStatus.ACTIVE) {
                        throw new DuplicateResourceException(
                                "Account " + destination.getAccountNumber() + " is already a beneficiary");
                    }
                    existing.setStatus(BeneficiaryStatus.ACTIVE);
                    existing.setNickname(nickname);
                    log.info("Re-activated beneficiary {} for customer {}",
                            destination.getAccountNumber(), owner.getCustomerNumber());
                    return BeneficiaryResponse.from(existing);
                })
                .orElseGet(() -> {
                    Beneficiary saved = beneficiaries.save(new Beneficiary(owner, destination, nickname));
                    log.info("Created beneficiary {} for customer {}",
                            destination.getAccountNumber(), owner.getCustomerNumber());
                    return BeneficiaryResponse.from(saved);
                });
    }

    @Transactional(readOnly = true)
    public List<BeneficiaryResponse> list() {
        User caller = currentUserService.requireUser();
        List<Beneficiary> rows = currentUserService.hasAnyRole(caller, Role.BANK_EMPLOYEE, Role.ADMIN)
                ? beneficiaries.findAll()
                : beneficiaries.findByCustomerIdOrderByIdAsc(requireOwnCustomerId(caller));
        return rows.stream().map(BeneficiaryResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public BeneficiaryResponse get(Long id) {
        return BeneficiaryResponse.from(requireVisible(id));
    }

    /**
     * Soft disable: ACTIVE → DISABLED. Already-disabled rows return as-is;
     * history is never physically deleted.
     */
    @Transactional
    public BeneficiaryResponse disable(Long id) {
        Beneficiary beneficiary = requireVisible(id);
        if (beneficiary.getStatus() != BeneficiaryStatus.DISABLED) {
            beneficiary.setStatus(BeneficiaryStatus.DISABLED);
            log.info("Disabled beneficiary {}", beneficiary.getId());
        }
        return BeneficiaryResponse.from(beneficiary);
    }

    /**
     * Loads a beneficiary for transfer use: must exist, be ACTIVE and belong
     * to the authenticated CUSTOMER (staff callers use operational access).
     * Enforces 404 (missing) vs 403 (someone else's) vs 422 (disabled).
     */
    @Transactional(readOnly = true)
    public Beneficiary requireActiveForTransfer(Long beneficiaryId) {
        Beneficiary beneficiary = beneficiaries.findById(beneficiaryId)
                .orElseThrow(() -> new BeneficiaryNotFoundException(
                        "Beneficiary not found with id " + beneficiaryId));
        User caller = currentUserService.requireUser();
        if (!currentUserService.hasAnyRole(caller, Role.BANK_EMPLOYEE, Role.ADMIN)) {
            Long ownCustomerId = requireOwnCustomerId(caller);
            if (!beneficiary.getCustomer().getId().equals(ownCustomerId)) {
                throw new ForbiddenOperationException("You do not have access to this beneficiary");
            }
        }
        if (beneficiary.getStatus() != BeneficiaryStatus.ACTIVE) {
            throw new InvalidTransactionException("Beneficiary '" + beneficiary.getNickname() + "' is not active");
        }
        return beneficiary;
    }

    private Beneficiary requireVisible(Long id) {
        Beneficiary beneficiary = beneficiaries.findById(id)
                .orElseThrow(() -> new BeneficiaryNotFoundException(
                        "Beneficiary not found with id " + id));
        User caller = currentUserService.requireUser();
        if (!currentUserService.hasAnyRole(caller, Role.BANK_EMPLOYEE, Role.ADMIN)) {
            Long ownCustomerId = requireOwnCustomerId(caller);
            if (!beneficiary.getCustomer().getId().equals(ownCustomerId)) {
                throw new ForbiddenOperationException("You do not have access to this beneficiary");
            }
        }
        return beneficiary;
    }

    private Customer requireOwnCustomer() {
        return customers.findById(currentUserService.requireCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer profile not found"));
    }

    private Long requireOwnCustomerId(User caller) {
        if (caller.getRole() != Role.CUSTOMER || caller.getCustomer() == null) {
            throw new ForbiddenOperationException("No customer profile is linked to this user");
        }
        return caller.getCustomer().getId();
    }
}
