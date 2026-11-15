package com.icmp.identity.auth.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.icmp.identity.common.error.ApiException;
import com.icmp.identity.user.domain.AppUser;
import com.icmp.identity.user.infrastructure.UserRepository;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String INVALID = "Invalid email or password.";

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, TokenService tokenService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
    }

    /** Authenticates by email + password. The same message is returned for unknown email and wrong password. */
    @Transactional(readOnly = true)
    public LoginResult login(String email, String password) {
        AppUser user = users.findByEmailIgnoreCase(email.trim())
                .filter(AppUser::isActive)
                .orElseThrow(() -> ApiException.unauthorized(INVALID));
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            log.info("Failed login for user {}", user.getId());
            throw ApiException.unauthorized(INVALID);
        }
        return new LoginResult(user, tokenService.issue(user));
    }

    public record LoginResult(AppUser user, TokenService.IssuedToken token) {
    }
}
