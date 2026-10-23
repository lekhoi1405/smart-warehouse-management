
package com.lekhoi.smart_warehouse.config;

import java.util.Base64;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.lekhoi.smart_warehouse.service.AuthorityService;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {

    @Value("${app.jwt.base64-secret}")
    private String jwtSecret;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    @Bean
    public JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey()));
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withSecretKey(secretKey()).macAlgorithm(MacAlgorithm.HS512).build();
    }

    private SecretKey secretKey() {
        byte[] bytes = Base64.getDecoder().decode(jwtSecret);

        if (bytes.length < 64) {
            throw new IllegalStateException("HS512 JWT secret must contain at least 64 bytes");
        }

        return new SecretKeySpec(bytes, "HmacSHA512");
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter(AuthorityService authorityService) {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> authorityService.loadActive(jwt.getSubject()).authorities());
        return converter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/auth/login", "/auth/refresh", "/auth/logout").permitAll()
                .requestMatchers("/auth/me").authenticated()

                .requestMatchers("/admin/**").hasRole("ADMIN")

                .requestMatchers("/products", "/products/**").hasAuthority("PRODUCT_MANAGE")
                .requestMatchers("/suppliers", "/suppliers/**").hasAuthority("SUPPLIER_MANAGE")
                .requestMatchers("/stock-documents", "/stock-documents/**").hasAuthority("STOCK_DOCUMENT_WRITE")
                .requestMatchers("/inventory", "/inventory/**").hasAuthority("INVENTORY_VIEW")
                .requestMatchers("/stocktakes", "/stocktakes/**").hasAuthority("STOCKTAKE_WRITE")

                .requestMatchers(HttpMethod.GET, "/low-stock-alerts", "/low-stock-alerts/**").hasAuthority("ALERT_VIEW")
                .requestMatchers("/low-stock-alerts", "/low-stock-alerts/**").hasAuthority("ALERT_MANAGE")

                .requestMatchers("/statistics", "/statistics/**").hasAuthority("STATISTICS_VIEW")
                .requestMatchers("/reports", "/reports/**").hasAuthority("REPORT_EXPORT")

                .anyRequest().denyAll()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable());

        return http.build();
    }
}
