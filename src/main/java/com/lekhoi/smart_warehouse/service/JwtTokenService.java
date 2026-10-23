package com.lekhoi.smart_warehouse.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class JwtTokenService {

    private final JwtEncoder jwtEncoder;

    @Value("${app.jwt.access-token-validity-in-seconds}")
    private long accessTokenValidity;

    public String createAccessToken(String username) {
        Instant now = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
            .subject(username)
            .issuedAt(now)
            .expiresAt(now.plus(accessTokenValidity, ChronoUnit.SECONDS))
            .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS512).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
