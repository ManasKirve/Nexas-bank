package com.nexabank.service;

import com.nexabank.dto.LoginRequest;
import com.nexabank.dto.LoginResponse;
import com.nexabank.dto.RegisterRequest;
import com.nexabank.dto.UserResponse;
import com.nexabank.entity.Customer;
import com.nexabank.entity.Role;
import com.nexabank.entity.User;
import com.nexabank.entity.UserStatus;
import com.nexabank.exception.AuthenticationFailedException;
import com.nexabank.exception.DuplicateResourceException;
import com.nexabank.exception.ResourceNotFoundException;
import com.nexabank.repository.CustomerRepository;
import com.nexabank.repository.UserRepository;
import com.nexabank.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Authentication use cases: public CUSTOMER registration, login
 * (password verification + JWT issuance) and current-user profile.
 */
@Service
public class AuthService {

    private final UserRepository users;
    private final CustomerRepository customers;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final CurrentUserService currentUserService;

    public AuthService(
            UserRepository users,
            CustomerRepository customers,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            CurrentUserService currentUserService) {
        this.users = users;
        this.customers = customers;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.currentUserService = currentUserService;
    }

    /**
     * Public registration. Always creates a CUSTOMER user — there is no
     * request field for staff roles, so privilege escalation via the
     * public endpoint is impossible by construction.
     */
    @Transactional
    public UserResponse register(RegisterRequest request) {
        String username = request.username().trim();
        String email = request.email().trim().toLowerCase();
        if (users.existsByUsername(username)) {
            throw new DuplicateResourceException("Username '" + username + "' is already taken");
        }
        if (users.existsByEmail(email)) {
            throw new DuplicateResourceException("Email '" + email + "' is already registered");
        }
        Customer customer = null;
        if (request.customerId() != null) {
            customer = customers.findById(request.customerId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Customer not found with id " + request.customerId()));
        }
        User user = new User(username, email, passwordEncoder.encode(request.password()), Role.CUSTOMER);
        user.setCustomer(customer);
        return UserResponse.from(users.save(user));
    }

    /**
     * Verifies credentials and issues a JWT. Every failure path yields the
     * same generic error to avoid account enumeration.
     */
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        User user = users.findByUsername(request.username().trim())
                .orElseThrow(AuthenticationFailedException::new);
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AuthenticationFailedException();
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new AuthenticationFailedException();
        }
        String token = jwtService.generateToken(user);
        return new LoginResponse(token, "Bearer", jwtService.getExpirationMs() / 1000, UserResponse.from(user));
    }

    @Transactional(readOnly = true)
    public UserResponse me() {
        return UserResponse.from(currentUserService.requireUser());
    }
}
