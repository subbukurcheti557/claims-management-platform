package com.icmp.identity.auth.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.icmp.identity.auth.application.AuthService;
import com.icmp.identity.common.error.ApiException;
import com.icmp.identity.common.security.CurrentUser;
import com.icmp.identity.user.api.UserResponse;
import com.icmp.identity.user.infrastructure.UserRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication")
public class AuthController {

    private final AuthService authService;
    private final UserRepository users;

    public AuthController(AuthService authService, UserRepository users) {
        this.authService = authService;
        this.users = users;
    }

    @PostMapping("/login")
    @Operation(summary = "Exchange email and password for a JWT access token")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        AuthService.LoginResult result = authService.login(request.email(), request.password());
        return new LoginResponse(result.token().value(), "Bearer", result.token().expiresInSeconds(),
                UserResponse.from(result.user()));
    }

    @GetMapping("/me")
    @Operation(summary = "Profile of the authenticated user")
    public UserResponse me() {
        CurrentUser current = CurrentUser.get();
        return users.findById(current.id()).map(UserResponse::from)
                .orElseThrow(() -> ApiException.notFound("User no longer exists"));
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
    }

    public record LoginResponse(String accessToken, String tokenType, long expiresIn, UserResponse user) {
    }
}
