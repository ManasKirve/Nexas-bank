package com.nexabank.config;

import com.nexabank.dto.CreateCustomerRequest;
import com.nexabank.entity.Customer;
import com.nexabank.entity.Role;
import com.nexabank.entity.User;
import com.nexabank.repository.CustomerRepository;
import com.nexabank.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Development-only seed users (one per role) so the Angular login flow can
 * be exercised without manual registration.
 *
 * <p>Active ONLY under the {@code dev} profile — never in production.
 * The seed password comes from {@code NEXABANK_DEV_SEED_PASSWORD} with a
 * documented insecure local default; it is logged as a warning, never the
 * value itself beyond noting that the default is in use.</p>
 */
@Component
@Profile("dev")
public class DevUserSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevUserSeeder.class);

    private static final String DEFAULT_SEED_PASSWORD = "Password123!";

    private final UserRepository users;
    private final CustomerRepository customers;
    private final PasswordEncoder passwordEncoder;
    private final String seedPassword;

    public DevUserSeeder(
            UserRepository users,
            CustomerRepository customers,
            PasswordEncoder passwordEncoder,
            @Value("${NEXABANK_DEV_SEED_PASSWORD:__unset__}") String configured,
            @Value("${nexabank.dev.seed-password:__unset__}") String propertyAlias) {
        this.users = users;
        this.customers = customers;
        this.passwordEncoder = passwordEncoder;
        String env = System.getenv("NEXABANK_DEV_SEED_PASSWORD");
        if (env != null && !env.isBlank()) {
            this.seedPassword = env;
        } else if (configured != null && !configured.isBlank() && !"__unset__".equals(configured)) {
            this.seedPassword = configured;
        } else if (propertyAlias != null && !propertyAlias.isBlank() && !"__unset__".equals(propertyAlias)) {
            this.seedPassword = propertyAlias;
        } else {
            this.seedPassword = DEFAULT_SEED_PASSWORD;
        }
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        boolean usingDefault = DEFAULT_SEED_PASSWORD.equals(seedPassword);
        if (usingDefault) {
            log.warn("DEV ONLY: seeding default development users with the documented default password. "
                    + "Set NEXABANK_DEV_SEED_PASSWORD to override. Never enable the 'dev' profile in production.");
        } else {
            log.info("DEV ONLY: seeding default development users (password from NEXABANK_DEV_SEED_PASSWORD).");
        }
        String hash = passwordEncoder.encode(seedPassword);

        Customer customer = customers.findByEmail("customer1.dev@nexabank.example").orElseGet(() -> {
            Customer created = new Customer(
                    nextCustomerNumber(), "Dev", "Customer", "customer1.dev@nexabank.example", null);
            return customers.save(created);
        });

        List<SeedUser> seeds = List.of(
                new SeedUser("customer1", "customer1.dev@nexabank.example", Role.CUSTOMER, customer),
                new SeedUser("employee1", "employee1.dev@nexabank.example", Role.BANK_EMPLOYEE, null),
                new SeedUser("fraud1", "fraud1.dev@nexabank.example", Role.FRAUD_ANALYST, null),
                new SeedUser("admin1", "admin1.dev@nexabank.example", Role.ADMIN, null));

        for (SeedUser seed : seeds) {
            users.findByUsername(seed.username()).ifPresentOrElse(
                    existing -> log.debug("Dev seed user '{}' already exists", seed.username()),
                    () -> {
                        User user = new User(seed.username(), seed.email(), hash, seed.role());
                        user.setCustomer(seed.customer());
                        users.save(user);
                        log.info("DEV ONLY: created seed user '{}' with role {}", seed.username(), seed.role());
                    });
        }
    }

    private String nextCustomerNumber() {
        long max = customers.findMaxCustomerNumber()
                .map(number -> {
                    try {
                        return Long.parseLong(number.substring("CUST-".length()));
                    } catch (NumberFormatException | IndexOutOfBoundsException ex) {
                        return 100000L;
                    }
                })
                .orElse(100000L);
        return "CUST-" + Math.max(100001L, max + 1);
    }

    private record SeedUser(String username, String email, Role role, Customer customer) {
    }
}
