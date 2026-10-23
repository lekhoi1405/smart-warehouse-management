package com.lekhoi.smart_warehouse.controller;

import com.lekhoi.smart_warehouse.domain.dto.AccountDTO;
import com.lekhoi.smart_warehouse.domain.dto.AuthDTO;
import com.lekhoi.smart_warehouse.service.AuthService;
import com.lekhoi.smart_warehouse.util.annotation.ApiMessage;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    @ApiMessage("Login success")
    @PostMapping("/login")
    public ResponseEntity<AuthDTO.LoginResponse> login(@Valid @RequestBody AuthDTO.LoginRequest request) {
        AuthService.Session session = authService.handleLogin(request);
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, session.cookie().toString()).body(session.response());
    }

    @ApiMessage("Refresh success")
    @PostMapping("/refresh")
    public ResponseEntity<AuthDTO.LoginResponse> refresh(@CookieValue(value = "refresh_token", required = false) String token) {
        AuthService.Session session = authService.handleRefresh(token);
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, session.cookie().toString()).body(session.response());
    }

    @ApiMessage("Logout success")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(value = "refresh_token", required = false) String token) {
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, authService.handleLogout(token).toString()).build();
    }

    @ApiMessage("Get current account")
    @GetMapping("/me")
    public ResponseEntity<AccountDTO.Response> getCurrentAccount() {
        return ResponseEntity.ok(authService.handleGetCurrentAccount());
    }
}
