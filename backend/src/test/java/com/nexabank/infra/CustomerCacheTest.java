package com.nexabank.infra;

import com.nexabank.dto.CustomerResponse;
import com.nexabank.service.CustomerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.nexabank.repository.UserRepository;
import com.nexabank.security.JwtService;
import com.nexabank.support.AuthTestHelper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 7 cache contract: customer reads populate the cache (hit), updates
 * invalidate it, and cache failures never fail requests (fail-safe error
 * handler). Balances/ledger writes are deliberately NOT cached.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CustomerCacheTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerService customers;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private CacheConfig cacheConfig;

    private Cache cache() {
        Cache cache = cacheManager.getCache(CacheConfig.CUSTOMERS);
        assertThat(cache).isNotNull();
        return cache;
    }

    @Test
    void readPopulatesCacheAndUpdateInvalidates() throws Exception {
        String employeeAuth = AuthTestHelper.bearer(
                AuthTestHelper.employeeToken(users, passwordEncoder, jwtService));
        long customerId = createCustomer(employeeAuth);

        // Miss → populated.
        assertThat(cache().get(customerId)).isNull();
        CustomerResponse first = customers.findById(customerId);
        assertThat(cache().get(customerId)).isNotNull();
        assertThat(cache().get(customerId, CustomerResponse.class)).isNotNull();

        // Hit → same data without touching the database path again.
        CustomerResponse second = customers.findById(customerId);
        assertThat(second.email()).isEqualTo(first.email());

        // Mutation → invalidated.
        customers.update(customerId, new com.nexabank.dto.UpdateCustomerRequest(
                "Cache", "Tester", "cache-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com", null));
        assertThat(cache().get(customerId)).isNull();

        // Repopulated on next read.
        customers.findById(customerId);
        assertThat(cache().get(customerId)).isNotNull();
    }

    @Test
    void cacheErrorsNeverFailRequests() {
        var handler = cacheConfig.errorHandler();
        org.springframework.cache.concurrent.ConcurrentMapCache dummy =
                new org.springframework.cache.concurrent.ConcurrentMapCache("dummy");
        assertThatNoException().isThrownBy(() -> {
            handler.handleCacheGetError(new RuntimeException("redis down"), dummy, "k");
            handler.handleCachePutError(new RuntimeException("redis down"), dummy, "k", "v");
            handler.handleCacheEvictError(new RuntimeException("redis down"), dummy, "k");
            handler.handleCacheClearError(new RuntimeException("redis down"), dummy);
        });
    }

    private long createCustomer(String employeeAuth) throws Exception {
        String email = "cache-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        MvcResult result = mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", employeeAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Cache","lastName":"Tester","email":"%s","phone":null}"""
                                .formatted(email)))
                .andExpect(status().isCreated())
                .andReturn();
        return ((Number) com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.id")).longValue();
    }
}
