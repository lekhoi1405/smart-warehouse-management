
package com.lekhoi.smart_warehouse.service;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import com.lekhoi.smart_warehouse.domain.dto.AccountDTO;
import com.lekhoi.smart_warehouse.domain.dto.AuthDTO;
import com.lekhoi.smart_warehouse.domain.entity.RefreshToken;
import com.lekhoi.smart_warehouse.domain.entity.UserAccount;
import com.lekhoi.smart_warehouse.repository.RefreshTokenRepository;
import com.lekhoi.smart_warehouse.repository.UserAccountRepository;
import com.lekhoi.smart_warehouse.util.constant.AccountStatusEnum;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenService jwtTokenService;
    private final AuthorityService authorityService;
    private final UserAccountRepository accountRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${app.jwt.refresh-token-validity-in-seconds}")
    private long refreshTokenValidity;

    @Value("${app.jwt.cookie-secure:true}")
    private boolean cookieSecure;

    public record Session(AuthDTO.LoginResponse response, ResponseCookie cookie) {}

    @Transactional
    public Session handleLogin(AuthDTO.LoginRequest request) {
        String username = request.username().trim().toLowerCase(Locale.ROOT);

        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, request.password()));

        UserAccount account = accountRepository.findByUsername(username)
            .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        AuthorityService.Snapshot snapshot = authorityService.resolve(account);
        return createSession(account, snapshot);
    }

    @Transactional
    public Session handleRefresh(String token) {
        if (token == null || token.isBlank()) {
            throw new BadCredentialsException("Missing refresh token");
        }

        RefreshToken stored = refreshTokenRepository.findByTokenWithAccount(token)
            .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));

        if (stored.isRevoked() || !stored.getExpiryDate().isAfter(Instant.now())
                || stored.getAccount().getStatus() != AccountStatusEnum.ACTIVE) {
            throw new BadCredentialsException("Expired or revoked refresh token");
        }

        UserAccount account = stored.getAccount();
        refreshTokenRepository.deleteByToken(token);

        return createSession(account, authorityService.resolve(account));
    }

    @Transactional
    public ResponseCookie handleLogout(String token) {
        if (token != null && !token.isBlank()) {
            refreshTokenRepository.deleteByToken(token);
        }

        return buildCookie("", 0);
    }

    @Transactional(readOnly = true)
    public AccountDTO.Response handleGetCurrentAccount() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        AuthorityService.Snapshot snapshot = authorityService.loadActive(username);

        UserAccount account = accountRepository.findByUsername(username)
            .orElseThrow(() -> new BadCredentialsException("Account not found"));

        return toResponse(account, snapshot);
    }

    private Session createSession(UserAccount account, AuthorityService.Snapshot snapshot) {
        if (account.getStatus() != AccountStatusEnum.ACTIVE) {
            throw new BadCredentialsException("Account is disabled");
        }

        String accessToken = jwtTokenService.createAccessToken(account.getUsername());
        String refreshValue = UUID.randomUUID().toString();

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken(refreshValue);
        refreshToken.setAccount(account);
        refreshToken.setExpiryDate(Instant.now().plusSeconds(refreshTokenValidity));
        refreshToken.setRevoked(false);
        refreshTokenRepository.save(refreshToken);

        return new Session(
            new AuthDTO.LoginResponse(accessToken, toResponse(account, snapshot)),
            buildCookie(refreshValue, refreshTokenValidity)
        );
    }

    private AccountDTO.Response toResponse(UserAccount account, AuthorityService.Snapshot snapshot) {
        return new AccountDTO.Response(
            account.getId(),
            account.getUsername(),
            account.getFullName(),
            account.getEmail(),
            account.getPhone(),
            account.getStatus(),
            snapshot.roleNames(),
            snapshot.permissionNames()
        );
    }

    private ResponseCookie buildCookie(String value, long maxAge) {
        return ResponseCookie.from("refresh_token", value)
            .httpOnly(true)
            .secure(cookieSecure)
            .sameSite("Lax")
            .path("/auth")
            .maxAge(maxAge)
            .build();
    }
}
