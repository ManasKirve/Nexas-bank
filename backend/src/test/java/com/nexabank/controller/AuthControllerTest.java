package com.nexabank.controller;

import com.nexabank.entity.Customer;
import com.nexabank.entity.Role;
import com.nexabank.entity.User;
import com.nexabank.entity.UserStatus;
import com.nexabank.repository.CustomerRepository;
import com.nexabank.repository.UserRepository;
import com.nexabank.security.JwtService;
import com.nexabank.support.AuthTestHelper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 3 authentication/authorization contract: registration, login, JWT
 * handling, role recognition and customer-data ownership enforcement.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private CustomerRepository customers;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    // ---------- registration ----------

    @Test
    void registerCreatesCustomerUserWithoutPasswordHash() throws Exception {
        String suffix = uid();
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"reg-%s","email":"reg-%s@example.com","password":"Password123!"}"""
                                .formatted(suffix, suffix)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username", is("reg-" + suffix)))
                .andExpect(jsonPath("$.role", is("CUSTOMER")))
                .andExpect(jsonPath("$.status", is("ACTIVE")))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void registerHashesPasswordAndNeverStoresPlaintext() throws Exception {
        String suffix = uid();
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"hash-%s","email":"hash-%s@example.com","password":"Password123!"}"""
                                .formatted(suffix, suffix)))
                .andExpect(status().isCreated());

        User saved = users.findByUsername("hash-" + suffix).orElseThrow();
        assertThat(saved.getPasswordHash()).isNotEqualTo("Password123!");
        assertThat(saved.getPasswordHash()).startsWith("$2");
        assertThat(passwordEncoder.matches("Password123!", saved.getPasswordHash())).isTrue();
    }

    @Test
    void registerRejectsDuplicateUsername() throws Exception {
        String suffix = uid();
        String body = """
                {"username":"dupe-%s","email":"dupe-a-%s@example.com","password":"Password123!"}"""
                .formatted(suffix, suffix);
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"dupe-%s","email":"dupe-b-%s@example.com","password":"Password123!"}"""
                                .formatted(suffix, suffix)))
                .andExpect(status().isConflict());
    }

    @Test
    void registerRejectsDuplicateEmail() throws Exception {
        String suffix = uid();
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"mail-a-%s","email":"dupe-mail-%s@example.com","password":"Password123!"}"""
                                .formatted(suffix, suffix)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"mail-b-%s","email":"dupe-mail-%s@example.com","password":"Password123!"}"""
                                .formatted(suffix, suffix)))
                .andExpect(status().isConflict());
    }

    @Test
    void publicRegistrationAlwaysCreatesCustomerRole() throws Exception {
        String suffix = uid();
        // Even if a caller tries to smuggle a role field, it is ignored:
        // RegisterRequest has no role property.
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"role-%s","email":"role-%s@example.com",
                                 "password":"Password123!","role":"ADMIN"}"""
                                .formatted(suffix, suffix)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role", is("CUSTOMER")));
    }

    @Test
    void registerWithLinkedCustomerReturnsCustomerId() throws Exception {
        Customer customer = newCustomer();
        String suffix = uid();
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"link-%s","email":"link-%s@example.com",
                                 "password":"Password123!","customerId":%d}"""
                                .formatted(suffix, suffix, customer.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerId", is(customer.getId().intValue())));
    }

    // ---------- login ----------

    @Test
    void loginSucceedsWithValidCredentialsAndReturnsJwt() throws Exception {
        String suffix = uid();
        register(suffix);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"login-%s","password":"Password123!"}""".formatted(suffix)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.tokenType", is("Bearer")))
                .andExpect(jsonPath("$.expiresIn", notNullValue()))
                .andExpect(jsonPath("$.user.username", is("login-" + suffix)))
                .andExpect(jsonPath("$.user.role", is("CUSTOMER")))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist());
    }

    @Test
    void loginFailsWithWrongPasswordAndGenericMessage() throws Exception {
        String suffix = uid();
        register(suffix);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"login-%s","password":"WrongPassword99!"}""".formatted(suffix)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", is("Invalid username or password")));
    }

    @Test
    void loginFailsWithUnknownUserAndSameGenericMessage() throws Exception {
        // Anti-enumeration: unknown usernames yield the identical error.
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"nobody-%s","password":"Whatever123!"}""".formatted(uid())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", is("Invalid username or password")));
    }

    @Test
    void lockedAndDisabledUsersCannotAuthenticate() throws Exception {
        User locked = persistUser("locked", UserStatus.LOCKED);
        User disabled = persistUser("disabled", UserStatus.DISABLED);

        for (String username : new String[]{locked.getUsername(), disabled.getUsername()}) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"username":"%s","password":"Password123!"}""".formatted(username)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message", is("Invalid username or password")));
        }
    }

    // ---------- JWT handling ----------

    @Test
    void meReturnsCurrentUserForValidToken() throws Exception {
        String suffix = uid();
        register(suffix);
        String token = loginAndGetToken("login-" + suffix);

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username", is("login-" + suffix)))
                .andExpect(jsonPath("$.role", is("CUSTOMER")));
    }

    @Test
    void meRejectsMissingTokenWith401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));
    }

    @Test
    void expiredJwtIsRejectedWith401() throws Exception {
        JwtService shortLived = new JwtService(
                "test-only-insecure-secret-for-automated-tests-1234567890", -1000L);
        User user = persistUser("expired", UserStatus.ACTIVE);
        String expired = shortLived.generateToken(user);
        assertThat(shortLived.isExpired(expired)).isTrue();

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tamperedJwtIsRejectedWith401() throws Exception {
        String suffix = uid();
        register(suffix);
        String token = loginAndGetToken("login-" + suffix);
        String tampered = token.substring(0, token.length() - 2) + "xx";

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }

    // ---------- roles ----------

    @Test
    void staffRolesAreRecognizedOnLogin() throws Exception {
        User employee = persistUserWithRole("emp", Role.BANK_EMPLOYEE);
        User fraud = persistUserWithRole("frd", Role.FRAUD_ANALYST);
        User admin = persistUserWithRole("adm", Role.ADMIN);

        assertThat(jwtService.extractRole(jwtService.generateToken(employee)))
                .isEqualTo(Role.BANK_EMPLOYEE);
        assertThat(jwtService.extractRole(jwtService.generateToken(fraud)))
                .isEqualTo(Role.FRAUD_ANALYST);
        assertThat(jwtService.extractRole(jwtService.generateToken(admin)))
                .isEqualTo(Role.ADMIN);

        for (String[] pair : new String[][]{
                {employee.getUsername(), "BANK_EMPLOYEE"},
                {fraud.getUsername(), "FRAUD_ANALYST"},
                {admin.getUsername(), "ADMIN"}}) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"username":"%s","password":"Password123!"}"""
                                    .formatted(pair[0])))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.user.role", is(pair[1])));
        }
    }

    @Test
    void employeeCanManageCustomersButCustomerCannot() throws Exception {
        String employee = AuthTestHelper.bearer(
                AuthTestHelper.employeeToken(users, passwordEncoder, jwtService));
        String suffix = uid();
        register(suffix);
        String customer = AuthTestHelper.bearer(loginAndGetToken("login-" + suffix));

        // Employee: allowed.
        mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", employee)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Managed","lastName":"ByEmployee",
                                 "email":"managed-%s@example.com","phone":null}""".formatted(suffix)))
                .andExpect(status().isCreated());

        // Customer: forbidden.
        mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", customer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Nope","lastName":"Nope",
                                 "email":"nope-%s@example.com","phone":null}""".formatted(suffix)))
                .andExpect(status().isForbidden());
    }

    // ---------- ownership ----------

    @Test
    void customerCanAccessOwnProfileButNotAnotherCustomers() throws Exception {
        Customer customerA = newCustomer();
        Customer customerB = newCustomer();
        String tokenA = AuthTestHelper.bearer(
                AuthTestHelper.customerToken(users, passwordEncoder, jwtService, customerA));

        // Own profile via /me.
        mockMvc.perform(get("/api/v1/customers/me")
                        .header("Authorization", tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(customerA.getId().intValue())));

        // Own id directly: allowed.
        mockMvc.perform(get("/api/v1/customers/" + customerA.getId())
                        .header("Authorization", tokenA))
                .andExpect(status().isOk());

        // Another customer's id: forbidden.
        mockMvc.perform(get("/api/v1/customers/" + customerB.getId())
                        .header("Authorization", tokenA))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)));
    }

    @Test
    void customerSeesOnlyOwnAccountsViaMe() throws Exception {
        Customer customerA = newCustomer();
        Customer customerB = newCustomer();
        String employee = AuthTestHelper.bearer(
                AuthTestHelper.employeeToken(users, passwordEncoder, jwtService));
        createAccount(employee, customerA.getId());
        createAccount(employee, customerB.getId());

        String tokenA = AuthTestHelper.bearer(
                AuthTestHelper.customerToken(users, passwordEncoder, jwtService, customerA));

        // /me lists only the caller's accounts.
        MvcResult mine = mockMvc.perform(get("/api/v1/accounts/me")
                        .header("Authorization", tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(1)))
                .andReturn();
        long ownAccountId = ((Number) com.jayway.jsonpath.JsonPath.read(
                mine.getResponse().getContentAsString(), "$[0].id")).longValue();

        // Own account: allowed; another customer's listing: forbidden.
        mockMvc.perform(get("/api/v1/accounts/" + ownAccountId)
                        .header("Authorization", tokenA))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/customers/" + customerB.getId() + "/accounts")
                        .header("Authorization", tokenA))
                .andExpect(status().isForbidden());
    }

    @Test
    void healthEndpointRemainsPublic() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")));
    }

    // ---------- helpers ----------

    private static final java.util.concurrent.atomic.AtomicLong TEST_CUSTOMER_SEQ =
            new java.util.concurrent.atomic.AtomicLong(10001L);

    private String uid() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private void register(String suffix) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"login-%s","email":"login-%s@example.com","password":"Password123!"}"""
                                .formatted(suffix, suffix)))
                .andExpect(status().isCreated());
    }

    private String loginAndGetToken(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"Password123!"}""".formatted(username)))
                .andExpect(status().isOk())
                .andReturn();
        return com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.accessToken");
    }

    private User persistUser(String prefix, UserStatus status) {
        String username = prefix + "-" + uid();
        User user = new User(
                username, username + "@example.com",
                passwordEncoder.encode(AuthTestHelper.TEST_PASSWORD), Role.CUSTOMER);
        user.setStatus(status);
        return users.save(user);
    }

    private User persistUserWithRole(String prefix, Role role) {
        String username = prefix + "-" + uid();
        User user = new User(
                username, username + "@example.com",
                passwordEncoder.encode(AuthTestHelper.TEST_PASSWORD), role);
        return users.save(user);
    }

    private Customer newCustomer() {
        // Well-formed number in a high range reserved for tests: keeps the
        // production MAX(customerNumber)+1 generator on well-formed input.
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        long seq = TEST_CUSTOMER_SEQ.getAndIncrement();
        return customers.save(new Customer(
                "CUST-9" + String.format("%05d", seq % 100000),
                "Test", "User",
                "cust-" + suffix + "@example.com", null));
    }

    private void createAccount(String employeeAuthHeader, Long customerId) throws Exception {
        mockMvc.perform(post("/api/v1/accounts")
                        .header("Authorization", employeeAuthHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":%d,"accountType":"SAVINGS","currency":"INR"}"""
                                .formatted(customerId)))
                .andExpect(status().isCreated());
    }
}
