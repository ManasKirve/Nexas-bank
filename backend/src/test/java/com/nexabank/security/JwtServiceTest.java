package com.nexabank.security;

import com.nexabank.entity.Role;
import com.nexabank.entity.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit contract for JwtService: generation, validation, claim extraction
 * and expiry handling — without any Spring context.
 */
class JwtServiceTest {

    private static final String SECRET =
            "test-only-insecure-secret-for-automated-tests-1234567890";

    private final JwtService jwtService = new JwtService(SECRET, 3600000L);

    private static User user() {
        return new User("alice", "alice@example.com", "hashed", Role.CUSTOMER);
    }

    @Test
    void generatesValidTokenWithExpectedClaims() {
        String token = jwtService.generateToken(user());

        assertThat(jwtService.isValid(token)).isTrue();
        assertThat(jwtService.isExpired(token)).isFalse();
        assertThat(jwtService.extractUsername(token)).isEqualTo("alice");
        assertThat(jwtService.extractRole(token)).isEqualTo(Role.CUSTOMER);
    }

    @Test
    void rejectsTamperedToken() {
        String token = jwtService.generateToken(user());
        String tampered = token.substring(0, token.length() - 2) + "ab";

        assertThat(jwtService.isValid(tampered)).isFalse();
    }

    @Test
    void rejectsTokenSignedWithDifferentSecret() {
        JwtService other = new JwtService(
                "a-completely-different-test-secret-1234567890", 3600000L);
        String foreign = other.generateToken(user());

        assertThat(jwtService.isValid(foreign)).isFalse();
    }

    @Test
    void expiredTokenIsDetectedAndInvalid() {
        JwtService expired = new JwtService(SECRET, -1000L);
        String token = expired.generateToken(user());

        assertThat(expired.isExpired(token)).isTrue();
        assertThat(expired.isValid(token)).isFalse();
    }

    @Test
    void rejectsBlankAndGarbageTokens() {
        assertThat(jwtService.isValid("")).isFalse();
        assertThat(jwtService.isValid("not-a-jwt")).isFalse();
        assertThat(jwtService.isValid(null)).isFalse();
    }

    @Test
    void requiresSufficientlyLongSecret() {
        assertThatThrownBy(() -> new JwtService("short", 3600000L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
        assertThatThrownBy(() -> new JwtService("", 3600000L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not configured");
    }
}
