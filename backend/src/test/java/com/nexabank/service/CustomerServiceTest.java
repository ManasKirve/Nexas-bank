package com.nexabank.service;

import com.nexabank.dto.CreateCustomerRequest;
import com.nexabank.dto.CustomerResponse;
import com.nexabank.dto.UpdateCustomerRequest;
import com.nexabank.entity.CustomerStatus;
import com.nexabank.exception.DuplicateResourceException;
import com.nexabank.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CustomerServiceTest {

    @Autowired
    private CustomerService customerService;

    @Test
    void createAssignsSequentialCustomerNumber() {
        CustomerResponse first = customerService.create(request("ann@example.com"));
        CustomerResponse second = customerService.create(request("bob@example.com"));

        assertThat(first.customerNumber()).isEqualTo("CUST-100001");
        assertThat(second.customerNumber()).isEqualTo("CUST-100002");
        assertThat(first.status()).isEqualTo(CustomerStatus.ACTIVE);
    }

    @Test
    void createRejectsDuplicateEmail() {
        customerService.create(request("ann@example.com"));

        assertThatThrownBy(() -> customerService.create(request("ann@example.com")))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void findByIdThrowsWhenMissing() {
        assertThatThrownBy(() -> customerService.findById(999999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Customer not found");
    }

    @Test
    void updateChangesDetailsButKeepsNumber() {
        CustomerResponse created = customerService.create(request("ann@example.com"));

        CustomerResponse updated = customerService.update(created.id(),
                new UpdateCustomerRequest("Annette", "Smith", "annette@example.com", "+91 98765 43210"));

        assertThat(updated.customerNumber()).isEqualTo(created.customerNumber());
        assertThat(updated.firstName()).isEqualTo("Annette");
        assertThat(updated.email()).isEqualTo("annette@example.com");
    }

    @Test
    void updateRejectsEmailOwnedByAnotherCustomer() {
        customerService.create(request("ann@example.com"));
        CustomerResponse bob = customerService.create(request("bob@example.com"));

        assertThatThrownBy(() -> customerService.update(bob.id(),
                new UpdateCustomerRequest("Bob", "Jones", "ann@example.com", null)))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void deactivateFlipsStatusInsteadOfDeleting() {
        CustomerResponse created = customerService.create(request("ann@example.com"));

        CustomerResponse deactivated = customerService.deactivate(created.id());

        assertThat(deactivated.status()).isEqualTo(CustomerStatus.INACTIVE);
        // Row still exists for the audit trail.
        assertThat(customerService.findById(created.id()).status()).isEqualTo(CustomerStatus.INACTIVE);
    }

    private CreateCustomerRequest request(String email) {
        return new CreateCustomerRequest("Ann", "Smith", email, "+91 98765 43210");
    }
}
