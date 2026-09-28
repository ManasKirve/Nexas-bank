package com.nexabank.service;

import com.nexabank.dto.AccountResponse;
import com.nexabank.dto.CreateAccountRequest;
import com.nexabank.dto.CreateCustomerRequest;
import com.nexabank.dto.CustomerResponse;
import com.nexabank.entity.AccountStatus;
import com.nexabank.entity.AccountType;
import com.nexabank.exception.InvalidStateTransitionException;
import com.nexabank.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AccountServiceTest {

    @Autowired
    private AccountService accountService;

    @Autowired
    private CustomerService customerService;

    @Test
    void createStartsWithZeroBalanceAndSequentialNumber() {
        CustomerResponse customer = customer();

        AccountResponse first = accountService.create(new CreateAccountRequest(customer.id(), AccountType.SAVINGS, "INR"));
        AccountResponse second = accountService.create(new CreateAccountRequest(customer.id(), AccountType.CHECKING, "INR"));

        assertThat(first.accountNumber()).isEqualTo("100000000001");
        assertThat(second.accountNumber()).isEqualTo("100000000002");
        assertThat(first.balance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(first.currency()).isEqualTo("INR");
        assertThat(first.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(first.customerId()).isEqualTo(customer.id());
    }

    @Test
    void createRequiresExistingCustomer() {
        assertThatThrownBy(() -> accountService.create(
                new CreateAccountRequest(999999L, AccountType.SAVINGS, "INR")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Customer not found");
    }

    @Test
    void findByCustomerReturnsOnlyOwnedAccounts() {
        CustomerResponse ann = customerService.create(
                new CreateCustomerRequest("Ann", "A", "ann@example.com", null));
        CustomerResponse bob = customerService.create(
                new CreateCustomerRequest("Bob", "B", "bob@example.com", null));
        accountService.create(new CreateAccountRequest(ann.id(), AccountType.SAVINGS, "INR"));
        accountService.create(new CreateAccountRequest(bob.id(), AccountType.SAVINGS, "INR"));

        assertThat(accountService.findByCustomer(ann.id())).hasSize(1);
        assertThat(accountService.findByCustomer(bob.id()).get(0).customerId()).isEqualTo(bob.id());
    }

    @Test
    void findByCustomerThrowsForMissingCustomer() {
        assertThatThrownBy(() -> accountService.findByCustomer(999999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void allowsActiveToFrozenToActiveToClosed() {
        AccountResponse account = account();

        assertThat(accountService.changeStatus(account.id(), AccountStatus.FROZEN).status())
                .isEqualTo(AccountStatus.FROZEN);
        assertThat(accountService.changeStatus(account.id(), AccountStatus.ACTIVE).status())
                .isEqualTo(AccountStatus.ACTIVE);
        assertThat(accountService.changeStatus(account.id(), AccountStatus.CLOSED).status())
                .isEqualTo(AccountStatus.CLOSED);
    }

    @Test
    void rejectsNonsensicalTransitions() {
        AccountResponse account = account();
        accountService.changeStatus(account.id(), AccountStatus.CLOSED);

        assertThatThrownBy(() -> accountService.changeStatus(account.id(), AccountStatus.ACTIVE))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("CLOSED");
        assertThatThrownBy(() -> accountService.changeStatus(account.id(), AccountStatus.FROZEN))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    private CustomerResponse customer() {
        return customerService.create(new CreateCustomerRequest("Ann", "Smith", "ann@example.com", null));
    }

    private AccountResponse account() {
        return accountService.create(new CreateAccountRequest(customer().id(), AccountType.SAVINGS, "INR"));
    }
}
