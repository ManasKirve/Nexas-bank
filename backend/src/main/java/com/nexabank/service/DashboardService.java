package com.nexabank.service;

import com.nexabank.dto.DashboardSummaryResponse;
import com.nexabank.entity.AccountStatus;
import com.nexabank.repository.AccountRepository;
import com.nexabank.repository.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only aggregates for the dashboard. Every figure is a live
 * database count — never hardcoded.
 */
@Service
public class DashboardService {

    private final CustomerRepository customers;
    private final AccountRepository accounts;

    public DashboardService(CustomerRepository customers, AccountRepository accounts) {
        this.customers = customers;
        this.accounts = accounts;
    }

    @Transactional(readOnly = true)
    public DashboardSummaryResponse summary() {
        return new DashboardSummaryResponse(
                customers.count(),
                accounts.count(),
                accounts.countByStatus(AccountStatus.ACTIVE),
                accounts.countByStatus(AccountStatus.FROZEN));
    }
}
