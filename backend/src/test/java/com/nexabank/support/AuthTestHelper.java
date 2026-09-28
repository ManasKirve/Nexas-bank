package com.nexabank.support;

import com.nexabank.entity.Customer;
import com.nexabank.entity.Role;
import com.nexabank.entity.User;
import com.nexabank.repository.UserRepository;
import com.nexabank.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

/**
 * Shared helper for controller tests: mints real DB users and JWTs so
 * tests exercise the production authentication path (no mocked security).
 */
public final class AuthTestHelper {

    public static final String TEST_PASSWORD = "Password123!";

    private AuthTestHelper() {
    }

    public static String employeeToken(
            UserRepository users, PasswordEncoder encoder, JwtService jwtService) {
        return tokenFor(users, encoder, jwtService, Role.BANK_EMPLOYEE, null);
    }

    public static String adminToken(
            UserRepository users, PasswordEncoder encoder, JwtService jwtService) {
        return tokenFor(users, encoder, jwtService, Role.ADMIN, null);
    }

    public static String fraudToken(
            UserRepository users, PasswordEncoder encoder, JwtService jwtService) {
        return tokenFor(users, encoder, jwtService, Role.FRAUD_ANALYST, null);
    }

    public static String customerToken(
            UserRepository users, PasswordEncoder encoder, JwtService jwtService, Customer customer) {
        return tokenFor(users, encoder, jwtService, Role.CUSTOMER, customer);
    }

    public static String tokenFor(
            UserRepository users,
            PasswordEncoder encoder,
            JwtService jwtService,
            Role role,
            Customer customer) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = role.name().toLowerCase() + "-" + suffix;
        User user = new User(
                username,
                username + "@example.com",
                encoder.encode(TEST_PASSWORD),
                role);
        user.setCustomer(customer);
        users.save(user);
        return jwtService.generateToken(user);
    }

    public static String bearer(String token) {
        return "Bearer " + token;
    }
}
