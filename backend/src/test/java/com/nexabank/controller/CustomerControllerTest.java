package com.nexabank.controller;

import com.nexabank.repository.UserRepository;
import com.nexabank.security.JwtService;
import com.nexabank.support.AuthTestHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API contract for /api/v1/customers: status codes, validation shape and errors.
 * Customer management requires BANK_EMPLOYEE/ADMIN, so every request carries
 * a real JWT minted for a persisted employee user.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CustomerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private String authHeader;

    @BeforeEach
    void authenticate() {
        authHeader = AuthTestHelper.bearer(
                AuthTestHelper.employeeToken(users, passwordEncoder, jwtService));
    }

    private static String uniqueEmail(String prefix) {
        return prefix + "-" + java.util.UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    @Test
    void createReturns201WithCustomerNumber() throws Exception {
        mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Ann","lastName":"Smith",
                                 "email":"%s","phone":"+91 98765 43210"}"""
                                .formatted(uniqueEmail("ann-smith"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerNumber", containsString("CUST-")))
                .andExpect(jsonPath("$.status", is("ACTIVE")));
    }

    @Test
    void createRejectsBlankNamesAndBadEmail() throws Exception {
        mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"","lastName":"","email":"not-an-email","phone":"abc"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Validation failed")))
                .andExpect(jsonPath("$.errors.firstName", notNullValue()))
                .andExpect(jsonPath("$.errors.lastName", notNullValue()))
                .andExpect(jsonPath("$.errors.email", notNullValue()));
    }

    @Test
    void createRejectsDuplicateEmailWith409() throws Exception {
        String email = uniqueEmail("dupe");
        String body = """
                {"firstName":"Ann","lastName":"Smith","email":"%s","phone":null}""".formatted(email);
        mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("already exists")));
    }

    @Test
    void getMissingCustomerReturns404WithoutStackTrace() throws Exception {
        mockMvc.perform(get("/api/v1/customers/999999")
                        .header("Authorization", authHeader))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.message", containsString("Customer not found")))
                .andExpect(jsonPath("$.path", is("/api/v1/customers/999999")));
    }

    @Test
    void updateAndDeactivateFlow() throws Exception {
        String email = uniqueEmail("flow");
        MvcResult created = mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Ann","lastName":"Smith",
                                 "email":"%s","phone":null}""".formatted(email)))
                .andExpect(status().isCreated())
                .andReturn();
        long id = ((Number) com.jayway.jsonpath.JsonPath.read(
                created.getResponse().getContentAsString(), "$.id")).longValue();

        mockMvc.perform(put("/api/v1/customers/" + id)
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Annette","lastName":"Smith",
                                 "email":"%s","phone":null}""".formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName", is("Annette")));

        // Soft delete: row kept, status flips to INACTIVE.
        mockMvc.perform(delete("/api/v1/customers/" + id)
                        .header("Authorization", authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("INACTIVE")));

        mockMvc.perform(get("/api/v1/customers/" + id)
                        .header("Authorization", authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("INACTIVE")));
    }

    @Test
    void missingJwtReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/customers/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));
    }
}
